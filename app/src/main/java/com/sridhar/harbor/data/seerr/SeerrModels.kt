package com.sridhar.harbor.data.seerr

import kotlinx.serialization.Serializable

@Serializable
data class PageResult<T>(
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalResults: Int = 0,
    val results: List<T> = emptyList(),
)

@Serializable
data class SeasonStatus(val seasonNumber: Int = 0, val status: Int = 1)

@Serializable
data class MediaInfo(
    val id: Int = 0,
    val tmdbId: Int = 0,
    val mediaType: String? = null,
    val status: Int = 1,
    val status4k: Int = 1,
    val seasons: List<SeasonStatus> = emptyList(),
    val jellyfinMediaId: String? = null,
)

@Serializable
data class SeerrMedia(
    val id: Int,
    val mediaType: String = "movie",
    val title: String? = null,
    val name: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val profilePath: String? = null,
    val overview: String? = null,
    val voteAverage: Double? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val mediaInfo: MediaInfo? = null,
) {
    val displayTitle get() = title ?: name ?: ""
    val year get() = (releaseDate ?: firstAirDate)?.take(4)
    val status get() = MediaStatus.of(mediaInfo?.status)
}

@Serializable
data class Genre(val id: Int = 0, val name: String = "")

@Serializable
data class CastMember(val id: Int = 0, val name: String = "", val character: String? = null, val profilePath: String? = null)

@Serializable
data class Credits(val cast: List<CastMember> = emptyList())

@Serializable
data class TvSeason(
    val id: Int = 0,
    val seasonNumber: Int = 0,
    val name: String = "",
    val episodeCount: Int = 0,
    val airDate: String? = null,
    val posterPath: String? = null,
)

@Serializable
data class RelatedVideo(val url: String? = null, val key: String? = null, val site: String? = null, val type: String? = null, val name: String? = null)

@Serializable
data class SeerrDetails(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val runtime: Int? = null,
    val episodeRunTime: List<Int> = emptyList(),
    val voteAverage: Double? = null,
    val genres: List<Genre> = emptyList(),
    val credits: Credits = Credits(),
    val seasons: List<TvSeason> = emptyList(),
    val numberOfSeasons: Int? = null,
    val status: String? = null,
    val mediaInfo: MediaInfo? = null,
    val relatedVideos: List<RelatedVideo> = emptyList(),
) {
    val displayTitle get() = title ?: name ?: ""
    val year get() = (releaseDate ?: firstAirDate)?.take(4)
    val trailerUrl get() = relatedVideos.firstOrNull { it.type == "Trailer" && it.site == "YouTube" }?.url
}

@Serializable
data class SeerrUser(
    val id: Int,
    val email: String? = null,
    val displayName: String = "",
    val username: String? = null,
    val jellyfinUsername: String? = null,
    val avatar: String? = null,
    val permissions: Long = 0,
    val requestCount: Int = 0,
    val userType: Int = 0,
    val createdAt: String? = null,
    val movieQuotaLimit: Int? = null,
    val tvQuotaLimit: Int? = null,
) {
    val name get() = displayName.ifBlank { jellyfinUsername ?: username ?: email ?: "User $id" }
    fun has(p: Long) = permissions and Permission.ADMIN != 0L || permissions and p != 0L
    val isAdmin get() = permissions and Permission.ADMIN != 0L
}

@Serializable
data class RequestMedia(
    val id: Int = 0,
    val tmdbId: Int = 0,
    val mediaType: String = "movie",
    val status: Int = 1,
)

@Serializable
data class SeerrRequest(
    val id: Int,
    val status: Int = 1,
    val createdAt: String? = null,
    val type: String = "movie",
    val is4k: Boolean = false,
    val media: RequestMedia = RequestMedia(),
    val requestedBy: SeerrUser? = null,
    val modifiedBy: SeerrUser? = null,
    val seasons: List<SeasonStatus> = emptyList(),
)

@Serializable
data class PageInfo(val pages: Int = 1, val page: Int = 1, val results: Int = 0)

@Serializable
data class RequestPage(val pageInfo: PageInfo = PageInfo(), val results: List<SeerrRequest> = emptyList())

@Serializable
data class UserPage(val pageInfo: PageInfo = PageInfo(), val results: List<SeerrUser> = emptyList())

@Serializable
data class RequestCount(
    val total: Int = 0, val movie: Int = 0, val tv: Int = 0, val pending: Int = 0,
    val approved: Int = 0, val declined: Int = 0, val processing: Int = 0, val available: Int = 0,
)

@Serializable
data class NewRequest(
    val mediaType: String,
    val mediaId: Int,
    val seasons: List<Int>? = null,
    val is4k: Boolean = false,
)

@Serializable
data class JellyfinImportUser(val id: String, val username: String = "", val thumb: String? = null, val email: String? = null)

@Serializable
data class ImportUsersBody(val jellyfinUserIds: List<String>)

@Serializable
data class PermissionsBody(val permissions: Long)

@Serializable
data class JellyfinLoginBody(val username: String, val password: String)

@Serializable
data class SeerrStatus(val version: String = "")

enum class MediaStatus(val label: String) {
    Unknown("Not requested"), Pending("Pending"), Processing("Processing"),
    Partial("Partially available"), Available("Available"), Blocked("Blocklisted");

    companion object {
        fun of(code: Int?) = when (code) {
            2 -> Pending; 3 -> Processing; 4 -> Partial; 5 -> Available; 6 -> Blocked; else -> Unknown
        }
    }
}

enum class RequestStatus(val label: String) {
    Pending("Pending"), Approved("Approved"), Declined("Declined"), Failed("Failed"), Completed("Completed");

    companion object {
        fun of(code: Int) = when (code) { 2 -> Approved; 3 -> Declined; 4 -> Failed; 5 -> Completed; else -> Pending }
    }
}

/** Jellyseerr permission bit flags (server/lib/permissions.ts). */
object Permission {
    const val ADMIN = 2L
    const val MANAGE_SETTINGS = 4L
    const val MANAGE_USERS = 8L
    const val MANAGE_REQUESTS = 16L
    const val REQUEST = 32L
    const val AUTO_APPROVE = 128L
    const val AUTO_APPROVE_MOVIE = 256L
    const val AUTO_APPROVE_TV = 512L
    const val REQUEST_4K = 1024L
    const val REQUEST_ADVANCED = 8192L
    const val REQUEST_VIEW = 16384L
    const val REQUEST_MOVIE = 262144L
    const val REQUEST_TV = 524288L
    const val MANAGE_ISSUES = 1048576L
    const val VIEW_ISSUES = 2097152L
    const val CREATE_ISSUES = 4194304L

    val editable = listOf(
        ADMIN to "Admin",
        MANAGE_USERS to "Manage users",
        MANAGE_REQUESTS to "Manage requests",
        REQUEST to "Request",
        REQUEST_MOVIE to "Request movies",
        REQUEST_TV to "Request series",
        AUTO_APPROVE to "Auto-approve",
        AUTO_APPROVE_MOVIE to "Auto-approve movies",
        AUTO_APPROVE_TV to "Auto-approve series",
        REQUEST_4K to "Request 4K",
        REQUEST_ADVANCED to "Advanced requests",
        REQUEST_VIEW to "View requests",
        MANAGE_ISSUES to "Manage issues",
        CREATE_ISSUES to "Report issues",
    )
}
