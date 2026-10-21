package com.vongzh.holler.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vongzh.holler.ui.theme.HollerShapes
import com.vongzh.holler.ui.theme.HollerTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 车规级一体式微晶呼吸悬浮音量条 (Monolithic Responsive Volume Bar)。
 *
 * 核心优化原则与交互架构：
 * 1. 平时精致雅致（Refined Idle）：非交互时收敛为宽 12dp、长 280dp 的车规高质感微晶胶囊轨，视认度清晰且低视觉侵占，通过流体液位直观显示当前音量比例；
 * 2. 交互智能放大（Interactive Expansion）：指尖轻触或滑动瞬间，依靠物理弹簧平滑放大至 40dp 宽幅 HUD 操控胶囊；
 * 3. 动态 HUD 穿透：胶囊放大时，顶部数字百分比与底部自适应喇叭/静音图标优雅淡入，随液位自适应反色与明暗；
 * 4. 宽幅防脱触感应：底层维持 54dp 宽幅热区，与左下角设置按键严格垂直居中对齐，行车盲操随手即触；
 * 5. 优雅驻留延时收纳：滑动调节松手或外部音量变动后，平滑驻留 800ms 展示最终音量，随后优雅微弹收起为常驻胶囊条；
 * 6. 多维触觉与手势：底部轻触快速静音/恢复，滑轨上下平滑调音，每越过 10% 产生细腻线性齿轮阻尼震动。
 */
@Composable
fun VerticalVolumeBar(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackHeightDp: Float = 280f,
    idleWidthDp: Float = 12f,
    expandedWidthDp: Float = 40f
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current
    val isDarkTheme = HollerTheme.colors.BgBase.luminance() < 0.5f
    val scope = rememberCoroutineScope()

    var isTouching by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    var collapseJob by remember { mutableStateOf<Job?>(null) }

    var lastSavedVolume by remember { mutableFloatStateOf(if (volume > 0.05f) volume else 0.5f) }
    var lastHapticStep by remember { mutableIntStateOf((volume * 10).roundToInt()) }

    // 外部音量发生变化时（如方向盘按键、物理旋钮、广播通知），自动展开 1000ms 呈现视觉反馈后静默收回
    var isFirstComposition by remember { mutableStateOf(true) }
    var lastSeenVolume by remember { mutableFloatStateOf(volume) }
    LaunchedEffect(volume) {
        if (isFirstComposition) {
            isFirstComposition = false
            lastSeenVolume = volume
            return@LaunchedEffect
        }
        if (abs(volume - lastSeenVolume) > 0.005f) {
            lastSeenVolume = volume
            if (!isTouching) {
                collapseJob?.cancel()
                isExpanded = true
                collapseJob = scope.launch {
                    delay(1000)
                    isExpanded = false
                }
            }
        }
    }

    // 拖拽中 0 延迟实时贴手跟手；释放或外部传入时带平滑弹性物理动效
    val animatedVol by animateFloatAsState(
        targetValue = volume.coerceIn(0f, 1f),
        animationSpec = if (isDragging) snap() else spring(dampingRatio = 0.85f, stiffness = 800f),
        label = "volAnim"
    )

    // 胶囊核心宽度：平时 12dp 胶囊轨，交互滑动时平滑膨胀至 40dp
    val capsuleWidthDp by animateDpAsState(
        targetValue = if (isExpanded) expandedWidthDp.dp else idleWidthDp.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 550f),
        label = "capsuleWidth"
    )

    // 展开过渡进度系数 (0f -> 1f)，用于文字、图标与刻度的优雅淡入淡出及缩放
    val expandProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isExpanded) 180 else 140,
            easing = FastOutSlowInEasing
        ),
        label = "expandProgress"
    )

    // 胶囊悬浮高程 (展开时增强阴影感)
    val elevationDp by animateDpAsState(
        targetValue = if (isExpanded) 12.dp else 4.dp,
        animationSpec = tween(180),
        label = "elevationAnim"
    )

    // 液态流光色彩（三段阶梯均衡分布：0~50% 柔和青绿，50~80% 暖金响度提醒，80~100% 警示亮红）
    val tierColor = when {
        volume <= 0.005f -> HollerTheme.colors.TextSecondary.copy(alpha = 0.35f)
        volume <= 0.50f -> HollerTheme.colors.Accent
        volume <= 0.80f -> Color(0xFFFFB300) // 暖金响度提醒
        else -> Color(0xFFFF4D4F)            // 警示红
    }
    val animatedTierColor by animateColorAsState(
        targetValue = tierColor,
        animationSpec = tween(180),
        label = "tierColor"
    )

    val isMuted = volume <= 0.005f
    val currentPercent = (animatedVol * 100).roundToInt()

    // 胶囊背景材质
    val capsuleBgColor = if (isDarkTheme) {
        HollerTheme.colors.BrandElevated.copy(alpha = if (isExpanded) 0.82f else 0.65f)
    } else {
        Color(0xFFD8E2ED).copy(alpha = if (isExpanded) 0.85f else 0.65f)
    }

    // 胶囊边缘微光折射边框
    val borderBrush = if (isDarkTheme) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (isExpanded) 0.38f else 0.16f),
                HollerTheme.colors.Stroke.copy(alpha = 0.6f),
                Color.White.copy(alpha = 0.08f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.95f),
                HollerTheme.colors.Stroke.copy(alpha = 0.8f),
                Color.White.copy(alpha = 0.5f)
            )
        )
    }

    val trackHeightPx = with(density) { trackHeightDp.dp.toPx() }
    val bottomMuteThresholdPx = with(density) { 38.dp.toPx() }
    val touchSlop = viewConfig.touchSlop

    // 外层容器：维持 54dp 宽幅防脱触热区，居中对齐内部自适应微晶胶囊
    Box(
        modifier = modifier
            .width(54.dp)
            .height(trackHeightDp.dp)
            .pointerInput(trackHeightDp) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startY = down.position.y
                    val isBottomTouch = startY >= (trackHeightPx - bottomMuteThresholdPx)

                    // 触摸按下即刻唤醒放大展开
                    collapseJob?.cancel()
                    isTouching = true
                    isExpanded = true

                    var isDragStarted = false
                    var pointerId = down.id
                    var currentY = startY

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) {
                            // 抬手处理
                            if (!isDragStarted) {
                                if (isBottomTouch) {
                                    // 判定为轻触底部小喇叭区域 -> 切换静音/恢复
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isMuted) {
                                        val restoreVol = lastSavedVolume.coerceAtLeast(0.35f)
                                        onVolumeChange(restoreVol)
                                    } else {
                                        lastSavedVolume = volume
                                        onVolumeChange(0f)
                                    }
                                } else {
                                    // 点击轨道直接调节至对应位置音量
                                    val tapVol = (1f - (startY / trackHeightPx)).coerceIn(0f, 1f)
                                    onVolumeChange(tapVol)
                                    if (tapVol > 0.05f) lastSavedVolume = tapVol
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                            break
                        }

                        currentY = change.position.y
                        val diffY = abs(currentY - startY)

                        if (!isDragStarted) {
                            if (!isBottomTouch || diffY > touchSlop) {
                                isDragStarted = true
                                isDragging = true
                                change.consume()

                                val initialVol = (1f - (currentY / trackHeightPx)).coerceIn(0f, 1f)
                                onVolumeChange(initialVol)
                                if (initialVol > 0.05f) lastSavedVolume = initialVol

                                val initialStep = (initialVol * 10).roundToInt()
                                lastHapticStep = initialStep
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        } else {
                            change.consume()
                            val newVol = (1f - (currentY / trackHeightPx)).coerceIn(0f, 1f)
                            onVolumeChange(newVol)
                            if (newVol > 0.05f) lastSavedVolume = newVol

                            val newStep = (newVol * 10).roundToInt()
                            if (newStep != lastHapticStep) {
                                lastHapticStep = newStep
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }

                    // 手指抬起，释放拖拽状态，进入 800ms 平滑驻留倒计时后微弹收纳
                    isDragging = false
                    isTouching = false
                    collapseJob?.cancel()
                    collapseJob = scope.launch {
                        delay(800)
                        isExpanded = false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 核心一体化自适应微晶胶囊主体
        Box(
            modifier = Modifier
                .width(capsuleWidthDp)
                .height(trackHeightDp.dp)
                .shadow(
                    elevation = elevationDp,
                    shape = HollerShapes.Capsule,
                    ambientColor = if (isDarkTheme) Color(0x33000000) else Color(0x1F1C2C40),
                    spotColor = if (isDarkTheme) animatedTierColor.copy(alpha = if (isExpanded) 0.35f else 0.12f) else Color(0x2E1C2C40)
                )
                .clip(HollerShapes.Capsule)
                .background(capsuleBgColor)
                .border(1.dp, borderBrush, HollerShapes.Capsule)
        ) {
            // 1. 底层 Canvas：绘制精密刻度标尺、液态充能填充柱、液面微晶高光线与静音微圆点
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val capsuleCorner = CornerRadius(w / 2f, w / 2f)

                // 1.1 精密内部微刻度 (25%, 50%, 75%)：仅在放大展开时渐进淡入
                if (expandProgress > 0.15f) {
                    val tickAlpha = ((expandProgress - 0.15f) / 0.85f) * (if (isDarkTheme) 0.18f else 0.22f)
                    val tickColor = if (isDarkTheme) Color.White.copy(alpha = tickAlpha) else Color(0x33000000).copy(alpha = tickAlpha)
                    val tickSteps = floatArrayOf(0.25f, 0.50f, 0.75f)
                    for (step in tickSteps) {
                        val tickY = (1f - step) * h
                        drawLine(
                            color = tickColor,
                            start = Offset(w * 0.24f, tickY),
                            end = Offset(w * 0.76f, tickY),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                // 1.2 动态向上液态充能柱
                if (animatedVol > 0.005f) {
                    val fillTopY = ((1f - animatedVol) * h).coerceIn(0f, h)
                    val fillH = (h - fillTopY).coerceAtLeast(0f)

                    val fillGradient = Brush.verticalGradient(
                        colors = listOf(
                            animatedTierColor,
                            animatedTierColor.copy(alpha = 0.75f)
                        ),
                        startY = fillTopY,
                        endY = h
                    )

                    drawRoundRect(
                        brush = fillGradient,
                        topLeft = Offset(0f, fillTopY),
                        size = Size(w, fillH),
                        cornerRadius = capsuleCorner
                    )

                    // 1.3 液位顶端微晶亮线与拖拽柔光 (Meniscus Specular & Dynamic Halo)
                    if (fillTopY > 1.dp.toPx()) {
                        if (isDragging && expandProgress > 0.5f) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        animatedTierColor.copy(alpha = 0.38f * expandProgress),
                                        Color.Transparent
                                    ),
                                    center = Offset(w / 2f, fillTopY),
                                    radius = w * 1.1f
                                ),
                                radius = w * 1.1f,
                                center = Offset(w / 2f, fillTopY)
                            )
                        }

                        val glowAlpha = (if (isDragging) 0.95f else 0.75f) * (0.4f + 0.6f * expandProgress)
                        val meniscusBrush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.15f * glowAlpha),
                                Color.White.copy(alpha = glowAlpha),
                                Color.White.copy(alpha = 0.15f * glowAlpha)
                            )
                        )
                        val insetX = if (expandProgress > 0.5f) 2.dp.toPx() else 0.5.dp.toPx()
                        drawLine(
                            brush = meniscusBrush,
                            start = Offset(insetX, fillTopY),
                            end = Offset(w - insetX, fillTopY),
                            strokeWidth = (1.5.dp * (0.6f + 0.4f * expandProgress)).toPx()
                        )
                    }
                } else {
                    // 静音/零音量状态：在平时常驻胶囊状态下，底部呈现一枚精致的微红警示静音圆点
                    val muteDotAlpha = (1f - expandProgress * 0.7f).coerceIn(0f, 1f)
                    if (muteDotAlpha > 0.05f) {
                        val dotRadius = (w * 0.28f).coerceIn(2.5.dp.toPx(), 4.dp.toPx())
                        val dotCenterY = h - (w / 2f).coerceAtLeast(6.dp.toPx())
                        drawCircle(
                            color = Color(0xFFFF5252).copy(alpha = 0.85f * muteDotAlpha),
                            radius = dotRadius,
                            center = Offset(w / 2f, dotCenterY)
                        )
                    }
                }
            }

            // 2. 顶部百分比与底部图标 HUD 内容（展开时优雅淡入与缩放呈现）
            if (expandProgress > 0.02f) {
                // 顶部内嵌读数：高对比等宽数字，随液位升降反色自适应
                val isReadoutSubmerged = animatedVol >= 0.88f
                val targetReadoutColor = when {
                    isReadoutSubmerged -> Color.White
                    isMuted -> HollerTheme.colors.TextSecondary.copy(alpha = 0.6f)
                    isDragging -> HollerTheme.colors.TextPrimary
                    else -> HollerTheme.colors.TextSecondary
                }
                val animatedReadoutColor by animateColorAsState(
                    targetValue = targetReadoutColor,
                    animationSpec = tween(140),
                    label = "readoutColor"
                )

                Text(
                    text = "$currentPercent%",
                    color = animatedReadoutColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .graphicsLayer {
                            alpha = expandProgress
                            scaleX = 0.65f + 0.35f * expandProgress
                            scaleY = 0.65f + 0.35f * expandProgress
                        }
                )

                // 底部内嵌小喇叭图标：支持点击静音/恢复，随液位自适应反色
                val isIconSubmerged = animatedVol >= 0.16f
                val iconVector = when {
                    isMuted -> Icons.AutoMirrored.Rounded.VolumeOff
                    volume < 0.35f -> Icons.AutoMirrored.Rounded.VolumeMute
                    volume < 0.75f -> Icons.AutoMirrored.Rounded.VolumeDown
                    else -> Icons.AutoMirrored.Rounded.VolumeUp
                }

                val targetIconColor = when {
                    isMuted -> if (isDarkTheme) Color(0xFFFF5252) else Color(0xFFD32F2F)
                    isIconSubmerged -> HollerTheme.colors.OnAccent
                    else -> HollerTheme.colors.TextPrimary
                }
                val animatedIconColor by animateColorAsState(
                    targetValue = targetIconColor,
                    animationSpec = tween(140),
                    label = "iconColor"
                )

                Icon(
                    imageVector = iconVector,
                    contentDescription = if (isMuted) "恢复音量" else "静音",
                    tint = animatedIconColor,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .size(17.dp)
                        .graphicsLayer {
                            alpha = expandProgress
                            scaleX = 0.65f + 0.35f * expandProgress
                            scaleY = 0.65f + 0.35f * expandProgress
                        }
                )
            }
        }
    }
}
