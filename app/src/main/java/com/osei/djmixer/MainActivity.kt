package com.osei.djmixer

import com.osei.djmixer.ui.theme.*

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.osei.djmixer.data.MediaRepository
import com.osei.djmixer.ui.*
import com.osei.djmixer.util.PermissionManager
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

class DJMixerViewModelFactory(
    private val context: android.content.Context,
    private val repository: MediaRepository,
    private val mediaStateHolder: com.osei.djmixer.playback.MediaStateHolder
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DJMixerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DJMixerViewModel(context, repository, mediaStateHolder) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MixerViewModelFactory(
    private val context: android.content.Context,
    private val repository: MediaRepository
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MixerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MixerViewModel(context, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            super.attachBaseContext(newBase.createAttributionContext("default_tag"))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    
    private var crashLogContent: String? = null
    
    private lateinit var viewModel: DJMixerViewModel
    private lateinit var mixerViewModel: MixerViewModel
    private var isInPipMode = mutableStateOf(false)

    private var hasRequestedMediaPermissionsOnStart = false

    private val mediaPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        handleMediaPermissionsResult(results)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            android.util.Log.i("MainActivity", "Notification permission was denied; playback will continue without system notifications.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MainActivity", "App started")
        enableEdgeToEdge()
        
        val app = application as DJMixerApplication
        val repository = MediaRepository(app.database.mediaDao(), app.database.epgDao(), applicationContext)
        
        val djMixerFactory = DJMixerViewModelFactory(applicationContext, repository, app.mediaStateHolder)
        viewModel = androidx.lifecycle.ViewModelProvider(this, djMixerFactory)[DJMixerViewModel::class.java]
        
        val mixerFactory = MixerViewModelFactory(applicationContext, repository)
        mixerViewModel = androidx.lifecycle.ViewModelProvider(this, mixerFactory)[MixerViewModel::class.java]

        checkPermissions()

        val playMediaId = intent?.getStringExtra("play_media_id")
        if (playMediaId != null) {
            viewModel.playByMediaId(playMediaId)
        }

        setContent {
            val orientationLock by viewModel.orientationLock.collectAsStateWithLifecycle()
            val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
            val inPip by isInPipMode
            
            LaunchedEffect(orientationLock) {
                if (!inPip) {
                    requestedOrientation = when (orientationLock) {
                        "portrait" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        "landscape" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
            OseiDjMixerTheme(themeMode = appTheme) {
                MainContent(
                    viewModel = viewModel,
                    mixerViewModel = mixerViewModel,
                    isInPip = inPip,
                    onRequestMediaPermissions = {
                        mediaPermissionLauncher.launch(PermissionManager.getRequiredMediaPermissions())
                    }
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!::viewModel.isInitialized) return
        val currentMedia = viewModel.currentMediaItem.value
        val isVideo = currentMedia?.mediaMetadata?.extras?.getBoolean("is_video") == true
        if (isVideo && viewModel.isPlaying.value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    enterPictureInPictureMode(
                        android.app.PictureInPictureParams.Builder()
                            .setAspectRatio(android.util.Rational(16, 9))
                            .build()
                    )
                } catch (e: Exception) {}
            } else {
                try {
                    @Suppress("DEPRECATION")
                    enterPictureInPictureMode()
                } catch (e: Exception) {}
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode.value = isInPictureInPictureMode
    }

    private fun checkPermissions() {
        val mediaStatus = PermissionManager.checkMediaPermissionStatus(this)
        if (mediaStatus.hasAnyMediaAccess) {
            viewModel.scanMedia()
            requestNotificationPermissionIfNeeded()
        } else if (!hasRequestedMediaPermissionsOnStart) {
            hasRequestedMediaPermissionsOnStart = true
            mediaPermissionLauncher.launch(PermissionManager.getRequiredMediaPermissions())
        }
    }

    private fun handleMediaPermissionsResult(results: Map<String, Boolean>) {
        val status = PermissionManager.evaluateMediaPermissions(results)
        if (status.hasAnyMediaAccess) {
            viewModel.scanMedia()
            if (!status.isFullyGranted) {
                if (status.hasAudioAccess && !status.hasVideoAccess) {
                    showFeedbackToast("Audio access granted. Video files require video storage permission.")
                } else if (!status.hasAudioAccess && status.hasVideoAccess) {
                    showFeedbackToast("Video access granted. Audio files require audio storage permission.")
                }
            }
        } else {
            showFeedbackToast("Storage permission was denied. Local audio and video files cannot be displayed.")
        }

        // Notification permission is requested separately so denial does not block media library
        requestNotificationPermissionIfNeeded()
    }

    fun requestNotificationPermissionIfNeeded() {
        if (PermissionManager.isNotificationPermissionNeeded(this)) {
            try {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Unable to request notification permission: ${e.message}")
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (mixerViewModel.handleKeyEvent(keyCode)) {
            return true
        }
        val isCtrlPressed = event?.isCtrlPressed == true
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> {
                if (event?.repeatCount == 0) {
                    viewModel.togglePlayPause()
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (event?.repeatCount == 0) {
                    viewModel.togglePlayPause()
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (event?.repeatCount == 0) {
                    viewModel.togglePlayPause()
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (event?.repeatCount == 0) {
                    viewModel.togglePlayPause()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isCtrlPressed) {
                    if (event?.repeatCount == 0) {
                        viewModel.skipPrevious()
                        showFeedbackToast("Skipping to previous track")
                    }
                } else {
                    viewModel.seekBack()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isCtrlPressed) {
                    if (event?.repeatCount == 0) {
                        viewModel.skipNext()
                        showFeedbackToast("Skipping to next track")
                    }
                } else {
                    viewModel.seekForward()
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (event?.repeatCount == 0) {
                    viewModel.skipNext()
                    showFeedbackToast("Skipping to next track")
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (event?.repeatCount == 0) {
                    viewModel.skipPrevious()
                    showFeedbackToast("Skipping to previous track")
                }
                true
            }
            KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_N -> {
                if (event?.repeatCount == 0) {
                    viewModel.skipNext()
                    showFeedbackToast("Skipping to next track")
                }
                true
            }
            KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_P -> {
                if (event?.repeatCount == 0) {
                    viewModel.skipPrevious()
                    showFeedbackToast("Skipping to previous track")
                }
                true
            }
            KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_PLUS -> {
                adjustVolume(raise = true)
                true
            }
            KeyEvent.KEYCODE_MINUS -> {
                adjustVolume(raise = false)
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                adjustVolume(raise = true)
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                adjustVolume(raise = false)
                true
            }
            KeyEvent.KEYCODE_M -> {
                if (event?.repeatCount == 0) {
                    toggleMute()
                }
                true
            }
            KeyEvent.KEYCODE_F -> {
                if (event?.repeatCount == 0) {
                    val currentId = viewModel.currentMediaItem.value?.mediaId
                    if (currentId != null) {
                        viewModel.toggleFavorite(currentId)
                        showFeedbackToast("Toggled Favorite status")
                    } else {
                        showFeedbackToast("No media is playing")
                    }
                }
                true
            }
            KeyEvent.KEYCODE_L -> {
                if (event?.repeatCount == 0) {
                    viewModel.toggleRepeatMode()
                    showFeedbackToast("Toggled Loop/Repeat Mode")
                }
                true
            }
            KeyEvent.KEYCODE_R -> {
                if (event?.repeatCount == 0) {
                    viewModel.toggleShuffleMode()
                    showFeedbackToast("Toggled Shuffle Mode")
                }
                true
            }
            KeyEvent.KEYCODE_H -> {
                if (event?.repeatCount == 0) {
                    viewModel.toggleShortcutsHelp()
                }
                true
            }
            KeyEvent.KEYCODE_SLASH -> {
                if (event?.isShiftPressed == true) {
                    if (event.repeatCount == 0) {
                        viewModel.toggleShortcutsHelp()
                    }
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun adjustVolume(raise: Boolean) {
        val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val direction = if (raise) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            direction,
            android.media.AudioManager.FLAG_SHOW_UI
        )
    }

    private fun toggleMute() {
        val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val isMuted = audioManager.isStreamMute(android.media.AudioManager.STREAM_MUSIC)
        audioManager.adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            if (isMuted) android.media.AudioManager.ADJUST_UNMUTE else android.media.AudioManager.ADJUST_MUTE,
            android.media.AudioManager.FLAG_SHOW_UI
        )
        val newMuteState = audioManager.isStreamMute(android.media.AudioManager.STREAM_MUSIC)
        showFeedbackToast(if (newMuteState) "Muted" else "Unmuted")
    }

    private fun showFeedbackToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val playMediaId = intent.getStringExtra("play_media_id")
        if (playMediaId != null) {
            requestNotificationPermissionIfNeeded()
            viewModel.playByMediaId(playMediaId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    viewModel: DJMixerViewModel,
    mixerViewModel: MixerViewModel,
    isInPip: Boolean,
    onRequestMediaPermissions: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val audioList by viewModel.allAudio.collectAsStateWithLifecycle()
    val videoList by viewModel.allVideo.collectAsStateWithLifecycle()
    val streams by viewModel.allStreams.collectAsStateWithLifecycle()
    val history by viewModel.recentHistory.collectAsStateWithLifecycle()
    val allHistory by viewModel.allHistory.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val currentMedia by viewModel.currentMediaItem.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val currentQueue by viewModel.currentQueue.collectAsStateWithLifecycle()
    val controller by viewModel.controller.collectAsStateWithLifecycle()
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val orientationLock by viewModel.orientationLock.collectAsStateWithLifecycle()
    val videoContinuousLooping by viewModel.videoContinuousLooping.collectAsStateWithLifecycle()
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val lastWatchedVideo by viewModel.lastWatchedVideo.collectAsStateWithLifecycle()
    val lastWatchedVideoPosition by viewModel.lastWatchedVideoPosition.collectAsStateWithLifecycle()

    var showFullPlayer by remember { mutableStateOf(false) }
    var isFullScreenVideo by remember { mutableStateOf(false) }
    var selectedMediaForDownload by remember { mutableStateOf<com.osei.djmixer.data.MediaItemEntity?>(null) }

    LaunchedEffect(isInPip) {
        if (isInPip) {
            isFullScreenVideo = true
        }
    }
    var selectedMediaForPlaylist by remember { mutableStateOf<com.osei.djmixer.data.MediaItemEntity?>(null) }
    val showShortcutsHelp by viewModel.showShortcutsHelp.collectAsStateWithLifecycle()

    LaunchedEffect(currentMedia?.mediaId) {
        if (currentMedia != null) {
            showFullPlayer = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = { MainTopAppBar(navController) },
        bottomBar = {
            if (!isInPip) {
                Column {
                    BottomNavigationBar(navController)
                }
            }
        }
    ) { innerPadding ->
        if (showFullPlayer && currentMedia != null && !isFullScreenVideo) {
            ModalBottomSheet(
                onDismissRequest = { showFullPlayer = false },
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)) }
            ) {
                com.osei.djmixer.ui.components.FrostedPlayer(
                    currentMedia = currentMedia,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    queue = currentQueue,
                    sleepTimerRemaining = sleepTimerRemaining,
                    playbackSpeed = playbackSpeed,
                    onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                    shuffleModeEnabled = shuffleModeEnabled,
                    repeatMode = repeatMode,
                    onToggleShuffle = { viewModel.toggleShuffleMode() },
                    onToggleRepeat = { viewModel.toggleRepeatMode() },
                    controller = if (isFullScreenVideo) null else controller,
                    onToggle = { viewModel.togglePlayPause() },
                    onPrevious = { viewModel.skipPrevious() },
                    onNext = { viewModel.skipNext() },
                    onSeek = { viewModel.seekTo(it) },
                    onQueueItemClick = { index ->
                        viewModel.seekToItem(index)
                    },
                    onRemoveQueueItem = { viewModel.removeQueueItem(it) },
                    onMoveQueueItem = { from, to -> viewModel.moveQueueItem(from, to) },
                    onSetSleepTimer = { viewModel.setSleepTimer(it) },
                    orientationLock = orientationLock,
                    onSetOrientationLock = { viewModel.setOrientationLock(it) },
                    videoContinuousLooping = videoContinuousLooping,
                    onToggleVideoContinuousLooping = { viewModel.setVideoContinuousLooping(!videoContinuousLooping) },
                    onToggleFullScreen = { isFullScreenVideo = true },
                    modifier = Modifier.padding(bottom = 48.dp)
                )
            }
        }

        if (selectedMediaForPlaylist != null) {
            AlertDialog(
                onDismissRequest = { selectedMediaForPlaylist = null },
                title = { Text("Add to Playlist") },
                text = {
                    LazyColumn {
                        items(playlists) { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.name) },
                                modifier = Modifier.clickable {
                                    viewModel.addMediaToPlaylist(playlist.id, selectedMediaForPlaylist!!)
                                    selectedMediaForPlaylist = null
                                }
                            )
                        }
                    }
                },
                confirmButton = {}
            )
        }

        if (showShortcutsHelp) {
            com.osei.djmixer.ui.components.KeyboardShortcutsDialog(
                onDismissRequest = { viewModel.toggleShortcutsHelp() }
            )
        }

        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("mixer") {
                val fullMediaList = remember(audioList, videoList, streams) { audioList + videoList + streams }
                MixerScreen(
                    viewModel = mixerViewModel,
                    allMedia = fullMediaList,
                    onSettingsClick = { navController.navigate("settings") },
                    onListenWithHtml5 = { filePath, title ->
                        val base64Path = android.util.Base64.encodeToString(filePath.absolutePath.toByteArray(Charsets.UTF_8), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                        navController.navigate("html5_player?filePath=$base64Path&title=$encodedTitle")
                    }
                )
            }
            composable("home") {
                val fullMediaList = remember(audioList, videoList) { audioList + videoList }
                HomeDashboard(
                    recentHistory = history,
                    playbackHistory = allHistory,
                    allMedia = fullMediaList,
                    favorites = favorites,
                    lastWatchedVideo = lastWatchedVideo,
                    lastWatchedVideoPosition = lastWatchedVideoPosition,
                    onMediaClick = { item -> 
                        val contextList = when {
                            (item.category == "TV" || item.category == "Streams") -> streams
                            item.isVideo -> videoList
                            else -> audioList
                        }
                        viewModel.playMedia(item, contextList) 
                    },
                    onAddToQueue = { viewModel.addToQueue(it) },
                    onDownload = { selectedMediaForDownload = it },
                    onToggleFavorite = { viewModel.toggleFavorite(it) }
                )
            }
            composable("library") {
                val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
                val dbSearchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

                val downloadingItems by viewModel.downloadingItems.collectAsStateWithLifecycle()
                val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
                
                LibraryScreen(
                    isOnline = isOnline,
                    audioList = audioList,
                    videoList = videoList,
                    streams = streams.filter { it.category == "Radio" },
                    favorites = favorites,
                    searchResults = searchResults,
                    dbSearchQuery = dbSearchQuery,
                    onUpdateSearchQuery = { viewModel.updateSearchQuery(it) },
                    playbackHistory = allHistory,
                    controller = if (isFullScreenVideo) null else controller,
                    onFullScreenToggle = { isFullScreenVideo = true },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onMediaClick = { item -> 
                        val contextList = when {
                            (item.category == "TV" || item.category == "Streams") -> streams
                            item.isVideo -> videoList
                            else -> audioList
                        }
                        viewModel.playMedia(item, contextList) 
                    },
                    onAddToQueue = { viewModel.addToQueue(it) },
                    onAddToPlaylist = { selectedMediaForPlaylist = it },
                    onAddStream = { title, artist, uri, isVideo ->
                        viewModel.addCustomStream(title, artist, uri, isVideo)
                    },
                    onDeleteMedia = { viewModel.deleteMedia(it) },
                    onRenameMedia = { id, name -> viewModel.renameMedia(id, name) },
                    onSetMediaPrivacy = { id, private -> viewModel.setMediaPrivacy(id, private) },
                    onSetAsRingtone = { viewModel.setAsRingtone(it) },
                    onAddNextToQueue = { viewModel.addNextToQueue(it) },
                    onListenWithHtml5 = { item ->
                        val base64Path = android.util.Base64.encodeToString(item.uri.toByteArray(Charsets.UTF_8), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                        val encodedTitle = java.net.URLEncoder.encode(item.title, "UTF-8")
                        navController.navigate("html5_player?filePath=$base64Path&title=$encodedTitle")
                    },
                    onDownloadYoutube = { url, title, artist, asMp3 ->
                        viewModel.downloadYoutubeVideo(url, title, artist, asMp3)
                    },
                    onScanUsb = {
                        val mediaStatus = PermissionManager.checkMediaPermissionStatus(context)
                        if (mediaStatus.hasAnyMediaAccess) {
                            viewModel.scanMedia()
                        } else {
                            onRequestMediaPermissions()
                        }
                    },
                    downloadingItems = downloadingItems,
                    onPlayMultiple = { items -> viewModel.setQueue(items, 0) }
                )
            }
            composable("favorites") {
                PlaylistScreen(
                    playlists = playlists,
                    favorites = favorites,
                    history = history,
                    onCreatePlaylist = { viewModel.createPlaylist(it) },
                    onDeletePlaylist = { viewModel.deletePlaylist(it) },
                    getMediaForPlaylist = { id -> viewModel.getMediaForPlaylist(id) },
                    onPlayPlaylist = { items, index -> viewModel.setQueue(items, index) },
                    onAddToQueue = { viewModel.addToQueue(it) },
                    onAppendPlaylistToQueue = { items ->
                        items.forEach { viewModel.addToQueue(it) }
                    },
                    onRemoveMediaFromPlaylist = { playlistId, mediaId ->
                        viewModel.removeMediaFromPlaylist(playlistId, mediaId)
                    },
                    onReorderPlaylist = { playlistId, orderedTracks ->
                        viewModel.reorderPlaylist(playlistId, orderedTracks.map { it.id })
                    },
                    currentQueue = currentQueue,
                    currentMedia = currentMedia,
                    onQueueItemClick = { viewModel.seekToItem(it) },
                    onRemoveQueueItem = { viewModel.removeQueueItem(it) },
                    onMoveQueueItem = { from, to -> viewModel.moveQueueItem(from, to) },
                    onClearQueue = { viewModel.clearQueue() },
                    onSaveQueueAsPlaylist = { viewModel.saveQueueAsPlaylist(it, currentQueue) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onPlayMedia = { viewModel.setQueue(listOf(it), 0) }
                )
            }
            composable("radio") {
                RadioScreen(
                    player = controller,
                    streams = streams.filter { it.category == "Radio" },
                    currentMedia = currentMedia,
                    recentHistory = history,
                    isPlaying = isPlaying,
                    favorites = favorites.map { it.id }.toSet(),
                    onPlayRadio = { item, contextList ->
                        viewModel.playMedia(item, contextList)
                    },
                    onToggleFavorite = { id ->
                        viewModel.toggleFavorite(id)
                    },
                    onAddRadioStation = { title, artist, uri, region ->
                        viewModel.addCustomStream(title, artist, uri, false, region)
                    },
                    onDeleteRadioStation = { id ->
                        viewModel.deleteMedia(id)
                    }
                )
            }
            composable("highlights") {
                val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
                com.osei.djmixer.ui.LiveTvScreen(viewModel = viewModel,
                    title = "HIGHLIGHTS & UPDATES",
                    player = controller,
                    streams = streams.filter { it.category == "Highlights" },
                    recentHistory = history,
                    currentMedia = currentMedia,
                    isPlaying = isPlaying,
                    isOnline = isOnline,
                    favorites = favorites.map { it.id }.toSet(),
                    onPlayTv = { items, index -> viewModel.setQueue(items, index) },
                    onToggleFavorite = { id -> viewModel.toggleFavorite(id) },
                    onAddTvStation = { title, artist, uri, region -> viewModel.addCustomStream(title, artist, uri, true, region) },
                    onDeleteTvStation = { id -> viewModel.deleteMedia(id) },
                    onEditTvStation = { id, title, artist, uri, region -> viewModel.editCustomStream(id, title, artist, uri, region) },
                    onImportM3u = { url -> viewModel.importM3uPlaylist(url) }
                )
            }
            composable("videos") {
                com.osei.djmixer.ui.DeviceVideosScreen(
                    videos = videoList,
                    favorites = favorites.map { it.id }.toSet(),
                    onPlayVideo = { video, contextList ->
                        viewModel.playMedia(video, contextList)
                    },
                    onToggleFavorite = { id ->
                        viewModel.toggleFavorite(id)
                    }
                )
            }
            composable("tv") {
                val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
                com.osei.djmixer.ui.LiveTvScreen(viewModel = viewModel,
                    player = controller,
                    streams = streams.filter { (it.category == "TV" || (it.category == "Streams" && !it.title.contains("radio", ignoreCase = true) && !it.artist.toString().contains("radio", ignoreCase = true))) },
                    recentHistory = history,
                    currentMedia = currentMedia,
                    isPlaying = isPlaying,
                    isOnline = isOnline,
                    favorites = favorites.map { it.id }.toSet(),
                    onPlayTv = { items, index ->
                        viewModel.setQueue(items, index)
                    },
                    onToggleFavorite = { id ->
                        viewModel.toggleFavorite(id)
                    },
                    onAddTvStation = { title, artist, uri, region ->
                        viewModel.addCustomStream(title, artist, uri, true, region)
                    },
                    onDeleteTvStation = { id ->
                        viewModel.deleteMedia(id)
                    },
                    onEditTvStation = { id, title, artist, uri, region ->
                        viewModel.editCustomStream(id, title, artist, uri, region)
                    },
                    onImportM3u = { url ->
                        viewModel.importM3uPlaylist(url)
                    }
                )
            }
            composable("studio") {
                com.osei.djmixer.ui.StudioBroadcastScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("legal") {
                com.osei.djmixer.ui.LegalScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("privacy_policy") {
                com.osei.djmixer.ui.PrivacyPolicyScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("terms_of_service") {
                com.osei.djmixer.ui.TermsOfServiceScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                val orientationLock by viewModel.orientationLock.collectAsStateWithLifecycle()
                val currentTheme by viewModel.appTheme.collectAsStateWithLifecycle()
                val streamQuality by viewModel.streamQuality.collectAsStateWithLifecycle()
                val defaultPlaybackSpeed by viewModel.defaultPlaybackSpeed.collectAsStateWithLifecycle()
                val prebufferMode by viewModel.prebufferMode.collectAsStateWithLifecycle()
                val privacyLock by viewModel.privacyLock.collectAsStateWithLifecycle()
                
                SettingsScreen(
                    orientationLock = orientationLock,
                    onSetOrientationLock = { viewModel.setOrientationLock(it) },
                    videoContinuousLooping = videoContinuousLooping,
                    onSetVideoContinuousLooping = { viewModel.setVideoContinuousLooping(it) },
                    appTheme = currentTheme,
                    onSetAppTheme = { viewModel.setAppTheme(it) },
                    streamQuality = streamQuality,
                    onSetStreamQuality = { viewModel.setStreamQuality(it) },
                    defaultPlaybackSpeed = defaultPlaybackSpeed,
                    onSetDefaultPlaybackSpeed = { viewModel.setDefaultPlaybackSpeed(speed = it) },
                    prebufferMode = prebufferMode,
                    onSetPrebufferMode = { viewModel.setPrebufferMode(it) },
                    privacyLock = privacyLock,
                    onSetPrivacyLock = { viewModel.setPrivacyLock(it) },
                    onManagementAction = { viewModel.performManagementAction(it) },
                    onScanMedia = {
                        val mediaStatus = PermissionManager.checkMediaPermissionStatus(context)
                        if (mediaStatus.hasAnyMediaAccess) {
                            viewModel.scanMedia()
                        } else {
                            onRequestMediaPermissions()
                        }
                    },
                    onShowKeyboardShortcuts = { viewModel.toggleShortcutsHelp() },
                    onShowLegal = { navController.navigate("legal") },
                    onShowPrivacyPolicy = { navController.navigate("privacy_policy") },
                    onShowTermsOfService = { navController.navigate("terms_of_service") },
                    onClearLiveTvCache = { 
                        com.osei.djmixer.playback.MediaCacheManager.clearCache(context)
                        android.widget.Toast.makeText(context, "Live TV cache cleared", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onApplyPreset = { preset -> mixerViewModel.applyPreset(preset) },
                    onResetOption = { opt -> 
                        when(opt) {
                            "Clear Cache" -> com.osei.djmixer.playback.MediaCacheManager.clearCache(context)
                            "Clear Recently Played" -> viewModel.clearRecentlyPlayed()
                            "Clear Favorites" -> viewModel.clearFavorites()
                            "Reset Equalizer" -> mixerViewModel.resetEq()
                            "Restore Default Settings" -> mixerViewModel.resetEq()
                        }
                    }
                )
            }
            composable(
                route = "html5_player?filePath={filePath}&title={title}",
                arguments = listOf(
                    navArgument("filePath") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
                val title = backStackEntry.arguments?.getString("title") ?: "Mix Recording"
                Html5PlayerScreen(
                    filePath = filePath,
                    mixTitle = title,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        if (selectedMediaForDownload != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedMediaForDownload = null },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "Download Options",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    ListItem(
                        headlineContent = { Text("Download Video (MP4)") },
                        supportingContent = { Text("High quality video for offline viewing") },
                        leadingContent = { Icon(Icons.Default.Videocam, null) },
                        modifier = Modifier.clickable {
                            viewModel.downloadMedia(selectedMediaForDownload!!, asMp3 = false)
                            selectedMediaForDownload = null
                        }
                    )
                    
                    ListItem(
                        headlineContent = { Text("Convert & Download Audio (MP3)") },
                        supportingContent = { Text("Extract audio for music-only playback") },
                        leadingContent = { Icon(Icons.Default.AudioFile, null) },
                        modifier = Modifier.clickable {
                            viewModel.downloadMedia(selectedMediaForDownload!!, asMp3 = true)
                            selectedMediaForDownload = null
                        }
                    )
                }
            }
        }
    }

    // Floating Now Playing Button (FAB)
    if (currentMedia != null && !showFullPlayer && !isFullScreenVideo && !isInPip) {
        var rotation by remember { mutableStateOf(0f) }
        if (isPlaying) {
            LaunchedEffect(Unit) {
                while (true) {
                    rotation = (rotation + 2f) % 360f
                    kotlinx.coroutines.delay(16)
                }
            }
        }
        
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 100.dp)
                .size(56.dp)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    ),
                    shape = RoundedCornerShape(50)
                )
                .clickable { showFullPlayer = true }
                .padding(2.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Now Playing",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer(rotationZ = rotation)
            )
            
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                )
            }
        }
    }

    if (isFullScreenVideo && currentMedia != null && controller != null) {
        val isVideo = currentMedia?.mediaMetadata?.extras?.getBoolean("is_video") == true
        if (isVideo) {
            com.osei.djmixer.ui.VideoPlayerScreen(
                player = controller!!,
                isInPip = isInPip,
                orientationLock = orientationLock,
                onBack = { isFullScreenVideo = false }
            )
        } else {
            com.osei.djmixer.ui.AudioPlayerScreen(
                player = controller!!,
                sleepTimerRemaining = sleepTimerRemaining,
                onSetSleepTimer = { viewModel.setSleepTimer(it) },
                onSetAlarm = { time, uri -> viewModel.setAlarm(time, uri) },
                onBack = { isFullScreenVideo = false }
            )
        }
    }
}
}

@Composable
fun BottomNavigationBar(navController: androidx.navigation.NavController) {
    val navItems = listOf(
        NavItem("Home", "home", Icons.Default.Home),
        NavItem("Library", "library", Icons.Default.LibraryMusic),
        NavItem("Mixer", "mixer", Icons.Default.Tune),
        NavItem("Radio", "radio", Icons.Default.Radio),
        NavItem("Live TV", "tv", Icons.Default.Tv),
        NavItem("Device Videos", "videos", Icons.Default.OndemandVideo),
        NavItem("My Music", "favorites", Icons.AutoMirrored.Filled.QueueMusic),
        NavItem("Studio", "studio", Icons.Default.CameraAlt),
        NavItem("Settings", "settings", Icons.Default.Settings)
    )
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val containerColor = when (MaterialTheme.colorScheme.background) {
        Color.White -> Color(0xF9F1F5F9)
        Color.Black -> Color(0xF2121212)
        else -> Color(0xF20A1529) // CyberDark / Deep Navy
    }
    val borderColor = when (MaterialTheme.colorScheme.background) {
        Color.White -> Color(0x33000000)
        else -> Color(0x33FFFFFF)
    }

    Surface(
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(4.dp))
            navItems.forEach { item ->
                val isSelected = currentRoute == item.route
                val selectedColor = MaterialTheme.colorScheme.primary
                val unselectedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                val contentColor = if (isSelected) selectedColor else unselectedColor
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                    )
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.title,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = item.title,
                        color = contentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

data class NavItem(val title: String, val route: String, val icon: ImageVector)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar(navController: androidx.navigation.NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val noTopBarRoutes = listOf(
        "mixer",
        "studio",
        "legal",
        "html5_player?filePath={filePath}&title={title}"
    )
    if (currentRoute in noTopBarRoutes) return

    var menuExpanded by remember { mutableStateOf(false) }
    
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = "Osei DJ Mixer",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            Image(
                painter = painterResource(id = R.drawable.dj_mixer_logo_1783155328060),
                contentDescription = "Osei DJ Mixer Logo",
                modifier = Modifier
                    .size(48.dp)
                    .padding(start = 8.dp)
            )
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.osei_profile),
                    contentDescription = "Osei Solomon",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Text(
                            text = "🇬🇭",
                            fontSize = 24.sp,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(4.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("🇬🇭 Proudly Made in Ghana") },
                            onClick = { menuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("About Osei DJ Mixer") },
                            onClick = { menuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                menuExpanded = false
                                navController.navigate("settings")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Contact Support") },
                            onClick = { menuExpanded = false }
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}
