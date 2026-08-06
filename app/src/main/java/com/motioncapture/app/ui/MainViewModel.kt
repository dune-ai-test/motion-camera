package com.motioncapture.app.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.motioncapture.app.MotionCaptureApplication
import com.motioncapture.app.camera.CameraSession
import com.motioncapture.app.camera.DetectionResult
import com.motioncapture.app.camera.SessionListener
import com.motioncapture.app.data.AppSettings
import com.motioncapture.app.data.CaptureMode
import com.motioncapture.app.data.GalleryItem
import com.motioncapture.app.data.GalleryRepository
import com.motioncapture.app.data.SaveDestination
import com.motioncapture.app.data.Sensitivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Screen { CAMERA, GALLERY, SETTINGS }

data class UiState(
    val screen: Screen = Screen.CAMERA,
    val analyzing: Boolean = false,
    val recording: Boolean = false,
    val flashOn: Boolean = false,
    val frontCamera: Boolean = false,
    val detection: DetectionResult? = null,
    val todayCount: Int = 0,
    val elapsedMs: Long = 0L,
    val lastCaptureAt: Long = 0L,
    val latestThumb: Uri? = null,
    val toastVisible: Boolean = false,
    val toastText: String = "Saved to Photos",
    val settings: AppSettings = AppSettings(),
    val errorMessage: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MotionCaptureApplication
    private val prefs = app.preferences
    private val gallery: GalleryRepository = app.galleryRepository

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _galleryItems = MutableStateFlow<List<GalleryItem>>(emptyList())
    val galleryItems: StateFlow<List<GalleryItem>> = _galleryItems.asStateFlow()

    private var session: CameraSession? = null
    private var toastJob: Job? = null
    private var tickerJob: Job? = null

    init {
        viewModelScope.launch {
            prefs.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                session?.setSettings(settings)
            }
        }
        viewModelScope.launch {
            prefs.lastCaptureTime.collect { last ->
                _uiState.update { it.copy(lastCaptureAt = last) }
            }
        }
        startTicker()
        viewModelScope.launch { refreshStats() }
    }

    fun attachCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        if (session == null) {
            session = CameraSession(app.applicationContext, sessionListener)
        }
        session?.start(lifecycleOwner, previewView)
        session?.setAnalyzing(_uiState.value.analyzing)
        session?.setSettings(_uiState.value.settings)
    }

    fun detachCamera() {
        session?.detachPreview()
    }

    fun toggleAnalyzing() {
        val next = !_uiState.value.analyzing
        _uiState.update { it.copy(analyzing = next) }
        session?.setAnalyzing(next)
    }

    fun toggleFlash() {
        val next = !_uiState.value.flashOn
        _uiState.update { it.copy(flashOn = next) }
        session?.setFlash(next)
    }

    fun toggleCamera() {
        val next = !_uiState.value.frontCamera
        _uiState.update { it.copy(frontCamera = next) }
        session?.toggleCamera()
    }

    fun navigate(screen: Screen) {
        _uiState.update { it.copy(screen = screen) }
        if (screen == Screen.GALLERY) refreshGallery()
    }

    fun refreshGallery() {
        viewModelScope.launch {
            _galleryItems.value = gallery.galleryItems()
        }
    }

    fun setSensitivity(value: Sensitivity) {
        viewModelScope.launch { prefs.setSensitivity(value) }
    }

    fun setPeopleOnly(value: Boolean) {
        viewModelScope.launch { prefs.setPeopleOnly(value) }
    }

    fun setBurstCount(value: Int) {
        viewModelScope.launch { prefs.setBurstCount(value) }
    }

    fun setSaveTo(value: SaveDestination) {
        viewModelScope.launch { prefs.setSaveTo(value) }
    }

    fun setNotifications(value: Boolean) {
        viewModelScope.launch { prefs.setNotifications(value) }
    }

    fun setCaptureMode(value: CaptureMode) {
        viewModelScope.launch { prefs.setCaptureMode(value) }
    }

    override fun onCleared() {
        super.onCleared()
        session?.shutdown()
    }

    private val sessionListener = object : SessionListener {
        override fun onDetectionResult(result: DetectionResult) {
            _uiState.update { it.copy(detection = result) }
        }

        override fun onCaptureComplete(savedUri: Uri?, success: Boolean) {
            viewModelScope.launch {
                if (success && savedUri != null) {
                    prefs.setLastCaptureTime(System.currentTimeMillis())
                    showToast("Capture saved")
                    refreshStats()
                } else {
                    showToast("Capture failed")
                }
            }
        }

        override fun onError(message: String) {
            Log.e("MotionCapture", message)
            _uiState.update { it.copy(errorMessage = message) }
            showToast(message)
        }

        override fun onRecordingState(recording: Boolean) {
            _uiState.update { it.copy(recording = recording) }
        }
    }

    private fun showToast(text: String) {
        toastJob?.cancel()
        _uiState.update { it.copy(toastText = text, toastVisible = true) }
        toastJob = viewModelScope.launch {
            delay(2400)
            _uiState.update { it.copy(toastVisible = false) }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _uiState.update { state ->
                    if (state.analyzing) state.copy(elapsedMs = state.elapsedMs + 1000L) else state
                }
            }
        }
    }

    private suspend fun refreshStats() {
        val today = gallery.todayCount()
        val latest = gallery.latestItem()
        _uiState.update { it.copy(todayCount = today, latestThumb = latest?.uri) }
    }
}
