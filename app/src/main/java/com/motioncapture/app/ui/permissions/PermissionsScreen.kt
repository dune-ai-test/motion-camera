package com.motioncapture.app.ui.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motioncapture.app.ui.theme.LabelGray
import com.motioncapture.app.ui.theme.SystemBlue
import com.motioncapture.app.ui.theme.TextPrimary

@Composable
fun PermissionsScreen(
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(Color(0xFFF2F2F7), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = SystemBlue,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Motion Capture",
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Motion Capture needs camera access to detect objects and save photos when motion is found.",
            color = LabelGray,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(280.dp),
        )

        Spacer(Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
                .height(54.dp)
                .background(SystemBlue, RoundedCornerShape(27.dp))
                .clickable(onClick = onAllow),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Allow Camera Access",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Not Now",
            color = LabelGray,
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .padding(12.dp)
                .clickable(onClick = onDeny),
        )
    }
}
