package com.osei.djmixer.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.util.concurrent.TimeUnit
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object MediaCacheManager {
    private fun getUnsafeOkHttpClient(): OkHttpClient {
        return try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectionSpecs(listOf(okhttp3.ConnectionSpec.MODERN_TLS, okhttp3.ConnectionSpec.COMPATIBLE_TLS, okhttp3.ConnectionSpec.CLEARTEXT))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        } catch (e: Exception) {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }

    private var simpleCache: SimpleCache? = null
    private const val CACHE_SIZE_BYTES = 512 * 1024 * 1024L // 512MB max cache

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, "media3_cache")
            val databaseProvider = StandaloneDatabaseProvider(context)
            val evictor = LeastRecentlyUsedCacheEvictor(CACHE_SIZE_BYTES)
            simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return simpleCache!!
    }
    
    fun getDirectCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val cache = getCache(context)
        val okHttpClient = getUnsafeOkHttpClient()
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(mapOf(
                "Accept" to "*/*",
                "Icy-MetaData" to "1"
            ))
            
        val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(defaultDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun getCacheDataSourceFactory(context: Context): androidx.media3.datasource.DataSource.Factory {
        val cacheDataSourceFactory = getDirectCacheDataSourceFactory(context)
        val okHttpClient = getUnsafeOkHttpClient()
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(mapOf(
                "Accept" to "*/*",
                "Icy-MetaData" to "1"
            ))
        val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        
        return androidx.media3.datasource.DataSource.Factory {
            val cacheDataSource = cacheDataSourceFactory.createDataSource()
            val upstreamDataSource = defaultDataSourceFactory.createDataSource()
            
            object : androidx.media3.datasource.DataSource {
                private var activeDataSource: androidx.media3.datasource.DataSource = cacheDataSource

                override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
                    cacheDataSource.addTransferListener(transferListener)
                    upstreamDataSource.addTransferListener(transferListener)
                }

                @Throws(java.io.IOException::class)
                override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
                    var finalDataSpec = dataSpec
                    var uriStr = dataSpec.uri.toString()
                    
                    var referer = ""
                    val refererMatcher = java.util.regex.Pattern.compile("[#?&]referer=([^&#]+)").matcher(uriStr)
                    if (refererMatcher.find()) {
                        referer = refererMatcher.group(1) ?: ""
                        // Remove referer from URI
                        uriStr = uriStr.replace(refererMatcher.group(0) ?: "", "")
                        finalDataSpec = finalDataSpec.withUri(android.net.Uri.parse(uriStr))
                    }
                    
                    if (referer.isNotEmpty()) {
                        val newHeaders = finalDataSpec.httpRequestHeaders.toMutableMap()
                        newHeaders["Referer"] = referer
                        finalDataSpec = finalDataSpec.buildUpon().setHttpRequestHeaders(newHeaders).build()
                    } else if (uriStr.contains("5centscdn.com") || uriStr.contains("ghanaweb.stream") || uriStr.contains("liveghanatv") || uriStr.contains("bozztv.com") || uriStr.contains("asiakwa.com")) {
                        val newHeaders = finalDataSpec.httpRequestHeaders.toMutableMap()
                        newHeaders["Referer"] = "https://liveghanatv.com/"
                        finalDataSpec = finalDataSpec.buildUpon().setHttpRequestHeaders(newHeaders).build()
                    }
                    
                    val isYoutube = uriStr.contains("youtube.com", ignoreCase = true) || 
                                     uriStr.contains("youtu.be", ignoreCase = true)
                    
                    if (isYoutube) {
                        android.util.Log.d("YOUTUBE_RESOLVER", "ExoPlayer loading Youtube URL: $uriStr")
                        val directUrl = fetchDirectYoutubeAudioUrlSync(uriStr)
                        if (directUrl != null) {
                            android.util.Log.d("YOUTUBE_RESOLVER", "ExoPlayer resolved direct URL on-the-fly: $directUrl")
                            finalDataSpec = dataSpec.withUri(android.net.Uri.parse(directUrl))
                        } else {
                            android.util.Log.e("YOUTUBE_RESOLVER", "ExoPlayer failed to extract YouTube direct URL for: $uriStr")
                            throw java.io.IOException("YouTube streaming is currently unavailable due to API limits.")
                        }
                    }

                    var finalUriStr = finalDataSpec.uri.toString()
                    val hasStreamFlag = finalUriStr.contains("is_stream=true", ignoreCase = true)
                    
                    if (hasStreamFlag) {
                        val cleanedUriStr = finalUriStr
                            .replace("#is_stream=true", "")
                            .replace("?is_stream=true", "")
                            .replace("&is_stream=true", "")
                        finalDataSpec = finalDataSpec.withUri(android.net.Uri.parse(cleanedUriStr))
                        finalUriStr = cleanedUriStr
                    }

                    val isLiveStream = hasStreamFlag ||
                                       finalUriStr.contains("stream", ignoreCase = true) || 
                                       finalUriStr.contains("zeno.fm", ignoreCase = true) ||
                                       finalUriStr.contains(".m3u8", ignoreCase = true) || finalUriStr.contains(".ts", ignoreCase = true) ||
                                       finalUriStr.contains("calmradio", ignoreCase = true) ||
                                       finalUriStr.contains(".fm", ignoreCase = true) ||
                                       finalUriStr.contains("icecast", ignoreCase = true) ||
                                       finalUriStr.contains("149.56.234.138") ||
                                       finalUriStr.contains("plex", ignoreCase = true) ||
                                       finalUriStr.contains("pluto", ignoreCase = true) ||
                                       finalUriStr.contains("tubi", ignoreCase = true) ||
                                       finalUriStr.contains("roku", ignoreCase = true) ||
                                       finalUriStr.contains("radio", ignoreCase = true) ||
                                       finalUriStr.contains("live", ignoreCase = true)
                    
                    val isLocalFile = finalUriStr.startsWith("file://") || finalUriStr.startsWith("content://")
                    
                    activeDataSource = if (isLiveStream || isLocalFile) upstreamDataSource else cacheDataSource
                    return activeDataSource.open(finalDataSpec)
                }

                @Throws(java.io.IOException::class)
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    return activeDataSource.read(buffer, offset, length)
                }

                override fun getUri(): android.net.Uri? {
                    return activeDataSource.getUri()
                }

                override fun getResponseHeaders(): Map<String, List<String>> {
                    return activeDataSource.getResponseHeaders()
                }

                @Throws(java.io.IOException::class)
                override fun close() {
                    activeDataSource.close()
                }
            }
        }
    }

    private fun fetchDirectYoutubeAudioUrlSync(youtubeUrl: String): String? {
        val instances = listOf(
            "https://api.cobalt.tools/api/json",
            "https://co.wuk.sh/api/json"
        )
        for (apiUrl in instances) {
            var conn: java.net.HttpURLConnection? = null
            try {
                val url = java.net.URL(apiUrl)
                conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                conn.doOutput = true
                conn.connectTimeout = 7000
                conn.readTimeout = 7000

                val payload = org.json.JSONObject()
                    .put("url", youtubeUrl)
                    .put("downloadMode", "audio")
                    .put("audioFormat", "mp3")
                    .put("isAudioOnly", true)
                    .put("aFormat", "mp3")
                    .toString()

                conn.outputStream.use { os ->
                    os.write(payload.toByteArray(Charsets.UTF_8))
                }

                if (conn.responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = org.json.JSONObject(responseText)
                    if (jsonObject.has("url")) {
                        val directUrl = jsonObject.getString("url")
                        android.util.Log.d("YOUTUBE_EXTRACTOR", "Successfully extracted direct audio URL from $apiUrl: $directUrl")
                        return directUrl
                    }
                } else {
                    android.util.Log.e("YOUTUBE_EXTRACTOR", "Error response $apiUrl: ${conn.responseCode}")
                }
            } catch (e: java.io.FileNotFoundException) {
                android.util.Log.e("YOUTUBE_EXTRACTOR", "File not found or specific video blocked on $apiUrl: ${e.message}")
            } catch (e: Exception) {
                android.util.Log.e("YOUTUBE_EXTRACTOR", "Failed connection to $apiUrl: ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    fun getCacheSize(context: Context): Long {
        return try {
            getCache(context).cacheSpace
        } catch (e: Exception) {
            0L
        }
    }

    fun clearCache(context: Context) {
        try {
            val cache = getCache(context)
            val keys = cache.keys
            for (key in keys) {
                cache.removeResource(key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
