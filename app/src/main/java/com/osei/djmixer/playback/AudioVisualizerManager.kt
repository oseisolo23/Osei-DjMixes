package com.osei.djmixer.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioVisualizerManager private constructor() {
    private val _amplitudes = MutableStateFlow<List<Float>>(emptyList())
    val amplitudes = _amplitudes.asStateFlow()

    companion object {
        val instance = AudioVisualizerManager()
    }

    fun initVisualizer(audioSessionId: Int) {
        // Visualizer usage removed to avoid AppOps RECORD_AUDIO/attributionTag errors
    }

    fun setPlaying(isPlaying: Boolean) {
        // UI handles simulated fallback
    }

    fun release() {
        _amplitudes.value = emptyList()
    }
}
