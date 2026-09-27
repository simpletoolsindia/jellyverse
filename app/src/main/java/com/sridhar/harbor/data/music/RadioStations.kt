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
        /** First stream URL in a PLS ("File1=http…") or M3U (plain lines) playlist. */
        fun parsePlaylist(text: String): String? = text.lineSequence().map { it.trim() }
            .map { if (it.startsWith("File", true) && '=' in it) it.substringAfter('=').trim() else it }
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }

        fun guessName(url: String): String = runCatching { java.net.URI(url).host.removePrefix("www.") }.getOrNull() ?: "Radio"

        /** Pulls the first http(s) link out of shared text ("Listen live: https://…"). */
        fun linkIn(text: String): String? = Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')', ']')
    }
}
