package com.sridhar.harbor.ui.components

import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import java.util.Locale
import kotlin.math.abs

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
}

fun formatSpeed(bps: Long): String = if (bps <= 0) "0 KB/s" else formatBytes(bps) + "/s"

fun formatEta(seconds: Long): String = when {
    seconds <= 0 || seconds >= 8_640_000 -> "∞"
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    seconds < 86400 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    else -> "${seconds / 86400}d ${(seconds % 86400) / 3600}h"
}

fun formatClock(ms: Long): String {
    val total = abs(ms) / 1000
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    val body = if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    return if (ms < 0) "-$body" else body
}

fun formatRuntime(minutes: Int?): String? = minutes?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" }

fun relativeTime(iso: String?): String {
    if (iso == null) return ""
    val then = runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return ""
    val mins = (System.currentTimeMillis() - then) / 60000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> L10n.s(R.string.s_1_sm_ago, mins)
        mins < 1440 -> L10n.s(R.string.s_1_sh_ago, mins / 60)
        mins < 43200 -> L10n.s(R.string.s_1_sd_ago, mins / 1440)
        else -> L10n.s(R.string.s_1_smo_ago, mins / 43200)
    }
}
