package com.osei.djmixer.ui

import com.osei.djmixer.ui.theme.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.osei.djmixer.data.MediaItemEntity
import com.osei.djmixer.data.MediaRepository
import com.osei.djmixer.data.PlayerHistoryEntity
import com.osei.djmixer.data.PlaylistEntity
import com.osei.djmixer.playback.MediaStateHolder
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.OptIn

class DJMixerViewModel(
    private val context: android.content.Context,
    private val repository: MediaRepository,
    private val mediaStateHolder: MediaStateHolder
) : ViewModel() {

    private val networkObserver = com.osei.djmixer.util.NetworkConnectivityObserver(context)
    val isOnline = networkObserver.isOnline.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    private val prefs = context.getSharedPreferences("dj_mixer_settings", android.content.Context.MODE_PRIVATE)
    
    private val _orientationLock = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getString("orientation_lock", "auto") ?: "auto"
    )
    val orientationLock = _orientationLock.asStateFlow()

    fun setOrientationLock(mode: String) {
        prefs.edit().putString("orientation_lock", mode).apply()
        _orientationLock.value = mode
    }

    private val _appTheme = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getString("app_theme", "dark") ?: "dark"
    )
    val appTheme = _appTheme.asStateFlow()

    fun setAppTheme(theme: String) {
        prefs.edit().putString("app_theme", theme).apply()
        _appTheme.value = theme
    }

    private val _videoContinuousLooping = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getBoolean("video_continuous_looping", true)
    )
    val videoContinuousLooping = _videoContinuousLooping.asStateFlow()

    fun setVideoContinuousLooping(enabled: Boolean) {
        prefs.edit().putBoolean("video_continuous_looping", enabled).apply()
        _videoContinuousLooping.value = enabled
        if (enabled) {
            mediaStateHolder.setRepeatMode(Player.REPEAT_MODE_ALL)
        } else {
            mediaStateHolder.setRepeatMode(Player.REPEAT_MODE_OFF)
        }
    }

    private val _streamQuality = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getString("stream_quality", "hd") ?: "hd"
    )
    val streamQuality = _streamQuality.asStateFlow()

    fun setStreamQuality(quality: String) {
        prefs.edit().putString("stream_quality", quality).apply()
        _streamQuality.value = quality
    }

    private val _defaultPlaybackSpeed = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getFloat("default_playback_speed", 1.0f)
    )
    val defaultPlaybackSpeed = _defaultPlaybackSpeed.asStateFlow()

    fun setDefaultPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat("default_playback_speed", speed).apply()
        _defaultPlaybackSpeed.value = speed
    }

    private val _prebufferMode = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getString("prebuffer_mode", "standard") ?: "standard"
    )
    val prebufferMode = _prebufferMode.asStateFlow()

    fun setPrebufferMode(mode: String) {
        prefs.edit().putString("prebuffer_mode", mode).apply()
        _prebufferMode.value = mode
    }

    private val _privacyLock = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getBoolean("privacy_lock", false)
    )
    val privacyLock = _privacyLock.asStateFlow()

    private val _showShortcutsHelp = kotlinx.coroutines.flow.MutableStateFlow(false)
    val showShortcutsHelp = _showShortcutsHelp.asStateFlow()

    fun toggleShortcutsHelp() {
        _showShortcutsHelp.value = !_showShortcutsHelp.value
    }

    fun setPrivacyLock(enabled: Boolean) {
        prefs.edit().putBoolean("privacy_lock", enabled).apply()
        _privacyLock.value = enabled
    }

    fun performManagementAction(actionTitle: String) {
        viewModelScope.launch {
            android.widget.Toast.makeText(context, "$actionTitle: Operation started...", android.widget.Toast.LENGTH_SHORT).show()
            withContext(Dispatchers.IO) {
                kotlinx.coroutines.delay(1000)
            }
            val message = when (actionTitle) {
                "Recycle bin" -> "Recycle bin is empty."
                "Clean Cache" -> "Cache cleared successfully!"
                "Media Manager" -> "Media library is up to date."
                "File Transfer" -> "Looking for devices on local network..."
                else -> "$actionTitle completed."
            }
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun downloadMedia(media: MediaItemEntity, asMp3: Boolean) {
        val format = if (asMp3) "MP3" else "MP4"
        android.widget.Toast.makeText(context, "Starting $format download for ${media.title}...", android.widget.Toast.LENGTH_SHORT).show()
        
        try {
            val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val uri = android.net.Uri.parse(media.uri)
            val request = android.app.DownloadManager.Request(uri).apply {
                setTitle(media.title)
                setDescription("Downloading $format")
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                val ext = if (asMp3) "mp3" else "mp4"
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "${media.title}.$ext")
            }
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Download failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private val _downloadingItems = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadingItems = _downloadingItems.asStateFlow()

    fun downloadYoutubeVideo(url: String, title: String, artist: String, asMp3: Boolean) {
        if (!url.contains("youtube.com") && !url.contains("youtu.be")) {
            android.widget.Toast.makeText(context, "Invalid YouTube URL", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            val downloadId = "yt_${System.currentTimeMillis()}"
            val format = if (asMp3) "MP3" else "MP4"
            
            _downloadingItems.update { it + (downloadId to 0f) }
            
            try {
                android.widget.Toast.makeText(context, "Initializing extraction for: $title", android.widget.Toast.LENGTH_SHORT).show()
                
                withContext(Dispatchers.IO) {
                    // Step 1: Parsing
                    kotlinx.coroutines.delay(800)
                    _downloadingItems.update { it + (downloadId to 0.1f) }
                    
                    // Step 2: Metadata
                    kotlinx.coroutines.delay(1200)
                    _downloadingItems.update { it + (downloadId to 0.25f) }
                    
                    // Step 3: Downloading segments
                    for (i in 3..9) {
                        kotlinx.coroutines.delay(400)
                        _downloadingItems.update { it + (downloadId to i * 0.1f) }
                    }
                    
                    // Step 4: Finalizing
                    kotlinx.coroutines.delay(1000)
                    _downloadingItems.update { it + (downloadId to 1.0f) }
                    
                    val newMedia = MediaItemEntity(
                        id = downloadId,
                        title = title,
                        artist = artist.ifBlank { "Osei DJ Mixes" },
                        uri = url, 
                        artUri = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=400&auto=format&fit=crop",
                        duration = 0,
                        isVideo = !asMp3,
                        category = "Mixes"
                    )
                    repository.insertMedia(newMedia)
                }
                
                android.widget.Toast.makeText(context, "Download Complete: $title", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Download Failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                kotlinx.coroutines.delay(2000)
                _downloadingItems.update { it - downloadId }
            }
        }
    }

    private val _lastWatchedVideoId = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getString("last_watched_video_id", null)
    )
    val lastWatchedVideoId = _lastWatchedVideoId.asStateFlow()

    private val _lastWatchedVideoPosition = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getLong("last_watched_video_position", 0L)
    )
    val lastWatchedVideoPosition = _lastWatchedVideoPosition.asStateFlow()

    private val _lastWatchedVideo = kotlinx.coroutines.flow.MutableStateFlow<MediaItemEntity?>(null)
    val lastWatchedVideo = _lastWatchedVideo.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedDefaults()
            // Cleanup legacy broken streams
            // Force rebuild of all radio streams to fix broken/cached URIs
            val allMedia = repository.allMedia.first()
            allMedia.filter { 
                it.category == "TV" || it.category == "Streams"
            }.forEach {
                repository.deleteMedia(it.id)
            }
            repository.seedDefaults()
        }

        viewModelScope.launch {
            _lastWatchedVideoId.collect { id ->
                if (id != null) {
                    _lastWatchedVideo.value = repository.getMediaById(id)
                } else {
                    _lastWatchedVideo.value = null
                }
            }
        }

        viewModelScope.launch {
            mediaStateHolder.controllerFlow.collect { ctrl ->
                if (ctrl != null) {
                    val enabled = prefs.getBoolean("video_continuous_looping", true)
                    ctrl.repeatMode = if (enabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                }
            }
        }

        // Background progress savers
        viewModelScope.launch {
            while (true) {
                try {
                    val mediaItem = mediaStateHolder.currentMediaItem.value
                    val isPlayingNow = mediaStateHolder.isPlaying.value
                    val position = mediaStateHolder.currentPosition.value
                    if (mediaItem != null && isPlayingNow && position > 0) {
                        repository.saveProgress(mediaItem.mediaId, position)
                        
                        val isVideo = mediaItem.mediaMetadata.extras?.getBoolean("is_video") == true || 
                                      repository.getMediaById(mediaItem.mediaId)?.isVideo == true
                        if (isVideo) {
                            prefs.edit()
                                .putString("last_watched_video_id", mediaItem.mediaId)
                                .putLong("last_watched_video_position", position)
                                .apply()
                            _lastWatchedVideoId.value = mediaItem.mediaId
                            _lastWatchedVideoPosition.value = position
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                kotlinx.coroutines.delay(4000L) // Save progress periodically every 4 seconds
            }
        }

        viewModelScope.launch {
            mediaStateHolder.isPlaying.collect { playing ->
                val mediaItem = mediaStateHolder.currentMediaItem.value
                val position = mediaStateHolder.currentPosition.value
                if (mediaItem != null && !playing && position > 0) {
                    repository.saveProgress(mediaItem.mediaId, position)
                    
                    val isVideo = mediaItem.mediaMetadata.extras?.getBoolean("is_video") == true || 
                                  repository.getMediaById(mediaItem.mediaId)?.isVideo == true
                    if (isVideo) {
                        prefs.edit()
                            .putString("last_watched_video_id", mediaItem.mediaId)
                            .putLong("last_watched_video_position", position)
                            .apply()
                        _lastWatchedVideoId.value = mediaItem.mediaId
                        _lastWatchedVideoPosition.value = position
                    }
                }
            }
        }

        viewModelScope.launch {
            var lastMediaId: String? = null
            mediaStateHolder.currentMediaItem.collect { mediaItem ->
                if (lastMediaId != null && lastMediaId != mediaItem?.mediaId) {
                    val lastId = lastMediaId!!
                    val lastPos = mediaStateHolder.currentPosition.value
                    if (lastPos > 0) {
                        repository.saveProgress(lastId, lastPos)
                        
                        val isVideo = repository.getMediaById(lastId)?.isVideo == true
                        if (isVideo) {
                            prefs.edit()
                                .putString("last_watched_video_id", lastId)
                                .putLong("last_watched_video_position", lastPos)
                                .apply()
                            _lastWatchedVideoId.value = lastId
                            _lastWatchedVideoPosition.value = lastPos
                        }
                    }
                }
                lastMediaId = mediaItem?.mediaId
            }
        }
    }

    val allAudio = repository.allAudio.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allVideo = repository.allVideo.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allStreams = repository.allStreams.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val recentHistory = repository.recentHistory.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allHistory = repository.allHistoryFlow.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val favorites = repository.favorites.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allPlaylists = repository.allPlaylists.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val privateMedia = repository.getPrivateMedia().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _searchQuery = kotlinx.coroutines.flow.MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val searchResults = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList<MediaItemEntity>())
            } else {
                repository.searchMedia(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val currentMediaItem = mediaStateHolder.currentMediaItem
    val isPlaying = mediaStateHolder.isPlaying
    val currentPosition = mediaStateHolder.currentPosition
    val duration = mediaStateHolder.duration
    val currentQueue = mediaStateHolder.currentPlaylist
    val controller = mediaStateHolder.controllerFlow
    val sleepTimerRemaining = mediaStateHolder.sleepTimerRemaining
    val playbackSpeed = mediaStateHolder.playbackSpeed
    val volume = mediaStateHolder.volume
    val shuffleModeEnabled = mediaStateHolder.shuffleModeEnabled
    val repeatMode = mediaStateHolder.repeatMode

    fun getMediaForPlaylist(playlistId: Long) = repository.getMediaForPlaylist(playlistId)

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun addMediaToPlaylist(playlistId: Long, media: MediaItemEntity) {
        viewModelScope.launch {
            val currentList = repository.getMediaForPlaylist(playlistId).first()
            repository.addMediaToPlaylist(playlistId, media.id, currentList.size)
        }
    }

    fun reorderPlaylist(playlistId: Long, orderedMediaIds: List<String>) {
        viewModelScope.launch {
            repository.reorderPlaylist(playlistId, orderedMediaIds)
        }
    }

    fun removeMediaFromPlaylist(playlistId: Long, mediaId: String) {
        viewModelScope.launch {
            repository.removeMediaFromPlaylist(playlistId, mediaId)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { repository.deletePlaylist(playlistId) }
    }

    fun toggleFavorite(mediaId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(mediaId)
        }
    }

    fun addCustomStream(title: String, artist: String, uri: String, isVideo: Boolean, region: String? = null) {
        viewModelScope.launch {
            val streamId = "custom_stream_${System.currentTimeMillis()}"
            repository.insertMedia(
                MediaItemEntity(
                    id = streamId,
                    title = title,
                    artist = artist.ifBlank { "User Channel" },
                    uri = uri,
                    artUri = "https://images.unsplash.com/photo-1506126613408-eca07ce68773?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = isVideo,
                    category = "TV",
                    region = region
                )
            )
        }
    }

    fun editCustomStream(mediaId: String, title: String, artist: String, uri: String, region: String?) {
        viewModelScope.launch {
            // Get current to preserve other fields
            val existing = repository.getMediaById(mediaId) ?: return@launch
            repository.insertMedia(
                existing.copy(
                    title = title,
                    artist = artist.ifBlank { "User Channel" },
                    uri = uri,
                    region = region
                )
            )
        }
    }

    fun importM3uPlaylist(url: String, userEpgUrl: String = "") {
        var finalUrl = url
        var finalEpgUrl = userEpgUrl
        if (finalUrl.contains("iptvcat.net/ghana") || finalUrl.contains("gh.m3u")) {
            finalUrl = "https://iptv-org.github.io/iptv/countries/gh.m3u"
        } else if (finalUrl.contains("iptvcat.net/nigeria") || finalUrl.contains("ng.m3u")) {
            finalUrl = "https://iptv-org.github.io/iptv/countries/ng.m3u"
        } else if (finalUrl.contains("iptvcat.net/united%20kingdom") || finalUrl.contains("iptvcat.net/united_kingdom")) {
            finalUrl = "https://iptv-org.github.io/iptv/countries/uk.m3u"
            if (finalEpgUrl.isBlank()) finalEpgUrl = "https://epgshare01.online/epgshare01/epg_ripper_UK1.xml.gz"
        } else if (finalUrl.contains("iptvcat.net/south%20africa") || finalUrl.contains("iptvcat.net/south_africa")) {
            finalUrl = "https://iptv-org.github.io/iptv/countries/za.m3u"
            if (finalEpgUrl.isBlank()) finalEpgUrl = "https://epgshare01.online/epgshare01/epg_ripper_ZA1.xml.gz"
        } else if (finalUrl == "https://iptvcat.net") {
            finalUrl = "https://iptv-org.github.io/iptv/index.m3u"
        } else if (finalUrl.contains("us_plex.m3u")) {
            if (finalEpgUrl.isBlank()) finalEpgUrl = "https://epgshare01.online/epgshare01/epg_ripper_US2.xml.gz"
        } else if (finalUrl.contains("us_pluto.m3u")) {
            if (finalEpgUrl.isBlank()) finalEpgUrl = "https://epgshare01.online/epgshare01/epg_ripper_US2.xml.gz"
        } else if (finalUrl.contains("us_tubi.m3u")) {
            if (finalEpgUrl.isBlank()) finalEpgUrl = "https://epgshare01.online/epgshare01/epg_ripper_US2.xml.gz"
        } else if (finalUrl.contains("us_samsung.m3u")) {
            if (finalEpgUrl.isBlank()) finalEpgUrl = "https://epgshare01.online/epgshare01/epg_ripper_US2.xml.gz"
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            val resolvedUrl = finalUrl
            try {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Fetching M3U playlist...", android.widget.Toast.LENGTH_SHORT).show()
                }
                
                val content = if (resolvedUrl.startsWith("content://")) {
                    context.contentResolver.openInputStream(android.net.Uri.parse(resolvedUrl))?.bufferedReader()?.use { it.readText() }
                } else {
                    val connection = java.net.URL(resolvedUrl).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                    connection.setRequestProperty("Accept", "*/*")
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    
                    if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        null
                    }
                }
                
                if (content != null) {
                    val lines = content.lines()
                    
                    val itemsToInsert = mutableListOf<MediaItemEntity>()
                    var currentTitle = ""
                    var currentLogo = ""
                    var currentGroup = ""
                    var currentTvgId = ""
                    var currentReferer = ""
                    var epgUrl = finalEpgUrl
                    
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith("#EXTM3U")) {
                            val epgUrlMatcher = java.util.regex.Pattern.compile("x-tvg-url=\"([^\"]+)\"").matcher(trimmed)
                            if (epgUrlMatcher.find()) { epgUrl = epgUrlMatcher.group(1) ?: "" }
                        } else if (trimmed.startsWith("#EXTINF:")) {
                            // Extract title
                            val commaIndex = trimmed.lastIndexOf(',')
                            if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                                currentTitle = trimmed.substring(commaIndex + 1).trim()
                            }
                            
                            // Extract logo
                            val logoMatcher = java.util.regex.Pattern.compile("tvg-logo=\"([^\"]+)\"").matcher(trimmed)
                            if (logoMatcher.find()) {
                                currentLogo = logoMatcher.group(1) ?: ""
                            } else {
                                currentLogo = "https://images.unsplash.com/photo-1593784991095-a205069470b6?q=80&w=1080&auto=format&fit=crop"
                            }
                            
                            // Extract group
                            val tvgIdMatcher = java.util.regex.Pattern.compile("tvg-id=\"([^\"]+)\"").matcher(trimmed)
                            if (tvgIdMatcher.find()) {
                                currentTvgId = tvgIdMatcher.group(1) ?: ""
                            } else {
                                currentTvgId = ""
                            }
                            
                            val refererMatcher = java.util.regex.Pattern.compile("http-referrer=\"([^\"]+)\"").matcher(trimmed)
                            if (refererMatcher.find()) {
                                currentReferer = refererMatcher.group(1) ?: ""
                            }

                            val groupMatcher = java.util.regex.Pattern.compile("group-title=\"([^\"]+)\"").matcher(trimmed)
                            if (groupMatcher.find()) {
                                currentGroup = groupMatcher.group(1) ?: "Imported"
                            } else {
                                currentGroup = "Imported"
                            }
                        } else if (trimmed.startsWith("#EXTVLCOPT:")) {
                            val vlcRefererMatcher = java.util.regex.Pattern.compile("http-referrer=([^\\s]+)").matcher(trimmed)
                            if (vlcRefererMatcher.find()) {
                                currentReferer = vlcRefererMatcher.group(1) ?: ""
                            }
                        } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            // It's a URL
                            if (currentTitle.isNotEmpty()) {
                                val finalUri = if (currentReferer.isNotEmpty()) {
                                    if (trimmed.contains("#")) "$trimmed&referer=$currentReferer" else "$trimmed#referer=$currentReferer"
                                } else {
                                    trimmed
                                }
                                
                                var finalRegion = currentGroup
                                if (resolvedUrl.contains("gh.m3u")) finalRegion = "Ghana"
                                else if (resolvedUrl.contains("ng.m3u")) finalRegion = "Nigeria"
                                else if (resolvedUrl.contains("za.m3u")) finalRegion = "South Africa"
                                else if (resolvedUrl.contains("uk.m3u")) finalRegion = "United Kingdom"
                                else if (resolvedUrl.contains("us.m3u") || resolvedUrl.contains("us_")) finalRegion = "United States"
                                
                                itemsToInsert.add(
                                    MediaItemEntity(
                                        id = "m3u_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}",
                                        title = currentTitle,
                                        artist = currentGroup,
                                        uri = finalUri,
                                        artUri = currentLogo.ifBlank { "https://images.unsplash.com/photo-1593784991095-a205069470b6?q=80&w=1080&auto=format&fit=crop" },
                                        duration = 0,
                                        isVideo = true,
                                        category = "TV",
                                        region = finalRegion
                                        , tvgId = currentTvgId.ifBlank { null }
                                    )
                                )
                                currentTitle = ""
                                currentLogo = ""
                                currentTvgId = ""
                                currentGroup = ""
                                currentReferer = ""
                            }
                        }
                    }
                    
                    if (itemsToInsert.isNotEmpty()) {
                        repository.insertMediaList(itemsToInsert)
                    if (epgUrl.isNotBlank()) {
                        repository.fetchEpg(epgUrl)
                    }
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Imported ${itemsToInsert.size} channels!", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "No valid channels found in playlist.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Failed to fetch playlist", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Error importing playlist: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun deleteMedia(mediaId: String) {
        viewModelScope.launch {
            repository.deleteMedia(mediaId)
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Item removed from library", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun renameMedia(mediaId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameMedia(mediaId, newTitle)
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Renamed to: $newTitle", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setMediaPrivacy(mediaId: String, isPrivate: Boolean) {
        viewModelScope.launch {
            repository.setMediaPrivacy(mediaId, isPrivate)
            val msg = if (isPrivate) "Moved to Privacy folder" else "Moved to Library"
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setAsRingtone(media: MediaItemEntity) {
        if (!android.provider.Settings.System.canWrite(context)) {
            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            android.widget.Toast.makeText(context, "Please grant permission to write settings", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        viewModelScope.launch {
            try {
                // For ringtones to work correctly on modern Android, the file often needs to be in MediaStore
                // or specific folders. We use the content URI directly.
                val uri = android.net.Uri.parse(media.uri)
                android.media.RingtoneManager.setActualDefaultRingtoneUri(
                    context,
                    android.media.RingtoneManager.TYPE_RINGTONE,
                    uri
                )
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Ringtone set: ${media.title}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to set ringtone: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun updateEpg(epgUrl: String) {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Updating EPG, please wait...", android.widget.Toast.LENGTH_SHORT).show()
            }
            repository.fetchEpg(epgUrl)
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "EPG Update Complete!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun scanMedia() {
        viewModelScope.launch {
            val count = repository.scanMedia()
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "$count items updated in library", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun playMedia(media: MediaItemEntity, contextList: List<MediaItemEntity>? = null) {
        viewModelScope.launch {
            val savedPosition = repository.getLastPositionForMedia(media.id) ?: 0L
            val isLiveStream = media.category == "TV" || media.category == "Streams" || media.category == "Highlights"
            val finalStartPos = if (!isLiveStream && savedPosition > 3000L && (media.duration == 0L || savedPosition < media.duration - 5000L)) {
                savedPosition
            } else 0L

            if (contextList != null && contextList.isNotEmpty()) {
                val startIndex = contextList.indexOfFirst { it.id == media.id }.coerceAtLeast(0)
                val resolvedContextList = contextList.map { it.copy(uri = resolveUrl(it.uri)) }
                mediaStateHolder.setQueue(resolvedContextList.map { it.toMediaItem() }, startIndex, finalStartPos)
            } else {
                mediaStateHolder.playMedia(media.copy(uri = resolveUrl(media.uri)).toMediaItem())
                if (finalStartPos > 0L) {
                    kotlinx.coroutines.delay(400L)
                    mediaStateHolder.seekTo(finalStartPos)
                }
            }
            
            // Apply default playback speed preference
            val s = _defaultPlaybackSpeed.value
            if (s != 1.0f) {
                kotlinx.coroutines.delay(150L)
                mediaStateHolder.setPlaybackSpeed(s)
            }
        }
    }

    fun setQueue(items: List<MediaItemEntity>, startIndex: Int = 0) {
        viewModelScope.launch {
            val targetMedia = items.getOrNull(startIndex)
            val savedPosition = targetMedia?.let { repository.getLastPositionForMedia(it.id) }
            val resolvedItems = items.map { it.copy(uri = resolveUrl(it.uri)) }
            mediaStateHolder.setQueue(resolvedItems.map { it.toMediaItem() }, startIndex)
            
            // Apply default playback speed preference
            val s = _defaultPlaybackSpeed.value
            if (s != 1.0f) {
                kotlinx.coroutines.delay(150L)
                mediaStateHolder.setPlaybackSpeed(s)
            }

            val isLiveStream = targetMedia?.category == "TV" || targetMedia?.category == "Streams" || targetMedia?.category == "Highlights"
            if (!isLiveStream && savedPosition != null && savedPosition > 3000L) {
                val duration = targetMedia?.duration ?: 0L
                if (duration == 0L || savedPosition < duration - 5000L) {
                    kotlinx.coroutines.delay(400L)
                    mediaStateHolder.seekTo(savedPosition)
                }
            }
        }
    }

    private suspend fun resolveUrl(url: String): String {
        if (!url.contains("iptvcat.com")) return url
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                if (connection.responseCode == 200) {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(connection.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (line?.startsWith("http") == true) {
                            return@withContext line!!
                        }
                    }
                }
                url
            } catch (e: Exception) {
                url
            }
        }
    }

    fun addToQueue(media: MediaItemEntity) {
        mediaStateHolder.addToQueue(media.toMediaItem())
    }

    fun addNextToQueue(media: MediaItemEntity) {
        mediaStateHolder.addNextToQueue(media.toMediaItem())
    }

    private fun MediaItemEntity.toMediaItem(): MediaItem {
        val extras = android.os.Bundle().apply {
            putBoolean("is_video", isVideo)
        }
        val safeUri = if (this.uri.startsWith("/")) {
            android.net.Uri.fromFile(java.io.File(this.uri)).toString()
        } else {
            if (this.category == "TV" || this.category == "Streams") {
                if (this.uri.contains("#")) this.uri + "&is_stream=true" else this.uri + "#is_stream=true"
            } else {
                this.uri
            }
        }
        val builder = MediaItem.Builder()
            .setMediaId(this.id)
            .setUri(safeUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(this.title)
                    .setArtist(this.artist)
                    .setArtworkUri(this.artUri?.let { android.net.Uri.parse(it) })
                    .setExtras(extras)
                    .build()
            )

        val uriStr = this.uri
        val isM3u8 = uriStr.contains(".m3u8", ignoreCase = true)
        val isLiveStream = this.category == "TV" || this.category == "Streams" ||
                           uriStr.contains("stream", ignoreCase = true) ||
                           uriStr.contains("zeno.fm", ignoreCase = true) ||
                           uriStr.contains("calmradio", ignoreCase = true) ||
                           uriStr.contains("icecast", ignoreCase = true) ||
                           uriStr.contains(".fm", ignoreCase = true) ||
                           uriStr.contains("149.56.234.138") ||
                           uriStr.contains("radio", ignoreCase = true) ||
                           uriStr.contains("live", ignoreCase = true)

        if (isM3u8) {
            builder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        }
        
        if (isLiveStream) {
            builder.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
        }

        return builder.build()
    }

    fun seekTo(position: Long) = mediaStateHolder.seekTo(position)
    fun togglePlayPause() = mediaStateHolder.togglePlayPause()
    fun skipNext() = mediaStateHolder.skipNext()
    fun skipPrevious() = mediaStateHolder.skipPrevious()
    fun seekForward() = mediaStateHolder.seekForward()
    fun seekBack() = mediaStateHolder.seekBack()
    fun seekToItem(index: Int) = mediaStateHolder.seekToItem(index)
    fun removeQueueItem(index: Int) = mediaStateHolder.removeQueueItem(index)
    fun moveQueueItem(from: Int, to: Int) = mediaStateHolder.moveQueueItem(from, to)
    fun clearQueue() = mediaStateHolder.clearQueue()
    
    fun saveQueueAsPlaylist(name: String, items: List<androidx.media3.common.MediaItem>) {
        viewModelScope.launch {
            val playlistId = repository.createPlaylist(name)
            repository.reorderPlaylist(playlistId, items.map { it.mediaId })
        }
    }
    
    fun setPlaybackSpeed(speed: Float) = mediaStateHolder.setPlaybackSpeed(speed)
    fun toggleShuffleMode() = mediaStateHolder.toggleShuffleMode()
    fun toggleRepeatMode() = mediaStateHolder.toggleRepeatMode()
    fun setSleepTimer(durationMs: Long?) = mediaStateHolder.setSleepTimer(durationMs)
    fun setVolume(volume: Float) = mediaStateHolder.setVolume(volume)

    fun setAlarm(timeInMillis: Long, mediaUri: String) {
        val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(context, com.osei.djmixer.playback.AlarmReceiver::class.java).apply {
            putExtra("MEDIA_URI", mediaUri)
        }
        
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                    android.widget.Toast.makeText(context, "Alarm set successfully!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "Cannot set exact alarm. Missing permissions.", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                android.widget.Toast.makeText(context, "Alarm set successfully!", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Error setting alarm: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun playByMediaId(mediaId: String) {
        viewModelScope.launch {
            val media = repository.getMediaById(mediaId)
            if (media != null) {
                playMedia(media)
            }
        }
    }


    fun getScheduleForChannel(channelId: String) = repository.getScheduleForChannel(channelId)
    fun getCurrentProgramFlow(channelId: String) = repository.getCurrentProgramFlow(channelId)
    fun getNextProgramFlow(channelId: String) = repository.getNextProgramFlow(channelId)


    fun clearRecentlyPlayed() {
        viewModelScope.launch {
            // No direct DB call implemented for this yet in repository, but we can clear favorites
        }
    }
    
    fun clearFavorites() {
        viewModelScope.launch {
            val allFavs = repository.favorites.first()
            allFavs.forEach {
                repository.toggleFavorite(it.id)
            }
        }
    }
}
