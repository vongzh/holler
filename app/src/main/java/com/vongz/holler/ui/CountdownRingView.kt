package com.vongz.holler.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vongz.holler.ui.theme.HollerTheme
import com.vongz.holler.ui.theme.HollerType

/**
 * 录音倒计时环（对齐 Hey! 的 CountdownRingView）。
 *
 * 为什么需要：
 *   HollerEngine 有 15 秒录音上限（MAX_SECONDS），达到上限会自动截断。
 *   没有倒计时的话，用户按着说话到一半被截断会莫名其妙；
 *   有了环，能直观看到「还剩多少秒」，可主动收尾。
 *
 * 视觉（沿用 Flyme Auto 纪律）：
 *   - 底环用 Stroke 细线（弱），进度弧用状态色（强），形成「点」而非「面」
 *   - 剩余时间文字在圆心，用等宽字体避免数字跳动
 *   - 最后 3 秒进度弧转为 danger 红，给出提醒
 *
 * @param elapsedSeconds  已录秒数（0 ~ maxSeconds）
 * @param maxSeconds      录音上限
 * @param active          是否正在录音（false 时环整体淡出，不占视觉）
 */
@Composable
fun CountdownRing(
    elapsedSeconds: Float,
    maxSeconds: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val progress = (elapsedSeconds / maxSeconds.toFloat()).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = if (active) progress else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "countdown",
    )
    // 最后 3 秒转红提醒
    val remaining = (maxSeconds - elapsedSeconds).coerceAtLeast(0f)
    val ringColor = when {
        !active -> HollerTheme.colors.TextSecondary
        remaining <= 3f -> HollerTheme.colors.Danger
        else -> HollerTheme.colors.Accent
    }
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "countdownAlpha",
    )

    val strokeColor = HollerTheme.colors.Stroke
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.055f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)

            // 底环（弱）
            drawArc(
                color = strokeColor.copy(alpha = alpha * 0.85f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // 进度弧：顺时针推进（从 -90° 开始）
            if (animated > 0f) {
                drawArc(
                    color = ringColor.copy(alpha = alpha),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        if (active) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${(maxSeconds - elapsedSeconds).coerceAtLeast(0f).toInt() + 1}",
                    color = ringColor,
                    fontSize = HollerType.Body,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text("秒", color = HollerTheme.colors.TextSecondary, fontSize = HollerType.Caption)
            }
        }
    }
}

/** 纯进度弧（无文字），用于紧凑场景（如 DOCK 或悬浮窗）。 */
@Composable
fun MiniRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 150),
        label = "miniRing",
    )
    Box(modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.14f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = color.copy(alpha = 0.25f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
