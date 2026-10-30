package com.osei.djmixer.ui

import com.osei.djmixer.ui.theme.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    onBack: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0.15f) } // Mock progress
    
    // Animate progress if playing
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (progress < 1f) {
                delay(100)
                progress += 0.005f
                if (progress >= 1f) {
                    isPlaying = false
                    progress = 0f
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("STUDIO", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Search */ }) {
                        Icon(Icons.Default.Search, "Search", tint = Color.White)
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
            // Waveform Editor View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CyberSurface),
                contentAlignment = Alignment.Center
            ) {
                // Mock waveform visualization (Static for UI mockup)
                Row(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0..50) {
                        val height = if (i == 25) 100.dp else ((10..100).random()).dp
                        val color = if (i < progress * 50) CyberPink else CyberPurple.copy(alpha = 0.5f)
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(height)
                                .clip(RoundedCornerShape(1.dp))
                                .background(color)
                        )
                    }
                }
                
                // Playhead indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (progress * 300).dp) // simplistic approximation
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color.White)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.TopCenter)
                            .offset(y = (-4).dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.BottomCenter)
                            .offset(y = 4.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
            
            // Time Text
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("00:30", color = Color.White, fontSize = 12.sp)
                Text("02:45", color = Color.White, fontSize = 12.sp)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Edit Controls Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StudioEditButton(Icons.Default.ContentCut, "TRIM")
                StudioEditButton(Icons.AutoMirrored.Filled.CallSplit, "SPLIT")
                StudioEditButton(Icons.Default.Tune, "FADE")
                StudioEditButton(Icons.Default.AutoFixHigh, "EFFECTS")
                StudioEditButton(Icons.Default.Speed, "SPEED")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Broadcast Platforms
            Text(
                "BROADCAST TO",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PlatformButton(name = "YouTube", color = Color(0xFFFF0000))
                PlatformButton(name = "Facebook", color = Color(0xFF1877F2))
                PlatformButton(name = "TikTok", color = Color(0xFF202020), textColor = Color.White)
            }

            // Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { progress = maxOf(0f, progress - 0.1f) }) {
                    Icon(Icons.Default.FastRewind, contentDescription = "Rewind", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(CyberPink)
                        .clickable { isPlaying = !isPlaying },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = { progress = minOf(1f, progress + 0.1f) }) {
                    Icon(Icons.Default.FastForward, contentDescription = "Forward", tint = Color.White)
                }
                
                Spacer(modifier = Modifier.width(32.dp))
                
                // Save Action
                TextButton(onClick = { /* Save */ }) {
                    Icon(Icons.Default.Save, "Save", tint = CyberPink)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SAVE", color = CyberPink, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StudioEditButton(icon: ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { }
    ) {
        Icon(
            icon, 
            contentDescription = label, 
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label, 
            color = Color.White, 
            fontSize = 10.sp, 
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun RowScope.PlatformButton(name: String, color: Color, textColor: Color = Color.White) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .clickable { },
        contentAlignment = Alignment.Center
    ) {
        Text(name, color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
