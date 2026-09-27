package com.sridhar.harbor.data.qbit

import com.sridhar.harbor.data.HarborJson
import com.sridhar.harbor.data.SettingsStore
import com.sridhar.harbor.data.normalizeUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class QbitException(message: String) : IOException(message)

/**
 * qBittorrent WebUI API v2. Authentication is a SID cookie; on a 403 we log in again once and retry.
 * Handles the 5.x rename of pause/resume → stop/start transparently.
 */
class QbitRepository(private val settings: SettingsStore, baseHttp: OkHttpClient) {

    private val cookies = mutableMapOf<String, List<Cookie>>()
    private val loginLock = Mutex()
    private var legacyPauseApi: Boolean? = null

    private val http = baseHttp.newBuilder().cookieJar(object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(this@QbitRepository.cookies) { this@QbitRepository.cookies[url.host] = cookies }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            synchronized(this@QbitRepository.cookies) { this@QbitRepository.cookies[url.host].orEmpty() }
    }).build()

    private suspend fun base() = settings.current().qbitUrl.normalizeUrl()

    suspend fun login(url: String = "", user: String? = null, pass: String? = null): String = withContext(Dispatchers.IO) {
        val cfg = settings.current()
        val base = url.ifBlank { cfg.qbitUrl }.normalizeUrl()
        val body = FormBody.Builder()
            .add("username", user ?: cfg.qbitUser)
            .add("password", pass ?: cfg.qbitPass)
            .build()
        val req = Request.Builder().url("$base/api/v2/auth/login").post(body)
            .header("Referer", base).header("Origin", base).build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 403) throw QbitException("IP banned by qBittorrent after too many failed logins")
            if (!resp.isSuccessful || text.contains("Fails", ignoreCase = true))
                throw QbitException("qBittorrent login failed – check username/password")
        }
        version(base)
    }

    private fun version(base: String): String {
        val req = Request.Builder().url("$base/api/v2/app/version").header("Referer", base).build()
        return http.newCall(req).execute().use { it.body?.string().orEmpty() }
    }

    private suspend fun call(path: String, form: Map<String, String>? = null, multipart: RequestBody? = null): String =
        withContext(Dispatchers.IO) {
            val base = base()
            if (base.isBlank()) throw QbitException("qBittorrent is not configured")
            fun build(): Request {
                val b = Request.Builder().url("$base/api/v2/$path").header("Referer", base)
                when {
                    multipart != null -> b.post(multipart)
                    form != null -> b.post(FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build())
                }
                return b.build()
            }
            var resp = http.newCall(build()).execute()
            if (resp.code == 403) {
                resp.close()
                loginLock.withLock { login() }
                resp = http.newCall(build()).execute()
            }
            resp.use {
                if (it.code == 404) throw NotFound()
                if (!it.isSuccessful) throw QbitException("qBittorrent: HTTP ${it.code} on $path")
                it.body?.string().orEmpty()
            }
        }

    private class NotFound : IOException("404")

    suspend fun torrents(filter: String = "all", sort: String = "added_on"): List<Torrent> =
        HarborJson.decodeFromString(ListSerializer(Torrent.serializer()),
            call("torrents/info?filter=$filter&sort=$sort&reverse=true"))

    suspend fun transfer(): TransferInfo = HarborJson.decodeFromString(TransferInfo.serializer(), call("transfer/info"))

    suspend fun altSpeedEnabled(): Boolean = call("transfer/speedLimitsMode").trim() == "1"
    suspend fun toggleAltSpeed() { call("transfer/toggleSpeedLimitsMode", form = emptyMap()) }

    suspend fun preferences(): QbitPrefs = HarborJson.decodeFromString(QbitPrefs.serializer(), call("app/preferences"))

    /** Limits are bytes/second; 0 = unlimited. */
    suspend fun setGlobalLimits(dl: Long, up: Long) {
        call("transfer/setDownloadLimit", mapOf("limit" to dl.toString()))
        call("transfer/setUploadLimit", mapOf("limit" to up.toString()))
    }

    /** Alternative ("turtle") limits are stored in preferences as KiB/s. */
    suspend fun setAltLimits(dlBytes: Long, upBytes: Long) {
        val json = buildJsonObject {
            put("alt_dl_limit", dlBytes / 1024)
            put("alt_up_limit", upBytes / 1024)
        }
        call("app/setPreferences", mapOf("json" to json.toString()))
    }

    suspend fun categories(): List<QbitCategory> = runCatching {
        HarborJson.decodeFromString(MapSerializer(String.serializer(), QbitCategory.serializer()), call("torrents/categories"))
            .map { (k, v) -> v.copy(name = k) }
    }.getOrDefault(emptyList())

    suspend fun files(hash: String): List<TorrentFile> =
        HarborJson.decodeFromString(ListSerializer(TorrentFile.serializer()), call("torrents/files?hash=$hash"))

    suspend fun setFilePriority(hash: String, indexes: List<Int>, priority: Int) {
        call("torrents/filePrio", mapOf("hash" to hash, "id" to indexes.joinToString("|"), "priority" to priority.toString()))
    }

    private fun joined(hashes: List<String>) = if (hashes.isEmpty()) "all" else hashes.joinToString("|")

    suspend fun pause(hashes: List<String>) = startStop(hashes, stop = true)
    suspend fun resume(hashes: List<String>) = startStop(hashes, stop = false)

    private suspend fun startStop(hashes: List<String>, stop: Boolean) {
        val form = mapOf("hashes" to joined(hashes))
        val modern = if (stop) "torrents/stop" else "torrents/start"
        val legacy = if (stop) "torrents/pause" else "torrents/resume"
        if (legacyPauseApi == true) { call(legacy, form); return }
        try {
            call(modern, form); legacyPauseApi = false
        } catch (e: NotFound) {
            legacyPauseApi = true; call(legacy, form)
        }
    }

    suspend fun delete(hashes: List<String>, deleteFiles: Boolean) {
        call("torrents/delete", mapOf("hashes" to joined(hashes), "deleteFiles" to deleteFiles.toString()))
    }

    suspend fun recheck(hashes: List<String>) { call("torrents/recheck", mapOf("hashes" to joined(hashes))) }
    suspend fun reannounce(hashes: List<String>) { call("torrents/reannounce", mapOf("hashes" to joined(hashes))) }
    suspend fun toggleSequential(hash: String) { call("torrents/toggleSequentialDownload", mapOf("hashes" to hash)) }
    suspend fun setForceStart(hash: String, value: Boolean) {
        call("torrents/setForceStart", mapOf("hashes" to hash, "value" to value.toString()))
    }
    suspend fun setCategory(hash: String, category: String) {
        call("torrents/setCategory", mapOf("hashes" to hash, "category" to category))
    }

    suspend fun setTorrentLimits(hash: String, dl: Long, up: Long) {
        call("torrents/setDownloadLimit", mapOf("hashes" to hash, "limit" to dl.toString()))
        call("torrents/setUploadLimit", mapOf("hashes" to hash, "limit" to up.toString()))
    }

    suspend fun add(req: AddTorrentRequest) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            if (req.urls.isNotBlank()) addFormDataPart("urls", req.urls.trim())
            if (req.fileBytes != null) addFormDataPart(
                "torrents", req.fileName ?: "file.torrent",
                req.fileBytes.toRequestBody("application/x-bittorrent".toMediaType()),
            )
            if (req.savePath.isNotBlank()) addFormDataPart("savepath", req.savePath)
            if (req.category.isNotBlank()) addFormDataPart("category", req.category)
            addFormDataPart("paused", req.startPaused.toString())   // qBit 4.x
            addFormDataPart("stopped", req.startPaused.toString())  // qBit 5.x
            addFormDataPart("sequentialDownload", req.sequential.toString())
            addFormDataPart("firstLastPiecePrio", req.firstLastPiece.toString())
        }.build()
        val result = call("torrents/add", multipart = body)
        if (result.contains("Fails", ignoreCase = true)) throw QbitException("qBittorrent rejected the torrent")
    }
}
