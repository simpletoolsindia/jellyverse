package com.sridhar.harbor.data.qbit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Torrent(
    val hash: String,
    val name: String = "",
    val size: Long = 0,
    @SerialName("total_size") val totalSize: Long = 0,
    val progress: Float = 0f,
    val dlspeed: Long = 0,
    val upspeed: Long = 0,
    val eta: Long = 0,
    val state: String = "",
    val category: String = "",
    val tags: String = "",
    val ratio: Double = 0.0,
    @SerialName("num_seeds") val seeds: Int = 0,
    @SerialName("num_leechs") val leechers: Int = 0,
    @SerialName("num_complete") val seedsTotal: Int = 0,
    @SerialName("num_incomplete") val leechersTotal: Int = 0,
    @SerialName("added_on") val addedOn: Long = 0,
    @SerialName("completion_on") val completionOn: Long = 0,
    @SerialName("save_path") val savePath: String = "",
    val downloaded: Long = 0,
    val uploaded: Long = 0,
    @SerialName("amount_left") val amountLeft: Long = 0,
    @SerialName("dl_limit") val dlLimit: Long = 0,
    @SerialName("up_limit") val upLimit: Long = 0,
    @SerialName("seq_dl") val sequential: Boolean = false,
    @SerialName("f_l_piece_prio") val firstLastPrio: Boolean = false,
    @SerialName("force_start") val forceStart: Boolean = false,
    val tracker: String = "",
) {
    val phase: TorrentPhase
        get() = when (state) {
            "downloading", "forcedDL", "metaDL", "forcedMetaDL", "allocating" -> TorrentPhase.Downloading
            "stalledDL", "queuedDL", "checkingDL" -> TorrentPhase.Waiting
            "uploading", "forcedUP", "stalledUP", "queuedUP", "checkingUP" -> TorrentPhase.Seeding
            "pausedDL", "stoppedDL" -> TorrentPhase.Paused
            "pausedUP", "stoppedUP" -> TorrentPhase.Done
            "checkingResumeData", "moving" -> TorrentPhase.Waiting
            "error", "missingFiles" -> TorrentPhase.Error
            else -> TorrentPhase.Waiting
        }
    val isStopped get() = state.startsWith("paused") || state.startsWith("stopped")
    val stateLabel: String
        get() = when (state) {
            "metaDL", "forcedMetaDL" -> "Fetching metadata"
            "forcedDL" -> "Forced download"
            "stalledDL" -> "Stalled"
            "queuedDL", "queuedUP" -> "Queued"
            "checkingDL", "checkingUP", "checkingResumeData" -> "Checking"
            "stalledUP" -> "Seeding (idle)"
            "uploading", "forcedUP" -> "Seeding"
            "pausedDL", "stoppedDL" -> "Paused"
            "pausedUP", "stoppedUP" -> "Completed"
            "missingFiles" -> "Missing files"
            "error" -> "Error"
            "moving" -> "Moving"
            "allocating" -> "Allocating"
            else -> "Downloading"
        }
}

enum class TorrentPhase { Downloading, Waiting, Seeding, Paused, Done, Error }

@Serializable
data class TransferInfo(
    @SerialName("dl_info_speed") val dlSpeed: Long = 0,
    @SerialName("up_info_speed") val upSpeed: Long = 0,
    @SerialName("dl_info_data") val dlData: Long = 0,
    @SerialName("up_info_data") val upData: Long = 0,
    @SerialName("dl_rate_limit") val dlLimit: Long = 0,
    @SerialName("up_rate_limit") val upLimit: Long = 0,
    @SerialName("dht_nodes") val dhtNodes: Int = 0,
    @SerialName("connection_status") val connectionStatus: String = "",
)

@Serializable
data class QbitPrefs(
    @SerialName("save_path") val savePath: String = "",
    @SerialName("dl_limit") val dlLimit: Long = 0,
    @SerialName("up_limit") val upLimit: Long = 0,
    @SerialName("alt_dl_limit") val altDlLimit: Long = 0,
    @SerialName("alt_up_limit") val altUpLimit: Long = 0,
    @SerialName("max_active_downloads") val maxActiveDownloads: Int = 0,
)

@Serializable
data class QbitCategory(val name: String = "", val savePath: String = "")

@Serializable
data class TorrentFile(
    val index: Int = 0,
    val name: String = "",
    val size: Long = 0,
    val progress: Float = 0f,
    val priority: Int = 1,
)

data class AddTorrentRequest(
    val urls: String = "",
    val fileName: String? = null,
    val fileBytes: ByteArray? = null,
    val savePath: String = "",
    val category: String = "",
    val startPaused: Boolean = false,
    val sequential: Boolean = false,
    val firstLastPiece: Boolean = false,
)
