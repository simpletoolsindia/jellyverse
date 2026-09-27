package com.sridhar.harbor.data.ai

data class ParsedTitle(val title: String, val year: Int?, val type: String /* movie | series */, val season: Int? = null, val episode: Int? = null)

/**
 * Rule-based release-name parser: handles scene/torrent names such as
 * "www.1TamilMV.rocks - The Awakening (2026) HQ HDRip - 1080p - x264 - [Tam + Tel] - 2.4GB.mkv".
 */
object TitleCleaner {
    private val junkTokens = listOf(
        "2160p", "1080p", "720p", "480p", "4k", "uhd", "hdr10", "hdr", "dv", "hevc", "x265", "x264", "h264", "h265", "10bit", "8bit",
        "hdrip", "webrip", "web-dl", "webdl", "web", "bluray", "blu-ray", "brrip", "bdrip", "dvdrip", "hdtv", "hq", "true", "untouched",
        "predvd", "hdts", "hdcam", "cam", "ts", "tc", "proper", "repack", "extended", "unrated", "remastered", "imax", "esub", "esubs", "subs",
        "aac", "ac3", "dd5", "ddp5", "eac3", "atmos", "dts", "5.1", "7.1", "2.0", "org", "multi", "dual", "audio", "yts", "rarbg", "psa", "galaxyrg",
        "tamil", "telugu", "hindi", "malayalam", "kannada", "english", "tam", "tel", "hin", "mal", "kan", "eng", "korean", "japanese",
    )
    private const val TLD = "rocks|meme|com|net|org|in|to|mx|se|ws|cc|me|app|gs|lol|zip|foo|fyi|bz|nz|guru|boo|co|io|xyz|info|pw|lat|vip|fun|site|online|club|live|cam|pics|tv|pro|world|link|click|top|ms|nl|ch|re|li|ac|la|ag|am|tel|biz|uk|ph|pk|pe|ist|hair|skin|diy|beauty|makeup|autos|homes|ink|dad|day|mov|net\\.in|co\\.in"
    // Site tags at the start: "www.1TamilMV.rocks - ", "www.1tamilmv.com_", "[www.site.net]", "TamilBlasters.co - ".
    // "_" is treated as a separator (it is a regex word char, so \b can't be used).
    private val sitePrefix = Regex("""^(\s*[\[(]?\s*(www\.)?[a-z0-9-]+(\.+[a-z0-9-]+)*\.+($TLD)(?![a-z0-9])\s*[\])]?[\s\-–_:.|]*)+""", RegexOption.IGNORE_CASE)
    private val siteSuffix = Regex("""[\s\-–_.]+(www\.)?[a-z0-9-]+\.($TLD)\s*$""", RegexOption.IGNORE_CASE)
    private val uploaderSuffix = Regex("""[\s\-–_]+(TamilBlasters|TamilRockers|1TamilMV|Tamilyogi|isaimini|moviesda|kuttymovies|YTS|RARBG|PSA|Pahe|MkvCinemas|Vegamovies|HDHub4u)\b.*$""", RegexOption.IGNORE_CASE)
    private val youtubeId = Regex("""[-_\s]([A-Za-z0-9_-]{11})$""")
    private val sxe = Regex("""\b[Ss](\d{1,2})[ ._-]?[Ee](\d{1,3})\b""")
    private val seasonOnly = Regex("""\b(?:[Ss]eason[ ._-]?(\d{1,2})|[Ss](\d{1,2}))\b""")
    private val yearRx = Regex("""(?<![0-9])(19[2-9]\d|20[0-4]\d)(?![0-9])""")
    private val bracket = Regex("""\[[^\]]*\]|\{[^}]*\}""")
    private val sizeRx = Regex("""\b\d+(\.\d+)?\s?(gb|mb)\b""", RegexOption.IGNORE_CASE)
    private val ext = Regex("""\.(mkv|mp4|avi|webm|m4v|mov|ts|wmv)$""", RegexOption.IGNORE_CASE)

    fun looksMessy(name: String): Boolean =
        sitePrefix.containsMatchIn(name) || bracket.containsMatchIn(name) || youtubeId.containsMatchIn(name.trim()) ||
            junkTokens.count { Regex("""(?i)(?<![a-z])${Regex.escape(it)}(?![a-z])""").containsMatchIn(name) } >= 2 ||
            name.count { it == '.' } >= 3 || name.contains("  ") || (!name.contains(' ') && Regex("""[a-z][A-Z][a-z]""").containsMatchIn(name))

    fun parse(raw: String): ParsedTitle {
        var s = raw.substringAfterLast('/').replace(ext, "")
        s = s.replace(sitePrefix, "").replace(siteSuffix, "").replace(uploaderSuffix, "")
        // Underscore-only names ("aranmanai_4_2026") → spaces before any other parsing.
        if (!s.contains(' ') && s.count { it == '_' } >= 1) s = s.replace('_', ' ')
        s = s.replace(bracket, " ").replace(sizeRx, " ")
        val sx = sxe.find(s)
        var season = sx?.groupValues?.get(1)?.toIntOrNull()
        val episode = sx?.groupValues?.get(2)?.toIntOrNull()
        if (season == null) seasonOnly.find(s)?.let { m -> season = (m.groupValues[1].ifBlank { m.groupValues[2] }).toIntOrNull() }
        // Title is everything before the first year / SxxEyy / quality token.
        val year = yearRx.findAll(s).lastOrNull { it.range.first > 0 }?.value?.toIntOrNull() ?: yearRx.find(s)?.value?.toIntOrNull()
        val cut = listOfNotNull(
            yearRx.find(s)?.takeIf { it.range.first > 1 }?.range?.first,
            sx?.range?.first,
            seasonOnly.find(s)?.range?.first?.takeIf { it > 1 },
            junkTokens.mapNotNull { t -> Regex("""(?i)(?<![a-z0-9])${Regex.escape(t)}(?![a-z0-9])""").find(s)?.range?.first }.filter { it > 1 }.minOrNull(),
        ).minOrNull() ?: s.length
        var title = s.substring(0, cut)
        title = title.replace(youtubeId, "").replace(Regex("""[._]+"""), " ").replace(Regex("""[\s\-–(]+$"""), "").replace(Regex("""\s{2,}"""), " ").trim()
        // "AlienEarth" → "Alien Earth"
        title = title.replace(Regex("""(?<=[a-z])(?=[A-Z][a-z])"""), " ")
        if (title == title.lowercase()) title = title.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        return ParsedTitle(title.ifBlank { raw }, year, if (season != null || episode != null) "series" else "movie", season, episode)
    }

    /** 0..1 similarity between two titles (token Jaccard + prefix bonus). */
    fun similarity(a: String, b: String): Float {
        fun norm(x: String) = java.text.Normalizer.normalize(x.replace('¢', 'c').replace('$', 's').replace('@', 'a'), java.text.Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(' ').filter { it.isNotBlank() && it !in setOf("the", "a", "an") }.toSet()
        val x = norm(a); val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return 0f
        val j = x.intersect(y).size.toFloat() / x.union(y).size
        val bonus = if (a.lowercase().startsWith(b.lowercase().take(5)) || b.lowercase().startsWith(a.lowercase().take(5))) 0.15f else 0f
        return (j + bonus).coerceAtMost(1f)
    }
}
