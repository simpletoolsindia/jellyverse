package com.sridhar.harbor.data.iptv

import android.content.Context
import com.sridhar.harbor.data.HarborJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

@Serializable
data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val epgUrl: String = "",
    val userAgent: String = "",
    /** Optional HTTP Basic credentials for private M3U providers. */
    val username: String = "",
    val password: String = "",
    /** Shipped free-channel directory (can be removed like any other). */
    val builtin: Boolean = false,
    /** What group-title means in this playlist: Category, Language or Country. */
    val groupLabel: String = "Category",
)

@Serializable
data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String = "Other",
    val tvgId: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
)

data class Program(val start: Long, val stop: Long, val title: String, val desc: String?) {
    val progress get() = ((System.currentTimeMillis() - start).toFloat() / (stop - start).coerceAtLeast(1)).coerceIn(0f, 1f)
}

data class NowNext(val now: Program?, val next: Program?)

/**
 * M3U / M3U8 playlists (incl. Xtream Codes) with optional XMLTV guide.
 * Parsed channels are cached on disk so the channel list opens instantly next time.
 */
class IptvRepository(private val context: Context, private val http: OkHttpClient) {
    private val prefs = context.getSharedPreferences("harbor_iptv", Context.MODE_PRIVATE)
    private val listSer = ListSerializer(Playlist.serializer())
    private val chSer = ListSerializer(Channel.serializer())
    private val _playlists = MutableStateFlow(load())
    val playlists: StateFlow<List<Playlist>> = _playlists
    private val channelCache = ConcurrentHashMap<String, List<Channel>>()
    private val epg = ConcurrentHashMap<String, List<Program>>()   // key = tvg-id (lowercase)
    private val epgLoaded = ConcurrentHashMap.newKeySet<String>()

    private fun load(): List<Playlist> {
        val saved = prefs.getString("playlists", null)?.let { runCatching { HarborJson.decodeFromString(listSer, it) }.getOrNull() }.orEmpty()
        if (!com.sridhar.harbor.BuildConfig.PRELOAD_IPTV || prefs.getInt("seeded", 0) >= SEED_VERSION) return saved
        // Preload free, publicly available channel directories (new ones are added on upgrade; removed ones stay removed).
        val known = prefs.getStringSet("seeded_ids", emptySet()).orEmpty()
        val seeded = saved + BUILTINS.filter { b -> b.id !in known && saved.none { it.id == b.id } }
        prefs.edit().putInt("seeded", SEED_VERSION).putStringSet("seeded_ids", known + BUILTINS.map { it.id })
            .putString("playlists", HarborJson.encodeToString(listSer, seeded)).apply()
        return seeded
    }

    companion object {
        const val SEED_VERSION = 2
        val BUILTINS = listOf(
            Playlist("builtin-iptvorg-world", "World · all languages", "https://iptv-org.github.io/iptv/index.language.m3u", builtin = true, groupLabel = "Language"),
            Playlist("builtin-freetv", "Free-TV · curated", "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8", builtin = true, groupLabel = "Country"),
            Playlist("builtin-iptvorg-tamil", "Tamil · iptv-org", "https://iptv-org.github.io/iptv/languages/tam.m3u", builtin = true),
            Playlist("builtin-iptvorg-india", "India · iptv-org", "https://iptv-org.github.io/iptv/countries/in.m3u", builtin = true),
            Playlist("builtin-prabhacap-tamil", "Tamil Local · prabhacap", "https://github.com/prabhacap/IPTV/releases/latest/download/playlist-tamil-local.m3u", builtin = true),
        )
    }
    private fun persist(list: List<Playlist>) { prefs.edit().putString("playlists", HarborJson.encodeToString(listSer, list)).apply(); _playlists.value = list }

    fun add(p: Playlist) = persist(_playlists.value.filterNot { it.id == p.id } + p)
    fun remove(p: Playlist) { persist(_playlists.value.filterNot { it.id == p.id }); cacheFile(p).delete(); channelCache.remove(p.id) }

    /** Xtream Codes → standard M3U + XMLTV URLs. */
    fun xtream(name: String, server: String, user: String, pass: String): Playlist {
        val base = server.trim().trimEnd('/').let { if (it.startsWith("http")) it else "http://$it" }
        return Playlist(name = name.ifBlank { base.substringAfter("://") }, url = "$base/get.php?username=$user&password=$pass&type=m3u_plus&output=ts",
            epgUrl = "$base/xmltv.php?username=$user&password=$pass")
    }

    val favorites: MutableStateFlow<Set<String>> = MutableStateFlow(prefs.getStringSet("favorites", emptySet()).orEmpty())
    fun toggleFavorite(ch: Channel) {
        val next = favorites.value.let { if (ch.id in it) it - ch.id else it + ch.id }
        favorites.value = next; prefs.edit().putStringSet("favorites", next).apply()
    }

    /** How often each channel was really watched (45 s+, so zapping doesn't count): id → (plays, last played). */
    val plays: MutableStateFlow<Map<String, Pair<Int, Long>>> = MutableStateFlow(
        prefs.getString("plays", null).orEmpty().lines().mapNotNull { l ->
            l.split('\t').takeIf { it.size == 3 }?.let { (id, n, t) -> id to ((n.toIntOrNull() ?: 0) to (t.toLongOrNull() ?: 0L)) }
        }.toMap())

    fun recordPlay(ch: Channel) {
        val cur = plays.value[ch.id]
        val next = (plays.value + (ch.id to ((cur?.first ?: 0) + 1 to System.currentTimeMillis())))
            .entries.sortedByDescending { it.value.second }.take(300).associate { it.key to it.value }
        plays.value = next
        prefs.edit().putString("plays", next.entries.joinToString("\n") { "${it.key}\t${it.value.first}\t${it.value.second}" }).apply()
    }

    /** A channel together with the playlist it belongs to (needed to tune it). */
    data class Tuned(val playlist: Playlist, val channel: Channel)

    /** Home-screen rows: picked for you, frequently watched and favourites – from already-downloaded lists only. */
    data class HomeRows(val forYou: List<Tuned>, val frequent: List<Tuned>, val favorites: List<Tuned>) {
        val isEmpty get() = forYou.isEmpty() && frequent.isEmpty() && favorites.isEmpty()
    }

    suspend fun homeRows(): HomeRows = withContext(Dispatchers.IO) {
        val all = _playlists.value.flatMap { p ->
            val list = channelCache[p.id] ?: cacheFile(p).takeIf { it.exists() }?.let { f ->
                runCatching { HarborJson.decodeFromString(chSer, f.readText()) }.getOrNull()?.also { channelCache[p.id] = it }
            }
            // Never opened Live TV yet: fetch the shipped free list so "Live TV for you" isn't empty on day one.
            (list ?: if (p.builtin) runCatching { channels(p) }.getOrNull() else null).orEmpty().map { Tuned(p, it) }
        }.filterNot { isOffline(it.channel) }
        if (all.isEmpty()) return@withContext HomeRows(emptyList(), emptyList(), emptyList())
        val byId = all.associateBy { it.channel.id }
        val pl = plays.value
        val frequent = pl.entries.filter { it.value.first >= 2 || pl.size < 6 }
            .sortedWith(compareByDescending<Map.Entry<String, Pair<Int, Long>>> { it.value.first }.thenByDescending { it.value.second })
            .mapNotNull { byId[it.key] }.take(15)
        val favs = favorites.value.mapNotNull { byId[it] }.take(30)
        // For you: more from the groups (language / category) you watch most, logos first, not already shown above.
        val shown = (frequent + favs).map { it.channel.id }.toSet()
        val watched = pl.entries.mapNotNull { e -> byId[e.key]?.channel?.group?.let { it to e.value.first } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        // Nothing watched yet: start from the app's and the phone's languages (Tamil / English …), not the alphabet.
        val langs = listOf(java.util.Locale.getDefault(), context.resources.configuration.locales[0])
            .mapNotNull { it?.getDisplayLanguage(java.util.Locale.ENGLISH)?.lowercase() } + "english"
        val groupWeight: (String) -> Int = { g -> watched[g] ?: if (langs.any { g.lowercase().contains(it) }) 1 else 0 }
        val forYou = all.asSequence().filter { it.channel.id !in shown }
            .sortedWith(compareByDescending<Tuned> { groupWeight(it.channel.group) }.thenByDescending { it.channel.logo != null }
                .thenByDescending { nowNext(it.channel).now != null })
            .distinctBy { it.channel.name.lowercase() }.take(15).toList()
        HomeRows(forYou, frequent, favs)
    }

    var lastChannel: String? get() = prefs.getString("last_channel", null); set(v) = prefs.edit().putString("last_channel", v).apply()

    private fun cacheFile(p: Playlist) = File(context.filesDir, "iptv/${p.id}.json").apply { parentFile?.mkdirs() }

    private fun open(url: String, ua: String?, user: String = "", pass: String = ""): InputStream {
        val req = Request.Builder().url(url).apply {
            ua?.takeIf { it.isNotBlank() }?.let { header("User-Agent", it) }
            if (user.isNotBlank()) header("Authorization", okhttp3.Credentials.basic(user, pass))
        }.build()
        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) { resp.close(); throw IOException("HTTP ${resp.code} for playlist") }
        val raw = resp.body!!.byteStream().buffered()
        raw.mark(2); val b1 = raw.read(); val b2 = raw.read(); raw.reset()
        return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(raw).buffered() else raw
    }

    /** Cached channels if present (instant), otherwise download + parse. */
    suspend fun channels(p: Playlist, refresh: Boolean = false): List<Channel> = withContext(Dispatchers.IO) {
        if (!refresh) channelCache[p.id]?.let { return@withContext it }
        val cached = cacheFile(p)
        if (!refresh && cached.exists()) runCatching { HarborJson.decodeFromString(chSer, cached.readText()) }.getOrNull()?.let {
            channelCache[p.id] = it; return@withContext it
        }
        val (list, headerEpg) = open(p.url, p.userAgent, p.username, p.password).use { parseM3u(it.bufferedReader().readText(), p) }
        if (list.isEmpty()) throw IOException("No channels found in playlist")
        cached.writeText(HarborJson.encodeToString(chSer, list))
        channelCache[p.id] = list
        if (p.epgUrl.isBlank() && headerEpg != null) add(p.copy(epgUrl = headerEpg))
        list
    }

    fun channelsCached(playlistId: String) = channelCache[playlistId]

    /** When the channel list was last fetched from the source (null = never). */
    fun lastRefreshed(p: Playlist): Long? = cacheFile(p).takeIf { it.exists() }?.lastModified()

    /** Public IPTV lists (e.g. GitHub-hosted) rotate stream URLs often – treat a cache older than this as stale. */
    fun isStale(p: Playlist, maxAgeMs: Long = 6 * 3_600_000L): Boolean = lastRefreshed(p)?.let { System.currentTimeMillis() - it > maxAgeMs } ?: true

    /**
     * Finds [old] in a freshly downloaded list. Channel ids include the list position, which shifts when the
     * source is edited, so match on guide id, then name + group, then name.
     */
    fun relocate(old: Channel, fresh: List<Channel>): Channel? =
        old.tvgId?.let { id -> fresh.firstOrNull { it.tvgId == id && it.name.equals(old.name, true) } ?: fresh.firstOrNull { it.tvgId == id } }
            ?: fresh.firstOrNull { it.name.equals(old.name, true) && it.group == old.group }
            ?: fresh.firstOrNull { it.name.equals(old.name, true) }

    fun parseM3u(text: String, p: Playlist): Pair<List<Channel>, String?> = M3uParser.parse(text, p)

    // ---------------- XMLTV guide ----------------

    /** Streams the XMLTV file keeping only programmes from 2 h ago to +18 h for channels in the playlist. */
    suspend fun loadEpg(p: Playlist, force: Boolean = false) = withContext(Dispatchers.IO) {
        if (p.epgUrl.isBlank() || (!force && p.id in epgLoaded)) return@withContext
        val wanted = channels(p).mapNotNull { it.tvgId }.toHashSet()
        val from = System.currentTimeMillis() - 2 * 3_600_000; val to = System.currentTimeMillis() + 18 * 3_600_000
        runCatching {
            open(p.epgUrl, p.userAgent, p.username, p.password).use { input ->
                val xp = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(input, null) }
                val tmp = HashMap<String, MutableList<Program>>()
                var ch: String? = null; var start = 0L; var stop = 0L; var title: String? = null; var desc: String? = null
                var ev = xp.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG) when (xp.name) {
                        "programme" -> {
                            ch = xp.getAttributeValue(null, "channel")?.lowercase()
                            start = XmltvTime.parse(xp.getAttributeValue(null, "start")); stop = XmltvTime.parse(xp.getAttributeValue(null, "stop"))
                            title = null; desc = null
                        }
                        "title" -> if (ch != null) title = xp.nextText()
                        "desc" -> if (ch != null) desc = xp.nextText()
                    } else if (ev == XmlPullParser.END_TAG && xp.name == "programme") {
                        val c = ch
                        if (c != null && c in wanted && stop > from && start < to && title != null) tmp.getOrPut(c) { mutableListOf() } += Program(start, stop, title!!, desc)
                        ch = null
                    }
                    ev = xp.next()
                }
                tmp.forEach { (k, v) -> epg[k] = v.sortedBy { it.start } }
            }
            epgLoaded += p.id
        }
    }


    // ---------------- channel health ----------------

    private val alive = ConcurrentHashMap<String, Boolean>()
    val checked: MutableStateFlow<Int> = MutableStateFlow(0)
    fun isOffline(ch: Channel) = alive[ch.id] == false

    /** Probe streams (8 at a time, 6 s timeout) so dead channels can be hidden. */
    suspend fun checkChannels(list: List<Channel>) = withContext(Dispatchers.IO) {
        val probe = http.newBuilder().callTimeout(6, java.util.concurrent.TimeUnit.SECONDS).followRedirects(true).build()
        val sem = kotlinx.coroutines.sync.Semaphore(8)
        kotlinx.coroutines.coroutineScope {
            list.filter { !alive.containsKey(it.id) }.forEach { ch ->
                launch {
                    sem.acquire()
                    try {
                        val req = Request.Builder().url(ch.url).header("Range", "bytes=0-1023")
                            .apply { ch.userAgent?.let { header("User-Agent", it) }; ch.referrer?.let { header("Referer", it) } }.build()
                        alive[ch.id] = runCatching { probe.newCall(req).execute().use { it.isSuccessful || it.code == 206 } }.getOrDefault(false)
                        checked.value = checked.value + 1
                    } finally { sem.release() }
                }
            }
        }
    }

    fun nowNext(ch: Channel): NowNext {
        val list = ch.tvgId?.let { epg[it] } ?: return NowNext(null, null)
        val now = System.currentTimeMillis()
        val i = list.indexOfFirst { now in it.start until it.stop }
        return if (i >= 0) NowNext(list[i], list.getOrNull(i + 1)) else NowNext(null, list.firstOrNull { it.start > now })
    }
}
