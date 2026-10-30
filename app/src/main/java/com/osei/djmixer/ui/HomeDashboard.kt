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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osei.djmixer.ui.components.ModernSearchBar
import com.osei.djmixer.data.MediaItemEntity
import coil.compose.AsyncImage
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.osei.djmixer.R

@Composable
fun HomeDashboard(
    recentHistory: List<MediaItemEntity>,
    playbackHistory: List<com.osei.djmixer.data.PlayerHistoryEntity> = emptyList(),
    allMedia: List<MediaItemEntity> = emptyList(),
    favorites: List<MediaItemEntity> = emptyList(),
    lastWatchedVideo: MediaItemEntity? = null,
    lastWatchedVideoPosition: Long = 0L,
    onMediaClick: (MediaItemEntity) -> Unit,
    onAddToQueue: (MediaItemEntity) -> Unit,
    onDownload: (MediaItemEntity) -> Unit = {},
    onToggleFavorite: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    val filteredMedia = if (searchQuery.isEmpty()) {
        emptyList()
    } else {
        allMedia.filter { 
            (it.title?.contains(searchQuery, ignoreCase = true) == true) || 
            (it.artist?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // App Header
        item {
            HeaderSection()
        }

        // Search Bar Section
        item {
            ModernSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search videos, DJ mixes, radio...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        if (searchQuery.isNotEmpty()) {
            item {
                Text(
                    text = "Search Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }
            if (filteredMedia.isEmpty()) {
                item {
                    Text(
                        text = "No results found for \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                items(filteredMedia) { media ->
                    MediaSearchResultItem(
                        media = media, 
                        onClick = { onMediaClick(media) },
                        onDownload = { onDownload(media) }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }

        // Featured Card
        if (searchQuery.isEmpty()) {
            item {
                FeaturedSection(
                    allMedia = allMedia,
                    onPlayClick = { onMediaClick(it) },
                    onDownload = { onDownload(it) }
                )
            }
        }

        // Last Watched Video Card if present
        if (lastWatchedVideo != null && searchQuery.isEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                LastWatchedSection(
                    video = lastWatchedVideo,
                    position = lastWatchedVideoPosition,
                    onClick = { onMediaClick(lastWatchedVideo) }
                )
            }
        }

        if (searchQuery.isEmpty()) {
            // Filter Pills
            item {
                FilterChips(
                    selectedFilter = selectedFilter,
                    onFilterChange = { selectedFilter = it }
                )
            }

            if (selectedFilter == "All") {
                // Recent Media Section
                item {
                    Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Text(
                            text = "RECENT PLAYING HISTORY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (recentHistory.isEmpty()) {
                    item {
                        RecentMediaPlaceholder()
                    }
                } else {
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val last5 = recentHistory.take(5)
                            items(last5) { item ->
                                val historyEntry = playbackHistory.find { it.mediaId == item.id }
                                val progress = if (historyEntry != null && item.duration > 0) {
                                    historyEntry.lastPosition.toFloat() / item.duration.toFloat()
                                } else {
                                    0f
                                }
                                RecentMediaCard(
                                    media = item,
                                    progress = progress,
                                    onClick = { onMediaClick(item) },
                                    onResumeClick = { onMediaClick(item) }
                                )
                            }
                        }
                    }
                }

                // Featured Audio Row
                item {
                    Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp)) {
                        Text(
                            text = "SPIRITUAL AUDIO SESSIONS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val audios = allMedia.filter { !it.isVideo }
                if (audios.isEmpty()) {
                    item {
                        Text(
                            text = "No media files found, please transfer some files to your device or adjust your preferences.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                } else {
                    items(audios.take(4)) { audioItem ->
                        HomeMediaListItem(
                            item = audioItem,
                            isFavorite = favorites.any { it.id == audioItem.id },
                            onToggleFavorite = { onToggleFavorite(audioItem.id) },
                            onClick = { onMediaClick(audioItem) },
                            onAddToQueue = { onAddToQueue(audioItem) }
                        )
                    }
                }

                // Featured Video Row
                item {
                    Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp)) {
                        Text(
                            text = "IMMERSIVE MEDITATION VIDEOS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val videos = allMedia.filter { it.isVideo }
                if (videos.isEmpty()) {
                    item {
                        Text(
                            text = "No devotional videos found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                } else {
                    items(videos.take(4)) { videoItem ->
                        HomeMediaListItem(
                            item = videoItem,
                            isFavorite = favorites.any { it.id == videoItem.id },
                            onToggleFavorite = { onToggleFavorite(videoItem.id) },
                            onClick = { onMediaClick(videoItem) },
                            onAddToQueue = { onAddToQueue(videoItem) }
                        )
                    }
                }

            } else if (selectedFilter == "Audio") {
                item {
                    Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Text(
                            text = "ALL SPIRITUAL AUDIO MIXES",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val audios = allMedia.filter { !it.isVideo }
                if (audios.isEmpty()) {
                    item {
                        Text(
                            text = "No audio mixes found. Play or download from Library.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                        )
                    }
                } else {
                    items(audios) { audioItem ->
                        HomeMediaListItem(
                            item = audioItem,
                            isFavorite = favorites.any { it.id == audioItem.id },
                            onToggleFavorite = { onToggleFavorite(audioItem.id) },
                            onClick = { onMediaClick(audioItem) },
                            onAddToQueue = { onAddToQueue(audioItem) }
                        )
                    }
                }

            } else if (selectedFilter == "Video") {
                item {
                    Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Text(
                            text = "ALL IMMERSIVE DEVO VIDEOS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val videos = allMedia.filter { it.isVideo }
                if (videos.isEmpty()) {
                    item {
                        Text(
                            text = "No devotional videos found. Access Library to explore streams.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                        )
                    }
                } else {
                    items(videos) { videoItem ->
                        HomeMediaListItem(
                            item = videoItem,
                            isFavorite = favorites.any { it.id == videoItem.id },
                            onToggleFavorite = { onToggleFavorite(videoItem.id) },
                            onClick = { onMediaClick(videoItem) },
                            onAddToQueue = { onAddToQueue(videoItem) }
                        )
                    }
                }

            } else if (selectedFilter == "Prayer") {
                val prayerItems = allMedia.filter { it.category == "Prayer" || it.title.contains("Prayer", ignoreCase = true) }
                val distinctPrayers = prayerItems.distinctBy { it.id }

                item {
                    Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Text(
                            text = "PRAYER STREAMS & CHANNELS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (distinctPrayers.isEmpty()) {
                    item {
                        Text(
                            text = "No prayer channels found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                        )
                    }
                } else {
                    items(distinctPrayers) { item ->
                        HomeMediaListItem(
                            item = item,
                            isFavorite = favorites.any { it.id == item.id },
                            onToggleFavorite = { onToggleFavorite(item.id) },
                            onClick = { onMediaClick(item) },
                            onAddToQueue = { onAddToQueue(item) }
                        )
                    }
                }
            } else if (selectedFilter == "Favorites") {
                item {
                    Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Text(
                            text = "YOUR FAVORITE SESSIONS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (favorites.isEmpty()) {
                    item {
                        FavoritesEmptyState()
                    }
                } else {
                    items(favorites) { favItem ->
                        HomeMediaListItem(
                            item = favItem,
                            isFavorite = true,
                            onToggleFavorite = { onToggleFavorite(favItem.id) },
                            onClick = { onMediaClick(favItem) },
                            onAddToQueue = { onAddToQueue(favItem) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.osei_profile),
                contentDescription = "Osei Solomon Portrait",
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentScale = ContentScale.Crop
            )
            Column {
                Text(
                    text = "WELCOME BACK",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "OSEI DJ MIXER",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.dj_mixer_logo_1783155328060),
                contentDescription = "Osei DJ Mixer Logo",
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
        }
    }
}

@Composable
fun FeaturedSection(
    allMedia: List<MediaItemEntity>,
    onPlayClick: (MediaItemEntity) -> Unit,
    onDownload: (MediaItemEntity) -> Unit
) {
    val featuredItem = allMedia.find { it.id == "audio_uplifting_sermon" } ?: allMedia.firstOrNull { !it.isVideo } ?: allMedia.firstOrNull()
    val title = featuredItem?.title ?: "Morning Supplications"
    val artist = featuredItem?.artist ?: "By Imam Al-Hadi"
    
    val isMonochrome = MaterialTheme.colorScheme.background == Color.Black && MaterialTheme.colorScheme.primary == Color.White
    val featuredGradient = if (isMonochrome) {
        Brush.linearGradient(
            listOf(Color(0xFF374151), Color(0xFF1F2937), Color(0xFF111827))
        )
    } else {
        Brush.linearGradient(
            listOf(CyberPurple, CyberBlue, CyberDark)
        )
    }

    val borderCol = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.1f)
    } else {
        GlassBorder
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .aspectRatio(16f/8f)
            .clip(RoundedCornerShape(32.dp))
            .background(featuredGradient)
            .border(1.dp, borderCol, RoundedCornerShape(32.dp))
    ) {
        // Dynamic "Sunlight" effect
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(200f, 200f),
                        radius = 600f
                    )
                )
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.3f),
                        shape = CircleShape,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "Featured Audio",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { featuredItem?.let { onDownload(it) } },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download Item", tint = Color.White)
                    }
                    FloatingActionButton(
                        onClick = { featuredItem?.let { onPlayClick(it) } },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp).testTag("play_featured_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play Featured")
                    }
                }
            }
        }
    }
}

@Composable
fun FilterChips(
    selectedFilter: String,
    onFilterChange: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.padding(vertical = 16.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val filters = listOf("All", "Audio", "Video", "Prayer", "Favorites")
        items(filters) { filter ->
            FilterChipItem(
                label = filter,
                isSelected = filter == selectedFilter,
                onClick = { onFilterChange(filter) }
            )
        }
    }
}

@Composable
fun FilterChipItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val elementBg = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.05f)
    } else {
        GlassWhite
    }
    val borderCol = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.1f)
    } else {
        GlassBorder
    }

    Surface(
        onClick = onClick,
        color = if (isSelected) MaterialTheme.colorScheme.primary else elementBg,
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) null else BorderStroke(1.dp, borderCol),
        modifier = Modifier.testTag("filter_chip_$label")
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

@Composable
fun HomeMediaListItem(
    item: MediaItemEntity,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit
) {
    val elementBg = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.03f)
    } else {
        GlassWhite
    }
    val borderCol = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.08f)
    } else {
        GlassBorder
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .testTag("home_media_item_${item.id}"),
        color = elementBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, borderCol)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.03f))
            ) {
                if (!item.artUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = item.artUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    val placeholderGradient = Brush.linearGradient(
                        colors = listOf(
                            CyberPurple,
                            CyberBlue,
                            CyberDark
                        )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(placeholderGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.isVideo) Icons.Default.Videocam else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = CyberPink,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = "${if (item.isVideo) "Video" else "Audio"} • ${item.artist ?: "Unknown Artist"}",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // Favorite Button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.testTag("favorite_toggle_${item.id}")
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            // Quick add to queue
            IconButton(onClick = onAddToQueue) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = "Add to Queue",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun FavoritesEmptyState() {
    val elementBg = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.03f)
    } else {
        GlassWhite
    }
    val borderCol = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.08f)
    } else {
        GlassBorder
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .border(1.dp, borderCol, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = elementBg),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Red.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "No Favorited Sessions Yet",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap the heart icon on any audio mix, video stream, or sermon in the Library tab or Home feed to keep track of your personal favorites here.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}


@Composable
fun RecentMediaItem(media: MediaItemEntity, progress: Float = 0f, onClick: () -> Unit, onAdd: () -> Unit) {
    val elementBg = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.03f)
    } else {
        GlassWhite
    }
    val borderCol = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.1f)
    } else {
        GlassBorder
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
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
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = media.title.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = media.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    text = "${if (media.isVideo) "Video" else "Audio"} • ${media.artist ?: "Unknown"}",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
            
            var showMenu by remember { mutableStateOf(false) }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to Queue") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            onAdd()
                            showMenu = false
                        }
                    )
                }
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
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        }
    }
}
}

@Composable
fun RecentMediaCard(
    media: MediaItemEntity,
    progress: Float,
    onClick: () -> Unit,
    onResumeClick: () -> Unit
) {
    val elementBg = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.03f)
    } else {
        GlassWhite
    }
    val borderCol = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.08f)
    } else {
        GlassBorder
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(180.dp)
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = elementBg),
        border = BorderStroke(1.dp, borderCol),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Thumbnail container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.03f))
            ) {
                if (!media.artUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = media.artUri,
                        contentDescription = "Thumbnail for ${media.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    // Beautiful custom modern gradient placeholder cover with fine icon indicator
                    val placeholderGradient = Brush.linearGradient(
                        colors = listOf(
                            CyberPurple,
                            CyberBlue,
                            CyberDark
                        )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(placeholderGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (media.isVideo) Icons.Default.Videocam else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = CyberPink.copy(alpha = 0.8f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Media type label overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (media.isVideo) "VIDEO" else "AUDIO",
                        color = CyberPink,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Thin progress bar inside thumbnail frame
                if (progress > 0.01f && progress < 0.99f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                        color = CyberPink,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = media.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            // Artist
            Text(
                text = media.artist ?: "Unknown Artist",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Beautiful explicit Resume button with custom modern style and identifier
            Button(
                onClick = onResumeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .testTag("resume_button_${media.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberPink.copy(alpha = 0.15f),
                    contentColor = CyberPink
                ),
                border = BorderStroke(1.dp, CyberPink.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                elevation = null
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume Icon",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Resume",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun RecentMediaPlaceholder() {
    val elementBg = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.03f)
    } else {
        GlassWhite
    }
    val borderCol = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.08f)
    } else {
        GlassBorder
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .border(1.dp, borderCol, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = elementBg),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(CyberPink.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = CyberPink,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Your Listening History is Empty",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Play streams, audio sermons, or immersive dynamic videos from the Library tab to curate your recent queue list here.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun RecentPlaceholderItem() {
    val elementBg = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.03f)
    } else {
        GlassWhite
    }
    val borderCol = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.1f)
    } else {
        GlassBorder
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        color = elementBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, borderCol)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = "Soul Reflection",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    text = "Audio • 42 min",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun MediaSearchResultItem(
    media: MediaItemEntity,
    onClick: () -> Unit,
    onDownload: () -> Unit = {}
) {
    ListItem(
        headlineContent = { Text(media.title ?: "Unknown", fontWeight = FontWeight.Bold) },
        supportingContent = { Text(media.artist ?: "Unknown", style = MaterialTheme.typography.bodySmall) },
        leadingContent = {
            AsyncImage(
                model = media.artUri,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "Download")
                }
                IconButton(onClick = onClick) {
                    Icon(Icons.Default.PlayCircle, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
    )
}

@Composable
fun LastWatchedSection(
    video: MediaItemEntity,
    position: Long,
    onClick: () -> Unit
) {
    val elementBg = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.03f)
    } else {
        GlassWhite
    }
    val borderCol = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.08f)
    } else {
        GlassBorder
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .testTag("last_watched_video_card"),
        colors = CardDefaults.cardColors(containerColor = elementBg),
        border = BorderStroke(1.dp, borderCol),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.03f))
            ) {
                if (!video.artUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = video.artUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(CyberPurple, CyberBlue, CyberDark)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = CyberPink.copy(alpha = 0.8f)
                        )
                    }
                }
                
                // Play overlay icon for the thumbnail
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text/Progress Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RESUME LAST WATCHED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.height(6.dp))

                // Progress Bar
                val progress = if (video.duration > 0) {
                    (position.toFloat() / video.duration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Resume arrow btn
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Resume video",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

