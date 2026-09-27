package com.sridhar.harbor.data.music

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: String,
    val title: String = "",
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
    val track: Int? = null,
    val year: Int? = null,
    val suffix: String? = null,
    val bitRate: Int? = null,
    val starred: String? = null,
    val playCount: Int = 0,
    /** Set for internet / FM radio stations: played live from this URL instead of Navidrome. */
    val streamUrl: String? = null,
) {
    val displayTitle get() = MusicText.cleanTitle(title)
    val displayArtist get() = MusicText.cleanArtist(artist).let { a -> if (a == "Unknown artist") MusicText.creditsFromTitle(title) ?: a else a }
    val displayAlbum get() = album?.let { MusicText.cleanAlbum(it) }
    val liked get() = starred != null
    /** Text for a generated cover: the album, unless the song is an untagged single. */
    val coverTitle get() = displayAlbum?.takeIf { it != "Singles" } ?: displayTitle
}

@Serializable
data class Album(
    val id: String,
    val name: String = "",
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val year: Int? = null,
    val genre: String? = null,
    val playCount: Int = 0,
    val starred: String? = null,
    @SerialName("song") val songs: List<Song> = emptyList(),
) {
    val displayName get() = MusicText.cleanAlbum(name)
    val displayArtist get() = MusicText.cleanArtist(artist)
}

@Serializable
data class Artist(
    val id: String,
    val name: String = "",
    val coverArt: String? = null,
    val artistImageUrl: String? = null,
    val albumCount: Int = 0,
    val starred: String? = null,
    @SerialName("album") val albums: List<Album> = emptyList(),
) {
    val displayName get() = MusicText.cleanArtist(name)
}

@Serializable
data class Playlist(
    val id: String,
    val name: String = "",
    val comment: String? = null,
    val owner: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String? = null,
    @SerialName("entry") val songs: List<Song> = emptyList(),
)

@Serializable data class ArtistIndex(val name: String = "", val artist: List<Artist> = emptyList())
@Serializable data class ArtistsResult(val index: List<ArtistIndex> = emptyList())
@Serializable data class AlbumList(val album: List<Album> = emptyList())
@Serializable data class SongList(val song: List<Song> = emptyList())
@Serializable data class PlaylistList(val playlist: List<Playlist> = emptyList())
@Serializable data class SearchResult(val artist: List<Artist> = emptyList(), val album: List<Album> = emptyList(), val song: List<Song> = emptyList())
@Serializable data class Starred(val artist: List<Artist> = emptyList(), val album: List<Album> = emptyList(), val song: List<Song> = emptyList())
@Serializable data class Genre(val value: String = "", val songCount: Int = 0, val albumCount: Int = 0)
@Serializable data class GenreList(val genre: List<Genre> = emptyList())
@Serializable data class SimilarArtist(val id: String, val name: String = "", val coverArt: String? = null, val artistImageUrl: String? = null)
@Serializable data class ArtistInfo(val biography: String? = null, val largeImageUrl: String? = null, val similarArtist: List<SimilarArtist> = emptyList())

@Serializable data class LyricLine(val start: Long? = null, val value: String = "")
@Serializable data class StructuredLyrics(val synced: Boolean = false, val lang: String? = null, val offset: Long = 0, val line: List<LyricLine> = emptyList())
@Serializable data class LyricsList(val structuredLyrics: List<StructuredLyrics> = emptyList())

/** An artist as shown in the app: the server's duplicate spellings merged under one clean name. */
data class ArtistGroup(val name: String, val ids: List<String>, val coverArt: String?, val albumCount: Int)
