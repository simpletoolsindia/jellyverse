package com.sridhar.harbor.ui.arr

import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.arr.ArrEntry
import com.sridhar.harbor.data.arr.ArrKind
import com.sridhar.harbor.data.arr.ArrMovie
import com.sridhar.harbor.data.arr.ArrRelease
import com.sridhar.harbor.data.arr.ArrSeries
import com.sridhar.harbor.data.arr.DiskSpace
import com.sridhar.harbor.data.arr.HealthItem
import com.sridhar.harbor.data.arr.QueueItem
import com.sridhar.harbor.ui.components.friendly
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Loading / content / error with a stable branch key for AnimatedContent. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}
val Load<*>.branch get() = when (this) { Load.Loading -> "loading"; is Load.Ready -> "ready"; is Load.Failed -> "error" }

enum class ManageTab(@androidx.annotation.StringRes val labelRes: Int) { Queue(R.string.queue), Upcoming(R.string.upcoming), Wanted(R.string.wanted), Movies(R.string.movies), Series(R.string.series);
    val label: String get() = com.sridhar.harbor.L10n.s(labelRes)
}

data class Queued(val kind: ArrKind, val item: QueueItem)

class ManageViewModel(private val c: AppContainer) : ViewModel() {
    var tab by mutableStateOf(ManageTab.Queue)
    var queue by mutableStateOf<Load<List<Queued>>>(Load.Loading); private set
    var upcoming by mutableStateOf<Load<List<ArrEntry>>>(Load.Loading); private set
    var wanted by mutableStateOf<Load<List<ArrEntry>>>(Load.Loading); private set
    var movies by mutableStateOf<Load<List<ArrMovie>>>(Load.Loading); private set
    var series by mutableStateOf<Load<List<ArrSeries>>>(Load.Loading); private set
    var disks by mutableStateOf<List<DiskSpace>>(emptyList()); private set
    var health by mutableStateOf<List<Pair<ArrKind, HealthItem>>>(emptyList()); private set
    var versions by mutableStateOf<Map<ArrKind, String>>(emptyMap()); private set
    var message by mutableStateOf<String?>(null)

    private suspend fun kinds() = ArrKind.entries.filter { c.arr(it).configured() }

    init { overview(); load(ManageTab.Queue); load(ManageTab.Upcoming); load(ManageTab.Wanted) }

    fun overview() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        val ks = kinds()
        versions = ks.associateWith { k -> runCatching { c.arr(k).status().version }.getOrDefault("offline") }
        // Sonarr & Radarr often report the same physical disk under different mounts – dedupe by size.
        disks = ks.flatMap { k -> runCatching { c.arr(k).disk() }.getOrDefault(emptyList()) }
            .filter { it.totalSpace > 10L shl 30 }.distinctBy { it.totalSpace to it.freeSpace / (1L shl 30) }
        health = ks.flatMap { k -> runCatching { c.arr(k).health() }.getOrDefault(emptyList()).map { k to it } }
    }

    fun load(t: ManageTab = tab, quiet: Boolean = false) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        suspend fun <T> gather(block: suspend (ArrKind) -> List<T>): Result<List<T>> = runCatching {
            coroutineScope { kinds().map { k -> async { block(k) } }.awaitAll().flatten() }
        }
        fun <T> Result<T>.toLoad(): Load<T> = fold({ Load.Ready(it) }, { Load.Failed(it.friendly()) })
        when (t) {
            ManageTab.Queue -> {
                if (!quiet) queue = Load.Loading
                val r = gather { k -> c.arr(k).queue().map { Queued(k, it) } }
                if (!(quiet && r.isFailure)) queue = r.toLoad()
            }
            ManageTab.Upcoming -> { upcoming = Load.Loading; upcoming = gather { c.arr(it).upcoming() }.map { l -> l.sortedBy { it.date } }.toLoad() }
            ManageTab.Wanted -> { wanted = Load.Loading; wanted = gather { c.arr(it).missing() }.toLoad() }
            ManageTab.Movies -> { movies = Load.Loading; movies = runCatching { c.radarr.movies().sortedBy { it.title.lowercase().removePrefix("the ") } }.toLoad() }
            ManageTab.Series -> { series = Load.Loading; series = runCatching { c.sonarr.series().sortedBy { it.title.lowercase().removePrefix("the ") } }.toLoad() }
        }
    }

    fun select(t: ManageTab) {
        tab = t
        val state = when (t) { ManageTab.Movies -> movies; ManageTab.Series -> series; ManageTab.Queue -> queue; ManageTab.Upcoming -> upcoming; ManageTab.Wanted -> wanted }
        if (state !is Load.Ready) load(t)
    }

    private fun act(msg: String, reload: ManageTab? = null, block: suspend () -> Unit) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        runCatching { block() }.onSuccess { message = msg; reload?.let { load(it, quiet = true) } }.onFailure { message = it.friendly() }
    }

    fun remove(q: Queued, fromClient: Boolean, blocklist: Boolean) =
        act(if (blocklist) L10n.s(R.string.removed_blocklisted) else L10n.s(R.string.removed_from_queue), ManageTab.Queue) { c.arr(q.kind).removeFromQueue(q.item.id, fromClient, blocklist) }

    fun search(e: ArrEntry) = act(L10n.s(R.string.searching_for_1_s, e.title)) {
        if (e.movieId != null) c.radarr.searchMovie(e.movieId) else e.episodeId?.let { c.sonarr.searchEpisodes(listOf(it)) }
    }

    fun searchAllMissing(k: ArrKind) = act(L10n.s(R.string.s_1_s_searching_all_missing, k.label)) { c.arr(k).searchAllMissing() }

    suspend fun releasesFor(e: ArrEntry): List<ArrRelease> =
        if (e.movieId != null) c.radarr.movieReleases(e.movieId) else c.sonarr.episodeReleases(e.episodeId!!)

    fun grab(kind: ArrKind, r: ArrRelease) = act(L10n.s(R.string.sent_to_download_client), ManageTab.Queue) { c.arr(kind).grab(r) }
}
