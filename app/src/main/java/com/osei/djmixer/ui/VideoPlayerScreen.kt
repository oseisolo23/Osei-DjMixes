package com.osei.djmixer.ui

import com.osei.djmixer.ui.theme.*

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
fun VideoPlayerScreen(
    player: Player,
    vttUrl: String? = "asset:///captions.vtt",
    isInPip: Boolean = false,
    orientationLock: String = "auto",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    // Immersive display & Orientation locking
    DisposableEffect(isInPip, orientationLock) {
        val window = activity?.window
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        
        if (!isInPip) {
            // Lock screen orientation based on user preference
            activity?.requestedOrientation = when (orientationLock) {
                "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            }
            
            if (window != null) {
                val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        
        onDispose {
            if (!isInPip) {
                // Restore original settings
                activity?.requestedOrientation = originalOrientation
                if (window != null) {
                    val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    // Controls HUD State
    var showControls by remember { mutableStateOf(true) }
    val effectiveShowControls = if (isInPip) false else showControls
    var isPlayingState by remember { mutableStateOf(player.isPlaying) }
    var currentPosition by remember { mutableStateOf(player.currentPosition) }
    var duration by remember { mutableStateOf(player.duration) }
    var isMuted by remember { mutableStateOf(player.volume == 0f) }
    var captionsEnabled by remember { mutableStateOf(true) }
    
    // Resize Modes: Fit vs Fill/Zoom
    var resizeModeState by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Media metadata
    val currentMediaItem = player.currentMediaItem
    val title = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Spiritual Presentation"
    val artist = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Artist"

    // Automated HUD fading after 3.5 seconds
    LaunchedEffect(showControls, isPlayingState) {
        if (showControls && isPlayingState) {
            delay(3500)
            showControls = false
        }
    }

    // Keep state and playback tracker in active sync
    LaunchedEffect(player) {
        while (true) {
            currentPosition = player.currentPosition
            duration = player.duration
            isPlayingState = player.isPlaying
            delay(250)
        }
    }

    var isBuffering by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    
    // Listener for general playback status updates
    DisposableEffect(player) {
         val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    playbackError = null
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackError = "Playback Error: ${error.message}"
                // Auto-reconnect / retry logic for live streams
                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED || 
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) {
                    player.prepare()
                    player.play()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    // Enable/disable text/subtitle tracks based on toggle state
    LaunchedEffect(player, captionsEnabled) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !captionsEnabled)
            .build()
    }

    // Dynamically inject custom subtitles if provided and not already present on active MediaItem
    LaunchedEffect(player, vttUrl) {
        if (!vttUrl.isNullOrEmpty()) {
            val currentItem = player.currentMediaItem
            if (currentItem != null && currentItem.localConfiguration?.subtitleConfigurations?.isEmpty() != false) {
                val currentPos = player.currentPosition
                val isPlaying = player.isPlaying
                val subtitleConfig = androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(vttUrl))
                    .setMimeType(androidx.media3.common.MimeTypes.TEXT_VTT)
                    .setLanguage("en")
                    .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                    .build()
                
                val updatedMediaItem = currentItem.buildUpon()
                    .setSubtitleConfigurations(listOf(subtitleConfig))
                    .build()
                
                player.setMediaItem(updatedMediaItem, currentPos)
                player.prepare()
                if (isPlaying) {
                    player.play()
                }
            }
        }
    }

    // Gesture indicator visuals
    var gestureOverlayText by remember { mutableStateOf<String?>(null) }
    var gestureOverlayTimer by remember { mutableStateOf(0) }
    LaunchedEffect(gestureOverlayText, gestureOverlayTimer) {
        if (gestureOverlayText != null) {
            delay(800)
            gestureOverlayText = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    },
                    onDoubleTap = { pressOffset ->
                        // Double tap on right side of screen → forward 10s
                        // Double tap on left side of screen → rewind 10s
                        val screenWidth = size.width
                        if (pressOffset.x > screenWidth / 2) {
                            val newPos = (player.currentPosition + 10000).coerceAtMost(player.duration)
                            player.seekTo(newPos)
                            currentPosition = newPos
                            gestureOverlayText = "+10s ⏩"
                        } else {
                            val newPos = (player.currentPosition - 10000).coerceAtLeast(0)
                            player.seekTo(newPos)
                            currentPosition = newPos
                            gestureOverlayText = "⏪ -10s"
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var dragDirection: VideoPlayerDragType? = null
                var startX = 0f
                var startY = 0f
                var accumulatedDragX = 0f
                var accumulatedDragY = 0f
                
                var initialPos = 0L
                var initialVol = 0f
                var maxVol = 1f
                var initialBright = 0.5f
                
                val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
                
                detectDragGestures(
                    onDragStart = { offset ->
                        dragDirection = null
                        startX = offset.x
                        startY = offset.y
                        accumulatedDragX = 0f
                        accumulatedDragY = 0f
                        
                        initialPos = player.currentPosition
                        
                        if (audioManager != null) {
                            initialVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()
                            maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).toFloat().coerceAtLeast(1f)
                        }
                        
                        val lp = activity?.window?.attributes
                        val currentAppBright = lp?.screenBrightness ?: -1f
                        initialBright = if (currentAppBright < 0f) {
                            try {
                                android.provider.Settings.System.getInt(
                                    context.contentResolver,
                                    android.provider.Settings.System.SCREEN_BRIGHTNESS
                                ) / 255f
                            } catch (e: Exception) {
                                0.5f
                            }
                        } else {
                            currentAppBright
                        }
                    },
                    onDragEnd = {
                        dragDirection = null
                        gestureOverlayTimer++
                    },
                    onDragCancel = {
                        dragDirection = null
                        gestureOverlayTimer++
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragX += dragAmount.x
                        accumulatedDragY += dragAmount.y
                        
                        if (dragDirection == null) {
                            // Threshold to confirm it is a drag. 15dp/px threshold
                            val threshold = 15f
                            if (kotlin.math.abs(accumulatedDragX) > threshold || kotlin.math.abs(accumulatedDragY) > threshold) {
                                if (kotlin.math.abs(accumulatedDragX) > kotlin.math.abs(accumulatedDragY)) {
                                    dragDirection = VideoPlayerDragType.SEEK
                                } else {
                                    if (startX < size.width / 2) {
                                        dragDirection = VideoPlayerDragType.BRIGHTNESS
                                    } else {
                                        dragDirection = VideoPlayerDragType.VOLUME
                                    }
                                }
                            }
                        }
                        
                        when (dragDirection) {
                            VideoPlayerDragType.SEEK -> {
                                val screenWidth = size.width.toFloat().coerceAtLeast(1f)
                                // standard seek range is 90 seconds from edge to edge
                                val seekDelta = (accumulatedDragX / screenWidth) * 90000L
                                val targetPos = (initialPos + seekDelta).toLong().coerceIn(0L, player.duration)
                                player.seekTo(targetPos)
                                currentPosition = targetPos
                                
                                val formattedTime = formatTime(targetPos)
                                val durationText = formatTime(player.duration)
                                val sign = if (seekDelta >= 0) "+" else "-"
                                val absDeltaSecs = kotlin.math.abs(seekDelta) / 1000L
                                gestureOverlayText = "Seeking: $formattedTime / $durationText ($sign${absDeltaSecs}s) ⏩"
                            }
                            VideoPlayerDragType.VOLUME -> {
                                val screenHeight = size.height.toFloat().coerceAtLeast(1f)
                                val volumeDelta = -(accumulatedDragY / screenHeight) * maxVol
                                val targetVol = (initialVol + volumeDelta).coerceIn(0f, maxVol)
                                audioManager?.setStreamVolume(
                                    android.media.AudioManager.STREAM_MUSIC,
                                    targetVol.toInt(),
                                    0
                                )
                                val percent = ((targetVol / maxVol) * 100).toInt()
                                val icon = if (percent == 0) "🔇" else if (percent < 50) "🔉" else "🔊"
                                gestureOverlayText = "Volume: $percent% $icon"
                            }
                            VideoPlayerDragType.BRIGHTNESS -> {
                                val screenHeight = size.height.toFloat().coerceAtLeast(1f)
                                val brightnessDelta = -(accumulatedDragY / screenHeight) * 1.0f
                                val targetBright = (initialBright + brightnessDelta).coerceIn(0.01f, 1.0f)
                                
                                activity?.runOnUiThread {
                                    val lp = activity.window.attributes
                                    lp.screenBrightness = targetBright
                                    activity.window.attributes = lp
                                }
                                
                                val percent = (targetBright * 100).toInt()
                                gestureOverlayText = "Brightness: $percent% 🔆"
                            }
                            null -> {}
                        }
                    }
                )
            }
    ) {
        // Raw Video Surface View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.useController = false  // Disable default Media3 controllers, use ours
                    this.resizeMode = resizeModeState
                }
            },
            update = { view ->
                view.resizeMode = resizeModeState
                if (view.player != player) {
                    view.player = player
                }
            },
            onRelease = { view ->
                view.player = null
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture seek alert / visual toast inside player
        gestureOverlayText?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = text,
                    color = CyberPink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Buffering Indicator
        if (isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CyberPink)
            }
        }
        
        // Error Display
        if (playbackError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = Color.Red,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = playbackError ?: "Unknown Error",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            player.prepare()
                            player.play()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberPink)
                    ) {
                        Text("Retry", color = Color.Black)
                    }
                }
            }
        }

        // --- Custom Fullscreen HUD Overlay ---
        AnimatedVisibility(
            visible = effectiveShowControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                
                // Top Gradient & Title Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit Fullscreen",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = artist,
                            color = Slate400,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }

                    // Picture in Picture Button
                    IconButton(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                activity?.enterPictureInPictureMode(
                                    android.app.PictureInPictureParams.Builder()
                                        .setAspectRatio(android.util.Rational(16, 9))
                                        .build()
                                )
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPicture,
                            contentDescription = "Enter PiP",
                            tint = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    // Mute / Unmute Button
                    IconButton(
                        onClick = {
                            if (isMuted) {
                                player.volume = 1f
                                isMuted = false
                            } else {
                                player.volume = 0f
                                isMuted = true
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Mute Toggle",
                            tint = if (isMuted) Color.Red else Color.White
                        )
                    }
                }

                // Center Action Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Skip Previous
                    if (player.hasPreviousMediaItem()) {
                        IconButton(
                            onClick = {
                                player.seekToPreviousMediaItem()
                                player.play()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Rewind 10s
                    IconButton(
                        onClick = {
                            val target = (player.currentPosition - 10000).coerceAtLeast(0)
                            player.seekTo(target)
                            currentPosition = target
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "-10 Seconds",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Large Play / Pause Toggle
                    IconButton(
                        onClick = {
                            if (isPlayingState) {
                                player.pause()
                            } else {
                                player.play()
                            }
                        },
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(CyberPink)
                    ) {
                        Icon(
                            imageVector = if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    // Fast Forward 10s
                    IconButton(
                        onClick = {
                            val target = (player.currentPosition + 10000).coerceAtMost(player.duration)
                            player.seekTo(target)
                            currentPosition = target
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "+10 Seconds",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Skip Next
                    if (player.hasNextMediaItem()) {
                        IconButton(
                            onClick = {
                                player.seekToNextMediaItem()
                                player.play()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                // Bottom Gradient Seek & Screen Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                ) {
                    // Progress Slider
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
                            player.seekTo((localSliderValue * duration).toLong())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = CyberPink,
                            activeTrackColor = CyberPink,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Timestamps & Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                            color = Slate400,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var audioLangIndex by remember { mutableIntStateOf(0) }
                            val audioLangs = listOf("", "en", "es", "fr", "de", "ja")
                            val currentAudioLang = audioLangs[audioLangIndex]

                            // Audio Track Toggle Button
                            Button(
                                onClick = {
                                    audioLangIndex = (audioLangIndex + 1) % audioLangs.size
                                    val newLang = audioLangs[audioLangIndex]
                                    val builder = player.trackSelectionParameters.buildUpon()
                                    if (newLang.isEmpty()) {
                                        builder.setPreferredAudioLanguage(null)
                                    } else {
                                        builder.setPreferredAudioLanguage(newLang)
                                    }
                                    player.trackSelectionParameters = builder.build()
                                    gestureOverlayText = if (newLang.isEmpty()) "Audio: Default" else "Audio: ${newLang.uppercase()}"
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (currentAudioLang.isNotEmpty()) CyberPink.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f)
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = "Audio Track",
                                    tint = if (currentAudioLang.isNotEmpty()) CyberPink else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (currentAudioLang.isEmpty()) "Audio" else currentAudioLang.uppercase(),
                                    color = if (currentAudioLang.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Subtitles/Captions Toggle Button
                            Button(
                                onClick = { captionsEnabled = !captionsEnabled },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (captionsEnabled) CyberPink.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f)
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("caption_toggle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = "Toggle Captions",
                                    tint = if (captionsEnabled) CyberPink else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (captionsEnabled) "CC On" else "CC Off",
                                    color = if (captionsEnabled) Color.White else Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Resize Mode Settings Selector Button
                            Button(
                                onClick = {
                                    resizeModeState = if (resizeModeState == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    } else {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.15f)
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (resizeModeState == AspectRatioFrameLayout.RESIZE_MODE_FIT) Icons.Default.AspectRatio else Icons.Default.CropFree,
                                    contentDescription = "Resize mode",
                                    tint = CyberPink,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (resizeModeState == AspectRatioFrameLayout.RESIZE_MODE_FIT) "Fit Screen" else "Zoom Fill",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Fullscreen Exit Action Button
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CyberPink.copy(alpha = 0.2f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FullscreenExit,
                                    contentDescription = "Exit Fullscreen",
                                    tint = CyberPink,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}

private enum class VideoPlayerDragType {
    SEEK, VOLUME, BRIGHTNESS
}
