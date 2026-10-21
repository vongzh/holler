package com.vongzh.holler.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vongzh.holler.ui.theme.HollerTheme
import com.vongzh.holler.ui.theme.HollerShapes
import com.vongzh.holler.ui.theme.HollerType

/**
 * Flyme Auto 风格的通用控件库。
 *
 * 纪律（与 Theme.kt 令牌一致，不引入新视觉语言）：
 *   - 所有「面」用 Brand / BrandElevated 渐变，禁止大面积铺强调色
 *   - 强调色只做「点」：选中态、状态点、数值
 *   - 统一 R 角：卡片 24dp / 按钮 20dp / 控件 14dp
 *   - 动效克制：选中态用 spring，时长不超过 200ms
 *
 * 这些控件是「三页导航」的构建积木，被 MainActivity 复用。
 */

// ======================================================================
// 卡片容器
// ======================================================================

/**
 * 标准卡片：深海蓝渐变面 + 细描边 + 可选标题。
 *
 * @param elevated 是否使用浮起层（比 Brand 亮一档，用于强调卡片）
 */
@Composable
fun HollerCard(
    title: String? = null,
    subtitle: String? = null,
    elevated: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(HollerShapes.Card)
            .background(
                if (elevated) {
                    Brush.verticalGradient(
                        listOf(HollerTheme.colors.BrandElevated, HollerTheme.colors.Brand)
                    )
                } else {
                    androidx.compose.ui.graphics.SolidColor(HollerTheme.colors.Brand)
                }
            )
            .border(
                BorderStroke(1.dp, if (elevated) HollerTheme.colors.AccentSoft else HollerTheme.colors.Stroke),
                HollerShapes.Card,
            )
            .padding(18.dp),
    ) {
        if (title != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    color = HollerTheme.colors.TextPrimary,
                    fontSize = HollerType.Body,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle != null) {
                    Text(subtitle, color = HollerTheme.colors.TextSecondary, fontSize = HollerType.Caption)
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        content()
    }
}

// ======================================================================
// 切换开关
// ======================================================================

/**
 * 车规风格开关。
 *
 * 设计取舍：不用 Material Switch —— 它的拇指动画与配色偏手机感，
 * 车机上需要更大的触控面积与更克制的色彩。这里自绘「胶囊 + 滑块」。
 */
@Composable
fun HollerSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) HollerTheme.colors.Accent else HollerTheme.colors.Stroke.copy(alpha = 0.5f),
        label = "switchTrack",
    )
    val knobColor by animateColorAsState(
        targetValue = if (checked) HollerTheme.colors.OnAccent else HollerTheme.colors.TextSecondary,
        label = "switchKnob",
    )
    val knobOffset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 850f),
        label = "switchOffset",
    )

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(
                if (!enabled) trackColor.copy(alpha = 0.25f)
                else if (checked) trackColor.copy(alpha = 0.95f)
                else HollerTheme.colors.BrandElevated
            )
            .border(
                1.dp,
                if (checked) HollerTheme.colors.Accent.copy(alpha = 0.4f) else HollerTheme.colors.Stroke.copy(alpha = 0.5f),
                RoundedCornerShape(15.dp)
            )
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { onCheckedChange(!checked) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 3.dp)
                .offset(x = (22 * knobOffset).dp)
                .shadow(3.dp, CircleShape, spotColor = if (checked) HollerTheme.colors.Accent.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.35f))
                .size(24.dp)
                .clip(CircleShape)
                .background(knobColor)
        )
    }
}

// ======================================================================
// 滑块
// ======================================================================

/**
 * 横向滑块。
 *
 * 车机上手指精度低，因此：
 *   - 轨道高 8dp（比手机粗），可视性好
 *   - 触控区高 40dp，容错大
 *   - 数值实时显示在右侧，不用拖动时浮层
 *
 * @param value 当前值
 * @param range 取值范围
 * @param steps 刻度数（0 表示连续）；每个刻度点会画出小圆点
 */
@Composable
fun HollerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueLabel: String = "",
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .pointerInput(range, steps) {
                        detectHorizontalDragGestures(
                            onDragStart = { off ->
                                onValueChange(fracToValue(off.x, size.width, range, steps))
                            },
                            onDragEnd = {
                                onValueChangeFinished?.invoke()
                            },
                            onDragCancel = {
                                onValueChangeFinished?.invoke()
                            }
                        ) { change, _ ->
                            onValueChange(fracToValue(change.position.x, size.width, range, steps))
                        }
                    }
                    .pointerInput(range, steps) {
                        detectTapGestures { off ->
                            onValueChange(fracToValue(off.x, size.width, range, steps))
                            onValueChangeFinished?.invoke()
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                // 轨道底 (沉浸式暗轨)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(HollerTheme.colors.BrandElevated)
                        .border(0.5.dp, HollerTheme.colors.Stroke.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                )
                // 已选段 (渐变流光)
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    HollerTheme.colors.Accent.copy(alpha = 0.6f),
                                    HollerTheme.colors.Accent
                                )
                            )
                        )
                )
                // 刻度点
                if (steps > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(steps + 1) {
                            Box(
                                Modifier
                                    .size(3.dp)
                                    .clip(CircleShape)
                                    .background(HollerTheme.colors.BgBase.copy(alpha = 0.7f))
                            )
                        }
                    }
                }
                // 拇指 (激光微雕立体旋钮)
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(40.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        Modifier
                            .shadow(5.dp, CircleShape, spotColor = HollerTheme.colors.Accent.copy(alpha = 0.5f))
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(HollerTheme.colors.TextPrimary)
                            .border(2.5.dp, HollerTheme.colors.Accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(HollerTheme.colors.Accent)
                        )
                    }
                }
            }
            if (valueLabel.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                Text(
                    valueLabel,
                    color = HollerTheme.colors.AccentText,
                    fontSize = HollerType.Body,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** 像素坐标 → 取值（steps>0 时吸附到刻度）。 */
private fun fracToValue(
    x: Float,
    width: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
): Float {
    val f = (x / width.toFloat()).coerceIn(0f, 1f)
    val span = range.endInclusive - range.start
    if (steps <= 0) return range.start + span * f
    val idx = Math.round(f * steps).coerceIn(0, steps)
    return range.start + span * (idx.toFloat() / steps)
}

// ======================================================================
// 选项芯片
// ======================================================================

/**
 * 可选中的胶囊按钮，用于变声效果这类「多选一」。
 * 选中：青绿描边 + 半透明青绿底；未选：细描边。
 */
@Composable
fun HollerChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val border by animateColorAsState(
        targetValue = if (selected) HollerTheme.colors.Accent else HollerTheme.colors.Stroke,
        label = "chipBorder",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.97f,
        label = "chipScale",
    )
    Box(
        modifier = modifier
            .scale(scale)
            .clip(HollerShapes.Control)
            .background(if (selected) HollerTheme.colors.AccentSoft else Color.Transparent)
            .border(BorderStroke(1.dp, border), HollerShapes.Control)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) HollerTheme.colors.AccentText else HollerTheme.colors.TextSecondary,
            fontSize = HollerType.Body,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ======================================================================
// 行动按钮
// ======================================================================

/**
 * 主行动按钮（青绿实心）与次行动按钮（描边）。
 *
 * @param primary true = 实心青绿；false = 描边
 */
@Composable
fun HollerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val accent = if (danger) HollerTheme.colors.Danger else HollerTheme.colors.Accent
    val bg = when {
        !enabled -> HollerTheme.colors.Stroke.copy(alpha = 0.3f)
        primary -> accent
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> HollerTheme.colors.TextSecondary.copy(alpha = 0.5f)
        primary -> if (danger) Color.White else HollerTheme.colors.OnAccent
        danger -> HollerTheme.colors.DangerText
        else -> HollerTheme.colors.TextPrimary
    }
    Box(
        modifier = modifier
            .clip(HollerShapes.Button)
            .background(bg)
            .then(
                if (primary) Modifier
                else Modifier.border(BorderStroke(1.dp, accent.copy(alpha = 0.7f)), HollerShapes.Button)
            )
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { onClick() }
            }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = HollerType.Body, fontWeight = FontWeight.SemiBold)
    }
}

// ======================================================================
// 标签值行
// ======================================================================

/** 左标签右值的一行，用于设置项与诊断信息。 */
@Composable
fun LabelValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = HollerTheme.colors.TextPrimary,
    mono: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = HollerTheme.colors.TextSecondary, fontSize = HollerType.Caption)
        Text(
            value,
            color = valueColor,
            fontSize = HollerType.Caption,
            fontWeight = FontWeight.Medium,
            fontFamily = if (mono) androidx.compose.ui.text.font.FontFamily.Monospace else null,
        )
    }
}

// ======================================================================
// 空状态
// ======================================================================

/** 空状态占位。 */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = HollerTheme.colors.TextSecondary, fontSize = HollerType.Body)
    }
}
