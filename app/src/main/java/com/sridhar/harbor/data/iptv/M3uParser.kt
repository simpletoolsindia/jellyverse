package com.sridhar.harbor.data.iptv

import java.text.SimpleDateFormat
import java.util.Locale

/** Extended-M3U playlist parser (EXTINF attributes, EXTVLCOPT headers, EXTGRP, url-tvg header). Pure – no I/O. */
object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String, p: Playlist): Pair<List<Channel>, String?> {
        val out = ArrayList<Channel>(1024)
        var headerEpg: String? = null
        var info: String? = null; var ua: String? = null; var ref: String? = null; var group: String? = null
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTM3U") -> headerEpg = attr.findAll(line).firstOrNull { it.groupValues[1] in setOf("url-tvg", "x-tvg-url") }?.groupValues?.get(2)?.split(',')?.firstOrNull()
                line.startsWith("#EXTINF") -> info = line
                line.startsWith("#EXTVLCOPT:http-user-agent=") -> ua = line.substringAfter('=')
                line.startsWith("#EXTVLCOPT:http-referrer=") -> ref = line.substringAfter('=')
                line.startsWith("#EXTGRP:") -> group = line.substringAfter(':').trim()
                line.isNotEmpty() && !line.startsWith("#") && info != null -> {
                    val inf = info!!
                    val attrs = attr.findAll(inf).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    val name = inf.substringAfterLast("\",", "").ifBlank { inf.substringAfterLast(',') }.trim().ifBlank { attrs["tvg-name"] ?: "Channel" }
                    out += Channel(
                        id = "${p.id}:${out.size}:${name.hashCode()}", name = name, url = line,
                        logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                        group = (attrs["group-title"] ?: group)?.takeIf { it.isNotBlank() } ?: "Other",
                        tvgId = (attrs["tvg-id"]?.takeIf { it.isNotBlank() } ?: attrs["tvg-name"])?.lowercase(),
                        userAgent = ua ?: attrs["user-agent"] ?: p.userAgent.takeIf { it.isNotBlank() }, referrer = ref,
                    )
                    info = null; ua = null; ref = null; group = null
                }
            }
        }
        return out to headerEpg
    }
}

/** XMLTV timestamps: "20260927183000 +0530" or bare "20260927183000" (treated as UTC). Returns epoch ms, 0 on failure. */
object XmltvTime {
    private val format = ThreadLocal.withInitial { SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US) }

    fun parse(s: String?): Long = s?.trim()?.let {
        runCatching { format.get()!!.parse(if (it.length > 14 && !it.contains(' ')) it.substring(0, 14) + " +0000" else if (it.length == 14) "$it +0000" else it)!!.time }.getOrNull()
    } ?: 0L
}
