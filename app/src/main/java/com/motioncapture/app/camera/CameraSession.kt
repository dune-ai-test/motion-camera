package com.motioncapture.app.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
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
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.motioncapture.app.data.AppSettings
import com.motioncapture.app.data.SaveDestination
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt

interface SessionListener {
    fun onDetectionResult(result: DetectionResult)
    fun onCaptureComplete(savedUri: Uri?, success: Boolean)
    fun onError(message: String)
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

    private var lastCenter: PointF? = null
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
            lastCenter = null
        }
    }

    fun setFlash(enabled: Boolean) {
        flashEnabled = enabled
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun setSettings(settings: AppSettings) {
        this.settings = settings
    }

    fun toggleCamera() {
        facing = if (facing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        lastCenter = null
        if (bound) bindUseCases()
    }

    fun shutdown() {
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

            preview = Preview.Builder().build().also {
                it.setSurfaceProvider(view.surfaceProvider)
            }

            val analysisExecutor = executor ?: return

            imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis?.setAnalyzer(analysisExecutor) { proxy -> analyzeFrame(proxy) }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(
                    if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                )
                .build()

            camera = provider.bindToLifecycle(owner, selector, preview, imageAnalysis, imageCapture)
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

        val image = InputImage.fromBitmap(bitmap, 0)
        detector?.process(image)
            ?.addOnSuccessListener { objects ->
                onDetections(objects, bitmap.width, bitmap.height)
            }
            ?.addOnFailureListener {
                // Ignore individual frame failures; keep streaming.
            }
    }

    private fun rotateIfNeeded(src: Bitmap, rotation: Int): Bitmap {
        if (rotation == 0) return src
        val matrix = Matrix()
        matrix.postRotate(rotation.toFloat())
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun onDetections(objects: List<DetectedObject>, imgW: Int, imgH: Int) {
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

        val largest = filtered.maxByOrNull { it.box.width() * it.box.height() }
        val motion = detectMotion(largest, imgW, imgH)

        listener.onDetectionResult(
            DetectionResult(filtered, motion, imgW.toFloat() / imgH.toFloat())
        )

        if (motion && largest != null && analyzing) {
            triggerCapture()
        }
    }

    private fun detectMotion(target: DetectedObjectData?, imgW: Int, imgH: Int): Boolean {
        if (target == null) {
            lastCenter = null
            return false
        }
        val center = PointF(
            target.box.centerX() * imgW,
            target.box.centerY() * imgH,
        )
        val prev = lastCenter
        lastCenter = center
        if (prev == null) return false

        val dx = center.x - prev.x
        val dy = center.y - prev.y
        return sqrt(dx * dx + dy * dy) > settings.sensitivity.motionThresholdPx
    }

    private fun triggerCapture() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCaptureTime < CAPTURE_COOLDOWN_MS) return
        if (burstInProgress) return
        lastCaptureTime = now
        burstInProgress = true
        captureBurst(settings.burstCount)
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

    companion object {
        private const val FRAME_INTERVAL_MS = 120L
        private const val CAPTURE_COOLDOWN_MS = 1800L
        private const val MIN_CONFIDENCE = 0.55f
    }
}
