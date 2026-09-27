package com.sridhar.harbor.data.music

import com.sridhar.harbor.data.HarborJson
import com.sridhar.harbor.data.ServerConfig
import com.sridhar.harbor.data.SettingsStore
import com.sridhar.harbor.data.normalizeUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class SubsonicException(val code: Int, message: String) : IOException(message)

/** Navidrome via the (Open)Subsonic REST API. */
class NavidromeRepository(private val settings: SettingsStore, private val http: OkHttpClient) {

    private fun base(cfg: ServerConfig, method: String): HttpUrl.Builder =
        "${cfg.navidromeUrl.normalizeUrl()}/rest/$method".toHttpUrl().newBuilder()
            .addQueryParameter("u", cfg.navidromeUser).addQueryParameter("t", cfg.navidromeToken)
            .addQueryParameter("s", cfg.navidromeSalt).addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "JellyVerse").addQueryParameter("f", "json")

    private suspend fun call(method: String, vararg params: Pair<String, Any?>, cfg: ServerConfig? = null): JsonObject = withContext(Dispatchers.IO) {
        val c = cfg ?: settings.current()
        if (c.navidromeUrl.isBlank()) throw SubsonicException(0, "Navidrome is not configured")
        val url = base(c, method).apply { params.forEach { (k, v) -> if (v != null) addQueryParameter(k, v.toString()) } }.build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val root = runCatching { HarborJson.parseToJsonElement(text).jsonObject["subsonic-response"]!!.jsonObject }
                .getOrElse { throw IOException("Navidrome: HTTP ${r.code}") }
            if (root["status"]?.jsonPrimitive?.content != "ok") {
                val e = root["error"]?.jsonObject
                throw SubsonicException(e?.get("code")?.jsonPrimitive?.int ?: -1, when (e?.get("code")?.jsonPrimitive?.int) {
                    40, 41 -> "Wrong Navidrome username or password"
                    70 -> "Navidrome library is empty – run a scan"
                    else -> e?.get("message")?.jsonPrimitive?.content ?: "Navidrome error"
                })
            }
            root
        }
    }

    private fun <T> JsonObject.field(name: String, ser: KSerializer<T>, empty: T): T =
        this[name]?.let { HarborJson.decodeFromJsonElement(ser, it) } ?: empty

    /** Verifies credentials and stores a salted token (never the password). */
    suspend fun signIn(url: String, user: String, password: String): String {
        val salt = SubsonicAuth.newSalt()
        val cfg = settings.current().copy(navidromeUrl = url.normalizeUrl(), navidromeUser = user.trim(), navidromeSalt = salt, navidromeToken = SubsonicAuth.token(password, salt))
        val root = call("ping", cfg = cfg)
        settings.update { it.copy(navidromeUrl = cfg.navidromeUrl, navidromeUser = cfg.navidromeUser, navidromeSalt = salt, navidromeToken = cfg.navidromeToken) }
        return root["serverVersion"]?.jsonPrimitive?.content ?: root["version"]?.jsonPrimitive?.content ?: "?"
    }

    suspend fun signOut() = settings.update { it.copy(navidromeUrl = "", navidromeUser = "", navidromeSalt = "", navidromeToken = "") }

    // ---------------- browse ----------------

    /** type: newest, recent, frequent, random, highest, starred, alphabeticalByName */
    suspend fun albums(type: String, size: Int = 20, offset: Int = 0, genre: String? = null): List<Album> =
        call("getAlbumList2", "type" to (if (genre != null) "byGenre" else type), "size" to size, "offset" to offset, "genre" to genre)
            .field("albumList2", AlbumList.serializer(), AlbumList()).album

    suspend fun album(id: String): Album = call("getAlbum", "id" to id).field("album", Album.serializer(), Album(id))

    suspend fun artists(): List<Artist> =
        call("getArtists").field("artists", ArtistsResult.serializer(), ArtistsResult()).index.flatMap { it.artist }

    suspend fun artist(id: String): Artist = call("getArtist", "id" to id).field("artist", Artist.serializer(), Artist(id))

    suspend fun artistInfo(id: String): ArtistInfo =
        runCatching { call("getArtistInfo2", "id" to id, "count" to 12).field("artistInfo2", ArtistInfo.serializer(), ArtistInfo()) }.getOrDefault(ArtistInfo())

    suspend fun topSongs(artistName: String, count: Int = 10): List<Song> =
        runCatching { call("getTopSongs", "artist" to artistName, "count" to count).field("topSongs", SongList.serializer(), SongList()).song }.getOrDefault(emptyList())

    suspend fun randomSongs(size: Int = 50, genre: String? = null): List<Song> =
        call("getRandomSongs", "size" to size, "genre" to genre).field("randomSongs", SongList.serializer(), SongList()).song

    suspend fun similarSongs(id: String, count: Int = 50): List<Song> =
        runCatching { call("getSimilarSongs2", "id" to id, "count" to count).field("similarSongs2", SongList.serializer(), SongList()).song }.getOrDefault(emptyList())

    suspend fun search(query: String): SearchResult =
        call("search3", "query" to query, "artistCount" to 8, "albumCount" to 12, "songCount" to 30).field("searchResult3", SearchResult.serializer(), SearchResult())

    suspend fun genres(): List<Genre> = MusicText.realGenres(call("getGenres").field("genres", GenreList.serializer(), GenreList()).genre)

    suspend fun starred(): Starred = call("getStarred2").field("starred2", Starred.serializer(), Starred())

    // ---------------- playlists ----------------

    suspend fun playlists(): List<Playlist> = call("getPlaylists").field("playlists", PlaylistList.serializer(), PlaylistList()).playlist
    suspend fun playlist(id: String): Playlist = call("getPlaylist", "id" to id).field("playlist", Playlist.serializer(), Playlist(id))
    suspend fun createPlaylist(name: String, songIds: List<String> = emptyList()) {
        val url = base(settings.current(), "createPlaylist").addQueryParameter("name", name).apply { songIds.forEach { addQueryParameter("songId", it) } }
        rawCall(url.build())
    }
    suspend fun addToPlaylist(playlistId: String, songIds: List<String>) {
        val url = base(settings.current(), "updatePlaylist").addQueryParameter("playlistId", playlistId).apply { songIds.forEach { addQueryParameter("songIdToAdd", it) } }
        rawCall(url.build())
    }

    private suspend fun rawCall(url: HttpUrl) = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use { if (!it.isSuccessful) throw IOException("Navidrome: HTTP ${it.code}") }
    }

    // ---------------- actions ----------------

    suspend fun setLiked(songId: String, liked: Boolean) { call(if (liked) "star" else "unstar", "id" to songId) }
    suspend fun setAlbumLiked(albumId: String, liked: Boolean) { call(if (liked) "star" else "unstar", "albumId" to albumId) }

    /** submission=false → "now playing"; true → counts a play (after ≥50 % or 4 min). */
    suspend fun scrobble(songId: String, submission: Boolean) { runCatching { call("scrobble", "id" to songId, "submission" to submission) } }

    suspend fun lyrics(songId: String): StructuredLyrics? = runCatching {
        call("getLyricsBySongId", "id" to songId).field("lyricsList", LyricsList.serializer(), LyricsList()).structuredLyrics
            .sortedByDescending { it.synced }.firstOrNull { it.line.isNotEmpty() }
    }.getOrNull()

    // ---------------- media URLs (token-authenticated, safe for ExoPlayer / Coil) ----------------

    fun coverUrl(cfg: ServerConfig, id: String?, size: Int = 500): String? =
        id?.takeIf { cfg.navidromeReady }?.let { base(cfg, "getCoverArt").addQueryParameter("id", it).addQueryParameter("size", size.toString()).build().toString() }

    /** maxBitRate 0 = original quality. */
    fun streamUrl(cfg: ServerConfig, songId: String, maxBitRate: Int = 0): String =
        base(cfg, "stream").addQueryParameter("id", songId).apply { if (maxBitRate > 0) addQueryParameter("maxBitRate", maxBitRate.toString()) }.build().toString()
}
