package com.sridhar.harbor.radio

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sridhar.harbor.data.HarborJson
import com.sridhar.harbor.data.music.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

/** A finished radio recording on this device. */
@Serializable
data class Recording(
    val id: String = UUID.randomUUID().toString(),
    val station: String,
    val path: String,
    val startedAt: Long,
    val durationMs: Long,
    val bytes: Long,
) {
    val file get() = File(path)
    /** Plays through the music engine with a real timeline (see Song.isLive). */
    fun toSong() = Song(id = "rec:$id", title = station, artist = "Recording · " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(startedAt),
        duration = (durationMs / 1000).toInt(), streamUrl = android.net.Uri.fromFile(file).toString())
}

/** A planned recording or listening reminder. */
@Serializable
data class RadioSchedule(
    val id: String = UUID.randomUUID().toString(),
    val stationId: String,
    val stationName: String,
    val url: String,
    val startAt: Long,
    val durationMin: Int = 60,
    /** "record" or "remind". */
    val kind: String = "record",
    val daily: Boolean = false,
)

/** Recording in progress (for UI and notification). */
data class LiveRecording(val station: String, val startedAt: Long, val durationMs: Long, val bytes: Long, val scheduleId: String? = null)

/** Recordings, schedules and the live recording state; persisted in app storage. */
object RadioLibrary {
    private lateinit var ctx: Context
    private val recSer = ListSerializer(Recording.serializer())
    private val schedSer = ListSerializer(RadioSchedule.serializer())
    private val prefs by lazy { ctx.getSharedPreferences("harbor_radio_rec", Context.MODE_PRIVATE) }

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings.asStateFlow()
    private val _schedules = MutableStateFlow<List<RadioSchedule>>(emptyList())
    val schedules: StateFlow<List<RadioSchedule>> = _schedules.asStateFlow()
    val live = MutableStateFlow<LiveRecording?>(null)

    fun init(context: Context) {
        ctx = context.applicationContext
        _recordings.value = runCatching { HarborJson.decodeFromString(recSer, prefs.getString("recordings", "[]")!!) }.getOrDefault(emptyList()).filter { it.file.exists() }
        _schedules.value = runCatching { HarborJson.decodeFromString(schedSer, prefs.getString("schedules", "[]")!!) }.getOrDefault(emptyList())
    }

    val dir: File get() = File(ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: ctx.filesDir, "Radio recordings").apply { mkdirs() }

    fun addRecording(r: Recording) { _recordings.value = listOf(r) + _recordings.value; saveRecs() }
    fun deleteRecording(r: Recording) { r.file.delete(); _recordings.value = _recordings.value.filterNot { it.id == r.id }; saveRecs() }
    private fun saveRecs() = prefs.edit().putString("recordings", HarborJson.encodeToString(recSer, _recordings.value)).apply()

    fun putSchedule(s: RadioSchedule) { _schedules.value = _schedules.value.filterNot { it.id == s.id } + s; saveScheds() }
    fun removeSchedule(id: String) { _schedules.value = _schedules.value.filterNot { it.id == id }; saveScheds() }
    fun schedule(id: String) = _schedules.value.firstOrNull { it.id == id }
    private fun saveScheds() = prefs.edit().putString("schedules", HarborJson.encodeToString(schedSer, _schedules.value)).apply()

    /**
     * Copies a recording to the phone's Downloads folder (Downloads/JellyVerse) so other apps can open it.
     * Returns a short description of where it went.
     */
    fun exportToDownloads(r: Recording): String {
        val name = r.file.name
        val mime = when (r.file.extension) { "mp3" -> "audio/mpeg"; "aac" -> "audio/aac"; "ts" -> "video/mp2t"; "m4a", "mp4" -> "audio/mp4"; else -> "audio/*" }
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/JellyVerse")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val cr = ctx.contentResolver
            val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Couldn't create the file")
            cr.openOutputStream(uri)!!.use { out -> r.file.inputStream().use { it.copyTo(out, 256 * 1024) } }
            cr.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            return "Downloads/JellyVerse/$name"
        }
        @Suppress("DEPRECATION")
        val target = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "JellyVerse").apply { mkdirs() }
        r.file.copyTo(File(target, name), overwrite = true)
        return "Downloads/JellyVerse/$name"
    }
}
