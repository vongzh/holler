package com.vongz.holler.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.vongz.holler.ui.theme.HollerTheme

/**
 * 声波可视化。
 *
 * 设计：以中心为原点向外扩散的同心圆弧，透明度与半径随录音强度变化。
 * 保持 Flyme Auto 的克制感 —— 不出彩但不廉价，无过度的粒子/光效。
 *
 * @param level     当前音量峰值 0~100
 * @param active    是否处于激活态（录音中）
 * @param listening 是否处于聆听/播放态
 */
@Composable
fun WaveformView(
    level: Int,
    active: Boolean,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val ringCount = 4

    // 保存上一帧用于平滑过渡
    val animatedLevel by animateFloatAsState(
        targetValue = level / 100f,
        animationSpec = tween(durationMillis = 90),
        label = "level",
    )

    val color = when {
        active -> HollerTheme.colors.Danger
        playing -> HollerTheme.colors.Accent
        else -> HollerTheme.colors.TextSecondary
    }

    Box(modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.18f
            val maxExtra = size.minDimension * 0.30f

            // 同心圆弧：每圈按序延迟响应强度
            for (i in 0 until ringCount) {
                val t = i / ringCount.toFloat()
                val factor = (1f - t) * animatedLevel + 0.12f
                val radius = baseRadius + maxExtra * factor
                val alpha = (0.55f * (1f - t * 0.6f) * (0.35f + factor)).coerceIn(0.05f, 0.9f)

                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 3f + 2f * (1f - t)),
                )
            }

            // 中心实心点
            drawCircle(
                color = color.copy(alpha = if (active || playing) 0.85f else 0.35f),
                radius = baseRadius * 0.42f,
                center = center,
            )
        }
    }
}

/** 一个更「条状」的声波，用于 DOCK 栏的迷你指示。 */
@Composable
fun MiniBars(level: Int, active: Boolean, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = level / 100f,
        animationSpec = tween(90),
        label = "minibar",
    )
    val color = if (active) HollerTheme.colors.Danger else HollerTheme.colors.TextSecondary

    Box(modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bars = 12
            val gap = size.width / (bars * 2f)
            val barW = gap
            for (i in 0 until bars) {
                val centerDist = kotlin.math.abs(i - (bars - 1) / 2f) / ((bars - 1) / 2f)
                val h = size.height * (0.25f + 0.75f * (1f - centerDist) * (0.3f + animated))
                val x = gap + i * gap * 2f
                drawLine(
                    color = color.copy(alpha = 0.8f),
                    start = Offset(x, size.height / 2f - h / 2f),
                    end = Offset(x, size.height / 2f + h / 2f),
                    strokeWidth = barW,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
