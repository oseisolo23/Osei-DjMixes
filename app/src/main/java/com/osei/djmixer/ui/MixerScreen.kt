package com.osei.djmixer.ui

import android.net.Uri
import com.osei.djmixer.ui.analysis.AudioTrackAnalyzer
import com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.AutoDjMixMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.osei.djmixer.data.MediaItemEntity
import com.osei.djmixer.ui.theme.*
import java.io.File
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MixerScreen(
    viewModel: MixerViewModel,
    allMedia: List<MediaItemEntity>,
    onSettingsClick: () -> Unit,
    onListenWithHtml5: (File, String) -> Unit = { _, _ -> }
) {
    val deckAMedia by viewModel.deckAMedia.collectAsState()
    val deckBMedia by viewModel.deckBMedia.collectAsState()

    val isPlayingA by viewModel.isPlayingA.collectAsState()
    val isPlayingB by viewModel.isPlayingB.collectAsState()

    val positionA by viewModel.positionA.collectAsState()
    val positionB by viewModel.positionB.collectAsState()

    val durationA by viewModel.durationA.collectAsState()
    val durationB by viewModel.durationB.collectAsState()

    val volumeA by viewModel.volumeA.collectAsState()
    val volumeB by viewModel.volumeB.collectAsState()

    val speedA by viewModel.speedA.collectAsState()
    val speedB by viewModel.speedB.collectAsState()

    val crossfade by viewModel.crossfade.collectAsState()

    val bpmA by viewModel.bpmA.collectAsState(120)
    val bpmB by viewModel.bpmB.collectAsState(120)

    val currentMediaA = remember(deckAMedia) {
        deckAMedia?.let { media ->
            val safeUri = if (media.uri.startsWith("/")) {
                Uri.fromFile(File(media.uri)).toString()
            } else {
                media.uri
            }
            MediaItem.Builder()
                .setUri(safeUri)
                .setMediaId(media.id)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(media.title).build())
                .build()
        }
    }

    val currentMediaB = remember(deckBMedia) {
        deckBMedia?.let { media ->
            val safeUri = if (media.uri.startsWith("/")) {
                Uri.fromFile(File(media.uri)).toString()
            } else {
                media.uri
            }
            MediaItem.Builder()
                .setUri(safeUri)
                .setMediaId(media.id)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(media.title).build())
                .build()
        }
    }

    MixerScreenContent(
        currentMediaA = currentMediaA,
        currentMediaB = currentMediaB,
        isPlayingA = isPlayingA,
        isPlayingB = isPlayingB,
        progressA = if (durationA > 0) positionA.toFloat() / durationA.toFloat() else 0f,
        progressB = if (durationB > 0) positionB.toFloat() / durationB.toFloat() else 0f,
        durationA = durationA,
        durationB = durationB,
        playbackPositionA = positionA,
        playbackPositionB = positionB,
        volumeA = volumeA,
        volumeB = volumeB,
        pitchA = speedA,
        pitchB = speedB,
        tempoA = speedA,
        tempoB = speedB,
        crossfaderPosition = crossfade,
        onPlayPauseA = { viewModel.togglePlayA() },
        onPlayPauseB = { viewModel.togglePlayB() },
        onSeekA = { pos -> viewModel.seekA((pos * durationA).toLong()) },
        onSeekB = { pos -> viewModel.seekB((pos * durationB).toLong()) },
        onVolumeChangeA = { viewModel.setVolumeA(it) },
        onVolumeChangeB = { viewModel.setVolumeB(it) },
        onTempoChangeA = { viewModel.setSpeedA(it) },
        onTempoChangeB = { viewModel.setSpeedB(it) },
        onCrossfaderChange = { viewModel.setCrossfade(it) },
        onToggleSyncA = { viewModel.syncAtoB() },
        onToggleSyncB = { viewModel.syncBtoA() },
        onCueA = { viewModel.cueA() },
        onCueB = { viewModel.cueB() },
        allMedia = allMedia,
        onMediaSelectA = { media -> viewModel.loadToDeckA(media) },
        onMediaSelectB = { media -> viewModel.loadToDeckB(media) },
        onSettingsClick = onSettingsClick,
        viewModel = viewModel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixerScreenContent(
    currentMediaA: MediaItem?,
    currentMediaB: MediaItem?,
    isPlayingA: Boolean,
    isPlayingB: Boolean,
    progressA: Float,
    progressB: Float,
    durationA: Long,
    durationB: Long,
    playbackPositionA: Long,
    playbackPositionB: Long,
    volumeA: Float,
    volumeB: Float,
    pitchA: Float,
    pitchB: Float,
    tempoA: Float,
    tempoB: Float,
    crossfaderPosition: Float,
    onPlayPauseA: () -> Unit,
    onPlayPauseB: () -> Unit,
    onSeekA: (Float) -> Unit,
    onSeekB: (Float) -> Unit,
    onVolumeChangeA: (Float) -> Unit,
    onVolumeChangeB: (Float) -> Unit,
    onTempoChangeA: (Float) -> Unit,
    onTempoChangeB: (Float) -> Unit,
    onCrossfaderChange: (Float) -> Unit,
    onToggleSyncA: () -> Unit,
    onToggleSyncB: () -> Unit,
    onCueA: () -> Unit,
    onCueB: () -> Unit,
    allMedia: List<MediaItemEntity>,
    onMediaSelectA: (MediaItemEntity) -> Unit,
    onMediaSelectB: (MediaItemEntity) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: MixerViewModel
) {
    val bpmA by viewModel.bpmA.collectAsState(120)
    val keyA by viewModel.keyA.collectAsState()
    val bpmB by viewModel.bpmB.collectAsState(120)
    val keyB by viewModel.keyB.collectAsState()

    val analysisA by viewModel.trackAnalysisA.collectAsState()
    val analysisB by viewModel.trackAnalysisB.collectAsState()
    val deckEntityA by viewModel.deckAMedia.collectAsState()
    val deckEntityB by viewModel.deckBMedia.collectAsState()

    val keyTextA = remember(analysisA, keyA) {
        val key = analysisA?.musicalKey ?: keyA
        val camelot = AudioTrackAnalyzer.keyToCamelot(key)
        if (key != null && camelot != null) "$key ($camelot)" else (key ?: "--")
    }
    val energyTextA = analysisA?.energyLevel?.takeIf { it != "UNKNOWN" } ?: "--"

    val keyTextB = remember(analysisB, keyB) {
        val key = analysisB?.musicalKey ?: keyB
        val camelot = AudioTrackAnalyzer.keyToCamelot(key)
        if (key != null && camelot != null) "$key ($camelot)" else (key ?: "--")
    }
    val energyTextB = analysisB?.energyLevel?.takeIf { it != "UNKNOWN" } ?: "--"

    val pitchLockA by viewModel.pitchLockA.collectAsState()
    val pitchLockB by viewModel.pitchLockB.collectAsState()

    val trackWaveformA by viewModel.trackWaveformA.collectAsState()
    val trackWaveformB by viewModel.trackWaveformB.collectAsState()
    val isAnalyzingA by viewModel.isAnalyzingWaveformA.collectAsState()
    val isAnalyzingB by viewModel.isAnalyzingWaveformB.collectAsState()
    val liveWaveformA by viewModel.waveformHistoryA.collectAsState()
    val liveWaveformB by viewModel.waveformHistoryB.collectAsState()

    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDuration by viewModel.recordingDurationSeconds.collectAsState()

    val selectedTab by viewModel.selectedTab.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF020617)
                    )
                )
            )
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "DJ Mixer",
                        tint = CyberPink,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Osei DJ Mixer",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                // Recording Indicator if active
                if (isRecording) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Red.copy(alpha = 0.25f))
                            .border(1.dp, Color.Red, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.Red)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "REC (WAV) ${formatTime(recordingDuration * 1000)}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 1. Waveform Display (Deck A on top, Deck B on bottom)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                // Deck A Waveform
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF0A0F1D))
                ) {
                    if (deckEntityA?.isVideo == true) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = viewModel.playerA
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
                    }

                    RealAudioWaveformCanvas(
                        trackWaveform = trackWaveformA,
                        liveWaveform = liveWaveformA,
                        isAnalyzing = isAnalyzingA,
                        progress = progressA,
                        accentColor = CyberCyan,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (currentMediaA == null) {
                        Text(
                            "DECK A: LOAD TRACK",
                            color = CyberCyan.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                // Deck B Waveform
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF1D0A16))
                ) {
                    if (deckEntityB?.isVideo == true) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = viewModel.playerB
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
                    }

                    RealAudioWaveformCanvas(
                        trackWaveform = trackWaveformB,
                        liveWaveform = liveWaveformB,
                        isAnalyzing = isAnalyzingB,
                        progress = progressB,
                        accentColor = CyberPink,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (currentMediaB == null) {
                        Text(
                            "DECK B: LOAD TRACK",
                            color = CyberPink.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }

            // 2. Decks (Deck A & Deck B)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.38f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Deck A
                Deck(
                    title = "DECK A",
                    color = CyberCyan,
                    currentMedia = currentMediaA,
                    isPlaying = isPlayingA,
                    progress = progressA,
                    duration = durationA,
                    playbackPosition = playbackPositionA,
                    tempo = tempoA,
                    pitch = pitchA,
                    volume = volumeA,
                    isSyncEnabled = false,
                    bpm = bpmA,
                    key = keyTextA,
                    energy = energyTextA,
                    pitchLock = pitchLockA,
                    onPlayPause = onPlayPauseA,
                    onSeek = onSeekA,
                    onTempoChange = onTempoChangeA,
                    onVolumeChange = onVolumeChangeA,
                    onToggleSync = onToggleSyncA,
                    onTogglePitchLock = { viewModel.togglePitchLockA() },
                    onTapBpm = { viewModel.tapBpmA() },
                    onCue = onCueA,
                    modifier = Modifier.weight(1f)
                )

                // Deck B
                Deck(
                    title = "DECK B",
                    color = CyberPink,
                    currentMedia = currentMediaB,
                    isPlaying = isPlayingB,
                    progress = progressB,
                    duration = durationB,
                    playbackPosition = playbackPositionB,
                    tempo = tempoB,
                    pitch = pitchB,
                    volume = volumeB,
                    isSyncEnabled = false,
                    bpm = bpmB,
                    key = keyTextB,
                    energy = energyTextB,
                    pitchLock = pitchLockB,
                    onPlayPause = onPlayPauseB,
                    onSeek = onSeekB,
                    onTempoChange = onTempoChangeB,
                    onVolumeChange = onVolumeChangeB,
                    onToggleSync = onToggleSyncB,
                    onTogglePitchLock = { viewModel.togglePitchLockB() },
                    onTapBpm = { viewModel.tapBpmB() },
                    onCue = onCueB,
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Center Mixer Section (EQs, Filters, FX, Auto DJ, Emergency Stop, Crossfader)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.24f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                MixerBottomSection(
                    viewModel = viewModel,
                    crossfaderPosition = crossfaderPosition,
                    onCrossfaderChange = onCrossfaderChange
                )
            }

            // 4. Tabbed Performance & DJ Tray (Files, Sampler, Hot Cues, Loops, FX, Auto DJ)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.38f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                // Tab Bar
                ScrollableTabRow(
                    selectedTabIndex = listOf("LIBRARY", "SAMPLER", "HOT CUES", "LOOPS", "EFFECTS", "AUTO DJ").indexOf(selectedTab).coerceAtLeast(0),
                    edgePadding = 4.dp,
                    containerColor = Color(0xFF0F172A),
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth().height(32.dp)
                ) {
                    val tabs = listOf("LIBRARY", "SAMPLER", "HOT CUES", "LOOPS", "EFFECTS", "AUTO DJ")
                    tabs.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            text = { Text(tab, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                // Tab Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                ) {
                    when (selectedTab) {
                        "LIBRARY" -> {
                            LibraryBrowserTab(
                                allMedia = allMedia,
                                onMediaSelectA = onMediaSelectA,
                                onMediaSelectB = onMediaSelectB
                            )
                        }
                        "SAMPLER" -> {
                            SamplerTab(onPlay = { viewModel.playSample(it) })
                        }
                        "HOT CUES" -> {
                            HotCuesTab(viewModel = viewModel)
                        }
                        "LOOPS" -> {
                            LoopsTab(viewModel = viewModel)
                        }
                        "EFFECTS" -> {
                            EffectsTab(viewModel = viewModel)
                        }
                        "AUTO DJ" -> {
                            AutoDjTab(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RealAudioWaveformCanvas(
    trackWaveform: FloatArray?,
    liveWaveform: FloatArray,
    isAnalyzing: Boolean,
    progress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    if (isAnalyzing) {
        // Genuine loading state while analyzing real audio file
        val infiniteTransition = rememberInfiniteTransition(label = "waveform_scan")
        val scanOffset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "scan"
        )
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scanX = size.width * scanOffset
                drawLine(
                    color = accentColor.copy(alpha = 0.8f),
                    start = Offset(scanX, 0f),
                    end = Offset(scanX, size.height),
                    strokeWidth = 3f
                )
            }
            Text(
                "ANALYZING AUDIO WAVEFORM...",
                color = accentColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        return
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val playheadX = width / 2f

        if (trackWaveform != null && trackWaveform.isNotEmpty()) {
            val barWidth = 3.5f
            val numBarsOnScreen = (width / barWidth).toInt()
            val currentCenterBarIndex = (progress.coerceIn(0f, 1f) * trackWaveform.size).toInt()

            val startBarIndex = currentCenterBarIndex - (numBarsOnScreen / 2)
            val endBarIndex = currentCenterBarIndex + (numBarsOnScreen / 2)

            for (i in startBarIndex..endBarIndex) {
                if (i in trackWaveform.indices) {
                    val amplitude = trackWaveform[i].coerceIn(0.04f, 1.0f)
                    val barHeight = amplitude * (height * 0.85f)
                    val offsetFromCenter = i - currentCenterBarIndex
                    val x = playheadX + (offsetFromCenter * barWidth)

                    val barColor = if (i <= currentCenterBarIndex) {
                        accentColor
                    } else {
                        accentColor.copy(alpha = 0.35f)
                    }

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, centerY - barHeight / 2f),
                        size = Size(barWidth - 1f, barHeight),
                        cornerRadius = CornerRadius(1f, 1f)
                    )
                }
            }
        } else {
            // Draw real-time live peak amplitudes from DjAudioProcessor
            val barWidth = width / liveWaveform.size.coerceAtLeast(1)
            for (i in liveWaveform.indices) {
                val amp = liveWaveform[i].coerceIn(0.02f, 1.0f)
                val barHeight = amp * (height * 0.85f)
                val x = i * barWidth
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.7f),
                    topLeft = Offset(x, centerY - barHeight / 2f),
                    size = Size((barWidth - 1f).coerceAtLeast(1f), barHeight),
                    cornerRadius = CornerRadius(1f, 1f)
                )
            }
        }

        // Center Red Playhead Line
        drawLine(
            color = Color.Red,
            start = Offset(playheadX, 0f),
            end = Offset(playheadX, height),
            strokeWidth = 2.5f
        )
    }
}

@Composable
fun Deck(
    title: String,
    color: Color,
    currentMedia: MediaItem?,
    isPlaying: Boolean,
    progress: Float,
    duration: Long,
    playbackPosition: Long,
    tempo: Float,
    pitch: Float,
    volume: Float,
    isSyncEnabled: Boolean,
    bpm: Int = 120,
    key: String = "C",
    energy: String = "",
    pitchLock: Boolean = true,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onTempoChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleSync: () -> Unit,
    onTogglePitchLock: () -> Unit,
    onTapBpm: () -> Unit,
    onCue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.85f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Track Info Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = currentMedia?.mediaMetadata?.title?.toString() ?: "No Track Loaded",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatTime(playbackPosition)} / ${formatTime(duration)}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                }

                // BPM, Key, Energy & Key Lock Badge
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (key.isNotBlank()) {
                            Surface(
                                color = color.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(3.dp),
                                border = BorderStroke(0.5.dp, color.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = key,
                                    color = color,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        if (energy.isNotBlank()) {
                            Surface(
                                color = Color.DarkGray.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(3.dp)
                            ) {
                                Text(
                                    text = "⚡$energy",
                                    color = Color(0xFFFFD700),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(
                            text = "${bpm} BPM",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = onTapBpm,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(3.dp),
                            modifier = Modifier.size(28.dp, 16.dp)
                        ) {
                            Text("TAP", fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Button(
                        onClick = onTogglePitchLock,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pitchLock) color else Color.Black.copy(alpha = 0.5f),
                            contentColor = if (pitchLock) Color.Black else Color.White
                        ),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(18.dp).padding(horizontal = 2.dp)
                    ) {
                        Text(if (pitchLock) "KEY LOCK ON" else "KEY LOCK OFF", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Jog Wheel Platter
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                JogWheel(
                    isPlaying = isPlaying,
                    progress = progress,
                    accentColor = color,
                    onSeek = onSeek
                )
            }

            // Transport Controls: CUE, PLAY, SYNC
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onCue,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(42.dp, 36.dp)
                    ) {
                        Text("CUE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onPlayPause,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlaying) color else Color.Black.copy(alpha = 0.6f),
                            contentColor = if (isPlaying) Color.Black else color
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(42.dp, 36.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Button(
                    onClick = onToggleSync,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(42.dp, 36.dp)
                ) {
                    Text("SYNC", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Tempo & Volume Sliders
            Row(
                modifier = Modifier.fillMaxWidth().height(80.dp).padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                VerticalSliderWithLabel("VOL", volume, 0f..1f, color, onVolumeChange)
                VerticalSliderWithLabel("TEMPO", tempo, 0.5f..1.5f, color, onTempoChange)
            }
        }
    }
}

@Composable
fun JogWheel(
    isPlaying: Boolean,
    progress: Float,
    accentColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var rotation by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, progress) {
        if (isPlaying) {
            rotation = (progress * 360f * 12f) % 360f
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(Color.Black)
            .border(2.dp, Color.DarkGray, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val dragAngle = atan2(dragAmount.y, dragAmount.x)
                    rotation = (rotation + dragAmount.x + dragAmount.y) % 360f
                    val deltaProgress = dragAmount.x / 1000f
                    onSeek(progress + deltaProgress)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().rotate(rotation)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            // Vinyl grooves
            for (i in 1..4) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.07f),
                    radius = radius * (0.35f + i * 0.12f),
                    center = center,
                    style = Stroke(width = 1f)
                )
            }

            // Center Label
            drawCircle(
                color = accentColor.copy(alpha = 0.3f),
                radius = radius * 0.35f,
                center = center
            )
            drawCircle(
                color = accentColor,
                radius = radius * 0.12f,
                center = center
            )

            // Vinyl Marker
            drawLine(
                color = Color.White,
                start = center,
                end = Offset(center.x, center.y - radius * 0.85f),
                strokeWidth = 3f
            )
        }
    }
}

@Composable
fun VerticalSliderWithLabel(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    onValueChange: (Float) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(42.dp)
    ) {
        Text(label, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .requiredWidth(68.dp)
                .rotate(-90f),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color.copy(alpha = 0.5f),
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
fun MixerBottomSection(
    viewModel: MixerViewModel,
    crossfaderPosition: Float,
    onCrossfaderChange: (Float) -> Unit
) {
    val autoDjEnabled by viewModel.autoDjEnabled.collectAsState()
    val masterVolume by viewModel.masterVolume.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()

    val eqLowA by viewModel.eqLowA.collectAsState()
    val eqMidA by viewModel.eqMidA.collectAsState()
    val eqHighA by viewModel.eqHighA.collectAsState()
    val filterA by viewModel.filterA.collectAsState()
    val echoA by viewModel.echoEnabledA.collectAsState()
    val reverbA by viewModel.reverbEnabledA.collectAsState()

    val eqLowB by viewModel.eqLowB.collectAsState()
    val eqMidB by viewModel.eqMidB.collectAsState()
    val eqHighB by viewModel.eqHighB.collectAsState()
    val filterB by viewModel.filterB.collectAsState()
    val echoB by viewModel.echoEnabledB.collectAsState()
    val reverbB by viewModel.reverbEnabledB.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // EQs & FX Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Deck A EQs
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                EqKnob("LOW", eqLowA, 0f..2f, CyberCyan) { viewModel.setEqLowA(it) }
                EqKnob("MID", eqMidA, 0f..2f, CyberCyan) { viewModel.setEqMidA(it) }
                EqKnob("HI", eqHighA, 0f..2f, CyberCyan) { viewModel.setEqHighA(it) }
                EqKnob("FLT", filterA, 0f..1f, CyberCyan) { viewModel.setFilterA(it) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FxButton("ECHO", echoA, CyberCyan) { viewModel.toggleEchoA() }
                    FxButton("RVB", reverbA, CyberCyan) { viewModel.toggleReverbA() }
                }
            }

            // Center Control Column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Auto DJ Toggle
                Button(
                    onClick = { viewModel.toggleAutoDj() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (autoDjEnabled) Color(0xFF10B981) else Color.DarkGray),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(if (autoDjEnabled) "AUTO DJ: ON" else "AUTO DJ", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                // Emergency Stop Button
                Button(
                    onClick = { viewModel.stopAllMusicImmediately() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text("EMERGENCY STOP", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.White)
                }

                // Record Button (Honest WAV)
                Button(
                    onClick = { viewModel.toggleRecording() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color.Red else Color(0xFF334155)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(22.dp)
                ) {
                    Text(if (isRecording) "STOP REC" else "REC (WAV)", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }

                EqKnob("MASTER", masterVolume, 0f..1f, Color.White) { viewModel.setMasterVolume(it) }
            }

            // Deck B EQs
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FxButton("ECHO", echoB, CyberPink) { viewModel.toggleEchoB() }
                    FxButton("RVB", reverbB, CyberPink) { viewModel.toggleReverbB() }
                }
                EqKnob("FLT", filterB, 0f..1f, CyberPink) { viewModel.setFilterB(it) }
                EqKnob("HI", eqHighB, 0f..2f, CyberPink) { viewModel.setEqHighB(it) }
                EqKnob("MID", eqMidB, 0f..2f, CyberPink) { viewModel.setEqMidB(it) }
                EqKnob("LOW", eqLowB, 0f..2f, CyberPink) { viewModel.setEqLowB(it) }
            }
        }

        // True Equal-Power Crossfader
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("DECK A", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                Text(
                    "CENTER (SNAP)",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onCrossfaderChange(0.5f) }
                )
                Text("DECK B", color = CyberPink, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            }
            Slider(
                value = crossfaderPosition.coerceIn(0f, 1f),
                onValueChange = onCrossfaderChange,
                valueRange = 0f..1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = CyberPink,
                    inactiveTrackColor = CyberCyan
                )
            )
        }
    }
}

@Composable
fun EqKnob(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    onValueChange: (Float) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(36.dp)
    ) {
        Text(label, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .requiredWidth(54.dp)
                .rotate(-90f),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color.copy(alpha = 0.5f),
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
fun FxButton(label: String, active: Boolean, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) color else Color.DarkGray,
            contentColor = if (active) Color.Black else Color.White
        ),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.size(34.dp, 20.dp)
    ) {
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

// --- TABBED PERFORMANCE TRAYS ---

@Composable
fun LibraryBrowserTab(
    allMedia: List<MediaItemEntity>,
    onMediaSelectA: (MediaItemEntity) -> Unit,
    onMediaSelectB: (MediaItemEntity) -> Unit
) {
    if (allMedia.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No audio files found. Add songs to your library to mix.", color = Color.Gray, fontSize = 12.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(2.dp)
        ) {
            items(allMedia, key = { it.id }) { media ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(
                            media.title,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(
                            "${media.artist ?: "Unknown Artist"} • ${if (media.bpm != null && media.bpm > 0) "${media.bpm} BPM" else "BPM Auto"}",
                            color = Color.Gray,
                            fontSize = 9.sp
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = if (media.isVideo) Icons.Default.Videocam else Icons.Default.AudioFile,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                onClick = { onMediaSelectA(media) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.25f)),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("DECK A", color = CyberCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { onMediaSelectB(media) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberPink.copy(alpha = 0.25f)),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("DECK B", color = CyberPink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SamplerTab(onPlay: (String) -> Unit) {
    val samples = listOf(
        "Air Horn", "Crowd", "Siren", "Drum Hit",
        "Scratch", "Bass Hit", "Laser", "Snare"
    )
    Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
        Text("DJ SOUND PADS (REAL OFFLINE AUDIO SYNTHESIS)", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until 4) {
                val sample = samples[i]
                Button(
                    onClick = { onPlay(sample) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1).copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Text(sample, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 4 until 8) {
                val sample = samples[i]
                Button(
                    onClick = { onPlay(sample) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899).copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Text(sample, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun HotCuesTab(viewModel: MixerViewModel) {
    val hotCuesA by viewModel.hotCuesA.collectAsState()
    val hotCuesB by viewModel.hotCuesB.collectAsState()

    Row(modifier = Modifier.fillMaxSize().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Deck A Cues
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("DECK A HOT CUES", color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("CLEAR ALL", color = Color.Gray, fontSize = 8.sp, modifier = Modifier.clickable { viewModel.clearAllHotCuesA() })
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 4) {
                    val cue = hotCuesA.getOrNull(i)
                    Button(
                        onClick = { viewModel.jumpToHotCueA(i) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (cue != null) CyberCyan else Color.DarkGray,
                            contentColor = if (cue != null) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("CUE ${i + 1}", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            if (cue != null) {
                                Text(formatTime(cue), fontSize = 8.sp)
                            } else {
                                Text("SET", fontSize = 7.sp, color = Color.White.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }

        // Deck B Cues
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("DECK B HOT CUES", color = CyberPink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("CLEAR ALL", color = Color.Gray, fontSize = 8.sp, modifier = Modifier.clickable { viewModel.clearAllHotCuesB() })
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 4) {
                    val cue = hotCuesB.getOrNull(i)
                    Button(
                        onClick = { viewModel.jumpToHotCueB(i) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (cue != null) CyberPink else Color.DarkGray,
                            contentColor = if (cue != null) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("CUE ${i + 1}", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            if (cue != null) {
                                Text(formatTime(cue), fontSize = 8.sp)
                            } else {
                                Text("SET", fontSize = 7.sp, color = Color.White.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoopsTab(viewModel: MixerViewModel) {
    val activeLoopA by viewModel.activeLoopA.collectAsState()
    val loopBeatsA by viewModel.loopBeatsA.collectAsState()
    val activeLoopB by viewModel.activeLoopB.collectAsState()
    val loopBeatsB by viewModel.loopBeatsB.collectAsState()

    val beatOptions = listOf(1, 2, 4, 8, 16)

    Row(modifier = Modifier.fillMaxSize().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Deck A Loop
        Column(modifier = Modifier.weight(1f)) {
            Text("DECK A BEAT LOOPS", color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                beatOptions.forEach { beats ->
                    val isActive = loopBeatsA == beats
                    Button(
                        onClick = { viewModel.setLoopA(beats) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActive) CyberCyan else Color.DarkGray,
                            contentColor = if (isActive) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Text("${beats}B", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = { viewModel.exitLoopA() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeLoopA != null) Color.Red else Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Text("EXIT", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Deck B Loop
        Column(modifier = Modifier.weight(1f)) {
            Text("DECK B BEAT LOOPS", color = CyberPink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                beatOptions.forEach { beats ->
                    val isActive = loopBeatsB == beats
                    Button(
                        onClick = { viewModel.setLoopB(beats) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActive) CyberPink else Color.DarkGray,
                            contentColor = if (isActive) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Text("${beats}B", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = { viewModel.exitLoopB() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeLoopB != null) Color.Red else Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Text("EXIT", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun EffectsTab(viewModel: MixerViewModel) {
    val activeEffects by viewModel.activeEffects.collectAsState()
    val effects = listOf("Echo", "Reverb", "Flanger", "Bit Crusher", "Roll", "Bass Boost")

    Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
        Text("REAL DSP AUDIO EFFECTS (DIRECT HARDWARE PROCESSING)", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until 3) {
                val fx = effects[i]
                val isActive = activeEffects.contains(fx)
                Button(
                    onClick = { viewModel.toggleEffect(fx) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primary else Color.DarkGray,
                        contentColor = if (isActive) Color.Black else Color.White
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Text(fx, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 3 until 6) {
                val fx = effects[i]
                val isActive = activeEffects.contains(fx)
                Button(
                    onClick = { viewModel.toggleEffect(fx) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primary else Color.DarkGray,
                        contentColor = if (isActive) Color.Black else Color.White
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Text(fx, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AutoDjTab(viewModel: MixerViewModel) {
    val isEnabled by viewModel.isAutoDjEnabled.collectAsState()
    val telemetry by viewModel.autoDjTelemetry.collectAsState()
    val autoDjMode by viewModel.autoDjMode.collectAsState()
    val crossfade by viewModel.crossfade.collectAsState()
    val mediaA by viewModel.deckAMedia.collectAsState()
    val mediaB by viewModel.deckBMedia.collectAsState()
    val analysisA by viewModel.trackAnalysisA.collectAsState()
    val analysisB by viewModel.trackAnalysisB.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 1. Header Bar: Title, Live Phase Badge, and Auto DJ Toggle Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "INTELLIGENT AUTO DJ",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (isEnabled) {
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, Color(0xFF10B981))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    telemetry.phase,
                                    color = Color(0xFF10B981),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Text(
                    if (isEnabled) "Harmonic Camelot Wheel mixing, BPM beat-matching & bass swap" else "Auto-mix your local tracks with real key & tempo synchronization",
                    color = if (isEnabled) Color.LightGray else Color.Gray,
                    fontSize = 8.sp
                )
            }

            Button(
                onClick = { viewModel.toggleAutoDj() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEnabled) Color(0xFFEF4444) else Color(0xFF10B981)
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(26.dp)
            ) {
                Text(
                    if (isEnabled) "STOP AUTO DJ" else "START AUTO DJ",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 2. Mode Selector Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AutoDjMixMode.values().forEach { mode ->
                val selected = autoDjMode == mode
                Surface(
                    onClick = { viewModel.setAutoDjMode(mode) },
                    shape = RoundedCornerShape(4.dp),
                    color = if (selected) CyberCyan.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, if (selected) CyberCyan else Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.weight(1f).height(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            mode.title,
                            color = if (selected) CyberCyan else Color.LightGray,
                            fontSize = 8.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 3. Live Harmonic & Transition Intelligence Card
        if (isEnabled && telemetry.transitionScore != null) {
            val score = telemetry.transitionScore!!
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(0.5.dp, CyberCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${score.matchPercentage}% MATCH",
                                color = if (score.matchPercentage >= 85) Color(0xFF10B981) else Color(0xFFFFB800),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                score.summary,
                                color = Color.White,
                                fontSize = 8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            "Harmonic: ${score.harmonicRelation.label} • ${score.energyDescription}",
                            color = Slate300,
                            fontSize = 7.5.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { viewModel.triggerImmediateTransition() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.85f)),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(20.dp)
                        ) {
                            Text("⚡ MIX NOW", color = Color.Black, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.skipNextTrack() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(20.dp)
                        ) {
                            Text("⏭ SKIP", color = Color.White, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 4. Deck Status Cards (Deck A vs Deck B)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val isAActive = telemetry.activeDeck == "A"
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, if (isAActive && isEnabled) CyberCyan else Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (isAActive && isEnabled) "DECK A (ON AIR)" else (if (isEnabled && !isAActive) "DECK A (NEXT UP)" else "DECK A"),
                            color = CyberCyan,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black
                        )
                        if (analysisA?.musicalKey != null) {
                            Text(
                                "${analysisA?.musicalKey} (${AudioTrackAnalyzer.keyToCamelot(analysisA?.musicalKey)})",
                                color = CyberCyan,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        mediaA?.title ?: "None loaded",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currentMediaA = mediaA
                        val bpmValA = currentMediaA?.bpm
                        val bpmDisplayA = when {
                            analysisA?.bpm != null -> "${analysisA?.bpm} BPM (${analysisA?.bpmSource?.name ?: "DSP"})"
                            bpmValA != null && bpmValA > 0 -> "$bpmValA BPM (META)"
                            else -> "-- BPM"
                        }
                        Text(bpmDisplayA, color = Slate400, fontSize = 7.5.sp)
                        if (analysisA?.energyLevel != null && analysisA?.energyLevel != "UNKNOWN") {
                            Text("⚡${analysisA?.energyLevel}", color = Color(0xFFFFD700), fontSize = 7.5.sp)
                        } else {
                            Text("⚡--", color = Color.Gray, fontSize = 7.5.sp)
                        }
                    }
                }
            }

            val isBActive = telemetry.activeDeck == "B"
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, if (isBActive && isEnabled) CyberPink else Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (isBActive && isEnabled) "DECK B (ON AIR)" else (if (isEnabled && !isBActive) "DECK B (NEXT UP)" else "DECK B"),
                            color = CyberPink,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black
                        )
                        if (analysisB?.musicalKey != null) {
                            Text(
                                "${analysisB?.musicalKey} (${AudioTrackAnalyzer.keyToCamelot(analysisB?.musicalKey)})",
                                color = CyberPink,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        mediaB?.title ?: "None loaded",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currentMediaB = mediaB
                        val bpmValB = currentMediaB?.bpm
                        val bpmDisplayB = when {
                            analysisB?.bpm != null -> "${analysisB?.bpm} BPM (${analysisB?.bpmSource?.name ?: "DSP"})"
                            bpmValB != null && bpmValB > 0 -> "$bpmValB BPM (META)"
                            else -> "-- BPM"
                        }
                        Text(bpmDisplayB, color = Slate400, fontSize = 7.5.sp)
                        if (analysisB?.energyLevel != null && analysisB?.energyLevel != "UNKNOWN") {
                            Text("⚡${analysisB?.energyLevel}", color = Color(0xFFFFD700), fontSize = 7.5.sp)
                        } else {
                            Text("⚡--", color = Color.Gray, fontSize = 7.5.sp)
                        }
                    }
                }
            }
        }

        // 5. Crossfader Automation Track
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("CROSSFADER (DECK A)", color = CyberCyan, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                if (isEnabled && telemetry.secondsUntilTransition > 0) {
                    Text("${telemetry.secondsUntilTransition}s until mix", color = Color(0xFF10B981), fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                }
                Text("DECK B", color = CyberPink, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(2.dp))
            LinearProgressIndicator(
                progress = { crossfade.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = CyberPink,
                trackColor = CyberCyan
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
