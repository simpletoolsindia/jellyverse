package com.sridhar.harbor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Hotstar-inspired palette: near-black canvas, slate cards, one confident blue.
 * Token names are historical (Violet = primary, Coral = deep primary) so every screen re-skins from here.
 */
object Harbor {
    val Ink = Color(0xFF0F1014)          // app background (Hotstar "Woodsmoke")
    val Surface = Color(0xFF16181F)      // cards, sheets
    val SurfaceHigh = Color(0xFF1F222A)  // raised cards, chips
    val Outline = Color(0x14FFFFFF)
    val Violet = Color(0xFF1F80E0)       // primary blue – CTAs, selection
    val VioletSoft = Color(0xFF8CC0F5)   // links, secondary accents on dark
    val Coral = Color(0xFF1766C8)        // deep blue – gradient end, emphasis pills
    val Mint = Color(0xFF3CD771)         // success / watched
    val Sky = Color(0xFF06B2FF)          // brand light blue – info
    val Amber = Color(0xFFF5B83D)        // warnings, ratings
    val Rose = Color(0xFFE8505B)         // destructive, errors
    val TextDim = Color(0xFF8F98B2)      // secondary text

    val accent = Brush.linearGradient(listOf(Violet, Coral))
    val accentH = Brush.horizontalGradient(listOf(Violet, Coral))
    fun scrimBottom(to: Color = Ink) = Brush.verticalGradient(0f to Color.Transparent, 0.55f to to.copy(alpha = 0.6f), 1f to to)
}

private val scheme = darkColorScheme(
    primary = Harbor.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0E2D52),
    onPrimaryContainer = Harbor.VioletSoft,
    secondary = Harbor.Sky,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF12304A),
    onSecondaryContainer = Color(0xFFBFE6FF),
    tertiary = Harbor.Mint,
    background = Harbor.Ink,
    onBackground = Color(0xFFF1F3F7),
    surface = Harbor.Ink,
    onSurface = Color(0xFFF1F3F7),
    surfaceVariant = Harbor.SurfaceHigh,
    onSurfaceVariant = Harbor.TextDim,
    surfaceContainerLowest = Color(0xFF0A0B0E),
    surfaceContainerLow = Color(0xFF131419),
    surfaceContainer = Harbor.Surface,
    surfaceContainerHigh = Harbor.SurfaceHigh,
    surfaceContainerHighest = Color(0xFF282B34),
    outline = Color(0x33FFFFFF),
    outlineVariant = Harbor.Outline,
    error = Harbor.Rose,
)

private val base = Typography()
private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.6.sp),
)

@Composable
fun HarborTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Text outside a Surface would otherwise default to black.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides scheme.onBackground, content = content,
        )
    }
}
