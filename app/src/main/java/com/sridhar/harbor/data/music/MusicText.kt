package com.sridhar.harbor.data.music

import java.security.MessageDigest

/**
 * Cleans the tag noise typical of ripped / downloaded music libraries
 * ("A.R.Rahman - MassTamilan.com", "Song with Tamil Lyrics_V-XJpelnIDY") and merges duplicate artists. Pure.
 */
object MusicText {
    private val siteTag = Regex("""\s*[-–|:]?\s*[\[(]?\s*(www\.)?[A-Za-z0-9-]*(masstamilan|isaimini|tnhits|starmusiq|kuttyweb|tamilwire|pagalworld|mr-?jatt|djpunjab|songspk)[A-Za-z0-9.-]*\s*[\])]?""", RegexOption.IGNORE_CASE)
    private val domainTag = Regex("""\s*[-–|]\s*[A-Za-z0-9-]+\.(com|net|org|in|dev|fm|so|co|io|me|cc|info|xyz|site|live)\b.*$""", RegexOption.IGNORE_CASE)
    private val youtubeId = Regex("""[_\s-]+[A-Za-z0-9_-]{11}$""")
    private val noise = Regex("""\s*(\(|\[)?\s*\b(with\s+(tamil\s+)?lyrics|(tamil\s+)?lyric(s|al)?(\s+video)?|official\s+(video|audio|lyric video)|full\s+song|video\s+song|audio\s+song|video|320\s?kbps|128\s?kbps|hd|4k)\b\s*(\)|])?\s*$""", RegexOption.IGNORE_CASE)
    /** YouTube-style titles separate credits with full-width or plain bars: "Song ｜ Composer ｜ Cast". */
    private val creditBar = Regex("""\s*[｜|]\s*""")
    private val bitrateFolder = Regex("""-?\s*\d{3}kbps.*$""", RegexOption.IGNORE_CASE)

    fun cleanTitle(raw: String): String {
        var s = raw.split(creditBar).first()
        s = s.replace(siteTag, "").replace(domainTag, "")
        // YouTube-ripped names end with an 11-char video id; only strip when it contains a digit/case mix (not a real word).
        youtubeId.find(s)?.let { m -> val id = m.value.trim('_', ' ', '-'); if (id.any(Char::isDigit) || id.any(Char::isUpperCase) && id.any(Char::isLowerCase) && id.count(Char::isUpperCase) > 2) s = s.removeRange(m.range) }
        repeat(2) { s = s.replace(noise, "") }
        s = s.replace('_', ' ').replace(Regex("""\s{2,}"""), " ").trim(' ', '-', '–', '|')
        return s.ifBlank { raw.trim() }
    }

    fun cleanAlbum(raw: String): String {
        if (raw.isBlank() || raw.equals("[Unknown Album]", true)) return "Singles"
        val s = cleanTitle(raw.replace(bitrateFolder, "")).replace(Regex("""^\d+\s*-\s*"""), "")
        return s.ifBlank { raw.trim() }
    }

    fun cleanArtist(raw: String?): String {
        if (raw.isNullOrBlank() || raw.equals("[Unknown Artist]", true) || domainLike(raw)) return "Unknown artist"
        return cleanTitle(raw).replace(Regex("""\s*\(feat\..*$""", RegexOption.IGNORE_CASE), "").ifBlank { raw.trim() }
    }

    /** Credits embedded in a YouTube-style title ("Song ｜ Yuvan ｜ Karthi_id") – used when the artist tag is missing. */
    fun creditsFromTitle(raw: String): String? = raw.split(creditBar).drop(1)
        .map { cleanTitle(it) }.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString(", ")

    /** "A.R. Rahman", "A.R.Rahman", "a r rahman" → "arrahman". */
    fun artistKey(name: String): String = cleanArtist(name).lowercase().replace(Regex("[^a-z0-9\\p{L}]"), "")

    fun groupArtists(artists: List<Artist>): List<ArtistGroup> =
        artists.groupBy { artistKey(it.name) }.values.map { same ->
            // The most complete spelling wins ("A.R. Rahman" over "A.R.Rahman").
            val best = same.maxWith(compareBy<Artist>({ it.albumCount }, { cleanArtist(it.name).count(Char::isWhitespace) }))
            ArtistGroup(cleanArtist(best.name), same.map { it.id }, same.firstNotNullOfOrNull { it.coverArt }, same.sumOf { it.albumCount })
        }.filterNot { it.name == "Unknown artist" }.sortedBy { it.name.lowercase() }

    /** Genres that are real genres – site tags like "MassTamilan.com" dropped, case-duplicates merged. */
    fun realGenres(genres: List<Genre>): List<Genre> = genres
        .filterNot { g -> g.value.isBlank() || domainLike(g.value) || siteTag.containsMatchIn(g.value) }
        .groupBy { it.value.trim().lowercase() }.values
        .map { same -> Genre(same.maxBy { it.songCount }.value.trim(), same.sumOf { it.songCount }, same.sumOf { it.albumCount }) }
        .sortedByDescending { it.songCount }

    private val domainRx = Regex("""^[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.(com|net|org|in|dev|fm|so|co|io|me|cc|info|xyz|site|live|to|ws|pw)$""", RegexOption.IGNORE_CASE)
    private fun domainLike(s: String) = domainRx.matches(s.trim())

    fun duration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

    fun totalDuration(seconds: Int): String = when {
        seconds >= 3600 -> "${seconds / 3600} hr ${(seconds % 3600) / 60} min"
        else -> "${seconds / 60} min"
    }
}

/** Subsonic token authentication (API ≥ 1.13): t = md5(password + salt). */
object SubsonicAuth {
    fun token(password: String, salt: String): String =
        MessageDigest.getInstance("MD5").digest((password + salt).toByteArray()).joinToString("") { "%02x".format(it) }

    fun newSalt(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(12)
}

/** Synced-lyrics helper: which line is active at [positionMs]. */
object Lyrics {
    private val spam = Regex("""(https?://|www\.|\.(com|net|org|in|dev|fm|so|co|cc)\b|download|320\s?kbps|128\s?kbps|mp3|free songs?)""", RegexOption.IGNORE_CASE)

    /** Download sites stuff adverts into the lyrics tag – drop those lines. */
    fun isSpam(line: String): Boolean = spam.containsMatchIn(line)

    fun clean(lines: List<LyricLine>): List<LyricLine> = lines.filter { it.value.isNotBlank() && !isSpam(it.value) }

    fun activeIndex(lines: List<LyricLine>, positionMs: Long, offsetMs: Long = 0): Int {
        var idx = -1
        // OpenSubsonic: a positive offset shows lyrics sooner.
        for (i in lines.indices) { val t = (lines[i].start ?: return idx) - offsetMs; if (t <= positionMs) idx = i else break }
        return idx
    }
}
