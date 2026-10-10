package com.vongzh.holler.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.vongzh.holler.ui.theme.HollerTheme

@Composable
fun AudioGlowRippleView(
    level: Int,
    active: Boolean,
    playing: Boolean,
    isCancelling: Boolean = false,
    urgent: Boolean = false,
    /**
     * 整体透明度系数（0~1）。上滑取消时由调用方按进度压到 0，
     * 让绿色声波/光晕在红色取消标记出现前完全退场——避免红绿两套色彩在同帧重叠。
     */
    fade: Float = 1f,
    baseRadiusDp: androidx.compose.ui.unit.Dp? = null,
    modifier: Modifier = Modifier,
) {
    val isBusy = active || playing

    val animatedLevel by animateFloatAsState(
        targetValue = (level / 100f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "audioLevel",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "ripplePulse")
    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isBusy) 1800 else 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )
    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isBusy) 2200 else 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )
    val glowBreath by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isBusy) 1000 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowBreath"
    )

    // 录音常态用 Accent；仅当剩余 ≤3s（urgent）才转 Danger，让红色重新成为时间压力信号
    val primaryColor = when {
        isCancelling -> HollerTheme.colors.Danger
        active -> if (urgent) HollerTheme.colors.Danger else HollerTheme.colors.Accent
        else -> HollerTheme.colors.Accent
    }

    val density = androidx.compose.ui.platform.LocalDensity.current

    Box(modifier) {
        // 待机状态完全不显示波纹，保持界面纯净温润；仅在录音喊话或播报时展开向外扩散的动感声波
        if (isBusy && fade > 0.005f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension / 2f
                val baseR = baseRadiusDp?.let { with(density) { it.toPx() } } ?: (maxR * 0.28f)

                // 核心扩散光晕（径向羽化柔和微光，从按键向外弥散）
                val dynamicGlowRadius = (baseR + (maxR - baseR) * 0.45f * glowBreath * (1f + animatedLevel * 0.35f)).coerceAtMost(maxR)
                val coreAlpha = (if (active) {
                    (0.16f + animatedLevel * 0.20f).coerceIn(0.08f, 0.35f)
                } else {
                    (0.12f + animatedLevel * 0.16f).coerceIn(0.06f, 0.28f)
                }) * fade

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = coreAlpha),
                            primaryColor.copy(alpha = coreAlpha * 0.4f),
                            primaryColor.copy(alpha = coreAlpha * 0.1f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = dynamicGlowRadius
                    ),
                    radius = dynamicGlowRadius,
                    center = center
                )

                // 多阶声波水波纹（从实体按钮边缘向全屏自由发散）
                // 取消态收敛：只留核心柔光晕，不再向外扩环——扩散环 + 淡出中的进度环 + 描边
                // 会在浅色座舱里叠出多层粉圈，视觉噪音盖过「松开即取消」的就地反馈。
                if (!isCancelling) {
                    val ripplePhases = floatArrayOf(
                        wavePhase1,
                        (wavePhase1 + 0.33f) % 1f,
                        (wavePhase1 + 0.66f) % 1f,
                        wavePhase2,
                        (wavePhase2 + 0.5f) % 1f
                    )

                    ripplePhases.forEach { p ->
                        // 波纹从实体按键边缘 baseR 开始向外无限阔散，覆盖整个屏幕中央区域
                        val r = baseR + (maxR * 1.85f - baseR) * p + (animatedLevel * maxR * 0.35f * (1f - p))
                        // 随着向外扩散，透明度自然平滑淡出，不形成硬边界
                        val decay = (1f - p).coerceIn(0f, 1f)
                        val ripAlpha = (decay * (0.35f + animatedLevel * 0.38f)).coerceIn(0f, 0.65f) * fade

                        if (ripAlpha > 0.005f) {
                            drawCircle(
                                color = primaryColor.copy(alpha = ripAlpha),
                                radius = r,
                                center = center,
                                style = Stroke(
                                    width = (3.4f * (1f - p * 0.4f) * (1f + animatedLevel * 0.35f)).coerceAtLeast(1.6f)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
