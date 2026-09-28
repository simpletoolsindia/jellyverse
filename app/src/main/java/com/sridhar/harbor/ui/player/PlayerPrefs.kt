package com.sridhar.harbor.ui.player

import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import android.content.Context

enum class SubStyle(@androidx.annotation.StringRes val labelRes: Int) { Embedded(R.string.original), Outline(R.string.outline), Box(R.string.box), Yellow(R.string.yellow);
    val label: String get() = com.sridhar.harbor.L10n.s(labelRes)
}
enum class Rotation(@androidx.annotation.StringRes val labelRes: Int) { Auto(R.string.auto_rotate), Landscape(R.string.landscape), Portrait(R.string.portrait);
    val label: String get() = com.sridhar.harbor.L10n.s(labelRes)
}

/** Sticky player preferences (per device). */
class PlayerPrefs(context: Context) {
    private val p = context.getSharedPreferences("harbor_player", Context.MODE_PRIVATE)
    var autoSkip: Boolean get() = p.getBoolean("auto_skip", false); set(v) = p.edit().putBoolean("auto_skip", v).apply()
    var autoPlayNext: Boolean get() = p.getBoolean("autoplay_next", true); set(v) = p.edit().putBoolean("autoplay_next", v).apply()
    var softwareDecoding: Boolean get() = p.getBoolean("sw_decode", false); set(v) = p.edit().putBoolean("sw_decode", v).apply()
    /** Send Dolby / DTS undecoded over HDMI (AV receivers). Off by default: decoded PCM is reliable on every TV box. */
    var passthrough: Boolean get() = p.getBoolean("passthrough", false); set(v) = p.edit().putBoolean("passthrough", v).apply()
    var backgroundPlay: Boolean get() = p.getBoolean("bg_play", false); set(v) = p.edit().putBoolean("bg_play", v).apply()
    var seekStepSec: Int get() = p.getInt("seek_step", 10); set(v) = p.edit().putInt("seek_step", v).apply()
    var subScale: Float get() = p.getFloat("sub_scale", 1f); set(v) = p.edit().putFloat("sub_scale", v).apply()
    var subStyle: SubStyle
        get() = runCatching { SubStyle.valueOf(p.getString("sub_style", SubStyle.Embedded.name)!!) }.getOrDefault(SubStyle.Embedded)
        set(v) = p.edit().putString("sub_style", v.name).apply()
    var rotation: Rotation
        get() = runCatching { Rotation.valueOf(p.getString("rotation", Rotation.Auto.name)!!) }.getOrDefault(Rotation.Auto)
        set(v) = p.edit().putString("rotation", v.name).apply()
    var speed: Float get() = p.getFloat("speed", 1f); set(v) = p.edit().putFloat("speed", v).apply()
    /** Remembered language choices (ISO 639-2, e.g. "tam") – applied automatically to the next title. */
    var audioLang: String? get() = p.getString("audio_lang", null); set(v) = p.edit().putString("audio_lang", v).apply()
    var subLang: String? get() = p.getString("sub_lang", null); set(v) = p.edit().putString("sub_lang", v).apply()
    /** Streaming quality the user picked last – Original (direct play) unless they chose a lower bitrate. */
    var quality: Quality
        get() = runCatching { Quality.valueOf(p.getString("quality", Quality.Original.name)!!) }.getOrDefault(Quality.Original)
        set(v) = p.edit().putString("quality", v.name).apply()
    var subsOn: Boolean get() = p.getBoolean("subs_on", false); set(v) = p.edit().putBoolean("subs_on", v).apply()
}

/** Normalises "ta", "tam", "ta-IN" → "tam" so choices match across Jellyfin and ExoPlayer. */
fun langKey(l: String?): String? = l?.takeIf { it.isNotBlank() && it != "und" }?.let {
    runCatching { java.util.Locale.forLanguageTag(it).isO3Language }.getOrNull()?.ifBlank { null } ?: it.lowercase()
}
