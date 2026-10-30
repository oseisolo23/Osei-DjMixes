package com.osei.djmixer

import com.osei.djmixer.ui.theme.*
import android.app.Application
import androidx.room.Room
import com.osei.djmixer.data.DJMixerDatabase
import com.osei.djmixer.playback.MediaStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class DJMixerApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun attachBaseContext(base: android.content.Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            super.attachBaseContext(base.createAttributionContext("default_tag"))
        } else {
            super.attachBaseContext(base)
        }
    }

    
    lateinit var database: DJMixerDatabase
        private set
    lateinit var mediaStateHolder: MediaStateHolder
        private set

    override fun onCreate() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                val file = java.io.File(getExternalFilesDir(null), "crash.log")
                java.io.FileOutputStream(file).use { 
                     it.write(android.util.Log.getStackTraceString(exception).toByteArray())
                }
            } catch (e: Exception) {}
            defaultHandler?.uncaughtException(thread, exception)
        }
        super.onCreate()
        
        val cookieManager = java.net.CookieManager()
        cookieManager.setCookiePolicy(java.net.CookiePolicy.ACCEPT_ALL)
        java.net.CookieHandler.setDefault(cookieManager)

        database = Room.databaseBuilder(
            applicationContext,
            DJMixerDatabase::class.java,
            "dj_mixer_db"
        ).addMigrations(DJMixerDatabase.MIGRATION_11_12, DJMixerDatabase.MIGRATION_12_13)
         .build()
        
        com.osei.djmixer.playback.AudioEqualizerManager.instance.init(this)
        mediaStateHolder = MediaStateHolder(this)

        // Trigger background sync for offline prayers
        appScope.launch {
            com.osei.djmixer.playback.OfflinePrayerCacheWorker.syncPrayers(this@DJMixerApplication)
        }
    }
}
