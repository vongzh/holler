package com.vongz.holler.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.vongz.holler.ui.theme.HollerTheme

/**
 * 震撼的水波纹与声波扩散动效（对标 Hey! 的 AudioGlow + Ripple + Waveform）。
 *
 * 视觉构成：
 * 1. 【水波纹扩散 Ripple】：多层同心大圆环持续向外动态涌动并淡出，在录音或播报时伴随电平强弱汹涌扩散。
 * 2. 【能量光晕 Glow】：大范围柔和径向渐变，根据实时音量产生呼吸脉冲。
 * 3. 【多频段动态声波圈 Waveform】：随音量动态起伏的环形声波波纹。
 *
 * @param level     当前音量峰值 0~100
 * @param active    是否处于录音中
 * @param playing   是否处于播报中
 */
@Composable
fun AudioGlowRippleView(
    level: Int,
    active: Boolean,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val isBusy = active || playing

    // 平滑电平过度
    val animatedLevel by animateFloatAsState(
        targetValue = (level / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 80),
        label = "audioLevel",
    )

    // 水波纹无限循环推进器（0f ~ 1f）
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
            animation = tween(if (isBusy) 2200 else 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )
    val glowBreath by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isBusy) 900 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowBreath"
    )

    val primaryColor = when {
        active -> HollerTheme.colors.Danger
        playing -> HollerTheme.colors.Accent
        else -> HollerTheme.colors.AccentSoft
    }
    val themeAccent = HollerTheme.colors.Accent

    Box(modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f
            val baseR = maxR * 0.46f

            // 1. 底层大范围环境光晕 (Radial Glow)
            val glowRadius = (baseR * 1.55f * glowBreath * (1f + animatedLevel * 0.45f)).coerceAtMost(maxR)
            val glowAlpha = when {
                active -> (0.28f + animatedLevel * 0.35f).coerceIn(0.1f, 0.65f)
                playing -> (0.22f + animatedLevel * 0.28f).coerceIn(0.1f, 0.55f)
                else -> 0.08f
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = glowAlpha),
                        primaryColor.copy(alpha = glowAlpha * 0.35f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )

            // 2. 动感水波纹 (Expanding Ripples)
            val ripplePhases = floatArrayOf(
                wavePhase1,
                (wavePhase1 + 0.33f) % 1f,
                (wavePhase1 + 0.66f) % 1f,
                wavePhase2,
                (wavePhase2 + 0.5f) % 1f
            )

            ripplePhases.forEachIndexed { index, p ->
                // 波纹从按钮边缘 baseR 开始扩散到最外圈 maxR
                val r = baseR + (maxR - baseR) * p + (animatedLevel * maxR * 0.15f * (1f - p))
                // 随着半径扩散，透明度衰减
                val baseFade = (1f - p).coerceIn(0f, 1f)
                val alphaMultiplier = if (isBusy) (0.35f + animatedLevel * 0.45f) else 0.15f
                val ripAlpha = (baseFade * baseFade * alphaMultiplier).coerceIn(0f, 0.8f)

                if (ripAlpha > 0.02f) {
                    val color = if (index % 2 == 0) primaryColor else themeAccent
                    drawCircle(
                        color = color.copy(alpha = ripAlpha),
                        radius = r,
                        center = center,
                        style = Stroke(
                            width = (5f * (1f - p * 0.5f) * (1f + animatedLevel * 0.5f)).coerceAtLeast(1.5f)
                        )
                    )
                }
            }

            // 3. 核心周围的实时紧密声波颤动环
            if (isBusy) {
                val waveCount = 3
                for (i in 0 until waveCount) {
                    val spread = (i + 1) * (12f + animatedLevel * 30f)
                    val waveR = (baseR + spread).coerceAtMost(maxR)
                    val a = (0.5f - i * 0.15f) * (0.3f + animatedLevel * 0.7f)
                    drawCircle(
                        color = primaryColor.copy(alpha = a.coerceIn(0.05f, 0.8f)),
                        radius = waveR,
                        center = center,
                        style = Stroke(width = 2.5f)
                    )
                }
            }
        }
    }
}
