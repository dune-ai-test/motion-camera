package com.motioncapture.app.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.motioncapture.app.data.AppSettings
import com.motioncapture.app.data.CaptureMode
import com.motioncapture.app.data.SaveDestination
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

interface SessionListener {
    fun onDetectionResult(result: DetectionResult)
    fun onCaptureComplete(savedUri: Uri?, success: Boolean)
    fun onError(message: String)
    fun onRecordingState(recording: Boolean)
}

data class DetectedObjectData(
    val label: String,
    val confidence: Float,
    val box: RectF,
)

data class DetectionResult(
    val objects: List<DetectedObjectData>,
    val motionDetected: Boolean,
    val imageAspect: Float,
)

class CameraSession(
    private val context: Context,
    private val listener: SessionListener,
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var camera: Camera? = null
    private var detector: ObjectDetector? = null
    private var executor: ExecutorService? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var previewView: PreviewView? = null

    @Volatile
    private var bound = false
    private var sessionStarted = false
    private var analyzing = true
    private var flashEnabled = false
    private var facing = CameraSelector.LENS_FACING_BACK
    private var settings = AppSettings()

    private var lastFrame: IntArray? = null
    private var recording: Recording? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopRecordingRunnable = Runnable { stopVideo() }

    private var lastAnalysisTime = 0L
    private var lastCaptureTime = 0L
    private var burstInProgress = false

    fun start(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        this.lifecycleOwner = lifecycleOwner
        this.previewView = previewView

        if (sessionStarted) {
            preview?.setSurfaceProvider(previewView.surfaceProvider)
            return
        }
        sessionStarted = true

        detector = ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build()
        )

        executor = Executors.newSingleThreadExecutor()

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                bindUseCases()
            } catch (e: Exception) {
                listener.onError(e.message ?: "Camera initialization failed")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun detachPreview() {
        preview?.setSurfaceProvider(null)
    }

    fun setAnalyzing(enabled: Boolean) {
        analyzing = enabled
        if (!enabled) {
            lastFrame = null
        }
    }

    fun setFlash(enabled: Boolean) {
        flashEnabled = enabled
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun setSettings(settings: AppSettings) {
        val modeChanged = settings.captureMode != this.settings.captureMode
        this.settings = settings
        if (modeChanged && bound) {
            stopVideo()
            lastFrame = null
            bindUseCases()
        }
    }

    fun toggleCamera() {
        facing = if (facing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        lastFrame = null
        if (bound) {
            stopVideo()
            bindUseCases()
        }
    }

    fun shutdown() {
        stopVideo()
        executor?.shutdown()
        cameraProvider?.unbindAll()
        detector?.close()
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        val owner = lifecycleOwner ?: return
        val view = previewView ?: return

        provider.unbindAll()
        try {
            val selector = CameraSelector.Builder().requireLensFacing(facing).build()
            val analysisExecutor = executor ?: return
            val useCases = mutableListOf<UseCase>()

            val previewUC = Preview.Builder().build().also {
                it.setSurfaceProvider(view.surfaceProvider)
            }
            preview = previewUC
            useCases.add(previewUC)

            val analysisUC = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis = analysisUC
            analysisUC.setAnalyzer(analysisExecutor) { proxy -> analyzeFrame(proxy) }
            useCases.add(analysisUC)

            if (settings.captureMode == CaptureMode.PHOTO) {
                val captureUC = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(
                        if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                    )
                    .build()
                imageCapture = captureUC
                useCases.add(captureUC)
            } else {
                val videoUC = VideoCapture.withOutput(
                    Recorder.Builder()
                        .setExecutor(analysisExecutor)
                        .setQualitySelector(
                            QualitySelector.fromOrderedList(
                                listOf(Quality.SD, Quality.LOWEST),
                                FallbackStrategy.higherQualityOrLowerThan(Quality.SD),
                            )
                        )
                        .build()
                )
                videoCapture = videoUC
                useCases.add(videoUC)
            }

            camera = provider.bindToLifecycle(owner, selector, *useCases.toTypedArray())
            camera?.cameraControl?.enableTorch(flashEnabled)
            bound = true
        } catch (e: Exception) {
            bound = false
            listener.onError(e.message ?: "Unable to bind camera")
        }
    }

    private fun analyzeFrame(proxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (!analyzing || now - lastAnalysisTime < FRAME_INTERVAL_MS) {
            proxy.close()
            return
        }
        lastAnalysisTime = now

        val rotation = proxy.imageInfo.rotationDegrees
        val raw = proxy.toBitmap()
        proxy.close()
        if (raw == null) return

        val bitmap = rotateIfNeeded(raw, rotation)
        if (bitmap.width == 0 || bitmap.height == 0) return

        val motion = detectPixelMotion(bitmap)

        val image = InputImage.fromBitmap(bitmap, 0)
        detector?.process(image)
            ?.addOnSuccessListener { objects ->
                onDetections(objects, bitmap.width, bitmap.height, motion)
            }
            ?.addOnFailureListener {
                // Ignore individual frame failures; keep streaming.
            }

        if (motion) {
            triggerCapture()
        }
    }

    private fun rotateIfNeeded(src: Bitmap, rotation: Int): Bitmap {
        if (rotation == 0) return src
        val matrix = Matrix()
        matrix.postRotate(rotation.toFloat())
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun detectPixelMotion(bitmap: Bitmap): Boolean {
        val current = downscaleToLuma(bitmap)
        val score = motionScore(current, lastFrame)
        lastFrame = current
        return score >= settings.sensitivity.motionFraction
    }

    private fun downscaleToLuma(src: Bitmap): IntArray {
        val cols = MOTION_GRID_COLS
        val rows = MOTION_GRID_ROWS
        val scaled = if (src.width == cols && src.height == rows) {
            src
        } else {
            Bitmap.createScaledBitmap(src, cols, rows, true)
        }
        val pixels = IntArray(cols * rows)
        scaled.getPixels(pixels, 0, cols, 0, 0, cols, rows)
        if (scaled !== src) {
            scaled.recycle()
        }
        val luma = IntArray(cols * rows)
        for (i in pixels.indices) {
            val c = pixels[i]
            luma[i] = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        }
        return luma
    }

    private fun motionScore(current: IntArray, previous: IntArray?): Float {
        if (previous == null || previous.size != current.size) return 0f
        var changed = 0
        for (i in current.indices) {
            if (abs(current[i] - previous[i]) > MOTION_PIXEL_DIFF) {
                changed++
            }
        }
        return changed.toFloat() / current.size
    }

    private fun onDetections(objects: List<DetectedObject>, imgW: Int, imgH: Int, motion: Boolean) {
        if (imgW == 0 || imgH == 0) return

        val detections = objects.mapNotNull { obj ->
            val labelInfo = obj.labels.maxByOrNull { it.confidence }
            val label = labelInfo?.text ?: "object"
            val confidence = labelInfo?.confidence ?: 0f
            if (confidence < MIN_CONFIDENCE) return@mapNotNull null
            val box = obj.boundingBox
            DetectedObjectData(
                label = label,
                confidence = confidence,
                box = RectF(
                    box.left.toFloat() / imgW,
                    box.top.toFloat() / imgH,
                    box.right.toFloat() / imgW,
                    box.bottom.toFloat() / imgH,
                ),
            )
        }

        val filtered = if (settings.peopleOnly) {
            detections.filter {
                it.label.equals("person", ignoreCase = true) ||
                    it.label.equals("people", ignoreCase = true)
            }
        } else {
            detections
        }

        listener.onDetectionResult(
            DetectionResult(filtered, motion, imgW.toFloat() / imgH.toFloat())
        )
    }

    private fun triggerCapture() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCaptureTime < CAPTURE_COOLDOWN_MS) return
        lastCaptureTime = now
        when (settings.captureMode) {
            CaptureMode.PHOTO -> captureBurst(settings.burstCount)
            CaptureMode.VIDEO -> startVideo()
        }
    }

    private fun captureBurst(remaining: Int) {
        if (remaining <= 0) {
            burstInProgress = false
            return
        }
        val capture = imageCapture
        if (capture == null) {
            burstInProgress = false
            return
        }

        val options = outputOptions()
        capture.takePicture(
            options,
            executor ?: ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    listener.onCaptureComplete(output.savedUri, true)
                    executor?.execute { captureBurst(remaining - 1) }
                }

                override fun onError(exception: ImageCaptureException) {
                    listener.onCaptureComplete(null, false)
                    burstInProgress = false
                }
            },
        )
    }

    private fun startVideo() {
        if (recording != null) return
        val video = videoCapture ?: return
        val pending = if (settings.saveTo == SaveDestination.APP) {
            val dir = File(context.filesDir, "MotionCaptureVideos").apply { mkdirs() }
            val file = File(dir, videoFileName())
            video.output.prepareRecording(
                context,
                FileOutputOptions.Builder(file).build(),
            )
        } else {
            video.output.prepareRecording(
                context,
                MediaStoreOutputOptions.Builder(
                    context.contentResolver,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                )
                    .setContentValues(videoStoreValues())
                    .build(),
            )
        }
        recording = pending.start(
            executor ?: ContextCompat.getMainExecutor(context),
        ) { event -> onVideoEvent(event) }
        listener.onRecordingState(true)
        mainHandler.postDelayed(stopRecordingRunnable, VIDEO_CLIP_MS)
    }

    private fun onVideoEvent(event: VideoRecordEvent) {
        if (event is VideoRecordEvent.Finalize) {
            mainHandler.removeCallbacks(stopRecordingRunnable)
            recording = null
            listener.onRecordingState(false)
            if (event.hasError()) {
                listener.onCaptureComplete(null, false)
            } else {
                listener.onCaptureComplete(event.outputResults.outputUri, true)
            }
        }
    }

    private fun stopVideo() {
        recording?.stop()
    }

    private fun outputOptions(): ImageCapture.OutputFileOptions {
        return if (settings.saveTo == SaveDestination.APP) {
            val dir = File(context.filesDir, "MotionCapture").apply { mkdirs() }
            val file = File(dir, fileName())
            ImageCapture.OutputFileOptions.Builder(file).build()
        } else {
            ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                mediaStoreValues(),
            ).build()
        }
    }

    private fun fileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return "MotionCapture_$stamp.jpg"
    }

    private fun videoFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return "MotionCapture_$stamp.mp4"
    }

    private fun mediaStoreValues(): ContentValues {
        val now = System.currentTimeMillis()
        return ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName())
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, now / 1000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/Motion Capture",
                )
            }
        }
    }

    private fun videoStoreValues(): ContentValues {
        val now = System.currentTimeMillis()
        return ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, videoFileName())
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, now / 1000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/Motion Capture",
                )
            }
        }
    }

    companion object {
        private const val FRAME_INTERVAL_MS = 120L
        private const val CAPTURE_COOLDOWN_MS = 2500L
        private const val MIN_CONFIDENCE = 0.55f
        private const val VIDEO_CLIP_MS = 10_000L
        private const val MOTION_GRID_COLS = 64
        private const val MOTION_GRID_ROWS = 48
        private const val MOTION_PIXEL_DIFF = 20
    }
}
