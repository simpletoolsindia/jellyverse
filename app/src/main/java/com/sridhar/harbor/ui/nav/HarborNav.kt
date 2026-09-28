package com.sridhar.harbor.ui.nav

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.drawBehind
import com.sridhar.harbor.ui.components.pressable
import com.sridhar.harbor.ui.components.Readable
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.sridhar.harbor.data.ServerConfig
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.discover.DiscoverScreen
import com.sridhar.harbor.ui.discover.SeerrDetailScreen
import com.sridhar.harbor.ui.offline.OfflineScreen
import com.sridhar.harbor.ui.profile.ProfileScreen
import com.sridhar.harbor.ui.requests.RequestsScreen
import com.sridhar.harbor.ui.setup.SetupScreen
import com.sridhar.harbor.ui.theme.Harbor
import com.sridhar.harbor.ui.torrents.TorrentsScreen
import com.sridhar.harbor.ui.users.UsersScreen
import com.sridhar.harbor.ui.watch.ItemDetailScreen
import com.sridhar.harbor.ui.watch.LibraryScreen
import com.sridhar.harbor.ui.watch.SearchScreen
import com.sridhar.harbor.ui.watch.WatchHomeScreen
import kotlinx.serialization.Serializable

@Serializable object SetupRoute
@Serializable object WatchRoute
@Serializable object DiscoverRoute
@Serializable object RequestsRoute
@Serializable object TorrentsRoute
@Serializable object ProfileRoute
@Serializable object SearchRoute
@Serializable object UsersRoute
@Serializable object AdminRoute
@Serializable data class AdminUserRoute(val id: String, val name: String)
@Serializable object OfflineRoute
@Serializable data class LibraryRoute(val id: String, val name: String, val collectionType: String? = null)
@Serializable data class ItemRoute(val id: String)
@Serializable data class SeerrRoute(val type: String, val id: Int)
@Serializable object ManageRoute
@Serializable object LabRoute
@Serializable object AssistantRoute
@Serializable object DoctorRoute
@Serializable object LiveRoute
@Serializable object HostsRoute
@Serializable data class TerminalRoute(val hostId: String)
@Serializable data class ArrMovieRoute(val id: Int)
@Serializable data class ArrSeriesRoute(val id: Int)
@Serializable object ProtectedRoute
@Serializable object RemoteRoute
@Serializable object MusicRoute
@Serializable data class MusicAlbumRoute(val id: String)
@Serializable data class MusicPlaylistRoute(val id: String)
@Serializable object MusicLikedRoute
@Serializable data class MusicArtistRoute(val ids: String, val name: String)
@Serializable object MusicSearchRoute
@Serializable object MusicLibraryRoute

/** Outlined when idle, filled when selected – the convention people know from the apps they use daily. */
private data class Tab(val route: Any, @androidx.annotation.StringRes val labelRes: Int, val icon: ImageVector, val idleIcon: ImageVector) {
    val label: String get() = com.sridhar.harbor.L10n.s(labelRes)
}

private val allTabs = listOf(
    Tab(WatchRoute, com.sridhar.harbor.R.string.tab_watch, Icons.Rounded.PlayCircle, Icons.Outlined.PlayCircle),
    Tab(MusicRoute, com.sridhar.harbor.R.string.tab_music, Icons.Rounded.MusicNote, Icons.Outlined.MusicNote),
    Tab(DiscoverRoute, com.sridhar.harbor.R.string.tab_discover, Icons.Rounded.Explore, Icons.Outlined.Explore),
    Tab(RequestsRoute, com.sridhar.harbor.R.string.tab_requests, Icons.Rounded.Inbox, Icons.Outlined.Inbox),
    Tab(ManageRoute, com.sridhar.harbor.R.string.tab_manage, Icons.Rounded.Tune, Icons.Outlined.Tune),
    Tab(TorrentsRoute, com.sridhar.harbor.R.string.tab_torrents, Icons.Rounded.SwapVert, Icons.Outlined.SwapVert),
    Tab(LabRoute, com.sridhar.harbor.R.string.tab_lab, Icons.Rounded.Dns, Icons.Outlined.Dns),
    Tab(ProfileRoute, com.sridhar.harbor.R.string.settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
)

/**
 * Every service is optional: a tab only exists once its service is set up. Settings stands in for Lab
 * when no SSH host is configured, so accounts are always one tap away.
 */
private fun visibleTabs(cfg: com.sridhar.harbor.data.ServerConfig?, hasSsh: Boolean): List<Tab> = allTabs.filter { t ->
    when (t.route) {
        WatchRoute -> cfg?.jellyfinReady == true
        MusicRoute -> cfg?.navidromeReady == true
        DiscoverRoute, RequestsRoute -> cfg?.seerrReady == true
        ManageRoute -> cfg?.arrReady == true
        TorrentsRoute -> cfg?.qbitReady == true || cfg?.aria2Ready == true
        LabRoute -> hasSsh
        ProfileRoute -> !hasSsh
        else -> true
    }
}

@Composable
fun HarborNavHost() {
    val container = LocalContainer.current
    val cfg by container.config.collectAsState()
    if (cfg == null) Box(Modifier.fillMaxSize().background(Harbor.Ink)) else HarborNavContent(cfg!!)
}

@Composable
private fun HarborNavContent(initial: ServerConfig) {
    val container = LocalContainer.current
    val nav = rememberNavController()
    val start: Any = remember {
        when {
            !initial.jellyfinReady && !initial.qbitReady && !initial.seerrReady && !initial.arrReady && container.ssh.hosts.value.isEmpty() -> SetupRoute
            else -> visibleTabs(initial, container.ssh.hosts.value.isNotEmpty()).firstOrNull()?.route ?: ProfileRoute
        }
    }

    var showPlayer by rememberSaveable { mutableStateOf(false) }
    // Bring back the last music queue (paused) so the mini player is there on every tab after a restart.
    LaunchedEffect(Unit) { if (container.config.value?.navidromeReady == true) container.musicEngine.restore() }
    val music by container.musicEngine.state.collectAsState()
    // The mini player follows actual listening: it tucks away after 1 min without playback (a restored/paused
    // queue no longer sits on every screen) and returns the moment anything plays.
    var musicIdle by remember { mutableStateOf(!music.playing) }
    LaunchedEffect(music.playing, music.current?.id) {
        if (music.playing) musicIdle = false
        else { kotlinx.coroutines.delay(60_000); musicIdle = true }
    }
    val musicActive = music.current != null && !musicIdle
    val musicNav = remember(nav) {
        com.sridhar.harbor.ui.music.MusicNav(
            album = { nav.navigate(MusicAlbumRoute(it)) }, playlist = { nav.navigate(MusicPlaylistRoute(it)) }, liked = { nav.navigate(MusicLikedRoute) },
            artist = { ids, name -> nav.navigate(MusicArtistRoute(ids.joinToString(","), name)) },
            search = { nav.navigate(MusicSearchRoute) }, library = { nav.navigate(MusicLibraryRoute) }, back = { nav.popBackStack() },
        )
    }

    // Magnet / .torrent opened from another app → jump to Torrents, which consumes it.
    LaunchedEffect(Unit) {
        container.incomingTorrents.collect { nav.switchTab(TorrentsRoute) }
    }
    LaunchedEffect(Unit) {
        container.navRequests.collect { dest ->
            when (dest) {
                "search" -> nav.navigate(SearchRoute)
                "ai" -> nav.navigate(AssistantRoute)
                "downloads" -> nav.switchTab(TorrentsRoute)
                "lab" -> nav.switchTab(LabRoute)
                "live" -> nav.navigate(LiveRoute)
                "music" -> nav.switchTab(MusicRoute)
                "remote" -> nav.navigate(RemoteRoute)
                "nowplaying" -> { nav.switchTab(MusicRoute); showPlayer = true }
                else -> nav.switchTab(WatchRoute)
            }
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) container.navRequests.resetReplayCache()
        }
    }

    val liveCfg by container.config.collectAsState()
    val sshHosts by container.ssh.hosts.collectAsState()
    val tabs = remember(liveCfg, sshHosts) { visibleTabs(liveCfg, sshHosts.isNotEmpty()) }
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val showBar = dest != null && tabs.any { t -> dest.hasRoute(t.route::class) }

    // Short landscape screens (phones on their side) lose half their height to a bottom bar – use a side rail there.
    val useRail = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenHeightDp < 500 && it.screenWidthDp > it.screenHeightDp }
    val railInset by androidx.compose.animation.core.animateDpAsState(if (useRail && showBar) 84.dp else 0.dp, label = "rail")

    Box(Modifier.fillMaxSize().background(Harbor.Ink)) {
        val isTab: (androidx.navigation.NavBackStackEntry) -> Boolean = { e -> tabs.any { t -> e.destination.hasRoute(t.route::class) } }
        // Mini player height (+ gap) that floating buttons and lists must stay clear of.
        val miniVisible = musicActive && (showBar || dest != null && listOf(MusicAlbumRoute::class, MusicPlaylistRoute::class, MusicLikedRoute::class, MusicArtistRoute::class, MusicSearchRoute::class, MusicLibraryRoute::class).any { dest.hasRoute(it) })
        val miniInset by androidx.compose.animation.core.animateDpAsState(if (miniVisible) 72.dp else 0.dp, label = "miniInset")
        androidx.compose.runtime.CompositionLocalProvider(com.sridhar.harbor.ui.components.LocalMiniPlayerInset provides miniInset, com.sridhar.harbor.ui.components.LocalBottomBarInset provides (if (useRail) 0.dp else 80.dp)) {
        NavHost(
            nav, startDestination = start, modifier = Modifier.fillMaxSize().padding(start = railInset),
            // Tabs cross-fade with a subtle zoom; pushed screens slide over like cards.
            enterTransition = {
                if (isTab(initialState) && isTab(targetState)) fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.98f)
                else slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { it / 3 } + fadeIn(tween(200))
            },
            exitTransition = {
                if (isTab(initialState) && isTab(targetState)) fadeOut(tween(160))
                else slideOutHorizontally(tween(260)) { -it / 8 } + fadeOut(tween(260), targetAlpha = 0.4f)
            },
            popEnterTransition = { slideInHorizontally(tween(260)) { -it / 8 } + fadeIn(tween(260), initialAlpha = 0.4f) },
            popExitTransition = { slideOutHorizontally(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { it / 3 } + fadeOut(tween(180)) },
        ) {
            composable<SetupRoute> { Readable {
                SetupScreen(onDone = {
                    val first = visibleTabs(container.config.value, container.ssh.hosts.value.isNotEmpty()).firstOrNull()?.route ?: ProfileRoute
                    nav.navigate(first) { popUpTo(0) { inclusive = true } }
                })
            }}
            composable<WatchRoute> {
                WatchHomeScreen(
                    onLive = { nav.navigate(LiveRoute) },
                    onItem = { nav.navigate(ItemRoute(it)) },
                    onLibrary = { id, name, type -> nav.navigate(LibraryRoute(id, name, type)) },
                    onProtected = { nav.navigate(ProtectedRoute) },
                    onRemote = { nav.navigate(RemoteRoute) },
                    onSearch = { nav.navigate(SearchRoute) },
                    onSetup = { nav.navigate(SetupRoute) },
                )
            }
            composable<DiscoverRoute> {
                DiscoverScreen(onOpen = { type, id -> nav.navigate(SeerrRoute(type, id)) }, onSetup = { nav.navigate(SetupRoute) })
            }
            composable<RequestsRoute> { Readable {
                RequestsScreen(onOpen = { type, id -> nav.navigate(SeerrRoute(type, id)) }, onSetup = { nav.navigate(SetupRoute) })
            }}
            composable<TorrentsRoute> { Readable { TorrentsScreen(onSetup = { nav.navigate(SetupRoute) }) }}
            composable<ProfileRoute> { Readable {
                ProfileScreen(
                    onDoctor = { nav.navigate(DoctorRoute) },
                    onAssistant = { nav.navigate(AssistantRoute) },
                    onTerminal = { nav.navigate(HostsRoute) },
                    onSetup = { nav.navigate(SetupRoute) },
                    onUsers = { nav.navigate(UsersRoute) },
                    onAdmin = { nav.navigate(AdminRoute) },
                    onOffline = { nav.navigate(OfflineRoute) },
                    onRemote = { nav.navigate(RemoteRoute) },
                )
            }}
            composable<SearchRoute> { SearchScreen(onItem = { nav.navigate(ItemRoute(it)) }, onBack = { nav.popBackStack() }) }
            composable<LibraryRoute> {
                val r = it.toRoute<LibraryRoute>()
                LibraryScreen(r.id, r.name, r.collectionType, onItem = { id -> nav.navigate(ItemRoute(id)) }, onBack = { nav.popBackStack() })
            }
            composable<ItemRoute> {
                val r = it.toRoute<ItemRoute>()
                ItemDetailScreen(r.id, onItem = { id -> nav.navigate(ItemRoute(id)) }, onBack = { nav.popBackStack() })
            }
            composable<SeerrRoute> {
                val r = it.toRoute<SeerrRoute>()
                SeerrDetailScreen(r.type, r.id, onOpen = { type, id -> nav.navigate(SeerrRoute(type, id)) }, onBack = { nav.popBackStack() },
                    onJellyfin = { id -> nav.navigate(ItemRoute(id)) })
            }
            composable<AssistantRoute> { Readable {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                com.sridhar.harbor.ui.ai.AssistantScreen(onBack = { nav.popBackStack() }, onNav = { target ->
                    when (target) {
                        com.sridhar.harbor.data.ai.AiNav.Doctor -> nav.navigate(DoctorRoute)
                        com.sridhar.harbor.data.ai.AiNav.Lab -> nav.switchTab(LabRoute)
                        com.sridhar.harbor.data.ai.AiNav.Terminal -> nav.navigate(HostsRoute)
                        com.sridhar.harbor.data.ai.AiNav.Downloads -> nav.switchTab(TorrentsRoute)
                        com.sridhar.harbor.data.ai.AiNav.Manage -> nav.switchTab(ManageRoute)
                        com.sridhar.harbor.data.ai.AiNav.Discover -> nav.switchTab(DiscoverRoute)
                        com.sridhar.harbor.data.ai.AiNav.Requests -> nav.switchTab(RequestsRoute)
                        is com.sridhar.harbor.data.ai.AiNav.Item -> nav.navigate(ItemRoute(target.id))
                        is com.sridhar.harbor.data.ai.AiNav.Seerr -> nav.navigate(SeerrRoute(target.type, target.id))
                        is com.sridhar.harbor.data.ai.AiNav.Play -> com.sridhar.harbor.ui.player.PlayerActivity.start(ctx, target.id)
                        is com.sridhar.harbor.data.ai.AiNav.Cast -> Unit
                        is com.sridhar.harbor.data.ai.AiNav.Route -> container.navRequests.tryEmit(target.dest)
                    }
                })
            }}
            composable<LiveRoute> { com.sridhar.harbor.ui.live.LiveTvScreen(onBack = { nav.popBackStack() }) }
            composable<DoctorRoute> { Readable { com.sridhar.harbor.ui.ai.LibraryDoctorScreen(onBack = { nav.popBackStack() }) }}
            composable<LabRoute> { Readable {
                com.sridhar.harbor.ui.lab.LabScreen(
                    onSetup = { nav.navigate(SetupRoute) },
                    onTerminal = {
                        val primary = container.ssh.primary
                        if (primary != null && container.ssh.hosts.value.size == 1) nav.navigate(TerminalRoute(primary.id)) else nav.navigate(HostsRoute)
                    },
                    onProfile = { nav.navigate(ProfileRoute) },
                )
            }}
            composable<HostsRoute> { Readable { com.sridhar.harbor.ui.ssh.HostsScreen(onConnect = { nav.navigate(TerminalRoute(it)) }, onBack = { nav.popBackStack() }) }}
            composable<TerminalRoute> { com.sridhar.harbor.ui.ssh.TerminalScreen(it.toRoute<TerminalRoute>().hostId, onBack = { nav.popBackStack() }) }
            composable<ManageRoute> {
                com.sridhar.harbor.ui.arr.ManageScreen(
                    onMovie = { nav.navigate(ArrMovieRoute(it)) }, onSeries = { nav.navigate(ArrSeriesRoute(it)) },
                    onSetup = { nav.navigate(SetupRoute) }, onProfile = { nav.navigate(ProfileRoute) },
                )
            }
            composable<ArrMovieRoute> { com.sridhar.harbor.ui.arr.ArrMovieScreen(it.toRoute<ArrMovieRoute>().id, onBack = { nav.popBackStack() }) }
            composable<ArrSeriesRoute> { com.sridhar.harbor.ui.arr.ArrSeriesScreen(it.toRoute<ArrSeriesRoute>().id, onBack = { nav.popBackStack() }) }
            composable<UsersRoute> { Readable { UsersScreen(onBack = { nav.popBackStack() }) }}
            composable<AdminRoute> { Readable { com.sridhar.harbor.ui.admin.AdminDashboardScreen(onBack = { nav.popBackStack() }, onUser = { id, name -> nav.navigate(AdminUserRoute(id, name)) }) }}
            composable<AdminUserRoute> { Readable { val r = it.toRoute<AdminUserRoute>(); com.sridhar.harbor.ui.admin.AdminUserScreen(r.id, r.name, onBack = { nav.popBackStack() }) }}
            composable<RemoteRoute> { com.sridhar.harbor.ui.remote.RemoteScreen(onBack = { nav.popBackStack() }) }
            composable<ProtectedRoute> { com.sridhar.harbor.ui.parental.ProtectedTitlesScreen(onItem = { nav.navigate(ItemRoute(it)) }, onBack = { nav.popBackStack() }) }
            composable<OfflineRoute> { Readable { OfflineScreen(onBack = { nav.popBackStack() }) }}
            composable<MusicRoute> { com.sridhar.harbor.ui.music.MusicHomeScreen(musicNav) }
            composable<MusicAlbumRoute> { Readable { com.sridhar.harbor.ui.music.CollectionScreen("album", it.toRoute<MusicAlbumRoute>().id, musicNav) }}
            composable<MusicPlaylistRoute> { Readable { com.sridhar.harbor.ui.music.CollectionScreen("playlist", it.toRoute<MusicPlaylistRoute>().id, musicNav) }}
            composable<MusicLikedRoute> { Readable { com.sridhar.harbor.ui.music.CollectionScreen("liked", "liked", musicNav) }}
            composable<MusicArtistRoute> { Readable { val r = it.toRoute<MusicArtistRoute>(); com.sridhar.harbor.ui.music.ArtistScreen(r.ids.split(','), r.name, musicNav) }}
            composable<MusicSearchRoute> { Readable { com.sridhar.harbor.ui.music.MusicSearchScreen(musicNav) }}
            composable<MusicLibraryRoute> { Readable { com.sridhar.harbor.ui.music.MusicLibraryScreen(musicNav) }}
        }

        }
        val onMusicScreen = dest != null && listOf(MusicAlbumRoute::class, MusicPlaylistRoute::class, MusicLikedRoute::class, MusicArtistRoute::class, MusicSearchRoute::class, MusicLibraryRoute::class).any { dest.hasRoute(it) }
        val onMusicTab = dest?.hasRoute(MusicRoute::class) == true
        val showMini = (musicActive || (onMusicScreen || onMusicTab) && music.current != null) && (showBar || onMusicScreen)
        val miniLift = if (showMini && !useRail) 68.dp else 0.dp
        com.sridhar.harbor.cast.CastMiniBar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = (if (showBar && !useRail) 160.dp else 16.dp) + miniLift))
        AnimatedVisibility(
            visible = showMini,
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
            modifier = if (useRail) Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 12.dp, bottom = 12.dp).width(380.dp)
                else Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (showBar) 80.dp else 12.dp),
        ) { com.sridhar.harbor.ui.music.MiniPlayer(onOpen = { showPlayer = true }) }
        AnimatedVisibility(
            visible = showBar && useRail,
            enter = androidx.compose.animation.slideInHorizontally { -it } + fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            FloatingNavRail(
                tabs = tabs,
                selected = tabs.indexOfFirst { t -> dest?.hasRoute(t.route::class) == true },
                onSelect = { nav.switchTab(tabs[it].route) },
                onAssistant = { nav.navigate(AssistantRoute) },
            )
        }
        AnimatedVisibility(
            visible = showBar && !useRail,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            FloatingNavBar(
                tabs = tabs,
                selected = tabs.indexOfFirst { t -> dest?.hasRoute(t.route::class) == true },
                onSelect = { nav.switchTab(tabs[it].route) },
                onAssistant = { nav.navigate(AssistantRoute) },
            )
        }
        AnimatedVisibility(
            visible = showPlayer && music.current != null,
            enter = slideInVertically(androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 380f)) { it },
            exit = slideOutVertically(androidx.compose.animation.core.tween(260)) { it },
        ) {
            androidx.activity.compose.BackHandler { showPlayer = false }
            com.sridhar.harbor.ui.music.NowPlayingScreen(
                onClose = { showPlayer = false },
                onAlbum = { showPlayer = false; musicNav.album(it) },
                onArtist = { ids, n -> showPlayer = false; musicNav.artist(ids, n) },
            )
        }
    }
}

private fun NavHostController.switchTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun FloatingNavBar(tabs: List<Tab>, selected: Int, onSelect: (Int) -> Unit, onAssistant: () -> Unit) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Harbor.Ink.copy(alpha = .9f))))
            .navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        val barMax = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.minus(24.dp).coerceAtMost(520.dp)
        // Width the selected pill gets (weights 2.4 vs 1, minus the AI orb and padding) – its label only shows if it fits whole.
        val activeW = (barMax - 12.dp - 50.dp) * (2.4f / (tabs.size - 1 + 2.4f))
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val labelStyle = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp)
        val density = androidx.compose.ui.platform.LocalDensity.current
        Row(
            Modifier.widthIn(max = 520.dp).fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(28.dp)).background(Color(0xF516181F))
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(28.dp))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { i, tab ->
                val active = i == selected
                // One transition per tab keeps weight, tint and label in lockstep.
                val t = updateTransition(active, label = "tab-${tab.label}")
                val weight by t.animateFloat({ spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow) }, label = "w") { if (it) 2.4f else 1f }
                val tint by t.animateColor(label = "tint") { if (it) Color.White else Harbor.TextDim }
                val pill by t.animateFloat(label = "pill") { if (it) 1f else 0f }
                Row(
                    Modifier.weight(weight).height(46.dp).clip(RoundedCornerShape(23.dp))
                        .drawBehind { if (pill > 0f) drawRect(Color.White, alpha = 0.12f * pill) }
                        .pressable(0.9f) { if (!active) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onSelect(i) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Selected icon fills in with a small spring "pop".
                    val pop by t.animateFloat({ spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium) }, label = "pop") { if (it) 1.12f else 1f }
                    Icon(if (active) tab.icon else tab.idleIcon, tab.label, tint = tint, modifier = Modifier.size(22.dp).graphicsLayer { scaleX = pop; scaleY = pop })
                    val fits = androidx.compose.runtime.remember(tab.label, activeW, density) {
                        with(density) { measurer.measure(tab.label, labelStyle).size.width.toDp() } + 22.dp + 6.dp + 20.dp <= activeW
                    }
                    t.AnimatedVisibility({ it && fits }, enter = fadeIn(tween(180, 80)) + expandHorizontally(), exit = fadeOut(tween(90)) + shrinkHorizontally()) {
                        Text(tab.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false,
                            modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            // AI lives in the bar (not floating over content) – nothing on screen hides behind it.
            Box(Modifier.padding(start = 4.dp).clip(androidx.compose.foundation.shape.CircleShape).pressable(0.88f, onClick = onAssistant)
                .semantics { contentDescription = com.sridhar.harbor.L10n.s(com.sridhar.harbor.R.string.jellyverse_ai); role = androidx.compose.ui.semantics.Role.Button }) {
                com.sridhar.harbor.ui.ai.AiOrb(46.dp)
            }
        }
    }
}

/** Vertical twin of [FloatingNavBar] for short landscape screens: icons only, AI orb docked at the foot. */
@Composable
private fun FloatingNavRail(tabs: List<Tab>, selected: Int, onSelect: (Int) -> Unit, onAssistant: () -> Unit) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(start = 10.dp, top = 8.dp, bottom = 8.dp).width(64.dp)
            .shadow(24.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(28.dp)).background(Color(0xF516181F))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(28.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEachIndexed { i, tab ->
            val active = i == selected
            val t = updateTransition(active, label = "rail-${tab.label}")
            val tint by t.animateColor(label = "tint") { if (it) Color.White else Harbor.TextDim }
            val pill by t.animateFloat(label = "pill") { if (it) 1f else 0f }
            val pop by t.animateFloat({ spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium) }, label = "pop") { if (it) 1.12f else 1f }
            Box(
                Modifier.size(48.dp, 40.dp).clip(RoundedCornerShape(20.dp))
                    .drawBehind { if (pill > 0f) drawRect(Harbor.Violet, alpha = 0.9f * pill) }
                    .pressable(0.9f) { if (!active) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onSelect(i) }
                    .semantics { contentDescription = tab.label; role = androidx.compose.ui.semantics.Role.Tab },
                contentAlignment = Alignment.Center,
            ) { Icon(if (active) tab.icon else tab.idleIcon, null, tint = tint, modifier = Modifier.size(22.dp).graphicsLayer { scaleX = pop; scaleY = pop }) }
        }
        Box(Modifier.clip(androidx.compose.foundation.shape.CircleShape).pressable(0.88f, onClick = onAssistant)
            .semantics { contentDescription = com.sridhar.harbor.L10n.s(com.sridhar.harbor.R.string.jellyverse_ai); role = androidx.compose.ui.semantics.Role.Button }) { com.sridhar.harbor.ui.ai.AiOrb(40.dp) }
    }
}
