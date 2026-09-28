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
    private val _stations = MutableStateFlow(seeded(load()))
    val stations: StateFlow<List<RadioStation>> = _stations.asStateFlow()

    /** First run: preload popular Tamil FM stations (removable like any other). */
    private fun seeded(list: List<RadioStation>): List<RadioStation> {
        if (prefs.getBoolean("seeded_tamil_v1", false)) return list
        val merged = list + TAMIL_FM.filterNot { s -> list.any { it.url == s.url } }
        prefs.edit().putBoolean("seeded_tamil_v1", true).putString("stations", HarborJson.encodeToString(ser, merged)).apply()
        return merged
    }

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

    private suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        val lower = url.substringBefore('?').lowercase()
        if (!lower.endsWith(".pls") && !lower.endsWith(".m3u")) return@withContext url
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { r -> parsePlaylist(r.body?.string().orEmpty()) }
        }.getOrNull() ?: url
    }

    companion object {
        val TAMIL_FM = listOf(
        RadioStation(id = "tamil-0", name = "AIR Kodaikanal FM", url = "https://air.pc.cdn.bitgravity.com/air/live/pbaudio051/chunklist.m3u8"),
        RadioStation(id = "tamil-1", name = "AIR Madurai FM", url = "https://air.pc.cdn.bitgravity.com/air/live/pbaudio126/chunklist.m3u8"),
        RadioStation(id = "tamil-2", name = "AIR Puducherry FM", url = "https://air.pc.cdn.bitgravity.com/air/live/pbaudio098/chunklist.m3u8"),
        RadioStation(id = "tamil-3", name = "AIR Nagercoil FM", url = "https://air.pc.cdn.bitgravity.com/air/live/pbaudio129/chunklist.m3u8"),
        RadioStation(id = "tamil-4", name = "AIR Tirunelveli FM", url = "https://air.pc.cdn.bitgravity.com/air/live/pbaudio062/chunklist.m3u8"),
        RadioStation(id = "tamil-5", name = "AIR Tuticorin", url = "https://air.pc.cdn.bitgravity.com/air/live/pbaudio025/masterlist.m3u8"),
        RadioStation(id = "tamil-6", name = "Tamil Panpalai Gold", url = "https://tamilpanpalai.radioca.st/ind"),
        RadioStation(id = "tamil-7", name = "M S Viswanathan FM", url = "http://stream.zeno.fm/x7wc1xgllvsvv"),
        RadioStation(id = "tamil-8", name = "Harris Jayaraj FM", url = "http://stream.zeno.fm/ob6tjg8gulptv"),
        RadioStation(id = "tamil-9", name = "Sooriyan FM", url = "https://radio.lotustechnologieslk.net:8006/"),
        RadioStation(id = "tamil-10", name = "Shakthi FM", url = "https://mbc.thestreamtech.com:8086/stream"),
        RadioStation(id = "tamil-11", name = "Tube Tamil FM", url = "http://s2.voscast.com:12084/;stream1619441439791/1"),
        RadioStation(id = "tamil-12", name = "Boom Tamil", url = "https://streaming.boomtamil.com/Toronto"),
        RadioStation(id = "tamil-13", name = "Vasantham FM", url = "https://cp12.serverse.com/proxy/vasanthamfm?mp=/stream"),
        RadioStation(id = "tamil-14", name = "Jei FM Klang", url = "https://usa3.fastcast4u.com/proxy/jeifm?mp=/1"),
        RadioStation(id = "tamil-15", name = "Star FM Sri Lanka", url = "https://stream.starfm.lk:12025/stream"),
        RadioStation(id = "tamil-16", name = "Vanavil FM Maestro", url = "https://s7.yesstreaming.net:8092/stream"),
        RadioStation(id = "tamil-17", name = "Vanavil FM ARR", url = "https://s7.yesstreaming.net:8038/stream"),
        RadioStation(id = "tamil-18", name = "Mohan Radio", url = "https://psrlive4.listenon.in/mohan?station=mohanradio"),
        RadioStation(id = "tamil-19", name = "American Tamil Radio", url = "https://cp11.serverse.com/proxy/hgsmgluv?mp=/stream"),
        RadioStation(id = "tamil-20", name = "Tamil Murasam FM", url = "https://tamilmurasam.radioca.st/live"),
        RadioStation(id = "tamil-21", name = "Lankasri FM", url = "http://media2.lankasri.fm/;stream.mp3")
        )

        /** First stream URL in a PLS ("File1=http…") or M3U (plain lines) playlist. */
        fun parsePlaylist(text: String): String? = text.lineSequence().map { it.trim() }
            .map { if (it.startsWith("File", true) && '=' in it) it.substringAfter('=').trim() else it }
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }

        fun guessName(url: String): String = runCatching { java.net.URI(url).host.removePrefix("www.") }.getOrNull() ?: "Radio"

        /** Pulls the first http(s) link out of shared text ("Listen live: https://…"). */
        fun linkIn(text: String): String? = Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')', ']')
    }
}
