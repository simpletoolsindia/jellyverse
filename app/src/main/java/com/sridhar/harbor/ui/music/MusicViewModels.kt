package com.sridhar.harbor.ui.music

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.music.Album
import com.sridhar.harbor.data.music.ArtistGroup
import com.sridhar.harbor.data.music.ArtistInfo
import com.sridhar.harbor.data.music.Genre
import com.sridhar.harbor.data.music.MusicText
import com.sridhar.harbor.data.music.Playlist
import com.sridhar.harbor.data.music.SearchResult
import com.sridhar.harbor.data.music.Song
import com.sridhar.harbor.ui.components.friendly
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A generated "Made for you" mix: a title, a seed for its cover and how to fetch its songs. */
data class Mix(val key: String, val title: String, val subtitle: String, val genre: String? = null)

class MusicHomeViewModel(private val c: AppContainer) : ViewModel() {
    var loading by mutableStateOf(true); private set
    var refreshing by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var recent by mutableStateOf<List<Album>>(emptyList()); private set
    var newest by mutableStateOf<List<Album>>(emptyList()); private set
    var frequent by mutableStateOf<List<Album>>(emptyList()); private set
    var forgotten by mutableStateOf<List<Album>>(emptyList()); private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList()); private set
    var artists by mutableStateOf<List<ArtistGroup>>(emptyList()); private set
    var genres by mutableStateOf<List<Genre>>(emptyList()); private set
    var liked by mutableStateOf<List<Song>>(emptyList()); private set

    val configured get() = c.config.value?.navidromeReady == true

    init { load() }

    fun load(pull: Boolean = false) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        if (!c.settings.current().navidromeReady) { loading = false; return@launch }
        if (pull) refreshing = true else loading = true
        error = null
        runCatching {
            val r = c.music
            val a = async { r.albums("recent", 12) }; val n = async { r.albums("newest", 16) }; val f = async { r.albums("frequent", 16) }
            val rnd = async { r.albums("random", 16) }; val p = async { r.playlists() }; val ar = async { r.artists() }
            val g = async { r.genres() }; val st = async { r.starred() }
            recent = a.await(); newest = n.await(); frequent = f.await(); playlists = p.await()
            artists = MusicText.groupArtists(ar.await()).sortedByDescending { it.albumCount }
            genres = g.await(); liked = st.await().song
            // "Rediscover": random albums you haven't played much.
            forgotten = rnd.await().filter { it.playCount <= 1 }.take(12)
            c.musicEngine.setLikedIds(liked.map { it.id }.toSet())
        }.onFailure { error = it.friendly() }
        loading = false; refreshing = false
        c.musicEngine.restore()
    }

    /** Daily-mix style cards: one per real genre, plus a shuffle of everything. */
    val mixes: List<Mix> get() = listOf(Mix("all", L10n.s(R.string.mu_shuffle_mix), L10n.s(R.string.mu_everything_shuffled))) +
        genres.take(6).mapIndexed { i, g -> Mix("g:${g.value}", L10n.s(R.string.mu_genre_mix, g.value), L10n.s(R.string.mu_songs_count, g.songCount), g.value) }

    fun playMix(m: Mix) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        runCatching { c.musicEngine.play(c.music.randomSongs(60, m.genre), source = m.title) }.onFailure { error = it.friendly() }
    }

    fun playLiked(shuffle: Boolean = false) = c.musicEngine.play(liked, shuffle = shuffle, source = L10n.s(R.string.mu_liked_songs))
}

/** Album, playlist, liked songs – anything that is "a list of songs with a cover". */
class CollectionViewModel(private val c: AppContainer, val kind: String, val id: String) : ViewModel() {
    var title by mutableStateOf(""); private set
    var subtitle by mutableStateOf(""); private set
    var cover by mutableStateOf<String?>(null); private set
    var songs by mutableStateOf<List<Song>>(emptyList()); private set
    var artistId by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null)

    init { load() }

    fun load() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        loading = true; error = null
        runCatching {
            when (kind) {
                "album" -> c.music.album(id).let { a ->
                    title = a.displayName; cover = a.coverArt; songs = a.songs; artistId = a.artistId
                    subtitle = listOfNotNull(a.displayArtist, a.year?.toString(), MusicText.totalDuration(a.duration)).joinToString(" • ")
                }
                "playlist" -> c.music.playlist(id).let { p ->
                    title = p.name; cover = p.coverArt; songs = p.songs
                    subtitle = listOfNotNull(p.owner, L10n.s(R.string.mu_songs_count, p.songCount), MusicText.totalDuration(p.duration)).joinToString(" • ")
                }
                "downloaded" -> c.offlineMusic.songs.value.let { s ->
                    title = L10n.s(R.string.mu_downloaded); songs = s; cover = s.firstOrNull()?.coverArt
                    subtitle = L10n.s(R.string.mu_songs_count, s.size)
                }
                else -> c.music.starred().song.let { s ->
                    title = L10n.s(R.string.mu_liked_songs); songs = s; cover = null
                    subtitle = L10n.s(R.string.mu_songs_count, s.size)
                }
            }
        }.onFailure { error = it.friendly() }
        loading = false
    }

    val source get() = when (kind) { "album" -> L10n.s(R.string.mu_src_album, title); "playlist" -> L10n.s(R.string.mu_src_playlist, title); else -> title }

    fun play(index: Int = 0, shuffle: Boolean = false) = c.musicEngine.play(songs, index, shuffle, source)

    fun download() { c.offlineMusic.download(songs); message = L10n.s(R.string.mu_downloading_n, songs.size) }
    fun removeDownloads() { c.offlineMusic.remove(songs); message = L10n.s(R.string.mu_removed_downloads) }

    fun addAllToQueue() { c.musicEngine.addToQueue(songs); message = L10n.s(R.string.mu_added_to_queue, songs.size) }
}

class ArtistViewModel(private val c: AppContainer, val ids: List<String>, val name: String) : ViewModel() {
    var albums by mutableStateOf<List<Album>>(emptyList()); private set
    var top by mutableStateOf<List<Song>>(emptyList()); private set
    var info by mutableStateOf(ArtistInfo()); private set
    var cover by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set

    init { load() }

    fun load() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        loading = true
        runCatching {
            // Merged spellings: gather albums from every id that belongs to this artist.
            val all = ids.map { id -> async { c.music.artist(id) } }.map { it.await() }
            albums = all.flatMap { it.albums }.distinctBy { it.id }.sortedByDescending { it.year ?: 0 }
            cover = all.firstNotNullOfOrNull { it.coverArt } ?: albums.firstNotNullOfOrNull { it.coverArt }
            info = c.music.artistInfo(ids.first())
            top = c.music.topSongs(name, 10).ifEmpty {
                // No Last.fm data: most-played songs across the artist's albums.
                albums.take(8).map { a -> async { c.music.album(a.id).songs } }.flatMap { it.await() }.sortedByDescending { it.playCount }.take(10)
            }
        }.onFailure { error = it.friendly() }
        loading = false
    }

    fun playTop(index: Int = 0, shuffle: Boolean = false) = c.musicEngine.play(top, index, shuffle, L10n.s(R.string.mu_src_artist, name))
}

class MusicSearchViewModel(private val c: AppContainer) : ViewModel() {
    var query by mutableStateOf(""); private set
    var result by mutableStateOf<SearchResult?>(null); private set
    var artists by mutableStateOf<List<ArtistGroup>>(emptyList()); private set
    var searching by mutableStateOf(false); private set
    var genres by mutableStateOf<List<Genre>>(emptyList()); private set
    private var job: Job? = null

    init { viewModelScope.launch(com.sridhar.harbor.CrashGuard) { genres = runCatching { c.music.genres() }.getOrDefault(emptyList()) } }

    fun onQuery(q: String) {
        query = q; job?.cancel()
        if (q.isBlank()) { result = null; return }
        job = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
            delay(250)   // debounce typing
            searching = true
            result = runCatching { c.music.search(q.trim()) }.getOrNull()
            artists = MusicText.groupArtists(result?.artist.orEmpty())
            searching = false
        }
    }

    fun playGenre(g: Genre) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) { runCatching { c.musicEngine.play(c.music.randomSongs(60, g.value), source = L10n.s(R.string.mu_genre_mix, g.value)) } }
}

class MusicLibraryViewModel(private val c: AppContainer) : ViewModel() {
    var playlists by mutableStateOf<List<Playlist>>(emptyList()); private set
    var albums by mutableStateOf<List<Album>>(emptyList()); private set
    var artists by mutableStateOf<List<ArtistGroup>>(emptyList()); private set
    var likedCount by mutableIntStateOf(0); private set
    var loading by mutableStateOf(true); private set

    init { load() }

    fun load() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        loading = true
        runCatching {
            val p = async { c.music.playlists() }; val a = async { c.music.albums("alphabeticalByName", 500) }
            val ar = async { c.music.artists() }; val s = async { c.music.starred() }
            playlists = p.await(); albums = a.await().sortedBy { it.displayName.lowercase() }
            artists = MusicText.groupArtists(ar.await()); likedCount = s.await().song.size
        }
        loading = false
    }

    fun createPlaylist(name: String) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) { runCatching { c.music.createPlaylist(name) }; load() }
}
