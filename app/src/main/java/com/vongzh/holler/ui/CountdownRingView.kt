package com.vongzh.holler.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
        // 常态 Accent（正在工作），只有剩余 ≤3s 才转 Danger —— 对应文档承诺的「最后 3 秒预警」，
        // 避免 15 秒全程常红导致红色失去信号意义。
        remaining <= 3f -> HollerTheme.colors.Danger
        else -> HollerTheme.colors.Accent
    }


    val baseAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "countdownAlpha",
    )
    // 内部淡入淡出 × 调用方给的退场系数：上滑取消时环随绿色体系一起让位给红色标记
    val alpha = baseAlpha * fade.coerceIn(0f, 1f)

    val strokeColor = HollerTheme.colors.Stroke
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.055f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)

            // 倒计时/播报进度弧：只有在激活时才绘制，不显示任何静态外框，避免将中心图标圈起来
            if (active && alpha > 0.01f) {
                // 弱底轨道（仅录音倒计时或播报时才可见）
                drawArc(
                    color = strokeColor.copy(alpha = alpha * 0.35f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            // 进度弧：顺时针推进（从 -90° 开始），带有高光发光弧效果
            if (animated > 0f) {
                // 发光外晕
                drawArc(
                    color = ringColor.copy(alpha = alpha * 0.35f),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke * 1.6f, cap = StrokeCap.Round),
                )
                // 实体主光弧
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
