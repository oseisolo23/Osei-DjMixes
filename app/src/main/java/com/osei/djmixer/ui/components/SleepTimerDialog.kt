package com.osei.djmixer.ui.components

import com.osei.djmixer.ui.theme.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun SleepTimerDialog(
    remainingMs: Long?,
    onSetTimer: (Long?) -> Unit,
    onDismiss: () -> Unit,
    showStopRecordingOption: Boolean = false,
    onSetTimerWithRecordingOption: ((Long?, Boolean) -> Unit)? = null
) {
    var customMinutesText by remember { mutableStateOf("") }
    var stopRecording by remember { mutableStateOf(true) }

    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF132038)),
            border = BorderStroke(1.dp, GlassBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = CyberPink,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sleep Timer",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (remainingMs != null) {
                    // Active Timer Display
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Timer Active",
                            color = Slate400,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = formatTime(remainingMs),
                            color = CyberPink,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { 
                                onSetTimer(null)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Turn Off Timer", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Set New Timer Display
                    Text(
                        text = "Choose a preset or enter custom minutes for the countdown.",
                        color = Slate400,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Presets Grid
                    val presets = listOf(
                        "15 min" to 15 * 60 * 1000L,
                        "30 min" to 30 * 60 * 1000L,
                        "60 min" to 60 * 60 * 1000L,
                        "90 min" to 90 * 60 * 1000L,
                        "120 min" to 120 * 60 * 1000L
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in presets.indices step 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (j in i until (i + 2).coerceAtMost(presets.size)) {
                                    val (label, duration) = presets[j]
                                    Button(
                                        onClick = {
                                            if (onSetTimerWithRecordingOption != null) {
                                                onSetTimerWithRecordingOption(duration, stopRecording)
                                            } else {
                                                onSetTimer(duration)
                                            }
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                                        border = BorderStroke(1.dp, GlassBorder),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (showStopRecordingOption) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Checkbox(
                                checked = stopRecording,
                                onCheckedChange = { stopRecording = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = CyberPink,
                                    uncheckedColor = GlassBorder,
                                    checkmarkColor = CyberDark
                                )
                            )
                            Text("Stop recording when timer expires", color = Color.White, fontSize = 14.sp)
                        }
                    }

                    HorizontalDivider(color = GlassBorder, thickness = 1.dp)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Custom Minutes Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = customMinutesText,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.all { it.isDigit() }) {
                                    customMinutesText = input
                                }
                            },
                            label = { Text("Custom Min", color = Slate400) },
                            placeholder = { Text("e.g. 10", color = Slate400.copy(alpha = 0.5f)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = CyberPink,
                                unfocusedBorderColor = GlassBorder,
                                cursorColor = CyberPink
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {
                                val minutes = customMinutesText.toLongOrNull()
                                if (minutes != null && minutes > 0) {
                                    val duration = minutes * 60 * 1000L
                                    if (onSetTimerWithRecordingOption != null) {
                                        onSetTimerWithRecordingOption(duration, stopRecording)
                                    } else {
                                        onSetTimer(duration)
                                    }
                                    onDismiss()
                                }
                            },
                            enabled = customMinutesText.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberPink,
                                disabledContainerColor = CyberPink.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("Set", color = CyberDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
