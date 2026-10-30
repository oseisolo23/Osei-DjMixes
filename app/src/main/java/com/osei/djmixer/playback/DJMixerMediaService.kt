package com.osei.djmixer.playback

import com.osei.djmixer.ui.theme.*

import android.app.PendingIntent
import android.content.Intent
import kotlinx.coroutines.launch
import android.os.Handler
import android.os.Looper
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class DJMixerMediaService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private var fadeOutPlayer: ExoPlayer? = null
    private var fadeAnimator: ValueAnimator? = null
    private var fadeInAnimator: ValueAnimator? = null
    
    private var isCrossfading = false
    
    private val serviceJob = kotlinx.coroutines.SupervisorJob()
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + serviceJob)
    private lateinit var networkObserver: com.osei.djmixer.util.NetworkConnectivityObserver
    private var wasPlayingBeforeNetworkLoss = false
    private var hadNetworkError = false
    private var consecutiveErrorCount = 0
    
    private val crossfadeEnabled: Boolean
        get() = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE).getBoolean("enable_crossfade", true)
        
    private val crossfadeDuration: Long
        get() = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE).getFloat("crossfade_duration", 5f).toLong() * 1000L
    
    private val crossfadeRunnable = object : Runnable {
        override fun run() {
            try {
                if (crossfadeEnabled) {
                    val player = mediaSession?.player as? ExoPlayer
                    if (player != null && player.isPlaying && player.playbackState == androidx.media3.common.Player.STATE_READY) {
                        val remain = player.duration - player.currentPosition
                        
                        if (!isCrossfading && player.hasNextMediaItem() && remain > 100 && remain <= crossfadeDuration) {
                            startCrossfade(player)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (!isCrossfading) {
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == "ACTION_ALARM_PLAY") {
            val mediaUri = intent.getStringExtra("MEDIA_URI")
            if (mediaUri != null) {
                val player = mediaSession?.player
                if (player != null) {
                    val mediaItem = androidx.media3.common.MediaItem.Builder()
                        .setUri(mediaUri)
                        .setMediaId("alarm_radio")
                        .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle("Morning Alarm").build())
                        .build()
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()
                }
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val playbackContext = this

        val cacheDataSourceFactory = com.osei.djmixer.playback.MediaCacheManager.getCacheDataSourceFactory(playbackContext)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(playbackContext)
            .setDataSourceFactory(cacheDataSourceFactory)

        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(playbackContext)
        val trackSelectorParams = trackSelector.buildUponParameters()
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE) // Allow 4K/HD video
            .setMaxVideoBitrate(Int.MAX_VALUE)
            .setPreferredAudioMimeTypes(androidx.media3.common.MimeTypes.AUDIO_AAC, androidx.media3.common.MimeTypes.AUDIO_MPEG)
            .build()
        trackSelector.parameters = trackSelectorParams
        
        val player = ExoPlayer.Builder(playbackContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            // .setWakeMode(androidx.media3.common.C.WAKE_MODE_LOCAL) // Removed to avoid AppOps WakeLock error
            .build()
        
        // Connect the active audio session ID to the Equalizer and Visualizer
        com.osei.djmixer.playback.AudioEqualizerManager.instance.initEqualizer(player.audioSessionId)
        com.osei.djmixer.playback.AudioVisualizerManager.instance.initVisualizer(player.audioSessionId)
        com.osei.djmixer.playback.AudioVisualizerManager.instance.setPlaying(player.isPlaying)
        
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != 0 && audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                    com.osei.djmixer.playback.AudioEqualizerManager.instance.initEqualizer(audioSessionId)
                    com.osei.djmixer.playback.AudioVisualizerManager.instance.initVisualizer(audioSessionId)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                com.osei.djmixer.playback.AudioVisualizerManager.instance.setPlaying(isPlaying)
                if (isPlaying) { consecutiveErrorCount = 0 }
            }
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Log as warning to prevent triggering crash alerts for dead streams
                android.util.Log.w("RadioPlayerError", "Playback Error: ${error.message}")
                val errorMsg = error.cause?.message ?: error.message
                var showToast = true
                
                val msg = if (errorMsg?.contains("YouTube streaming is currently unavailable") == true) {
                    "YouTube streaming is currently unavailable. Please try again later."
                } else if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) {
                    hadNetworkError = true
                    "No internet connection. Please check your network and try again."
                } else if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                    showToast = false // don't spam toasts for dead streams
                    "Stream is currently offline (Bad HTTP Status). Skipping..."
                } else {
                    "Error playing stream: ${error.message}"
                }
                
                if (showToast) {
                    android.widget.Toast.makeText(this@DJMixerMediaService, msg, android.widget.Toast.LENGTH_LONG).show()
                }
                
                consecutiveErrorCount++
                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) {
                    // Do nothing here, NetworkObserver will handle it when connection returns
                } else if (consecutiveErrorCount < 3 && player.hasNextMediaItem()) {
                    player.seekToNext()
                    player.prepare()
                    player.play()
                } else if (consecutiveErrorCount >= 3) {
                    android.util.Log.e("DJMixerMediaService", "Too many consecutive playback errors. Stopping.")
                    player.pause()
                }
            }
            
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                // If the user skips manually or transition happens, reset our checks
                isCrossfading = false
                player.volume = 1f
                handler.removeCallbacks(crossfadeRunnable)
                handler.post(crossfadeRunnable)
            }
        })
        
        networkObserver = com.osei.djmixer.util.NetworkConnectivityObserver(this)
        serviceScope.launch {
            networkObserver.isOnline.collect { isOnline ->
                if (!isOnline) {
                    if (player.isPlaying) {
                        wasPlayingBeforeNetworkLoss = true
                    }
                } else {
                    if (hadNetworkError || wasPlayingBeforeNetworkLoss) {
                        android.widget.Toast.makeText(this@DJMixerMediaService, "Internet connected. Retrying playback...", android.widget.Toast.LENGTH_SHORT).show()
                        player.prepare()
                        player.play()
                        wasPlayingBeforeNetworkLoss = false
                        hadNetworkError = false
                    }
                }
            }
        }
        
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = intent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val sessionBuilder = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    return MediaSession.ConnectionResult.accept(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                        session.player.availableCommands
                    )
                }
                
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): com.google.common.util.concurrent.ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    // This allows external devices (like Car Bluetooth) to resume playback
                    // even if the app was recently stopped but is kept in recent memory.
                    return super.onPlaybackResumption(mediaSession, controller)
                }
            })
        pendingIntent?.let {
            sessionBuilder.setSessionActivity(it)
        }
        
        mediaSession = sessionBuilder.build()
        handler.post(crossfadeRunnable)
    }

    private fun startCrossfade(mainPlayer: ExoPlayer) {
        val currentItem = mainPlayer.currentMediaItem ?: return
        val currentPosition = mainPlayer.currentPosition
        val remain = mainPlayer.duration - currentPosition
        
        if (remain <= 0) return
        
        isCrossfading = true
        
        val playbackContext = this
        
        val cacheDataSourceFactory = com.osei.djmixer.playback.MediaCacheManager.getCacheDataSourceFactory(playbackContext)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(playbackContext)
            .setDataSourceFactory(cacheDataSourceFactory)

        // Setup fadeOutPlayer to finish the current track
        fadeOutPlayer?.release()
        fadeOutPlayer = ExoPlayer.Builder(playbackContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                setMediaItem(currentItem)
                seekTo(currentPosition)
                volume = mainPlayer.volume
                playWhenReady = true
                prepare()
            }
            
        // Start fade out animation for the outgoing track
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(mainPlayer.volume, 0f).apply {
            duration = remain
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                fadeOutPlayer?.volume = anim.animatedValue as Float
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    fadeOutPlayer?.release()
                    fadeOutPlayer = null
                    isCrossfading = false
                    handler.post(crossfadeRunnable)
                }
            })
            start()
        }
        
        // Skip current player to the next track and fade it in
        mainPlayer.volume = 0f
        mainPlayer.seekToNextMediaItem()
        
        fadeInAnimator?.cancel()
        fadeInAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = crossfadeDuration
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                if (mediaSession?.player?.currentMediaItem != null) {
                    (mediaSession?.player as? ExoPlayer)?.volume = anim.animatedValue as Float
                }
            }
            start()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null) {
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                stopSelf()
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceJob.cancel()
        handler.removeCallbacksAndMessages(null)
        fadeAnimator?.cancel()
        fadeInAnimator?.cancel()
        fadeOutPlayer?.release()
        
        com.osei.djmixer.playback.AudioEqualizerManager.instance.release()
        com.osei.djmixer.playback.AudioVisualizerManager.instance.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
