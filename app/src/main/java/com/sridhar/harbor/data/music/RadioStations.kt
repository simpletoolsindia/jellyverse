package com.sridhar.harbor.data.music

import android.content.Context
import com.sridhar.harbor.data.HarborJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID

@Serializable
data class RadioStation(val id: String = UUID.randomUUID().toString(), val name: String, val url: String) {
    /** Radio plays through the music engine like any song. */
    fun toSong() = Song(id = "radio:$id", title = name, artist = "Live radio", streamUrl = url)
}

/** User-saved internet / FM radio stations (any Icecast/Shoutcast/HLS stream link). */
class RadioStations(context: Context, private val http: OkHttpClient) {
    private val prefs = context.getSharedPreferences("harbor_radio", Context.MODE_PRIVATE)
    private val ser = ListSerializer(RadioStation.serializer())
    private val _stations = MutableStateFlow(load())
    val stations: StateFlow<List<RadioStation>> = _stations.asStateFlow()

    /**
     * The preset stations come from the public repo (see [RadioDirectory]), not from the app: downloaded once,
     * kept on the device, refreshed at most daily, and re-pulled when a station stops playing.
     */
    private val dirFile = java.io.File(context.filesDir, "radio/stations.json")
    /** Installs that got the old built-in presets already have them – the directory then only refreshes links. */
    private var seeded: Boolean
        get() = prefs.getBoolean("seeded_directory", false) || prefs.getBoolean("seeded_tamilnadu_v2", false) || prefs.getBoolean("seeded_tamil_v1", false)
        set(v) = prefs.edit().putBoolean("seeded_directory", v).apply()
    private var edited: Set<String>
        get() = prefs.getStringSet("edited_ids", emptySet()).orEmpty()
        set(v) = prefs.edit().putStringSet("edited_ids", v).apply()
    private val syncLock = kotlinx.coroutines.sync.Mutex()

    /** Downloads the directory (raw GitHub, then the jsDelivr mirror); null if neither answers with a valid list. */
    private suspend fun download(): Pair<String, List<RadioDirectory.Entry>>? = withContext(Dispatchers.IO) {
        for (url in RadioDirectory.URLS) {
            val text = runCatching {
                http.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
            }.getOrNull() ?: continue
            RadioDirectory.parse(text)?.let { return@withContext text to it }
        }
        null
    }

    private fun cached(): List<RadioDirectory.Entry>? = runCatching { dirFile.readText() }.getOrNull()?.let(RadioDirectory::parse)

    /**
     * Brings the preset stations up to date. Uses the copy on the device unless it's missing, older than a day or
     * [force]d; a failed download falls back to that copy. Returns true when a directory (fresh or cached) was applied.
     */
    suspend fun sync(force: Boolean = false): Boolean = syncLock.withLockCompat {
        val age = System.currentTimeMillis() - prefs.getLong("dir_fetched", 0)
        val fresh = if (force || !dirFile.exists() || age > 24 * 3_600_000L) download() else null
        if (fresh != null) withContext(Dispatchers.IO) {
            dirFile.parentFile?.mkdirs(); dirFile.writeText(fresh.first)
            prefs.edit().putLong("dir_fetched", System.currentTimeMillis()).apply()
        }
        val entries = fresh?.second ?: cached() ?: return@withLockCompat false
        // First directory sync after upgrading from built-in presets: keep links the user changed as theirs.
        if (!prefs.getBoolean("seeded_directory", false) && seeded) edited = edited + RadioDirectory.userEdited(_stations.value, entries)
        val merged = RadioDirectory.merge(_stations.value, entries, firstTime = !seeded, edited = edited)
        if (merged != _stations.value) save(merged)
        seeded = true
        true
    }

    /**
     * A preset station won't play: pull the directory again and, if the station's link changed there, switch to it.
     * Returns the updated station, or null when there's nothing new to try (offline, same link, user-edited station).
     */
    suspend fun relocate(id: String): RadioStation? {
        val before = byId(id) ?: return null
        if (id in edited) return null
        if (!sync(force = true)) return null
        return byId(id)?.takeIf { it.url != before.url }
    }

    private suspend fun <T> kotlinx.coroutines.sync.Mutex.withLockCompat(block: suspend () -> T): T { lock(); try { return block() } finally { unlock() } }

    /** Live list of working Tamil stations from Radio Browser (community directory, health-checked). */
    suspend fun discoverTamil(): List<RadioStation> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://de1.api.radio-browser.info/json/stations/search?tag=tamil&hidebroken=true&order=clickcount&reverse=true&limit=80")
            .header("User-Agent", "JellyVerse").build()
        http.newCall(req).execute().use { r ->
            val arr = HarborJson.parseToJsonElement(r.body?.string().orEmpty()) as? kotlinx.serialization.json.JsonArray ?: return@use emptyList()
            arr.mapNotNull { e ->
                val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                fun f(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
                val url = f("url_resolved")?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
                if ("zt=" in url || "zs=" in url) return@mapNotNull null   // tokenised links expire
                RadioStation(id = "rb-" + (f("stationuuid") ?: url.hashCode().toString()), name = f("name")?.trim().orEmpty().ifBlank { guessName(url) }, url = url)
            }.distinctBy { it.url }
        }
    }

    /** Adds a discovered station as-is (already a direct stream). */
    fun save(station: RadioStation) = save(_stations.value.filterNot { it.url == station.url } + station)

    private fun load(): List<RadioStation> =
        runCatching { HarborJson.decodeFromString(ser, prefs.getString("stations", "[]")!!) }.getOrDefault(emptyList())

    private fun save(list: List<RadioStation>) {
        prefs.edit().putString("stations", HarborJson.encodeToString(ser, list)).apply(); _stations.value = list
    }

    /** Saves a station, resolving .pls / .m3u playlist links to the actual stream first. */
    suspend fun add(name: String, url: String): RadioStation {
        val stream = resolve(url.trim())
        val station = RadioStation(name = name.trim().ifBlank { guessName(stream) }, url = stream)
        save(_stations.value.filterNot { it.url == stream } + station)
        return station
    }

    fun remove(station: RadioStation) = save(_stations.value.filterNot { it.id == station.id })

    /** Rename a station and/or change its stream link (playlist links are resolved like when adding). Keeps its place. */
    suspend fun update(id: String, name: String, url: String): RadioStation {
        val stream = resolve(url.trim())
        val updated = RadioStation(id = id, name = name.trim().ifBlank { guessName(stream) }, url = stream)
        edited = edited + id   // the user's own link wins over later directory updates
        save(_stations.value.map { if (it.id == id) updated else it })
        return updated
    }

    fun byId(id: String) = _stations.value.firstOrNull { it.id == id }

    private suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        val lower = url.substringBefore('?').lowercase()
        if (!lower.endsWith(".pls") && !lower.endsWith(".m3u")) return@withContext url
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { r -> parsePlaylist(r.body?.string().orEmpty()) }
        }.getOrNull() ?: url
    }

    companion object {
        /** First stream URL in a PLS ("File1=http…") or M3U (plain lines) playlist. */
        fun parsePlaylist(text: String): String? = text.lineSequence().map { it.trim() }
            .map { if (it.startsWith("File", true) && '=' in it) it.substringAfter('=').trim() else it }
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }

        fun guessName(url: String): String = runCatching { java.net.URI(url).host.removePrefix("www.") }.getOrNull() ?: "Radio"

        /** Pulls the first http(s) link out of shared text ("Listen live: https://…"). */
        fun linkIn(text: String): String? = Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')', ']')
    }
}
