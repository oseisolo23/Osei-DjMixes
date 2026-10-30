package com.osei.djmixer.ui

import com.osei.djmixer.ui.theme.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.osei.djmixer.data.MediaItemEntity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LiveTvScreen(
    viewModel: com.osei.djmixer.ui.DJMixerViewModel,
    title: String = "LIVE TV CHANNELS", 
    player: Player?,
    streams: List<MediaItemEntity>,
    recentHistory: List<MediaItemEntity> = emptyList(),
    currentMedia: androidx.media3.common.MediaItem?,
    isPlaying: Boolean,
    isOnline: Boolean = true,
    favorites: Set<String>,
    onPlayTv: (List<MediaItemEntity>, Int) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAddTvStation: (String, String, String, String?) -> Unit,
    onDeleteTvStation: (String) -> Unit,
    onEditTvStation: (String, String, String, String, String?) -> Unit,
    onImportM3u: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var selectedEpgStation by remember { mutableStateOf<com.osei.djmixer.data.MediaItemEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MediaItemEntity?>(null) }

    val tvStreams = streams.filter { it.isVideo && !it.title.contains("radio", ignoreCase=true) && !(it.artist ?: "").contains("radio", ignoreCase=true) }
        
    val filteredByProvider = remember(tvStreams) {
        tvStreams.filter { stream ->
            val artist = stream.artist ?: ""
            val isPlex = artist.contains("Plex", ignoreCase = true)
            val isPluto = artist.contains("Pluto", ignoreCase = true)
            val isTubi = artist.contains("Tubi", ignoreCase = true)
            val isSamsung = artist.contains("Samsung", ignoreCase = true)
                
            !(isPlex || isPluto || isTubi || isSamsung)
        }
    }

    var selectedCategory by remember { mutableStateOf("All") }
    val categoryDisplayNames = remember {
        mapOf(
            "All" to "All",
            "Ghana" to "🇬🇭 Ghana TV",
            "Nigeria" to "🇳🇬 Nigeria TV",
            "United Kingdom" to "🇬🇧 United Kingdom TV",
            "United States" to "🇺🇸 United States TV",
            "South Africa" to "🇿🇦 South Africa TV",
            "International" to "🌍 International TV",
            "Christian" to "✝️ Christian TV",
            "Music" to "🎵 Music TV",
            "Sports" to "⚽ Sports TV",
            "News" to "📰 News TV",
            "Movies" to "🎬 Movies",
            "Kids" to "🧸 Kids"
        )
    }
    
    val categories = remember(tvStreams) {
        val predefined = listOf("Ghana", "Nigeria", "United Kingdom", "United States", "South Africa", "International", "Christian", "Music", "Sports", "News", "Movies", "Kids")
        val dynamic = tvStreams.mapNotNull { it.region }.filter { it.isNotBlank() && !predefined.contains(it) }.distinct()
        listOf("All") + predefined + dynamic.sorted()
    }

    val filteredStreams = remember(filteredByProvider, searchQuery, selectedCategory) {
        val countries = listOf("Ghana", "Nigeria", "United Kingdom", "United States", "South Africa", "International")
        filteredByProvider.filter { item ->
            val isCountryCategory = countries.contains(selectedCategory)
            
            val matchCategory = if (selectedCategory == "All") {
                true
            } else if (isCountryCategory) {
                item.region?.equals(selectedCategory, ignoreCase = true) == true
            } else {
                item.region?.equals(selectedCategory, ignoreCase = true) == true ||
                item.category?.equals(selectedCategory, ignoreCase = true) == true ||
                item.artist?.contains(selectedCategory, ignoreCase = true) == true ||
                item.title.contains(selectedCategory, ignoreCase = true)
            }
            
            val matchSearch = searchQuery.isBlank() || 
                              item.title.contains(searchQuery, ignoreCase = true) ||
                              item.artist?.contains(searchQuery, ignoreCase = true) == true ||
                              item.region?.contains(searchQuery, ignoreCase = true) == true
                              
            matchCategory && matchSearch
        }
    }

    val playingStreamEntity = remember(currentMedia, tvStreams) {
        tvStreams.find { it.id == currentMedia?.mediaId || it.uri == currentMedia?.requestMetadata?.mediaUri?.toString() }
    }
    
    val isVideoPlaying = playingStreamEntity != null
    var isFullScreen by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF030712),
                        Color(0xFF0D1527),
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize().then(if (isFullScreen) Modifier else Modifier.statusBarsPadding())) {
            // Player Area
            val activity = LocalContext.current as? android.app.Activity
            
            BackHandler(enabled = isFullScreen) {
                isFullScreen = false
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }

            if (isVideoPlaying && player != null) {
                var showControls by remember { mutableStateOf(true) }
                LaunchedEffect(showControls, isVideoPlaying) {
                    if (showControls) {
                        delay(5000)
                        showControls = false
                    }
                }
                
                val currentIdx = filteredStreams.indexOfFirst { it.id == playingStreamEntity?.id }

                val isPip = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) activity?.isInPictureInPictureMode == true else false
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isFullScreen || isPip) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { showControls = !showControls }
                            )
                        }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                setShowNextButton(false)
                                setShowPreviousButton(false)
                            }
                        },
                        update = { view ->
                            if (view.player != player) {
                                view.player = player
                            }
                        },
                        onRelease = { view ->
                            view.player = null
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Custom Overlay
                    val isPip = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) activity?.isInPictureInPictureMode == true else false
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showControls && !isPip,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            showControls = !showControls
                                        },
                                        onPress = {
                                            // Reset timer on any touch
                                            showControls = true
                                        }
                                    )
                                }
                        ) {
                            // Top Bar
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isFullScreen) {
                                        IconButton(onClick = {
                                            isFullScreen = false
                                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                        }) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit Fullscreen", tint = Color.White)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Red)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "LIVE",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = playingStreamEntity?.title ?: "",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                                
                                IconButton(onClick = { /* Settings Placeholder */ }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                                }
                            }

                            // Center Controls (Prev, Play/Pause, Next)
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(32.dp)
                            ) {
                                IconButton(
                                    onClick = { 
                                        if (currentIdx > 0 && isOnline) onPlayTv(listOf(filteredStreams[currentIdx - 1]), 0)
                                    },
                                    modifier = Modifier.size(48.dp).background(Color.White.copy(alpha=0.1f), CircleShape)
                                ) {
                                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = if (currentIdx > 0) Color.White else Color.White.copy(alpha=0.3f), modifier = Modifier.size(32.dp))
                                }
                                
                                IconButton(
                                    onClick = {
                                        if (isPlaying) player.pause() else player.play()
                                    },
                                    modifier = Modifier.size(64.dp).background(CyberPink, CircleShape)
                                ) {
                                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Color.White, modifier = Modifier.size(40.dp))
                                }
                                
                                IconButton(
                                    onClick = { 
                                        if (currentIdx < filteredStreams.size - 1 && isOnline) onPlayTv(listOf(filteredStreams[currentIdx + 1]), 0)
                                    },
                                    modifier = Modifier.size(48.dp).background(Color.White.copy(alpha=0.1f), CircleShape)
                                ) {
                                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = if (currentIdx < filteredStreams.size - 1) Color.White else Color.White.copy(alpha=0.3f), modifier = Modifier.size(32.dp))
                                }
                            }

                            // Bottom Bar (EPG, Fav, Fullscreen)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val tvgId = playingStreamEntity?.tvgId
                                    val playingProgramFlow = remember(tvgId) { if (tvgId != null) viewModel.getCurrentProgramFlow(tvgId) else kotlinx.coroutines.flow.flowOf(null) }
                                    val nextProgramFlow = remember(tvgId) { if (tvgId != null) viewModel.getNextProgramFlow(tvgId) else kotlinx.coroutines.flow.flowOf(null) }
                                    val playingProgram by playingProgramFlow.collectAsState(initial = null)
                                    val nextProgram by nextProgramFlow.collectAsState(initial = null)
                                    
                                    if (playingProgram != null) {
                                        Text(text = "Now: ${playingProgram!!.title}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (!playingProgram!!.description.isNullOrBlank()) {
                                            Text(text = playingProgram!!.description!!, color = Color.White.copy(alpha=0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (nextProgram != null) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(text = "Next: ${nextProgram!!.title}", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    var volume by remember { mutableStateOf(1f) }
                                    IconButton(onClick = { 
                                        volume = if (volume > 0f) 0f else 1f
                                        player.volume = volume
                                    }) {
                                        Icon(if (volume > 0f) Icons.Default.VolumeUp else Icons.Default.VolumeOff, contentDescription = "Volume", tint = Color.White)
                                    }
                                    val isFav = favorites.contains(playingStreamEntity?.id)
                                    IconButton(onClick = { playingStreamEntity?.id?.let { onToggleFavorite(it) } }) {
                                        Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favorite", tint = if (isFav) Color.Red else Color.White)
                                    }
                                    
                                    IconButton(onClick = {
                                        isFullScreen = !isFullScreen
                                        activity?.requestedOrientation = if (isFullScreen) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    }) {
                                        Icon(if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = "Full Screen", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Select a channel to watch LIVE",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            

            if (!isFullScreen) {
            val tvgId = playingStreamEntity?.tvgId
            val playingProgramFlow = remember(tvgId) { if (tvgId != null) viewModel.getCurrentProgramFlow(tvgId) else kotlinx.coroutines.flow.flowOf(null) }
            val playingProgram by playingProgramFlow.collectAsState(initial = null)
            if (playingStreamEntity != null && isPlaying) {
                Row(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha=0.05f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Now Playing on ${playingStreamEntity!!.title}", color = Color.White.copy(alpha=0.6f), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = playingProgram?.title ?: playingStreamEntity!!.artist ?: "LIVE TV", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        if (playingProgram?.description != null) {
                            Text(text = playingProgram!!.description!!, color = Color.White.copy(alpha=0.8f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    if (playingStreamEntity?.tvgId != null) {
                        IconButton(onClick = { selectedEpgStation = playingStreamEntity }, modifier = Modifier.background(CyberPink.copy(alpha=0.15f), CircleShape)) {
                            Icon(Icons.Default.ViewList, contentDescription = "TV Guide", tint = CyberPink)
                        }
                    }
                }
            }
            
            // Title block and actions

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        color = CyberPink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Official & Imported Streams",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CyberPink.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Import M3U playlist",
                            tint = CyberPink,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CyberPink.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add custom TV station",
                            tint = CyberPink,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            
            
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search Channels...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = CyberPink
                ),
                singleLine = true
            )
            // Categories Filter
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = cat == selectedCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) CyberPink else Color.White.copy(alpha = 0.1f))
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = categoryDisplayNames[cat] ?: cat,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Favorites Section
            val favoriteStreams = filteredStreams.filter { favorites.contains(it.id) }
            if (favoriteStreams.isNotEmpty() && searchQuery.isBlank()) {
                Text(
                    text = "Favorites",
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(favoriteStreams) { stream ->
                                                Box(
                            modifier = Modifier
                                .width(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha=0.05f))
                                .clickable { onPlayTv(listOf(stream), 0) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (stream.artUri != null) {
                                    coil.compose.AsyncImage(
                                        model = stream.artUri,
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha=0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White.copy(alpha=0.5f))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = stream.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (stream.artist != null) {
                                    Text(text = stream.artist, color = Color.White.copy(alpha=0.6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            // Channels List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredStreams) { station ->
                    val isStationPlaying = station.id == playingStreamEntity?.id
                    val isFavorite = favorites.contains(station.id)
                    val isCustom = station.id.startsWith("custom_stream_") || station.id.startsWith("m3u_")
                    
                    TvChannelRow(
                        viewModel = viewModel,
                        station = station,
                        isPlaying = isStationPlaying,
                        isFavorite = isFavorite,
                        onPlay = {
                            if (isOnline) {
                                onPlayTv(listOf(station), 0)
                            }
                        },
                        onToggleFav = { onToggleFavorite(station.id) },
                        onShowEpg = { selectedEpgStation = station },
                        onEdit = if (isCustom) { { editingItem = station } } else null,
                        onDelete = if (isCustom) { { onDeleteTvStation(station.id) } } else null
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(120.dp)) // Bottom nav padding
                }
            }
            } // end if (!isFullScreen)
        }
    }

    // Dialogs
    if (showImportDialog) {
        var importUrl by remember { mutableStateOf("") }
        var epgUrl by remember { mutableStateOf("") }
        val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                importUrl = uri.toString()
            }
        }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import M3U Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = importUrl,
                            onValueChange = { importUrl = it },
                            label = { Text("M3U URL or Path") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(CyberPink.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = "Select File", tint = CyberPink)
                        }
                    }
                    OutlinedTextField(
                        value = epgUrl,
                        onValueChange = { epgUrl = it },
                        label = { Text("EPG URL (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Or select a popular free IPTV network:", fontSize = 12.sp, color = Color.White.copy(alpha=0.6f))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = importUrl == "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us_plex.m3u", 
                            onClick = { 
                                importUrl = "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us_plex.m3u"
                            }, 
                            label = { Text("Plex") }
                        )
                        FilterChip(
                            selected = importUrl == "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us_pluto.m3u", 
                            onClick = { 
                                importUrl = "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us_pluto.m3u"
                            }, 
                            label = { Text("Pluto TV") }
                        )
                        FilterChip(
                            selected = importUrl == "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us_tubi.m3u", 
                            onClick = { 
                                importUrl = "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us_tubi.m3u"
                            }, 
                            label = { Text("Tubi") }
                        )
                        FilterChip(
                            selected = importUrl == "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us_samsung.m3u", 
                            onClick = { 
                                importUrl = "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us_samsung.m3u"
                            }, 
                            label = { Text("Samsung TV") }
                        )
                        FilterChip(
                            selected = importUrl == "https://iptv-org.github.io/iptv/countries/gh.m3u", 
                            onClick = { 
                                importUrl = "https://iptv-org.github.io/iptv/countries/gh.m3u"
                            }, 
                            label = { Text("Ghana") }
                        )
                        FilterChip(
                            selected = importUrl == "https://iptv-org.github.io/iptv/countries/ng.m3u", 
                            onClick = { 
                                importUrl = "https://iptv-org.github.io/iptv/countries/ng.m3u"
                            }, 
                            label = { Text("Nigeria") }
                        )
                        FilterChip(
                            selected = importUrl == "https://iptvcat.net/united%20kingdom", 
                            onClick = { 
                                importUrl = "https://iptvcat.net/united%20kingdom"
                            }, 
                            label = { Text("United Kingdom") }
                        )
                        FilterChip(
                            selected = importUrl == "https://iptvcat.net/south%20africa", 
                            onClick = { 
                                importUrl = "https://iptvcat.net/south%20africa"
                            }, 
                            label = { Text("South Africa") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importUrl.isNotBlank()) {
                            viewModel.importM3uPlaylist(importUrl, epgUrl)
                            showImportDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPink)
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var uri by remember { mutableStateOf("") }
        var region by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Custom TV Station") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Station Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uri,
                        onValueChange = { uri = it },
                        label = { Text("Stream URL (M3U8)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = region,
                        onValueChange = { region = it },
                        label = { Text("Region/Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank() && uri.isNotBlank()) {
                            onAddTvStation(title, "Custom TV", uri, region.ifBlank { "Custom" })
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPink)
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    editingItem?.let { itemToEdit ->
        var title by remember { mutableStateOf(itemToEdit.title) }
        var uri by remember { mutableStateOf(itemToEdit.uri) }
        var region by remember { mutableStateOf(itemToEdit.region ?: "") }
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("Edit Custom TV Station") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Station Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uri,
                        onValueChange = { uri = it },
                        label = { Text("Stream URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = region,
                        onValueChange = { region = it },
                        label = { Text("Region") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank() && uri.isNotBlank()) {
                            onEditTvStation(itemToEdit.id, title, "Custom TV", uri, region)
                            editingItem = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPink)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedEpgStation?.let { station ->
        EpgBottomSheet(
            viewModel = viewModel,
            station = station,
            onDismissRequest = { selectedEpgStation = null }
        )
    }
}

@Composable
fun TvChannelRow(
    viewModel: com.osei.djmixer.ui.DJMixerViewModel,
    station: MediaItemEntity,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFav: () -> Unit,
    onShowEpg: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) CyberPink.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)
        ),
        border = BorderStroke(
            width = if (isPlaying) 1.dp else 0.dp,
            color = if (isPlaying) CyberPink else Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (!station.artUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(station.artUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = station.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = if (isPlaying) CyberPink else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        val tvgId2 = station.tvgId
                        val currentProgramFlow = remember(tvgId2) { if (tvgId2 != null) viewModel.getCurrentProgramFlow(tvgId2) else kotlinx.coroutines.flow.flowOf(null) }
                        val currentProgram by currentProgramFlow.collectAsState(initial = null)
                        Text(
                            text = "Now: ${currentProgram?.title ?: station.artist ?: "LIVE TV"}",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    // Determine quality badge based on uri hash or title to keep it consistent
                    val qualityText = when (kotlin.math.abs(station.id.hashCode() % 3)) {
                        0 -> "4K"
                        1 -> "FHD"
                        else -> "HD"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE5A93B).copy(alpha = 0.2f))
                            .border(1.dp, Color(0xFFE5A93B).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = qualityText,
                            color = Color(0xFFE5A93B),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    
                    if (isPlaying) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PLAYING",
                            color = CyberPink,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                val tvgId3 = station.tvgId
                val nextProgramFlow = remember(tvgId3) { if (tvgId3 != null) viewModel.getNextProgramFlow(tvgId3) else kotlinx.coroutines.flow.flowOf(null) }
                val nextProgram by nextProgramFlow.collectAsState(initial = null)
                if (nextProgram != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Next: ${nextProgram?.title}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // EPG Button
            IconButton(onClick = onShowEpg) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "TV Guide",
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }
            
            // Favorite Button
            IconButton(onClick = onToggleFav) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color.Red else Color.White.copy(alpha = 0.3f)
                )
            }

            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Station",
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Station",
                        tint = Color.Red.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}



@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EpgBottomSheet(
    viewModel: com.osei.djmixer.ui.DJMixerViewModel,
    station: MediaItemEntity,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF0F0C29),
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tv, contentDescription = null, tint = CyberPink, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TV Guide (EPG)",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = station.title,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
                
                var epgUpdateUrl by remember { mutableStateOf("https://epgshare01.online/epgshare01/epg_ripper_US2.xml.gz") }
                var showEpgUpdateDialog by remember { mutableStateOf(false) }
                
                IconButton(onClick = { showEpgUpdateDialog = true }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Update EPG", tint = CyberPink)
                }
                
                if (showEpgUpdateDialog) {
                    AlertDialog(
                        onDismissRequest = { showEpgUpdateDialog = false },
                        title = { Text("Update EPG") },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = epgUpdateUrl,
                                    onValueChange = { epgUpdateUrl = it },
                                    label = { Text("EPG XML.GZ URL") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("Common: US1, UK1, ZA1, ALL_SOURCES1", fontSize = 10.sp, color = Color.White.copy(alpha=0.5f))
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                viewModel.updateEpg(epgUpdateUrl)
                                showEpgUpdateDialog = false
                            }) { Text("Update") }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            val tvgId4 = station.tvgId
            val scheduleFlow = remember(tvgId4) { if (tvgId4 != null) viewModel.getScheduleForChannel(tvgId4) else kotlinx.coroutines.flow.flowOf(emptyList()) }
            val schedule by scheduleFlow.collectAsState(initial = emptyList())
            val now = System.currentTimeMillis()
            
            if (schedule.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("No EPG Data Available", color = Color.White.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val catchUp = schedule.filter { it.endTime <= now }
                    val current = schedule.find { it.startTime <= now && it.endTime > now }
                    val upcoming = schedule.filter { it.startTime > now }
                    
                    val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())

                    if (catchUp.isNotEmpty()) {
                        item {
                            Text(
                                text = "Catch-up (Past 24 Hours)",
                                color = CyberPink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(catchUp) { prog ->
                            EpgItem(time = timeFormat.format(java.util.Date(prog.startTime)), title = prog.title, isCatchUp = true)
                        }
                        item {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }

                    if (current != null) {
                        item {
                            Text(
                                text = "Live Now",
                                color = CyberPink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        item {
                            EpgItem(time = "Now", title = current.title, isLive = true)
                        }
                        item {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }

                    if (upcoming.isNotEmpty()) {
                        item {
                            Text(
                                text = "Upcoming",
                                color = CyberPink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(upcoming) { prog ->
                            EpgItem(time = timeFormat.format(java.util.Date(prog.startTime)), title = prog.title)
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EpgItem(time: String, title: String, isLive: Boolean = false, isCatchUp: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = time,
            color = if (isLive) CyberPink else Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp,
            fontWeight = if (isLive) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.width(100.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (isCatchUp) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Replay,
                        contentDescription = "Catch-up Available",
                        tint = CyberPink,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Catch-up Available",
                        color = CyberPink,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
