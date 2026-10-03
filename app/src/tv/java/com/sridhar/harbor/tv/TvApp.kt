package com.sridhar.harbor.tv

import androidx.compose.animation.scaleIn
import androidx.compose.material.icons.rounded.MusicNote
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.ui.theme.Harbor

sealed interface TvDest {
    data object Search : TvDest
    data object Home : TvDest
    data object Movies : TvDest
    data object Shows : TvDest
    data object Live : TvDest
    data object Music : TvDest
    data class MusicCollection(val kind: String, val id: String) : TvDest
    data object NowPlaying : TvDest
    data object Settings : TvDest
    data object Adult : TvDest
    data object Connect : TvDest
    data class Detail(val id: String) : TvDest
}

private data class RailItem(val dest: TvDest, @androidx.annotation.StringRes val labelRes: Int, val icon: ImageVector) {
    val label: String get() = com.sridhar.harbor.L10n.s(labelRes)
}
private val rail = listOf(
    RailItem(TvDest.Search, com.sridhar.harbor.R.string.tv_search, Icons.Rounded.Search),
    RailItem(TvDest.Home, com.sridhar.harbor.R.string.tv_home, Icons.Rounded.Home),
    RailItem(TvDest.Movies, com.sridhar.harbor.R.string.tv_movies, Icons.Rounded.Movie),
    RailItem(TvDest.Shows, com.sridhar.harbor.R.string.tv_shows, Icons.Rounded.Tv),
    RailItem(TvDest.Live, com.sridhar.harbor.R.string.tv_live, Icons.Rounded.LiveTv),
    RailItem(TvDest.Music, com.sridhar.harbor.R.string.tv_music, Icons.Rounded.MusicNote),
    RailItem(TvDest.Connect, com.sridhar.harbor.R.string.tv_connect_phone, Icons.Rounded.QrCode2),
    RailItem(TvDest.Adult, com.sridhar.harbor.R.string.adult_menu, Icons.Rounded.Lock),
    RailItem(TvDest.Settings, com.sridhar.harbor.R.string.tv_settings, Icons.Rounded.Settings),
)

@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun TvApp() {
    val stack = remember { mutableStateListOf<TvDest>(TvDest.Home) }
    val current = stack.last()
    val go: (TvDest) -> Unit = { d -> if (d is TvDest.Detail || d is TvDest.MusicCollection || d is TvDest.NowPlaying) stack.add(d) else { stack.clear(); stack.add(d) } }
    // "Home" from the phone remote.
    val navContainer = com.sridhar.harbor.ui.components.LocalContainer.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        navContainer.navRequests.collect {
            if (it == "home") { stack.clear(); stack.add(TvDest.Home) }
            if (it.startsWith("tvitem:")) { stack.clear(); stack.add(TvDest.Home); stack.add(TvDest.Detail(it.removePrefix("tvitem:"))) }
        }
    }
    BackHandler(enabled = stack.size > 1 || current != TvDest.Home) { if (stack.size > 1) stack.removeAt(stack.lastIndex) else { stack.clear(); stack.add(TvDest.Home) } }
    var railFocused by remember { mutableStateOf(false) }
    val railFocus = remember { mutableMapOf<TvDest, androidx.compose.ui.focus.FocusRequester>() }
    val railWidth by animateDpAsState(if (railFocused) 230.dp else 84.dp, spring(dampingRatio = 0.85f), label = "rail")

    // Any key press counts as activity (hides the remote tips; they come back after a few idle seconds).
    var keyTick by remember { mutableStateOf(0) }
    Box(Modifier.fillMaxSize().background(Harbor.Ink).onPreviewKeyEvent { if (it.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) keyTick++; false }) {
        Box(Modifier.fillMaxSize().padding(start = 84.dp)) {
            val reduced = com.sridhar.harbor.ui.components.reducedMotion()
            AnimatedContent(current, transitionSpec = {
                // Fade-through: the old screen is gone before the new one is mostly drawn, so the TV never renders
                // two full screens for long. Low-end: a quick plain fade.
                if (reduced) fadeIn(tween(140)) togetherWith fadeOut(tween(90))
                else (fadeIn(tween(220, delayMillis = 70, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) +
                    scaleIn(tween(260, delayMillis = 70, easing = androidx.compose.animation.core.LinearOutSlowInEasing), initialScale = 0.97f)) togetherWith
                    fadeOut(tween(90, easing = androidx.compose.animation.core.FastOutLinearInEasing))
            }, label = "tvdest") { d ->
                when (d) {
                    TvDest.Home -> TvHome(onOpen = { go(TvDest.Detail(it)) })
                    TvDest.Movies -> TvGrid("movies", onOpen = { go(TvDest.Detail(it)) })
                    TvDest.Shows -> TvGrid("tvshows", onOpen = { go(TvDest.Detail(it)) })
                    TvDest.Live -> TvLive()
                    TvDest.Music -> TvMusicHome(onCollection = { k, id -> go(TvDest.MusicCollection(k, id)) }, onNowPlaying = { go(TvDest.NowPlaying) })
                    is TvDest.MusicCollection -> TvMusicCollection(d.kind, d.id, onNowPlaying = { go(TvDest.NowPlaying) })
                    TvDest.NowPlaying -> TvNowPlaying()
                    TvDest.Search -> TvSearch(onOpen = { go(TvDest.Detail(it)) })
                    TvDest.Settings -> TvSettings()
                    TvDest.Connect -> TvConnectPhone()
                    TvDest.Adult -> TvProtected(onOpen = { go(TvDest.Detail(it)) }, onCancel = { stack.clear(); stack.add(TvDest.Home) })
                    is TvDest.Detail -> TvDetail(d.id, onOpen = { go(TvDest.Detail(it)) })
                }
            }
        }
        // While the menu is open, dim the page behind it so the two never read as one.
        val scrim by androidx.compose.animation.core.animateFloatAsState(if (railFocused) 0.72f else 0f, label = "railScrim")
        if (scrim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))
        // Rail: collapsed icons; expands with labels while it has focus (Hotstar/Google TV style).
        if (current !is TvDest.Detail && current !is TvDest.NowPlaying && current !is TvDest.MusicCollection) Column(
            Modifier.fillMaxHeight().width(railWidth)
                // Open: a solid panel with a soft edge; closed: the light fade over content, as before.
                .background(if (railFocused) Brush.horizontalGradient(listOf(Harbor.Ink, Harbor.Ink, Harbor.Surface))
                    else Brush.horizontalGradient(listOf(Harbor.Ink.copy(alpha = 0.9f), Harbor.Ink.copy(alpha = 0f))))
                .onFocusChanged { railFocused = it.hasFocus }
                // Entering the menu lands on the screen you're on, not whichever entry happens to be nearest.
                .focusProperties { enter = { railFocus[current] ?: androidx.compose.ui.focus.FocusRequester.Default } }
                .focusGroup()
                // Fits 8 entries on a 540dp-tall TV screen; scrolls (keeping the focused entry visible) if ever taller.
                .verticalScroll(rememberScrollState())
                // Left inside the sidebar has nowhere to go – swallow it so focus never jumps to the last entry.
                .onPreviewKeyEvent { it.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && it.key == androidx.compose.ui.input.key.Key.DirectionLeft }
                .padding(vertical = 24.dp, horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            com.sridhar.harbor.ui.ai.JellyBuddy(48.dp)
            Spacer(Modifier.height(18.dp))
            // Only services that are set up get a rail entry; 18+ appears once parental control is on.
            val cfgRail = com.sridhar.harbor.ui.components.rememberConfig()
            val parental by com.sridhar.harbor.ui.components.LocalContainer.current.parental.state.collectAsState()
            rail.filter { r -> when (r.dest) { TvDest.Adult -> parental.enabled && parental.showMenu; else -> true } }.forEach { r ->
                // Browse (Search…Music) and You (Connect, 18+, Settings) groups, split by a thin line.
                if (r.dest == TvDest.Connect) Box(Modifier.padding(vertical = 8.dp, horizontal = 10.dp).fillMaxWidth().height(1.dp).background(Harbor.line(.12f)))
                var focused by remember { mutableStateOf(false) }
                val selected = r.dest == current
                Row(
                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp))
                        .background(when { focused -> Color.White; selected -> Harbor.line(.12f); else -> Color.Transparent })
                        .focusRequester(railFocus.getOrPut(r.dest) { androidx.compose.ui.focus.FocusRequester() })
                        .onFocusChanged { focused = it.isFocused }.clickable { go(r.dest) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(26.dp)) {
                        Icon(r.icon, r.label, tint = if (focused) Color.Black else if (selected) Harbor.Sky else Harbor.TextDim, modifier = Modifier.fillMaxSize())
                    }
                    AnimatedVisibility(railFocused, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                        Text(r.label, color = if (focused) Color.Black else Harbor.Fg, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
        TvHints(current, keyTick, Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp))
    }
}
