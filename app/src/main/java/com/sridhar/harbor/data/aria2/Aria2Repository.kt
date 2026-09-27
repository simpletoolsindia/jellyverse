package com.sridhar.harbor.data.aria2

import android.util.Base64
import com.sridhar.harbor.data.HarborJson
import com.sridhar.harbor.data.SettingsStore
import com.sridhar.harbor.data.normalizeUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@Serializable data class Aria2Uri(val uri: String = "", val status: String = "")
@Serializable data class Aria2File(val index: String = "", val path: String = "", val length: String = "0", val completedLength: String = "0", val selected: String = "true", val uris: List<Aria2Uri> = emptyList())
@Serializable data class Aria2BtInfo(val name: String? = null)
@Serializable data class Aria2Bt(val info: Aria2BtInfo? = null)

@Serializable
data class Aria2Download(
    val gid: String,
    val status: String = "",
    val totalLength: String = "0",
    val completedLength: String = "0",
    val uploadLength: String = "0",
    val downloadSpeed: String = "0",
    val uploadSpeed: String = "0",
    val connections: String = "0",
    val numSeeders: String? = null,
    val dir: String? = null,
    val errorMessage: String? = null,
    val files: List<Aria2File> = emptyList(),
    val bittorrent: Aria2Bt? = null,
) {
    val total get() = totalLength.toLongOrNull() ?: 0
    val done get() = completedLength.toLongOrNull() ?: 0
    val progress get() = if (total > 0) done.toFloat() / total else 0f
    val dl get() = downloadSpeed.toLongOrNull() ?: 0
    val ul get() = uploadSpeed.toLongOrNull() ?: 0
    val eta get() = if (dl > 0) (total - done) / dl else -1
    val isTorrent get() = bittorrent != null
    val name: String
        get() = bittorrent?.info?.name
            ?: files.firstOrNull()?.path?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: files.firstOrNull()?.uris?.firstOrNull()?.uri?.substringAfterLast('/')?.substringBefore('?')
            ?: gid
}

@Serializable
data class Aria2Stat(
    val downloadSpeed: String = "0", val uploadSpeed: String = "0",
    val numActive: String = "0", val numWaiting: String = "0", val numStopped: String = "0",
)

/** aria2 JSON-RPC client (aria2-pro / AriaNg compatible). */
class Aria2Repository(private val settings: SettingsStore, private val http: OkHttpClient) {
    private val ids = AtomicInteger()
    private val keys = listOf("gid", "status", "totalLength", "completedLength", "uploadLength", "downloadSpeed", "uploadSpeed",
        "connections", "numSeeders", "dir", "errorMessage", "files", "bittorrent")

    private suspend fun rpc(method: String, vararg params: JsonElement, url: String? = null, secret: String? = null): JsonElement =
        withContext(Dispatchers.IO) {
            val cfg = settings.current()
            val base = (url ?: cfg.aria2Url).normalizeUrl()
            if (base.isBlank()) throw IOException("aria2 is not configured")
            val token = secret ?: cfg.aria2Secret
            val body = buildJsonObject {
                put("jsonrpc", "2.0"); put("id", "harbor-${ids.incrementAndGet()}"); put("method", method)
                put("params", buildJsonArray {
                    if (token.isNotBlank()) add(JsonPrimitive("token:$token"))
                    params.forEach { add(it) }
                })
            }
            val req = Request.Builder().url("$base/jsonrpc").post(body.toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                val obj = runCatching { HarborJson.parseToJsonElement(text).jsonObject }.getOrNull()
                    ?: throw IOException("aria2: HTTP ${r.code}")
                obj["error"]?.let { e -> throw IOException("aria2: " + (e.jsonObject["message"]?.toString()?.trim('"') ?: "error")) }
                obj["result"] ?: JsonPrimitive("OK")
            }
        }

    private fun keysJson() = JsonArray(keys.map { JsonPrimitive(it) })
    private fun list(e: JsonElement) = HarborJson.decodeFromJsonElement(ListSerializer(Aria2Download.serializer()), e)

    suspend fun test(url: String, secret: String): String {
        val v = rpc("aria2.getVersion", url = url, secret = secret)
        settings.update { it.copy(aria2Url = url.normalizeUrl(), aria2Secret = secret) }
        return v.jsonObject["version"]?.toString()?.trim('"') ?: "?"
    }

    suspend fun downloads(): List<Aria2Download> =
        list(rpc("aria2.tellActive", keysJson())) +
            list(rpc("aria2.tellWaiting", JsonPrimitive(0), JsonPrimitive(200), keysJson())) +
            list(rpc("aria2.tellStopped", JsonPrimitive(0), JsonPrimitive(200), keysJson()))

    suspend fun stat() = HarborJson.decodeFromJsonElement(Aria2Stat.serializer(), rpc("aria2.getGlobalStat"))

    suspend fun globalOptions(): JsonObject = rpc("aria2.getGlobalOption").jsonObject

    suspend fun addUri(uris: List<String>, dir: String? = null) {
        rpc("aria2.addUri", JsonArray(uris.map { JsonPrimitive(it) }), buildJsonObject { dir?.takeIf { it.isNotBlank() }?.let { put("dir", it) } })
    }

    suspend fun addTorrent(bytes: ByteArray, dir: String? = null) {
        rpc("aria2.addTorrent", JsonPrimitive(Base64.encodeToString(bytes, Base64.NO_WRAP)), JsonArray(emptyList()),
            buildJsonObject { dir?.takeIf { it.isNotBlank() }?.let { put("dir", it) } })
    }

    suspend fun pause(gid: String) { rpc("aria2.pause", JsonPrimitive(gid)) }
    suspend fun resume(gid: String) { rpc("aria2.unpause", JsonPrimitive(gid)) }
    suspend fun pauseAll() { rpc("aria2.pauseAll") }
    suspend fun resumeAll() { rpc("aria2.unpauseAll") }

    /** Active/waiting → forceRemove; finished/errored → drop the result entry. */
    suspend fun remove(d: Aria2Download) {
        if (d.status in setOf("active", "waiting", "paused")) rpc("aria2.forceRemove", JsonPrimitive(d.gid))
        else rpc("aria2.removeDownloadResult", JsonPrimitive(d.gid))
    }

    suspend fun purgeFinished() { rpc("aria2.purgeDownloadResult") }

    /** Bytes/s, 0 = unlimited. */
    suspend fun setLimits(dl: Long, ul: Long) {
        rpc("aria2.changeGlobalOption", buildJsonObject {
            put("max-overall-download-limit", dl.toString()); put("max-overall-upload-limit", ul.toString())
        })
    }
}
