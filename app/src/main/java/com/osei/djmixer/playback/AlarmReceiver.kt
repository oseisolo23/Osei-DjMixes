package com.osei.djmixer.playback

import com.osei.djmixer.ui.theme.*

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Toast.makeText(context, "Alarm triggered! Waking up...", Toast.LENGTH_LONG).show()
        // Start the service or an activity to play the media
        val mediaUri = intent.getStringExtra("MEDIA_URI")
        val serviceIntent = Intent(context, DJMixerMediaService::class.java).apply {
            action = "ACTION_ALARM_PLAY"
            putExtra("MEDIA_URI", mediaUri)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
