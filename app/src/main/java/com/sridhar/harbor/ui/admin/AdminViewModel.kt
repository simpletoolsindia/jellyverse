package com.sridhar.harbor.ui.admin

import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sridhar.harbor.data.jellyfin.admin.ActivityEntry
import com.sridhar.harbor.data.jellyfin.admin.AdminSession
import com.sridhar.harbor.data.jellyfin.admin.AdminUser
import com.sridhar.harbor.data.jellyfin.admin.ApiKey
import com.sridhar.harbor.data.jellyfin.admin.DeviceInfo
import com.sridhar.harbor.data.jellyfin.admin.ItemCounts
import com.sridhar.harbor.data.jellyfin.admin.JellyfinAdminRepository
import com.sridhar.harbor.data.jellyfin.admin.LibraryFolder
import com.sridhar.harbor.data.jellyfin.admin.LibraryType
import com.sridhar.harbor.data.jellyfin.admin.LogFile
import com.sridhar.harbor.data.jellyfin.admin.PlayCommand
import com.sridhar.harbor.data.jellyfin.admin.PluginInfo
import com.sridhar.harbor.data.jellyfin.admin.PolicyEditor
import com.sridhar.harbor.data.jellyfin.admin.ScheduledTask
import com.sridhar.harbor.data.jellyfin.admin.SystemInfo
import com.sridhar.harbor.ui.components.friendly
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AdminTab(@androidx.annotation.StringRes val labelRes: Int, val live: Boolean = false) {
    Overview(R.string.overview, live = true), Playing(R.string.now_playing, live = true), Users(R.string.users), Libraries(R.string.libraries, live = true),
    Tasks(R.string.tasks, live = true), Activity(R.string.activity), Devices(R.string.devices), Plugins(R.string.plugins), ApiKeys(R.string.api_keys), Logs(R.string.logs),;
    val label: String get() = com.sridhar.harbor.L10n.s(labelRes)
}

/** Holds one async section's state: data, first-load spinner and error. */
class Loadable<T> {
    var data by mutableStateOf<T?>(null); internal set
    var error by mutableStateOf<String?>(null); internal set
    var loading by mutableStateOf(false); internal set
}

class AdminViewModel(private val repo: JellyfinAdminRepository, val selfUserId: String) : ViewModel() {
    var tab by mutableStateOf(AdminTab.Overview); private set
    var message by mutableStateOf<String?>(null)

    val info = Loadable<SystemInfo>()
    val counts = Loadable<ItemCounts>()
    val sessions = Loadable<List<AdminSession>>()
    val users = Loadable<List<AdminUser>>()
    val libraries = Loadable<List<LibraryFolder>>()
    val tasks = Loadable<List<ScheduledTask>>()
    val activity = Loadable<List<ActivityEntry>>()
    val devices = Loadable<List<DeviceInfo>>()
    val plugins = Loadable<List<PluginInfo>>()
    val keys = Loadable<List<ApiKey>>()
    val logs = Loadable<List<LogFile>>()

    private var poller: Job? = null

    init { select(AdminTab.Overview) }

    fun select(t: AdminTab) {
        tab = t
        refresh()
        poller?.cancel()
        // Live tabs refresh in the background – like the web dashboard's websocket updates.
        if (t.live) poller = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
            while (isActive) { delay(if (t == AdminTab.Playing) 3_000 else 5_000); refresh(quiet = true) }
        }
    }

    fun refresh(quiet: Boolean = false) {
        when (tab) {
            AdminTab.Overview -> { load(info, quiet) { repo.systemInfo() }; load(counts, quiet) { repo.counts() }; load(sessions, quiet) { repo.sessions() }; load(users, true) { repo.users() } }
            AdminTab.Playing -> load(sessions, quiet) { repo.sessions() }
            AdminTab.Users -> load(users, quiet) { repo.users() }
            AdminTab.Libraries -> load(libraries, quiet) { repo.libraries().also(::settleQueued) }
            AdminTab.Tasks -> load(tasks, quiet) { repo.tasks() }
            AdminTab.Activity -> load(activity, quiet) { repo.activity().items }
            AdminTab.Devices -> load(devices, quiet) { repo.devices() }
            AdminTab.Plugins -> load(plugins, quiet) { repo.plugins() }
            AdminTab.ApiKeys -> load(keys, quiet) { repo.apiKeys() }
            AdminTab.Logs -> load(logs, quiet) { repo.logFiles() }
        }
    }

    private fun <T> load(target: Loadable<T>, quiet: Boolean, block: suspend () -> T) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        if (!quiet || target.data == null) target.loading = true
        runCatching { block() }
            .onSuccess { target.data = it; target.error = null }
            .onFailure { if (!quiet || target.data == null) target.error = it.friendly() }
        target.loading = false
    }

    private fun act(done: String?, block: suspend () -> Unit) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        runCatching { block() }.onSuccess { done?.let { message = it }; refresh(quiet = true) }.onFailure { message = it.friendly() }
    }

    // ---- server ----
    fun restart() = act(L10n.s(R.string.jellyfin_is_restarting)) { repo.restart() }
    fun shutdown() = act(L10n.s(R.string.jellyfin_is_shutting_down)) { repo.shutdown() }

    // ---- sessions ----
    fun command(s: AdminSession, cmd: PlayCommand) = act(null) { repo.command(s.id, cmd) }
    fun seekBy(s: AdminSession, seconds: Int) = act(null) {
        val pos = (s.playState.positionTicks ?: 0) + seconds * 10_000_000L
        repo.command(s.id, PlayCommand.Seek, pos.coerceIn(0, s.nowPlaying?.runTimeTicks ?: Long.MAX_VALUE))
    }
    fun sendMessage(s: AdminSession, header: String, text: String) = act(L10n.s(R.string.message_sent_to_1_s, s.deviceName)) { repo.message(s.id, header, text) }

    // ---- users ----
    fun validateNewUser(name: String, password: String) = PolicyEditor.validateNewUser(name, password, users.data.orEmpty().map { it.name })
    fun createUser(name: String, password: String, onCreated: (AdminUser) -> Unit = {}) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        runCatching { repo.createUser(name, password) }
            .onSuccess { message = L10n.s(R.string.s_1_s_created, it.name); refresh(quiet = true); onCreated(it) }
            .onFailure { message = it.friendly() }
    }

    // ---- libraries ----
    /**
     * Libraries asked to scan that Jellyfin hasn't started yet. It scans one library at a time and reports no
     * progress for the waiting ones, so without this a second scan looked like it did nothing. itemId → asked at.
     */
    val queued = androidx.compose.runtime.mutableStateMapOf<String, Long>()

    /** Drops a library from [queued] once its scan is running (it shows real progress then), or once nothing is
     *  scanning any more (the queue has drained – quick scans can finish between two polls). */
    private fun settleQueued(libs: List<LibraryFolder>) {
        val now = System.currentTimeMillis()
        val anyActive = libs.any { it.refreshStatus == "Active" }
        libs.filter { it.refreshStatus == "Active" }.forEach { queued.remove(it.itemId) }
        queued.entries.removeAll { (_, at) -> (!anyActive && now - at > 6_000) || now - at > 30 * 60_000L }
    }

    fun scanAll() = act(L10n.s(R.string.scanning_all_libraries)) {
        repo.scanAll(); libraries.data.orEmpty().forEach { queued[it.itemId] = System.currentTimeMillis() }
    }
    fun scan(l: LibraryFolder) {
        val busy = libraries.data.orEmpty().any { it.refreshStatus == "Active" && it.itemId != l.itemId }
        act(if (busy) L10n.s(R.string.scan_queued_msg, l.name) else L10n.s(R.string.scanning_1_s_2, l.name)) {
            repo.scan(l); queued[l.itemId] = System.currentTimeMillis()
        }
    }
    fun addLibrary(name: String, type: LibraryType, path: String) = act(L10n.s(R.string.library_1_s_added, name)) { repo.addLibrary(name, type, path) }
    fun removeLibrary(l: LibraryFolder) = act(L10n.s(R.string.s_1_s_removed_files_are_untouched, l.name)) { repo.removeLibrary(l.name) }

    // ---- tasks ----
    fun toggleTask(t: ScheduledTask) = act(if (t.running) L10n.s(R.string.stopping_1_s, t.name) else L10n.s(R.string.started_1_s, t.name)) {
        if (t.running) repo.stopTask(t.id) else repo.startTask(t.id)
    }

    // ---- devices / plugins / keys ----
    fun removeDevice(d: DeviceInfo) = act(L10n.s(R.string.s_1_s_signed_out, d.name)) { repo.deleteDevice(d.id) }
    fun setPlugin(p: PluginInfo, on: Boolean) = act("${p.name} ${if (on) "enabled" else "disabled"} – restart Jellyfin to apply") { repo.setPluginEnabled(p, on) }
    fun createKey(app: String) = act(L10n.s(R.string.api_key_created_for_1_s, app)) { repo.createApiKey(app) }
    fun revokeKey(k: ApiKey) = act(L10n.s(R.string.key_for_1_s_revoked, k.appName)) { repo.revokeApiKey(k.token) }

    suspend fun logTail(name: String): String = runCatching { repo.logTail(name) }.getOrElse { it.friendly() }

    override fun onCleared() { poller?.cancel() }
}
