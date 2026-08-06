package com.motioncapture.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.motioncapture.app.ui.MainViewModel
import com.motioncapture.app.ui.Screen
import com.motioncapture.app.ui.camera.CameraScreen
import com.motioncapture.app.ui.gallery.GalleryScreen
import com.motioncapture.app.ui.permissions.PermissionsScreen
import com.motioncapture.app.ui.settings.SettingsScreen
import com.motioncapture.app.ui.theme.MotionCaptureTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MotionCaptureTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionsGranted by remember { mutableStateOf(hasAllPermissions(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionsGranted = requiredPermissions(context).all { grants[it] == true }
    }

    LaunchedEffect(Unit) {
        permissionsGranted = hasAllPermissions(context)
    }

    if (!permissionsGranted) {
        PermissionsScreen(
            onAllow = { permissionLauncher.launch(requiredPermissions(context)) },
            onDeny = {
                // Stay on the permissions screen; user can tap Allow again.
            },
        )
    } else {
        when (state.screen) {
            Screen.CAMERA -> CameraScreen(
                state = state,
                viewModel = viewModel,
                lifecycleOwner = lifecycleOwner,
            )

            Screen.GALLERY -> GalleryScreen(state = state, viewModel = viewModel)

            Screen.SETTINGS -> SettingsScreen(state = state, viewModel = viewModel)
        }
    }
}

private fun requiredPermissions(context: Context): Array<String> = buildList {
    add(Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}.toTypedArray()

private fun hasAllPermissions(context: Context): Boolean =
    requiredPermissions(context).all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
