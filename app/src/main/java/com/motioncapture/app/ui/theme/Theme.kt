package com.motioncapture.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = SystemBlue,
    onPrimary = Color.White,
    secondary = SystemGreen,
    background = Color.White,
    surface = CellBackground,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = Color(0xFFFF3B30),
)

@Composable
fun MotionCaptureTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
