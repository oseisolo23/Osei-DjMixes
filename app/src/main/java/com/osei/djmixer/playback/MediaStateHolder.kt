package com.osei.djmixer.playback

import com.osei.djmixer.ui.theme.*

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MediaStateHolder(context: Context) {
    private val sessionToken = SessionToken(context, ComponentName(context, DJMixerMediaService::class.java))
    private var controllerFuture: ListenableFuture<MediaController> = MediaController.Builder(context, sessionToken).buildAsync()
    
    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem = _currentMediaItem.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    val playbackState = _playbackState.asStateFlow()

    private val _controllerFlow = MutableStateFlow<MediaController?>(null)
    val controllerFlow = _controllerFlow.asStateFlow()

    private val _currentPlaylist = MutableStateFlow<List<MediaItem>>(emptyList())
    val currentPlaylist = _currentPlaylist.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _sleepTimerRemaining = MutableStateFlow<Long?>(null)
    val sleepTimerRemaining = _sleepTimerRemaining.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed = _playbackSpeed.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume = _volume.asStateFlow()

    private var controller: MediaController? = null
    
    private val sleepTimerRunnable = object : Runnable {
        override fun run() {
            _sleepTimerRemaining.value?.let { remaining ->
                if (remaining > 0) {
                    val nextRemaining = (remaining - 1000).coerceAtLeast(0)
                    _sleepTimerRemaining.value = nextRemaining
                    if (nextRemaining == 0L) {
                        controller?.pause()
                        _sleepTimerRemaining.value = null
                    } else {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 1000)
                    }
                }
            }
        }
    }

    private val positionUpdateRunnable = object : Runnable {
        override fun run() {
            controller?.let {
                _currentPosition.value = it.currentPosition
                _duration.value = it.duration.coerceAtLeast(0L)
                if (it.isPlaying) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 1000)
                }
            }
        }
    }

    init {
        controllerFuture.addListener({
            val controllerInstance = controllerFuture.get()
            controller = controllerInstance
            _controllerFlow.value = controllerInstance
            controller?.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    _currentMediaItem.value = mediaItem
                    _duration.value = controller?.duration?.coerceAtLeast(0L) ?: 0L
                    _playbackSpeed.value = controller?.playbackParameters?.speed ?: 1.0f
                }

                override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                    _playbackSpeed.value = playbackParameters.speed
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _shuffleModeEnabled.value = shuffleModeEnabled
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _repeatMode.value = repeatMode
                }

                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    controller?.let {
                        val items = mutableListOf<MediaItem>()
                        for (i in 0 until it.mediaItemCount) {
                            items.add(it.getMediaItemAt(i))
                        }
                        _currentPlaylist.value = items
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post(positionUpdateRunnable)
                    }
                }

                override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
                    // This is system volume tracking
                }
                
                override fun onVolumeChanged(volume: Float) {
                    _volume.value = volume
                }

                override fun onPlaybackStateChanged(state: Int) {
                    _playbackState.value = state
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) { android.util.Log.e("DJMixerPlayer", "ExoPlayer Error: ${error.message}", error); super.onPlayerError(error) }
            })
            _currentMediaItem.value = controller?.currentMediaItem
            _isPlaying.value = controller?.isPlaying ?: false
            _playbackState.value = controller?.playbackState ?: Player.STATE_IDLE
            _duration.value = controller?.duration?.coerceAtLeast(0L) ?: 0L
            _playbackSpeed.value = controller?.playbackParameters?.speed ?: 1.0f
            _volume.value = controller?.volume ?: 1.0f
            _shuffleModeEnabled.value = controller?.shuffleModeEnabled ?: false
            _repeatMode.value = controller?.repeatMode ?: Player.REPEAT_MODE_OFF
        }, MoreExecutors.directExecutor())
    }

    fun playMedia(mediaItem: MediaItem) {
        controller?.let {
            it.setMediaItem(mediaItem)
            it.prepare()
            it.play()
        }
    }

    fun setQueue(items: List<MediaItem>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        controller?.let {
            it.setMediaItems(items, startIndex, startPositionMs)
            it.prepare()
            it.play()
        }
    }

    fun addToQueue(mediaItem: MediaItem) {
        controller?.addMediaItem(mediaItem)
    }

    fun addNextToQueue(mediaItem: MediaItem) {
        controller?.let {
            val nextIndex = if (it.mediaItemCount > 0) it.currentMediaItemIndex + 1 else 0
            it.addMediaItem(nextIndex, mediaItem)
        }
    }

    fun clearQueue() {
        controller?.clearMediaItems()
    }

    fun seekTo(position: Long) {
        controller?.seekTo(position)
    }

    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun skipNext() = controller?.seekToNext()
    fun skipPrevious() = controller?.seekToPrevious()
    fun seekForward() = controller?.seekForward()
    fun seekBack() = controller?.seekBack()
    fun seekToItem(index: Int) = controller?.seekToDefaultPosition(index)
    
    fun removeQueueItem(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        controller?.moveMediaItem(fromIndex, toIndex)
    }
    
    fun setVolume(volume: Float) {
        controller?.volume = volume
        _volume.value = volume
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    fun setShuffleModeEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
        _shuffleModeEnabled.value = enabled
    }

    fun toggleShuffleMode() {
        val next = !_shuffleModeEnabled.value
        setShuffleModeEnabled(next)
    }

    fun setRepeatMode(repeatMode: Int) {
        controller?.repeatMode = repeatMode
        _repeatMode.value = repeatMode
    }

    fun toggleRepeatMode() {
        val next = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        setRepeatMode(next)
    }

    fun setSleepTimer(durationMs: Long?) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.removeCallbacks(sleepTimerRunnable)
        _sleepTimerRemaining.value = durationMs
        if (durationMs != null && durationMs > 0) {
            handler.postDelayed(sleepTimerRunnable, 1000)
        }
    }

    fun release() {
        MediaController.releaseFuture(controllerFuture)
    }
}
