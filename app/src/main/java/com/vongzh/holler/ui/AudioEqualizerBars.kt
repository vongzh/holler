package com.vongzh.holler.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 录音与播报状态下的灵动跳动声波柱组件。
 * 5 根胶囊条柱随音量与动态相位有节奏地上下起伏，替代生硬缩放的静态图标。
 */
@Composable
fun AudioEqualizerBars(
    color: Color,
    level: Int,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eqWaves")
    
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRecording) 350 else 420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase1"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRecording) 480 else 550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase2"
    )
    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRecording) 400 else 460, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase3"
    )

    val normalizedLevel = (level / 100f).coerceIn(0.1f, 1f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(74.dp, 54.dp)) {
            val barCount = 5
            val barWidth = 8.dp.toPx()
            val totalSpacing = size.width - (barCount * barWidth)
            val gap = totalSpacing / (barCount - 1)
            val maxBarHeight = size.height
            val minBarHeight = 10.dp.toPx()

            // 5 根柱子的高度权重乘数，形成自然的中间高、两边低曲线
            val heightMultipliers = floatArrayOf(
                (0.35f + phase3 * 0.45f * normalizedLevel),
                (0.50f + phase1 * 0.50f * normalizedLevel),
                (0.65f + phase2 * 0.35f * normalizedLevel),
                (0.50f + phase3 * 0.50f * normalizedLevel),
                (0.35f + phase1 * 0.45f * normalizedLevel)
            )

            for (i in 0 until barCount) {
                val h = (minBarHeight + (maxBarHeight - minBarHeight) * heightMultipliers[i]).coerceIn(minBarHeight, maxBarHeight)
                val left = i * (barWidth + gap)
                val top = (size.height - h) / 2f
                
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
    }
}
