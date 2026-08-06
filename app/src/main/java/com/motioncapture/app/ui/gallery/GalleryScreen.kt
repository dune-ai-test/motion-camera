package com.motioncapture.app.ui.gallery

import androidx.activity.compose.BackHandler
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.motioncapture.app.data.GalleryItem
import com.motioncapture.app.ui.MainViewModel
import com.motioncapture.app.ui.Screen
import com.motioncapture.app.ui.UiState
import com.motioncapture.app.ui.theme.LabelGray
import com.motioncapture.app.ui.theme.SystemBlue
import com.motioncapture.app.ui.theme.TextPrimary

@Composable
fun GalleryScreen(
    state: UiState,
    viewModel: MainViewModel,
) {
    val items by viewModel.galleryItems.collectAsStateWithLifecycle()
    var selectedItem by remember { mutableStateOf<GalleryItem?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshGallery() }

    BackHandler { viewModel.navigate(Screen.CAMERA) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding(),
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
                text = "Captures",
                color = TextPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${items.size} captures",
                color = LabelGray,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
            )
        }

        Spacer(Modifier.height(20.dp))

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Photo,
                        contentDescription = null,
                        tint = LabelGray,
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        text = "No captures yet",
                        color = LabelGray,
                        fontSize = 15.sp,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(items, key = { it.uri.toString() }) { item ->
                    if (item.isVideo) {
                        VideoTile(onClick = { selectedItem = item })
                    } else {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedItem = item },
                        )
                    }
                }
            }
        }
    }

    selectedItem?.let { item ->
        PhotoViewerDialog(item = item, onDismiss = { selectedItem = null })
    }
}

@Composable
private fun VideoTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(42.dp),
        )
    }
}

@Composable
private fun VideoPlayer(item: GalleryItem) {
    val context = LocalContext.current
    val videoView = remember { VideoView(context) }
    DisposableEffect(Unit) {
        onDispose { videoView.stopPlayback() }
    }
    AndroidView(
        factory = {
            videoView.apply {
                setVideoURI(item.uri)
                setOnPreparedListener { it.setVolume(0f, 0f); it.start() }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun PhotoViewerDialog(item: GalleryItem, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
        ) {
            if (item.isVideo) {
                VideoPlayer(item)
            } else {
                AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(22.dp)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
