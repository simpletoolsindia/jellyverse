package com.sridhar.harbor.data.arr

import com.sridhar.harbor.data.HarborJson
import com.sridhar.harbor.data.SettingsStore
import com.sridhar.harbor.data.normalizeUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class ArrException(message: String) : IOException(message)

/** Sonarr v4 / Radarr v5+ share the v3 REST API; one client serves both. */
class ArrRepository(private val kind: ArrKind, private val settings: SettingsStore, baseHttp: OkHttpClient) {

    // Interactive release searches hit every indexer and can take a minute.
    private val http = baseHttp.newBuilder().readTimeout(120, TimeUnit.SECONDS).build()
    private val json = "application/json".toMediaType()

    private suspend fun creds(): Pair<String, String> {
        val c = settings.current()
        return when (kind) {
            ArrKind.Sonarr -> c.sonarrUrl to c.sonarrKey
            ArrKind.Radarr -> c.radarrUrl to c.radarrKey
        }
    }

    suspend fun configured() = creds().let { it.first.isNotBlank() && it.second.isNotBlank() }

    /**
     * Forms-login with username/password, then read the API key from /initialize.json
     * (served only to an authenticated session). Saves URL + key.
     */
    suspend fun loginForKey(url: String, user: String, pass: String): String = withContext(Dispatchers.IO) {
        val base = url.normalizeUrl()
        val jar = mutableListOf<Cookie>()
        val client = http.newBuilder().followRedirects(false).followSslRedirects(false).cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { jar.addAll(cookies) }
            override fun loadForRequest(url: HttpUrl) = jar.toList()
        }).build()
        val form = FormBody.Builder().add("username", user).add("password", pass).add("rememberMe", "on").build()
        client.newCall(Request.Builder().url("$base/login?returnUrl=%2F").post(form).build()).execute().use { r ->
            val loc = r.header("Location").orEmpty()
            if (r.code !in 300..399 || loc.contains("loginFailed")) throw ArrException("${kind.label} login failed – check username/password")
        }
        val key = client.newCall(Request.Builder().url("$base/initialize.json").build()).execute().use { r ->
            if (!r.isSuccessful) throw ArrException("${kind.label}: couldn't read API key (HTTP ${r.code})")
            HarborJson.decodeFromString(InitializeJson.serializer(), r.body!!.string()).apiKey
        }
        if (key.isBlank()) throw ArrException("${kind.label} returned no API key")
        save(base, key)
        status().version
    }

    suspend fun useApiKey(url: String, key: String): String {
        save(url.normalizeUrl(), key.trim())
        return status().version
    }

    private suspend fun save(base: String, key: String) = settings.update {
        when (kind) {
            ArrKind.Sonarr -> it.copy(sonarrUrl = base, sonarrKey = key)
            ArrKind.Radarr -> it.copy(radarrUrl = base, radarrKey = key)
        }
    }

    private suspend fun <T> call(path: String, ser: KSerializer<T>?, method: String = "GET", body: RequestBody? = null): T? =
        withContext(Dispatchers.IO) {
            val (base, key) = creds()
            if (base.isBlank()) throw ArrException("${kind.label} is not configured")
            val req = Request.Builder().url("$base/api/v3/$path").header("X-Api-Key", key).method(method, body).build()
            http.newCall(req).execute().use { r ->
                if (r.code == 401) throw ArrException("${kind.label} rejected the API key – reconnect in Settings")
                if (!r.isSuccessful) throw ArrException("${kind.label}: HTTP ${r.code} ${r.body?.string()?.take(160).orEmpty()}")
                val text = r.body?.string().orEmpty()
                if (ser == null || text.isBlank()) null else HarborJson.decodeFromString(ser, text)
            }
        }

    private suspend fun <T> get(path: String, ser: KSerializer<T>): T = call(path, ser)!!
    private suspend fun send(path: String, method: String, body: JsonObject? = null) {
        call<Unit>(path, null, method, (body?.toString() ?: "").toRequestBody(json).takeIf { body != null || method != "DELETE" })
    }

    suspend fun status() = get("system/status", SystemStatus.serializer())
    suspend fun disk() = get("diskspace", ListSerializer(DiskSpace.serializer()))
    suspend fun health() = get("health", ListSerializer(HealthItem.serializer()))
    suspend fun profiles() = get("qualityprofile", ListSerializer(QualityProfile.serializer()))

    suspend fun queue(): List<QueueItem> = get(
        "queue?pageSize=100&includeUnknownSeriesItems=true&includeUnknownMovieItems=true&includeSeries=true&includeEpisode=true&includeMovie=true",
        Paged.serializer(QueueItem.serializer()),
    ).records

    suspend fun removeFromQueue(id: Int, removeFromClient: Boolean, blocklist: Boolean) =
        send("queue/$id?removeFromClient=$removeFromClient&blocklist=$blocklist", "DELETE")

    // ---- Radarr ----
    suspend fun movies() = get("movie", ListSerializer(ArrMovie.serializer()))
    suspend fun movie(id: Int) = get("movie/$id", ArrMovie.serializer())
    suspend fun setMovieMonitored(id: Int, monitored: Boolean) = send("movie/editor", "PUT", buildJsonObject {
        putJsonArray("movieIds") { add(id) }; put("monitored", monitored)
    })
    suspend fun deleteMovie(id: Int, deleteFiles: Boolean) = send("movie/$id?deleteFiles=$deleteFiles&addImportExclusion=false", "DELETE")
    suspend fun movieReleases(id: Int) = get("release?movieId=$id", ListSerializer(ArrRelease.serializer()))

    // ---- Sonarr ----
    suspend fun series() = get("series", ListSerializer(ArrSeries.serializer()))
    suspend fun seriesById(id: Int) = get("series/$id", ArrSeries.serializer())
    suspend fun episodes(seriesId: Int) = get("episode?seriesId=$seriesId", ListSerializer(ArrEpisode.serializer()))
    suspend fun setSeriesMonitored(id: Int, monitored: Boolean) = send("series/editor", "PUT", buildJsonObject {
        putJsonArray("seriesIds") { add(id) }; put("monitored", monitored)
    })
    suspend fun setEpisodesMonitored(ids: List<Int>, monitored: Boolean) = send("episode/monitor", "PUT", buildJsonObject {
        putJsonArray("episodeIds") { ids.forEach { add(it) } }; put("monitored", monitored)
    })
    suspend fun deleteSeries(id: Int, deleteFiles: Boolean) = send("series/$id?deleteFiles=$deleteFiles", "DELETE")
    suspend fun episodeReleases(episodeId: Int) = get("release?episodeId=$episodeId", ListSerializer(ArrRelease.serializer()))
    suspend fun seasonReleases(seriesId: Int, season: Int) = get("release?seriesId=$seriesId&seasonNumber=$season", ListSerializer(ArrRelease.serializer()))

    // ---- Shared ----
    suspend fun grab(r: ArrRelease) = send("release", "POST", buildJsonObject { put("guid", r.guid); put("indexerId", r.indexerId) })

    suspend fun command(name: String, build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) =
        send("command", "POST", buildJsonObject { put("name", name); build() })

    suspend fun searchMovie(id: Int) = command("MoviesSearch") { putJsonArray("movieIds") { add(id) } }
    suspend fun searchSeries(id: Int) = command("SeriesSearch") { put("seriesId", id) }
    suspend fun searchSeason(id: Int, season: Int) = command("SeasonSearch") { put("seriesId", id); put("seasonNumber", season) }
    suspend fun searchEpisodes(ids: List<Int>) = command("EpisodeSearch") { putJsonArray("episodeIds") { ids.forEach { add(it) } } }
    suspend fun searchAllMissing() = command(if (kind == ArrKind.Radarr) "MissingMoviesSearch" else "MissingEpisodeSearch")
    suspend fun refreshDownloads() = command("RefreshMonitoredDownloads")

    /** Missing items, normalised. */
    suspend fun missing(): List<ArrEntry> = when (kind) {
        ArrKind.Radarr -> get("wanted/missing?pageSize=100&sortKey=movieMetadata.sortTitle&sortDirection=ascending&monitored=true",
            Paged.serializer(ArrMovie.serializer())).records.map { it.toEntry() }
        ArrKind.Sonarr -> get("wanted/missing?pageSize=100&includeSeries=true&sortKey=episodes.airDateUtc&sortDirection=descending&monitored=true",
            Paged.serializer(ArrEpisode.serializer())).records.map { it.toEntry() }
    }

    /** Next [days] of releases/airings, normalised. */
    suspend fun upcoming(days: Long = 30): List<ArrEntry> {
        val start = LocalDate.now().minusDays(1); val end = LocalDate.now().plusDays(days)
        return when (kind) {
            ArrKind.Radarr -> get("calendar?start=$start&end=$end&unmonitored=false", ListSerializer(ArrMovie.serializer())).map { m ->
                val next = listOfNotNull(m.inCinemas, m.digitalRelease, m.physicalRelease).filter { it.take(10) >= start.toString() }.minOrNull()
                m.toEntry().copy(date = next ?: m.inCinemas)
            }
            ArrKind.Sonarr -> get("calendar?start=$start&end=$end&includeSeries=true&unmonitored=false",
                ListSerializer(ArrEpisode.serializer())).map { it.toEntry() }
        }
    }

    private fun ArrMovie.toEntry() = ArrEntry(
        ArrKind.Radarr, "m$id", title, year.takeIf { it > 0 }?.toString(), images.poster(),
        inCinemas ?: digitalRelease, hasFile, movieId = id,
    )

    private fun ArrEpisode.toEntry() = ArrEntry(
        ArrKind.Sonarr, "e$id", series?.title ?: "Episode", listOfNotNull(code, title).joinToString(" · "),
        series?.images?.poster(), airDateUtc, hasFile, seriesId = seriesId, episodeId = id,
    )
}
