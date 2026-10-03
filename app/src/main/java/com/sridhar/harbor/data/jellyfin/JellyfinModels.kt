package com.sridhar.harbor.data.jellyfin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(@SerialName("Username") val username: String, @SerialName("Pw") val pw: String)

@Serializable
data class AuthResult(
    @SerialName("AccessToken") val accessToken: String,
    @SerialName("User") val user: JfUser,
)

@Serializable
data class JfUser(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerialName("Policy") val policy: JfPolicy? = null,
)

@Serializable
data class JfPolicy(@SerialName("IsAdministrator") val isAdministrator: Boolean = false)

@Serializable
data class ItemsResult(
    @SerialName("Items") val items: List<BaseItem> = emptyList(),
    @SerialName("TotalRecordCount") val total: Int = 0,
)

@Serializable
data class UserData(
    @SerialName("PlaybackPositionTicks") val positionTicks: Long = 0,
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null,
    @SerialName("Played") val played: Boolean = false,
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("UnplayedItemCount") val unplayedCount: Int? = null,
    @SerialName("PlayCount") val playCount: Int = 0,
    @SerialName("LastPlayedDate") val lastPlayedDate: String? = null,
)

@Serializable
data class MediaStream(
    @SerialName("Index") val index: Int = 0,
    @SerialName("Type") val type: String = "",
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null,
    @SerialName("IsExternal") val isExternal: Boolean = false,
    @SerialName("IsDefault") val isDefault: Boolean = false,
    @SerialName("Height") val height: Int? = null,
    @SerialName("Width") val width: Int? = null,
    @SerialName("DeliveryUrl") val deliveryUrl: String? = null,
    @SerialName("IsTextSubtitleStream") val isTextSubtitle: Boolean = false,
)

@Serializable
data class MediaSource(
    @SerialName("Id") val id: String,
    @SerialName("Container") val container: String? = null,
    @SerialName("Size") val size: Long? = null,
    @SerialName("Bitrate") val bitrate: Long? = null,
    @SerialName("MediaStreams") val streams: List<MediaStream> = emptyList(),
)

@Serializable
data class NameRef(@SerialName("Name") val name: String = "")

@Serializable
data class Person(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
    @SerialName("Role") val role: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
)

@Serializable
data class Chapter(
    @SerialName("StartPositionTicks") val startTicks: Long = 0,
    @SerialName("Name") val name: String? = null,
    @SerialName("ImageTag") val imageTag: String? = null,
)

/** Jellyfin 10.9+ trickplay tile sheet description (per media source, per width). */
@Serializable
data class TrickplayInfo(
    @SerialName("Width") val width: Int = 0,
    @SerialName("Height") val height: Int = 0,
    @SerialName("TileWidth") val tileWidth: Int = 10,
    @SerialName("TileHeight") val tileHeight: Int = 10,
    @SerialName("ThumbnailCount") val thumbnailCount: Int = 0,
    @SerialName("Interval") val interval: Int = 10_000,
)

@Serializable
data class BaseItem(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: String = "",
    /** YouTube trailer links from metadata providers – used for the hover/focus trailer preview. */
    @SerialName("RemoteTrailers") val remoteTrailers: List<RemoteTrailer> = emptyList(),
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("ProductionYear") val year: Int? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("Tags") val tags: List<String> = emptyList(),
    @SerialName("Studios") val studios: List<NameRef> = emptyList(),
    @SerialName("Taglines") val taglines: List<String> = emptyList(),
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("ParentIndexNumber") val seasonNumber: Int? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropTags: List<String> = emptyList(),
    @SerialName("ParentBackdropItemId") val parentBackdropItemId: String? = null,
    @SerialName("ParentBackdropImageTags") val parentBackdropTags: List<String> = emptyList(),
    @SerialName("SeriesPrimaryImageTag") val seriesPrimaryImageTag: String? = null,
    @SerialName("UserData") val userData: UserData? = null,
    @SerialName("MediaSources") val mediaSources: List<MediaSource> = emptyList(),
    @SerialName("People") val people: List<Person> = emptyList(),
    @SerialName("Chapters") val chapters: List<Chapter> = emptyList(),
    @SerialName("Trickplay") val trickplay: Map<String, Map<String, TrickplayInfo>> = emptyMap(),
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("PremiereDate") val premiereDate: String? = null,
    /** Title in its original language/spelling (searched too). */
    @SerialName("OriginalTitle") val originalTitle: String? = null,
    /** When it was added to the Jellyfin library. */
    @SerialName("DateCreated") val dateCreated: String? = null,
) {
    val isFolderish get() = type in setOf("Series", "Season", "BoxSet", "Folder", "CollectionFolder")
    val progress: Float
        get() = ((userData?.playedPercentage ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val runtimeMinutes: Int? get() = runTimeTicks?.let { (it / 600_000_000L).toInt() }
    val episodeLabel: String?
        get() = if (type == "Episode" && seasonNumber != null && indexNumber != null)
            "S${seasonNumber} · E${indexNumber}" else null
}

@Serializable
data class MediaSegment(
    @SerialName("Type") val type: String = "",
    @SerialName("StartTicks") val startTicks: Long = 0,
    @SerialName("EndTicks") val endTicks: Long = 0,
)

@Serializable
data class MediaSegmentsResult(@SerialName("Items") val items: List<MediaSegment> = emptyList())

@Serializable
data class PlaybackReport(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("PositionTicks") val positionTicks: Long = 0,
    @SerialName("IsPaused") val isPaused: Boolean = false,
    @SerialName("CanSeek") val canSeek: Boolean = true,
    @SerialName("PlayMethod") val playMethod: String = "DirectPlay",
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
)

const val TICKS_PER_MS = 10_000L


@Serializable
data class LibraryItem(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: String = "",
    @SerialName("ProductionYear") val year: Int? = null,
    @SerialName("Path") val path: String? = null,
    @SerialName("ProviderIds") val providerIds: Map<String, String?> = emptyMap(),
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
) {
    val identified get() = listOf("Tmdb", "Imdb", "Tvdb").any { !providerIds[it].isNullOrBlank() }
}

@Serializable
data class LibraryItemsResult(@SerialName("Items") val items: List<LibraryItem> = emptyList())

@Serializable
data class RemoteSearchQuery(
    @SerialName("SearchInfo") val searchInfo: RemoteSearchInfo,
    @SerialName("ItemId") val itemId: String,
    @SerialName("IncludeDisabledProviders") val includeDisabled: Boolean = false,
)

@Serializable
data class RemoteSearchInfo(@SerialName("Name") val name: String, @SerialName("Year") val year: Int? = null)

@Serializable
data class RemoteSearchResult(
    @SerialName("Name") val name: String = "",
    @SerialName("ProductionYear") val year: Int? = null,
    @SerialName("ProviderIds") val providerIds: Map<String, String?> = emptyMap(),
    @SerialName("ImageUrl") val imageUrl: String? = null,
    @SerialName("SearchProviderName") val provider: String? = null,
    @SerialName("Overview") val overview: String? = null,
)

@Serializable
data class VirtualFolder(
    @SerialName("Name") val name: String = "",
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("Locations") val locations: List<String> = emptyList(),
)


@Serializable
data class QuickConnectResult(
    @SerialName("Authenticated") val authenticated: Boolean = false,
    @SerialName("Secret") val secret: String = "",
    @SerialName("Code") val code: String = "",
)

@Serializable
data class QuickConnectAuth(@SerialName("Secret") val secret: String)

@Serializable
data class RemoteSubtitle(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("ProviderName") val provider: String? = null,
    @SerialName("Format") val format: String? = null,
    @SerialName("ThreeLetterISOLanguageName") val language: String? = null,
    @SerialName("DownloadCount") val downloads: Int? = null,
    @SerialName("CommunityRating") val rating: Float? = null,
    @SerialName("IsHashMatch") val hashMatch: Boolean? = null,
    @SerialName("HearingImpaired") val hearingImpaired: Boolean? = null,
)

@Serializable
data class RemoteTrailer(@SerialName("Url") val url: String? = null, @SerialName("Name") val name: String? = null) {
    /** YouTube video id from watch?v=, youtu.be/ or /embed/ links. */
    val youtubeId: String? get() = url?.let { u ->
        Regex("""(?:v=|youtu\.be/|/embed/)([A-Za-z0-9_-]{11})""").find(u)?.groupValues?.get(1)
    }
}
