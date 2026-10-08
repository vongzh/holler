package com.vongz.holler.ui.theme

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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Holler 色彩令牌。
 */
class HollerColors(
    val BgBase: Color,
    val Brand: Color,
    val BrandElevated: Color,
    val Accent: Color,
    val Danger: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val Stroke: Color,
    val AccentSoft: Color
)

val DarkHollerColors = HollerColors(
    BgBase = Color(0xFF0A0F14),
    Brand = Color(0xFF0A2A43),
    BrandElevated = Color(0xFF12314D),
    Accent = Color(0xFF21D4A5),
    Danger = Color(0xFFC8324B),
    TextPrimary = Color(0xFFF2F6F9),
    TextSecondary = Color(0xFF8FA3B5),
    Stroke = Color(0xFF1E3A52),
    AccentSoft = Color(0x3321D4A5)
)

val LightHollerColors = HollerColors(
    BgBase = Color(0xFFF5F7FA),
    Brand = Color(0xFFFFFFFF),
    BrandElevated = Color(0xFFE2E8F0),
    Accent = Color(0xFF00A985),
    Danger = Color(0xFFD32F2F),
    TextPrimary = Color(0xFF1A202C),
    TextSecondary = Color(0xFF718096),
    Stroke = Color(0xFFCBD5E0),
    AccentSoft = Color(0x3300A985)
)

val LocalHollerColors = staticCompositionLocalOf { DarkHollerColors }

/** 形状令牌：统一 R 角，Flyme Auto 的一致性要求 */
object HollerShapes {
    val Card = RoundedCornerShape(24.dp)
    val Button = RoundedCornerShape(20.dp)
    val Control = RoundedCornerShape(14.dp)
}

/** 间距令牌 */
object HollerSpacing {
    val Page = 24.dp
    val CardGap = 16.dp
    val MainButtonHeight = 88.dp
}

/** 字号令牌 */
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
    themeMode: Int = 0, // 0 = System, 1 = Light, 2 = Dark
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
                window.statusBarColor = colors.BgBase.toArgb()
                window.navigationBarColor = colors.BgBase.toArgb()
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
