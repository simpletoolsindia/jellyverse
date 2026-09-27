package com.sridhar.harbor.data.seerr

import com.sridhar.harbor.data.HarborJson
import com.sridhar.harbor.data.ServerConfig
import com.sridhar.harbor.data.SettingsStore
import com.sridhar.harbor.data.normalizeUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException

class SeerrRepository(private val settings: SettingsStore, private val baseHttp: OkHttpClient) {

    private var cachedKey: String? = null
    private var cachedApi: SeerrApi? = null
    private val detailCache = mutableMapOf<String, SeerrDetails>()

    private fun build(cfg: ServerConfig): SeerrApi {
        val client = baseHttp.newBuilder().addInterceptor { chain ->
            val b = chain.request().newBuilder()
            if (cfg.seerrApiKey.isNotBlank()) b.header("X-Api-Key", cfg.seerrApiKey)
            else if (cfg.seerrCookie.isNotBlank()) b.header("Cookie", cfg.seerrCookie)
            chain.proceed(b.build())
        }.build()
        return Retrofit.Builder().baseUrl("${cfg.seerrUrl}/").client(client)
            .addConverterFactory(HarborJson.asConverterFactory("application/json".toMediaType()))
            .build().create(SeerrApi::class.java)
    }

    private suspend fun api(): SeerrApi {
        val cfg = settings.current()
        if (cfg.seerrUrl.isBlank()) throw IOException("Jellyseerr is not configured")
        val key = "${cfg.seerrUrl}|${cfg.seerrApiKey}|${cfg.seerrCookie}"
        return cachedApi?.takeIf { cachedKey == key } ?: build(cfg).also { cachedApi = it; cachedKey = key }
    }

    /** Sign in with Jellyfin credentials; Jellyseerr answers with a connect.sid session cookie. */
    suspend fun loginWithJellyfin(url: String, user: String, pass: String): SeerrUser = withContext(Dispatchers.IO) {
        val base = url.normalizeUrl()
        val body = HarborJson.encodeToString(JellyfinLoginBody.serializer(), JellyfinLoginBody(user, pass))
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$base/api/v1/auth/jellyfin").post(body).build()
        baseHttp.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Jellyseerr login failed (HTTP ${resp.code})")
            val cookie = resp.headers("Set-Cookie").firstOrNull { it.startsWith("connect.sid") }
                ?.substringBefore(';') ?: throw IOException("Jellyseerr returned no session cookie")
            settings.update { it.copy(seerrUrl = base, seerrCookie = cookie, seerrApiKey = "") }
            HarborJson.decodeFromString(SeerrUser.serializer(), text)
        }
    }

    suspend fun useApiKey(url: String, key: String): SeerrUser {
        settings.update { it.copy(seerrUrl = url.normalizeUrl(), seerrApiKey = key.trim(), seerrCookie = "") }
        return try { api().me() } catch (e: HttpException) {
            throw IOException("Jellyseerr rejected the API key (HTTP ${e.code()})")
        }
    }

    suspend fun me() = api().me()
    suspend fun trending(page: Int = 1) = api().trending(page)
    suspend fun popularMovies(page: Int = 1) = api().popularMovies(page)
    suspend fun popularTv(page: Int = 1) = api().popularTv(page)
    suspend fun upcomingMovies(page: Int = 1) = api().upcomingMovies(page)
    suspend fun upcomingTv(page: Int = 1) = api().upcomingTv(page)
    suspend fun search(q: String, page: Int = 1) = api().search(q, page)

    suspend fun details(type: String, id: Int, fresh: Boolean = false): SeerrDetails {
        val key = "$type:$id"
        if (!fresh) detailCache[key]?.let { return it }
        return (if (type == "tv") api().tv(id) else api().movie(id)).also { detailCache[key] = it }
    }

    suspend fun recommendations(type: String, id: Int) =
        runCatching { if (type == "tv") api().tvRecs(id) else api().movieRecs(id) }.getOrNull()?.results.orEmpty()

    suspend fun requests(filter: String, skip: Int, take: Int = 20, requestedBy: Int? = null) =
        api().requests(take, skip, filter, requestedBy = requestedBy)
    suspend fun requestCount() = api().requestCount()

    suspend fun request(type: String, tmdbId: Int, seasons: List<Int>?, is4k: Boolean = false) =
        api().request(NewRequest(type, tmdbId, seasons, is4k)).also { detailCache.remove("$type:$tmdbId") }

    suspend fun approve(id: Int) = api().setRequestStatus(id, "approve").check()
    suspend fun decline(id: Int) = api().setRequestStatus(id, "decline").check()
    suspend fun retry(id: Int) = api().retryRequest(id).check()
    suspend fun deleteRequest(id: Int) = api().deleteRequest(id).check()

    suspend fun users() = api().users().results
    suspend fun setPermissions(id: Int, perms: Long) = api().setPermissions(id, PermissionsBody(perms)).check()
    suspend fun deleteUser(id: Int) = api().deleteUser(id).check()
    suspend fun importableJellyfinUsers() = api().jellyfinUsers()
    suspend fun importJellyfinUsers(ids: List<String>) = api().importJellyfinUsers(ImportUsersBody(ids)).check()

    private fun retrofit2.Response<*>.check() {
        if (!isSuccessful) throw IOException("Jellyseerr: HTTP ${code()} ${errorBody()?.string()?.take(200).orEmpty()}")
    }

    companion object {
        fun tmdb(path: String?, size: String = "w500") = path?.let { "https://image.tmdb.org/t/p/$size$it" }
        fun avatar(cfg: ServerConfig, avatar: String?) = when {
            avatar.isNullOrBlank() -> null
            avatar.startsWith("http") -> avatar
            else -> cfg.seerrUrl + avatar
        }
    }
}
