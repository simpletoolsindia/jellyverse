package com.sridhar.harbor.ui.admin

import androidx.compose.runtime.mutableIntStateOf
import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.jellyfin.admin.ActivityEntry
import com.sridhar.harbor.data.jellyfin.admin.AdminSession
import com.sridhar.harbor.data.jellyfin.admin.AdminUser
import com.sridhar.harbor.data.jellyfin.admin.ApiKey
import com.sridhar.harbor.data.jellyfin.admin.DeviceInfo
import com.sridhar.harbor.data.jellyfin.admin.ItemCounts
import com.sridhar.harbor.data.jellyfin.admin.LibraryFolder
import com.sridhar.harbor.data.jellyfin.admin.LibraryType
import com.sridhar.harbor.data.jellyfin.admin.PlayCommand
import com.sridhar.harbor.data.jellyfin.admin.PluginInfo
import com.sridhar.harbor.data.jellyfin.admin.ScheduledTask
import com.sridhar.harbor.data.jellyfin.admin.SystemInfo
import com.sridhar.harbor.ui.components.GradientButton
import com.sridhar.harbor.ui.components.GradientProgress
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.Pill
import com.sridhar.harbor.ui.components.formatBytes
import com.sridhar.harbor.ui.components.glass
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.components.relativeTime
import com.sridhar.harbor.ui.theme.Harbor

/** Jellyfin's web "Dashboard", rebuilt natively: server, users, live sessions, libraries, tasks, logs. */
@Composable
fun AdminDashboardScreen(onBack: () -> Unit, onUser: (id: String, name: String) -> Unit) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    val vm = viewModel { AdminViewModel(container.admin, cfg.jellyfinUserId) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var addUser by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Harbor.Ink,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            Column(Modifier.background(Harbor.Ink)) {
                TopAppBar(
                    title = { Text(stringResource(R.string.server_dashboard)) },
                    navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
                    actions = {
                        IconButton({ vm.refresh() }) { Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh)) }
                        Box {
                            IconButton({ menu = true }, Modifier.testTag("admin_menu")) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.server_actions)) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text(stringResource(R.string.restart_jellyfin)) }, leadingIcon = { Icon(Icons.Rounded.RestartAlt, null) },
                                    onClick = { menu = false; confirm = L10n.s(R.string.restart_jellyfin_everyone_watching_will_be) to { vm.restart() } })
                                DropdownMenuItem({ Text(stringResource(R.string.shut_down_jellyfin), color = Harbor.Rose) }, leadingIcon = { Icon(Icons.Rounded.PowerSettingsNew, null, tint = Harbor.Rose) },
                                    onClick = { menu = false; confirm = L10n.s(R.string.shut_down_jellyfin_you_ll_need) to { vm.shutdown() } })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Harbor.Ink),
                )
                TabStrip(vm.tab, vm::select)
            }
        },
    ) { pad ->
        AnimatedContent(vm.tab, Modifier.padding(pad).fillMaxSize(), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "admin-tab") { tab ->
            when (tab) {
                AdminTab.Overview -> Section(vm.info) { info ->
                    item { OverviewHeader(info, vm.counts.data, vm.users.data?.size, vm.sessions.data.orEmpty()) }
                    val playing = vm.sessions.data.orEmpty().filter { it.nowPlaying != null }
                    if (playing.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.now_playing)) }
                        items(playing, key = { it.id }) { s -> SessionCard(s, onCommand = { vm.command(s, it) }, onSeek = { vm.seekBy(s, it) }, onMessage = null) }
                    }
                    item { SectionTitle(stringResource(R.string.server)) }
                    item { ServerPaths(info) }
                }
                AdminTab.Playing -> Section(vm.sessions) { list ->
                    if (list.isEmpty()) item { Empty(stringResource(R.string.no_active_devices), Icons.Rounded.Tv) }
                    items(list, key = { it.id }) { s ->
                        var msg by remember { mutableStateOf(false) }
                        SessionCard(s, onCommand = { vm.command(s, it) }, onSeek = { vm.seekBy(s, it) }, onMessage = { msg = true })
                        if (msg) MessageDialog(s.deviceName, onDismiss = { msg = false }) { h, t -> vm.sendMessage(s, h, t); msg = false }
                    }
                }
                AdminTab.Users -> Section(vm.users) { list ->
                    item { GradientButton(stringResource(R.string.add_user), { addUser = true }, Modifier.fillMaxWidth().testTag("add_user"), icon = Icons.Rounded.Add) }
                    items(list, key = { it.id }) { u -> UserRow(u, isSelf = u.id == vm.selfUserId, avatar = userAvatar(cfg.jellyfinUrl, u)) { onUser(u.id, u.name) } }
                }
                AdminTab.Libraries -> {
                    var add by remember { mutableStateOf(false) }
                    Section(vm.libraries) { list ->
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GradientButton(stringResource(R.string.scan_all), { vm.scanAll() }, Modifier.weight(1f), icon = Icons.Rounded.Refresh)
                                GradientButton(stringResource(R.string.add_library), { add = true }, Modifier.weight(1f), icon = Icons.Rounded.Add)
                            }
                        }
                        items(list, key = { it.name }) { l ->
                            LibraryRow(l, onScan = { vm.scan(l) }, onRemove = { confirm = L10n.s(R.string.remove_library_1_s_media_files, l.name) to { vm.removeLibrary(l) } })
                        }
                    }
                    if (add) AddLibraryDialog(onDismiss = { add = false }) { n, t, p -> vm.addLibrary(n, t, p); add = false }
                }
                AdminTab.Tasks -> Section(vm.tasks) { list ->
                    list.groupBy { it.category.ifBlank { L10n.s(R.string.other) } }.forEach { (cat, tasks) ->
                        item(key = "h-$cat") { SectionTitle(cat) }
                        items(tasks, key = { it.id }) { t -> TaskRow(t) { vm.toggleTask(t) } }
                    }
                }
                AdminTab.Activity -> Section(vm.activity) { list ->
                    if (list.isEmpty()) item { Empty(stringResource(R.string.no_activity_yet), Icons.Rounded.Description) }
                    items(list, key = { it.id }) { ActivityRow(it) }
                }
                AdminTab.Devices -> Section(vm.devices) { list ->
                    items(list, key = { it.id }) { d ->
                        DeviceRow(d) { confirm = L10n.s(R.string.sign_out_1_s_it_will, d.customName ?: d.name) to { vm.removeDevice(d) } }
                    }
                }
                AdminTab.Plugins -> Section(vm.plugins) { list ->
                    items(list, key = { it.id }) { p -> PluginRow(p) { vm.setPlugin(p, it) } }
                }
                AdminTab.ApiKeys -> {
                    var add by remember { mutableStateOf(false) }
                    Section(vm.keys) { list ->
                        item { GradientButton(stringResource(R.string.new_api_key), { add = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Key) }
                        if (list.isEmpty()) item { Empty(stringResource(R.string.no_api_keys), Icons.Rounded.Key) }
                        items(list, key = { it.token }) { k -> ApiKeyRow(k) { confirm = L10n.s(R.string.revoke_the_key_for_1_s, k.appName) to { vm.revokeKey(k) } } }
                    }
                    if (add) TextPromptDialog(stringResource(R.string.new_api_key), stringResource(R.string.app_name_label), stringResource(R.string.create), onDismiss = { add = false }) { vm.createKey(it); add = false }
                }
                AdminTab.Logs -> {
                    var open by remember { mutableStateOf<String?>(null) }
                    Section(vm.logs) { list ->
                        items(list, key = { it.name }) { f ->
                            ListCard(Modifier.clickable { open = f.name }) {
                                Icon(Icons.Rounded.Description, null, tint = Harbor.Sky)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${formatBytes(f.size)} · ${relativeTime(f.modified)}", color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    open?.let { name -> LogViewer(name, load = { vm.logTail(name) }, onDismiss = { open = null }) }
                }
            }
        }
    }

    if (addUser) AddUserDialog(validate = vm::validateNewUser, onDismiss = { addUser = false }) { n, p ->
        addUser = false
        vm.createUser(n, p) { onUser(it.id, it.name) }
    }
    confirm?.let { (text, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.are_you_sure)) },
            text = { Text(text) },
            confirmButton = { TextButton({ confirm = null; action() }, Modifier.testTag("confirm")) { Text(stringResource(R.string.confirm), color = Harbor.Rose) } },
            dismissButton = { TextButton({ confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun userAvatar(base: String, u: AdminUser) = u.primaryImageTag?.let { "$base/Users/${u.id}/Images/Primary?tag=$it&maxWidth=120" }

// ------------------------------------------------------------------ building blocks

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TabStrip(selected: AdminTab, onSelect: (AdminTab) -> Unit) {
    val scroll = rememberScrollState()
    val centers = remember { IntArray(AdminTab.entries.size) }
    var rowWidth by remember { mutableIntStateOf(0) }
    // Keep the selected chip centred so its neighbours are always visible – no hunting in a long row.
    LaunchedEffect(selected, rowWidth) { if (rowWidth > 0) scroll.animateScrollTo((centers[selected.ordinal] - rowWidth / 2).coerceAtLeast(0)) }
    // testTagsAsResourceId lets UiAutomator (end-to-end tests, accessibility tooling) address the row and chips.
    Row(Modifier.fillMaxWidth().onSizeChanged { rowWidth = it.width }.semantics { testTagsAsResourceId = true }.testTag("admin_tabs")
        .horizontalScroll(scroll).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AdminTab.entries.forEach { t ->
            FilterChip(
                selected = t == selected, onClick = { onSelect(t) }, label = { Text(t.label) },
                modifier = Modifier.testTag("tab_${t.name}").onGloballyPositioned { c ->
                    centers[t.ordinal] = (c.positionInParent().x + c.size.width / 2f).toInt()
                },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Harbor.Violet, selectedLabelColor = Color.White),
            )
        }
    }
}

@Composable
private fun <T> Section(state: Loadable<T>, content: LazyListScope.(T) -> Unit) {
    val data = state.data
    when {
        data == null && state.error != null -> MessageState(stringResource(R.string.couldn_t_load), state.error, icon = Icons.Rounded.AdminPanelSettings)
        data == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() }
        else -> LazyColumn(Modifier.fillMaxSize().testTag("admin_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content(data)
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Harbor.TextDim, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

@Composable
private fun Empty(text: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(48.dp), tint = Harbor.TextDim)
        Spacer(Modifier.height(8.dp))
        Text(text, color = Harbor.TextDim)
    }
}

@Composable
internal fun ListCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Harbor.Surface).then(modifier).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, content = content)
}

// ------------------------------------------------------------------ overview

@Composable
internal fun OverviewHeader(info: SystemInfo, counts: ItemCounts?, users: Int?, sessions: List<AdminSession>) {
    Column(Modifier.fillMaxWidth().glass().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Harbor.accent), Alignment.Center) { Icon(Icons.Rounded.AdminPanelSettings, null, tint = Color.White) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(info.serverName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.jellyfin_1_s_2_s, info.version, info.osDisplay ?: info.os).trimEnd(' ', '·'), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (info.pendingRestart || info.updateAvailable) {
            Spacer(Modifier.height(10.dp))
            Pill(if (info.pendingRestart) stringResource(R.string.restart_pending_to_apply_changes) else stringResource(R.string.update_available), Harbor.Amber)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(stringResource(R.string.streaming), sessions.count { it.nowPlaying != null }.toString(), Harbor.Coral, Modifier.weight(1f))
            Stat(stringResource(R.string.online), sessions.size.toString(), Harbor.Mint, Modifier.weight(1f))
            Stat(stringResource(R.string.users), users?.toString() ?: "–", Harbor.Sky, Modifier.weight(1f))
        }
        counts?.let { c ->
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(stringResource(R.string.movies), c.movies.toString(), Harbor.Violet, Modifier.weight(1f))
                Stat(stringResource(R.string.series), c.series.toString(), Harbor.VioletSoft, Modifier.weight(1f))
                Stat(stringResource(R.string.episodes), c.episodes.toString(), Harbor.Amber, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, tint: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = .12f)).padding(vertical = 10.dp, horizontal = 12.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Harbor.TextDim)
    }
}

@Composable
private fun ServerPaths(info: SystemInfo) {
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(L10n.s(R.string.address) to info.localAddress, L10n.s(R.string.data) to info.dataPath, L10n.s(R.string.cache) to info.cachePath, L10n.s(R.string.logs) to info.logPath, L10n.s(R.string.transcodes) to info.transcodePath)
            .filter { !it.second.isNullOrBlank() }
            .forEach { (k, v) ->
                Row {
                    Text(k, Modifier.width(92.dp), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
                    Text(v!!, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
    }
}

// ------------------------------------------------------------------ sessions

@Composable
internal fun SessionCard(s: AdminSession, onCommand: (PlayCommand) -> Unit, onSeek: (Int) -> Unit, onMessage: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().glass().padding(14.dp).testTag("session_${s.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (s.nowPlaying != null) (if (s.playState.isPaused) Harbor.Amber else Harbor.Mint) else Harbor.TextDim))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.deviceName.ifBlank { s.client }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(s.userName, "${s.client} ${s.appVersion}".trim(), s.remoteEndPoint).joinToString(" · "),
                    color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            onMessage?.let { IconButton(it) { Icon(Icons.Rounded.ChatBubble, stringResource(R.string.send_message), tint = Harbor.TextDim) } }
        }
        val np = s.nowPlaying
        if (np == null) {
            Text(stringResource(R.string.idle_1_s, relativeTime(s.lastActivity)), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            return@Column
        }
        Spacer(Modifier.height(10.dp))
        Text(np.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        GradientProgress(s.progress, Modifier.fillMaxWidth().height(4.dp))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(s.playMethodLabel, if (s.transcoding != null && !(s.transcoding.isVideoDirect && s.transcoding.isAudioDirect)) Harbor.Amber else Harbor.Mint)
            s.transcoding?.bitrate?.let { Pill("${"%.1f".format(it / 1_000_000.0)} Mbps", Harbor.Sky) }
            if (s.playState.isPaused) Pill(stringResource(R.string.paused), Harbor.Amber)
        }
        if (s.supportsMediaControl) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton({ onCommand(PlayCommand.PreviousTrack) }) { Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.previous)) }
                IconButton({ onSeek(-10) }, enabled = s.playState.canSeek) { Icon(Icons.Rounded.Replay10, stringResource(R.string.back_10_seconds)) }
                IconButton({ onCommand(PlayCommand.PlayPause) }, Modifier.testTag("playpause_${s.id}")) {
                    Icon(if (s.playState.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (s.playState.isPaused) stringResource(R.string.resume) else stringResource(R.string.pause))
                }
                IconButton({ onSeek(30) }, enabled = s.playState.canSeek) { Icon(Icons.Rounded.Forward30, stringResource(R.string.forward_30_seconds)) }
                IconButton({ onCommand(PlayCommand.NextTrack) }) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.next)) }
                IconButton({ onCommand(PlayCommand.Stop) }) { Icon(Icons.Rounded.Stop, stringResource(R.string.stop), tint = Harbor.Rose) }
            }
        }
    }
}

@Composable
private fun MessageDialog(device: String, onDismiss: () -> Unit, onSend: (String, String) -> Unit) {
    var header by remember { mutableStateOf("Message from admin") }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.message_1_s, device)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(header, { header = it }, label = { Text(stringResource(R.string.title)) }, singleLine = true)
                OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.message)) }, minLines = 2)
            }
        },
        confirmButton = { TextButton({ onSend(header, text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.send)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ------------------------------------------------------------------ users

@Composable
internal fun UserRow(u: AdminUser, isSelf: Boolean, avatar: String?, onClick: () -> Unit) {
    ListCard(Modifier.clickable(onClick = onClick).testTag("user_${u.name}")) {
        Box(Modifier.size(46.dp).clip(CircleShape)) { NetImage(avatar, Modifier.fillMaxSize(), fallback = u.name.take(1).uppercase()) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(u.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (isSelf) Text(stringResource(R.string.you), color = Harbor.TextDim)
            }
            Text(u.lastActivity?.let { stringResource(R.string.active_1_s, relativeTime(it)) } ?: stringResource(R.string.never_signed_in), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (u.policy.isAdministrator) Pill(stringResource(R.string.admin), Harbor.Coral) else Pill(stringResource(R.string.user), Harbor.Sky)
                if (u.policy.isDisabled) Pill(stringResource(R.string.disabled), Harbor.Rose)
                if (u.policy.isHidden) Pill(stringResource(R.string.hidden), Harbor.TextDim)
                if (!u.hasPassword) Pill(stringResource(R.string.no_password), Harbor.Amber)
            }
        }
    }
}

@Composable
internal fun AddUserDialog(validate: (String, String) -> String?, onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var touched by remember { mutableStateOf(false) }
    val error = validate(name, pass).takeIf { touched }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_jellyfin_user)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it; touched = true }, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.testTag("new_user_name"))
                OutlinedTextField(pass, { pass = it; touched = true }, label = { Text(stringResource(R.string.password_optional)) }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.testTag("new_user_pass"))
                error?.let { Text(it, color = Harbor.Rose, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("new_user_error")) }
                Text(stringResource(R.string.you_can_set_permissions_and_library), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton({ onCreate(name.trim(), pass) }, enabled = touched && validate(name, pass) == null, modifier = Modifier.testTag("create_user")) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ------------------------------------------------------------------ libraries

@Composable
internal fun LibraryRow(l: LibraryFolder, onScan: () -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().glass().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FolderOpen, null, tint = Harbor.Violet)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(l.name, fontWeight = FontWeight.Bold)
                Text(LibraryType.entries.firstOrNull { it.api == l.collectionType }?.label ?: (l.collectionType ?: stringResource(R.string.mixed)), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onScan) { Icon(Icons.Rounded.Refresh, stringResource(R.string.scan_1_s, l.name)) }
            IconButton(onRemove) { Icon(Icons.Rounded.Delete, stringResource(R.string.remove_1_s, l.name), tint = Harbor.Rose) }
        }
        l.locations.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Harbor.TextDim) }
        val p = l.refreshProgress
        if (l.refreshStatus == "Active" && p != null) {
            Spacer(Modifier.height(8.dp))
            com.sridhar.harbor.ui.components.TideBar(Modifier.fillMaxWidth(), progress = { (p / 100).toFloat() })
            Text(stringResource(R.string.scanning_1_s, p.toInt()), style = MaterialTheme.typography.labelSmall, color = Harbor.TextDim)
        }
    }
}

@Composable
private fun AddLibraryDialog(onDismiss: () -> Unit, onAdd: (String, LibraryType, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("/media/") }
    var type by remember { mutableStateOf(LibraryType.Movies) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_library)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.display_name)) }, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LibraryType.entries.forEach { t -> FilterChip(t == type, { type = t }, label = { Text(t.label) }) }
                }
                OutlinedTextField(path, { path = it }, label = { Text(stringResource(R.string.folder_on_the_server)) }, singleLine = true)
                Text(stringResource(R.string.the_path_as_the_jellyfin_server), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton({ onAdd(name, type, path) }, enabled = name.isNotBlank() && path.startsWith("/")) { Text(stringResource(R.string.add)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ------------------------------------------------------------------ tasks, activity, devices, plugins, keys

@Composable
internal fun TaskRow(t: ScheduledTask, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().glass().padding(14.dp).testTag("task_${t.name}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.name, fontWeight = FontWeight.SemiBold)
                val last = t.last
                Text(
                    when {
                        t.running -> stringResource(R.string.running)
                        t.cancelling -> stringResource(R.string.cancelling)
                        last != null -> "${last.status} · ${relativeTime(last.end)}"
                        else -> stringResource(R.string.never_run)
                    },
                    color = when { t.running -> Harbor.Mint; last?.status == "Failed" -> Harbor.Rose; else -> Harbor.TextDim },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onToggle, enabled = !t.cancelling) {
                Icon(if (t.running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (t.running) stringResource(R.string.stop_1_s, t.name) else stringResource(R.string.run_1_s, t.name), tint = if (t.running) Harbor.Rose else Harbor.Mint)
            }
        }
        if (t.running) {
            Spacer(Modifier.height(6.dp))
            val p = t.progress
            if (p != null) com.sridhar.harbor.ui.components.TideBar(Modifier.fillMaxWidth(), progress = { (p / 100).toFloat() })
            else com.sridhar.harbor.ui.components.TideBar(Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun ActivityRow(e: ActivityEntry) {
    val tint = when (e.severity) { "Error", "Critical" -> Harbor.Rose; "Warning" -> Harbor.Amber; else -> when {
        e.type.contains("Authentication") -> Harbor.Sky; e.type.contains("Playback") -> Harbor.Mint; else -> Harbor.Violet } }
    ListCard {
        Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(e.overview, relativeTime(e.date)).joinToString(" · "), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
    }
}

@Composable
internal fun DeviceRow(d: DeviceInfo, onRemove: () -> Unit) {
    ListCard {
        Icon(Icons.Rounded.Devices, null, tint = Harbor.Sky)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.customName ?: d.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull("${d.appName} ${d.appVersion}".trim(), d.lastUserName, relativeTime(d.lastActivity)).joinToString(" · "),
                color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
        IconButton(onRemove) { Icon(Icons.Rounded.Delete, stringResource(R.string.sign_out_1_s, d.name), tint = Harbor.Rose) }
    }
}

@Composable
internal fun PluginRow(p: PluginInfo, onToggle: (Boolean) -> Unit) {
    ListCard {
        Icon(Icons.Rounded.Extension, null, tint = Harbor.Violet)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${p.name}  ", fontWeight = FontWeight.SemiBold)
            Text("v${p.version} · ${p.status}", color = if (p.status == "Malfunctioned") Harbor.Rose else Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
            if (p.description.isNotBlank()) Text(p.description, color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // Built-in (non-uninstallable) plugins can't be disabled from the API either.
        if (p.canUninstall) Switch(p.enabled, onToggle)
    }
}

@Composable
internal fun ApiKeyRow(k: ApiKey, onRevoke: () -> Unit) {
    val clip = LocalClipboardManager.current
    ListCard {
        Icon(Icons.Rounded.Key, null, tint = Harbor.Amber)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(k.appName, fontWeight = FontWeight.SemiBold)
            Text(k.token.take(6) + "••••••" + k.token.takeLast(4) + " · " + relativeTime(k.created), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        IconButton({ clip.setText(AnnotatedString(k.token)) }) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.copy_key)) }
        IconButton(onRevoke) { Icon(Icons.Rounded.Delete, stringResource(R.string.revoke_key), tint = Harbor.Rose) }
    }
}

@Composable
internal fun TextPromptDialog(title: String, label: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var v by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(v, { v = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton({ onConfirm(v.trim()) }, enabled = v.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun LogViewer(name: String, load: suspend () -> String, onDismiss: () -> Unit) {
    var text by remember(name) { mutableStateOf<String?>(null) }
    LaunchedEffect(name) { text = load() }
    androidx.compose.ui.window.Dialog(onDismiss, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Harbor.Ink)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val t = text
            if (t == null) Box(Modifier.fillMaxSize(), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() }
            else SelectionContainer {
                val scroll = rememberScrollState()
                LaunchedEffect(t) { scroll.scrollTo(scroll.maxValue) }
                Text(t, Modifier.fillMaxSize().verticalScroll(scroll).horizontalScroll(rememberScrollState()).padding(12.dp).heightIn(min = 100.dp),
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, color = Color(0xFFD7D7E3), softWrap = false)
            }
        }
    }
}
