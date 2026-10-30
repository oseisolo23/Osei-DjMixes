package com.osei.djmixer.ui.components

import com.osei.djmixer.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView

@Composable
fun FrostedPlayer(
    currentMedia: MediaItem?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    queue: List<MediaItem>,
    sleepTimerRemaining: Long?,
    playbackSpeed: Float,
    onSetPlaybackSpeed: (Float) -> Unit,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    controller: Player?,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onQueueItemClick: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onSetSleepTimer: (Long?) -> Unit,
    orientationLock: String = "auto",
    onSetOrientationLock: (String) -> Unit = {},
    videoContinuousLooping: Boolean = true,
    onToggleVideoContinuousLooping: () -> Unit = {},
    onToggleFullScreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (currentMedia == null) return
    var showQueue by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    
    val isVideoSource = currentMedia.mediaMetadata.extras?.getBoolean("is_video") == true
    var isVideoEnabled by remember(currentMedia.mediaId) { mutableStateOf(isVideoSource) }

    var previousMediaForFade by remember { mutableStateOf<MediaItem?>(null) }
    var previousIsVideo by remember { mutableStateOf(false) }
    val fadeAlpha = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(currentMedia.mediaId) {
        if (previousMediaForFade != null && previousMediaForFade?.mediaId != currentMedia.mediaId) {
            fadeAlpha.snapTo(1f)
            fadeAlpha.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 650)
            )
        }
        previousMediaForFade = currentMedia
        previousIsVideo = isVideoSource
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, GlassBorder, RoundedCornerShape(32.dp)),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isVideoSource) {
                    IconButton(onClick = { isVideoEnabled = !isVideoEnabled }) {
                        Icon(
                            if (isVideoEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Video",
                            tint = if (isVideoEnabled) CyberPink else Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    var showTimerMenu by remember { mutableStateOf(false) }
                    var showSpeedMenu by remember { mutableStateOf(false) }

                    // Orientation Lock Toggle Shortcut
                    IconButton(onClick = {
                        val nextMode = when (orientationLock) {
                            "auto" -> "portrait"
                            "portrait" -> "landscape"
                            else -> "auto"
                        }
                        onSetOrientationLock(nextMode)
                    }) {
                        val icon = when (orientationLock) {
                            "portrait" -> Icons.Default.StayCurrentPortrait
                            "landscape" -> Icons.Default.StayCurrentLandscape
                            else -> Icons.Default.ScreenRotation
                        }
                        Icon(
                            icon,
                            contentDescription = "Orientation Mode",
                            tint = if (orientationLock != "auto") CyberPink else Color.White
                        )
                    }

                    if (orientationLock != "auto") {
                        Text(
                            text = if (orientationLock == "portrait") "Port" else "Land",
                            color = CyberPink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }

                    IconButton(onClick = { showSpeedMenu = true }) {
                        Icon(
                            Icons.Default.SlowMotionVideo,
                            contentDescription = "Playback Speed",
                            tint = if (playbackSpeed != 1.0f) CyberPink else Color.White
                        )
                    }

                    if (showSpeedMenu) {
                        PlaybackSpeedDialog(
                            playbackSpeed = playbackSpeed,
                            onSetSpeed = onSetPlaybackSpeed,
                            onDismiss = { showSpeedMenu = false }
                        )
                    }

                    if (playbackSpeed != 1.0f) {
                        Text(
                            text = "${playbackSpeed}x",
                            color = CyberPink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }

                    IconButton(onClick = { showTimerMenu = true }) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = "Sleep Timer",
                            tint = if (sleepTimerRemaining != null) CyberPink else Color.White
                        )
                    }
                    
                    if (showTimerMenu) {
                        SleepTimerDialog(
                            remainingMs = sleepTimerRemaining,
                            onSetTimer = onSetSleepTimer,
                            onDismiss = { showTimerMenu = false }
                        )
                    }

                    if (sleepTimerRemaining != null) {
                        Text(
                            text = formatTime(sleepTimerRemaining),
                            color = CyberPink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }

                    IconButton(onClick = { showQueue = !showQueue }) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Queue",
                            tint = if (showQueue) CyberPink else Color.White
                        )
                    }

                    // Equalizer Button
                    IconButton(
                        onClick = { showEqualizer = true },
                        modifier = Modifier.testTag("frosted_player_equalizer_button")
                    ) {
                        Icon(
                            Icons.Default.Equalizer,
                            contentDescription = "Equalizer",
                            tint = CyberPink
                        )
                    }

                    // Immersive Fullscreen Button for any Media Source (Video / Audio)
                    IconButton(
                        onClick = onToggleFullScreen,
                        modifier = Modifier.testTag("frosted_player_fullscreen_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Launch,
                            contentDescription = "Open Full Player",
                            tint = Color.White
                        )
                    }
                }
            }

            if (showQueue) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    itemsIndexed(queue) { index, item ->
                        val isCurrent = item.mediaId == currentMedia.mediaId
                        ListItem(
                            headlineContent = {
                                Text(
                                    item.mediaMetadata.title?.toString() ?: "Unknown",
                                    color = if (isCurrent) CyberPink else Color.White
                                )
                            },
                            supportingContent = {
                                Text(
                                    item.mediaMetadata.artist?.toString() ?: "Unknown",
                                    color = Slate400
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (index > 0) {
                                        IconButton(onClick = { onMoveQueueItem(index, index - 1) }) {
                                            Icon(Icons.Default.ArrowUpward, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    if (index < queue.size - 1) {
                                        IconButton(onClick = { onMoveQueueItem(index, index + 1) }) {
                                            Icon(Icons.Default.ArrowDownward, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    IconButton(onClick = { onRemoveQueueItem(index) }) {
                                        Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                                    }
                                }
                            },
                            modifier = Modifier.clickable { onQueueItemClick(index) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            } else {
                // Media Content Area
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(CyberPurple),
                    contentAlignment = Alignment.Center
                ) {
                    val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                    val heightPx = with(LocalDensity.current) { maxHeight.toPx() }

                    var scale by remember(currentMedia.mediaId) { mutableStateOf(1f) }
                    var offsetX by remember(currentMedia.mediaId) { mutableStateOf(0f) }
                    var offsetY by remember(currentMedia.mediaId) { mutableStateOf(0f) }

                    val modifierWithGestures = if (isVideoEnabled) {
                        Modifier
                            .fillMaxSize()
                            .pointerInput(currentMedia.mediaId) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                                    scale = newScale
                                    
                                    val maxPanX = (newScale - 1f) * widthPx / 2f
                                    val maxPanY = (newScale - 1f) * heightPx / 2f
                                    offsetX = (offsetX + pan.x * newScale).coerceIn(-maxPanX, maxPanX)
                                    offsetY = (offsetY + pan.y * newScale).coerceIn(-maxPanY, maxPanY)
                                }
                            }
                            .pointerInput(currentMedia.mediaId) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                )
                            }
                    } else {
                        Modifier.fillMaxSize()
                    }

                    Box(modifier = modifierWithGestures) {
                        if (isVideoEnabled && controller != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = 1f - fadeAlpha.value
                                    }
                            ) {
                                AndroidView(
                                    factory = { context ->
                                        PlayerView(context).apply {
                                            useController = false // Use our custom controls
                                        }
                                    },
                                    update = { view ->
                                        if (view.player != controller) {
                                            view.player = controller
                                        }
                                    },
                                    onRelease = { view ->
                                        view.player = null
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = offsetX
                                            translationY = offsetY
                                        }
                                )

                                // Overlay: Video Name, Zoom status & Quick Info
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = currentMedia.mediaMetadata.title?.toString() ?: "Unknown Video",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        if (scale > 1f) {
                                            Text(
                                                text = "Zoom: %.2fx (Double-tap to reset)".format(scale),
                                                color = CyberPink,
                                                fontSize = 10.sp
                                            )
                                        } else {
                                            Text(
                                                text = "Pinch to zoom / double-tap",
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val orientText = when (orientationLock) {
                                            "portrait" -> "Locked Port"
                                            "landscape" -> "Locked Land"
                                            else -> "Auto-Rotate"
                                        }
                                        
                                        // Clickable Orientation Lock
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.12f))
                                                .clickable {
                                                    val nextMode = when (orientationLock) {
                                                        "auto" -> "portrait"
                                                        "portrait" -> "landscape"
                                                        else -> "auto"
                                                    }
                                                    onSetOrientationLock(nextMode)
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = when (orientationLock) {
                                                    "portrait" -> Icons.Default.StayCurrentPortrait
                                                    "landscape" -> Icons.Default.StayCurrentLandscape
                                                    else -> Icons.Default.ScreenRotation
                                                },
                                                contentDescription = null,
                                                tint = CyberPink,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = orientText,
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        // Continuous video Playlist looping quick toggle
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (videoContinuousLooping) CyberPink.copy(alpha = 0.2f)
                                                    else Color.White.copy(alpha = 0.12f)
                                                )
                                                .clickable {
                                                    onToggleVideoContinuousLooping()
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .testTag("video_overlay_loop_toggle")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Repeat,
                                                contentDescription = "Loop Playlist Toggle",
                                                tint = if (videoContinuousLooping) CyberPink else Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (videoContinuousLooping) "Loop On" else "Loop Off",
                                                color = if (videoContinuousLooping) CyberPink else Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        // Immersive Fullscreen toggle button
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CyberPink.copy(alpha = 0.2f))
                                                .clickable {
                                                    onToggleFullScreen()
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .testTag("video_overlay_fullscreen_toggle")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fullscreen,
                                                contentDescription = "Toggle Fullscreen",
                                                tint = CyberPink,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Fullscreen",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            AsyncImage(
                                model = currentMedia.mediaMetadata.artworkUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = 1f - fadeAlpha.value
                                    },
                                contentScale = ContentScale.Crop
                            )
                            if (!isVideoSource) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = CyberPink.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .size(64.dp)
                                        .graphicsLayer {
                                            alpha = 1f - fadeAlpha.value
                                        }
                                )
                            }
                        }

                        // Previous media cross-fade overlay
                        if (fadeAlpha.value > 0f && previousMediaForFade != null) {
                            val prevMedia = previousMediaForFade!!
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = fadeAlpha.value
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (prevMedia.mediaMetadata.artworkUri != null) {
                                    AsyncImage(
                                        model = prevMedia.mediaMetadata.artworkUri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(CyberPurple),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = CyberPink.copy(alpha = 0.5f),
                                            modifier = Modifier.size(64.dp)
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.4f))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = prevMedia.mediaMetadata.title?.toString() ?: "Track",
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Transitioning...",
                                            color = CyberPink,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Metadata with HD Quality badge indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        currentMedia.mediaMetadata.title?.toString() ?: "Unknown",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val prefs = remember(context) { context.getSharedPreferences("dj_mixer_settings", android.content.Context.MODE_PRIVATE) }
                    val isHd = remember(prefs) { prefs.getString("stream_quality", "hd") == "hd" }
                    
                    if (isHd) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CyberPink.copy(alpha = 0.15f))
                                .border(1.dp, CyberPink.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "HD",
                                color = CyberPink,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    currentMedia.mediaMetadata.artist?.toString() ?: "Unknown Artist",
                    color = Slate400,
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                WaveformVisualizer(
                    isPlaying = isPlaying,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Seek Bar
            val sliderValue = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
            var localSliderValue by remember(sliderValue) { mutableStateOf(sliderValue) }
            val isDragging = remember { mutableStateOf(false) }

            Slider(
                value = if (isDragging.value) localSliderValue else sliderValue,
                onValueChange = {
                    isDragging.value = true
                    localSliderValue = it
                },
                onValueChangeFinished = {
                    isDragging.value = false
                    onSeek((localSliderValue * duration).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = CyberPink,
                    activeTrackColor = CyberPink,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(currentPosition), color = Slate400, fontSize = 12.sp)
                Text(formatTime(duration), color = Slate400, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleModeEnabled) CyberPink else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }

                Surface(
                    onClick = onToggle,
                    color = CyberPink,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null,
                            tint = CyberDark,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }

                IconButton(onClick = onToggleRepeat) {
                    val icon = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                        Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                        else -> Icons.Default.Repeat
                    }
                    val tint = if (repeatMode != Player.REPEAT_MODE_OFF) CyberPink else Color.White.copy(alpha = 0.6f)
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat Mode",
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        
        if (showEqualizer) {
            EqualizerDialog(
                onDismiss = { showEqualizer = false }
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
