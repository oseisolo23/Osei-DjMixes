package com.osei.djmixer.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.osei.djmixer.data.MediaItemEntity
import com.osei.djmixer.data.MediaRepository
import com.osei.djmixer.ui.analysis.AudioTrackAnalyzer
import com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.AutoDjMixMode
import com.osei.djmixer.ui.analysis.AudioTrackAnalyzer.TransitionScore
import com.osei.djmixer.ui.analysis.TrackAnalysisResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Production-ready DJ Mixer ViewModel.
 * Controls Deck A, Deck B, Real Audio DSP, Hot Cues, Loops, Real SoundPool Sampler,
 * Auto DJ Mixing, and Honest WAV Recording.
 */
class MixerViewModel(
    private val context: Context,
    private val repository: MediaRepository
) : ViewModel() {

    // Audio Processors for Deck A and Deck B
    val djProcessorA = DjAudioProcessor()
    val djProcessorB = DjAudioProcessor()

    // Real Audio Sampler
    private val sampleManager = SampleManager(context)

    // Hot Cue Manager
    private val hotCueManager = HotCueManager(context)

    // Master Controls
    private val _masterVolume = MutableStateFlow(1.0f)
    val masterVolume = _masterVolume.asStateFlow()

    private val _crossfade = MutableStateFlow(0.5f) // 0.0 = 100% Deck A, 0.5 = Center, 1.0 = 100% Deck B
    val crossfade = _crossfade.asStateFlow()

    // Performance Mode
    private val _performanceMode = MutableStateFlow(false)
    val performanceMode = _performanceMode.asStateFlow()
    fun togglePerformanceMode() { _performanceMode.value = !_performanceMode.value }

    // Active bottom tab
    private val _selectedTab = MutableStateFlow("LIBRARY")
    val selectedTab = _selectedTab.asStateFlow()
    fun selectTab(tab: String) { _selectedTab.value = tab }

    // Deck Media
    private val _deckAMedia = MutableStateFlow<MediaItemEntity?>(null)
    val deckAMedia = _deckAMedia.asStateFlow()

    private val _deckBMedia = MutableStateFlow<MediaItemEntity?>(null)
    val deckBMedia = _deckBMedia.asStateFlow()

    // Volumes
    private val _volumeA = MutableStateFlow(1.0f)
    val volumeA = _volumeA.asStateFlow()

    private val _volumeB = MutableStateFlow(1.0f)
    val volumeB = _volumeB.asStateFlow()

    private val _gainA = MutableStateFlow(0.707f)
    val gainAState = _gainA.asStateFlow()

    private val _gainB = MutableStateFlow(0.707f)
    val gainBState = _gainB.asStateFlow()

    // Speed & Pitch Lock
    val speedA = MutableStateFlow(1.0f)
    val speedB = MutableStateFlow(1.0f)

    private val _pitchLockA = MutableStateFlow(true) // Default key lock on
    val pitchLockA = _pitchLockA.asStateFlow()

    private val _pitchLockB = MutableStateFlow(true)
    val pitchLockB = _pitchLockB.asStateFlow()

    // Playback States
    val isPlayingA = MutableStateFlow(false)
    val isPlayingB = MutableStateFlow(false)
    val positionA = MutableStateFlow(0L)
    val positionB = MutableStateFlow(0L)
    val durationA = MutableStateFlow(0L)
    val durationB = MutableStateFlow(0L)

    // BPM & Keys
    val bpmA = djProcessorA.bpm
    val bpmB = djProcessorB.bpm

    private val _keyA = MutableStateFlow<String?>(null)
    val keyA = _keyA.asStateFlow()
    private val _energyA = MutableStateFlow<String?>("UNKNOWN")
    val energyA = _energyA.asStateFlow()

    private val _keyB = MutableStateFlow<String?>(null)
    val keyB = _keyB.asStateFlow()
    private val _energyB = MutableStateFlow<String?>("UNKNOWN")
    val energyB = _energyB.asStateFlow()

    private val tapTimesA = mutableListOf<Long>()
    private val tapTimesB = mutableListOf<Long>()

    private val _isDetectingBpmA = MutableStateFlow(false)
    val isDetectingBpmA = _isDetectingBpmA.asStateFlow()
    private val _isDetectingBpmB = MutableStateFlow(false)
    val isDetectingBpmB = _isDetectingBpmB.asStateFlow()

    // Real Audio Waveforms
    val waveformHistoryA = djProcessorA.waveformHistory
    val waveformHistoryB = djProcessorB.waveformHistory

    private val _trackWaveformA = MutableStateFlow<FloatArray?>(null)
    val trackWaveformA = _trackWaveformA.asStateFlow()

    private val _trackWaveformB = MutableStateFlow<FloatArray?>(null)
    val trackWaveformB = _trackWaveformB.asStateFlow()

    private val _isAnalyzingWaveformA = MutableStateFlow(false)
    val isAnalyzingWaveformA = _isAnalyzingWaveformA.asStateFlow()

    private val _isAnalyzingWaveformB = MutableStateFlow(false)
    val isAnalyzingWaveformB = _isAnalyzingWaveformB.asStateFlow()

    // 3-Band EQs & DJ Filter
    private val _eqLowA = MutableStateFlow(1.0f)
    val eqLowA = _eqLowA.asStateFlow()
    private val _eqMidA = MutableStateFlow(1.0f)
    val eqMidA = _eqMidA.asStateFlow()
    private val _eqHighA = MutableStateFlow(1.0f)
    val eqHighA = _eqHighA.asStateFlow()
    private val _filterA = MutableStateFlow(0.5f) // 0.0 = LPF, 0.5 = Bypass, 1.0 = HPF
    val filterA = _filterA.asStateFlow()

    private val _eqLowB = MutableStateFlow(1.0f)
    val eqLowB = _eqLowB.asStateFlow()
    private val _eqMidB = MutableStateFlow(1.0f)
    val eqMidB = _eqMidB.asStateFlow()
    private val _eqHighB = MutableStateFlow(1.0f)
    val eqHighB = _eqHighB.asStateFlow()
    private val _filterB = MutableStateFlow(0.5f)
    val filterB = _filterB.asStateFlow()

    // Real DSP Effects
    private val _activeEffects = MutableStateFlow<Set<String>>(emptySet())
    val activeEffects = _activeEffects.asStateFlow()

    private val _echoEnabledA = MutableStateFlow(false)
    val echoEnabledA = _echoEnabledA.asStateFlow()
    private val _reverbEnabledA = MutableStateFlow(false)
    val reverbEnabledA = _reverbEnabledA.asStateFlow()

    private val _echoEnabledB = MutableStateFlow(false)
    val echoEnabledB = _echoEnabledB.asStateFlow()
    private val _reverbEnabledB = MutableStateFlow(false)
    val reverbEnabledB = _reverbEnabledB.asStateFlow()

    // Hot Cues (4 per deck)
    private val _hotCuesA = MutableStateFlow<List<Long?>>(listOf(null, null, null, null))
    val hotCuesA = _hotCuesA.asStateFlow()

    private val _hotCuesB = MutableStateFlow<List<Long?>>(listOf(null, null, null, null))
    val hotCuesB = _hotCuesB.asStateFlow()

    // Beat-Based Loops (1, 2, 4, 8, 16 beats)
    private val _activeLoopA = MutableStateFlow<Pair<Long, Long>?>(null)
    val activeLoopA = _activeLoopA.asStateFlow()
    private val _loopBeatsA = MutableStateFlow<Int?>(null)
    val loopBeatsA = _loopBeatsA.asStateFlow()

    private val _activeLoopB = MutableStateFlow<Pair<Long, Long>?>(null)
    val activeLoopB = _activeLoopB.asStateFlow()
    private val _loopBeatsB = MutableStateFlow<Int?>(null)
    val loopBeatsB = _loopBeatsB.asStateFlow()

    // Headphone Cue
    private val _headphoneCueA = MutableStateFlow(false)
    val headphoneCueA = _headphoneCueA.asStateFlow()
    private val _headphoneCueB = MutableStateFlow(false)
    val headphoneCueB = _headphoneCueB.asStateFlow()

    // Real Audio Track Analysis (BPM, Key, Energy)
    private val _trackAnalysisA = MutableStateFlow<TrackAnalysisResult?>(null)
    val trackAnalysisA = _trackAnalysisA.asStateFlow()

    private val _trackAnalysisB = MutableStateFlow<TrackAnalysisResult?>(null)
    val trackAnalysisB = _trackAnalysisB.asStateFlow()

    // Real Auto DJ
    data class AutoDjTelemetry(
        val isRunning: Boolean = false,
        val phase: String = "IDLE",
        val activeDeck: String = "A",
        val nextTrack: MediaItemEntity? = null,
        val nextTrackAnalysis: TrackAnalysisResult? = null,
        val currentTrackAnalysis: TrackAnalysisResult? = null,
        val transitionScore: TransitionScore? = null,
        val secondsUntilTransition: Int = 0,
        val isBeatSynced: Boolean = false
    )

    private val _isAutoDjEnabled = MutableStateFlow(false)
    val isAutoDjEnabled = _isAutoDjEnabled.asStateFlow()
    val autoDjEnabled = _isAutoDjEnabled.asStateFlow()
    private val _autoDjTelemetry = MutableStateFlow(AutoDjTelemetry())
    val autoDjTelemetry = _autoDjTelemetry.asStateFlow()
    private val _autoDjMode = MutableStateFlow(AutoDjMixMode.SMOOTH_FLOW)
    val autoDjMode = _autoDjMode.asStateFlow()
    private var forceImmediateTransition = false
    private val skippedTrackIds = mutableSetOf<String>()
    private var autoDjJob: Job? = null
    private val recentlyPlayedTrackIds = mutableListOf<String>()

    // DJ Recording (Honest WAV)
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()
    private val _recordingDurationSeconds = MutableStateFlow(0L)
    val recordingDurationSeconds = _recordingDurationSeconds.asStateFlow()
    private var recordingTimerJob: Job? = null
    private var mixRecorder: MixRecorder? = null
    private val recordingBufferA = ArrayDeque<Short>()
    private val recordingBufferB = ArrayDeque<Short>()

    // Sleep Timer
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs = _sleepTimerRemainingMs.asStateFlow()
    private var sleepTimerJob: Job? = null

    // Storage / File Browser
    private val _currentPath = MutableStateFlow<File?>(null)
    val currentPath = _currentPath.asStateFlow()

    private val _browserItems = MutableStateFlow<List<File>>(emptyList())
    val browserItems = _browserItems.asStateFlow()

    // Audio Attributes
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    // ExoPlayers with integrated DjAudioProcessor
    val playerA: ExoPlayer = ExoPlayer.Builder(
        context,
        object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setAudioProcessors(arrayOf(djProcessorA))
                    .build()
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
    ).setMediaSourceFactory(
        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
            .setDataSourceFactory(com.osei.djmixer.playback.MediaCacheManager.getCacheDataSourceFactory(context))
    ).setAudioAttributes(audioAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    val playerB: ExoPlayer = ExoPlayer.Builder(
        context,
        object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setAudioProcessors(arrayOf(djProcessorB))
                    .build()
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
    ).setMediaSourceFactory(
        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
            .setDataSourceFactory(com.osei.djmixer.playback.MediaCacheManager.getCacheDataSourceFactory(context))
    ).setAudioAttributes(audioAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    // Media Session
    private val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(playerA) {
        override fun play() {
            if (!playerA.isPlaying && !playerB.isPlaying) {
                if (deckAMedia.value != null) playerA.play()
                else if (deckBMedia.value != null) playerB.play()
            } else if (!playerA.isPlaying && deckAMedia.value != null && crossfade.value < 0.5f) {
                playerA.play()
            } else if (!playerB.isPlaying && deckBMedia.value != null && crossfade.value > 0.5f) {
                playerB.play()
            }
        }

        override fun pause() {
            if (playerA.isPlaying) playerA.pause()
            if (playerB.isPlaying) playerB.pause()
        }

        override fun seekToNext() {
            toggleAutoDj()
        }

        override fun seekToNextMediaItem() {
            toggleAutoDj()
        }
    }

    private val mediaSession = androidx.media3.session.MediaSession.Builder(context.applicationContext, forwardingPlayer)
        .setId("MixerSession_${System.currentTimeMillis()}")
        .setCallback(object : androidx.media3.session.MediaSession.Callback {
            override fun onConnect(
                session: androidx.media3.session.MediaSession,
                controller: androidx.media3.session.MediaSession.ControllerInfo
            ): androidx.media3.session.MediaSession.ConnectionResult {
                return androidx.media3.session.MediaSession.ConnectionResult.accept(
                    androidx.media3.session.MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                    session.player.availableCommands
                )
            }
        })
        .build()

    init {
        setupPlayer(playerA, isPlayingA, positionA, durationA, speedA, _activeLoopA)
        setupPlayer(playerB, isPlayingB, positionB, durationB, speedB, _activeLoopB)

        // Equal-power crossfader and volume audio routing
        viewModelScope.launch {
            combine(crossfade, volumeA, volumeB, masterVolume) { cf, vA, vB, mv ->
                Quadruple(cf, vA, vB, mv)
            }.collect { (cf, vA, vB, mv) ->
                val clampedCf = cf.coerceIn(0.0f, 1.0f)
                val cfRad = (clampedCf * PI / 2.0).toFloat()
                val computedGainA = cos(cfRad.toDouble()).toFloat() * vA * mv
                val computedGainB = sin(cfRad.toDouble()).toFloat() * vB * mv

                _gainA.value = computedGainA
                _gainB.value = computedGainB

                djProcessorA.masterGain = computedGainA
                djProcessorB.masterGain = computedGainB

                playerA.volume = 1.0f
                playerB.volume = 1.0f
            }
        }

        // Recording buffer tap from DSP
        djProcessorA.onBufferProcessed = { buffer, _ ->
            if (_isRecording.value) {
                processRecordingBuffer(buffer, true)
            }
        }
        djProcessorB.onBufferProcessed = { buffer, _ ->
            if (_isRecording.value) {
                processRecordingBuffer(buffer, false)
            }
        }

        // Persist detected BPM
        viewModelScope.launch {
            bpmA.collect { valBpm ->
                val media = _deckAMedia.value
                if (valBpm > 0 && media != null && media.bpm != valBpm) {
                    repository.updateMediaBpm(media.id, valBpm)
                }
            }
        }
        viewModelScope.launch {
            bpmB.collect { valBpm ->
                val media = _deckBMedia.value
                if (valBpm > 0 && media != null && media.bpm != valBpm) {
                    repository.updateMediaBpm(media.id, valBpm)
                }
            }
        }
    }

    private fun setupPlayer(
        player: ExoPlayer,
        isPlayingFlow: MutableStateFlow<Boolean>,
        positionFlow: MutableStateFlow<Long>,
        durationFlow: MutableStateFlow<Long>,
        speedFlow: MutableStateFlow<Float>,
        activeLoopFlow: MutableStateFlow<Pair<Long, Long>?>
    ) {
        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                isPlayingFlow.value = p.playWhenReady && p.playbackState != Player.STATE_ENDED && p.playbackState != Player.STATE_IDLE
            }
            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                positionFlow.value = new.positionMs
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    durationFlow.value = player.duration.coerceAtLeast(0)
                }
            }
        })

        // Lifecycle-aware progress & loop boundary monitor
        viewModelScope.launch {
            while (true) {
                if (player.isPlaying) {
                    val pos = player.currentPosition
                    positionFlow.value = pos

                    // Beat Loop enforcement
                    activeLoopFlow.value?.let { loop ->
                        if (pos >= loop.second) {
                            player.seekTo(loop.first)
                        }
                    }
                }
                delay(20)
            }
        }
    }

    // --- SAMPLER ---
    fun playSample(sampleName: String) {
        sampleManager.play(sampleName, _masterVolume.value)
    }

    // --- TRACK LOADING & WAVEFORMS ---
    fun loadToDeckA(media: MediaItemEntity) {
        _deckAMedia.value = media
        tapTimesA.clear()
        _hotCuesA.value = hotCueManager.getCuesForTrack(media.id)
        _activeLoopA.value = null
        _loopBeatsA.value = null

        if (media.bpm != null && media.bpm > 0) {
            djProcessorA.setBpm(media.bpm)
        } else {
            djProcessorA.flush()
        }

        playerA.setMediaItem(MediaItem.fromUri(media.uri))
        playerA.prepare()

        // Extract real waveform asynchronously
        _isAnalyzingWaveformA.value = true
        _trackWaveformA.value = null
        viewModelScope.launch {
            try {
                val peaks = WaveformExtractor.extractWaveform(context, media.uri)
                _trackWaveformA.value = peaks
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isAnalyzingWaveformA.value = false
            }
        }

        // Real Audio Track Analysis (BPM, Harmonic Key, Energy)
        _isDetectingBpmA.value = true
        _trackAnalysisA.value = null
        _keyA.value = null
        _energyA.value = "UNKNOWN"
        viewModelScope.launch {
            try {
                val analysis = AudioTrackAnalyzer.analyzeTrack(context, media, repository)
                _trackAnalysisA.value = analysis
                _keyA.value = analysis.musicalKey
                _energyA.value = analysis.energyLevel
                if (analysis.bpm != null && analysis.bpm > 0) {
                    djProcessorA.setBpm(analysis.bpm)
                    _deckAMedia.value = media.copy(bpm = analysis.bpm)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isDetectingBpmA.value = false
            }
        }
    }

    fun loadToDeckB(media: MediaItemEntity) {
        _deckBMedia.value = media
        tapTimesB.clear()
        _hotCuesB.value = hotCueManager.getCuesForTrack(media.id)
        _activeLoopB.value = null
        _loopBeatsB.value = null

        if (media.bpm != null && media.bpm > 0) {
            djProcessorB.setBpm(media.bpm)
        } else {
            djProcessorB.flush()
        }

        playerB.setMediaItem(MediaItem.fromUri(media.uri))
        playerB.prepare()

        // Extract real waveform asynchronously
        _isAnalyzingWaveformB.value = true
        _trackWaveformB.value = null
        viewModelScope.launch {
            try {
                val peaks = WaveformExtractor.extractWaveform(context, media.uri)
                _trackWaveformB.value = peaks
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isAnalyzingWaveformB.value = false
            }
        }

        // Real Audio Track Analysis (BPM, Harmonic Key, Energy)
        _isDetectingBpmB.value = true
        _trackAnalysisB.value = null
        _keyB.value = null
        _energyB.value = "UNKNOWN"
        viewModelScope.launch {
            try {
                val analysis = AudioTrackAnalyzer.analyzeTrack(context, media, repository)
                _trackAnalysisB.value = analysis
                _keyB.value = analysis.musicalKey
                _energyB.value = analysis.energyLevel
                if (analysis.bpm != null && analysis.bpm > 0) {
                    djProcessorB.setBpm(analysis.bpm)
                    _deckBMedia.value = media.copy(bpm = analysis.bpm)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isDetectingBpmB.value = false
            }
        }
    }

    // --- PLAYBACK CONTROLS ---
    fun togglePlayA() {
        if (playerA.playWhenReady) playerA.pause() else playerA.play()
    }

    fun togglePlayB() {
        if (playerB.playWhenReady) playerB.pause() else playerB.play()
    }

    fun cueA() {
        playerA.pause()
        playerA.seekTo(0)
    }

    fun cueB() {
        playerB.pause()
        playerB.seekTo(0)
    }

    fun seekA(posMs: Long) {
        playerA.seekTo(posMs.coerceAtLeast(0L))
    }

    fun seekB(posMs: Long) {
        playerB.seekTo(posMs.coerceAtLeast(0L))
    }

    // --- TEMPO, PITCH LOCK & SPEED ---
    fun setSpeedA(s: Float) {
        val clamped = s.coerceIn(0.5f, 2.0f)
        speedA.value = clamped
        updatePlaybackParametersA()
    }

    fun setSpeedB(s: Float) {
        val clamped = s.coerceIn(0.5f, 2.0f)
        speedB.value = clamped
        updatePlaybackParametersB()
    }

    fun togglePitchLockA() {
        _pitchLockA.value = !_pitchLockA.value
        updatePlaybackParametersA()
    }

    fun togglePitchLockB() {
        _pitchLockB.value = !_pitchLockB.value
        updatePlaybackParametersB()
    }

    private fun updatePlaybackParametersA() {
        val speed = speedA.value
        val pitch = if (_pitchLockA.value) 1.0f else speed
        playerA.playbackParameters = PlaybackParameters(speed, pitch)
    }

    private fun updatePlaybackParametersB() {
        val speed = speedB.value
        val pitch = if (_pitchLockB.value) 1.0f else speed
        playerB.playbackParameters = PlaybackParameters(speed, pitch)
    }

    fun syncBtoA() {
        val baseBpmA = bpmA.value.toFloat()
        val baseBpmB = bpmB.value.toFloat()
        if (baseBpmA > 0f && baseBpmB > 0f) {
            val targetSpeed = (baseBpmA * speedA.value) / baseBpmB
            setSpeedB(targetSpeed.coerceIn(0.5f, 2.0f))
        } else if (speedA.value > 0) {
            setSpeedB(speedA.value)
        }
    }

    fun syncAtoB() {
        val baseBpmA = bpmA.value.toFloat()
        val baseBpmB = bpmB.value.toFloat()
        if (baseBpmA > 0f && baseBpmB > 0f) {
            val targetSpeed = (baseBpmB * speedB.value) / baseBpmA
            setSpeedA(targetSpeed.coerceIn(0.5f, 2.0f))
        } else if (speedB.value > 0) {
            setSpeedA(speedB.value)
        }
    }

    // --- HOT CUES ---
    fun setHotCueA(index: Int, pos: Long = playerA.currentPosition) {
        val media = _deckAMedia.value ?: return
        _hotCuesA.value = hotCueManager.setCue(media.id, index, pos)
    }

    fun jumpToHotCueA(index: Int) {
        val cue = _hotCuesA.value.getOrNull(index)
        if (cue != null) {
            seekA(cue)
            if (!playerA.isPlaying && playerA.playbackState != Player.STATE_ENDED) {
                playerA.play()
            }
        } else {
            setHotCueA(index)
        }
    }

    fun clearHotCueA(index: Int) {
        val media = _deckAMedia.value ?: return
        _hotCuesA.value = hotCueManager.clearCue(media.id, index)
    }

    fun clearAllHotCuesA() {
        val media = _deckAMedia.value ?: return
        _hotCuesA.value = hotCueManager.clearAllCues(media.id)
    }

    fun setHotCueB(index: Int, pos: Long = playerB.currentPosition) {
        val media = _deckBMedia.value ?: return
        _hotCuesB.value = hotCueManager.setCue(media.id, index, pos)
    }

    fun jumpToHotCueB(index: Int) {
        val cue = _hotCuesB.value.getOrNull(index)
        if (cue != null) {
            seekB(cue)
            if (!playerB.isPlaying && playerB.playbackState != Player.STATE_ENDED) {
                playerB.play()
            }
        } else {
            setHotCueB(index)
        }
    }

    fun clearHotCueB(index: Int) {
        val media = _deckBMedia.value ?: return
        _hotCuesB.value = hotCueManager.clearCue(media.id, index)
    }

    fun clearAllHotCuesB() {
        val media = _deckBMedia.value ?: return
        _hotCuesB.value = hotCueManager.clearAllCues(media.id)
    }

    // --- BEAT-BASED LOOPS ---
    fun setLoopA(beats: Int) {
        if (_loopBeatsA.value == beats) {
            exitLoopA()
            return
        }
        val bpm = if (bpmA.value > 0) bpmA.value else 120
        val beatMs = (60000.0 / bpm).toLong()
        val loopLengthMs = (beatMs * beats).coerceAtLeast(100L)
        val curPos = playerA.currentPosition
        val dur = playerA.duration.coerceAtLeast(0L)

        val start = if (dur > 0 && curPos + loopLengthMs > dur) {
            (dur - loopLengthMs).coerceAtLeast(0L)
        } else {
            curPos
        }
        val end = if (dur > 0) (start + loopLengthMs).coerceAtMost(dur) else (start + loopLengthMs)

        _activeLoopA.value = Pair(start, end)
        _loopBeatsA.value = beats
    }

    fun exitLoopA() {
        _activeLoopA.value = null
        _loopBeatsA.value = null
    }

    fun setLoopB(beats: Int) {
        if (_loopBeatsB.value == beats) {
            exitLoopB()
            return
        }
        val bpm = if (bpmB.value > 0) bpmB.value else 120
        val beatMs = (60000.0 / bpm).toLong()
        val loopLengthMs = (beatMs * beats).coerceAtLeast(100L)
        val curPos = playerB.currentPosition
        val dur = playerB.duration.coerceAtLeast(0L)

        val start = if (dur > 0 && curPos + loopLengthMs > dur) {
            (dur - loopLengthMs).coerceAtLeast(0L)
        } else {
            curPos
        }
        val end = if (dur > 0) (start + loopLengthMs).coerceAtMost(dur) else (start + loopLengthMs)

        _activeLoopB.value = Pair(start, end)
        _loopBeatsB.value = beats
    }

    fun exitLoopB() {
        _activeLoopB.value = null
        _loopBeatsB.value = null
    }

    // --- CROSSFADE & VOLUMES ---
    fun setCrossfade(v: Float) {
        _crossfade.value = v.coerceIn(0.0f, 1.0f)
    }

    fun setVolumeA(v: Float) {
        _volumeA.value = v.coerceIn(0.0f, 1.0f)
    }

    fun setVolumeB(v: Float) {
        _volumeB.value = v.coerceIn(0.0f, 1.0f)
    }

    fun setMasterVolume(v: Float) {
        _masterVolume.value = v.coerceIn(0.0f, 1.0f)
    }

    // --- EQ & FILTER CONTROLS ---
    fun setEqLowA(v: Float) {
        _eqLowA.value = v
        djProcessorA.lowGain = v
    }

    fun setEqMidA(v: Float) {
        _eqMidA.value = v
        djProcessorA.midGain = v
    }

    fun setEqHighA(v: Float) {
        _eqHighA.value = v
        djProcessorA.highGain = v
    }

    fun setFilterA(v: Float) {
        val clamped = v.coerceIn(0.0f, 1.0f)
        _filterA.value = clamped
        djProcessorA.filterValue = clamped
    }

    fun setEqLowB(v: Float) {
        _eqLowB.value = v
        djProcessorB.lowGain = v
    }

    fun setEqMidB(v: Float) {
        _eqMidB.value = v
        djProcessorB.midGain = v
    }

    fun setEqHighB(v: Float) {
        _eqHighB.value = v
        djProcessorB.highGain = v
    }

    fun setFilterB(v: Float) {
        val clamped = v.coerceIn(0.0f, 1.0f)
        _filterB.value = clamped
        djProcessorB.filterValue = clamped
    }

    fun resetEq() {
        setEqLowA(1.0f)
        setEqMidA(1.0f)
        setEqHighA(1.0f)
        setFilterA(0.5f)
        setEqLowB(1.0f)
        setEqMidB(1.0f)
        setEqHighB(1.0f)
        setFilterB(0.5f)
    }

    // --- REAL DSP EFFECTS ---
    fun toggleEffect(effect: String) {
        val current = _activeEffects.value.toMutableSet()
        if (current.contains(effect)) current.remove(effect) else current.add(effect)
        _activeEffects.value = current

        applyEffectsToProcessors(current)
    }

    private fun applyEffectsToProcessors(effects: Set<String>) {
        val echo = effects.contains("Echo")
        val reverb = effects.contains("Reverb")
        val flanger = effects.contains("Flanger")
        val bitCrush = effects.contains("Bit Crusher")
        val roll = effects.contains("Roll")
        val bassBoost = effects.contains("Bass Boost")

        djProcessorA.echoEnabled = echo
        djProcessorB.echoEnabled = echo
        djProcessorA.reverbEnabled = reverb
        djProcessorB.reverbEnabled = reverb
        djProcessorA.flangerEnabled = flanger
        djProcessorB.flangerEnabled = flanger
        djProcessorA.bitCrusherEnabled = bitCrush
        djProcessorB.bitCrusherEnabled = bitCrush
        djProcessorA.rollEnabled = roll
        djProcessorB.rollEnabled = roll
        djProcessorA.bassBoostEnabled = bassBoost
        djProcessorB.bassBoostEnabled = bassBoost

        _echoEnabledA.value = echo
        _echoEnabledB.value = echo
        _reverbEnabledA.value = reverb
        _reverbEnabledB.value = reverb
    }

    fun toggleEchoA() {
        _echoEnabledA.value = !_echoEnabledA.value
        djProcessorA.echoEnabled = _echoEnabledA.value
    }

    fun toggleReverbA() {
        _reverbEnabledA.value = !_reverbEnabledA.value
        djProcessorA.reverbEnabled = _reverbEnabledA.value
    }

    fun toggleEchoB() {
        _echoEnabledB.value = !_echoEnabledB.value
        djProcessorB.echoEnabled = _echoEnabledB.value
    }

    fun toggleReverbB() {
        _reverbEnabledB.value = !_reverbEnabledB.value
        djProcessorB.reverbEnabled = _reverbEnabledB.value
    }

    // --- REAL INTELLIGENT AUTO DJ ENGINE ---
    fun toggleAutoDj() {
        _isAutoDjEnabled.value = !_isAutoDjEnabled.value
        if (_isAutoDjEnabled.value) {
            startAutoDjEngine()
        } else {
            autoDjJob?.cancel()
            autoDjJob = null
            _autoDjTelemetry.value = AutoDjTelemetry(isRunning = false, phase = "IDLE")
        }
    }

    private fun startAutoDjEngine() {
        autoDjJob?.cancel()
        autoDjJob = viewModelScope.launch {
            // Get available local audio tracks
            val allAudio = repository.allAudio.firstOrNull() ?: emptyList()
            if (allAudio.isEmpty()) {
                Toast.makeText(context, "No local music found for Auto DJ", Toast.LENGTH_SHORT).show()
                _isAutoDjEnabled.value = false
                _autoDjTelemetry.value = AutoDjTelemetry(isRunning = false, phase = "NO AUDIO FOUND")
                return@launch
            }

            _autoDjTelemetry.value = AutoDjTelemetry(isRunning = true, phase = "ANALYZING POOL")

            // Ensure Deck A has a starting track if none loaded
            if (_deckAMedia.value == null) {
                val initialSelection = selectIntelligentAutoDjTrack(allAudio, null, null)
                val trackA = initialSelection?.first ?: allAudio.first()
                loadToDeckA(trackA)
                recentlyPlayedTrackIds.add(trackA.id)
                delay(400)
            }

            // Determine starting active deck
            var activeDeck = if (playerB.isPlaying && !playerA.isPlaying) "B" else "A"
            if (!playerA.isPlaying && !playerB.isPlaying) {
                _crossfade.value = 0.0f
                playerA.seekTo(0)
                playerA.play()
                activeDeck = "A"
            }

            val transitionDurationMs = 8000L // 8-second professional crossfade transition

            while (_isAutoDjEnabled.value) {
                val currentMedia = if (activeDeck == "A") _deckAMedia.value else _deckBMedia.value
                val currentAnalysis = if (activeDeck == "A") _trackAnalysisA.value else _trackAnalysisB.value
                val currentPlayer = if (activeDeck == "A") playerA else playerB
                val standbyPlayer = if (activeDeck == "A") playerB else playerA
                val standbyDeck = if (activeDeck == "A") "B" else "A"

                // Check if standby deck needs next track queued
                val standbyMedia = if (standbyDeck == "A") _deckAMedia.value else _deckBMedia.value
                if (standbyMedia == null || standbyMedia.id == currentMedia?.id) {
                    _autoDjTelemetry.value = _autoDjTelemetry.value.copy(
                        isRunning = true,
                        phase = "MATCHING NEXT TRACK",
                        activeDeck = activeDeck
                    )
                    val nextSelection = selectIntelligentAutoDjTrack(allAudio, currentMedia, currentAnalysis)
                    if (nextSelection != null) {
                        val (nextTrack, score) = nextSelection
                        if (standbyDeck == "A") {
                            loadToDeckA(nextTrack)
                        } else {
                            loadToDeckB(nextTrack)
                        }
                        standbyPlayer.pause()
                        standbyPlayer.seekTo(0)

                        delay(350)
                        val nextAnalysis = if (standbyDeck == "A") _trackAnalysisA.value else _trackAnalysisB.value
                        _autoDjTelemetry.value = _autoDjTelemetry.value.copy(
                            isRunning = true,
                            phase = "READY (NEXT QUEUED)",
                            activeDeck = activeDeck,
                            nextTrack = nextTrack,
                            nextTrackAnalysis = nextAnalysis,
                            currentTrackAnalysis = currentAnalysis,
                            transitionScore = score
                        )
                    }
                }

                // Real Beat Synchronization: align tempo / playback speed between active and standby decks
                val activeBpm = if (activeDeck == "A") bpmA.value else bpmB.value
                val standbyBpm = if (standbyDeck == "A") bpmA.value else bpmB.value
                var isSynced = false
                if (activeBpm > 0 && standbyBpm > 0) {
                    val speedRatio = (activeBpm.toFloat() / standbyBpm.toFloat()).coerceIn(0.85f, 1.15f)
                    standbyPlayer.setPlaybackSpeed(speedRatio)
                    if (standbyDeck == "A") speedA.value = speedRatio else speedB.value = speedRatio
                    isSynced = true
                }

                val dur = currentPlayer.duration
                val pos = currentPlayer.currentPosition
                val remainingMs = if (dur > 0) (dur - pos).coerceAtLeast(0L) else 60000L
                val secondsUntil = (remainingMs / 1000).toInt()

                _autoDjTelemetry.value = _autoDjTelemetry.value.copy(
                    isRunning = true,
                    phase = if (isSynced) "PLAYING DECK $activeDeck • BEAT SYNCED" else "PLAYING DECK $activeDeck",
                    activeDeck = activeDeck,
                    currentTrackAnalysis = currentAnalysis,
                    secondsUntilTransition = secondsUntil,
                    isBeatSynced = isSynced
                )

                // Trigger transition when approaching track end or when "Mix Now" is forced
                val shouldTransition = (dur > 0 && remainingMs <= transitionDurationMs) || forceImmediateTransition
                if (shouldTransition) {
                    forceImmediateTransition = false
                    _autoDjTelemetry.value = _autoDjTelemetry.value.copy(
                        phase = "CROSSFADING TO DECK $standbyDeck (EQUAL POWER)"
                    )

                    // Start standby deck from the beginning
                    standbyPlayer.seekTo(0)
                    standbyPlayer.play()

                    val actualDuration = if (remainingMs < transitionDurationMs && remainingMs > 2000L) remainingMs else transitionDurationMs
                    val steps = 60
                    val stepDelayMs = (actualDuration / steps).coerceAtLeast(20L)

                    val startCf = if (activeDeck == "A") 0.0f else 1.0f
                    val targetCf = if (activeDeck == "A") 1.0f else 0.0f

                    for (step in 1..steps) {
                        if (!_isAutoDjEnabled.value) break
                        val progress = step.toFloat() / steps.toFloat()
                        val cfValue = startCf + (targetCf - startCf) * progress
                        _crossfade.value = cfValue

                        // Dynamic EQ Bass Swap
                        if (activeDeck == "A") {
                            // Fading A -> B
                            if (progress < 0.45f) {
                                setEqLowB(0.6f) // Attenuate incoming bass
                                setEqLowA(1.0f)
                            } else if (progress in 0.45f..0.55f) {
                                setEqLowA(0.4f) // Bass swap at midpoint
                                setEqLowB(1.0f)
                            } else {
                                setEqLowA(0.3f)
                                setEqLowB(1.0f)
                            }
                        } else {
                            // Fading B -> A
                            if (progress < 0.45f) {
                                setEqLowA(0.6f)
                                setEqLowB(1.0f)
                            } else if (progress in 0.45f..0.55f) {
                                setEqLowB(0.4f)
                                setEqLowA(1.0f)
                            } else {
                                setEqLowB(0.3f)
                                setEqLowA(1.0f)
                            }
                        }

                        delay(stepDelayMs)
                    }

                    if (_isAutoDjEnabled.value) {
                        // Complete crossfade
                        currentPlayer.pause()
                        _crossfade.value = targetCf
                        resetEq()

                        if (currentMedia != null) {
                            recentlyPlayedTrackIds.add(currentMedia.id)
                        }

                        // Swap active deck
                        activeDeck = standbyDeck

                        // Immediately select and queue the next intelligent track on the new standby deck
                        val newCurrentMedia = if (activeDeck == "A") _deckAMedia.value else _deckBMedia.value
                        val newCurrentAnalysis = if (activeDeck == "A") _trackAnalysisA.value else _trackAnalysisB.value
                        val newStandbyDeck = if (activeDeck == "A") "B" else "A"

                        val nextSelection = selectIntelligentAutoDjTrack(allAudio, newCurrentMedia, newCurrentAnalysis)
                        if (nextSelection != null) {
                            val (nextTrack, score) = nextSelection
                            if (newStandbyDeck == "A") {
                                loadToDeckA(nextTrack)
                                playerA.pause()
                                playerA.seekTo(0)
                            } else {
                                loadToDeckB(nextTrack)
                                playerB.pause()
                                playerB.seekTo(0)
                            }
                            _autoDjTelemetry.value = _autoDjTelemetry.value.copy(
                                phase = "PLAYING DECK $activeDeck",
                                activeDeck = activeDeck,
                                nextTrack = nextTrack,
                                currentTrackAnalysis = newCurrentAnalysis,
                                transitionScore = score
                            )
                        }
                    }
                }

                delay(300)
            }
        }
    }

    private suspend fun selectIntelligentAutoDjTrack(
        pool: List<MediaItemEntity>,
        currentMedia: MediaItemEntity?,
        currentAnalysis: TrackAnalysisResult?
    ): Pair<MediaItemEntity, TransitionScore?>? {
        if (pool.isEmpty()) return null

        // Avoid repeating recently played tracks (keep history of up to 75% of pool or 20 tracks)
        val maxHistory = (pool.size * 0.75).toInt().coerceAtLeast(1).coerceAtMost(20)
        while (recentlyPlayedTrackIds.size > maxHistory) {
            recentlyPlayedTrackIds.removeAt(0)
        }

        val candidates = pool.filter { candidate ->
            candidate.id != currentMedia?.id &&
            candidate.id !in recentlyPlayedTrackIds &&
            candidate.id !in skippedTrackIds
        }

        val finalCandidates = if (candidates.isNotEmpty()) {
            candidates
        } else {
            skippedTrackIds.clear()
            pool.filter { it.id != currentMedia?.id }
        }

        if (finalCandidates.isEmpty()) return null

        var bestCandidate: MediaItemEntity? = null
        var bestScore: TransitionScore? = null

        for (candidate in finalCandidates) {
            val analysis = try {
                AudioTrackAnalyzer.analyzeTrack(context, candidate, repository)
            } catch (e: Exception) {
                null
            }

            val score = AudioTrackAnalyzer.calculateTransitionScore(
                current = currentAnalysis,
                candidate = analysis,
                mode = _autoDjMode.value
            )

            if (bestScore == null || score.totalScore > bestScore.totalScore) {
                bestScore = score
                bestCandidate = candidate
            }
        }

        return if (bestCandidate != null) Pair(bestCandidate, bestScore) else null
    }

    fun triggerImmediateTransition() {
        if (_isAutoDjEnabled.value) {
            forceImmediateTransition = true
        }
    }

    fun setAutoDjMode(mode: AutoDjMixMode) {
        _autoDjMode.value = mode
        val currentTelemetry = _autoDjTelemetry.value
        if (currentTelemetry.nextTrack != null) {
            val newScore = AudioTrackAnalyzer.calculateTransitionScore(
                currentTelemetry.currentTrackAnalysis,
                currentTelemetry.nextTrackAnalysis,
                mode
            )
            _autoDjTelemetry.value = currentTelemetry.copy(transitionScore = newScore)
        }
    }

    fun skipNextTrack() {
        viewModelScope.launch {
            val currentTelemetry = _autoDjTelemetry.value
            val nextTrack = currentTelemetry.nextTrack ?: return@launch
            skippedTrackIds.add(nextTrack.id)

            val allAudio = repository.allAudio.firstOrNull() ?: emptyList()
            val currentMedia = if (currentTelemetry.activeDeck == "A") _deckAMedia.value else _deckBMedia.value
            val currentAnalysis = if (currentTelemetry.activeDeck == "A") _trackAnalysisA.value else _trackAnalysisB.value
            val standbyDeck = if (currentTelemetry.activeDeck == "A") "B" else "A"

            val nextSelection = selectIntelligentAutoDjTrack(allAudio, currentMedia, currentAnalysis)
            if (nextSelection != null) {
                val (newTrack, score) = nextSelection
                if (standbyDeck == "A") {
                    loadToDeckA(newTrack)
                    playerA.pause()
                    playerA.seekTo(0)
                } else {
                    loadToDeckB(newTrack)
                    playerB.pause()
                    playerB.seekTo(0)
                }
                _autoDjTelemetry.value = currentTelemetry.copy(
                    nextTrack = newTrack,
                    transitionScore = score
                )
                Toast.makeText(context, "Queued next: ${newTrack.title}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- EMERGENCY STOP ---
    fun emergencyStop(stopRecording: Boolean = false) {
        playerA.pause()
        playerB.pause()

        _isAutoDjEnabled.value = false
        autoDjJob?.cancel()
        autoDjJob = null
        _autoDjTelemetry.value = AutoDjTelemetry(isRunning = false, phase = "IDLE")

        sampleManager.stopAll()

        _activeEffects.value = emptySet()
        applyEffectsToProcessors(emptySet())

        djProcessorA.flush()
        djProcessorB.flush()

        _activeLoopA.value = null
        _loopBeatsA.value = null
        _activeLoopB.value = null
        _loopBeatsB.value = null

        resetEq()

        if (stopRecording && _isRecording.value) {
            stopRecording()
        }

        Toast.makeText(context, "EMERGENCY STOP: All audio stopped", Toast.LENGTH_SHORT).show()
    }

    fun stopAllMusicImmediately() {
        emergencyStop(stopRecording = false)
    }

    // --- HONEST WAV RECORDING ---
    fun toggleRecording() {
        if (_isRecording.value) stopRecording() else startRecording()
    }

    private fun startRecording() {
        val dir = File(context.getExternalFilesDir(null), "Recordings")
        if (!dir.exists()) dir.mkdirs()
        mixRecorder = MixRecorder(File(dir, "Mix_${System.currentTimeMillis()}.wav")).apply { start() }
        _recordingDurationSeconds.value = 0L
        _isRecording.value = true
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _recordingDurationSeconds.value += 1
            }
        }
        Toast.makeText(context, "Recording started (WAV)", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        _isRecording.value = false

        synchronized(this) {
            if (recordingBufferA.isNotEmpty()) flushLoneBuffer(recordingBufferA, _gainA.value)
            if (recordingBufferB.isNotEmpty()) flushLoneBuffer(recordingBufferB, _gainB.value)
        }
        mixRecorder?.stop()
        val savedFile = mixRecorder?.getOutputFile()
        mixRecorder = null

        if (savedFile != null) {
            Toast.makeText(context, "Mix saved: ${savedFile.name} (WAV PCM)", Toast.LENGTH_LONG).show()
        }
    }

    private fun processRecordingBuffer(buffer: ByteBuffer, isA: Boolean) {
        val shortBuffer = buffer.asShortBuffer()
        val samples = ShortArray(shortBuffer.remaining())
        shortBuffer.get(samples)
        synchronized(this) {
            if (isA) samples.forEach { recordingBufferA.add(it) } else samples.forEach { recordingBufferB.add(it) }
            val mixCount = minOf(recordingBufferA.size, recordingBufferB.size)
            if (mixCount > 0) {
                val mixedBuffer = ByteBuffer.allocate(mixCount * 2).order(ByteOrder.nativeOrder())
                val mixedShorts = mixedBuffer.asShortBuffer()
                val gA = _gainA.value
                val gB = _gainB.value
                for (i in 0 until mixCount) {
                    val sA = recordingBufferA.removeFirst()
                    val sB = recordingBufferB.removeFirst()
                    val mixed = (sA * gA + sB * gB).toInt().coerceIn(-32768, 32767).toShort()
                    mixedShorts.put(mixed)
                }
                mixedBuffer.position(0)
                mixRecorder?.write(mixedBuffer)
            }
            checkAndFlushLoneRecording()
        }
    }

    private fun checkAndFlushLoneRecording() {
        val limit = 4096
        if (recordingBufferA.size > limit && recordingBufferB.isEmpty() && !playerB.isPlaying) {
            flushLoneBuffer(recordingBufferA, _gainA.value)
        } else if (recordingBufferB.size > limit && recordingBufferA.isEmpty() && !playerA.isPlaying) {
            flushLoneBuffer(recordingBufferB, _gainB.value)
        }
    }

    private fun flushLoneBuffer(buffer: ArrayDeque<Short>, gain: Float) {
        val count = buffer.size
        val mixedBuffer = ByteBuffer.allocate(count * 2).order(ByteOrder.nativeOrder())
        val mixedShorts = mixedBuffer.asShortBuffer()
        for (i in 0 until count) {
            mixedShorts.put((buffer.removeFirst() * gain).toInt().coerceIn(-32768, 32767).toShort())
        }
        mixedBuffer.position(0)
        mixRecorder?.write(mixedBuffer)
    }

    fun goToRecordings() {
        val dir = File(context.getExternalFilesDir(null), "Recordings")
        if (!dir.exists()) dir.mkdirs()
        browseTo(dir)
    }

    // --- BPM DETECTION & TAPPING ---
    fun tapBpmA() {
        val now = System.currentTimeMillis()
        tapTimesA.add(now)
        if (tapTimesA.size > 8) tapTimesA.removeAt(0)
        if (tapTimesA.size >= 2) {
            val intervals = (1 until tapTimesA.size).map { tapTimesA[it] - tapTimesA[it - 1] }
            val avg = intervals.average()
            if (avg > 0) djProcessorA.setBpm((60000.0 / avg).toInt())
        }
    }

    fun tapBpmB() {
        val now = System.currentTimeMillis()
        tapTimesB.add(now)
        if (tapTimesB.size > 8) tapTimesB.removeAt(0)
        if (tapTimesB.size >= 2) {
            val intervals = (1 until tapTimesB.size).map { tapTimesB[it] - tapTimesB[it - 1] }
            val avg = intervals.average()
            if (avg > 0) djProcessorB.setBpm((60000.0 / avg).toInt())
        }
    }

    fun resetBpmA() {
        tapTimesA.clear()
        djProcessorA.flush()
        autoDetectBpmA()
    }

    fun resetBpmB() {
        tapTimesB.clear()
        djProcessorB.flush()
        autoDetectBpmB()
    }

    fun autoDetectBpmA() {
        val media = _deckAMedia.value ?: return
        _isDetectingBpmA.value = true
        viewModelScope.launch {
            try {
                val analysis = AudioTrackAnalyzer.analyzeTrack(context, media, repository, forceReanalyze = true)
                _trackAnalysisA.value = analysis
                if (analysis.bpm != null && analysis.bpm > 0) {
                    djProcessorA.setBpm(analysis.bpm)
                    _deckAMedia.value = media.copy(bpm = analysis.bpm)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isDetectingBpmA.value = false
            }
        }
    }

    fun autoDetectBpmB() {
        val media = _deckBMedia.value ?: return
        _isDetectingBpmB.value = true
        viewModelScope.launch {
            try {
                val analysis = AudioTrackAnalyzer.analyzeTrack(context, media, repository, forceReanalyze = true)
                _trackAnalysisB.value = analysis
                if (analysis.bpm != null && analysis.bpm > 0) {
                    djProcessorB.setBpm(analysis.bpm)
                    _deckBMedia.value = media.copy(bpm = analysis.bpm)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isDetectingBpmB.value = false
            }
        }
    }

    // --- HEADPHONE CUE ---
    fun toggleHeadphoneCueA() {
        _headphoneCueA.value = !_headphoneCueA.value
    }

    fun toggleHeadphoneCueB() {
        _headphoneCueB.value = !_headphoneCueB.value
    }

    // --- FILE BROWSER ---
    fun getStorageRoots(): List<File> {
        val roots = mutableListOf<File>()
        roots.add(android.os.Environment.getExternalStorageDirectory())
        val externalDirs = context.getExternalFilesDirs(null)
        if (externalDirs.size > 1) {
            for (i in 1 until externalDirs.size) {
                val file = externalDirs[i]
                if (file != null) {
                    val path = file.absolutePath
                    val storageIndex = path.indexOf("/Android/data")
                    if (storageIndex != -1) {
                        roots.add(File(path.substring(0, storageIndex)))
                    }
                }
            }
        }
        val storageRoot = File("/storage")
        if (storageRoot.exists() && storageRoot.isDirectory) {
            storageRoot.listFiles()?.forEach {
                if (it.isDirectory && it.canRead() && !roots.contains(it) && it.name != "self" && it.name != "emulated") {
                    roots.add(it)
                }
            }
        }
        return roots.distinctBy { it.absolutePath }
    }

    fun browseTo(path: File?) {
        _currentPath.value = path
        if (path == null) {
            _browserItems.value = getStorageRoots()
        } else {
            val files = path.listFiles()?.toList() ?: emptyList()
            _browserItems.value = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }
    }

    fun selectFile(file: File, deck: String) {
        val media = MediaItemEntity(
            id = "file-${file.absolutePath}",
            title = file.name,
            artist = "Local File",
            uri = Uri.fromFile(file).toString(),
            artUri = null,
            duration = 0,
            isVideo = file.extension.lowercase() in listOf("mp4", "mkv", "webm", "avi", "mov"),
            category = "Files"
        )
        if (deck == "A") loadToDeckA(media) else loadToDeckB(media)
    }

    // --- SLEEP TIMER ---
    fun setSleepTimer(durationMs: Long?, stopRecording: Boolean = false) {
        sleepTimerJob?.cancel()
        if (durationMs == null || durationMs <= 0) {
            _sleepTimerRemainingMs.value = null
            return
        }

        _sleepTimerRemainingMs.value = durationMs
        sleepTimerJob = viewModelScope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                _sleepTimerRemainingMs.value = remaining
            }
            _sleepTimerRemainingMs.value = null

            emergencyStop(stopRecording = stopRecording)
        }
    }

    // Keyboard Shortcuts Handler
    fun handleKeyEvent(keyCode: Int): Boolean {
        try {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_SPACE -> { togglePlayA(); return true }
                android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> { togglePlayB(); return true }
                android.view.KeyEvent.KEYCODE_A -> { cueA(); return true }
                android.view.KeyEvent.KEYCODE_S -> { cueB(); return true }
                android.view.KeyEvent.KEYCODE_Q -> { seekA((playerA.currentPosition - 5000).coerceAtLeast(0)); return true }
                android.view.KeyEvent.KEYCODE_W -> { seekA((playerA.currentPosition + 5000).coerceAtMost(playerA.duration)); return true }
                android.view.KeyEvent.KEYCODE_O -> { seekB((playerB.currentPosition - 5000).coerceAtLeast(0)); return true }
                android.view.KeyEvent.KEYCODE_P -> { seekB((playerB.currentPosition + 5000).coerceAtMost(playerB.duration)); return true }
                android.view.KeyEvent.KEYCODE_Z -> { setCrossfade((_crossfade.value - 0.05f).coerceIn(0.0f, 1.0f)); return true }
                android.view.KeyEvent.KEYCODE_X -> { setCrossfade(0.5f); return true }
                android.view.KeyEvent.KEYCODE_C -> { setCrossfade((_crossfade.value + 0.05f).coerceIn(0.0f, 1.0f)); return true }
                android.view.KeyEvent.KEYCODE_D -> { setSpeedA((speedA.value - 0.05f).coerceIn(0.5f, 2.0f)); return true }
                android.view.KeyEvent.KEYCODE_F -> { setSpeedA((speedA.value + 0.05f).coerceIn(0.5f, 2.0f)); return true }
                android.view.KeyEvent.KEYCODE_K -> { setSpeedB((speedB.value - 0.05f).coerceIn(0.5f, 2.0f)); return true }
                android.view.KeyEvent.KEYCODE_L -> { setSpeedB((speedB.value + 0.05f).coerceIn(0.5f, 2.0f)); return true }
                android.view.KeyEvent.KEYCODE_1 -> { jumpToHotCueA(0); return true }
                android.view.KeyEvent.KEYCODE_2 -> { jumpToHotCueA(1); return true }
                android.view.KeyEvent.KEYCODE_3 -> { jumpToHotCueA(2); return true }
                android.view.KeyEvent.KEYCODE_4 -> { jumpToHotCueA(3); return true }
                android.view.KeyEvent.KEYCODE_7 -> { jumpToHotCueB(0); return true }
                android.view.KeyEvent.KEYCODE_8 -> { jumpToHotCueB(1); return true }
                android.view.KeyEvent.KEYCODE_9 -> { jumpToHotCueB(2); return true }
                android.view.KeyEvent.KEYCODE_0 -> { jumpToHotCueB(3); return true }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun applyPreset(presetName: String) {
        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        when (presetName) {
            "🎉 Party" -> {
                setEqLowA(0.8f)
                setEqMidA(0.6f)
                setEqHighA(0.7f)
                setEqLowB(0.8f)
                setEqMidB(0.6f)
                setEqHighB(0.7f)
                prefs.edit().putFloat("crossfade_duration", 6f).apply()
            }
            "💪 Workout" -> {
                setEqLowA(0.9f)
                setEqMidA(0.5f)
                setEqHighA(0.6f)
                setEqLowB(0.9f)
                setEqMidB(0.5f)
                setEqHighB(0.6f)
                prefs.edit().putFloat("crossfade_duration", 4f).apply()
            }
            "💍 Wedding" -> {
                setEqLowA(0.5f)
                setEqMidA(0.5f)
                setEqHighA(0.5f)
                setEqLowB(0.5f)
                setEqMidB(0.5f)
                setEqHighB(0.5f)
                prefs.edit().putFloat("crossfade_duration", 10f).apply()
            }
            "🎧 Club Night" -> {
                setEqLowA(1.0f)
                setEqMidA(0.4f)
                setEqHighA(0.6f)
                setEqLowB(1.0f)
                setEqMidB(0.4f)
                setEqHighB(0.6f)
                prefs.edit().putFloat("crossfade_duration", 12f).apply()
            }
            "📼 Throwback" -> {
                setEqLowA(0.4f)
                setEqMidA(0.8f)
                setEqHighA(0.3f)
                setEqLowB(0.4f)
                setEqMidB(0.8f)
                setEqHighB(0.3f)
                prefs.edit().putFloat("crossfade_duration", 8f).apply()
            }
            "🙏 Gospel" -> {
                setEqLowA(0.3f)
                setEqMidA(0.9f)
                setEqHighA(0.8f)
                setEqLowB(0.3f)
                setEqMidB(0.9f)
                setEqHighB(0.8f)
                prefs.edit().putFloat("crossfade_duration", 5f).apply()
            }
            "😌 Chill" -> {
                setEqLowA(0.6f)
                setEqMidA(0.4f)
                setEqHighA(0.3f)
                setEqLowB(0.6f)
                setEqMidB(0.4f)
                setEqHighB(0.3f)
                prefs.edit().putFloat("crossfade_duration", 14f).apply()
            }
        }
    }

    // Lifecycle clean up
    override fun onCleared() {
        super.onCleared()
        autoDjJob?.cancel()
        recordingTimerJob?.cancel()
        sleepTimerJob?.cancel()
        mixRecorder?.stop()
        mixRecorder = null
        sampleManager.release()
        djProcessorA.reset()
        djProcessorB.reset()
        synchronized(this) {
            recordingBufferA.clear()
            recordingBufferB.clear()
        }
        playerA.release()
        playerB.release()
        mediaSession.release()
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
