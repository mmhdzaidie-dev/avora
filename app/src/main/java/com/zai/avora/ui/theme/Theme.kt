package com.zai.avora.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.zai.avora.data.settings.ThemeMode

@Immutable
data class AvoraColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val iconPrimary: Color,
    val isDark: Boolean,
    val accent: Color = textPrimary
)

val DarkAvoraColors = AvoraColors(
    background = PureBlack,
    surface = DarkSurface,
    card = DarkCard,
    border = DarkBorder,
    textPrimary = PureWhite,
    textSecondary = SubtleGray,
    textMuted = MutedText,
    iconPrimary = PureWhite,
    isDark = true,
    accent = PureWhite
)

val LightAvoraColors = AvoraColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFAFAFA),
    card = Color(0xFFF4F4F5),
    border = Color(0xFFE4E4E7),
    textPrimary = Color(0xFF09090B),
    textSecondary = Color(0xFF71717A),
    textMuted = Color(0xFFA1A1AA),
    iconPrimary = Color(0xFF09090B),
    isDark = false,
    accent = Color(0xFF09090B)
)

val LocalAvoraColors = staticCompositionLocalOf { DarkAvoraColors }

object AvoraAppTheme {
    val colors: AvoraColors
        @Composable
        get() = LocalAvoraColors.current
}

private val DarkColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = PureBlack,
    secondary = AccentWhite,
    onSecondary = PureBlack,
    background = PureBlack,
    onBackground = PureWhite,
    surface = DarkSurface,
    onSurface = PureWhite,
    surfaceVariant = DarkCard,
    onSurfaceVariant = MutedText,
    outline = DarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = PureBlack,
    onPrimary = PureWhite,
    secondary = Color(0xFF27272A),
    onSecondary = PureWhite,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF09090B),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF09090B),
    surfaceVariant = Color(0xFFF4F4F5),
    onSurfaceVariant = Color(0xFF71717A),
    outline = Color(0xFFE4E4E7)
)

private const val REFERENCE_WIDTH_DP = 411f
private const val COMPACT_FACTOR = 0.9f
private const val MIN_UI_SCALE = 0.7f
private const val MAX_UI_SCALE = 1.15f

@Composable
private fun AdaptiveDensity(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val smallestWidthDp = LocalConfiguration.current.smallestScreenWidthDp.toFloat()
    val scale = ((smallestWidthDp / REFERENCE_WIDTH_DP) * COMPACT_FACTOR)
        .coerceIn(MIN_UI_SCALE, MAX_UI_SCALE)
    val scaled = Density(
        density = density.density * scale,
        fontScale = density.fontScale
    )
    CompositionLocalProvider(LocalDensity provides scaled, content = content)
}

@Composable
fun AvoraTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val avoraColors = if (isDark) DarkAvoraColors else LightAvoraColors

    AdaptiveDensity {
        CompositionLocalProvider(LocalAvoraColors provides avoraColors) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = Typography,
                content = content
            )
        }
    }
}
