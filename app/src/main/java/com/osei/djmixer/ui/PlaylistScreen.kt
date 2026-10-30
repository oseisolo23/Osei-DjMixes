package com.osei.djmixer.ui

import com.osei.djmixer.ui.theme.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osei.djmixer.data.MediaItemEntity
import com.osei.djmixer.data.PlaylistEntity
import kotlinx.coroutines.flow.Flow
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

@Composable
fun PlaylistScreen(
    playlists: List<PlaylistEntity>,
    favorites: List<MediaItemEntity>,
    history: List<MediaItemEntity>,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    getMediaForPlaylist: (Long) -> Flow<List<MediaItemEntity>>,
    onPlayPlaylist: (List<MediaItemEntity>, Int) -> Unit,
    onAddToQueue: (MediaItemEntity) -> Unit,
    onAppendPlaylistToQueue: (List<MediaItemEntity>) -> Unit,
    onRemoveMediaFromPlaylist: (Long, String) -> Unit,
    onReorderPlaylist: (Long, List<MediaItemEntity>) -> Unit,
    currentQueue: List<androidx.media3.common.MediaItem>,
    currentMedia: androidx.media3.common.MediaItem?,
    onQueueItemClick: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
    onSaveQueueAsPlaylist: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPlayMedia: (MediaItemEntity) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showSaveQueueDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var saveQueueName by remember { mutableStateOf("") }
    var selectedSectionTab by remember { mutableStateOf(0) }

    val isLightTheme = MaterialTheme.colorScheme.background == Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val activePlaylist = selectedPlaylist
        if (activePlaylist != null) {
            // Collection of current tracks for this playlist
            val playlistItemsState = remember(activePlaylist.id) {
                getMediaForPlaylist(activePlaylist.id)
            }.collectAsState(initial = emptyList())
            
            val tracks = playlistItemsState.value

            PlaylistDetailView(
                playlist = activePlaylist,
                tracks = tracks,
                onBack = { selectedPlaylist = null },
                onPlayTrack = { index -> onPlayPlaylist(tracks, index) },
                onPlayAll = { onPlayPlaylist(tracks, 0) },
                onQueueAll = { onAppendPlaylistToQueue(tracks) },
                onAddToQueue = onAddToQueue,
                onRemoveTrack = { mediaId -> onRemoveMediaFromPlaylist(activePlaylist.id, mediaId) },
                onReorderPlaylist = { reorderedTracks -> onReorderPlaylist(activePlaylist.id, reorderedTracks) }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (selectedSectionTab) {
                            0 -> "Live Queue"
                            else -> "Playlists"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = {
                            if (selectedSectionTab == 1) {
                                showCreateDialog = true
                            } else if (selectedSectionTab == 0) {
                                if (currentQueue.isEmpty()) {
                                    android.widget.Toast.makeText(context, "Queue is empty! Queue some items first.", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    showSaveQueueDialog = true
                                }
                            }
                        },
                        modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = if (selectedSectionTab == 0) Icons.Default.Save else Icons.Default.Add,
                            contentDescription = "Action",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                val tabOptions = listOf("Queue", "Playlists", "Favorites", "History")
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tabOptions.forEachIndexed { index, title ->
                        Button(
                            onClick = { selectedSectionTab = index },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedSectionTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = title,
                                color = if (selectedSectionTab == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (selectedSectionTab == 0) {
                    QueueManagementTab(
                        currentQueue = currentQueue,
                        currentMedia = currentMedia,
                        onQueueItemClick = onQueueItemClick,
                        onRemoveQueueItem = onRemoveQueueItem,
                        onMoveQueueItem = onMoveQueueItem,
                        onClearQueue = onClearQueue,
                        onSaveQueueClick = { showSaveQueueDialog = true }
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (selectedSectionTab) {
                            1 -> {
                                if (playlists.isEmpty()) {
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 48.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                                modifier = Modifier.size(64.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "No playlists found",
                                                color = MaterialTheme.colorScheme.onBackground,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 16.sp
                                            )
                                            Text(
                                                text = "Tap '+' at the top right to create a new one.",
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                fontSize = 14.sp,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                                items(playlists) { playlist ->
                                    PlaylistItem(
                                        playlist = playlist,
                                        onClick = { selectedPlaylist = playlist },
                                        onDelete = { onDeletePlaylist(playlist.id) }
                                    )
                                }
                            }
                            2 -> { // Favorites
                                if (favorites.isEmpty()) {
                                    item {
                                        Text("No favorites found", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(16.dp))
                                    }
                                }
                                items(favorites) { media ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { onPlayPlaylist(favorites, favorites.indexOf(media)) }.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(imageVector = Icons.Default.Favorite, contentDescription = "Favorite", tint = CyberPink, modifier = Modifier.size(32.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(media.title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                                            Text(media.artist ?: "Unknown", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
                                        }
                                        IconButton(onClick = { onToggleFavorite(media.id) }) {
                                            Icon(imageVector = Icons.Default.Favorite, contentDescription = "Remove", tint = CyberPink)
                                        }
                                    }
                                }
                            }
                            3 -> { // History
                                if (history.isEmpty()) {
                                    item {
                                        Text("No history found", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(16.dp))
                                    }
                                }
                                items(history) { media ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { onPlayPlaylist(history, history.indexOf(media)) }.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(imageVector = Icons.Default.History, contentDescription = "History", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(media.title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                                            Text(media.artist ?: "Unknown", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("New Playlist", color = MaterialTheme.colorScheme.onSurface) },
                text = {
                    TextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("e.g., Chill Vibes", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if (isLightTheme) Color.Black.copy(alpha = 0.15f) else GlassBorder,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                onCreatePlaylist(newPlaylistName)
                                newPlaylistName = ""
                                showCreateDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = "Create",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text(
                            text = "Cancel",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(24.dp)
            )
        }

        if (showSaveQueueDialog) {
            AlertDialog(
                onDismissRequest = { showSaveQueueDialog = false },
                title = { Text("Save Queue as Playlist", color = MaterialTheme.colorScheme.onSurface) },
                text = {
                    Column {
                        Text(
                            text = "Enter a name to save all ${currentQueue.size} items in your current playback queue into a custom playlist:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        TextField(
                            value = saveQueueName,
                            onValueChange = { saveQueueName = it },
                            placeholder = { Text("e.g., Daily Dhikr Session", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = if (isLightTheme) Color.Black.copy(alpha = 0.15f) else GlassBorder,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (saveQueueName.isNotBlank()) {
                                onSaveQueueAsPlaylist(saveQueueName)
                                saveQueueName = ""
                                showSaveQueueDialog = false
                                android.widget.Toast.makeText(context, "Playback queue saved successfully!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = saveQueueName.isNotBlank() && currentQueue.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = "Save",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveQueueDialog = false }) {
                        Text(
                            text = "Cancel",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
private fun PlaylistDetailView(
    playlist: PlaylistEntity,
    tracks: List<MediaItemEntity>,
    onBack: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onPlayAll: () -> Unit,
    onQueueAll: () -> Unit,
    onAddToQueue: (MediaItemEntity) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onReorderPlaylist: (List<MediaItemEntity>) -> Unit
) {
    val isLightTheme = MaterialTheme.colorScheme.background == Color.White

    var localTracks by remember(tracks) { mutableStateOf(tracks) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(tracks) {
        if (!isDragging) {
            localTracks = tracks
        }
    }

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var itemHeightPx by remember { mutableStateOf(200f) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Custom Header with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 24.dp, end = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "${localTracks.size} tracks",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        // Action Buttons Row (Play All & Queue All)
        if (localTracks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { onPlayTrack(0) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Play All",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                
                OutlinedButton(
                    onClick = { localTracks.forEach { onAddToQueue(it) } },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Queue All",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Tracks list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (localTracks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "This playlist is empty",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Go to the Library screen and click 'Add to Playlist' on any item.",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                itemsIndexed(localTracks, key = { _, track -> track.id }) { index, track ->
                    val isDraggingItem = index == draggedIndex

                    TrackListItem(
                        index = index,
                        track = track,
                        isDraggingItem = isDraggingItem,
                        dragOffsetY = dragOffsetY,
                        onPlay = { onPlayTrack(index) },
                        onQueue = { onAddToQueue(track) },
                        onRemove = { onRemoveTrack(track.id) },
                        onDragStart = {
                            isDragging = true
                            draggedIndex = index
                            dragOffsetY = 0f
                        },
                        onDragEnd = {
                            onReorderPlaylist(localTracks)
                            isDragging = false
                            draggedIndex = null
                            dragOffsetY = 0f
                        },
                        onDrag = { deltaY ->
                            dragOffsetY += deltaY
                            if (dragOffsetY > itemHeightPx) {
                                val current = draggedIndex
                                if (current != null && current < localTracks.size - 1) {
                                    val next = current + 1
                                    val newList = localTracks.toMutableList()
                                    newList.removeAt(current)
                                    newList.add(next, track)
                                    localTracks = newList
                                    draggedIndex = next
                                    dragOffsetY -= itemHeightPx
                                }
                            } else if (dragOffsetY < -itemHeightPx) {
                                val current = draggedIndex
                                if (current != null && current > 0) {
                                    val prev = current - 1
                                    val newList = localTracks.toMutableList()
                                    newList.removeAt(current)
                                    newList.add(prev, track)
                                    localTracks = newList
                                    draggedIndex = prev
                                    dragOffsetY += itemHeightPx
                                }
                            }
                        },
                        modifier = Modifier.onSizeChanged { size ->
                            if (size.height > 0) {
                                itemHeightPx = size.height.toFloat() + 12f
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackListItem(
    index: Int,
    track: MediaItemEntity,
    isDraggingItem: Boolean,
    dragOffsetY: Float,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLightTheme = MaterialTheme.colorScheme.background == Color.White
    val elementBg = if (isLightTheme) Color.Black.copy(alpha = 0.03f) else GlassWhite
    val borderCol = if (isLightTheme) Color.Black.copy(alpha = 0.10f) else GlassBorder

    val scale = if (isDraggingItem) 1.04f else 1.0f
    val shadowElev = if (isDraggingItem) 12.dp else 0.dp
    val draggingBorder = if (isDraggingItem) BorderStroke(1.5.dp, CyberPink) else BorderStroke(1.dp, borderCol)

    Surface(
        onClick = onPlay,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = if (isDraggingItem) dragOffsetY else 0f
                scaleX = scale
                scaleY = scale
                shadowElevation = shadowElev.toPx()
            },
        color = elementBg,
        shape = RoundedCornerShape(16.dp),
        border = draggingBorder
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle to trigger instant drag reordering
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = if (isDraggingItem) CyberPink else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(24.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            }
                        )
                    }
                    .testTag("track_drag_handle_${track.id}")
            )

            // Index number
            Text(
                text = "${index + 1}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(28.dp)
            )

            // Title and Artist
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = track.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Text(
                    text = track.artist ?: "Unknown Artist",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            // Time and actions
            Text(
                text = formatDuration(track.duration),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 8.dp)
            )

            IconButton(onClick = onQueue) {
                Icon(
                    imageVector = Icons.Default.Queue,
                    contentDescription = "Add to Queue",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove Track",
                    tint = Color.Red.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistItem(
    playlist: PlaylistEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isLightTheme = MaterialTheme.colorScheme.background == Color.White
    val elementBg = if (isLightTheme) Color.Black.copy(alpha = 0.03f) else GlassWhite
    val borderCol = if (isLightTheme) Color.Black.copy(alpha = 0.10f) else GlassBorder

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = elementBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, borderCol)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = playlist.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.Red.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private fun formatDuration(duration: Long): String {
    val sec = duration / 1000
    val min = sec / 60
    val remainingSec = sec % 60
    return "%02d:%02d".format(min, remainingSec)
}


@Composable
fun QueueManagementTab(
    currentQueue: List<androidx.media3.common.MediaItem>,
    currentMedia: androidx.media3.common.MediaItem?,
    onQueueItemClick: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
    onSaveQueueClick: () -> Unit
) {
    val isLightTheme = MaterialTheme.colorScheme.background == Color.White
    val context = androidx.compose.ui.platform.LocalContext.current

    if (currentQueue.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Play queue is empty",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Text(
                text = "Go to the Library screen and click 'Add to Queue' or tap elements to build up a list of recitations or videos.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Queue Action buttons
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onSaveQueueClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Playlist", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            onClearQueue()
                            android.widget.Toast.makeText(context, "Queue cleared", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.15f)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear",
                            tint = Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Queue", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Now playing indicator
            currentMedia?.let { media ->
                val currentIdx = currentQueue.indexOfFirst { it.mediaId == media.mediaId }
                item {
                    Text(
                        text = "NOW PLAYING",
                        color = CyberPink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                    
                    Surface(
                        onClick = { if (currentIdx >= 0) onQueueItemClick(currentIdx) },
                        modifier = Modifier.fillMaxWidth().testTag("queue_now_playing_item"),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CyberPink.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CyberPink.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val isVideo = media.mediaMetadata.extras?.getBoolean("is_video") == true
                                Icon(
                                    imageVector = if (isVideo) Icons.Default.VideoLibrary else Icons.Default.Audiotrack,
                                    contentDescription = null,
                                    tint = CyberPink,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 14.dp)
                            ) {
                                Text(
                                    text = media.mediaMetadata.title?.toString() ?: "Unknown Track",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1
                                )
                                Text(
                                    text = media.mediaMetadata.artist?.toString() ?: "Unknown Artist",
                                    color = Slate400,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                            
                            // Pulse dot indicator
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(CyberPink)
                            )
                        }
                    }
                }
            }

            // Next Up header
            item {
                Text(
                    text = "UP NEXT (${currentQueue.size})",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
            }

            // Queued items
            itemsIndexed(currentQueue) { index, item ->
                val isCurrent = currentMedia != null && item.mediaId == currentMedia.mediaId
                val elementBg = if (isLightTheme) Color.Black.copy(alpha = 0.03f) else GlassWhite
                val borderCol = if (isCurrent) CyberPink.copy(alpha = 0.3f) else if (isLightTheme) Color.Black.copy(alpha = 0.08f) else GlassBorder

                Surface(
                    onClick = { onQueueItemClick(index) },
                    modifier = Modifier.fillMaxWidth().testTag("queue_item_$index"),
                    color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.04f) else elementBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, borderCol)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Reorder buttons (Move Up/Down)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { onMoveQueueItem(index, index - 1) },
                                enabled = index > 0,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Move Up",
                                    tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onMoveQueueItem(index, index + 1) },
                                enabled = index < currentQueue.size - 1,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Move Down",
                                    tint = if (index < currentQueue.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Video or Audio Type Indicator Icon
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val isVideo = item.mediaMetadata.extras?.getBoolean("is_video") == true
                            Icon(
                                imageVector = if (isVideo) Icons.Default.VideoLibrary else Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                              )
                        }

                        // Text content
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = item.mediaMetadata.title?.toString() ?: "Unknown Track",
                                color = if (isCurrent) CyberPink else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                            Text(
                                text = item.mediaMetadata.artist?.toString() ?: "Unknown Artist",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }

                        // Remove from queue clicker
                        IconButton(
                            onClick = { onRemoveQueueItem(index) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = Color.Red.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
