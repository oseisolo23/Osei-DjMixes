package com.osei.djmixer.ui

import com.osei.djmixer.ui.theme.*

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.io.FileInputStream
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Html5PlayerScreen(
    filePath: String,
    mixTitle: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }

        val decodedFilePath = remember(filePath) {
        try {
            val decodedBytes = android.util.Base64.decode(filePath, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
            val decodedStr = String(decodedBytes, Charsets.UTF_8)
            if (decodedStr.startsWith("/") || decodedStr.contains("://")) decodedStr else filePath
        } catch (e: Exception) {
            filePath
        }
    }

    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    
    val fileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            fileChooserCallback?.onReceiveValue(arrayOf(uri))
        } else {
            fileChooserCallback?.onReceiveValue(null)
        }
        fileChooserCallback = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "HTML5 Mix Player",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Mixes",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberDark,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(CyberDark)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                        
                        // Extra file access settings
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                Log.d("HTML5_PLAYER", "SSL Error received for local domain: ${error?.primaryError}. Proceeding...")
                                handler?.proceed() // Allow local domain applet.local to load despite self-signed/no certificate
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url ?: return null
                                if (url.host == "applet.local") {
                                    val path = url.path
                                    if (path == "/mix.wav") {
                                        val (inputStream, mimeType) = getInputStreamAndMime(context, decodedFilePath)
                                        if (inputStream != null) {
                                            val response = WebResourceResponse(mimeType, "UTF-8", inputStream)
                                            response.responseHeaders = mapOf(
                                                "Access-Control-Allow-Origin" to "*",
                                                "Access-Control-Allow-Methods" to "GET, OPTIONS",
                                                "Access-Control-Allow-Headers" to "Content-Type, Range",
                                                "Access-Control-Allow-Credentials" to "true"
                                            )
                                            return response
                                        }
                                    } else if (path == "/custom_stream") {
                                        val targetUrl = url.getQueryParameter("url") ?: ""
                                        val (inputStream, mimeType) = getInputStreamAndMime(context, targetUrl)
                                        if (inputStream != null) {
                                            val response = WebResourceResponse(mimeType, "UTF-8", inputStream)
                                            response.responseHeaders = mapOf(
                                                "Access-Control-Allow-Origin" to "*",
                                                "Access-Control-Allow-Methods" to "GET, OPTIONS",
                                                "Access-Control-Allow-Headers" to "Content-Type, Range",
                                                "Access-Control-Allow-Credentials" to "true"
                                            )
                                            return response
                                        }
                                    } else if (path == "/player.html") {
                                        try {
                                            val inputStream = context.assets.open("html5_player.html")
                                            val response = WebResourceResponse("text/html", "UTF-8", inputStream)
                                            response.responseHeaders = mapOf(
                                                "Access-Control-Allow-Origin" to "*",
                                                "Access-Control-Allow-Methods" to "GET, OPTIONS",
                                                "Access-Control-Allow-Headers" to "Content-Type"
                                            )
                                            return response
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                consoleMessage?.let {
                                    Log.d("HTML5_PLAYER_CONSOLE", "[${it.messageLevel()}] ${it.message()} -- line ${it.lineNumber()} of ${it.sourceId()}")
                                }
                                return true
                            }
                            
                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                fileChooserCallback = filePathCallback
                                fileChooserLauncher.launch("audio/*")
                                return true
                            }
                        }

                        // Load with query parameters to describe title
                        val encodedTitle = try {
                            URLEncoder.encode(mixTitle, "UTF-8")
                        } catch (e: Exception) {
                            "Mix%20Recording"
                        }
                        loadUrl("https://applet.local/player.html?title=$encodedTitle")
                    }
                }
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = CyberPink
                )
            }
        }
    }
}

private fun fetchDirectYoutubeAudioUrlSync(youtubeUrl: String): String? {
    Log.e("YOUTUBE_EXTRACTOR", "YouTube extraction is currently disabled due to public API shutdowns (Cobalt v7) and aggressive anti-bot blocking by YouTube (Invidious/Piped). Please use direct .mp3, .wav, or Icecast streams instead.")
    return null
}

private fun getInputStreamAndMime(context: android.content.Context, path: String): Pair<java.io.InputStream?, String> {
    var finalPath = path
    val isYoutube = path.contains("youtube.com") || path.contains("youtu.be")
    
    if (isYoutube) {
        Log.d("HTML5_PLAYER", "Attempting real-time extraction of YouTube URL: $path")
        val extractedUrl = fetchDirectYoutubeAudioUrlSync(path)
        if (extractedUrl != null) {
            finalPath = extractedUrl
            Log.d("HTML5_PLAYER", "Extracted YouTube direct stream URL successfully: $finalPath")
        } else {
            Log.e("HTML5_PLAYER", "Failed to extract direct YouTube audio stream URL for: $path")
        }
    }

    val mimeType = when {
        finalPath.contains(".mp3") || finalPath.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
        finalPath.contains(".m4a") || finalPath.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
        finalPath.contains(".wav") || finalPath.endsWith(".wav", ignoreCase = true) -> "audio/wav"
        else -> "audio/mpeg" // Normal stream fallback to generic mpeg
    }

    return try {
        if (finalPath.startsWith("content://") || finalPath.startsWith("android.resource://")) {
            val uri = Uri.parse(finalPath)
            val inputStream = context.contentResolver.openInputStream(uri)
            val resolvedMime = context.contentResolver.getType(uri) ?: mimeType
            Pair(inputStream, resolvedMime)
        } else if (finalPath.startsWith("http://") || finalPath.startsWith("https://")) {
            val url = java.net.URL(finalPath)
            val conn = url.openConnection() as java.net.HttpURLConnection
            // Standard User-Agent to bypass Cloudflare and host anti-scraping blocks
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
            conn.connect()
            Pair(conn.inputStream, conn.contentType ?: mimeType)
        } else {
            val trimmedPath = if (finalPath.startsWith("file://")) finalPath.substring(7) else finalPath
            val file = File(trimmedPath)
            if (file.exists() && file.isFile) {
                Pair(FileInputStream(file), mimeType)
            } else {
                Pair(null, mimeType)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Pair(null, mimeType)
    }
}
