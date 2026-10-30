package com.osei.djmixer.ui

import android.Manifest
import android.view.SurfaceHolder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.osei.djmixer.ui.theme.*
import com.pedro.library.rtmp.RtmpCamera1
import com.pedro.common.ConnectChecker
import com.pedro.library.view.OpenGlView
import android.widget.Toast
import com.google.accompanist.permissions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun StudioBroadcastScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var rtmpCamera by remember { mutableStateOf<RtmpCamera1?>(null) }
    var isBroadcasting by remember { mutableStateOf(false) }
    var currentPlatform by remember { mutableStateOf("YouTube") }
    var streamUrl by remember { mutableStateOf("") }
    
    val coroutineScope = rememberCoroutineScope()
    
    val cameraPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.allPermissionsGranted) {
            cameraPermissionState.launchMultiplePermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LIVE STUDIO", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberDark)
            )
        },
        containerColor = CyberDark
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (cameraPermissionState.allPermissionsGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CyberSurface),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            OpenGlView(ctx).apply {
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {
                                        val checker = object : ConnectChecker {
                                            override fun onConnectionStarted(url: String) {}
                                            override fun onConnectionSuccess() {
                                                coroutineScope.launch(Dispatchers.Main) {
                                                    isBroadcasting = true
                                                    Toast.makeText(context, "Live Broadcast Started", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            override fun onConnectionFailed(reason: String) {
                                                coroutineScope.launch(Dispatchers.Main) {
                                                    rtmpCamera?.stopStream()
                                                    isBroadcasting = false
                                                    Toast.makeText(context, "Connection Failed: $reason", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                            override fun onNewBitrate(bitrate: Long) {}
                                            override fun onDisconnect() {
                                                coroutineScope.launch(Dispatchers.Main) {
                                                    isBroadcasting = false
                                                    Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            override fun onAuthError() {}
                                            override fun onAuthSuccess() {}
                                        }
                                        rtmpCamera = RtmpCamera1(this@apply, checker)
                                        rtmpCamera?.startPreview()
                                    }

                                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        if (rtmpCamera?.isStreaming == true) {
                                            rtmpCamera?.stopStream()
                                        }
                                        rtmpCamera?.stopPreview()
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    if (isBroadcasting) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Red)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("LIVE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CyberSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Camera & Mic Permissions Required", color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { cameraPermissionState.launchMultiplePermissionRequest() }, colors = ButtonDefaults.buttonColors(containerColor = CyberPink)) {
                            Text("Grant Permissions", color = Color.White)
                        }
                    }
                }
            }

            Text(
                "BROADCAST PLATFORM",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BroadcastPlatformButton(
                    name = "YouTube", 
                    color = Color(0xFFFF0000), 
                    selected = currentPlatform == "YouTube",
                    onClick = { 
                        currentPlatform = "YouTube" 
                        streamUrl = "rtmp://a.rtmp.youtube.com/live2/YOUR_STREAM_KEY"
                    }
                )
                BroadcastPlatformButton(
                    name = "Facebook", 
                    color = Color(0xFF1877F2), 
                    selected = currentPlatform == "Facebook",
                    onClick = { 
                        currentPlatform = "Facebook" 
                        streamUrl = "rtmps://live-api-s.facebook.com:443/rtmp/YOUR_STREAM_KEY"
                    }
                )
                BroadcastPlatformButton(
                    name = "TikTok", 
                    color = Color(0xFF202020), 
                    textColor = Color.White,
                    selected = currentPlatform == "TikTok",
                    onClick = { 
                        currentPlatform = "TikTok" 
                        streamUrl = "rtmp://push-rtmp-f5-va01.tiktokcdn.com/YOUR_STREAM_KEY"
                    }
                )
            }
            
            OutlinedTextField(
                value = streamUrl,
                onValueChange = { streamUrl = it },
                label = { Text("RTMP Stream URL", color = Color.White.copy(alpha = 0.7f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberPink,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (rtmpCamera?.isStreaming == true) {
                            rtmpCamera?.stopStream()
                            isBroadcasting = false
                        } else {
                            if (streamUrl.isNotEmpty() && !streamUrl.contains("YOUR_STREAM_KEY")) {
                                if (rtmpCamera?.prepareAudio() == true && rtmpCamera?.prepareVideo() == true) {
                                    rtmpCamera?.startStream(streamUrl)
                                } else {
                                    Toast.makeText(context, "Error preparing stream", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Please enter a valid Stream Key", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBroadcasting) Color.Red else CyberPink
                    ),
                    modifier = Modifier
                        .height(56.dp)
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                ) {
                    Icon(
                        if (isBroadcasting) Icons.Default.Stop else Icons.Default.Sensors,
                        contentDescription = "Broadcast",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isBroadcasting) "STOP BROADCAST" else "GO LIVE", 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun RowScope.BroadcastPlatformButton(
    name: String, 
    color: Color, 
    textColor: Color = Color.White,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) color else color.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(name, color = if (selected) textColor else textColor.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
