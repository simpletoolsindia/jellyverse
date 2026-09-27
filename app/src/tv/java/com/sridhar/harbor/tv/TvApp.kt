package com.sridhar.harbor.tv

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
    RailItem(TvDest.Adult, com.sridhar.harbor.R.string.adult_menu, Icons.Rounded.Lock),
    RailItem(TvDest.Settings, com.sridhar.harbor.R.string.tv_settings, Icons.Rounded.Settings),
)

@Composable
fun TvApp() {
    val stack = remember { mutableStateListOf<TvDest>(TvDest.Home) }
    val current = stack.last()
    val go: (TvDest) -> Unit = { d -> if (d is TvDest.Detail || d is TvDest.MusicCollection || d is TvDest.NowPlaying) stack.add(d) else { stack.clear(); stack.add(d) } }
    // "Home" from the phone remote.
    val navContainer = com.sridhar.harbor.ui.components.LocalContainer.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        navContainer.navRequests.collect { if (it == "home") { stack.clear(); stack.add(TvDest.Home) } }
    }
    BackHandler(enabled = stack.size > 1 || current != TvDest.Home) { if (stack.size > 1) stack.removeAt(stack.lastIndex) else { stack.clear(); stack.add(TvDest.Home) } }
    var railFocused by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(if (railFocused) 230.dp else 84.dp, spring(dampingRatio = 0.85f), label = "rail")

    Box(Modifier.fillMaxSize().background(Harbor.Ink)) {
        Box(Modifier.fillMaxSize().padding(start = 84.dp)) {
            AnimatedContent(current, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) }, label = "tvdest") { d ->
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
                    TvDest.Adult -> TvProtected(onOpen = { go(TvDest.Detail(it)) }, onCancel = { stack.clear(); stack.add(TvDest.Home) })
                    is TvDest.Detail -> TvDetail(d.id, onOpen = { go(TvDest.Detail(it)) })
                }
            }
        }
        // Rail: collapsed icons; expands with labels while it has focus (Hotstar/Google TV style).
        if (current !is TvDest.Detail && current !is TvDest.NowPlaying && current !is TvDest.MusicCollection) Column(
            Modifier.fillMaxHeight().width(railWidth)
                .background(Brush.horizontalGradient(listOf(Harbor.Ink.copy(alpha = if (railFocused) 0.98f else 0.9f), Harbor.Ink.copy(alpha = if (railFocused) 0.9f else 0f))))
                .onFocusChanged { railFocused = it.hasFocus }.focusGroup().padding(vertical = 40.dp, horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            com.sridhar.harbor.ui.components.HarborLogo(56.dp)
            Spacer(Modifier.height(40.dp))
            // Only services that are set up get a rail entry; 18+ appears once parental control is on.
            val cfgRail = com.sridhar.harbor.ui.components.rememberConfig()
            val parental by com.sridhar.harbor.ui.components.LocalContainer.current.parental.state.collectAsState()
            rail.filter { r -> when (r.dest) { TvDest.Music -> cfgRail.navidromeReady; TvDest.Adult -> parental.enabled && parental.showMenu; else -> true } }.forEach { r ->
                var focused by remember { mutableStateOf(false) }
                val selected = r.dest == current
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(when { focused -> Color.White; selected -> Color.White.copy(.12f); else -> Color.Transparent })
                        .onFocusChanged { focused = it.isFocused }.clickable { go(r.dest) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(r.icon, r.label, tint = if (focused) Color.Black else if (selected) Color.White else Harbor.TextDim, modifier = Modifier.size(26.dp))
                    AnimatedVisibility(railFocused, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                        Text(r.label, color = if (focused) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}
