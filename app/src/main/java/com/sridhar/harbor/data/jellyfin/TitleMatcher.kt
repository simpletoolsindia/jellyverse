package com.sridhar.harbor.data.jellyfin

/**
 * Pure, typo-tolerant title matching used by fuzzy search and the assistant's "play X".
 * Normalises transliteration noise ("aaranmanai" ≈ "Aranmanai", "zh" ≈ "l") then scores 0..1.
 */
object TitleMatcher {
    private val fillerWords = Regex("(?i)\\b(movie|film|series|show|please|the)\\b")

    fun normalizeQuery(query: String): String = normalize(query.replace(fillerWords, " "))

    /** Leading articles are dropped from titles too, so "the 6th day" and "The 6th Day" normalise identically. */
    fun normalize(s: String): String = s.lowercase().replace(Regex("\\(\\d{4}\\)"), "").replace(Regex("^\\s*(the|a|an)\\s+"), "").replace(Regex("[^a-z0-9]"), "")
        .replace(Regex("(.)\\1+"), "$1").replace("th", "t").replace("zh", "l")

    /** 1 = identical, prefix matches score by length ratio, otherwise 1 - normalised Levenshtein distance. */
    fun score(q: String, n: String): Float {
        if (n.isEmpty()) return 0f
        if (q == n) return 1f
        if (n.length >= 4 && (q.startsWith(n) || n.startsWith(q))) return 0.9f * minOf(q.length, n.length) / maxOf(q.length, n.length) + 0.1f
        val d = IntArray(n.length + 1) { it }
        for (i in 1..q.length) { var prev = d[0]; d[0] = i
            for (j in 1..n.length) { val t = d[j]; d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (q[i - 1] == n[j - 1]) 0 else 1); prev = t } }
        return 1f - d[n.length].toFloat() / maxOf(q.length, n.length)
    }
}
