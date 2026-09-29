package com.sridhar.harbor.data.jellyfin

import android.os.Build
import com.sridhar.harbor.data.HarborJson
import com.sridhar.harbor.data.ServerConfig
import com.sridhar.harbor.data.SettingsStore
import com.sridhar.harbor.data.normalizeUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.URLEncoder
import java.util.UUID

class JellyfinRepository(private val settings: SettingsStore, private val baseHttp: OkHttpClient) {

    private var cachedKey: String? = null
    private var cachedApi: JellyfinApi? = null

    fun authHeader(cfg: ServerConfig): String = buildString {
        append("MediaBrowser Client=\"JellyVerse\", Device=\"")
        append(Build.MODEL.replace("\"", ""))
        append("\", DeviceId=\"").append(cfg.deviceId)
        append("\", Version=\"").append(com.sridhar.harbor.BuildConfig.VERSION_NAME).append('"')
        if (cfg.jellyfinToken.isNotBlank()) append(", Token=\"").append(cfg.jellyfinToken).append('"')
    }

    private fun build(url: String, cfg: ServerConfig): JellyfinApi = retrofit(url, cfg).create(JellyfinApi::class.java)

    private fun retrofit(url: String, cfg: ServerConfig): Retrofit {
        val client = baseHttp.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Authorization", authHeader(cfg)).build())
            }.build()
        return Retrofit.Builder()
            .baseUrl("$url/")
            .client(client)
            .addConverterFactory(HarborJson.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private var cachedAdmin: Pair<String, com.sridhar.harbor.data.jellyfin.admin.JellyfinAdminApi>? = null

    /** Admin-only endpoints (users, sessions, tasks, …) sharing this client's auth and connection pool. */
    suspend fun adminApi(): com.sridhar.harbor.data.jellyfin.admin.JellyfinAdminApi {
        val cfg = settings.current()
        val key = "${cfg.jellyfinUrl}|${cfg.jellyfinToken}|${cfg.deviceId}"
        return cachedAdmin?.takeIf { it.first == key }?.second
            ?: retrofit(cfg.jellyfinUrl, cfg).create(com.sridhar.harbor.data.jellyfin.admin.JellyfinAdminApi::class.java).also { cachedAdmin = key to it }
    }

    private suspend fun api(): Pair<JellyfinApi, ServerConfig> {
        val cfg = settings.current()
        val key = "${cfg.jellyfinUrl}|${cfg.jellyfinToken}|${cfg.deviceId}"
        val api = cachedApi?.takeIf { cachedKey == key } ?: build(cfg.jellyfinUrl, cfg).also {
            cachedApi = it; cachedKey = key
        }
        return api to cfg
    }

    suspend fun login(url: String, user: String, pass: String): AuthResult {
        val cfg = settings.current().copy(jellyfinUrl = url.normalizeUrl(), jellyfinToken = "")
        val result = build(cfg.jellyfinUrl, cfg).authenticate(AuthRequest(user, pass))
        settings.update {
            it.copy(
                jellyfinUrl = cfg.jellyfinUrl, jellyfinUser = user,
                jellyfinToken = result.accessToken, jellyfinUserId = result.user.id,
                deviceId = cfg.deviceId,
            )
        }
        return result
    }

    // ---------------- Quick Connect ----------------

    private suspend fun anonApi(url: String) = settings.current().copy(jellyfinUrl = url.normalizeUrl(), jellyfinToken = "").let { build(it.jellyfinUrl, it) }

    /** Throws when the server can't be reached – so "unreachable" isn't mistaken for "disabled". */
    suspend fun quickConnectEnabled(url: String): Boolean = anonApi(url).quickConnectEnabled()
    suspend fun quickConnectStart(url: String) = anonApi(url).quickConnectInitiate()
    suspend fun quickConnectPoll(url: String, secret: String) = anonApi(url).quickConnectState(secret)

    /** Exchange an approved Quick Connect secret for a token and save the session. */
    suspend fun quickConnectFinish(url: String, secret: String): AuthResult {
        val base = url.normalizeUrl()
        val cfg = settings.current()
        val result = anonApi(base).authenticateWithQuickConnect(QuickConnectAuth(secret))
        settings.update {
            it.copy(jellyfinUrl = base, jellyfinUser = result.user.name, jellyfinToken = result.accessToken,
                jellyfinUserId = result.user.id, deviceId = cfg.deviceId)
        }
        return result
    }

    /** Approve a code shown on another device (TV) with this signed-in account. */
    suspend fun quickConnectAuthorize(code: String) {
        val (a, c) = api()
        val r = a.quickConnectAuthorize(code.filter(Char::isDigit), c.jellyfinUserId)
        if (!r.isSuccessful) throw java.io.IOException(if (r.code() == 404) "Code not found or expired" else "Couldn't authorize (HTTP ${r.code()})")
    }

    suspend fun me(): JfUser { val (a, c) = api(); return a.user(c.jellyfinUserId) }

    private var adminCache: Pair<String, Boolean>? = null

    /** Whether the signed-in account is a server administrator (cached per token). */
    suspend fun isAdmin(): Boolean {
        val token = settings.current().jellyfinToken
        if (token.isBlank()) return false
        adminCache?.takeIf { it.first == token }?.let { return it.second }
        return runCatching { me().policy?.isAdministrator == true }.getOrElse { return false }.also { adminCache = token to it }
    }
    suspend fun views(): List<BaseItem> { val (a, c) = api(); return a.views(c.jellyfinUserId).items }
    suspend fun resume(): List<BaseItem> { val (a, c) = api(); return a.resume(c.jellyfinUserId).items }
    suspend fun nextUp(): List<BaseItem> { val (a, c) = api(); return a.nextUp(c.jellyfinUserId).items }
    suspend fun nextUpFor(seriesId: String): BaseItem? {
        val (a, c) = api(); return a.nextUp(c.jellyfinUserId, 1, seriesId).items.firstOrNull()
    }
    suspend fun latest(parentId: String): List<BaseItem> { val (a, c) = api(); return a.latest(c.jellyfinUserId, parentId) }
    suspend fun item(id: String): BaseItem { val (a, c) = api(); return a.item(id, c.jellyfinUserId) }
    suspend fun similar(id: String): List<BaseItem> { val (a, c) = api(); return a.similar(id, c.jellyfinUserId).items }
    suspend fun seasons(seriesId: String): List<BaseItem> { val (a, c) = api(); return a.seasons(seriesId, c.jellyfinUserId).items }
    suspend fun episodes(seriesId: String, seasonId: String?): List<BaseItem> {
        val (a, c) = api(); return a.episodes(seriesId, c.jellyfinUserId, seasonId).items
    }

    private val collageCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    /** Up to four poster URLs from inside a collection (cached for the session). */
    suspend fun collagePosters(collectionId: String): List<String> = collageCache[collectionId] ?: run {
        val cfg = settings.current()
        val kids = runCatching { library(collectionId, "Movie,Series", "PremiereDate,SortName", "Ascending", 0, 12).items }.getOrDefault(emptyList())
        kids.filter { it.imageTags["Primary"] != null }.take(4).map { posterUrl(cfg, it, 300) }.also { collageCache[collectionId] = it }
    }

    /**
     * Something the player can actually stream. Collections, series, seasons and folders have no file of their own
     * (the server rejects them with an HTTP error), so resolve them to the right movie / episode inside.
     */
    suspend fun playable(item: BaseItem): BaseItem? {
        if (!item.isFolderish) return item
        return when (item.type) {
            "Series" -> nextUpFor(item.id) ?: episodes(item.id, null).firstOrNull { it.userData?.played != true } ?: episodes(item.id, null).firstOrNull()
            "Season" -> episodes(item.seriesId ?: return null, item.id).let { eps -> eps.firstOrNull { it.userData?.played != true } ?: eps.firstOrNull() }
            else -> library(item.id, "Movie,Episode,Video", "PremiereDate,ProductionYear,SortName", "Ascending", 0, 200).items
                .let { kids -> kids.firstOrNull { it.userData?.played != true } ?: kids.firstOrNull() }
        }
    }

    suspend fun library(
        parentId: String?, types: String?, sortBy: String, sortOrder: String, start: Int, limit: Int,
        filters: String? = null, recursive: Boolean = true, genres: String? = null, years: String? = null,
    ): ItemsResult {
        val (a, c) = api()
        return a.items(c.jellyfinUserId, parentId, types, recursive, sortBy, sortOrder, start, limit, filters = filters, genres = genres, years = years)
    }

    /** Fetches specific items (one page of a client-filtered list) and keeps the requested order. */
    suspend fun itemsByIds(ids: List<String>): List<BaseItem> {
        if (ids.isEmpty()) return emptyList()
        val (a, c) = api()
        val got = a.items(c.jellyfinUserId, recursive = true, limit = ids.size, ids = ids.joinToString(",")).items.associateBy { it.id }
        return ids.mapNotNull { got[it] }
    }

    /** Best matches first: exact file-hash matches, then most downloaded. */
    suspend fun searchSubtitles(itemId: String, language: String): List<RemoteSubtitle> =
        api().first.searchSubtitles(itemId, language).sortedWith(compareByDescending<RemoteSubtitle> { it.hashMatch == true }.thenByDescending { it.downloads ?: 0 })

    suspend fun downloadSubtitle(itemId: String, subtitleId: String) {
        val r = api().first.downloadSubtitle(itemId, subtitleId)
        r.body()?.close(); r.errorBody()?.close()
        if (!r.isSuccessful) throw java.io.IOException("Subtitle download failed (HTTP ${r.code()})")
    }

    private val indexCache = HashMap<String, Pair<Long, LibraryIndex>>()

    /** Language index for a library (cached 30 min): one request for titles, one for episodes when it holds series. */
    suspend fun libraryIndex(parentId: String, types: String): LibraryIndex {
        indexCache[parentId]?.takeIf { System.currentTimeMillis() - it.first < 1_800_000 }?.let { return it.second }
        val (a, c) = api()
        val titles = a.indexItems(c.jellyfinUserId, parentId, types).items
        val episodes = if (titles.any { it.type == "Series" }) a.indexItems(c.jellyfinUserId, parentId, "Episode", fields = "MediaStreams").items else emptyList()
        return LibraryIndex.build(titles, episodes).also { indexCache[parentId] = System.currentTimeMillis() to it }
    }

    suspend fun search(term: String): List<BaseItem> {
        val (a, c) = api()
        return a.items(c.jellyfinUserId, types = "Movie,Series,Episode", search = term, limit = 40, sortBy = "SortName").items
    }

    suspend fun randomUnwatched(genre: String?): List<BaseItem> {
        val (a, c) = api()
        return a.items(c.jellyfinUserId, types = "Movie,Series", sortBy = "Random", limit = 12, filters = "IsUnplayed",
            genres = genre?.replaceFirstChar { it.uppercase() }).items
    }

    /** H.264/AAC HLS that Chromecast's default receiver can play. */
    suspend fun castUrl(itemId: String): String {
        val cfg = settings.current()
        val it = item(itemId)
        return hlsUrl(cfg, itemId, it.mediaSources.firstOrNull()?.id ?: itemId, newPlaySessionId(), 10_000_000, null, null)
    }

    private var titleCache: Pair<Long, List<BaseItem>>? = null

    /** Search that survives typos/transliterations ("aaranmanai" → "Aranmanai"); every hit is scored, never trusted blindly. */
    suspend fun fuzzyFind(query: String, types: Set<String> = setOf("Movie", "Series", "Episode"), minScore: Float = 0.6f): List<BaseItem> {
        val q = TitleMatcher.normalizeQuery(query)
        if (q.isBlank()) return emptyList()
        val direct = runCatching { search(query) }.getOrDefault(emptyList()).filter { it.type in types }
        val all = titleCache?.takeIf { System.currentTimeMillis() - it.first < 600_000 }?.second ?: run {
            val (a, c) = api()
            a.items(c.jellyfinUserId, types = "Movie,Series", limit = 5000, sortBy = "SortName").items.also { titleCache = System.currentTimeMillis() to it }
        }
        return (direct + all).distinctBy { it.id }
            .map { it to TitleMatcher.score(q, TitleMatcher.normalize(it.seriesName ?: it.name)) }
            .filter { it.second >= minScore }
            .sortedByDescending { it.second }.map { it.first }.take(8)
    }

    suspend fun filtered(term: String?, genres: String?, years: String?, types: String = "Movie,Series"): List<BaseItem> {
        val (a, c) = api()
        return a.items(c.jellyfinUserId, types = types, search = term?.ifBlank { null }, genres = genres?.ifBlank { null }, years = years?.ifBlank { null },
            limit = 40, sortBy = "CommunityRating,SortName", sortOrder = "Descending").items
    }

    /** Generic browse row for TV/phone shelves. */
    suspend fun browse(types: String, sortBy: String, sortOrder: String = "Descending", filters: String? = null, genres: String? = null, limit: Int = 20): List<BaseItem> {
        val (a, c) = api()
        return a.items(c.jellyfinUserId, types = types, sortBy = sortBy, sortOrder = sortOrder, filters = filters, genres = genres, limit = limit).items
    }

    /** Every Movie / Series with its rating – used by parental control (18+ menu, adult series for episodes). */
    suspend fun ratedTitles(): List<BaseItem> {
        val (a, c) = api()
        return a.items(c.jellyfinUserId, types = "Movie,Series", sortBy = "SortName", sortOrder = "Ascending", limit = 10_000).items
    }

    suspend fun heroItems(): List<BaseItem> {
        val (a, c) = api()
        return a.items(
            c.jellyfinUserId, types = "Movie,Series", sortBy = "Random", limit = 8,
            filters = "IsUnplayed",
        ).items.filter { it.backdropTags.isNotEmpty() }
    }

    /** Home "Top 10": the best-rated films and shows in the library. */
    suspend fun top10(): List<BaseItem> {
        val (a, c) = api()
        return a.items(c.jellyfinUserId, types = "Movie,Series", sortBy = "CommunityRating,SortName", sortOrder = "Descending", limit = 14)
            .items.filter { (it.communityRating ?: 0f).toFloat() > 0f }
    }

    /** Home marquee: a random, unwatched mix to discover. */
    suspend fun discoverPicks(): List<BaseItem> {
        val (a, c) = api()
        return a.items(c.jellyfinUserId, types = "Movie,Series", sortBy = "Random", limit = 24, filters = "IsUnplayed").items
    }

    /** Every film and show with the metadata and watch history the recommender needs (one request). */
    suspend fun recoCorpus(): List<BaseItem> {
        val (a, c) = api()
        return a.items(c.jellyfinUserId, types = "Movie,Series", limit = 6000, sortBy = "SortName",
            fields = "Genres,Tags,Studios,People,Overview,OfficialRating,PremiereDate", imageTypeLimit = 1).items
    }

    suspend fun libraryItems(): List<LibraryItem> { val (a, c) = api(); return a.libraryItems(c.jellyfinUserId).items }

    /** Parsed results plus the raw JSON needed for Apply. */
    suspend fun remoteSearch(itemId: String, kind: String, name: String, year: Int?): List<Pair<RemoteSearchResult, kotlinx.serialization.json.JsonObject>> =
        api().first.remoteSearch(kind, RemoteSearchQuery(RemoteSearchInfo(name, year), itemId)).map {
            HarborJson.decodeFromJsonElement(RemoteSearchResult.serializer(), it) to it
        }

    suspend fun applyMatch(itemId: String, raw: kotlinx.serialization.json.JsonObject) {
        val r = api().first.applySearchResult(itemId, raw)
        if (!r.isSuccessful) throw java.io.IOException("Jellyfin refused the match (HTTP ${r.code()})")
    }

    suspend fun refreshItem(itemId: String) { api().first.refreshItem(itemId) }
    suspend fun refreshLibrary() { api().first.refreshLibrary() }
    suspend fun virtualFolders() = api().first.virtualFolders()

    suspend fun segments(id: String): List<MediaSegment> =
        runCatching { api().first.segments(id).items }.getOrDefault(emptyList())

    suspend fun setPlayed(id: String, played: Boolean) {
        val (a, c) = api(); if (played) a.markPlayed(id, c.jellyfinUserId) else a.markUnplayed(id, c.jellyfinUserId)
    }

    /** Drops a title from Continue watching by clearing its saved position (watched state is untouched). */
    suspend fun removeFromResume(id: String) {
        val (a, c) = api()
        val r = a.updateUserData(id, c.jellyfinUserId, kotlinx.serialization.json.buildJsonObject { put("PlaybackPositionTicks", kotlinx.serialization.json.JsonPrimitive(0)) })
        if (!r.isSuccessful) throw retrofit2.HttpException(r)
    }

    suspend fun setFavorite(id: String, fav: Boolean) {
        val (a, c) = api(); if (fav) a.favorite(id, c.jellyfinUserId) else a.unfavorite(id, c.jellyfinUserId)
    }

    suspend fun reportStart(r: PlaybackReport) = runCatching { api().first.playing(r) }
    suspend fun reportProgress(r: PlaybackReport) = runCatching { api().first.progress(r) }
    suspend fun reportStop(r: PlaybackReport) = runCatching { api().first.stopped(r) }

    // ---- URL builders (sync; need a snapshot of config) ----

    fun imageUrl(cfg: ServerConfig, itemId: String, type: String = "Primary", maxWidth: Int = 480, tag: String? = null): String =
        "${cfg.jellyfinUrl}/Items/$itemId/Images/$type?maxWidth=$maxWidth&quality=90" + (tag?.let { "&tag=$it" } ?: "")

    fun posterUrl(cfg: ServerConfig, item: BaseItem, maxWidth: Int = 400): String = when {
        // Collections usually have no artwork of their own – NetImage draws a collage of the movies inside.
        item.type == "BoxSet" && item.imageTags["Primary"] == null -> "$COLLAGE${item.id}"
        // For episodes, prefer the series poster over the episode still
        item.type == "Episode" && item.seriesId != null && item.seriesPrimaryImageTag != null -> imageUrl(cfg, item.seriesId, "Primary", maxWidth, item.seriesPrimaryImageTag)
        item.imageTags["Primary"] != null -> imageUrl(cfg, item.id, "Primary", maxWidth, item.imageTags["Primary"])
        item.seriesId != null && item.seriesPrimaryImageTag != null -> imageUrl(cfg, item.seriesId, "Primary", maxWidth, item.seriesPrimaryImageTag)
        else -> imageUrl(cfg, item.id, "Primary", maxWidth)
    }

    fun backdropUrl(cfg: ServerConfig, item: BaseItem, maxWidth: Int = 1280): String = when {
        item.backdropTags.isNotEmpty() -> imageUrl(cfg, item.id, "Backdrop", maxWidth, item.backdropTags.first())
        item.parentBackdropItemId != null && item.parentBackdropTags.isNotEmpty() -> imageUrl(cfg, item.parentBackdropItemId, "Backdrop", maxWidth, item.parentBackdropTags.first())
        else -> imageUrl(cfg, item.id, "Primary", maxWidth)
    }

    /** Landscape art for rows like Continue Watching: episode still, else backdrop. */
    fun thumbUrl(cfg: ServerConfig, item: BaseItem, maxWidth: Int = 640): String = when {
        item.type == "Episode" && item.imageTags["Primary"] != null -> imageUrl(cfg, item.id, "Primary", maxWidth, item.imageTags["Primary"])
        item.imageTags["Thumb"] != null -> imageUrl(cfg, item.id, "Thumb", maxWidth, item.imageTags["Thumb"])
        else -> backdropUrl(cfg, item, maxWidth)
    }

    fun logoUrl(cfg: ServerConfig, item: BaseItem): String? {
        val tag = item.imageTags["Logo"] ?: return null
        return imageUrl(cfg, item.id, "Logo", 600, tag)
    }

    fun personUrl(cfg: ServerConfig, p: Person): String? =
        p.primaryImageTag?.let { imageUrl(cfg, p.id, "Primary", 200, it) }

    fun directStreamUrl(cfg: ServerConfig, itemId: String, mediaSourceId: String): String =
        "${cfg.jellyfinUrl}/Videos/$itemId/stream?static=true&mediaSourceId=$mediaSourceId" +
            "&deviceId=${cfg.deviceId}&ApiKey=${cfg.jellyfinToken}"

    /** Server-side transcode to H.264/AAC HLS; used for quality caps or when direct play fails. */
    fun hlsUrl(
        cfg: ServerConfig, itemId: String, mediaSourceId: String, playSessionId: String,
        maxBitrate: Int, audioIndex: Int?, subtitleIndex: Int?, reencode: Boolean = false,
    ): String = buildString {
        append("${cfg.jellyfinUrl}/Videos/$itemId/master.m3u8?")
        append("mediaSourceId=$mediaSourceId&playSessionId=$playSessionId&deviceId=${cfg.deviceId}")
        append("&ApiKey=${cfg.jellyfinToken}&videoCodec=h264&audioCodec=aac&maxAudioChannels=2")
        append("&videoBitrate=$maxBitrate&audioBitrate=192000&segmentContainer=mp4&transcodingMaxAudioChannels=2")
        audioIndex?.let { append("&audioStreamIndex=$it") }
        subtitleIndex?.let { append("&subtitleStreamIndex=$it&subtitleMethod=Encode") }
        // Last-resort recovery: a real re-encode gives a clean H.264 stream even when the source bitstream
        // trips a device's hardware decoder (stream copy would pass the same bits through).
        if (reencode) append("&allowVideoStreamCopy=false&allowAudioStreamCopy=false")
    }

    fun trickplayTileUrl(cfg: ServerConfig, itemId: String, mediaSourceId: String, width: Int, tile: Int): String =
        "${cfg.jellyfinUrl}/Videos/$itemId/Trickplay/$width/$tile.jpg?mediaSourceId=$mediaSourceId&ApiKey=${cfg.jellyfinToken}"

    fun chapterImageUrl(cfg: ServerConfig, itemId: String, index: Int, tag: String?): String =
        "${cfg.jellyfinUrl}/Items/$itemId/Images/Chapter/$index?maxWidth=360" + (tag?.let { "&tag=$it" } ?: "")

    fun subtitleUrl(cfg: ServerConfig, itemId: String, mediaSourceId: String, index: Int): String =
        "${cfg.jellyfinUrl}/Videos/$itemId/$mediaSourceId/Subtitles/$index/0/Stream.vtt?ApiKey=${cfg.jellyfinToken}"

    fun downloadUrl(cfg: ServerConfig, itemId: String): String =
        "${cfg.jellyfinUrl}/Items/$itemId/Download?ApiKey=${URLEncoder.encode(cfg.jellyfinToken, "UTF-8")}"

    fun newPlaySessionId(): String = UUID.randomUUID().toString().replace("-", "")
}

/** Pseudo-URL NetImage understands: "collage:<collectionId>". */
const val COLLAGE = "collage:"
