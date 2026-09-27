package com.sridhar.harbor.data.jellyfin.admin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AdminUser(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
    @SerialName("HasPassword") val hasPassword: Boolean = true,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerialName("LastLoginDate") val lastLogin: String? = null,
    @SerialName("LastActivityDate") val lastActivity: String? = null,
    @SerialName("Policy") val policy: AdminPolicy = AdminPolicy(),
)

@Serializable
data class AdminPolicy(
    @SerialName("IsAdministrator") val isAdministrator: Boolean = false,
    @SerialName("IsDisabled") val isDisabled: Boolean = false,
    @SerialName("IsHidden") val isHidden: Boolean = false,
    @SerialName("EnableAllFolders") val enableAllFolders: Boolean = true,
    @SerialName("EnabledFolders") val enabledFolders: List<String> = emptyList(),
    @SerialName("RemoteClientBitrateLimit") val remoteBitrateLimit: Long = 0,
    @SerialName("MaxActiveSessions") val maxActiveSessions: Int = 0,
    @SerialName("LoginAttemptsBeforeLockout") val loginAttemptsBeforeLockout: Int = -1,
)

@Serializable
data class NowPlaying(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("Type") val type: String = "",
    @SerialName("ParentIndexNumber") val season: Int? = null,
    @SerialName("IndexNumber") val episode: Int? = null,
    @SerialName("ProductionYear") val year: Int? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
) {
    val title get() = if (seriesName != null) "$seriesName · S${season ?: 0}E${episode ?: 0} · $name" else listOfNotNull(name, year?.toString()).joinToString(" · ")
}

@Serializable
data class SessionPlayState(
    @SerialName("PositionTicks") val positionTicks: Long? = null,
    @SerialName("IsPaused") val isPaused: Boolean = false,
    @SerialName("IsMuted") val isMuted: Boolean = false,
    @SerialName("CanSeek") val canSeek: Boolean = false,
    @SerialName("PlayMethod") val playMethod: String? = null,
)

@Serializable
data class TranscodingInfo(
    @SerialName("VideoCodec") val videoCodec: String? = null,
    @SerialName("AudioCodec") val audioCodec: String? = null,
    @SerialName("Bitrate") val bitrate: Long? = null,
    @SerialName("IsVideoDirect") val isVideoDirect: Boolean = false,
    @SerialName("IsAudioDirect") val isAudioDirect: Boolean = false,
    @SerialName("CompletionPercentage") val completion: Double? = null,
    @SerialName("TranscodeReasons") val reasons: List<String> = emptyList(),
    @SerialName("HardwareAccelerationType") val hwAccel: String? = null,
)

@Serializable
data class AdminSession(
    @SerialName("Id") val id: String,
    @SerialName("UserId") val userId: String? = null,
    @SerialName("UserName") val userName: String? = null,
    @SerialName("Client") val client: String = "",
    @SerialName("DeviceName") val deviceName: String = "",
    @SerialName("DeviceId") val deviceId: String = "",
    @SerialName("ApplicationVersion") val appVersion: String = "",
    @SerialName("RemoteEndPoint") val remoteEndPoint: String? = null,
    @SerialName("LastActivityDate") val lastActivity: String? = null,
    @SerialName("IsActive") val isActive: Boolean = false,
    @SerialName("SupportsRemoteControl") val supportsRemoteControl: Boolean = false,
    @SerialName("SupportsMediaControl") val supportsMediaControl: Boolean = false,
    @SerialName("NowPlayingItem") val nowPlaying: NowPlaying? = null,
    @SerialName("PlayState") val playState: SessionPlayState = SessionPlayState(),
    @SerialName("TranscodingInfo") val transcoding: TranscodingInfo? = null,
) {
    val progress: Float get() {
        val run = nowPlaying?.runTimeTicks ?: return 0f
        return if (run > 0) ((playState.positionTicks ?: 0).toFloat() / run).coerceIn(0f, 1f) else 0f
    }
    /** "Direct play", "Direct stream" or "Transcode (h264 → hevc)". */
    val playMethodLabel: String get() = when {
        transcoding != null && !(transcoding.isVideoDirect && transcoding.isAudioDirect) -> "Transcoding" + (transcoding.videoCodec?.let { " · ${it.uppercase()}" } ?: "")
        playState.playMethod == "DirectStream" -> "Direct stream"
        else -> "Direct play"
    }
}

@Serializable
data class TaskResult(
    @SerialName("Status") val status: String = "",
    @SerialName("StartTimeUtc") val start: String? = null,
    @SerialName("EndTimeUtc") val end: String? = null,
    @SerialName("ErrorMessage") val error: String? = null,
)

@Serializable
data class ScheduledTask(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
    @SerialName("State") val state: String = "Idle",
    @SerialName("Category") val category: String = "",
    @SerialName("Description") val description: String = "",
    @SerialName("CurrentProgressPercentage") val progress: Double? = null,
    @SerialName("LastExecutionResult") val last: TaskResult? = null,
) {
    val running get() = state == "Running"
    val cancelling get() = state == "Cancelling"
}

@Serializable
data class ActivityEntry(
    @SerialName("Id") val id: Long,
    @SerialName("Name") val name: String = "",
    @SerialName("ShortOverview") val overview: String? = null,
    @SerialName("Type") val type: String = "",
    @SerialName("Date") val date: String? = null,
    @SerialName("Severity") val severity: String = "Information",
)

@Serializable
data class ActivityPage(@SerialName("Items") val items: List<ActivityEntry> = emptyList(), @SerialName("TotalRecordCount") val total: Int = 0)

@Serializable
data class DeviceInfo(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
    @SerialName("CustomName") val customName: String? = null,
    @SerialName("AppName") val appName: String = "",
    @SerialName("AppVersion") val appVersion: String = "",
    @SerialName("LastUserName") val lastUserName: String? = null,
    @SerialName("DateLastActivity") val lastActivity: String? = null,
)

@Serializable
data class DevicePage(@SerialName("Items") val items: List<DeviceInfo> = emptyList())

@Serializable
data class PluginInfo(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
    @SerialName("Version") val version: String = "",
    @SerialName("Description") val description: String = "",
    @SerialName("Status") val status: String = "",
    @SerialName("CanUninstall") val canUninstall: Boolean = false,
) {
    val enabled get() = status == "Active" || status == "Restart"
}

@Serializable
data class ApiKey(
    @SerialName("AccessToken") val token: String,
    @SerialName("AppName") val appName: String = "",
    @SerialName("DateCreated") val created: String? = null,
)

@Serializable
data class ApiKeyPage(@SerialName("Items") val items: List<ApiKey> = emptyList())

@Serializable
data class SystemInfo(
    @SerialName("ServerName") val serverName: String = "",
    @SerialName("Version") val version: String = "",
    @SerialName("OperatingSystem") val os: String = "",
    @SerialName("OperatingSystemDisplayName") val osDisplay: String? = null,
    @SerialName("LocalAddress") val localAddress: String? = null,
    @SerialName("HasPendingRestart") val pendingRestart: Boolean = false,
    @SerialName("HasUpdateAvailable") val updateAvailable: Boolean = false,
    @SerialName("CanSelfRestart") val canSelfRestart: Boolean = true,
    @SerialName("ProgramDataPath") val dataPath: String? = null,
    @SerialName("CachePath") val cachePath: String? = null,
    @SerialName("LogPath") val logPath: String? = null,
    @SerialName("TranscodingTempPath") val transcodePath: String? = null,
)

@Serializable
data class ItemCounts(
    @SerialName("MovieCount") val movies: Int = 0,
    @SerialName("SeriesCount") val series: Int = 0,
    @SerialName("EpisodeCount") val episodes: Int = 0,
    @SerialName("AlbumCount") val albums: Int = 0,
    @SerialName("SongCount") val songs: Int = 0,
    @SerialName("BoxSetCount") val collections: Int = 0,
)

@Serializable
data class LibraryFolder(
    @SerialName("Name") val name: String = "",
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("Locations") val locations: List<String> = emptyList(),
    @SerialName("ItemId") val itemId: String = "",
    @SerialName("RefreshProgress") val refreshProgress: Double? = null,
    @SerialName("RefreshStatus") val refreshStatus: String? = null,
)

@Serializable
data class LogFile(
    @SerialName("Name") val name: String,
    @SerialName("Size") val size: Long = 0,
    @SerialName("DateModified") val modified: String? = null,
)

@Serializable
data class NewUser(@SerialName("Name") val name: String, @SerialName("Password") val password: String)

@Serializable
data class PasswordChange(
    @SerialName("CurrentPw") val currentPw: String = "",
    @SerialName("NewPw") val newPw: String = "",
    @SerialName("ResetPassword") val resetPassword: Boolean = false,
)

@Serializable
data class SessionMessage(@SerialName("Header") val header: String, @SerialName("Text") val text: String, @SerialName("TimeoutMs") val timeoutMs: Long = 8000)

/** Remote-control commands accepted by POST /Sessions/{id}/Playing/{command}. */
enum class PlayCommand { PlayPause, Pause, Unpause, Stop, NextTrack, PreviousTrack, Seek, Rewind, FastForward }

/** Library content types Jellyfin accepts when creating a virtual folder. */
enum class LibraryType(val api: String, val label: String) {
    Movies("movies", "Movies"), Shows("tvshows", "Shows"), Music("music", "Music"), MusicVideos("musicvideos", "Music videos"),
    HomeVideos("homevideos", "Home videos & photos"), Books("books", "Books"), Mixed("mixed", "Mixed content"),
}
