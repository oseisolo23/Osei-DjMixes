package com.osei.djmixer.ui.components

import com.osei.djmixer.ui.theme.*

import android.media.AudioDeviceInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.osei.djmixer.playback.BluetoothRoutingManager

@Composable
fun BluetoothOutputDialog(
    routingManager: BluetoothRoutingManager,
    onDismiss: () -> Unit
) {
    val availableDevices by routingManager.availableDevices.collectAsState()
    val currentDevice by routingManager.currentDevice.collectAsState()

    LaunchedEffect(Unit) {
        routingManager.refreshDevices()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CyberDark)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BluetoothAudio,
                        contentDescription = "Output Devices",
                        tint = CyberPink,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Audio Output",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    item {
                        DeviceItem(
                            name = "System Default",
                            icon = Icons.Default.Smartphone,
                            isSelected = currentDevice == null,
                            onClick = {
                                routingManager.setPreferredDevice(null)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(availableDevices) { device ->
                        
                        val icon = when (device.type) {
                            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> Icons.Default.Headphones
                            AudioDeviceInfo.TYPE_WIRED_HEADSET,
                            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> Icons.Default.Headset
                            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Icons.Default.Speaker
                            else -> Icons.Default.DeviceUnknown
                        }
                        
                        DeviceItem(
                            name = device.productName.toString().takeIf { it.isNotBlank() } ?: "Device",
                            icon = icon,
                            isSelected = currentDevice?.id == device.id,
                            onClick = {
                                routingManager.setPreferredDevice(device)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close", color = Slate400)
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) CyberPink.copy(alpha = 0.15f) else CyberBlue.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) CyberPink else Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = name,
            color = if (isSelected) CyberPink else Color.White,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = CyberPink,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

