package com.motioncapture.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motioncapture.app.data.CaptureMode
import com.motioncapture.app.data.SaveDestination
import com.motioncapture.app.data.Sensitivity
import com.motioncapture.app.ui.MainViewModel
import com.motioncapture.app.ui.Screen
import com.motioncapture.app.ui.UiState
import com.motioncapture.app.ui.theme.CellBackground
import com.motioncapture.app.ui.theme.LabelGray
import com.motioncapture.app.ui.theme.RowDivider
import com.motioncapture.app.ui.theme.SettingsBackground
import com.motioncapture.app.ui.theme.SystemBlue
import com.motioncapture.app.ui.theme.TextPrimary
import com.motioncapture.app.ui.theme.ToggleTrackOff

@Composable
fun SettingsScreen(
    state: UiState,
    viewModel: MainViewModel,
) {
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    val settings = state.settings

    BackHandler { viewModel.navigate(Screen.CAMERA) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun applyNotification(value: Boolean) {
        viewModel.setNotifications(value)
        if (value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.navigate(Screen.CAMERA) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SystemBlue,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Settings",
                color = TextPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(24.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            SectionHeader("DETECTION")
            SettingsGroup {
                SettingsRow(
                    label = "Detection Sensitivity",
                    value = settings.sensitivity.label,
                    onClick = { dialog = SettingsDialog.SENSITIVITY },
                )
                RowDividerLine()
                SettingsRow(
                    label = "Detect Only People",
                    value = if (settings.peopleOnly) "On" else "Off",
                    onClick = { dialog = SettingsDialog.PEOPLE_ONLY },
                )
            }

            Spacer(Modifier.height(28.dp))

            SectionHeader("CAPTURE")
            SettingsGroup {
                SettingsRow(
                    label = "Capture Mode",
                    value = settings.captureMode.label,
                    onClick = { dialog = SettingsDialog.CAPTURE_MODE },
                )
                RowDividerLine()
                SettingsRow(
                    label = "Capture Burst",
                    value = burstLabel(settings.burstCount),
                    onClick = { dialog = SettingsDialog.BURST },
                )
                RowDividerLine()
                SettingsRow(
                    label = "Save To",
                    value = settings.saveTo.label,
                    onClick = { dialog = SettingsDialog.SAVE_TO },
                )
            }

            Spacer(Modifier.height(28.dp))

            SectionHeader("GENERAL")
            SettingsGroup {
                SettingsRow(
                    label = "Notifications",
                    value = "",
                    onClick = null,
                    trailing = {
                        IOSToggle(
                            checked = settings.notifications,
                            onCheckedChange = { applyNotification(it) },
                        )
                    },
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    when (dialog) {
        SettingsDialog.CAPTURE_MODE -> OptionDialog(
            title = "Capture Mode",
            options = CaptureMode.entries.map { it.label },
            selected = settings.captureMode.label,
            onSelect = { label ->
                CaptureMode.entries.firstOrNull { it.label == label }?.let {
                    viewModel.setCaptureMode(it)
                }
            },
            onDismiss = { dialog = null },
        )

        SettingsDialog.SENSITIVITY -> OptionDialog(
            title = "Detection Sensitivity",
            options = Sensitivity.entries.map { it.label },
            selected = settings.sensitivity.label,
            onSelect = { label ->
                Sensitivity.entries.firstOrNull { it.label == label }?.let {
                    viewModel.setSensitivity(it)
                }
            },
            onDismiss = { dialog = null },
        )

        SettingsDialog.PEOPLE_ONLY -> OptionDialog(
            title = "Detect Only People",
            options = listOf("On", "Off"),
            selected = if (settings.peopleOnly) "On" else "Off",
            onSelect = { option -> viewModel.setPeopleOnly(option == "On") },
            onDismiss = { dialog = null },
        )

        SettingsDialog.BURST -> OptionDialog(
            title = "Capture Burst",
            options = listOf(1, 2, 3, 5).map { burstLabel(it) },
            selected = burstLabel(settings.burstCount),
            onSelect = { label ->
                listOf(1, 2, 3, 5).firstOrNull { burstLabel(it) == label }?.let {
                    viewModel.setBurstCount(it)
                }
            },
            onDismiss = { dialog = null },
        )

        SettingsDialog.SAVE_TO -> OptionDialog(
            title = "Save To",
            options = SaveDestination.entries.map { it.label },
            selected = settings.saveTo.label,
            onSelect = { label ->
                SaveDestination.entries.firstOrNull { it.label == label }?.let {
                    viewModel.setSaveTo(it)
                }
            },
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}

private enum class SettingsDialog { CAPTURE_MODE, SENSITIVITY, PEOPLE_ONLY, BURST, SAVE_TO }

private fun burstLabel(count: Int): String = if (count == 1) "1 photo" else "$count photos"

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = LabelGray,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CellBackground),
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(horizontal = 16.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
        )
        if (trailing != null) {
            trailing()
        } else {
            Text(
                text = value,
                color = LabelGray,
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun RowDividerLine() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = RowDivider,
        thickness = 0.5.dp,
    )
}

@Composable
private fun IOSToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val knobOffset by animateDpAsState(if (checked) 22.dp else 2.dp)
    Box(
        modifier = Modifier
            .width(51.dp)
            .height(31.dp)
            .clip(CircleShape)
            .background(if (checked) SystemBlue else ToggleTrackOff)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = knobOffset)
                .size(27.dp)
                .background(Color.White, CircleShape),
        )
    }
}

@Composable
private fun OptionDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = SystemBlue,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = option,
                            color = TextPrimary,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SystemBlue)
            }
        },
    )
}
