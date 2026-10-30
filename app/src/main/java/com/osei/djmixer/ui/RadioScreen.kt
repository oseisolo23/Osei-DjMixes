package com.osei.djmixer.ui

import com.osei.djmixer.ui.theme.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import coil.compose.AsyncImage
import com.osei.djmixer.ui.components.ModernSearchBar
import com.osei.djmixer.data.MediaItemEntity
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    player: Player?,
    streams: List<MediaItemEntity>,
    currentMedia: androidx.media3.common.MediaItem?,
    recentHistory: List<MediaItemEntity>,
    isPlaying: Boolean,
    favorites: Set<String>,
    onPlayRadio: (MediaItemEntity, List<MediaItemEntity>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAddRadioStation: (String, String, String, String) -> Unit,
    onDeleteRadioStation: (String) -> Unit,
    onImportM3u: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val categories = listOf(
        "All", "Recently Played", "Favorites", 
        "🇬🇭 Ghana Radio", "🇳🇬 Nigeria Radio", 
        "✝️ Christian Radio", "📰 News Radio", 
        "🎵 Music Radio", "⚽ Sports Radio"
    )
    var selectedCategory by remember { mutableStateOf("All") }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredStreams = remember(streams, searchQuery, selectedCategory, favorites, recentHistory) {
        streams.filter { item ->
            val matchesSearch = item.title.contains(searchQuery, ignoreCase = true) ||
                    (item.artist?.contains(searchQuery, ignoreCase = true) == true)
            
            val matchesCategory = when (selectedCategory) {
                "All" -> true
                "Favorites" -> favorites.contains(item.id)
                "Recently Played" -> recentHistory.any { it.id == item.id }
                "🇬🇭 Ghana Radio" -> (item.region?.equals("GH", ignoreCase = true) == true) || (item.region?.contains("Ghana", ignoreCase = true) == true) || (item.region?.contains("Accra", ignoreCase = true) == true)
                "🇳🇬 Nigeria Radio" -> (item.region?.equals("NG", ignoreCase = true) == true) || (item.region?.contains("Nigeria", ignoreCase = true) == true)
                "✝️ Christian Radio" -> item.category.contains("Prayer", ignoreCase = true) || item.category.contains("Gospel", ignoreCase = true) || item.category.contains("Christian", ignoreCase = true) || item.category.contains("Catholic", ignoreCase = true) || item.category.contains("Teaching", ignoreCase = true)
                "📰 News Radio" -> item.category.contains("News", ignoreCase = true)
                "🎵 Music Radio" -> item.category.contains("Music", ignoreCase = true) || item.category.contains("Contemporary", ignoreCase = true)
                "⚽ Sports Radio" -> item.category.contains("Sports", ignoreCase = true) || item.category.contains("Football", ignoreCase = true)
                else -> true
            }
            matchesSearch && matchesCategory
        }.let {
            if (selectedCategory == "Recently Played") {
                // Return them in the order they appear in recentHistory
                val historyMap = recentHistory.withIndex().associate { it.value.id to it.index }
                it.sortedBy { stream -> historyMap[stream.id] ?: 9999 }
            } else {
                it
            }
        }
    }

    val playingStreamEntity = remember(currentMedia, streams) {
        streams.find { it.id == currentMedia?.mediaId || it.uri == currentMedia?.requestMetadata?.mediaUri?.toString() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF020617),
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "RADIO PLAYER",
                        color = CyberPink,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Professional Streaming Experience",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(CyberPink.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add custom station",
                            tint = CyberPink
                        )
                    }
                }
            }

            // Sticky Header (Currently Playing)
            AnimatedVisibility(
                visible = playingStreamEntity != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                playingStreamEntity?.let { activeStation ->
                    RadioLiveMonitorCard(
                        station = activeStation,
                        player = player,
                        isPlaying = isPlaying,
                        isFavorite = favorites.contains(activeStation.id),
                        onToggleFavorite = { onToggleFavorite(activeStation.id) },
                        onPlayRadio = { onPlayRadio(activeStation, filteredStreams) }
                    )
                }
            }

            // Search Bar
            ModernSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search stations...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )

            // Categories
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                listItems(categories) { category ->
                    val isSel = selectedCategory == category
                    FilterChip(
                        selected = isSel,
                        onClick = { selectedCategory = category },
                        label = { Text(category, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberPink,
                            selectedLabelColor = Color.Black,
                            containerColor = Color.White.copy(alpha = 0.05f),
                            labelColor = Color.White
                        ),
                        border = null,
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            // Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 100.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                gridItems(filteredStreams) { station ->
                    val isPlayingThis = playingStreamEntity?.id == station.id
                    RadioStationGridCard(
                        station = station,
                        isPlaying = isPlayingThis && isPlaying,
                        isFavorite = favorites.contains(station.id),
                        onPlay = { onPlayRadio(station, filteredStreams) },
                        onToggleFav = { onToggleFavorite(station.id) },
                        onDelete = { onDeleteRadioStation(station.id) }
                    )
                }
            }
        }
    }
}

fun getFlagEmoji(region: String?): String {
    if (region == null) return "🌍"
    return when {
        region.equals("GH", ignoreCase = true) || region.contains("Ghana", ignoreCase = true) || region.contains("Accra", ignoreCase = true) -> "🇬🇭"
        region.equals("NG", ignoreCase = true) || region.contains("Nigeria", ignoreCase = true) -> "🇳🇬"
        region.equals("US", ignoreCase = true) || region.contains("USA", ignoreCase = true) -> "🇺🇸"
        region.equals("UK", ignoreCase = true) || region.contains("Britain", ignoreCase = true) -> "🇬🇧"
        region.equals("ZA", ignoreCase = true) || region.contains("South Africa", ignoreCase = true) -> "🇿🇦"
        region.equals("QA", ignoreCase = true) || region.contains("Qatar", ignoreCase = true) -> "🇶🇦"
        region.equals("TR", ignoreCase = true) || region.contains("Turkey", ignoreCase = true) -> "🇹🇷"
        else -> "🌍"
    }
}

@Composable
fun RadioLiveMonitorCard(
    station: MediaItemEntity,
    player: Player?,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayRadio: () -> Unit
) {
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var playerError by remember { mutableStateOf<PlaybackException?>(null) }
    var volume by remember { mutableFloatStateOf(player?.volume ?: 1f) }
    var showVolumeSlider by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        if (player == null) return@LaunchedEffect
        playbackState = player.playbackState
        playerError = player.playerError
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }
            override fun onPlayerError(error: PlaybackException) {
                playerError = error
            }
            override fun onVolumeChanged(v: Float) {
                volume = v
            }
        }
        player.addListener(listener)
        while (true) {
            kotlinx.coroutines.delay(1000)
            if (player.volume != volume) {
                volume = player.volume
            }
        }
    }

    val isBuffering = playbackState == Player.STATE_BUFFERING
    val statusText = when {
        playerError != null -> "ERROR: CONNECTION FAILED"
        isBuffering -> "BUFFERING..."
        isPlaying -> "LIVE TUNED"
        else -> "PAUSED"
    }
    val statusColor = when {
        playerError != null -> Color.Red
        isBuffering -> Color(0xFFFACC15) // Yellow
        isPlaying -> Color(0xFF22C55E) // Green
        else -> Color.White.copy(alpha = 0.5f)
    }

    // Animation for Artwork
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CyberDark),
        border = BorderStroke(1.dp, CyberPink.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Animated Artwork
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .scale(if (isPlaying) scale else 1f)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(2.dp, if (isPlaying) CyberPink else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!station.artUri.isNullOrEmpty()) {
                        AsyncImage(
                            model = station.artUri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(if (isPlaying) rotation else 0f)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = null,
                            tint = CyberPink,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        if (isPlaying && !isBuffering) {
                            Spacer(modifier = Modifier.width(8.dp))
                            RadioAudioVisualizer()
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = station.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "${getFlagEmoji(station.region)} ${station.artist ?: "Live Broadcast"} • 128 kbps",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
                
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) CyberPink else Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            if (playerError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = playerError?.message ?: "Unable to connect to stream.",
                    color = Color.Red.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showVolumeSlider = !showVolumeSlider }) {
                    Icon(
                        imageVector = if (volume == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = { player?.seekToPreviousMediaItem() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                }
                IconButton(
                    onClick = {
                        if (playerError != null) {
                            player?.prepare()
                            player?.play()
                        } else {
                            if (isPlaying) player?.pause() else { player?.play() ?: onPlayRadio() }
                        }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(CyberPink)
                ) {
                    Icon(
                        imageVector = if (isPlaying && playerError == null) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(
                    onClick = { player?.seekToNextMediaItem() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                }
                IconButton(
                    onClick = { 
                        player?.prepare() 
                        player?.play() 
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reconnect",
                        tint = Color.White
                    )
                }
            }
            
            AnimatedVisibility(visible = showVolumeSlider) {
                Slider(
                    value = volume,
                    onValueChange = { player?.volume = it; volume = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = CyberPink,
                        activeTrackColor = CyberPink,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun RadioStationGridCard(
    station: MediaItemEntity,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFav: () -> Unit,
    onDelete: () -> Unit
) {
    val isCustom = station.id.startsWith("custom_stream_")
    
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.03f)
        ),
        border = BorderStroke(
            width = if (isPlaying) 2.dp else 1.dp,
            color = if (isPlaying) CyberPink else Color.White.copy(alpha = 0.08f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("station_card_${station.id}")
            .clickable { onPlay() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getFlagEmoji(station.region),
                    fontSize = 16.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleFav, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) CyberPink else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (isCustom) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color.Red.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                if (!station.artUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = station.artUri,
                        contentDescription = station.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = if (isPlaying) CyberPink else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = station.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Text(
                text = station.artist ?: "Broadcasting",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            if (isPlaying) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PLAYING",
                        color = CyberPink,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    RadioAudioVisualizer(barCount = 3, maxHeight = 10)
                }
            }
        }
    }
}

@Composable
fun RadioAudioVisualizer(
    barCount: Int = 4,
    maxHeight: Int = 16,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition()
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.height(maxHeight.dp)
    ) {
        for (i in 0 until barCount) {
            val duration = 300 + (i * 150)
            val scale by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = duration, easing = FastOutLinearInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            val currentHeight = maxOf(3.dp, (maxHeight * scale).dp)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(currentHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CyberPink)
            )
        }
    }
}
