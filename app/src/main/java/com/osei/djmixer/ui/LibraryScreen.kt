package com.osei.djmixer.ui

import com.osei.djmixer.ui.theme.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.media3.common.Player
import com.osei.djmixer.data.MediaItemEntity
import com.osei.djmixer.ui.components.ModernSearchBar
import com.osei.djmixer.ui.components.InlineVideoPlayer

@Composable
fun LibraryScreen(
    isOnline: Boolean = true,
    audioList: List<MediaItemEntity>,
    videoList: List<MediaItemEntity>,
    streams: List<MediaItemEntity>,
    favorites: List<MediaItemEntity>,
    searchResults: List<MediaItemEntity> = emptyList(),
    dbSearchQuery: String = "",
    onUpdateSearchQuery: (String) -> Unit = {},
    playbackHistory: List<com.osei.djmixer.data.PlayerHistoryEntity> = emptyList(),
    controller: Player? = null,
    onFullScreenToggle: () -> Unit = {},
    onToggleFavorite: (String) -> Unit,
    onMediaClick: (MediaItemEntity) -> Unit,
    onAddToQueue: (MediaItemEntity) -> Unit,
    onAddToPlaylist: (MediaItemEntity) -> Unit,
    onAddStream: (String, String, String, Boolean) -> Unit,
    onDeleteMedia: (String) -> Unit,
    onScanUsb: () -> Unit,
    onRenameMedia: (String, String) -> Unit = { _, _ -> },
    onSetMediaPrivacy: (String, Boolean) -> Unit = { _, _ -> },
    onSetAsRingtone: (MediaItemEntity) -> Unit = {},
    onAddNextToQueue: (MediaItemEntity) -> Unit = {},
    onListenWithHtml5: (MediaItemEntity) -> Unit = {},
    onDownloadYoutube: (String, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    downloadingItems: Map<String, Float> = emptyMap(),
    onPlayMultiple: (List<MediaItemEntity>) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    var activePreviewVideo by remember { mutableStateOf<MediaItemEntity?>(null) }
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedMediaIds by remember { mutableStateOf(setOf<String>()) }

    // Synchronize selected preview video item with ExoPlayer current item ID
    LaunchedEffect(controller, controller?.currentMediaItem) {
        val playingId = controller?.currentMediaItem?.mediaId
        if (playingId != null) {
            val matchingVideo = videoList.find { it.id == playingId }
            if (matchingVideo != null) {
                activePreviewVideo = matchingVideo
            }
        }
    }
val tabs = listOf("Songs", "Albums", "Artists", "Videos", "Favorites", "Recent")
    var searchQuery by remember { mutableStateOf(dbSearchQuery) }
    
    // Sync with ViewModel's search query
    LaunchedEffect(searchQuery) {
        onUpdateSearchQuery(searchQuery)
    }
    
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedDuration by remember { mutableStateOf("Any Length") }
    var selectedRegion by remember { mutableStateOf("All") }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var selectedSort by remember { mutableStateOf("Date Added") }
    var sortDescending by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    var showAddStreamDialog by remember { mutableStateOf(false) }
    var streamTitle by remember { mutableStateOf("") }
    var streamArtist by remember { mutableStateOf("") }
    var streamUri by remember { mutableStateOf("") }
    var streamIsVideo by remember { mutableStateOf(false) }
    
    var showYoutubeDownloadDialog by remember { mutableStateOf(false) }
    var youtubeUrl by remember { mutableStateOf("") }
    var youtubeTitle by remember { mutableStateOf("") }
    var youtubeArtist by remember { mutableStateOf("") }
    var youtubeAsMp3 by remember { mutableStateOf(false) }
    
    var selectedMediaForRename by remember { mutableStateOf<MediaItemEntity?>(null) }
    var newMediaName by remember { mutableStateOf("") }
    
    val historyMediaMap = remember(audioList, videoList, playbackHistory) {
        val allLocal = audioList + videoList
        val map = allLocal.associateBy { it.id }
        playbackHistory.sortedByDescending { it.playedAt }.mapNotNull { map[it.mediaId] }.distinctBy { it.id }
    }

    val rawItems = when {
        searchQuery.isNotBlank() -> {
            when (selectedTab) {
                0 -> searchResults.filter { !it.isVideo }
                1 -> searchResults.filter { !it.isVideo && !it.album.isNullOrBlank() }
                2 -> searchResults.filter { !it.isVideo && !it.artist.isNullOrBlank() }
                3 -> searchResults.filter { it.isVideo }
                4 -> favorites.filter { it.title.contains(searchQuery, true) || it.artist?.contains(searchQuery, true) == true }
                5 -> historyMediaMap.filter { it.title.contains(searchQuery, true) || it.artist?.contains(searchQuery, true) == true }
                else -> searchResults
            }
        }
        else -> {
            when (selectedTab) {
                0 -> audioList
                1 -> audioList.filter { !it.album.isNullOrBlank() }
                2 -> audioList.filter { !it.artist.isNullOrBlank() }
                3 -> videoList
                4 -> favorites
                5 -> historyMediaMap
                else -> audioList
            }
        }
    }

    val favoriteIds = remember(favorites) { favorites.map { it.id }.toSet() }

    // Gather unique categories dynamically from core items List
    val categories = remember(rawItems, selectedTab) {
        if (selectedTab >= 2) {
            listOf("All")
        } else {
            val dynamicCats = rawItems.map { it.category }.distinct().filter { it.isNotBlank() }
            listOf("All") + dynamicCats
        }
    }

    val regions = remember(rawItems, selectedTab) {
        if (selectedTab == 3) {
            val dynamicRegions = rawItems.mapNotNull { it.region }.distinct().sorted()
            listOf("All") + dynamicRegions
        } else {
            listOf("All")
        }
    }

    // Reset filtering options when tab changes
    LaunchedEffect(selectedTab) {
        searchQuery = ""
        selectedCategory = "All"
        selectedDuration = "Any Length"
        selectedRegion = "All"
        showFavoritesOnly = false
    }

    // Filter Items dynamically on the client side
    val filteredItems = remember(rawItems, searchQuery, selectedCategory, selectedDuration, selectedRegion, showFavoritesOnly, favoriteIds, selectedSort, sortDescending) {
val sorted = rawItems.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    (item.artist?.contains(searchQuery, ignoreCase = true) ?: false) ||
                    item.category.contains(searchQuery, ignoreCase = true)
            if (selectedTab == 3) { // Streams / Radio
                val matchesRegion = selectedRegion == "All" || item.region == selectedRegion
                val matchesFavorites = !showFavoritesOnly || favoriteIds.contains(item.id)
                matchesQuery && matchesFavorites && matchesRegion
            } else if (selectedTab >= 4) { // USB / Privacy
                val matchesFavorites = !showFavoritesOnly || favoriteIds.contains(item.id)
                matchesQuery && matchesFavorites
            } else {
                val matchesCategory = selectedCategory == "All" ||
                        item.category.equals(selectedCategory, ignoreCase = true)
                val matchesDuration = when (selectedDuration) {
                    "Short (< 3m)" -> item.duration < 3 * 60 * 1000L
                    "Medium (3m - 10m)" -> item.duration in (3 * 60 * 1000L)..(10 * 60 * 1000L)
                    "Long (> 10m)" -> item.duration > 10 * 60 * 1000L
                    else -> true
                }
                val matchesFavorites = !showFavoritesOnly || favoriteIds.contains(item.id)
                matchesQuery && matchesCategory && matchesDuration && matchesFavorites
            }
        }.sortedWith { a, b ->
            val cmp = when (selectedSort) {
                "Name" -> a.title.compareTo(b.title, ignoreCase = true)
                "Artist" -> (a.artist ?: "").compareTo(b.artist ?: "", ignoreCase = true)
                "Album" -> (a.album ?: "").compareTo(b.album ?: "", ignoreCase = true)
                "Duration" -> a.duration.compareTo(b.duration)
                else -> (a.addedAt ?: 0).compareTo(b.addedAt ?: 0)
            }
            if (sortDescending) -cmp else cmp
        }
        sorted
    }

val groupedAlbums = remember(filteredItems, selectedTab) {
        if (selectedTab == 1) {
            filteredItems.groupBy { it.album ?: "Unknown Album" }
        } else {
            emptyMap<String, List<MediaItemEntity>>()
        }
    }
    
    val groupedArtists = remember(filteredItems, selectedTab) {
        if (selectedTab == 2) {
            filteredItems.groupBy { it.artist ?: "Unknown Artist" }
        } else {
            emptyMap<String, List<MediaItemEntity>>()
        }
    }

    val isLightTheme = MaterialTheme.colorScheme.background == Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "OFFLINE LIBRARY",
                        color = CyberPink,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Your Local Music & Videos",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (!isOnline) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE53935).copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudOff, contentDescription = "Offline", tint = Color(0xFFE53935), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Offline Mode", color = Color(0xFFE53935), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModernSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search for DJ mixes, videos or songs...",
                    onClear = { searchQuery = "" },
                    modifier = Modifier.weight(1f),
                    trailingContent = {
                        IconButton(onClick = { showFavoritesOnly = !showFavoritesOnly }) {
                            Icon(
                                imageVector = if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (showFavoritesOnly) "Filter favorites" else "Show all",
                                tint = if (showFavoritesOnly) Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
                
                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { 
                        isSelectMode = !isSelectMode
                        selectedMediaIds = emptySet()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isSelectMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else if (isLightTheme) Color.Black.copy(alpha = 0.05f) else GlassWhite, 
                            RoundedCornerShape(12.dp)
                        )
                        .testTag("library_batch_select_button")
                ) {
                    Icon(
                        imageVector = if (isSelectMode) Icons.Default.Checklist else Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Batch Select Mode",
                        tint = if (isSelectMode) MaterialTheme.colorScheme.primary else CyberPink
                    )
                }


                Spacer(modifier = Modifier.width(8.dp))
                
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier
                            .size(48.dp)
                            .background(if (isLightTheme) Color.Black.copy(alpha = 0.05f) else GlassWhite, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort",
                            tint = CyberPink
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        val sortOptions = listOf("Date Added", "Name", "Artist", "Album", "Duration")
                        sortOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, fontWeight = if (selectedSort == option) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    if (selectedSort == option) {
                                        sortDescending = !sortDescending
                                    } else {
                                        selectedSort = option
                                        sortDescending = option == "Date Added" || option == "Duration"
                                    }
                                    showSortMenu = false
                                },
                                trailingIcon = {
                                    if (selectedSort == option) {
                                        Icon(
                                            imageVector = if (sortDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(
                    onClick = onScanUsb,
                    modifier = Modifier
                        .size(48.dp)
                        .background(if (isLightTheme) Color.Black.copy(alpha = 0.05f) else GlassWhite, RoundedCornerShape(12.dp))
                        .testTag("scan_usb_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = "Scan USB",
                        tint = CyberPink
                    )
                }
            }

        Spacer(modifier = Modifier.height(12.dp))

        // Tab selection (Audio / Video)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    if (title == "Videos") return@forEachIndexed
                    val isSelected = selectedTab == index
                    val tabBg = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        if (isLightTheme) Color.Black.copy(alpha = 0.05f) else GlassWhite
                    }
                    val tabText = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    }
                    val tabBorder = if (isSelected) {
                        null
                    } else {
                        BorderStroke(1.dp, if (isLightTheme) Color.Black.copy(alpha = 0.10f) else GlassBorder)
                    }

                    Surface(
                        onClick = { selectedTab = index },
                        color = tabBg,
                        shape = RoundedCornerShape(16.dp),
                        border = tabBorder
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            color = tabText,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Category Filter Row
        if (selectedTab != 2 && selectedTab != 3 && categories.size > 1) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Category",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategory == category
                        val chipBg = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            if (isLightTheme) Color.Black.copy(alpha = 0.05f) else GlassWhite
                        }
                        val chipText = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        }
                        val chipBorder = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            if (isLightTheme) Color.Black.copy(alpha = 0.10f) else GlassBorder
                        }

                        Surface(
                            onClick = { selectedCategory = category },
                            color = chipBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, chipBorder)
                        ) {
                            Text(
                                text = category,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                color = chipText,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Duration Filter Row removed to reclaim vertical space for track lists

        // Region Filter Row (Radio Tuner)


                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(regions) { region ->
                        val isSelected = selectedRegion == region
                        val chipBg = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            if (isLightTheme) Color.Black.copy(alpha = 0.05f) else GlassWhite
                        }
                        val chipText = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        }
                        val chipBorder = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            if (isLightTheme) Color.Black.copy(alpha = 0.10f) else GlassBorder
                        }

                        Surface(
                            onClick = { selectedRegion = region },
                            color = chipBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, chipBorder)
                        ) {
                            Text(
                                text = region,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                color = chipText,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grid/List of Results inside a Responsive Layout
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 720.dp
            val isVideoTab = selectedTab == 3
            val isAlbumTab = selectedTab == 1
            val isArtistTab = selectedTab == 2

            if (isWideScreen && isVideoTab) {
                // Adaptive Split List-Detail Pane (Tablets, Foldables, landscape viewports)
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Column Scrollable video library (40%)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 24.dp, end = 12.dp, bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (downloadingItems.isNotEmpty()) {
                                item {
                                    Text(
                                        "Active Downloads",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                                items(downloadingItems.toList()) { (id, progress) ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(12.dp))
                                                Text("Extraction in progress...", style = MaterialTheme.typography.bodyMedium)
                                                Spacer(Modifier.weight(1f))
                                                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                                            }
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            if (filteredItems.isEmpty()) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 40.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FilterList,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "No media files found. To use the offline library, please add music or videos to your device storage.",
                                            color = MaterialTheme.colorScheme.onBackground,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            } else {
                                items(filteredItems) { item ->
                                    val isSelected = (activePreviewVideo?.id ?: videoList.firstOrNull()?.id) == item.id
                                    val historyEntry = playbackHistory.find { it.mediaId == item.id }
                                    val progress = if (historyEntry != null && item.duration > 0) {
                                        historyEntry.lastPosition.toFloat() / item.duration.toFloat()
                                    } else {
                                        0f
                                    }

                                    MediaListItem(
                                        media = item,
                                        progress = progress,
                                        isFavorite = favoriteIds.contains(item.id),
                                        onToggleFavorite = { onToggleFavorite(item.id) },
                                        onClick = {
                                            activePreviewVideo = item
                                            onMediaClick(item)
                                        },
                                        onAddToQueue = { onAddToQueue(item) },
                                        onAddNextToQueue = { onAddNextToQueue(item) },
                                        onAddToPlaylist = { onAddToPlaylist(item) },
                                        onDelete = { onDeleteMedia(item.id) },
                                        onRename = { 
                                            selectedMediaForRename = item
                                            newMediaName = item.title
                                        },
                                        onSetPrivacy = { onSetMediaPrivacy(item.id, !item.isPrivate) },
                                        onSetAsRingtone = { onSetAsRingtone(item) },
                                        onListenWithHtml5 = { onListenWithHtml5(item) },
                                        modifier = Modifier.border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) CyberPink else Color.Transparent,
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Right Column Dedicated Video Rendering Pane (60%)
                    val activeVideo = activePreviewVideo ?: videoList.firstOrNull()
                    Column(
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                            .padding(end = 24.dp, bottom = 100.dp)
                    ) {
                        if (activeVideo != null) {
                            val isCurrentPlayingVideo = controller != null &&
                                    controller.currentMediaItem?.mediaId == activeVideo.id &&
                                    controller.isPlaying

                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isLightTheme) Color.Black.copy(alpha = 0.03f) else GlassWhite
                                ),
                                border = BorderStroke(1.dp, if (isLightTheme) Color.Black.copy(alpha = 0.1f) else GlassBorder),
                                modifier = Modifier.fillMaxWidth().fillMaxHeight()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxSize()
                                ) {
                                    // Embedded interactive video frame
                                    if (isCurrentPlayingVideo) {
                                        InlineVideoPlayer(
                                            player = controller,
                                            onFullScreenToggle = onFullScreenToggle,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color.Black)
                                                .clickable { onMediaClick(activeVideo) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!activeVideo.artUri.isNullOrEmpty()) {
                                                coil.compose.AsyncImage(
                                                    model = activeVideo.artUri,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                            }
                                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))

                                            Surface(
                                                shape = CircleShape,
                                                color = CyberPink,
                                                modifier = Modifier.size(56.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Start playback",
                                                    tint = Color.Black,
                                                    modifier = Modifier.padding(12.dp).size(32.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Media meta labels
                                    Text(
                                        text = activeVideo.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2
                                    )

                                    Text(
                                        text = activeVideo.artist ?: "Unknown Guide",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = "Category: ${activeVideo.category}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CyberPink,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.weight(1f))

                                    // Dynamic inline button controllers
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { onMediaClick(activeVideo) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f).height(46.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isCurrentPlayingVideo) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isCurrentPlayingVideo) "Pause Session" else "Start Session",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 13.sp
                                            )
                                        }

                                        IconButton(
                                            onClick = { onToggleFavorite(activeVideo.id) },
                                            modifier = Modifier
                                                .background(if (isLightTheme) Color.Black.copy(alpha = 0.05f) else GlassWhite, RoundedCornerShape(12.dp))
                                                .size(46.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (favoriteIds.contains(activeVideo.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = "Favorite track",
                                                tint = if (favoriteIds.contains(activeVideo.id)) Color.Red else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { onAddToQueue(activeVideo) },
                                            modifier = Modifier
                                                .background(if (isLightTheme) Color.Black.copy(alpha = 0.05f) else GlassWhite, RoundedCornerShape(12.dp))
                                                .size(46.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                                contentDescription = "Queue track",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No videos available", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            } else {
                // Adaptive mobile list context (compact viewports or non-video assets)
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isVideoTab && videoList.isNotEmpty()) {
                        val activeVideo = activePreviewVideo ?: videoList.firstOrNull()
                        if (activeVideo != null) {
                            val isCurrentPlayingVideo = controller != null &&
                                    controller.currentMediaItem?.mediaId == activeVideo.id &&
                                    controller.isPlaying

                            if (isCurrentPlayingVideo) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp)
                                        .padding(bottom = 12.dp)
                                ) {
                                    InlineVideoPlayer(
                                        player = controller,
                                        onFullScreenToggle = onFullScreenToggle,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Active Video: ${activeVideo.title}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CyberPink,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (downloadingItems.isNotEmpty()) {
                            item {
                                Text(
                                    "Active Downloads",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(downloadingItems.toList()) { (id, progress) ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text("YouTube Extraction...", style = MaterialTheme.typography.bodyMedium)
                                            Spacer(Modifier.weight(1f))
                                            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                                        }
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                        )
                                    }
                                }
                            }
                        }



                        if (filteredItems.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = when(selectedTab) {
                                            2 -> "No albums found"
                                            3 -> "No streams found"
                                            4 -> "No mixes found"
                                            5 -> "No USB items found"
                                            else -> "No media files found. To use the offline library, please add music or videos to your device storage."
                                        },
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = when(selectedTab) {
                                            2 -> "No albums match your search query."
                                            3 -> "No live streams match your search."
                                            4 -> "Try downloading some mixes from YouTube!"
                                            5 -> "Ensure your USB drive is connected and tap Scan."
                                            else -> "No tracks match your current filters and search query."
                                        },
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 32.dp)
                                    )
                                    if (selectedTab == 5) {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Button(
                                            onClick = onScanUsb,
                                            colors = ButtonDefaults.buttonColors(containerColor = CyberPink),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Scan USB Storage", color = Color.Black)
                                        }
                                    } else if (selectedTab < 2) {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Button(
                                            onClick = {
                                                searchQuery = ""
                                                selectedCategory = "All"
                                                selectedDuration = "Any Length"
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = "Reset Filters",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
if (isAlbumTab) {
                                val columns = if (isWideScreen) 4 else 2
                                val albumEntriesList = groupedAlbums.toList()
                                items(albumEntriesList.chunked(columns)) { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        rowItems.forEach { entry ->
                                            AlbumCard(
                                                albumName = entry.first,
                                                tracks = entry.second,
                                                onMediaClick = { onMediaClick(it) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(columns - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else if (isArtistTab) {
                                val columns = if (isWideScreen) 4 else 2
                                val artistEntriesList = groupedArtists.toList()
                                items(artistEntriesList.chunked(columns)) { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        rowItems.forEach { entry ->
                                            AlbumCard(
                                                albumName = entry.first, // Using AlbumCard for Artist
                                                tracks = entry.second,
                                                onMediaClick = { onMediaClick(it) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(columns - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else {
                                items(filteredItems) { item ->
                                    val historyEntry = playbackHistory.find { it.mediaId == item.id }
                                    val progress = if (historyEntry != null && item.duration > 0) {
                                        historyEntry.lastPosition.toFloat() / item.duration.toFloat()
                                    } else {
                                        0f
                                    }

                                    MediaListItem(
                                        media = item,
                                        progress = progress,
                                        isFavorite = favoriteIds.contains(item.id),
                                        onToggleFavorite = { onToggleFavorite(item.id) },
                                        onClick = { onMediaClick(item) },
                                        onAddToQueue = { onAddToQueue(item) },
                                        isSelectMode = isSelectMode,
                                        isSelected = selectedMediaIds.contains(item.id),
                                        onToggleSelect = {
                                            selectedMediaIds = if (selectedMediaIds.contains(item.id)) {
                                                selectedMediaIds - item.id
                                             } else {
                                                selectedMediaIds + item.id
                                             }
                                        },
                                        onAddNextToQueue = { onAddNextToQueue(item) },
                                        onAddToPlaylist = { onAddToPlaylist(item) },
                                        onDelete = { onDeleteMedia(item.id) },
                                        onRename = { 
                                            selectedMediaForRename = item
                                            newMediaName = item.title
                                        },
                                        onSetPrivacy = { onSetMediaPrivacy(item.id, !item.isPrivate) },
                                        onSetAsRingtone = { onSetAsRingtone(item) },
                                        onListenWithHtml5 = { onListenWithHtml5(item) }
                                    )
                                }
                            }
                        }
                    } // closes else
                } // closes LazyColumn

        if (isSelectMode && selectedMediaIds.isNotEmpty()) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isSelectMode && selectedMediaIds.isNotEmpty(),
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isLightTheme) Color.White else CyberSurface,
                    tonalElevation = 6.dp,
                    border = BorderStroke(1.dp, if (isLightTheme) Color.Black.copy(alpha = 0.15f) else GlassBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("batch_action_panel")
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Selected Items",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "${selectedMediaIds.size} Track(s)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { selectedMediaIds = emptySet() }
                            ) {
                                Text("Clear", fontSize = 12.sp)
                            }

                            // Batch Add to Queue
                            Button(
                                onClick = {
                                    val itemsToQueue = (audioList + videoList + streams).filter { selectedMediaIds.contains(it.id) }
                                    itemsToQueue.forEach { onAddToQueue(it) }
                                    android.widget.Toast.makeText(context, "Added ${itemsToQueue.size} items to Queue", android.widget.Toast.LENGTH_SHORT).show()
                                    selectedMediaIds = emptySet()
                                    isSelectMode = false
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Queue, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Queue", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // Batch Play Consecutively
                            Button(
                                onClick = {
                                    val itemsToPlay = (audioList + videoList + streams).filter { selectedMediaIds.contains(it.id) }
                                    onPlayMultiple(itemsToPlay)
                                    android.widget.Toast.makeText(context, "Playing ${itemsToPlay.size} tracks consecutively", android.widget.Toast.LENGTH_SHORT).show()
                                    selectedMediaIds = emptySet()
                                    isSelectMode = false
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberPink,
                                    contentColor = CyberDark
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play Consecutive", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedMediaForRename != null) {
        AlertDialog(
            onDismissRequest = { selectedMediaForRename = null },
            title = { Text("Rename Media") },
            text = {
                OutlinedTextField(
                    value = newMediaName,
                    onValueChange = { newMediaName = it },
                    label = { Text("New Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRenameMedia(selectedMediaForRename!!.id, newMediaName)
                    selectedMediaForRename = null
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedMediaForRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddStreamDialog) {
        AlertDialog(
            onDismissRequest = { showAddStreamDialog = false },
            title = { Text("Add Live Stream Channel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = streamTitle,
                        onValueChange = { streamTitle = it },
                        label = { Text("Stream Title") },
                        placeholder = { Text("e.g. Divine Harmony Chants") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = streamArtist,
                        onValueChange = { streamArtist = it },
                        label = { Text("Broadcast Studio / Artist") },
                        placeholder = { Text("e.g. Sacred Chimes TV") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = streamUri,
                        onValueChange = { streamUri = it },
                        label = { Text("Stream URL (MP3, HLS / M3U8)") },
                        placeholder = { Text("https://example.com/live.m3u8") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Stream contains Video", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = streamIsVideo,
                            onCheckedChange = { streamIsVideo = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (streamTitle.isNotBlank() && streamUri.isNotBlank()) {
                            onAddStream(streamTitle, streamArtist, streamUri, streamIsVideo)
                            streamTitle = ""
                            streamArtist = ""
                            streamUri = ""
                            streamIsVideo = false
                            showAddStreamDialog = false
                        }
                    },
                    enabled = streamTitle.isNotBlank() && streamUri.isNotBlank()
                ) {
                    Text("Save Stream")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddStreamDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // YouTube Download FAB
        if (selectedTab == 0 || selectedTab == 1 || selectedTab == 3 || selectedTab == 4) {
            FloatingActionButton(
                onClick = { showYoutubeDownloadDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    
                    .padding(24.dp)
                    .padding(bottom = 80.dp) // Offset from bottom player bar
                    .testTag("youtube_download_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download from YouTube",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

    if (showYoutubeDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showYoutubeDownloadDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("YouTube Downloader", style = MaterialTheme.typography.titleLarge)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Extract content directly from YouTube into your 'Osei DJ Mixes' library.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    OutlinedTextField(
                        value = youtubeUrl,
                        onValueChange = { youtubeUrl = it },
                        label = { Text("YouTube URL") },
                        placeholder = { Text("https://youtube.com/watch?v=...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Link, null) }
                    )
                    
                    OutlinedTextField(
                        value = youtubeTitle,
                        onValueChange = { youtubeTitle = it },
                        label = { Text("Video/Mix Title") },
                        placeholder = { Text("e.g. Afrobeats Party Mix 2024") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Title, null) }
                    )

                    OutlinedTextField(
                        value = youtubeArtist,
                        onValueChange = { youtubeArtist = it },
                        label = { Text("DJ / Artist Name") },
                        placeholder = { Text("e.g. Osei DJ") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, null) }
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { youtubeAsMp3 = !youtubeAsMp3 }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = youtubeAsMp3,
                            onCheckedChange = { youtubeAsMp3 = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download as Audio (MP3) only")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (youtubeUrl.isBlank() || youtubeTitle.isBlank()) {
                            // Simple validation
                        } else {
                            onDownloadYoutube(youtubeUrl, youtubeTitle, youtubeArtist, youtubeAsMp3)
                            showYoutubeDownloadDialog = false
                            youtubeUrl = ""
                            youtubeTitle = ""
                            youtubeArtist = ""
                        }
                    },
                    enabled = youtubeUrl.isNotBlank() && youtubeTitle.isNotBlank()
                ) {
                    Text("Start Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showYoutubeDownloadDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }
}
}

@Composable
private fun MediaListItem(
    media: MediaItemEntity,
    progress: Float = 0f,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddNextToQueue: () -> Unit = {},
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit = {},
    onSetPrivacy: () -> Unit = {},
    onSetAsRingtone: () -> Unit = {},
    onListenWithHtml5: () -> Unit = {},
    isSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val isLightTheme = MaterialTheme.colorScheme.background == Color.White

    val isYoutube = media.uri.contains("youtube.com") || media.uri.contains("youtu.be")
    val elementBg = if (isLightTheme) Color.Black.copy(alpha = 0.03f) else GlassWhite
    val borderCol = if (isLightTheme) Color.Black.copy(alpha = 0.10f) else GlassBorder

    Surface(
        onClick = { 
            if (isSelectMode) {
                onToggleSelect() 
            } else {
                if (isYoutube) onListenWithHtml5() else onClick() 
            }
        },
        modifier = modifier.fillMaxWidth(),
        color = elementBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, borderCol)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelectMode && isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                } else {
                    Icon(
                        imageVector = if (media.isVideo) Icons.Default.VideoLibrary else Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = media.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = media.artist ?: "Unknown",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (media.category == "Mixes") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = CyberPink.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, CyberPink)
                        ) {
                            Text(
                                text = "MIX",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CyberPink,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            
            if (!isSelectMode) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                        tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                    DropdownMenuItem(
                        text = { Text("Add to Queue") },
                        onClick = {
                            onAddToQueue()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Queue, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )
                    DropdownMenuItem(
                        text = { Text("Play Next") },
                        onClick = {
                            onAddNextToQueue()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.SkipNext, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )
                    DropdownMenuItem(
                        text = { Text("Play in HTML5 Player") },
                        onClick = {
                            onListenWithHtml5()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.PlayCircle, contentDescription = null, tint = CyberPink) }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            onRename()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (media.isPrivate) "Move from Privacy" else "Move to Privacy") },
                        onClick = {
                            onSetPrivacy()
                            showMenu = false
                        },
                        leadingIcon = { Icon(if (media.isPrivate) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )
                    if (!media.isVideo) {
                        DropdownMenuItem(
                            text = { Text("Set as Ringtone") },
                            onClick = {
                                onSetAsRingtone()
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Add to Playlist") },
                        onClick = {
                            onAddToPlaylist()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            onDelete()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
            }

            if (media.category == "TV" || media.category == "Streams") {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE53935))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = formatDuration(media.duration),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        if (progress > 0.01f && progress < 0.99f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        }
    }
}
}

@Composable
fun AlbumCard(
    albumName: String,
    tracks: List<MediaItemEntity>,
    onMediaClick: (MediaItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val artist = tracks.firstOrNull()?.artist
    val artUri = tracks.firstOrNull { !it.artUri.isNullOrEmpty() }?.artUri

    Card(
        onClick = { 
            // Play first track of album
            tracks.firstOrNull()?.let { onMediaClick(it) }
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (MaterialTheme.colorScheme.background == Color.White) Color.Black.copy(alpha = 0.03f) else GlassWhite),
        border = BorderStroke(1.dp, if (MaterialTheme.colorScheme.background == Color.White) Color.Black.copy(alpha = 0.1f) else GlassBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Gray.copy(alpha = 0.1f))
            ) {
                if (!artUri.isNullOrEmpty()) {
                    coil.compose.AsyncImage(
                        model = artUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = albumName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = artist ?: "Unknown Artist",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "${tracks.size} tracks",
                style = MaterialTheme.typography.labelSmall,
                color = CyberPink,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatDuration(duration: Long): String {
    val sec = duration / 1000
    val min = sec / 60
    val remainingSec = sec % 60
    return "%02d:%02d".format(min, remainingSec)
}

