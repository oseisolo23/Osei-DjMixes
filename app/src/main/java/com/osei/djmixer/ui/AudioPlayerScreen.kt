package com.osei.djmixer.ui

import com.osei.djmixer.ui.theme.*
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity

@Composable
fun AudioPlayerScreen(
    player: Player,
    vttUrl: String? = "asset:///captions.vtt",
    sleepTimerRemaining: Long? = null,
    onSetSleepTimer: (Long?) -> Unit = {},
    onSetAlarm: (Long, String) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    val context = LocalContext.current
    BackHandler { onBack() }
    
    var isPlayingState by remember { mutableStateOf(player.isPlaying) }
    var currentPosition by remember { mutableStateOf(player.currentPosition) }
    var duration by remember { mutableStateOf(player.duration) }
        
    val currentMediaItem = player.currentMediaItem
    val title = currentMediaItem?.mediaMetadata?.title?.toString() ?: "No Media Playing"
    val artist = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown Artist"
    val album = currentMediaItem?.mediaMetadata?.albumTitle?.toString() ?: ""
    val artworkUri = currentMediaItem?.mediaMetadata?.artworkUri
    
    // Smooth progress bar animation
    val progressAnim by animateFloatAsState(
        targetValue = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
        animationSpec = tween(durationMillis = 100, easing = LinearEasing),
        label = "progressAnim"
    )

    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
            }
        }
        player.addListener(listener)
        while (true) {
            currentPosition = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            isPlayingState = player.isPlaying
            delay(100) // update frequently for smooth slider
        }
    }
    
    // Rotating artwork animation
    val rotationState = remember { Animatable(0f) }
    LaunchedEffect(isPlayingState) {
        if (isPlayingState) {
            while (true) {
                rotationState.animateTo(
                    targetValue = rotationState.value + 360f,
                    animationSpec = tween(durationMillis = 15000, easing = LinearEasing)
                )
            }
        } else {
            rotationState.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                   
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Now Playing",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    AnimatedContent(
                        targetState = artist,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(500)) + slideInVertically { height -> height }) togetherWith
                            (fadeOut(animationSpec = tween(500)) + slideOutVertically { height -> -height })
                        },
                        label = "artist_anim"
                    ) { targetArtist ->
                        Text(
                            text = targetArtist,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                   
                IconButton(onClick = { /* menu */ }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))

            // Artwork + Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                // The visualizer as background
                YellowWaveformVisualizer(
                    isPlaying = isPlayingState,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Rotating Artwork with crossfade
                AnimatedContent(
                    targetState = artworkUri,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(800)) togetherWith fadeOut(animationSpec = tween(800))
                    },
                    label = "artwork_anim"
                ) { targetArtworkUri ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.6f)
                            .clip(CircleShape)
                            .background(Color(0xFF222222))
                            .graphicsLayer { rotationZ = rotationState.value }
                    ) {
                        if (targetArtworkUri != null) {
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(context)
                                    .data(targetArtworkUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Album Artwork",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Album Artwork",
                                tint = Color.Gray,
                                modifier = Modifier.fillMaxSize().padding(48.dp)
                            )
                        }
                        // Inner circle to make it look like a vinyl record
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.background)
                                .border(1.dp, Color.DarkGray, CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Title and Heart
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(500)) + slideInVertically { height -> height }) togetherWith
                            (fadeOut(animationSpec = tween(500)) + slideOutVertically { height -> -height })
                        },
                        label = "title_anim"
                    ) { targetTitle ->
                        Text(
                            text = targetTitle,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    AnimatedContent(
                        targetState = album,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(500)) + slideInHorizontally { width -> width }) togetherWith
                            (fadeOut(animationSpec = tween(500)) + slideOutHorizontally { width -> -width })
                        },
                        label = "album_anim"
                    ) { targetAlbum ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (targetAlbum.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = targetAlbum,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = "High Quality",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                IconButton(onClick = { /* toggle favorite */ }) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = Color(0xFFFFB300)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTimeMs(currentPosition),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                CustomAudioSlider(
                    progress = progressAnim,
                    onProgressChange = { newVal ->
                        if (duration > 0) {
                            player.seekTo((newVal * duration).toLong())
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )
                Text(
                    text = formatTimeMs(duration),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* shuffle */ }) {
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
                IconButton(onClick = { 
                    player.seekToPreviousMediaItem()
                    player.play()
                }) {
                    Icon(Icons.Default.SkipPrevious, "Previous", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(32.dp))
                }
                
                // Play/Pause Button
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFFB300),
                    modifier = Modifier
                        .size(64.dp)
                        .clickable {
                            if (isPlayingState) player.pause() else player.play()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = isPlayingState,
                            transitionSpec = {
                                scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut()
                            },
                            label = "play_pause_anim"
                        ) { isPlaying ->
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                
                IconButton(onClick = { 
                    player.seekToNextMediaItem()
                    player.play()
                }) {
                    Icon(Icons.Default.SkipNext, "Next", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = { /* repeat */ }) {
                    Icon(Icons.Default.Repeat, "Repeat", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Bottom Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* playlist */ }) {
                    Icon(Icons.Outlined.QueueMusic, "Playlist", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
                IconButton(onClick = { /* eq */ }) {
                    Icon(Icons.Outlined.Tune, "EQ", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
                IconButton(onClick = { /* download */ }) {
                    Icon(Icons.Outlined.FileDownload, "Download", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
                IconButton(onClick = { /* share */ }) {
                    Icon(Icons.Outlined.Share, "Share", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
                IconButton(onClick = { /* menu */ }) {
                    Icon(Icons.Outlined.Menu, "Menu", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
fun YellowWaveformVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 30
) {
    val realTimeAmplitudes by com.osei.djmixer.playback.AudioVisualizerManager.instance.amplitudes.collectAsState()
    val hasRealTimeData = remember(realTimeAmplitudes, isPlaying) {
        realTimeAmplitudes.isNotEmpty() && isPlaying
    }
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    
    val simulatedHeights = (0 until barCount).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = if (isPlaying) 0.6f + (index % 3) * 0.15f else 0.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 400 + (index % 5) * 80,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
    }

    val finalBarHeights = (0 until barCount).map { index ->
        val targetHeight = if (hasRealTimeData) {
            realTimeAmplitudes.getOrElse(index) { 0.1f }
        } else {
            simulatedHeights[index].value
        }
        animateFloatAsState(
            targetValue = targetHeight,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "smooth_bar_$index"
        )
    }

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barWidth = (canvasWidth / barCount) * 0.6f
        val gap = (canvasWidth - (barCount * barWidth)) / (barCount - 1)
        
        val gapTop = canvasHeight * 0.65f
        val gapBottom = gapTop + canvasHeight * 0.05f
        
        for (i in 0 until barCount) {
            val barHeightFactor = finalBarHeights[i].value
            val barHeight = canvasHeight * barHeightFactor
            val x = i * (barWidth + gap)
            val yTop = (canvasHeight - barHeight) / 2
            val yBottom = yTop + barHeight
            
            if (yBottom <= gapTop) {
                drawRoundRect(
                    color = Color(0xFFFFB300),
                    topLeft = Offset(x, yTop),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                )
            } else if (yTop >= gapBottom) {
                drawRoundRect(
                    color = Color(0xFFFFB300),
                    topLeft = Offset(x, yTop),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                )
            } else {
                val topHeight = (gapTop - yTop).coerceAtLeast(barWidth)
                drawRoundRect(
                    color = Color(0xFFFFB300),
                    topLeft = Offset(x, yTop),
                    size = Size(barWidth, topHeight),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                )
                
                val bottomHeight = (yBottom - gapBottom).coerceAtLeast(0f)
                if (bottomHeight > barWidth / 2) {
                    drawRoundRect(
                        color = Color(0xFFFFB300),
                        topLeft = Offset(x, gapBottom),
                        size = Size(barWidth, bottomHeight),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                    )
                }
            }
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun CustomAudioSlider(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var width by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    
    Box(
        modifier = modifier
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (width > 0) onProgressChange((offset.x / width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    if (width > 0) onProgressChange((change.position.x / width).coerceIn(0f, 1f))
                }
            }
            .onSizeChanged { width = it.width.toFloat() },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFB300))
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(14.dp)
                .clip(RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp))
                .background(Color(0xFFFFB300))
        )
        if (width > 0) {
            Box(
                modifier = Modifier
                    .offset(x = with(density) { (width * progress.coerceIn(0f, 1f)).toDp() - 2.dp })
                    .width(4.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFFFB300))
            )
        }
    }
}
