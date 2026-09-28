package com.sridhar.harbor.ui.profile

import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sridhar.harbor.data.ServerConfig
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.glass
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(onDoctor: () -> Unit, onAssistant: () -> Unit, onTerminal: () -> Unit, onSetup: () -> Unit, onUsers: () -> Unit, onOffline: () -> Unit, onAdmin: () -> Unit = {}, onRemote: () -> Unit = {}) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    val scope = rememberCoroutineScope()
    var isAdmin by remember { mutableStateOf(false) }
    LaunchedEffect(cfg.jellyfinToken) { isAdmin = cfg.jellyfinReady && container.jellyfin.isAdmin() }
    val offline by container.offline.entries.collectAsState(emptyList())
    var confirmSignOut by remember { mutableStateOf(false) }
    var authorize by remember { mutableStateOf(false) }
    var parental by remember { mutableStateOf(false) }

    val qbitVersion by produceState<String?>(null, cfg.qbitUrl) {
        value = if (cfg.qbitReady) runCatching { container.qbit.login() }.getOrNull() else null
    }
    val seerrUser by produceState<String?>(null, cfg.seerrUrl) {
        value = if (cfg.seerrReady) runCatching { container.seerr.me().name }.getOrNull() else null
    }

    Column(Modifier.fillMaxSize().background(Harbor.Ink).verticalScroll(rememberScrollState()).statusBarsPadding().padding(20.dp).padding(bottom = 120.dp + com.sridhar.harbor.ui.components.LocalMiniPlayerInset.current)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(Harbor.accent), contentAlignment = Alignment.Center) {
                if (cfg.jellyfinReady) NetImage("${cfg.jellyfinUrl}/Users/${cfg.jellyfinUserId}/Images/Primary?maxWidth=200", Modifier.size(72.dp),
                    fallback = cfg.jellyfinUser.take(1).uppercase())
                else Text(cfg.jellyfinUser.take(1).uppercase().ifBlank { "H" }, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                // Long names (emails) step down a size and never break mid-word.
                val name = cfg.jellyfinUser.ifBlank { stringResource(R.string.guest) }
                Text(name, style = if (name.length > 16) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(stringResource(R.string.jellyverse_your_whole_media_universe), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.services), style = MaterialTheme.typography.labelSmall, color = Harbor.TextDim)
        Spacer(Modifier.height(8.dp))
        ServiceRow(stringResource(R.string.jellyfin), cfg.jellyfinUrl, cfg.jellyfinReady, if (cfg.jellyfinReady) stringResource(R.string.signed_in) else null, Harbor.Violet)
        ServiceRow(stringResource(R.string.qbittorrent), cfg.qbitUrl, cfg.qbitReady && qbitVersion != null, qbitVersion, Harbor.Sky)
        ServiceRow(stringResource(R.string.jellyseerr), cfg.seerrUrl, cfg.seerrReady && seerrUser != null, seerrUser?.let { stringResource(R.string.as_1_s, it) }, Harbor.Coral)
        ServiceRow(stringResource(R.string.sonarr), cfg.sonarrUrl, cfg.sonarrReady, if (cfg.sonarrReady) stringResource(R.string.api_key) else null, Harbor.Sky)
        ServiceRow(stringResource(R.string.radarr), cfg.radarrUrl, cfg.radarrReady, if (cfg.radarrReady) stringResource(R.string.api_key) else null, Harbor.Amber)
        val ssh = container.ssh.primary
        ServiceRow("SSH", ssh?.let { "${it.user}@${it.host}:${it.port}" }.orEmpty(), ssh != null, ssh?.name, Harbor.Mint)

        Spacer(Modifier.height(16.dp))
        com.sridhar.harbor.ui.ai.ModelCard()
        Spacer(Modifier.height(24.dp))
        com.sridhar.harbor.ui.components.AppearancePicker(Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        com.sridhar.harbor.ui.components.LanguagePicker(Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.alerts_privacy), style = MaterialTheme.typography.labelSmall, color = Harbor.TextDim)
        Spacer(Modifier.height(8.dp))
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val alertPrefs = remember { com.sridhar.harbor.alerts.AlertPrefs(ctx) }
        val lockPrefs = remember { com.sridhar.harbor.ui.lock.LockPrefs(ctx) }
        var aDl by remember { mutableStateOf(alertPrefs.downloads) }
        var aRq by remember { mutableStateOf(alertPrefs.requests) }
        var aLab by remember { mutableStateOf(alertPrefs.homelab) }
        var lock by remember { mutableStateOf(lockPrefs.enabled) }
        Column(Modifier.glass()) {
            ToggleRow(stringResource(R.string.download_finished), stringResource(R.string.qbittorrent_aria2), aDl) { aDl = it; alertPrefs.downloads = it; com.sridhar.harbor.alerts.Alerts.schedule(ctx) }
            ToggleRow(stringResource(R.string.request_available), stringResource(R.string.when_jellyseerr_requests_land_in_your), aRq) { aRq = it; alertPrefs.requests = it; com.sridhar.harbor.alerts.Alerts.schedule(ctx) }
            ToggleRow(stringResource(R.string.homelab_alerts), stringResource(R.string.disk_full_overheating_containers_down), aLab) { aLab = it; alertPrefs.homelab = it; com.sridhar.harbor.alerts.Alerts.schedule(ctx) }
            ToggleRow(stringResource(R.string.app_lock), stringResource(R.string.fingerprint_pin_after_1_minute_away), lock) { lock = it; lockPrefs.enabled = it }
            if (container.updater.enabled) {
                val autoUpd by container.updater.autoCheckFlow.collectAsState()
                ToggleRow(stringResource(R.string.update_auto), stringResource(R.string.update_auto_hint), autoUpd) {
                    container.updater.autoCheck = it; com.sridhar.harbor.update.UpdateWorker.schedule(ctx)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.library), style = MaterialTheme.typography.labelSmall, color = Harbor.TextDim)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.glass()) {
            MenuRow(Icons.Rounded.AutoFixHigh, stringResource(R.string.library_doctor), stringResource(R.string.fix_names_missing_posters_with_ai), onDoctor)
            MenuRow(Icons.Rounded.AutoAwesome, stringResource(R.string.jellyverse_ai), stringResource(R.string.on_device_qwen_assistant), onAssistant)
            MenuRow(Icons.Rounded.DownloadForOffline, stringResource(R.string.offline_downloads), stringResource(R.string.s_1_s_saved_on_this_phone, offline.size), onOffline)
            if (isAdmin) MenuRow(Icons.Rounded.AdminPanelSettings, stringResource(R.string.server_dashboard), stringResource(R.string.jellyfin_users_live_sessions_libraries_tasks), onAdmin)
            if (cfg.seerrReady) MenuRow(Icons.Rounded.Group, stringResource(R.string.manage_users), stringResource(R.string.jellyseerr_accounts_permissions), onUsers)
            if (cfg.jellyfinReady) MenuRow(Icons.Rounded.Devices, stringResource(R.string.quick_connect), stringResource(R.string.sign_in_a_tv_or_device)) { authorize = true }
            MenuRow(Icons.Rounded.SettingsRemote, stringResource(R.string.remote_title), stringResource(R.string.remote_menu_sub), onRemote)
            com.sridhar.harbor.update.updateStatus()?.let { status ->
                MenuRow(Icons.Rounded.SystemUpdate, stringResource(R.string.update_check), status) {
                    scope.launch(com.sridhar.harbor.CrashGuard) { container.updater.check(userInitiated = true) }
                }
            }
            if (cfg.jellyfinReady) MenuRow(Icons.Rounded.FamilyRestroom, stringResource(R.string.parental_title), com.sridhar.harbor.ui.parental.parentalSummary()) { parental = true }
            MenuRow(Icons.Rounded.Terminal, stringResource(R.string.ssh_hosts), stringResource(R.string.terminal_logins_host_keys), onTerminal)
            MenuRow(Icons.Rounded.Dns, stringResource(R.string.servers_accounts), stringResource(R.string.change_addresses_or_sign_in_again), onSetup)
            MenuRow(Icons.AutoMirrored.Rounded.Logout, stringResource(R.string.sign_out_everywhere), stringResource(R.string.forget_all_servers_on_this_device)) { confirmSignOut = true }
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.jellyverse_1_s, com.sridhar.harbor.BuildConfig.VERSION_NAME), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
            com.sridhar.harbor.ui.components.MadeWithLove()
        }
    }

    if (authorize) com.sridhar.harbor.ui.quickconnect.AuthorizeDeviceDialog { authorize = false }
    if (parental) com.sridhar.harbor.ui.parental.ParentalSettingsDialog { parental = false }
    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false },
        title = { Text(stringResource(R.string.sign_out)) },
        text = { Text(stringResource(R.string.jellyverse_will_forget_every_server_and)) },
        confirmButton = { TextButton({
            confirmSignOut = false
            scope.launch(com.sridhar.harbor.CrashGuard) { container.settings.update { ServerConfig(deviceId = it.deviceId) }; onSetup() }
        }) { Text(stringResource(R.string.sign_out_2), color = Harbor.Rose) } },
        dismissButton = { TextButton({ confirmSignOut = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall) }
        androidx.compose.material3.Switch(checked, onChange)
    }
}

@Composable
private fun ServiceRow(name: String, url: String, ok: Boolean, detail: String?, tint: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Harbor.Surface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) Harbor.Mint else if (url.isBlank()) Harbor.TextDim.copy(alpha = .4f) else Harbor.Rose))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(url.ifBlank { stringResource(R.string.not_configured) }, color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
        }
        detail?.let { Text(it, color = tint, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Harbor.Violet.copy(.3f), Harbor.Coral.copy(.3f)))),
            contentAlignment = Alignment.Center) { Icon(icon, null, tint = Harbor.Fg) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall) }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Harbor.TextDim)
    }
}
