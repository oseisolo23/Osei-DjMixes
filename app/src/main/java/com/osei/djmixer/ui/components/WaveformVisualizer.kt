package com.osei.djmixer.ui.components

import com.osei.djmixer.ui.theme.*

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable
fun WaveformVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 40
) {
    val realTimeAmplitudes by com.osei.djmixer.playback.AudioVisualizerManager.instance.amplitudes.collectAsState()
    
    val hasRealTimeData = remember(realTimeAmplitudes, isPlaying) {
        realTimeAmplitudes.isNotEmpty() && isPlaying
    }

    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    
    // Fallback simulation animations when native visualizer is not active
    val simulatedHeights = (0 until barCount).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = if (isPlaying) 0.8f + (index % 3) * 0.1f else 0.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 450 + (index % 5) * 90,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
    }

    // Dynamic smoothing to avoid any frame stutter or rapid flickers
    val finalBarHeights = (0 until barCount).map { index ->
        val targetHeight = if (hasRealTimeData) {
            realTimeAmplitudes.getOrElse(index) { 0.15f }
        } else {
            simulatedHeights[index].value
        }

        animateFloatAsState(
            targetValue = targetHeight,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "smooth_bar_$index"
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barWidth = 4.dp.toPx()
        val gap = (canvasWidth - (barCount * barWidth)) / (barCount - 1)

        for (i in 0 until barCount) {
            val barHeightFactor = finalBarHeights[i].value
            val barHeight = canvasHeight * barHeightFactor
            val x = i * (barWidth + gap)
            val y = (canvasHeight - barHeight) / 2

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CyberPink.copy(alpha = 0.6f),
                        CyberPink,
                        CyberPink.copy(alpha = 0.6f)
                    )
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

