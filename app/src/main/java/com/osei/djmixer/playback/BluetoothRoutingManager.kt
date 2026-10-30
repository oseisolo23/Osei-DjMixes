package com.osei.djmixer.playback

import com.osei.djmixer.ui.theme.*

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BluetoothRoutingManager private constructor(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _availableDevices = MutableStateFlow<List<AudioDeviceInfo>>(emptyList())
    val availableDevices = _availableDevices.asStateFlow()

    private val _currentDevice = MutableStateFlow<AudioDeviceInfo?>(null)
    val currentDevice = _currentDevice.asStateFlow()

    private var player: ExoPlayer? = null

    companion object {
        @Volatile
        private var INSTANCE: BluetoothRoutingManager? = null

        fun getInstance(context: Context): BluetoothRoutingManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BluetoothRoutingManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun attachPlayer(exoPlayer: ExoPlayer) {
        this.player = exoPlayer
        refreshDevices()
    }

    fun refreshDevices() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        _availableDevices.value = devices.toList()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            _currentDevice.value = audioManager.communicationDevice
        } else {
            // Find currently active device based on simple checks
            _currentDevice.value = devices.firstOrNull { 
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP && audioManager.isBluetoothA2dpOn ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET && audioManager.isWiredHeadsetOn
            } ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        }
    }

    fun setPreferredDevice(deviceInfo: AudioDeviceInfo?) {
        try {
            if (deviceInfo != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.setCommunicationDevice(deviceInfo)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice()
                }
            }
            
            // For ExoPlayer 1.2+
            player?.setPreferredAudioDevice(deviceInfo)
            
            _currentDevice.value = deviceInfo
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        player?.setPreferredAudioDevice(null)
        player = null
    }
}
