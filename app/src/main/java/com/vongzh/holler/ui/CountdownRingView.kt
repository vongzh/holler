package com.vongzh.holler.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import com.vongzh.holler.ui.theme.HollerTheme
import com.vongzh.holler.ui.theme.HollerType

/**
 * 录音倒计时高精度流光进度环。
 *
 * 遵循车规级克制设计原则：
 * 1. 弱底导轨：1.8dp 微弱轨道底线，不遮蔽背景；
 * 2. 精准实体流光弧：3.6dp 圆角高光弧，平滑顺时针行进；
 * 3. 告警变色：常态品牌绿，最后 3 秒自动切换为警示红；
 * 4. 上滑取消时随 fade 平滑淡出，不与取消标记重叠。
 */
@Composable
fun CountdownRing(
    elapsedSeconds: Float,
    maxSeconds: Float,
    active: Boolean,
    modifier: Modifier = Modifier,
    showText: Boolean = true,
    customColor: Color? = null,
    /** 整体透明度系数（0~1）：上滑取消时压到 0，让绿环在红色取消标记出现前退净。 */
    fade: Float = 1f,
) {
    val progress = (elapsedSeconds / maxSeconds.coerceAtLeast(0.1f)).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = if (active) progress else 0f,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "countdown",
    )
    val remaining = (maxSeconds - elapsedSeconds).coerceAtLeast(0f)
    val ringColor = customColor ?: when {
        !active -> HollerTheme.colors.TextSecondary
        remaining <= 3f -> HollerTheme.colors.Danger
        else -> HollerTheme.colors.Accent
    }

    val baseAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "countdownAlpha",
    )
    val alpha = baseAlpha * fade.coerceIn(0f, 1f)
    val trackColor = HollerTheme.colors.Stroke

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackStroke = 1.8.dp.toPx()
            val arcStroke = 3.6.dp.toPx()
            val glowStroke = 7.dp.toPx()
            val inset = glowStroke / 2f
            val arcSize = Size(size.width - glowStroke, size.height - glowStroke)

            if (active && alpha > 0.01f) {
                // 1. 极轻微克制的环境导轨线
                drawArc(
                    color = trackColor.copy(alpha = alpha * 0.35f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = trackStroke, cap = StrokeCap.Round),
                )
            }

            // 2. 流光进度弧
            if (animated > 0.002f) {
                val sweep = (360f * animated).coerceIn(0f, 360f)

                // 2.1 底层微散晕
                drawArc(
                    color = ringColor.copy(alpha = alpha * 0.25f),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = glowStroke, cap = StrokeCap.Round),
                )

                // 2.2 核心精密流光弧
                drawArc(
                    color = ringColor.copy(alpha = alpha),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = arcStroke, cap = StrokeCap.Round),
                )
            }
        }

        if (active && showText) {
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
