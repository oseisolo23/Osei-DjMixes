package com.osei.djmixer.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@UnstableApi
object OfflinePrayerCacheWorker {
    suspend fun syncPrayers(context: Context) = withContext(Dispatchers.IO) {
        try {
            Log.d("OfflinePrayerCacheWorker", "Starting offline cache for prayers...")
            val app = context.applicationContext as com.osei.djmixer.DJMixerApplication
            val db = app.database

            val recentPrayers = db.mediaDao().getRecentPrayersList()
            if (recentPrayers.isEmpty()) {
                Log.d("OfflinePrayerCacheWorker", "No frequently listened prayers found.")
                return@withContext
            }

            val dataSourceFactory = MediaCacheManager.getDirectCacheDataSourceFactory(context)

            for (media in recentPrayers) {
                if (media.uri.startsWith("http")) {
                    Log.d("OfflinePrayerCacheWorker", "Caching prayer: ${media.title}")
                    try {
                        val dataSpec = DataSpec(Uri.parse(media.uri))
                        val cacheWriter = CacheWriter(
                            dataSourceFactory.createDataSource(),
                            dataSpec,
                            null,
                            null
                        )
                        cacheWriter.cache()
                        Log.d("OfflinePrayerCacheWorker", "Successfully cached: ${media.title}")
                    } catch (e: Exception) {
                        Log.e("OfflinePrayerCacheWorker", "Failed to cache: ${media.title}", e)
                    }
                }
            }

            Log.d("OfflinePrayerCacheWorker", "Finished offline caching.")
        } catch (e: Exception) {
            Log.e("OfflinePrayerCacheWorker", "Error in cache worker", e)
        }
    }
}
