package com.sridhar.harbor.ui.torrents

import androidx.compose.runtime.mutableIntStateOf
import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.qbit.AddTorrentRequest
import com.sridhar.harbor.data.qbit.QbitCategory
import com.sridhar.harbor.data.qbit.QbitPrefs
import com.sridhar.harbor.data.qbit.Torrent
import com.sridhar.harbor.data.qbit.TorrentFile
import com.sridhar.harbor.data.qbit.TorrentPhase
import com.sridhar.harbor.data.qbit.TransferInfo
import com.sridhar.harbor.ui.components.friendly
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

enum class TFilter(@androidx.annotation.StringRes val labelRes: Int) { All(R.string.all), Downloading(R.string.downloading), Seeding(R.string.seeding), Paused(R.string.paused), Completed(R.string.completed), Errored(R.string.errored);
    val label: String get() = com.sridhar.harbor.L10n.s(labelRes)
}
enum class TSort(@androidx.annotation.StringRes val labelRes: Int) { Added(R.string.recently_added), Name(R.string.name), Progress(R.string.progress_2), Speed(R.string.speed), Size(R.string.size);
    val label: String get() = com.sridhar.harbor.L10n.s(labelRes)
}

class TorrentsViewModel(private val c: AppContainer) : ViewModel() {
    var torrents by mutableStateOf<List<Torrent>>(emptyList()); private set
    var transfer by mutableStateOf(TransferInfo()); private set
    var altSpeed by mutableStateOf(false); private set
    var prefs by mutableStateOf(QbitPrefs()); private set
    var categories by mutableStateOf<List<QbitCategory>>(emptyList()); private set
    var error by mutableStateOf<String?>(null); private set
    var loaded by mutableStateOf(false); private set
    var filter by mutableStateOf(TFilter.All)
    var sort by mutableStateOf(TSort.Added)
    var query by mutableStateOf("")
    var selection by mutableStateOf<Set<String>>(emptySet())
    var message by mutableStateOf<String?>(null)

    val dlHistory = ArrayDeque<Long>()
    val ulHistory = ArrayDeque<Long>()
    var historyVersion by mutableIntStateOf(0); private set

    fun matches(t: Torrent, f: TFilter) = when (f) {
        TFilter.All -> true
        TFilter.Downloading -> t.progress < 1f && (t.phase == TorrentPhase.Downloading || t.phase == TorrentPhase.Waiting)
        TFilter.Seeding -> t.phase == TorrentPhase.Seeding
        TFilter.Paused -> t.isStopped
        TFilter.Completed -> t.progress >= 1f
        TFilter.Errored -> t.phase == TorrentPhase.Error
    }

    fun count(f: TFilter) = torrents.count { matches(it, f) }

    val visible: List<Torrent>
        get() = torrents.filter { t -> matches(t, filter) && (query.isBlank() || t.name.contains(query, ignoreCase = true)) }
            .let { list ->
                when (sort) {
                    TSort.Added -> list.sortedByDescending { it.addedOn }
                    TSort.Name -> list.sortedBy { it.name.lowercase() }
                    TSort.Progress -> list.sortedByDescending { it.progress }
                    TSort.Speed -> list.sortedByDescending { it.dlspeed + it.upspeed }
                    TSort.Size -> list.sortedByDescending { it.size }
                }
            }

    suspend fun refresh() {
        runCatching {
            coroutineScope {
                val t = async { c.qbit.torrents() }
                val tr = async { c.qbit.transfer() }
                val alt = async { runCatching { c.qbit.altSpeedEnabled() }.getOrDefault(altSpeed) }
                torrents = t.await(); transfer = tr.await(); altSpeed = alt.await()
            }
        }.onSuccess {
            error = null; loaded = true
            push(dlHistory, transfer.dlSpeed); push(ulHistory, transfer.upSpeed); historyVersion++
            selection = selection.intersect(torrents.map { it.hash }.toSet())
        }.onFailure { error = it.friendly(); loaded = true }
    }

    private fun push(q: ArrayDeque<Long>, v: Long) { q.addLast(v); while (q.size > 60) q.removeFirst() }

    fun loadMeta() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        runCatching { prefs = c.qbit.preferences() }
        categories = c.qbit.categories()
    }

    private fun act(msg: String? = null, block: suspend () -> Unit) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        runCatching { block() }.onSuccess { msg?.let { message = it }; refresh() }.onFailure { message = it.friendly() }
    }

    fun toggle(t: Torrent) = act { if (t.isStopped) c.qbit.resume(listOf(t.hash)) else c.qbit.pause(listOf(t.hash)) }
    fun pause(h: List<String>) = act(L10n.s(R.string.paused)) { c.qbit.pause(h) }
    fun resume(h: List<String>) = act(L10n.s(R.string.resumed)) { c.qbit.resume(h) }
    fun delete(h: List<String>, files: Boolean) = act(if (files) L10n.s(R.string.deleted_with_files) else L10n.s(R.string.removed)) { c.qbit.delete(h, files); selection = emptySet() }
    fun recheck(h: String) = act(L10n.s(R.string.rechecking)) { c.qbit.recheck(listOf(h)) }
    fun reannounce(h: String) = act(L10n.s(R.string.reannounced)) { c.qbit.reannounce(listOf(h)) }
    fun toggleSequential(h: String) = act { c.qbit.toggleSequential(h) }
    fun forceStart(t: Torrent) = act { c.qbit.setForceStart(t.hash, !t.forceStart) }
    fun setCategory(h: String, cat: String) = act(L10n.s(R.string.category_set)) { c.qbit.setCategory(h, cat) }
    fun setTorrentLimits(h: String, dl: Long, up: Long) = act(L10n.s(R.string.limits_updated)) { c.qbit.setTorrentLimits(h, dl, up) }
    fun toggleAlt() = act { c.qbit.toggleAltSpeed() }
    fun setGlobal(dl: Long, up: Long) = act(L10n.s(R.string.speed_limits_saved)) { c.qbit.setGlobalLimits(dl, up) }
    fun setAlt(dl: Long, up: Long) = act(L10n.s(R.string.alternative_limits_saved)) { c.qbit.setAltLimits(dl, up); prefs = c.qbit.preferences() }
    fun add(req: AddTorrentRequest, onDone: () -> Unit) = act(L10n.s(R.string.torrent_added)) { c.qbit.add(req); onDone() }

    suspend fun files(hash: String): List<TorrentFile> = runCatching { c.qbit.files(hash) }.getOrDefault(emptyList())
    fun setFilePriority(hash: String, index: Int, prio: Int) = act { c.qbit.setFilePriority(hash, listOf(index), prio) }
}
