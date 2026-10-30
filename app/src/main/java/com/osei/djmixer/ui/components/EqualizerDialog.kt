package com.osei.djmixer.ui.components

import com.osei.djmixer.ui.theme.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.osei.djmixer.playback.AudioEqualizerManager

@Composable
fun EqualizerDialog(
    onDismiss: () -> Unit
) {
    val equalizerManager = AudioEqualizerManager.instance
    
    val isEnabled by equalizerManager.enabled.collectAsState()
    val gains by equalizerManager.gains.collectAsState()
    val selectedPreset by equalizerManager.selectedPreset.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(CyberDark)
                .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column {
                // --- HEADER BAR ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = null,
                            tint = CyberPink,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Audio Equalizer",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ON/OFF Switch Label
                        Text(
                            text = if (isEnabled) "Active" else "Bypassed",
                            color = if (isEnabled) CyberPink else Slate400,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { equalizerManager.setEqualizerEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberDark,
                                checkedTrackColor = CyberPink,
                                uncheckedThumbColor = Slate400,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                        
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Equalizer",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- PRESETS ROW ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presetList = listOf("Flat", "Bass Booster", "Treble Booster", "Vocal Booster")
                    presetList.forEach { preset ->
                        val isSelected = selectedPreset == preset
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) CyberPink.copy(alpha = 0.15f)
                                    else Color.White.copy(alpha = 0.04f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) CyberPink else Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable(enabled = isEnabled) {
                                    equalizerManager.applyPreset(preset)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset,
                                color = if (isSelected) CyberPink else if (isEnabled) Color.White else Color.White.copy(alpha = 0.4f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // --- 10-BAND MX SLIDERS PANEL ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.2f))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val bands = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
                        bands.forEachIndexed { index, label ->
                            val gain = gains.getOrElse(index) { 0f }
                            VerticalFader(
                                value = gain,
                                onValueChange = { newValue ->
                                    equalizerManager.setBandGain(index, newValue)
                                },
                                label = label,
                                enabled = isEnabled,
                                modifier = Modifier.fillMaxHeight()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Adjust bands from -15dB to +15dB or use quick audio presets to tailor sound frequency output.",
                    color = Slate400,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun VerticalFader(
    value: Float, // -15 to +15
    onValueChange: (Float) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = -15f..15f
) {
    val sliderAlpha = if (enabled) 1f else 0.4f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(52.dp)
            .padding(vertical = 2.dp)
    ) {
        // Gain dB reading
        Text(
            text = "${if (value > 0) "+" else ""}${value.toInt()}dB",
            fontSize = 10.sp,
            color = if (!enabled) Slate400.copy(alpha = 0.4f) else if (value != 0f) CyberPink else Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Fader Container Box
        BoxWithConstraints(
            modifier = Modifier
                .width(22.dp)
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = if (enabled) 0.05f else 0.02f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = if (enabled) 0.1f else 0.05f),
                    shape = RoundedCornerShape(6.dp)
                )
                .pointerInput(enabled, value) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = { offset ->
                            val rawFraction = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                            val newValue = range.start + rawFraction * (range.endInclusive - range.start)
                            onValueChange(newValue)
                        }
                    )
                }
                .pointerInput(enabled, value) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val fractionDelta = -dragAmount.y / size.height.toFloat()
                        val valueDelta = fractionDelta * (range.endInclusive - range.start)
                        val newValue = (value + valueDelta).coerceIn(range.start, range.endInclusive)
                        onValueChange(newValue)
                    }
                }
        ) {
            val h = constraints.maxHeight.toFloat()
            val fraction = (value - range.start) / (range.endInclusive - range.start)
            val thumbY = h * (1f - fraction)

            Canvas(modifier = Modifier.fillMaxSize()) {
                // Line track
                drawLine(
                    color = Color.White.copy(alpha = if (enabled) 0.12f else 0.05f),
                    start = Offset(size.width / 2f, 8.dp.toPx()),
                    end = Offset(size.width / 2f, size.height - 8.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )

                // Highlight active path
                if (enabled) {
                    drawLine(
                        color = CyberPink.copy(alpha = 0.5f),
                        start = Offset(size.width / 2f, thumbY),
                        end = Offset(size.width / 2f, size.height - 8.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                }

                // Tick marks (Grid lines)
                val ticks = listOf(0.25f, 0.5f, 0.75f)
                ticks.forEach { t ->
                    val tickY = size.height * t
                    drawLine(
                        color = Color.White.copy(alpha = if (enabled) 0.15f else 0.05f),
                        start = Offset(size.width / 2f - 4.dp.toPx(), tickY),
                        end = Offset(size.width / 2f + 4.dp.toPx(), tickY),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Drawing physical Slider Knob
                val thumbWidth = size.width - 4.dp.toPx()
                val thumbHeight = 12.dp.toPx()
                drawRoundRect(
                    color = if (enabled) CyberPink else Slate400.copy(alpha = 0.5f),
                    topLeft = Offset(2.dp.toPx(), thumbY - thumbHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )

                // Central knob line groove (like a real physical mixer board!)
                drawLine(
                    color = CyberSurface,
                    start = Offset(2.dp.toPx(), thumbY),
                    end = Offset(size.width - 2.dp.toPx(), thumbY),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Frequency Label ("31", "1k")
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (enabled) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.4f),
            fontWeight = FontWeight.Bold
        )
    }
}
