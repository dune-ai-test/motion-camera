package com.motioncapture.app.ui.camera

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import coil.compose.AsyncImage
import com.motioncapture.app.ui.MainViewModel
import com.motioncapture.app.ui.Screen
import com.motioncapture.app.ui.UiState
import com.motioncapture.app.ui.theme.CameraBlack
import com.motioncapture.app.ui.theme.ControlBackground
import com.motioncapture.app.ui.theme.DetectionFill
import com.motioncapture.app.ui.theme.HudBackground
import com.motioncapture.app.ui.theme.LabelGray
import com.motioncapture.app.ui.theme.LabelSecondaryLight
import com.motioncapture.app.ui.theme.SystemGreen
import com.motioncapture.app.ui.theme.SystemOrange
import com.motioncapture.app.ui.theme.SystemRed
import com.motioncapture.app.ui.theme.ToastBackground
import java.util.Locale

@Composable
fun CameraScreen(
    state: UiState,
    viewModel: MainViewModel,
    lifecycleOwner: LifecycleOwner,
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(previewView) {
        viewModel.attachCamera(lifecycleOwner, previewView)
        onDispose { viewModel.detachCamera() }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CameraBlack),
    ) {
        val screenW = maxWidth
        val screenH = maxHeight

        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        CameraTopBar(state = state, viewModel = viewModel)

        HudCard(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 170.dp),
        )

        val detection = state.detection
        val objects = detection?.objects.orEmpty()
        if (objects.isNotEmpty() && detection != null) {
            val target = objects.maxByOrNull { it.box.width() * it.box.height() }
            if (target != null) {
                val leftRaw = target.box.left
                val rightRaw = target.box.right
                val leftMirrored = if (state.frontCamera) 1f - rightRaw else leftRaw
                val rightMirrored = if (state.frontCamera) 1f - leftRaw else rightRaw
                val topRaw = target.box.top
                val bottomRaw = target.box.bottom

                // Map normalized (analysis-image) coordinates onto the FILL_CENTER
                // cropped preview so the box stays glued to the subject.
                val viewAspect = screenW.value / screenH.value
                val imgAspect = detection.imageAspect
                val left: Float
                val right: Float
                val top: Float
                val bottom: Float
                if (imgAspect > viewAspect) {
                    val frac = viewAspect / imgAspect
                    val offset = (1f - frac) / 2f
                    left = leftMirrored * frac + offset
                    right = rightMirrored * frac + offset
                    top = topRaw
                    bottom = bottomRaw
                } else {
                    val frac = imgAspect / viewAspect
                    val offset = (1f - frac) / 2f
                    left = leftMirrored
                    right = rightMirrored
                    top = topRaw * frac + offset
                    bottom = bottomRaw * frac + offset
                }

                val boxLeft = screenW * left
                val boxTop = screenH * top
                val boxWidth = screenW * (right - left)
                val boxHeight = screenH * (bottom - top)
                if (boxWidth > 24.dp && boxHeight > 24.dp) {
                    Box(
                        modifier = Modifier
                            .offset(x = boxLeft, y = boxTop)
                            .width(boxWidth)
                            .height(boxHeight),
                    ) {
                        DetectionBoxFill()
                        ConfidenceChip(
                            label = displayLabel(target.label),
                            confidence = target.confidence,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 10.dp, y = 10.dp),
                        )
                        FocusGrid(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(gridSize(boxWidth, boxHeight)),
                        )
                    }
                }
            }
        }

        SavedToast(
            text = state.toastText,
            visible = state.toastVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 216.dp),
        )

        BottomControls(
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

private fun gridSize(boxWidth: Dp, boxHeight: Dp): Dp {
    val max = 140.dp
    val min = if (boxWidth < boxHeight) boxWidth else boxHeight
    return if (min < max) min else max
}

private fun displayLabel(label: String): String {
    return when {
        label.equals("people", ignoreCase = true) || label.equals("person", ignoreCase = true) ->
            "Person"
        label.isEmpty() -> "Object"
        else -> label.replaceFirstChar { it.uppercase(Locale.ROOT) }
    }
}

@Composable
private fun CameraTopBar(state: UiState, viewModel: MainViewModel) {
    Row(
        modifier = Modifier
            .statusBarsPadding()
            .height(64.dp)
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(
                color = when {
                    state.recording -> SystemRed
                    !state.analyzing -> SystemOrange
                    state.detection?.objects?.isNotEmpty() == true -> SystemGreen
                    else -> LabelGray
                },
            )
            Text(
                text = when {
                    state.recording -> "Recording"
                    state.analyzing -> "Live"
                    else -> "Idle"
                },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.recording) {
                Text(
                    text = "REC",
                    color = SystemRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            } else if (state.analyzing) {
                Text(
                    text = "LIVE",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(
                icon = Icons.Filled.FlashOn,
                active = state.flashOn,
                onClick = { viewModel.toggleFlash() },
            )
            CircleButton(
                icon = Icons.Filled.Settings,
                onClick = { viewModel.navigate(Screen.SETTINGS) },
            )
        }
    }
}

@Composable
private fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(10.dp)) {
        drawCircle(color = color)
    }
}

@Composable
private fun CircleButton(
    icon: ImageVector,
    onClick: () -> Unit,
    active: Boolean = false,
    size: Dp = 44.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(ControlBackground, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) SystemGreen else Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun HudCard(state: UiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .width(250.dp)
            .height(56.dp)
            .background(HudBackground, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudStat(value = state.todayCount.toString(), label = "Today")
        HudStat(value = formatElapsed(state.elapsedMs), label = "Detecting")
        HudStat(value = relativeTime(state.lastCaptureAt), label = "Last capture")
    }
}

@Composable
private fun HudStat(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            color = LabelSecondaryLight,
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatElapsed(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun relativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return "—"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L}m"
        else -> "${diff / 3_600_000L}h"
    }
}

@Composable
private fun DetectionBoxFill() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DetectionFill, RoundedCornerShape(6.dp))
            .border(2.dp, SystemGreen, RoundedCornerShape(6.dp)),
    )
}

@Composable
private fun ConfidenceChip(
    label: String,
    confidence: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SystemGreen)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = Color.White, modifier = Modifier.size(6.dp))
        Text(
            text = "$label ${(confidence * 100).toInt()}%",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FocusGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.alpha(0.35f)) {
        val stroke = 1.dp.toPx()
        val w = size.width
        val h = size.height
        val color = Color.White
        drawLine(color, Offset(w / 3, 0f), Offset(w / 3, h), stroke)
        drawLine(color, Offset(w * 2 / 3, 0f), Offset(w * 2 / 3, h), stroke)
        drawLine(color, Offset(0f, h / 3), Offset(w, h / 3), stroke)
        drawLine(color, Offset(0f, h * 2 / 3), Offset(w, h * 2 / 3), stroke)
    }
}

@Composable
private fun SavedToast(
    text: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(ToastBackground)
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = SystemGreen,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun BottomControls(
    state: UiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThumbGroup(
            latestUri = state.latestThumb,
            onClick = { viewModel.navigate(Screen.GALLERY) },
        )

        ShutterGroup(
            analyzing = state.analyzing,
            onClick = { viewModel.toggleAnalyzing() },
        )

        FlipGroup(onClick = { viewModel.toggleCamera() })
    }
}

@Composable
private fun ThumbGroup(latestUri: android.net.Uri?, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(2.dp, Color.White, RoundedCornerShape(10.dp))
                .clickable(onClick = onClick),
        ) {
            if (latestUri != null) {
                AsyncImage(
                    model = latestUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.25f)),
                )
            }
        }
        Text(
            text = "Latest",
            color = LabelSecondaryLight,
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
private fun ShutterGroup(analyzing: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(78.dp)
                .clickable(onClick = onClick)
                .drawBehind {
                    drawCircle(
                        color = Color.White,
                        radius = size.minDimension / 2,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx()),
                    )
                },
        )
        Text(
            text = if (analyzing) "Tap to stop" else "Start live",
            color = LabelSecondaryLight,
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
private fun FlipGroup(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(ControlBackground, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = "Flip",
            color = LabelSecondaryLight,
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}
