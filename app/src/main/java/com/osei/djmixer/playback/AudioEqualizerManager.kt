package com.osei.djmixer.playback

import com.osei.djmixer.ui.theme.*

import android.content.Context
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class AudioEqualizerManager private constructor() {

    private var equalizer: Equalizer? = null
    private var activeAudioSessionId: Int = 0
    private var contextRef: Context? = null

    // 10 frequencies defined for the visual equalizer
    val frequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    // Standard preset mappings
    val presets = mapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Bass Booster" to listOf(8f, 7f, 5f, 3f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Treble Booster" to listOf(0f, 0f, 0f, 0f, 0f, -1f, 2f, 5f, 8f, 9f),
        "Vocal Booster" to listOf(-4f, -3f, -1f, 2f, 5f, 6f, 5f, 3f, 0f, -2f),
        "Osei Pro Mix" to listOf(4f, 5f, 3f, 0f, -2f, 0f, 3f, 5f, 6f, 4f)
    )

    private val _enabled = MutableStateFlow(false)
    val enabled = _enabled.asStateFlow()

    private val _gains = MutableStateFlow(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
    val gains = _gains.asStateFlow()

    private val _selectedPreset = MutableStateFlow("Flat")
    val selectedPreset = _selectedPreset.asStateFlow()

    companion object {
        val instance = AudioEqualizerManager()
    }

    fun init(context: Context) {
        contextRef = context.applicationContext
        loadSettings()
    }

    fun initEqualizer(audioSessionId: Int) {
        if (audioSessionId == 0) return
        activeAudioSessionId = audioSessionId

        try {
            equalizer?.release()
            
            // Create native hardware Equalizer
            val eq = Equalizer(0, audioSessionId)
            eq.enabled = _enabled.value
            equalizer = eq
            
            applyGainsToHardware()
        } catch (e: Exception) {
            e.printStackTrace()
            equalizer = null
        }
    }

    fun setEqualizerEnabled(isEnabled: Boolean) {
        _enabled.value = isEnabled
        try {
            equalizer?.enabled = isEnabled
            if (isEnabled) {
                applyGainsToHardware()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        saveSettings()
    }

    fun setBandGain(index: Int, gainDb: Float) {
        if (index !in frequencies.indices) return
        val currentList = _gains.value.toMutableList()
        currentList[index] = gainDb.coerceIn(-15f, 15f)
        _gains.value = currentList
        _selectedPreset.value = "Custom"
        
        applyGainsToHardware()
        saveSettings()
    }

    fun applyPreset(presetName: String) {
        val presetGains = presets[presetName] ?: return
        _gains.value = presetGains
        _selectedPreset.value = presetName
        
        applyGainsToHardware()
        saveSettings()
    }

    private fun applyGainsToHardware() {
        val eq = equalizer ?: return
        try {
            if (!eq.enabled) return
            val numBands = eq.numberOfBands
            val bandRange = eq.bandLevelRange
            val minLevel = bandRange[0]
            val maxLevel = bandRange[1]

            // Map each hardware band to our closest 10-band user slider settings
            for (b in 0 until numBands) {
                val centerFreqHz = eq.getCenterFreq(b.toShort()) / 1000
                val closestUserBandIdx = findClosestUserBandIndex(centerFreqHz)
                val userGain = _gains.value[closestUserBandIdx]
                val levelMb = (userGain * 100).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt())
                eq.setBandLevel(b.toShort(), levelMb.toShort())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun findClosestUserBandIndex(freqHz: Int): Int {
        var minDiff = Int.MAX_VALUE
        var closestIndex = 0
        for (i in frequencies.indices) {
            val diff = abs(frequencies[i] - freqHz)
            if (diff < minDiff) {
                minDiff = diff
                closestIndex = i
            }
        }
        return closestIndex
    }

    private fun saveSettings() {
        val ctx = contextRef ?: return
        val prefs = ctx.getSharedPreferences("equalizer_settings", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("enabled", _enabled.value)
            putString("preset", _selectedPreset.value)
            putString("gains", _gains.value.joinToString(","))
            apply()
        }
    }

    private fun loadSettings() {
        val ctx = contextRef ?: return
        val prefs = ctx.getSharedPreferences("equalizer_settings", Context.MODE_PRIVATE)
        val savedEnabled = prefs.getBoolean("enabled", false)
        val savedPreset = prefs.getString("preset", "Flat") ?: "Flat"
        val savedGainsStr = prefs.getString("gains", null)

        _enabled.value = savedEnabled
        _selectedPreset.value = savedPreset

        if (savedGainsStr != null) {
            try {
                val list = savedGainsStr.split(",").map { it.toFloat() }
                if (list.size == 10) {
                    _gains.value = list
                } else {
                    _gains.value = presets[savedPreset] ?: presets["Flat"]!!
                }
            } catch (e: Exception) {
                _gains.value = presets[savedPreset] ?: presets["Flat"]!!
            }
        } else {
            _gains.value = presets[savedPreset] ?: presets["Flat"]!!
        }
    }

    fun release() {
        equalizer?.release()
        equalizer = null
    }
}
