package com.osei.djmixer.data

import com.osei.djmixer.ui.theme.*

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.osei.djmixer.playback.MediaStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MediaRepository(
    private val mediaDao: MediaDao,
    private val epgDao: EpgDao,
    private val context: android.content.Context
) {
    private val contentResolver = context.contentResolver
    val allMedia = mediaDao.getAllMedia()
    val allAudio = mediaDao.getAllAudio()
    val allVideo = mediaDao.getAllVideo()
    val favorites = mediaDao.getFavorites()
    val recentHistory = mediaDao.getRecentHistory()
    val allHistoryFlow = mediaDao.getAllHistoryFlow()
    val allPlaylists = mediaDao.getAllPlaylists()
    val allStreams = mediaDao.getAllStreams()

    suspend fun getMediaById(mediaId: String) = withContext(Dispatchers.IO) {
        mediaDao.getMediaByIdSingle(mediaId)
    }

    suspend fun getTrackAnalysis(trackId: String): TrackAnalysisEntity? = withContext(Dispatchers.IO) {
        mediaDao.getTrackAnalysis(trackId)
    }

    suspend fun saveTrackAnalysis(analysis: TrackAnalysisEntity) = withContext(Dispatchers.IO) {
        mediaDao.insertTrackAnalysis(analysis)
    }

    suspend fun clearIncompatibleAnalysis(currentVersion: Int) = withContext(Dispatchers.IO) {
        mediaDao.clearIncompatibleAnalysis(currentVersion)
    }

    fun getMediaForPlaylist(playlistId: Long) = mediaDao.getMediaForPlaylist(playlistId)

    suspend fun insertMedia(media: MediaItemEntity) = withContext(Dispatchers.IO) {
        mediaDao.insertMedia(media)
    }

    suspend fun insertMediaList(mediaList: List<MediaItemEntity>) = withContext(Dispatchers.IO) {
        mediaDao.insertMediaList(mediaList)
    }

    suspend fun deleteMedia(mediaId: String) = withContext(Dispatchers.IO) {
        mediaDao.deleteMedia(mediaId)
    }

    suspend fun createPlaylist(name: String) = mediaDao.createPlaylist(PlaylistEntity(name = name))

    suspend fun addMediaToPlaylist(playlistId: Long, mediaId: String, index: Int) {
        mediaDao.addMediaToPlaylist(PlaylistMediaCrossRef(playlistId, mediaId, index))
    }

    suspend fun reorderPlaylist(playlistId: Long, orderedMediaIds: List<String>) {
        val crossRefs = orderedMediaIds.mapIndexed { index, mediaId ->
            PlaylistMediaCrossRef(playlistId, mediaId, index)
        }
        mediaDao.insertPlaylistMediaCrossRefs(crossRefs)
    }

    suspend fun removeMediaFromPlaylist(playlistId: Long, mediaId: String) {
        mediaDao.removeMediaFromPlaylist(playlistId, mediaId)
    }

    suspend fun deletePlaylist(playlistId: Long) = mediaDao.deletePlaylist(playlistId)

    suspend fun toggleFavorite(mediaId: String) = withContext(Dispatchers.IO) {
        if (mediaDao.isFavoriteSingle(mediaId)) {
            mediaDao.removeFavorite(mediaId)
        } else {
            mediaDao.addFavorite(FavoriteEntity(mediaId))
        }
    }

    suspend fun getLastPositionForMedia(mediaId: String): Long? = withContext(Dispatchers.IO) {
        mediaDao.getLastPositionForMediaSingle(mediaId)
    }

    suspend fun saveProgress(mediaId: String, position: Long) = withContext(Dispatchers.IO) {
        mediaDao.deleteHistoryForMedia(mediaId)
        mediaDao.addToHistory(PlayerHistoryEntity(mediaId = mediaId, lastPosition = position))
    }

    fun searchMedia(query: String) = mediaDao.searchMedia(query)
    
    suspend fun updateMediaBpm(mediaId: String, bpm: Int?) = withContext(Dispatchers.IO) {
        mediaDao.updateMediaBpm(mediaId, bpm)
    }

    suspend fun renameMedia(mediaId: String, newTitle: String) = withContext(Dispatchers.IO) {
        mediaDao.renameMedia(mediaId, newTitle)
    }

    suspend fun setMediaPrivacy(mediaId: String, isPrivate: Boolean) = withContext(Dispatchers.IO) {
        mediaDao.setMediaPrivacy(mediaId, isPrivate)
    }

    fun getPrivateMedia() = mediaDao.getPrivateMedia()

    suspend fun scanMedia(): Int = withContext(Dispatchers.IO) {
        // Force system scan of common folders to ensure MediaStore is fresh
        val rootPaths = arrayOf(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC).absolutePath,
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES).absolutePath,
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath
        )
        android.media.MediaScannerConnection.scanFile(context, rootPaths, null, null)
        
        mediaDao.clearLocalMedia()
        val mediaList = mutableListOf<MediaItemEntity>()
        
        val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(context).toMutableSet().apply {
                add(MediaStore.VOLUME_INTERNAL)
            }
        } else {
            setOf(MediaStore.VOLUME_EXTERNAL, MediaStore.VOLUME_INTERNAL)
        }

        volumes.forEach { volumeName ->
            val isUsbOrExternal = volumeName != MediaStore.VOLUME_INTERNAL && 
                                 volumeName != MediaStore.VOLUME_EXTERNAL &&
                                 volumeName != "external_primary"
            
            val volumeLabel = when {
                volumeName == MediaStore.VOLUME_INTERNAL -> "Internal"
                volumeName == MediaStore.VOLUME_EXTERNAL || volumeName == "external_primary" -> "Storage"
                else -> "USB Drive ($volumeName)"
            }

            // Scan Audio
            try {
                val audioUri = MediaStore.Audio.Media.getContentUri(volumeName)
                val audioProjection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DATE_ADDED
                )
                
                contentResolver.query(audioUri, audioProjection, null, null, null)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val title = cursor.getString(titleColumn) ?: "Unknown"
                        var artist = cursor.getString(artistColumn) ?: "Unknown"
                        val album = cursor.getString(albumColumn) ?: "Unknown Album"
                        if (isUsbOrExternal && (artist == "Unknown" || artist == "<unknown>")) {
                            artist = volumeLabel
                        }
                        val duration = cursor.getLong(durationColumn)
                        val albumId = cursor.getLong(albumIdColumn)
                        val dateAdded = cursor.getLong(dateAddedColumn) * 1000 // Convert to ms
                        val contentUri = ContentUris.withAppendedId(audioUri, id)
                        
                        val artUri = ContentUris.withAppendedId(
                            Uri.parse("content://media/$volumeName/audio/albumart"),
                            albumId
                        ).toString()

                        mediaList.add(
                            MediaItemEntity(
                                id = "$volumeName-$id",
                                title = title,
                                artist = artist,
                                album = album,
                                uri = contentUri.toString(),
                                artUri = artUri,
                                duration = duration,
                                isVideo = false,
                                category = if (isUsbOrExternal) "USB Audio" else "Library",
                                addedAt = dateAdded
                            )
                        )
                    }
                }
            } catch (e: SecurityException) {
                android.util.Log.w("MediaRepository", "Audio media permission not granted for volume $volumeName: ${e.message}")
            } catch (e: Exception) {
                android.util.Log.w("MediaRepository", "Error scanning audio on volume $volumeName: ${e.message}")
            }

            // Scan Video
            try {
                val videoUri = MediaStore.Video.Media.getContentUri(volumeName)
                val videoProjection = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.TITLE,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.DATE_ADDED
                )

                contentResolver.query(videoUri, videoProjection, null, null, null)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val title = cursor.getString(titleColumn) ?: "Unknown"
                        val duration = cursor.getLong(durationColumn)
                        val dateAdded = cursor.getLong(dateAddedColumn) * 1000
                        val contentUri = ContentUris.withAppendedId(videoUri, id)

                        mediaList.add(
                            MediaItemEntity(
                                id = "$volumeName-$id",
                                title = title,
                                artist = volumeLabel,
                                uri = contentUri.toString(),
                                artUri = null,
                                duration = duration,
                                isVideo = true,
                                category = if (isUsbOrExternal) "USB Video" else "Videos",
                                addedAt = dateAdded
                            )
                        )
                    }
                }
            } catch (e: SecurityException) {
                android.util.Log.w("MediaRepository", "Video media permission not granted for volume $volumeName: ${e.message}")
            } catch (e: Exception) {
                android.util.Log.w("MediaRepository", "Error scanning video on volume $volumeName: ${e.message}")
            }
        }

        mediaDao.insertMediaList(mediaList)
        seedDefaults()
        mediaList.size
    }

    suspend fun seedDefaults() = withContext(Dispatchers.IO) {
        mediaDao.removeOldTvDefaults()
        if (true) {
            val defaults = listOf(
                MediaItemEntity(
                    id = "stream_sweet_melodies",
                    title = "Sweet Melodies FM 94.3",
                    artist = "Accra",
                    uri = "http://sweetmelodies.tk/stream1",
                    artUri = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Greater Accra"
                ),
                MediaItemEntity(
                    id = "stream_the_bride",
                    title = "The Bride Online Radio",
                    artist = "Kumasi",
                    uri = "https://stream.zeno.fm/2fng89885wzuv",
                    artUri = "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Ashanti"
                ),
                MediaItemEntity(
                    id = "stream_sam_bennah",
                    title = "Sam Bennah Radio",
                    artist = "Christian",
                    uri = "https://stream.zeno.fm/4vysr9a8w1zuv",
                    artUri = "https://images.unsplash.com/photo-1438283173091-5dbf5c5a3206?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Greater Accra"
                ),
                MediaItemEntity(
                    id = "stream_user_royal_tv",
                    title = "Royal TV",
                    artist = "Ghana",
                    uri = "https://list.iptvcat.com/my_list/s/e506859007a126784fc137e37505e8c8.m3u8",
                    artUri = "https://i.imgur.com/8Qz9jO2.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),

                MediaItemEntity(
                    id = "stream_user_ceejay_tv",
                    title = "CeeJay TV",
                    artist = "Ghana",
                    uri = "https://list.iptvcat.com/my_list/s/179f3b68770b6d5524abed4ea8bbdaa1.m3u8",
                    artUri = "https://i.imgur.com/8Qz9jO2.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),


                MediaItemEntity(
                    id = "stream_vision1_fm",
                    title = "Vision 1 FM 93.5",
                    artist = "Ghana's Super Station",
                    uri = "https://stream.zeno.fm/xua5q9hfgg0uv",
                    artUri = "https://images.unsplash.com/photo-1598555319808-1647a508b981?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Greater Accra"
                ),
                MediaItemEntity(
                    id = "stream_angel_fm",
                    title = "Angel FM 102.9",
                    artist = "Angel Broadcasting",
                    uri = "https://angelfmaccra-atunwadigital.streamguys1.com/angelfmaccra",
                    artUri = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Greater Accra"
                ),
                MediaItemEntity(
                    id = "stream_joy_fm",
                    title = "Joy FM 99.7",
                    artist = "Ghana's Super Station",
                    uri = "https://mmg.streamguys1.com/JoyFM-mp3",
                    artUri = "https://images.unsplash.com/photo-1593697821252-0c9137d9fc45?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Greater Accra"
                ),
                MediaItemEntity(
                    id = "stream_peace_fm",
                    title = "Peace FM 104.3",
                    artist = "Obiara se eye",
                    uri = "https://peacefm-atunwadigital.streamguys1.com/peacefm",
                    artUri = "https://images.unsplash.com/photo-1516280440504-747fc4e1e1d0?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Greater Accra"
                ),
                MediaItemEntity(
                    id = "stream_citi_fm",
                    title = "Citi FM 97.3",
                    artist = "Relevant Radio. Always.",
                    uri = "http://citi973fm.radioca.st:8977/stream/",
                    artUri = "https://images.unsplash.com/photo-1579548122080-c35fd6820ceb?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Greater Accra"
                ),
                MediaItemEntity(
                    id = "stream_adom_fm",
                    title = "Adom FM 106.3",
                    artist = "Ye de a, edeh!",
                    uri = "https://mmg.streamguys1.com/AdomFM-mp3",
                    artUri = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Greater Accra"
                ),
                MediaItemEntity(
                    id = "stream_kessben_fm",
                    title = "Kessben FM 93.3",
                    artist = "Aseda Nkoaa",
                    uri = "https://kessben933fmkumasi-atunwadigital.streamguys1.com/kessben933fmkumasi",
                    artUri = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Ashanti"
                ),
                MediaItemEntity(
                    id = "stream_luv_fm",
                    title = "Luv FM 99.5",
                    artist = "Kumasi's Best Music",
                    uri = "https://mmg.streamguys1.com/LuvFM-mp3",
                    artUri = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Ashanti"
                ),
                MediaItemEntity(
                    id = "stream_hello_fm",
                    title = "Hello FM 101.5",
                    artist = "Kumasi",
                    uri = "https://hellofm-atunwadigital.streamguys1.com/hellofm",
                    artUri = "https://images.unsplash.com/photo-1558231922-bf4c478a5e3a?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Ashanti"
                ),
                MediaItemEntity(
                    id = "stream_okay_fm",
                    title = "Okay FM 101.7",
                    artist = "Everything is Okay",
                    uri = "https://okayfm-atunwadigital.streamguys1.com/okayfm",
                    artUri = "https://images.unsplash.com/photo-1516280440504-747fc4e1e1d0?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Greater Accra"
                ),
                MediaItemEntity(
                    id = "stream_asampa_fm",
                    title = "Asempa FM 94.7",
                    artist = "All Talk, All Day",
                    uri = "https://mmg.streamguys1.com/AsempaFM-mp3",
                    artUri = "https://images.unsplash.com/photo-1579548122080-c35fd6820ceb?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Greater Accra"
                ),
                MediaItemEntity(
                    id = "stream_nhyira_fm",
                    title = "Nhyira FM 104.5",
                    artist = "Kwadwom",
                    uri = "https://mmg.streamguys1.com/NhyiraFM-mp3",
                    artUri = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Ashanti"
                ),
                MediaItemEntity(
                    id = "stream_highlights_2",
                    title = "Sports News & Updates",
                    artist = "Sports Network",
                    uri = "https://bein-xtra-bein.amagi.tv/playlist.m3u8",
                    artUri = "https://images.unsplash.com/photo-1522778119026-d647f0596c20?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Highlights",
                    region = "Global"
                ),
                MediaItemEntity(
                    id = "stream_football_0",
                    title = "IMPORT YOUR IPTV M3U",
                    artist = "Click + or Link icon above",
                    uri = "https://bein-xtra-bein.amagi.tv/playlist.m3u8",
                    artUri = "https://images.unsplash.com/photo-1579952363873-27f3bade9f55?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Football",
                    region = "Global"
                ),
                MediaItemEntity(
                    id = "stream_football_africa",
                    title = "Africa 24 Sport",
                    artist = "Sports Network",
                    uri = "https://africa24.vedge.infomaniak.com/livecast/ik:africa24sport/manifest.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Football",
                    region = "Global"
                ),
                MediaItemEntity(
                    id = "stream_football_aspor",
                    title = "A Spor Live",
                    artist = "Sports HD",
                    uri = "https://rnttwmjcin.turknet.ercdn.net/lcpmvefbyo/aspor/aspor.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Football",
                    region = "Global"
                ),
                MediaItemEntity(
                    id = "stream_football_3",
                    title = "Football Highlights 24/7",
                    artist = "Sports",
                    uri = "http://sample.vodobox.net/skate_phantom_flex_4k/skate_phantom_flex_4k.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Football",
                    region = "Global"
                ),
                MediaItemEntity(
                    id = "stream_football_4",
                    title = "Sports Live 4",
                    artist = "Sports TV",
                    uri = "http://sample.vodobox.net/skate_phantom_flex_4k/skate_phantom_flex_4k.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Football",
                    region = "Global"
                ),
                MediaItemEntity(
                    id = "stream_football_5",
                    title = "Champions League Stream",
                    artist = "Euro Sports",
                    uri = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Football",
                    region = "Global"
                ),
                MediaItemEntity(
                    id = "stream_supersport_rugby",
                    title = "SuperSport Rugby",
                    artist = "Sport",
                    uri = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "News",
                    region = "ZA"
                ),
                MediaItemEntity(
                    id = "stream_bein_sports_usa",
                    title = "beIN Sports USA",
                    artist = "Sport",
                    uri = "http://23.237.104.106:8080/USA_BEIN/index.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Live",
                    region = "US"
                ),
                MediaItemEntity(
                    id = "stream_bbc_one",
                    title = "BBC One HD",
                    artist = "News / General",
                    uri = "http://193.46.58.239:8080/BBCOneHD/index.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "UK"
                ),
                MediaItemEntity(
                    id = "stream_bbc_two",
                    title = "BBC Two HD",
                    artist = "News / General",
                    uri = "http://193.46.58.239:8080/BBCTwoHD/index.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "UK"
                ),
                MediaItemEntity(
                    id = "stream_bbc_news",
                    title = "BBC News Europe",
                    artist = "News",
                    uri = "http://46.32.176.50/bbcworld/index.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "News",
                    region = "UK"
                ),
                MediaItemEntity(
                    id = "stream_sky_news",
                    title = "Sky News",
                    artist = "News",
                    uri = "http://41.205.77.102/SKYNEWS/index.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "News",
                    region = "UK"
                ),
                MediaItemEntity(
                    id = "stream_gb_news",
                    title = "GB News",
                    artist = "News",
                    uri = "https://live-gbnews.simplestreamcdn.com/s3/gbnews/index.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "News",
                    region = "UK"
                ),
                MediaItemEntity(
                    id = "stream_itv1",
                    title = "ITV1",
                    artist = "General",
                    uri = "https://xemzi.short.gy/1000011",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "UK"
                ),
                MediaItemEntity(
                    id = "stream_bein_sports_xtra",
                    title = "beIN SPORTS XTRA",
                    artist = "Sport",
                    uri = "https://bein-xtra-bein.amagi.tv/playlist.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "US"
                ),
                MediaItemEntity(
                    id = "stream_bein_sports_xtra_es",
                    title = "beIN SPORTS XTRA en Espanol",
                    artist = "Sport",
                    uri = "https://dc1644a9jazgj.cloudfront.net/beIN_Sports_Xtra_Espanol.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "US"
                ),
                MediaItemEntity(
                    id = "stream_bein_sports_en_espanol",
                    title = "beIN Sports en Espanol",
                    artist = "Sport",
                    uri = "https://futbol9865.ultratv13.workers.dev/deportivo111/69.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "QA"
                ),
                MediaItemEntity(
                    id = "stream_bein_sports_haber",
                    title = "beIN Sports Haber",
                    artist = "Sport",
                    uri = "https://1nyaler.streamhostingcdn.top/stream/23/index.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "TR"
                ),
                MediaItemEntity(
                    id = "stream_bein_sports_1",
                    title = "beIN Sports 1",
                    artist = "Sport",
                    uri = "http://41.205.70.146/BEINSPORT1/index.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "Global"
                ),
                MediaItemEntity(
                    id = "stream_adom_tv",
                    title = "Adom TV",
                    artist = "Ghana",
                    uri = "https://g2qd3exjy7an-hls-live.5centscdn.com/adomtv/ghanatvweb.stream/playlist.m3u8#referer=https://liveghanatv.com/",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gtv_sports_plus",
                    title = "GTV Sports +",
                    artist = "Ghana",
                    uri = "https://g2qd3exjy7an-hls-live.5centscdn.com/gtvsport/ghanaweb.stream/playlist.m3u8#referer=https://liveghanatv.com/",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_max_tv",
                    title = "Max TV",
                    artist = "Ghana",
                    uri = "https://g2qd3exjy7an-hls-live.5centscdn.com/gtvsport/ghanaweb.stream/playlist.m3u8#referer=https://liveghanatv.com/",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_metro_tv",
                    title = "Metro TV",
                    artist = "Ghana",
                    uri = "https://g2qd3exjy7an-hls-live.5centscdn.com/metrotvg/ghanaweb.stream/playlist.m3u8#referer=https://liveghanatv.com/",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_kessben_tv",
                    title = "Kessben TV",
                    artist = "Ghana",
                    uri = "https://g2qd3exjy7an-hls-live.5centscdn.com/adomtv/ghanatvweb.stream/playlist.m3u8#referer=https://liveghanatv.com/",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_joy_news",
                    title = "Joy News",
                    artist = "Ghana",
                    uri = "https://g2qd3exjy7an-hls-live.5centscdn.com/joyprim/ghanaweb.stream/playlist.m3u8#referer=https://liveghanatv.com/",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "News",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_jesus_temple_tv",
                    title = "Jesus Temple TV",
                    artist = "Ghana",
                    uri = "https://videodelivery.net/dfbdca87f2a6291aa4fdc8fe3290769b/manifest/video.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Prayer",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_joy_prime",
                    title = "Joy Prime",
                    artist = "Ghana",
                    uri = "https://g2qd3exjy7an-hls-live.5centscdn.com/joyprim/ghanaweb.stream/playlist.m3u8#referer=https://liveghanatv.com/",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_maranatha_tv",
                    title = "Maranatha TV",
                    artist = "Ghana",
                    uri = "https://media.streambrothers.com:1936/8298/8298/playlist.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_mighty_tv",
                    title = "Mighty TV",
                    artist = "Ghana",
                    uri = "http://mightytv.srfms.com:1935/mightytv/livestream/playlist.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_adinkra_tv",
                    title = "Adinkra TV",
                    artist = "Ghana",
                    uri = "https://59d39900ebfb8.streamlock.net/adinkratvny/adinkratvny/playlist.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_ap_tv",
                    title = "AP TV",
                    artist = "Ghana",
                    uri = "https://59nyqg8elwap-hls-live.5centscdn.com/APTV/85c20905211659d143febd7c32592ee0.sdp/playlist.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_channel247",
                    title = "Channel247",
                    artist = "Ghana",
                    uri = "https://tv.localstreamgh.com/Channel247/index.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_fish_tv",
                    title = "Fish TV",
                    artist = "Ghana",
                    uri = "https://tv.localstreamgh.com/fishtv/index.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gtv",
                    title = "GTV",
                    artist = "Ghana",
                    uri = "https://g2qd3exjy7an-hls-live.5centscdn.com/gtvghana/ghanaweb.stream/playlist.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_hope4life",
                    title = "Hope4Life TV",
                    artist = "Ghana",
                    uri = "http://144.217.14.88/hls/hope4life.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_hope_channel_ghana",
                    title = "Hope Channel Ghana",
                    artist = "Ghana",
                    uri = "https://videodelivery.net/dfbdca87f2a6291aa4fdc8fe3290769b/manifest/video.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_mogpa_tv",
                    title = "MOGPA TV",
                    artist = "Ghana",
                    uri = "https://livestream.anojed.com/ablazetv/f7b44cfafd5c52223d5498196c8a2e7b.sdp/playlist.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_mogpa_tv_plus",
                    title = "MOGPA TV Plus",
                    artist = "Ghana",
                    uri = "https://bk7l2r2nyx53-hls-live.5centscdn.com/mogpatvplus/2567a5ec9705eb7ac2c984033e06189d.sdp/playlist.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_otv",
                    title = "OTV",
                    artist = "Ghana",
                    uri = "https://5dcabf026b188.streamlock.net/OceansTV/livestream/playlist.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_pent_tv",
                    title = "Pent TV",
                    artist = "Ghana",
                    uri = "https://playlist.dacast.com/live/a72117af-d78e-7ffe-7c12-1af6cd4a8483-live-42172a86-494b-a866-498f-dbaff4b5e3c0/master.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_resurrection_tv",
                    title = "Resurrection TV",
                    artist = "Ghana",
                    uri = "https://1681360479.rsc.cdn77.org/1681360479/index.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_tv3",
                    title = "TV3",
                    artist = "Ghana",
                    uri = "https://g2qd3exjy7an-hls-live.5centscdn.com/webtv3/ghanatv.stream/playlist.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_united_tv",
                    title = "United TV (UTV)",
                    artist = "Ghana",
                    uri = "https://list.iptvcat.com/my_list/s/e83a3d839daf641ca5b9d4fdd9cacd11.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_football_6",
                    title = "World Cup Replays",
                    artist = "FIFA Streams",
                    uri = "http://sample.vodobox.net/skate_phantom_flex_4k/skate_phantom_flex_4k.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Football",
                    region = "Global"
                ),
                MediaItemEntity(
                    id = "stream_hope_channel",
                    title = "Hope Channel Radio",
                    artist = "Hope Channel",
                    uri = "https://hopechannel.streamguys1.com/hopechannel",
                    artUri = "https://images.unsplash.com/photo-1507838153414-b4b713384a76?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "International"
                ),
                MediaItemEntity(
                    id = "stream_3abn",
                    title = "3ABN Radio",
                    artist = "Three Angels Broadcasting",
                    uri = "https://war.str3am.com/3abnradio",
                    artUri = "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Teaching"
                ),
                MediaItemEntity(
                    id = "stream_premier_christian",
                    title = "Premier Christian Radio",
                    artist = "Christian Radio UK",
                    uri = "https://media-ssl.musicradio.com/PremierChristian",
                    artUri = "https://images.unsplash.com/photo-1519682577862-22b62b24e493?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Teaching"
                ),
                MediaItemEntity(
                    id = "stream_ucb",
                    title = "UCB 1",
                    artist = "United Christian Broadcasters",
                    uri = "https://edge-audio-01.musicradio.com/UCB1",
                    artUri = "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Contemporary"
                ),
                MediaItemEntity(
                    id = "stream_ewtn",
                    title = "EWTN Global Catholic Radio",
                    artist = "EWTN",
                    uri = "https://ewtn-ice.streamguys1.com/english-aac",
                    artUri = "https://images.unsplash.com/photo-1542044896530-05d85be9b11a?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Catholic"
                ),
                MediaItemEntity(
                    id = "stream_bible_is",
                    title = "Bible.is Audio",
                    artist = "Faith Comes By Hearing",
                    uri = "https://s3.amazonaws.com/fcbh-audio/ENGKJV/ENGKJVN2DA/B01___01_Matthew_____ENGKJVN2DA.mp3",
                    artUri = "https://images.unsplash.com/photo-1490730141103-6cac27aaab94?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Teaching"
                ),
                MediaItemEntity(
                    id = "stream_premier_gospel",
                    title = "Premier Gospel (UK)",
                    artist = "Global Gospel",
                    uri = "https://media-ssl.musicradio.com/PremierGospel",
                    artUri = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Gospel"
                ),
                MediaItemEntity(
                    id = "stream_praise_and_worship",
                    title = "1.FM - Praise & Worship",
                    artist = "Global Gospel",
                    uri = "http://strm112.1.fm/acappella_mobile_mp3",
                    artUri = "https://images.unsplash.com/photo-1481514782065-27a3a6699252?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Streams",
                    region = "Gospel"
                ),
                MediaItemEntity(
                    id = "stream_ng_0_acnn480p",
                    title = "ACNN (480p)",
                    artist = "Nigeria",
                    uri = "https://viewmedia7219.bozztv.com/wmedia/viewmedia100/web_014/Stream/playlist.m3u8",
                    artUri = "https://i.imgur.com/91JLpxr.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_1_advocatebroadcastingnetwork576p",
                    title = "Advocate Broadcasting Network (576p)",
                    artist = "Nigeria",
                    uri = "https://viewmedia7219.bozztv.com/wmedia/viewmedia100/web_045/Stream/playlist.m3u8",
                    artUri = "https://i.imgur.com/ZkBEAp7.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_2_africatv3720pnot247",
                    title = "Africa TV3 (720p) [Not 24/7]",
                    artist = "Nigeria",
                    uri = "http://africatv.live.net.sa:1935/live/africatv3/playlist.m3u8",
                    artUri = "https://i.imgur.com/iibE9WL.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_3_amusicchannel720p",
                    title = "AMusic Channel (720p)",
                    artist = "Nigeria",
                    uri = "http://mn-nl.mncdn.com/amusictv/amusicsrt.stream/playlist.m3u8",
                    artUri = "https://i.imgur.com/06zuf64.png",
                    duration = 0,
                    isVideo = true,
                    category = "Music",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_4_approvaltv720p",
                    title = "Approval TV (720p)",
                    artist = "Nigeria",
                    uri = "https://live.approvaltv.com/approvaltv/index.m3u8",
                    artUri = "https://storage.approvaltv.com/2026/03/approvallogo.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_5_bcsstarcrosstv576p",
                    title = "BCS StarCross TV (576p)",
                    artist = "Nigeria",
                    uri = "https://viewmedia7219.bozztv.com/wmedia/viewmedia100/web_001/Stream/playlist.m3u8",
                    artUri = "https://i.imgur.com/VKv1Y6p.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_6_bridetv576p",
                    title = "Bride TV (576p)",
                    artist = "Nigeria",
                    uri = "https://viewmedia7219.bozztv.com/wmedia/viewmedia100/web_013/Stream/playlist.m3u8",
                    artUri = "https://i.imgur.com/snZMucg.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_7_caughtuptv480pnot247",
                    title = "Caught-Up TV (480p) [Not 24/7]",
                    artist = "Nigeria",
                    uri = "https://webstreaming-3.viewmedia.tv/web_031/Stream/playlist.m3u8",
                    artUri = "https://i.imgur.com/7jeoulU.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_8_channels24",
                    title = "Channels 24",
                    artist = "Nigeria",
                    uri = "https://live20.bozztv.com/dvrfl06/astv/astv-channel24africa/index.m3u8",
                    artUri = "https://i.imgur.com/ww5zNmY.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_9_channelstv",
                    title = "Channels TV",
                    artist = "Nigeria",
                    uri = "https://cs2.push2stream.com/CHANNELSTV-DVR/playlist.m3u8",
                    artUri = "https://upload.wikimedia.org/wikipedia/en/7/76/Channels_TV.jpg",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_10_chosentvenglish360p",
                    title = "Chosen TV English (360p)",
                    artist = "Nigeria",
                    uri = "https://viewmedia7219.bozztv.com/wmedia/viewmedia100/web_024/Stream/playlist.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Prayer",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_11_cloveworldtv480p",
                    title = "cLoveworld TV (480p)",
                    artist = "Nigeria",
                    uri = "https://live-hls-5rxy.livepush.io/live_cdn/em_LJ5aZjqp0LdiQ/index.m3u8",
                    artUri = "https://i.imgur.com/MKKap2F.png",
                    duration = 0,
                    isVideo = true,
                    category = "Prayer",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_12_cozatv576p",
                    title = "Coza TV (576p)",
                    artist = "Nigeria",
                    uri = "https://viewmedia7219.bozztv.com/wmedia/viewmedia100/web_019/Stream/playlist.m3u8",
                    artUri = "https://i.imgur.com/QVBdpuF.png",
                    duration = 0,
                    isVideo = true,
                    category = "Prayer",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_13_dunamistv576pnot247",
                    title = "Dunamis TV (576p) [Not 24/7]",
                    artist = "Nigeria",
                    uri = "https://atechgroupuk.site/DTV.m3u8",
                    artUri = "https://i.imgur.com/thGAXeM.png",
                    duration = 0,
                    isVideo = true,
                    category = "Prayer",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_14_flametv576p",
                    title = "Flame TV (576p)",
                    artist = "Nigeria",
                    uri = "https://viewmedia7219.bozztv.com/wmedia/viewmedia100/web_010/Stream/playlist.m3u8",
                    artUri = "https://i.imgur.com/ORKcnzr.png",
                    duration = 0,
                    isVideo = true,
                    category = "Prayer",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_15_fortistv",
                    title = "Fortis TV",
                    artist = "Nigeria",
                    uri = "https://stream.fortistv.com/hls/fortis-news/index.m3u8",
                    artUri = "https://i.imgur.com/HoO9Urd.jpg",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_16_foursquaretv360pnot247",
                    title = "Foursquare TV (360p) [Not 24/7]",
                    artist = "Nigeria",
                    uri = "https://webstreaming-3.viewmedia.tv/web_033/Stream/playlist.m3u8",
                    artUri = "https://i.imgur.com/ORer9ag.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_17_itagetv480p",
                    title = "Itage TV (480p)",
                    artist = "Nigeria",
                    uri = "https://viewmedia7219.bozztv.com/wmedia/viewmedia100/web_011/Stream/playlist.m3u8",
                    artUri = "https://i.imgur.com/FP1aUPw.jpg",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_18_kingsviewtv1080p",
                    title = "Kingsview TV (1080p)",
                    artist = "Nigeria",
                    uri = "https://j78dp6reyq5r-hls-live.5centscdn.com/4896_push_1963_001/00cb1f2e4ff89048f2e77e26940c00e6.sdp/playlist.m3u8",
                    artUri = "https://i.imgur.com/py1XOXX.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_19_ln2471080p",
                    title = "LN247 (1080p)",
                    artist = "Nigeria",
                    uri = "https://go5lmb6oyawb-hls-live.5centscdn.com/station/3dfd3752af3d7aec5c53992c2da3a316.sdp/playlist.m3u8",
                    artUri = "https://i.imgur.com/s6PUehQ.jpg",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_20_loveworldarabic360p",
                    title = "LoveWorld Arabic (360p)",
                    artist = "Nigeria",
                    uri = "https://go5lm6a6dawb-hls-live.5centscdn.com/lwarabic/53132d996b4263ec0d7b602292c46eaf.sdp/playlist.m3u8",
                    artUri = "https://i.imgur.com/KYMjRjK.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_21_loveworldpersia720p",
                    title = "LoveWorld Persia (720p)",
                    artist = "Nigeria",
                    uri = "https://cdn3.wowza.com/5/aVJETlF0UFdmYTFu/LWPP/ngrp:persia.stream_all/playlist.m3u8",
                    artUri = "https://i.imgur.com/Vd2xPxh.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_22_loveworlduk1080pnot247",
                    title = "Loveworld UK (1080p) [Not 24/7]",
                    artist = "Nigeria",
                    uri = "https://cdn3.wowza.com/5/L1Uzd2FrbVlLRG1W/live/smil:lwukweb.smil/playlist.m3u8",
                    artUri = "https://i.imgur.com/JgHbDmF.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_23_loveworldusa720p",
                    title = "LoveWorld USA (720p)",
                    artist = "Nigeria",
                    uri = "https://cdn3.wowza.com/5/RGtVZkFxL3FOQkxX/LWU/ngrp:lwu.stream_all/playlist.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_24_loveworldsat1080p",
                    title = "LoveworldSAT (1080p)",
                    artist = "Nigeria",
                    uri = "https://cdnstack.internetmultimediaonline.org/lwsat/lwsat.stream/index.m3u8",
                    artUri = "https://i.imgur.com/Fg65XCS.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_25_masterstv720p",
                    title = "Master's TV (720p)",
                    artist = "Nigeria",
                    uri = "https://mn-nl.mncdn.com/commectv_live/masterstv/index.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_26_moregracetv410pnot247",
                    title = "More Grace TV (410p) [Not 24/7]",
                    artist = "Nigeria",
                    uri = "https://atechgroupuk.site/ETV.m3u8",
                    artUri = "https://i.imgur.com/DNTeCQt.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_27_newvisiontv360p",
                    title = "New Vision TV (360p)",
                    artist = "Nigeria",
                    uri = "https://viewmedia7219.bozztv.com/wmedia/viewmedia100/web_027/Stream/playlist.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_28_newscentral",
                    title = "News Central",
                    artist = "Nigeria",
                    uri = "https://wf.newscentral.ng:8443/hls/stream.m3u8",
                    artUri = "https://i.imgur.com/qvCgI8N.png",
                    duration = 0,
                    isVideo = true,
                    category = "News",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_29_nigbati720pnot247",
                    title = "Nigbati (720p) [Not 24/7]",
                    artist = "Nigeria",
                    uri = "https://viewmedia7219.bozztv.com/wmedia/viewmedia100/web_023/Stream/playlist.m3u8",
                    artUri = "https://images.unsplash.com/photo-1518605368461-1ee7e5414f6b?q=80&w=1080&auto=format&fit=crop",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_30_ntainternational576pnot247",
                    title = "NTA International (576p) [Not 24/7]",
                    artist = "Nigeria",
                    uri = "https://api.visionip.tv/live/ASHTTP/visiontvuk-entertainment-ntai-hsslive-25f-4x3-MB/playlist.m3u8",
                    artUri = "https://i.imgur.com/mN8j8CR.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_31_osbctv480p",
                    title = "OSBC TV (480p)",
                    artist = "Nigeria",
                    uri = "https://cloud.odysee.live/content/0edfb4b7fb52d2d5ae30e052ce6b61d376fcd662/master.m3u8",
                    artUri = "https://i.imgur.com/lj9WEq8.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_32_phenomenallifetv1080p",
                    title = "Phenomenal Life TV (1080p)",
                    artist = "Nigeria",
                    uri = "https://5be80bd118c27.streamlock.net:433/phenomenaltv/phenomenal/playlist.m3u8",
                    artUri = "https://i.imgur.com/JHbhLuK.jpg",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_33_plustvafrica720pnot247",
                    title = "Plus TV Africa (720p) [Not 24/7]",
                    artist = "Nigeria",
                    uri = "https://5c0740dba62b3.streamlock.net/plustv/myStream/playlist.m3u8",
                    artUri = "https://i.imgur.com/6RT3yao.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_34_qausaintv720p",
                    title = "Qausain TV (720p)",
                    artist = "Nigeria",
                    uri = "https://acasmedia3.acangroup.org/qausaintv/qausaintv_output/playlist.m3u8",
                    artUri = "https://i.ibb.co/N70QyQ7/Qausain-TV-logo.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_35_questtv240pnot247",
                    title = "Quest TV (240p) [Not 24/7]",
                    artist = "Nigeria",
                    uri = "https://media2.streambrothers.com:19360/8028/8028.m3u8",
                    artUri = "https://i.imgur.com/4ATTIQi.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_36_rapidtv",
                    title = "Rapid TV",
                    artist = "Nigeria",
                    uri = "https://stream-hls.castr-cdn.com/6a05a899db0ebd344a759542/live_9655bc604f8211f19e748901a3f596d0/index.m3u8",
                    artUri = "https://i.imgur.com/FkMNcj1.jpeg",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_37_ravetv576p",
                    title = "Rave TV (576p)",
                    artist = "Nigeria",
                    uri = "https://viewmedia7219.bozztv.com/wmedia/viewmedia100/web_039/Stream/playlist.m3u8",
                    artUri = "https://i.imgur.com/pXKQE0G.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_38_salvationtv576p",
                    title = "Salvation TV (576p)",
                    artist = "Nigeria",
                    uri = "https://viewmedia7219.bozztv.com/wmedia/viewmedia100/web_017/Stream/playlist.m3u8",
                    artUri = "https://i.imgur.com/Nb08dfK.png",
                    duration = 0,
                    isVideo = true,
                    category = "Prayer",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_39_silverbirdnews24576p",
                    title = "Silverbird News 24 (576p)",
                    artist = "Nigeria",
                    uri = "https://viewmedia7219.bozztv.com/wmedia/viewmedia100/web_029/Stream/playlist.m3u8",
                    artUri = "https://i.imgur.com/9jcV9oG.png",
                    duration = 0,
                    isVideo = true,
                    category = "News",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_40_soundcitytv",
                    title = "Soundcity TV",
                    artist = "Nigeria",
                    uri = "https://cs2.push2stream.com/SOUNDCITY/playlist.m3u8",
                    artUri = "https://i.imgur.com/fKtPcT4.png",
                    duration = 0,
                    isVideo = true,
                    category = "Music",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_41_sunnatv576p",
                    title = "Sunna TV (576p)",
                    artist = "Nigeria",
                    uri = "https://viewmedia7219.bozztv.com/wmedia/viewmedia100/web_015/Stream/playlist.m3u8",
                    artUri = "https://i.imgur.com/K2Snk2T.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_42_superscreentv412p",
                    title = "Superscreen TV (412p)",
                    artist = "Nigeria",
                    uri = "https://video1.getstreamhosting.com:1936/8398/8398/playlist.m3u8",
                    artUri = "https://i.imgur.com/uTNmSWE.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_43_synagoguetv576p",
                    title = "Synagogue TV (576p)",
                    artist = "Nigeria",
                    uri = "https://viewmedia7219.bozztv.com/wmedia/viewmedia100/web_038/Stream/playlist.m3u8",
                    artUri = "https://i.imgur.com/MOaBr9M.png",
                    duration = 0,
                    isVideo = true,
                    category = "Prayer",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_44_teleregencytv576p",
                    title = "Tele Regency TV (576p)",
                    artist = "Nigeria",
                    uri = "https://streams.playtv.com.ng/Tele-Regency_TRTV/index.m3u8",
                    artUri = "https://teleregency.tv/logo-white.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_45_tvc1080p",
                    title = "TVC (1080p)",
                    artist = "Nigeria",
                    uri = "https://tvce.gridpapaservers.com/TVCSEPT/ngrp:myStream_all/playlist.m3u8",
                    artUri = "https://i.imgur.com/ReV8R5S.png",
                    duration = 0,
                    isVideo = true,
                    category = "News",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_46_tvcnews576p",
                    title = "TVC News (576p)",
                    artist = "Nigeria",
                    uri = "http://69.64.57.208/tvcnews/playlist.m3u8",
                    artUri = "https://i.imgur.com/jaSq18B.png",
                    duration = 0,
                    isVideo = true,
                    category = "News",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_47_utvafrica",
                    title = "UTV Africa",
                    artist = "Nigeria",
                    uri = "https://list.iptvcat.com/my_list/s/e83a3d839daf641ca5b9d4fdd9cacd11.m3u8",
                    artUri = "https://www.utvafrica.com/images/utv-logo.png",
                    duration = 0,
                    isVideo = true,
                    category = "News",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_48_waffitv240p",
                    title = "Waffi TV (240p)",
                    artist = "Nigeria",
                    uri = "https://oqgdro3xd4rm-hls-live.5centscdn.com/waffiitvstreaminglivetfmediacast/e0885d428bea69e372309657f3bd895f.sdp/playlist.m3u8",
                    artUri = "https://i.imgur.com/3uEuzVH.jpeg",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_49_waptv720pnot247",
                    title = "Wap TV (720p) [Not 24/7]",
                    artist = "Nigeria",
                    uri = "https://newproxy3.vidivu.tv/waptv/index.m3u8",
                    artUri = "https://i.imgur.com/Djng8pI.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_50_wazobiamaxtvabuja720p",
                    title = "Wazobia Max TV Abuja (720p)",
                    artist = "Nigeria",
                    uri = "https://wazobia.live:8333/channel/wmaxabuja.m3u8",
                    artUri = "https://i.imgur.com/XYMKPPT.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_51_wazobiamaxtvnigeria720p",
                    title = "Wazobia Max TV Nigeria (720p)",
                    artist = "Nigeria",
                    uri = "https://wazobia.live:8333/channel/wmax.m3u8",
                    artUri = "https://i.imgur.com/IeioxBp.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_52_wazobiamaxtvportharcourt720p",
                    title = "Wazobia Max TV Port Harcourt (720p)",
                    artist = "Nigeria",
                    uri = "https://wazobia.live:8333/channel/wmaxph.m3u8",
                    artUri = "https://rndcdn.dstv.com/dstvcms/2016/10/20/WazobiaMaz_new4-4logo_001_xlrg.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_53_wholewordtv720p",
                    title = "Whole Word TV (720p)",
                    artist = "Nigeria",
                    uri = "https://mn-nl.mncdn.com/wholewordtv/wholewordtv/index.m3u8",
                    artUri = "https://i.imgur.com/71MRtuK.png",
                    duration = 0,
                    isVideo = true,
                    category = "Teaching",
                    region = "NG"
                ),
                MediaItemEntity(
                    id = "stream_ng_54_yobetv",
                    title = "Yobe TV",
                    artist = "Nigeria",
                    uri = "https://oqgdro3xd4rm-hls-live.5centscdn.com/tfmediacastlivestream24/60414fd886a863e5851f4559fb86e7f6.sdp/playlist.m3u8",
                    artUri = "https://ytv.ng/images/yobe/ytv-logo.jpg",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    ),
                MediaItemEntity(
                    id = "stream_gh_radio_0",
                    title = "057 radio",
                    artist = "Ghana Radio",
                    uri = "https://audio.alter-media.hr/9052/stream",
                    artUri = "https://hdz.hr/favicon.ico",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_1",
                    title = "360Africa Radio",
                    artist = "Ghana Radio",
                    uri = "https://node-10.zeno.fm/5gyngmysfzzuv?rj-ttl=5&rj-tok=AAABdx05YS8AqTsc1KFbnTJr2A",
                    artUri = "https://cdn.onlineradiobox.com/img/logo/3/88623.v1.png",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_2",
                    title = "Abdulbasit Abdulsamad",
                    artist = "Ghana Radio",
                    uri = "https://radio.mp3islam.com/listen/abdulbasit/radio.mp3",
                    artUri = "null",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_3",
                    title = "Adom Fm",
                    artist = "Ghana Radio",
                    uri = "https://mmg.streamguys1.com/AdomFM-mp3?key=9099965b0e49394af3be11b42a71f0faa90378311d2d85f5983495217e350f0a4636df5ecf5ea3f63e53cb9026d89102&awparams=playerid%3ASGplayer%3B&amsparams=playerid%3ASGplayer%3Bskey%3A1609841442537%3B&aw_0_req.gdpr=true&us_privacy=1YNN",
                    artUri = "https://cdn.webrad.io/images/logos/radio-com-gh/adom-fm.png",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_4",
                    title = "ADUM RADIO",
                    artist = "Ghana Radio",
                    uri = "http://stream.zeno.fm/dmn9atfb7ehvv",
                    artUri = "https://images.unsplash.com/photo-1548502624-9b512c1995a9?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_8",
                    title = "Agoo 96.9 FM",
                    artist = "Ghana Radio",
                    uri = "https://agoofm-atunwadigital.streamguys1.com/agoofm",
                    artUri = "https://cdn-profiles.tunein.com/s228220/images/logod.jpg?t=636911170360000000",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_9",
                    title = "Ahenfie FM 106.1MHz",
                    artist = "Ghana Radio",
                    uri = "https://radio.localstreamgh.com/listen/ahenfie_radio_/radio.mp3",
                    artUri = "https://cdn-profiles.tunein.com/s350080/images/logod.png?t=639145179930000000",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_10",
                    title = "Ahotor Fm",
                    artist = "Ghana Radio",
                    uri = "https://node-07.zeno.fm/bbpfy9pk21zuv?rj-ttl=5&rj-tok=AAABdtJi4QkAQOQEu89yxCY6mw",
                    artUri = "https://cdn-radiotime-logos.tunein.com/s270288g.png",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_11",
                    title = "Akomapa FM",
                    artist = "Ghana Radio",
                    uri = "http://mounthoreb1.primcast.com:8210/",
                    artUri = "https://images.unsplash.com/photo-1548502624-9b512c1995a9?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_12",
                    title = "Akomapa FM",
                    artist = "Ghana Radio",
                    uri = "https://stream.streamgenial.stream/cxxs2usduv8uv",
                    artUri = "https://cdn.onlineradiobox.com/img/l/4/7084.v9.png",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_13",
                    title = "Akomapa FM",
                    artist = "Ghana Radio",
                    uri = "http://mounthoreb1.primcast.com:8210/;",
                    artUri = "https://cdn.onlineradiobox.com/img/logo/4/7084.v3.png",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_14",
                    title = "Amen FM",
                    artist = "Ghana Radio",
                    uri = "https://ice7.securenetsystems.net/AMENFM",
                    artUri = "https://amenfm.com/wp-content/uploads/2024/01/AmenFMred.png",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_15",
                    title = "Angel FM",
                    artist = "Ghana Radio",
                    uri = "https://atunwadigital.streamguys1.com/angelfm",
                    artUri = "https://cdn-profiles.tunein.com/s110138/images/logoq.jpg?t=158204",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_16",
                    title = "Asaase Radio 99.5FM Accra",
                    artist = "Ghana Radio",
                    uri = "http://stream.radiojar.com/cyg76xcvp0hvv",
                    artUri = "https://scontent-los4-1.xx.fbcdn.net/v/t39.30808-6/481904456_980915947582722_2509686940645709777_n.jpg?_nc_cat=106&ccb=1-7&_nc_sid=6ee11a&_nc_eui2=AeGejgxTxnRpRJ9CCw9zK5LjXsnjS-iwXEleyeNL6LBcSfcgIwfBvFxN_t_rMA8pIoOaYkEhFO365voxbRdvn0ar&_nc_ohc=Vz3f8__xxHYQ7kNvwFjEFcA&_nc_oc=Adof5lEygF1ehASTlCsKHfKWnCxDrfwVC8BeCHZdVRbPu44-577aDE7Iy4jK4dwuiNV1I-lHHRem6wdHSK5LZ3x8&_nc_zt=23&_nc_ht=scontent-los4-1.xx&_nc_gid=wLVi1jVRxwTz2nhR4BofnQ&_nc_ss=7b2a8&oh=00_Af-KU_oV7yZ7eL_SqtjZezjqFP9tAqNwpJNf9RO0PPQ9iQ&oe=6A260003",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_17",
                    title = "Asempa Fm",
                    artist = "Ghana Radio",
                    uri = "https://mmg.streamguys1.com/AsempaFM-mp3?key=23f81ad063fc94d769b3f56668d742562a0645bb7ba0f5633dbce7d73d151195adfd4a7b4b2afeeff7fb81cf4ef6e790&awparams=playerid%3ASGplayer%3B&amsparams=playerid%3ASGplayer%3Bskey%3A1609843961148%3B&aw_0_req.gdpr=true&us_privacy=1YNN",
                    artUri = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRvvDQVvTFoGoa6mS_YzCiR6X_1wKA7SaTEuQ&usqp=CAU",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_18",
                    title = "Asenteman Radio",
                    artist = "Ghana Radio",
                    uri = "https://node-31.zeno.fm/whaba8v8d4duv?rj-ttl=5&rj-tok=AAABdtY3N84AAc3outs3-ohyeA",
                    artUri = "https://cdn-profiles.tunein.com/s224923/images/bannerx.jpg?t=636808145780000000",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_19",
                    title = "ATL FM 100.5MHz",
                    artist = "Ghana Radio",
                    uri = "https://atlfm1005.radioca.st/stream?type=http&nocache=231",
                    artUri = "https://cdn-profiles.tunein.com/s190555/images/logod.jpg?t=637048450040000000",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_20",
                    title = "B-Zar Radio",
                    artist = "Ghana Radio",
                    uri = "https://node-22.zeno.fm/mbzfkyrfkzzuv?rj-ttl=5&rj-tok=AAABdyiX_LoAerhRUzCmxkUzTg",
                    artUri = "https://cdn.onlineradiobox.com/img/logo/2/92582.v4.png",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_21",
                    title = "Box Lofi Radio",
                    artist = "Ghana Radio",
                    uri = "https://stream.zeno.fm/tabzverz0fctv",
                    artUri = "https://zeno.fm/_ipx/_/https://images.zeno.fm/iAxt21RULW18xMxbT74dT1wVoefziySqQ_UKAI7asFk/rs:fill:288:288/g:ce:0:0/aHR0cHM6Ly9wcm94eS56ZW5vLmZtL2NvbnRlbnQvc3RhdGlvbnMvZTFkYTZkZDUtZDA2ZS00ODA5LWJjMDQtNGNlODEzOGEwMzYyL2ltYWdlLz91PTE3MjU3MjAxODEwMDA.webp",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_22",
                    title = "Bramcom Radio Online",
                    artist = "Ghana Radio",
                    uri = "https://node-30.zeno.fm/5k38xxf1v7zuv?rj-ttl=5&rj-tok=AAABdx0o9UAAN89Op-WH21jpBw",
                    artUri = "https://cdn.onlineradiobox.com/img/logo/5/92385.v3.png",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_23",
                    title = "Cape FM",
                    artist = "Ghana Radio",
                    uri = "https://stream.zeno.fm/qpq7hygy77zuv",
                    artUri = "https://scontent-los4-1.xx.fbcdn.net/v/t39.30808-6/298517234_411173810998083_5547939028292378821_n.png?stp=dst-jpg_tt6&_nc_cat=100&ccb=1-7&_nc_sid=a5f93a&_nc_eui2=AeHZmoq2RAg7MiqJ-V9MytFZWDXJe7Vlfz1YNcl7tWV_PZ-mpceiGSVWmonYOIpqnq46m0MNc-ZaDbCsoa9M303C&_nc_ohc=Z8S280Hgy9EQ7kNvwHKqRmQ&_nc_oc=AdocYNSKDPudYPz0WOWDl1i12YQJ8aj6U5c6Dd40zyq-zxyGiSBBFDjMhIRHDY0Yfr8dr6RVOCtWd4aJB12OYOz4&_nc_zt=23&_nc_ht=scontent-los4-1.xx&_nc_gid=1U3xh4QAPimjkyNNzWJhQA&_nc_ss=7b2a8&oh=00_Af_8YKNHOrXeBZwRNXNhqKWxeT5aK6S2nFgjLiGZJhvbMw&oe=6A2601A5",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_24",
                    title = "ChinaGhana Fm",
                    artist = "Ghana Radio",
                    uri = "https://stream.zeno.fm/3w43axr9mg0uv",
                    artUri = "https://i.ytimg.com/vi/kgunluvspta/0.jpg",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_25",
                    title = "Citi FM",
                    artist = "Ghana Radio",
                    uri = "https://citi973fm.radioca.st/index.html:80/;stream.mp3",
                    artUri = "https://citinewsroom.com/wp-content/uploads/2019/08/cnr_logo_web.png",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_27",
                    title = "Connect Fm",
                    artist = "Ghana Radio",
                    uri = "https://node-04.zeno.fm/13tbqr42u8quv?rj-ttl=5&rj-tok=AAABdxzzpuYA9jSATDr69lNONQ",
                    artUri = "https://cdn.onlineradiobox.com/img/logo/3/76093.v5.png",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_28",
                    title = "Dadi Fm",
                    artist = "Ghana Radio",
                    uri = "https://node-04.zeno.fm/afzd4f4nq7zuv?rj-ttl=5&rj-tok=AAABdyiroWQAN3agP4vCJME8ig",
                    artUri = "https://cdn.onlineradiobox.com/img/logo/1/6981.v5.png",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_29",
                    title = "Evangelist Oduro Radio",
                    artist = "Ghana Radio",
                    uri = "https://listen.openstream.co/4103/audio",
                    artUri = "https://cdn-profiles.tunein.com/s336934/images/logod.png?t=638711993140000000",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_32",
                    title = "Fruitful Radio",
                    artist = "Ghana Radio",
                    uri = "https://node-09.zeno.fm/zavpry0xx7zuv?rj-ttl=5&rj-tok=AAABdx1fAO8AKVWNGL-5qa1y9A",
                    artUri = "https://cdn.onlineradiobox.com/img/logo/4/92524.v1.png",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_33",
                    title = "GBC Uniiq 95.7 FM",
                    artist = "Ghana Radio",
                    uri = "https://mediagh.us:2000/stream/uniiqfm",
                    artUri = "https://scontent-los4-1.xx.fbcdn.net/v/t39.30808-6/476211391_1140917558043725_5359624271353467391_n.jpg?_nc_cat=104&ccb=1-7&_nc_sid=cc71e4&_nc_eui2=AeEqGZgQ6-OlqrcluLfF6JbhisOaLLqCgiqKw5osuoKCKsvZ4wVITa7erhL1o64IcUUex8BdeVtSyPV4h1KIK9Ct&_nc_ohc=VfPfNOrJgIAQ7kNvwHoFeiC&_nc_oc=AdpN0f3NC0134TMKoSGlMclopwB1quLcka2MqaqpzxSzIk-fBpsocx9BidMbKbGHIzLTUlfYjUnaky7OT2NkCFoQ&_nc_zt=23&_nc_ht=scontent-los4-1.xx&_nc_gid=PkPsf4Ld7h1MITeA3Vi7Bw&_nc_ss=7b2a8&oh=00_Af9QWdBIutyG-YWrFHsvP0x41AmCffVzxCuRDuGoa2hFmQ&oe=6A25EB56",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_34",
                    title = "GBC Uniiq 95.7 FM",
                    artist = "Ghana Radio",
                    uri = "http://173.249.50.205:8004/",
                    artUri = "https://images.unsplash.com/photo-1548502624-9b512c1995a9?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_36",
                    title = "Ghana Music Radio",
                    artist = "Ghana Radio",
                    uri = "https://streaming.radio.co/s92f890821/listen",
                    artUri = "https://cdn.onlineradiobox.com/img/logo/2/7042.v2.png",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_37",
                    title = "Ghana Waves Radio",
                    artist = "Ghana Radio",
                    uri = "https://node-10.zeno.fm/pg5c7zx3p0quv?rj-ttl=5&rj-tok=AAABdtd3XeAAU1f9o-yKlGaPXg",
                    artUri = "https://ghanawaves.com//wp-content/uploads/2020/08/Ghana-Waves-Radio.png",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_38",
                    title = "GHERLA",
                    artist = "Ghana Radio",
                    uri = "http://89.39.189.52:8000/",
                    artUri = "https://images.unsplash.com/photo-1548502624-9b512c1995a9?q=80&w=350&auto=format&fit=crop",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    region = "GH"
                ),
                MediaItemEntity(
                    id = "stream_gh_radio_39",
                    title = "HANA RADIO - Ghana",
                    artist = "Ghana Radio",
                    uri = "https://stream.zeno.fm/qqaz1l2tujvuv",
                    artUri = "https://zeno.fm/_ipx/_/https://images.zeno.fm/ZJi8coXweWDKG7VUcdAJu3KuHmsSVONFWitoowmeeV4/rs:fill:2048:512/g:ce:0:0/aHR0cHM6Ly9wcm94eS56ZW5vLmZtL2NvbnRlbnQvc3RhdGlvbnMvMDhiYjljMDQtZjNjOS00NjY3LTk0YzQtYjgwNTU5OGJkNmYzL21pY3Jvc2l0ZS9iYWNrZ3JvdW5kX2ltYWdlLz91PTE3NDcwNjk2NDgwMDA.webp",
                    duration = 0,
                    isVideo = false,
                    category = "Radio",
                    ),
                MediaItemEntity(
                    id = "stream_mx_0",
                    title = "8NTV (1080p)",
                    artist = "Mexico TV",
                    uri = "https://60417ddeaf0d9.streamlock.net/ntv/videontv/playlist.m3u8",
                    artUri = "https://i.imgur.com/7ecgGIG.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_1",
                    title = "15TV (720p) [Not 24/7]",
                    artist = "Mexico TV",
                    uri = "https://cdn.fastocloud.com/leonel.m3u8",
                    artUri = "https://i.imgur.com/CZ8LKmA.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_2",
                    title = "A+ (1080p)",
                    artist = "Mexico TV",
                    uri = "http://45.189.62.33:8002/play/a0oc/index.m3u8",
                    artUri = "https://i.imgur.com/TWk9gfd.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_3",
                    title = "Acustik TV",
                    artist = "Mexico TV",
                    uri = "https://s5.mexside.net:1936/clientetv/clientetv/playlist.m3u8",
                    artUri = "https://i.imgur.com/dhiNIng.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_4",
                    title = "ADN 40 (1080p)",
                    artist = "Mexico TV",
                    uri = "http://45.189.62.33:8002/play/a0ob/index.m3u8",
                    artUri = "https://i.imgur.com/Og17U9N.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_5",
                    title = "Alcance TV (720p)",
                    artist = "Mexico TV",
                    uri = "https://5bf8041cb3fed.streamlock.net/AlcanceTV/AlcanceTV/playlist.m3u8",
                    artUri = "https://i.imgur.com/5nYjRlb.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_6",
                    title = "AMX Noticias (720p) [Not 24/7]",
                    artist = "Mexico TV",
                    uri = "https://5e50264bd6766.streamlock.net/mexiquense2/videomexiquense2/playlist.m3u8",
                    artUri = "https://i.imgur.com/snIU1UA.jpg",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_7",
                    title = "Antena TV",
                    artist = "Mexico TV",
                    uri = "https://5ca9af4645e15.streamlock.net/grd/videogrd/playlist.m3u8",
                    artUri = "https://i.imgur.com/1sAgSME.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_8",
                    title = "Así Sucede Guanajuato (720p) [Not 24/7]",
                    artist = "Mexico TV",
                    uri = "https://stream.oursnetworktv.com/latin/encoder13/playlist.m3u8",
                    artUri = "https://i.imgur.com/6W7HUtG.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_9",
                    title = "AXN Latin America Mexico",
                    artist = "Mexico TV",
                    uri = "http://190.11.225.124:5000/live/axn_hd/playlist.m3u8",
                    artUri = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/52/AXN_logo_%282015%29.svg/960px-AXN_logo_%282015%29.svg.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_10",
                    title = "Azteca 7 (1080p)",
                    artist = "Mexico TV",
                    uri = "http://45.189.62.33:8002/play/a0fm/index.m3u8",
                    artUri = "https://i.imgur.com/UtFXjIn.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_11",
                    title = "Azteca Deportes Network (1080p)",
                    artist = "Mexico TV",
                    uri = "http://181.119.66.28:8081/AZTECA-DEPORTES-HD/index.m3u8",
                    artUri = "https://match-images.icdb.tv/5913207f2494844101287828e87625eb.jpg",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_12",
                    title = "Azteca Internacional (1080p)",
                    artist = "Mexico TV",
                    uri = "https://azt-mun.otteravision.com/azt/mun/mun.m3u8",
                    artUri = "https://upload.wikimedia.org/wikipedia/commons/e/eb/Azteca_Internacional_logo_2023.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_13",
                    title = "Azteca Uno (1080p)",
                    artist = "Mexico TV",
                    uri = "http://45.189.62.33:8002/play/a0cc/index.m3u8",
                    artUri = "https://i.imgur.com/rRxgBtI.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_14",
                    title = "B15 Fresnillo (1080p)",
                    artist = "Mexico TV",
                    uri = "https://s5.mexside.net:1936/lsac/lsac/playlist.m3u8",
                    artUri = "https://i.imgur.com/kaUhEQy.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_15",
                    title = "B15 Zacatecas (1080p)",
                    artist = "Mexico TV",
                    uri = "https://s5.mexside.net:1936/envio2/envio2/playlist.m3u8",
                    artUri = "https://i.imgur.com/kaUhEQy.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_16",
                    title = "BAMOS TV (1080p) [Not 24/7]",
                    artist = "Mexico TV",
                    uri = "https://video2.getstreamhosting.com:19360/8092/8092.m3u8",
                    artUri = "https://i.imgur.com/ntK4D64.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_17",
                    title = "bitMe (1080p)",
                    artist = "Mexico TV",
                    uri = "https://cablered.iptvperu.tv:1936/cablered/bitme-srt_new/playlist.m3u8",
                    artUri = "https://i.imgur.com/79hrcx8.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_18",
                    title = "California Medios TV (720p)",
                    artist = "Mexico TV",
                    uri = "https://s5.mexside.net:1936/medios/medios/playlist.m3u8",
                    artUri = "https://i.imgur.com/IDvpRNI.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_19",
                    title = "Canal 5 (1080p)",
                    artist = "Mexico TV",
                    uri = "http://45.181.87.106/CANAL5HD/index.m3u8",
                    artUri = "https://i.imgur.com/uZdVSoW.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_20",
                    title = "Canal 5 TV Cozumel (1080p)",
                    artist = "Mexico TV",
                    uri = "https://video0.rogohosting.com:19360/tvcozumel/tvcozumel.m3u8",
                    artUri = "https://i.imgur.com/0nzNMuP.jpeg",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_21",
                    title = "Canal 10 Cancún (720p) [Not 24/7]",
                    artist = "Mexico TV",
                    uri = "http://stream2.dynalias.com:1935/live/tvlive1/playlist.m3u8",
                    artUri = "https://i.imgur.com/TvTn3hO.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_22",
                    title = "Canal 10 Chiapas (720p)",
                    artist = "Mexico TV",
                    uri = "https://5ca9af4645e15.streamlock.net/chiapas/videochiapas/playlist.m3u8",
                    artUri = "https://i.imgur.com/dw6puHC.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_23",
                    title = "Canal 10 Durango (1080p)",
                    artist = "Mexico TV",
                    uri = "https://5e50264bd6766.streamlock.net/canal10durango/videocanal10durango/playlist.m3u8",
                    artUri = "https://i.imgur.com/H34quHQ.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_24",
                    title = "Canal 13 Bajío (720p) [Not 24/7]",
                    artist = "Mexico TV",
                    uri = "https://stream-207963.castr.net/65313d270e749722b6474684/live_1c690d40dfb711eeabd66ba1b9ba93a1/index.fmp4.m3u8",
                    artUri = "https://i.imgur.com/SlycKgZ.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_25",
                    title = "Canal 13 Campeche (720p) [Not 24/7]",
                    artist = "Mexico TV",
                    uri = "https://stream-207963.castr.net/65313d270e749722b6474684/live_38c14fb0dfbd11ee8847c303f3096501/index.fmp4.m3u8",
                    artUri = "https://i.imgur.com/SlycKgZ.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_26",
                    title = "Canal 13 Chiapas (720p) [Not 24/7]",
                    artist = "Mexico TV",
                    uri = "https://stream-207963.castr.net/65313d270e749722b6474684/live_55784330f36711ee97a7a19a8448730c/index.fmp4.m3u8",
                    artUri = "https://i.imgur.com/SlycKgZ.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_27",
                    title = "Canal 13 Guadalajara (720p) [Not 24/7]",
                    artist = "Mexico TV",
                    uri = "https://stream-207963.castr.net/65313d270e749722b6474684/live_fa0ed380dfbe11eeabd66ba1b9ba93a1/index.fmp4.m3u8",
                    artUri = "https://i.imgur.com/SlycKgZ.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_28",
                    title = "Canal 13 Michoacán (720p) [Not 24/7]",
                    artist = "Mexico TV",
                    uri = "https://stream-207963.castr.net/65313d270e749722b6474684/live_0424eb20dfbf11ee821911a0b6ba5091/index.fmp4.m3u8",
                    artUri = "https://i.imgur.com/SlycKgZ.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                ),
                MediaItemEntity(
                    id = "stream_mx_29",
                    title = "Canal 13 Oaxaca (720p) [Not 24/7]",
                    artist = "Mexico TV",
                    uri = "https://stream-207963.castr.net/65313d270e749722b6474684/live_0bd65ed0dfbf11ee848c53d8a686e201/index.fmp4.m3u8",
                    artUri = "https://i.imgur.com/SlycKgZ.png",
                    duration = 0,
                    isVideo = true,
                    category = "Streams",
                    region = "MX"
                )
            )
            mediaDao.insertMediaList(defaults)
        }
    }

    suspend fun fetchEpg(url: String) = withContext(Dispatchers.IO) {
        try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                var inputStream = connection.inputStream
                if (url.endsWith(".gz") || connection.contentEncoding == "gzip") {
                    inputStream = java.util.zip.GZIPInputStream(inputStream)
                }

                val parser = android.util.Xml.newPullParser()
                parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(inputStream, null)

                val programs = mutableListOf<EpgProgramEntity>()
                var currentChannelId = ""
                var currentTitle = ""
                var currentDesc = ""
                var startTime = 0L
                var endTime = 0L
                
                val dateFormat = java.text.SimpleDateFormat("yyyyMMddHHmmss Z", java.util.Locale.US)
                val dateFormatNoZ = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US)

                fun parseTime(timeStr: String?): Long {
                    if (timeStr.isNullOrBlank()) return 0L
                    return try {
                        if (timeStr.contains(" ")) {
                            dateFormat.parse(timeStr)?.time ?: 0L
                        } else {
                            dateFormatNoZ.parse(timeStr)?.time ?: 0L
                        }
                    } catch (e: Exception) {
                        0L
                    }
                }

                var eventType = parser.eventType
                while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        org.xmlpull.v1.XmlPullParser.START_TAG -> {
                            val name = parser.name
                            if (name == "programme") {
                                currentChannelId = parser.getAttributeValue(null, "channel") ?: ""
                                startTime = parseTime(parser.getAttributeValue(null, "start"))
                                endTime = parseTime(parser.getAttributeValue(null, "stop"))
                            } else if (name == "title" && currentChannelId.isNotEmpty()) {
                                currentTitle = parser.nextText()
                            } else if (name == "desc" && currentChannelId.isNotEmpty()) {
                                currentDesc = parser.nextText()
                            }
                        }
                        org.xmlpull.v1.XmlPullParser.END_TAG -> {
                            if (parser.name == "programme") {
                                if (currentChannelId.isNotEmpty() && currentTitle.isNotEmpty()) {
                                    programs.add(
                                        EpgProgramEntity(
                                            channelId = currentChannelId,
                                            title = currentTitle,
                                            description = currentDesc,
                                            startTime = startTime,
                                            endTime = endTime
                                        )
                                    )
                                }
                                currentChannelId = ""
                                currentTitle = ""
                                currentDesc = ""
                                startTime = 0L
                                endTime = 0L
                                
                                // Batch insert to avoid memory overflow for large EPGs
                                if (programs.size >= 1000) {
                                    epgDao.insertPrograms(programs.toList())
                                    programs.clear()
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
                
                if (programs.isNotEmpty()) {
                    epgDao.insertPrograms(programs)
                }
                
                // Cleanup old
                epgDao.deleteOldPrograms(System.currentTimeMillis() - 24 * 60 * 60 * 1000) // keep last 24h
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun getScheduleForChannel(channelId: String): Flow<List<EpgProgramEntity>> {
        return epgDao.getScheduleForChannel(channelId)
    }

    fun getCurrentProgramFlow(channelId: String): Flow<EpgProgramEntity?> {
        return epgDao.getCurrentProgramFlow(channelId, System.currentTimeMillis())
    }

    fun getNextProgramFlow(channelId: String): Flow<EpgProgramEntity?> {
        return epgDao.getNextProgramFlow(channelId, System.currentTimeMillis())
    }
}
