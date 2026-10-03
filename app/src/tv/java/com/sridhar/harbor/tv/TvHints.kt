package com.sridhar.harbor.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.R
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.delay

/** Remote tips on/off (TV settings → About). */
object TvHintPrefs {
    fun on(ctx: android.content.Context) = ctx.getSharedPreferences("harbor_tv", android.content.Context.MODE_PRIVATE).getBoolean("hints", true)
    fun set(ctx: android.content.Context, v: Boolean) = ctx.getSharedPreferences("harbor_tv", android.content.Context.MODE_PRIVATE).edit().putBoolean("hints", v).apply()
}

/** Which remote buttons do what on each screen: (key glyph, string). */
private fun hintsFor(d: TvDest): List<Pair<String, Int>> = when (d) {
    TvDest.Home -> listOf("◀" to R.string.hint_menu, "OK" to R.string.hint_play, "▼" to R.string.hint_more_rows, "⏺" to R.string.hint_hold_ok)
    TvDest.Movies, TvDest.Shows, TvDest.Adult -> listOf("◀" to R.string.hint_menu, "OK" to R.string.hint_open, "▲▼" to R.string.hint_browse)
    is TvDest.Detail -> listOf("OK" to R.string.hint_play, "▼" to R.string.hint_episodes, "↩" to R.string.hint_back)
    TvDest.Live -> listOf("OK" to R.string.hint_watch, "▲▼" to R.string.hint_channels, "◀" to R.string.hint_menu)
    TvDest.Music, is TvDest.MusicCollection -> listOf("OK" to R.string.hint_play, "◀" to R.string.hint_menu)
    TvDest.NowPlaying -> listOf("OK" to R.string.hint_pause, "◀▶" to R.string.hint_skip, "↩" to R.string.hint_back)
    TvDest.Search -> listOf("📱" to R.string.hint_type_phone, "OK" to R.string.hint_open)
    TvDest.Settings -> listOf("▶" to R.string.hint_options, "◀" to R.string.hint_categories, "OK" to R.string.hint_change)
    TvDest.Connect -> listOf("📷" to R.string.hint_scan_qr)
}

/**
 * Bottom hint bar: fades in after a few idle seconds on a screen, hides on the next key press ([keyTick] changes).
 * Re-arms when the screen changes; turned off in Settings → About.
 */
@Composable
fun TvHints(dest: TvDest, keyTick: Int, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    if (!TvHintPrefs.on(ctx)) return
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(dest, keyTick) {
        show = false
        delay(3500)
        show = true
        delay(7000)
        show = false
    }
    AnimatedVisibility(show, modifier, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
        Row(Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .78f)).padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            hintsFor(dest).forEach { (key, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(key, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(label), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Text("· " + stringResource(R.string.hint_turn_off), color = Harbor.TextDim, fontSize = 12.sp)
        }
    }
}
