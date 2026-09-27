package com.sridhar.harbor.data.arr

import kotlinx.serialization.Serializable

enum class ArrKind(val label: String) { Sonarr("Sonarr"), Radarr("Radarr") }

@Serializable
data class Paged<T>(val page: Int = 1, val pageSize: Int = 0, val totalRecords: Int = 0, val records: List<T> = emptyList())

@Serializable data class ArrImage(val coverType: String = "", val url: String? = null, val remoteUrl: String? = null)
@Serializable data class QualityName(val name: String = "")
@Serializable data class ArrQuality(val quality: QualityName = QualityName())
@Serializable data class ArrLanguage(val name: String = "")
@Serializable data class StatusMessage(val title: String? = null, val messages: List<String> = emptyList())

fun List<ArrImage>.poster() = firstOrNull { it.coverType == "poster" }?.remoteUrl?.replace("/original/", "/w342/")
fun List<ArrImage>.fanart() = firstOrNull { it.coverType == "fanart" }?.remoteUrl?.replace("/original/", "/w780/")

@Serializable
data class ArrMovieFile(val id: Int = 0, val relativePath: String? = null, val size: Long = 0, val quality: ArrQuality? = null)

@Serializable
data class ArrMovie(
    val id: Int = 0,
    val title: String = "",
    val year: Int = 0,
    val overview: String? = null,
    val images: List<ArrImage> = emptyList(),
    val hasFile: Boolean = false,
    val monitored: Boolean = false,
    val status: String? = null,
    val sizeOnDisk: Long = 0,
    val runtime: Int = 0,
    val tmdbId: Int = 0,
    val qualityProfileId: Int = 0,
    val path: String? = null,
    val inCinemas: String? = null,
    val digitalRelease: String? = null,
    val physicalRelease: String? = null,
    val isAvailable: Boolean = false,
    val added: String? = null,
    val genres: List<String> = emptyList(),
    val certification: String? = null,
    val movieFile: ArrMovieFile? = null,
    val youTubeTrailerId: String? = null,
)

@Serializable
data class SeasonStats(
    val episodeFileCount: Int = 0, val episodeCount: Int = 0, val totalEpisodeCount: Int = 0,
    val sizeOnDisk: Long = 0, val percentOfEpisodes: Double = 0.0,
)

@Serializable
data class SeriesStats(
    val seasonCount: Int = 0, val episodeFileCount: Int = 0, val episodeCount: Int = 0,
    val totalEpisodeCount: Int = 0, val sizeOnDisk: Long = 0, val percentOfEpisodes: Double = 0.0,
)

@Serializable data class ArrSeason(val seasonNumber: Int = 0, val monitored: Boolean = false, val statistics: SeasonStats? = null)

@Serializable
data class ArrSeries(
    val id: Int = 0,
    val title: String = "",
    val year: Int = 0,
    val overview: String? = null,
    val images: List<ArrImage> = emptyList(),
    val status: String? = null,
    val network: String? = null,
    val monitored: Boolean = false,
    val qualityProfileId: Int = 0,
    val path: String? = null,
    val seasons: List<ArrSeason> = emptyList(),
    val statistics: SeriesStats? = null,
    val nextAiring: String? = null,
    val previousAiring: String? = null,
    val tvdbId: Int = 0,
    val genres: List<String> = emptyList(),
)

@Serializable
data class ArrEpisode(
    val id: Int = 0,
    val seriesId: Int = 0,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val title: String? = null,
    val airDateUtc: String? = null,
    val overview: String? = null,
    val hasFile: Boolean = false,
    val monitored: Boolean = false,
    val series: ArrSeries? = null,
) {
    val code get() = "S%02dE%02d".format(seasonNumber, episodeNumber)
}

@Serializable
data class QueueItem(
    val id: Int = 0,
    val title: String? = null,
    val size: Double = 0.0,
    val sizeleft: Double = 0.0,
    val timeleft: String? = null,
    val status: String = "",
    val trackedDownloadStatus: String? = null,
    val trackedDownloadState: String? = null,
    val statusMessages: List<StatusMessage> = emptyList(),
    val errorMessage: String? = null,
    val downloadClient: String? = null,
    val indexer: String? = null,
    val protocol: String? = null,
    val quality: ArrQuality? = null,
    val movie: ArrMovie? = null,
    val series: ArrSeries? = null,
    val episode: ArrEpisode? = null,
) {
    val progress get() = if (size > 0) ((size - sizeleft) / size).toFloat().coerceIn(0f, 1f) else 0f
    val hasIssue get() = trackedDownloadStatus == "warning" || trackedDownloadStatus == "error" || status == "failed" || status == "warning"
}

@Serializable data class DiskSpace(val path: String = "", val label: String? = null, val freeSpace: Long = 0, val totalSpace: Long = 0)
@Serializable data class HealthItem(val source: String? = null, val type: String = "", val message: String = "", val wikiUrl: String? = null)
@Serializable data class QualityProfile(val id: Int = 0, val name: String = "")
@Serializable data class SystemStatus(val version: String = "", val appName: String? = null)
@Serializable data class InitializeJson(val apiKey: String = "", val apiRoot: String? = null)

@Serializable
data class ArrRelease(
    val guid: String = "",
    val indexerId: Int = 0,
    val indexer: String? = null,
    val title: String = "",
    val size: Long = 0,
    val seeders: Int? = null,
    val leechers: Int? = null,
    val protocol: String = "",
    val quality: ArrQuality? = null,
    val rejected: Boolean = false,
    val rejections: List<String> = emptyList(),
    val ageHours: Double? = null,
    val approved: Boolean = false,
    val customFormatScore: Int = 0,
    val languages: List<ArrLanguage> = emptyList(),
)

@Serializable data class GrabBody(val guid: String, val indexerId: Int)

/** Something in Sonarr or Radarr that is missing / upcoming, normalised for combined lists. */
data class ArrEntry(
    val kind: ArrKind,
    val key: String,
    val title: String,
    val subtitle: String?,
    val poster: String?,
    val date: String?,
    val hasFile: Boolean,
    val movieId: Int? = null,
    val seriesId: Int? = null,
    val episodeId: Int? = null,
)
