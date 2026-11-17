package com.qw.player.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * TV 配色。
 *
 * 客厅场景几乎总是暗环境，参考官方 TV 设计指南：深色底 + 高对比内容 + 单一强调色。
 * 强调色沿用 demo 的靛蓝主色，保证两套界面是同一个品牌。
 */
private val Indigo = Color(0xFF8E9BFF)
private val IndigoDeep = Color(0xFF2B3487)
private val OnIndigo = Color(0xFF0B1250)
private val Ink = Color(0xFF101014)
private val InkElevated = Color(0xFF1A1A21)
private val InkCard = Color(0xFF24242D)
private val Mist = Color(0xFFF2F1F8)
private val MistDim = Color(0xFFA8A6B4)
private val Hairline = Color(0xFF3A3947)

@Immutable
data class TvColors(
    val primary: Color = Indigo,
    val onPrimary: Color = OnIndigo,
    val primaryContainer: Color = IndigoDeep,
    val onPrimaryContainer: Color = Color(0xFFDDE1FF),
    val background: Color = Ink,
    val surface: Color = InkElevated,
    val surfaceVariant: Color = InkCard,
    val onSurface: Color = Mist,
    val onSurfaceVariant: Color = MistDim,
    val border: Color = Hairline,
    val error: Color = Color(0xFFFFB4AB)
)

private fun tvColorScheme(c: TvColors): ColorScheme = darkColorScheme(
    primary = c.primary,
    onPrimary = c.onPrimary,
    primaryContainer = c.primaryContainer,
    onPrimaryContainer = c.onPrimaryContainer,
    inversePrimary = c.primary,
    secondary = Color(0xFFC4C6DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434659),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFFFB1C8),
    onTertiary = Color(0xFF5A1136),
    tertiaryContainer = Color(0xFF7A294E),
    onTertiaryContainer = Color(0xFFFFD8E5),
    background = c.background,
    onBackground = c.onSurface,
    surface = c.surface,
    onSurface = c.onSurface,
    surfaceVariant = c.surfaceVariant,
    onSurfaceVariant = c.onSurfaceVariant,
    surfaceTint = c.primary,
    inverseSurface = c.onSurface,
    inverseOnSurface = c.background,
    error = c.error,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    border = c.border,
    borderVariant = c.border,
    scrim = Color.Black
)

@Composable
fun QPlayerTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = tvColorScheme(TvColors()), content = content)
}
