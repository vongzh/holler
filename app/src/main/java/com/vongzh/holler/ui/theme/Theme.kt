package com.vongzh.holler.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class HollerColors(
    val BgBase: Color,
    val BgBaseTop: Color,              // 页面背景垂直渐变顶端：给玻璃面提供可透的明度梯度
    val Brand: Color,
    val BrandElevated: Color,
    val Accent: Color,
    val Danger: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val Stroke: Color,
    val AccentSoft: Color,
    val GlassSurface: Color,           // 玻璃面填充渐变顶（DOCK / 齿轮）
    val GlassSurfaceBottom: Color,     // 玻璃面填充渐变底（越往下越透）
    val GlassEdgeTop: Color,           // 玻璃面高光描边渐变顶（亮线）
    val GlassEdgeBottom: Color,        // 玻璃面高光描边渐变底（收敛，避免整圈亮线发闷）
    val AccentText: Color,             // 强调色「文本档」：浅色主题下把 Accent 压深，保证小字号可读
    val DangerText: Color,             // 警示色「文本档」：同上
)

val DarkHollerColors = HollerColors(
    BgBase = Color(0xFF0B1117),
    BgBaseTop = Color(0xFF0E151E),     // 极轻的顶部提亮，只给深色背景加一层纵深感
    Brand = Color(0xFF131D28),
    BrandElevated = Color(0xFF1D2B3A),
    Accent = Color(0xFF1AE5A3),        // 优雅智能翡翠青
    Danger = Color(0xFFFF5252),        // 警示珊瑚红
    TextPrimary = Color(0xFFF2F6FA),
    TextSecondary = Color(0xFF8B9DAE),
    Stroke = Color(0xFF233446),
    AccentSoft = Color(0x1F1AE5A3),    // 柔和微光背景
    GlassSurface = Color(0xFF131D28),  // 深色玻璃：均匀表面色（=Brand），与背景仅差一档亮度的沉稳 elevation
    GlassSurfaceBottom = Color(0xFF131D28),  // 上下同色：深色下面板靠轮廓而非亮度渐变分离
    GlassEdgeTop = Color(0xFF233446),  // 整圈冷色细描边（=Stroke），勾出边界但不出白亮线
    GlassEdgeBottom = Color(0xFF233446),
    AccentText = Color(0xFF1AE5A3),    // 深色底上青绿本身已 10.4:1，文本档与强调色同值
    DangerText = Color(0xFFFF5252),
)

// Flyme Auto 白昼座舱高级浅色调（避免纯白眩光与刺眼红色）
val LightHollerColors = HollerColors(
    // 背景整体压深 + 上亮下深渐变：白卡/白圆与背景明度差从 1.09:1 拉到 ~1.25:1，
    // 同时给底部玻璃 DOCK 提供「可透」的明度梯度，玻璃感真实而非贴纸。
    BgBase = Color(0xFFE2E9F1),
    BgBaseTop = Color(0xFFF7FAFC),
    Brand = Color(0xFFFFFFFF),
    BrandElevated = Color(0xFFDAE3EE), // 选中态/浮层面板底：比新背景更深一档，仍可分辨
    Accent = Color(0xFF00B578),        // 车规级智能翡翠绿（高对比、通透）
    Danger = Color(0xFFFF4D4F),        // 仅在取消/报警时出现的警示红
    TextPrimary = Color(0xFF161C24),
    TextSecondary = Color(0xFF5B6C80), // 比原 #6B7888 压深一档：浅灰底上小字号可读性回升
    Stroke = Color(0xFFC9D5E3),        // 描边加深：浅色下面元素边界不再靠想象
    AccentSoft = Color(0x1400B578),    // 柔和淡雅质感底色（约8%透明度）
    GlassSurface = Color(0x8CFFFFFF),  // 浅色玻璃：55% 白 → 26% 白，填充刻意压薄，靠边缘系统立形体
    GlassSurfaceBottom = Color(0x42FFFFFF),
    GlassEdgeTop = Color(0xFFFFFFFF),  // 顶部高光亮线 → 底部收敛
    GlassEdgeBottom = Color(0x4DFFFFFF),
    // 白卡上 #00B578 仅 2.66:1、#FF4D4F 仅 3.27:1，都不足以承载 11–15sp 小字号。
    // 文本档压深到 5.76 / 5.56（白卡），浅灰卡上 4.80 / 4.63，全部过 WCAG AA。
    // 图标、圆点、描边、填充等「非文本」仍用原 Accent / Danger 保品牌观感。
    AccentText = Color(0xFF00754C),
    DangerText = Color(0xFFC62B27),
)

val LocalHollerColors = staticCompositionLocalOf { DarkHollerColors }

object HollerShapes {
    val Card = RoundedCornerShape(24.dp)
    val Button = RoundedCornerShape(20.dp)
    val Control = RoundedCornerShape(14.dp)
    val Capsule = RoundedCornerShape(50)
}

object HollerSpacing {
    val Page = 24.dp
    val CardGap = 16.dp
    val MainButtonHeight = 88.dp
}

object HollerType {
    val Title = 28.sp
    val Body = 16.sp
    val Caption = 12.sp
}

private val DarkHollerColorScheme = darkColorScheme(
    primary = DarkHollerColors.Accent,
    onPrimary = Color(0xFF06231B),
    background = DarkHollerColors.BgBase,
    onBackground = DarkHollerColors.TextPrimary,
    surface = DarkHollerColors.Brand,
    onSurface = DarkHollerColors.TextPrimary,
    surfaceVariant = DarkHollerColors.BrandElevated,
    onSurfaceVariant = DarkHollerColors.TextSecondary,
    error = DarkHollerColors.Danger,
    outline = DarkHollerColors.Stroke,
)

private val LightHollerColorScheme = lightColorScheme(
    primary = LightHollerColors.Accent,
    onPrimary = Color.White,
    background = LightHollerColors.BgBase,
    onBackground = LightHollerColors.TextPrimary,
    surface = LightHollerColors.Brand,
    onSurface = LightHollerColors.TextPrimary,
    surfaceVariant = LightHollerColors.BrandElevated,
    onSurfaceVariant = LightHollerColors.TextSecondary,
    error = LightHollerColors.Danger,
    outline = LightHollerColors.Stroke,
)

private val HollerShapesSpec = Shapes(
    extraSmall = HollerShapes.Control,
    small = HollerShapes.Control,
    medium = HollerShapes.Button,
    large = HollerShapes.Card,
    extraLarge = HollerShapes.Card,
)

@Composable
fun HollerTheme(
    themeMode: Int = 0,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val colors = if (isDark) DarkHollerColors else LightHollerColors
    val materialColors = if (isDark) DarkHollerColorScheme else LightHollerColorScheme

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(LocalHollerColors provides colors) {
        MaterialTheme(
            colorScheme = materialColors,
            shapes = HollerShapesSpec,
            content = content,
        )
    }
}

object HollerTheme {
    val colors: HollerColors
        @Composable
        @ReadOnlyComposable
        get() = LocalHollerColors.current
}
