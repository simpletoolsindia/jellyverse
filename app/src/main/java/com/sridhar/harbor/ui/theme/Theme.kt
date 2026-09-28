package com.sridhar.harbor.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Hand-picked neutral scale (Radix Colors steps 1–4, 11, 12). Each accent is paired with the neutral that carries
 * a hint of its hue – blue↔slate, red↔gray, green↔sage, gold↔sand, violet↔mauve – so backgrounds, cards and text
 * feel part of the theme instead of one generic grey.
 */
data class Neutrals(val bg: Color, val surface: Color, val high: Color, val highest: Color, val dim: Color, val fg: Color)

private val slateDark = Neutrals(Color(0xFF111113), Color(0xFF18191B), Color(0xFF212225), Color(0xFF272A2D), Color(0xFFB0B4BA), Color(0xFFEDEEF0))
private val slateLight = Neutrals(Color(0xFFF9F9FB), Color(0xFFFCFCFD), Color(0xFFF0F0F3), Color(0xFFE8E8EC), Color(0xFF60646C), Color(0xFF1C2024))
private val grayDark = Neutrals(Color(0xFF111111), Color(0xFF191919), Color(0xFF222222), Color(0xFF2A2A2A), Color(0xFFB4B4B4), Color(0xFFEEEEEE))
private val grayLight = Neutrals(Color(0xFFF9F9F9), Color(0xFFFCFCFC), Color(0xFFF0F0F0), Color(0xFFE8E8E8), Color(0xFF646464), Color(0xFF202020))
private val sageDark = Neutrals(Color(0xFF101211), Color(0xFF171918), Color(0xFF202221), Color(0xFF272A29), Color(0xFFADB5B2), Color(0xFFECEEED))
private val sageLight = Neutrals(Color(0xFFF7F9F8), Color(0xFFFBFDFC), Color(0xFFEEF1F0), Color(0xFFE6E9E8), Color(0xFF5F6563), Color(0xFF1A211E))
private val sandDark = Neutrals(Color(0xFF111110), Color(0xFF191918), Color(0xFF222221), Color(0xFF2A2A28), Color(0xFFB5B3AD), Color(0xFFEEEEEC))
private val sandLight = Neutrals(Color(0xFFF9F9F8), Color(0xFFFDFDFC), Color(0xFFF1F0EF), Color(0xFFE9E8E6), Color(0xFF63635E), Color(0xFF21201C))
private val mauveDark = Neutrals(Color(0xFF121113), Color(0xFF1A191B), Color(0xFF232225), Color(0xFF2B292D), Color(0xFFB5B2BC), Color(0xFFEEEEF0))
private val mauveLight = Neutrals(Color(0xFFFAF9FB), Color(0xFFFDFCFD), Color(0xFFF2EFF3), Color(0xFFEAE7EC), Color(0xFF65636D), Color(0xFF211F26))

/**
 * Colour themes. Names are our own – no streaming-service branding. Accents were chosen so white text on a
 * button stays readable (≥ 3:1, WCAG large/bold text) and links stay ≥ 4.5:1 on their background.
 */
enum class Skin(
    val id: String, val title: String, val primary: Color, val deep: Color, val soft: Color, val info: Color, val home: HomeStyle,
    val darkN: Neutrals, val lightN: Neutrals,
) {
    BlueBird("blue", "Blue Bird", Color(0xFF1F80E0), Color(0xFF1766C8), Color(0xFF8CC0F5), Color(0xFF06B2FF), HomeStyle.Spotlight, slateDark, slateLight),
    RedBlaster("red", "Red Blaster", Color(0xFFE50914), Color(0xFFB20710), Color(0xFFFF6B72), Color(0xFFFF4D57), HomeStyle.Billboard, grayDark, grayLight),
    EmeraldWave("emerald", "Emerald Wave", Color(0xFF059669), Color(0xFF047857), Color(0xFF6EE7B7), Color(0xFF34D399), HomeStyle.Spotlight, sageDark, sageLight),
    SunsetGold("gold", "Sunset Gold", Color(0xFFD97706), Color(0xFFB45309), Color(0xFFFCD34D), Color(0xFFFBBF24), HomeStyle.Billboard, sandDark, sandLight),
    VioletDream("violet", "Violet Dream", Color(0xFF6E56CF), Color(0xFF5B45B8), Color(0xFFBAA7FF), Color(0xFF9E8CFC), HomeStyle.Spotlight, mauveDark, mauveLight),
}

enum class ThemeMode(val id: String) { System("system"), Dark("dark"), Light("light") }

/** Home layout: a poster carousel with neighbours peeking, or one full-bleed billboard with a numbered Top 10. */
enum class HomeStyle(val id: String) { Spotlight("spotlight"), Billboard("billboard") }

/** Optional Home sections the user can hide. */
enum class HomeSection(val id: String) { Shortcuts("shortcuts"), Continue("continue"), NextUp("nextup"), Top10("top10"), Latest("latest") }

data class Look(
    val skin: Skin = Skin.BlueBird,
    val mode: ThemeMode = ThemeMode.System,
    /** null = the skin's own layout. */
    val homeStyle: HomeStyle? = null,
    val hidden: Set<HomeSection> = emptySet(),
) {
    val home: HomeStyle get() = homeStyle ?: skin.home
    fun shows(s: HomeSection) = s !in hidden
}

/** Surface + text colours for one brightness. */
data class Surfaces(val ink: Color, val surface: Color, val high: Color, val highest: Color, val fg: Color, val dim: Color, val line: Color, val dark: Boolean)

private fun Neutrals.toSurfaces(dark: Boolean) = Surfaces(bg, surface, high, highest, fg, dim, if (dark) Color.White else Color.Black, dark)

/**
 * The chosen look, persisted, and read by every screen through [Harbor]. Backed by snapshot state, so a change
 * recomposes and redraws the whole app live.
 */
object Looks {
    private var prefs: android.content.SharedPreferences? = null
    var look by mutableStateOf(Look()); private set
    internal var systemDark by mutableStateOf(true)
    /** >0 while an always-dark screen (player, Now Playing) is showing. */
    private var forcedDark by mutableIntStateOf(0)

    fun init(context: Context, isTv: Boolean) {
        val p = context.getSharedPreferences("harbor_look", Context.MODE_PRIVATE).also { prefs = it }
        look = Look(
            skin = Skin.entries.firstOrNull { it.id == p.getString("skin", null) } ?: Skin.BlueBird,
            // TVs default to dark: most report "light" and a white UI glares in a dark room.
            mode = ThemeMode.entries.firstOrNull { it.id == p.getString("mode", null) } ?: if (isTv) ThemeMode.Dark else ThemeMode.System,
            homeStyle = HomeStyle.entries.firstOrNull { it.id == p.getString("home", null) },
            hidden = p.getStringSet("hidden", emptySet()).orEmpty().mapNotNull { id -> HomeSection.entries.firstOrNull { it.id == id } }.toSet(),
        )
    }

    fun update(f: (Look) -> Look) {
        val n = f(look); look = n
        prefs?.edit()?.putString("skin", n.skin.id)?.putString("mode", n.mode.id)?.putString("home", n.homeStyle?.id)
            ?.putStringSet("hidden", n.hidden.map { it.id }.toSet())?.apply()
    }

    val isDark: Boolean get() = forcedDark > 0 || when (look.mode) { ThemeMode.Dark -> true; ThemeMode.Light -> false; ThemeMode.System -> systemDark }
    val surfaces: Surfaces get() = if (isDark) look.skin.darkN.toSurfaces(true) else look.skin.lightN.toSurfaces(false)

    internal fun pushDark() { forcedDark++ }
    internal fun popDark() { forcedDark = (forcedDark - 1).coerceAtLeast(0) }
}

/**
 * Design tokens. Token names are historical (Violet = primary, Coral = deep primary); they follow the chosen
 * [Skin] and light/dark mode, so every screen re-skins from here.
 */
object Harbor {
    val Ink: Color get() = Looks.surfaces.ink                  // app background
    val Surface: Color get() = Looks.surfaces.surface          // cards, sheets
    val SurfaceHigh: Color get() = Looks.surfaces.high         // raised cards, chips
    val Outline: Color get() = Looks.surfaces.line.copy(alpha = 0.08f)
    /** Text / icons on the app background (white in dark mode, near-black in light). */
    val Fg: Color get() = Looks.surfaces.fg
    /** Hairline fills and borders on the background – white in dark mode, black in light – at [alpha]. */
    fun line(alpha: Float): Color = Looks.surfaces.line.copy(alpha = alpha)
    val Violet: Color get() = Looks.look.skin.primary          // primary – CTAs, selection
    val VioletSoft: Color get() = if (Looks.isDark) Looks.look.skin.soft else Looks.look.skin.deep   // links, secondary accents
    val Coral: Color get() = Looks.look.skin.deep               // deep primary – gradient end, emphasis pills
    val Mint = Color(0xFF3CD771)         // success / watched
    val Sky: Color get() = if (Looks.isDark) Looks.look.skin.info else Looks.look.skin.deep       // info, focus rings
    val Amber = Color(0xFFF5B83D)        // warnings, ratings
    val Rose = Color(0xFFE8505B)         // destructive, errors
    val TextDim: Color get() = Looks.surfaces.dim              // secondary text

    val accent: Brush get() = Brush.linearGradient(listOf(Violet, Coral))
    val accentH: Brush get() = Brush.horizontalGradient(listOf(Violet, Coral))
    fun scrimBottom(to: Color = Ink) = Brush.verticalGradient(0f to Color.Transparent, 0.55f to to.copy(alpha = 0.6f), 1f to to)
}

private fun Color.over(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}

private fun scheme(): androidx.compose.material3.ColorScheme {
    val s = Looks.surfaces; val skin = Looks.look.skin
    return if (s.dark) darkColorScheme(
        primary = skin.primary, onPrimary = Color.White,
        primaryContainer = skin.deep.copy(alpha = 0.35f).over(s.ink), onPrimaryContainer = skin.soft,
        secondary = skin.info, onSecondary = Color.White,
        secondaryContainer = skin.deep.copy(alpha = 0.3f).over(s.ink), onSecondaryContainer = skin.soft,
        tertiary = Harbor.Mint,
        background = s.ink, onBackground = s.fg, surface = s.ink, onSurface = s.fg,
        surfaceVariant = s.high, onSurfaceVariant = s.dim,
        surfaceContainerLowest = s.ink, surfaceContainerLow = s.surface, surfaceContainer = s.surface,
        surfaceContainerHigh = s.high, surfaceContainerHighest = s.highest,
        outline = Color(0x33FFFFFF), outlineVariant = Color(0x14FFFFFF), error = Harbor.Rose,
    ) else lightColorScheme(
        primary = skin.primary, onPrimary = Color.White,
        primaryContainer = skin.primary.copy(alpha = 0.14f).over(s.surface), onPrimaryContainer = skin.deep,
        secondary = skin.deep, onSecondary = Color.White,
        secondaryContainer = skin.primary.copy(alpha = 0.12f).over(s.surface), onSecondaryContainer = skin.deep,
        tertiary = Color(0xFF16A34A),
        background = s.ink, onBackground = s.fg, surface = s.ink, onSurface = s.fg,
        surfaceVariant = s.high, onSurfaceVariant = s.dim,
        surfaceContainerLowest = s.surface, surfaceContainerLow = s.surface, surfaceContainer = s.surface,
        surfaceContainerHigh = s.high, surfaceContainerHighest = s.highest,
        outline = Color(0x33000000), outlineVariant = Color(0x14000000), error = Color(0xFFD32F3C),
    )
}

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
    val sysDark = isSystemInDarkTheme()
    SideEffect { Looks.systemDark = sysDark }
    val scheme = scheme()
    // Status / navigation bar icons follow the mode (dark icons on a light app).
    val view = androidx.compose.ui.platform.LocalView.current
    val dark = Looks.isDark
    if (!view.isInEditMode) SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Text outside a Surface would otherwise default to black.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides scheme.onBackground, content = content,
        )
    }
}

/** Screens that stay dark whatever the mode – video and full-screen artwork (Now Playing). */
@Composable
fun ForceDark(content: @Composable () -> Unit) {
    DisposableEffect(Unit) { Looks.pushDark(); onDispose { Looks.popDark() } }
    content()
}
