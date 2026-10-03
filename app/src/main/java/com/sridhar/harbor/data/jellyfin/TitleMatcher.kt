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

    // ------------------------------------------------------------------------------------------------------------
    // Search matching (forgiving, word-based) – used by the Search screen. The strict [score] above stays for
    // "play X", where a wrong guess is worse than no guess.
    //
    // How it works (the approach typo-tolerant engines such as Algolia / Meilisearch use):
    //  • words, not whole strings: "pushpa rule" finds "Pushpa 2: The Rule"
    //  • the last word is a prefix while typing: "ponni" finds "Ponniyin Selvan"
    //  • typos per word scale with length (0 for ≤3 letters, 1 for ≤6, else 2), Damerau–Levenshtein so a swapped
    //    pair ("jialer") is one typo; Jaro–Winkler as a softer fallback for short names
    //  • Indian-English transliteration folding: aa→a, ee→i, oo→u, zh→l, th→t, dh→d, sh→s, w→v, doubled letters…
    //  • spacing-insensitive: "spiderman" = "Spider-Man", "kgf2" = "K.G.F: Chapter 2"
    //  • a year in the query ("leo 2023") boosts that year
    // ------------------------------------------------------------------------------------------------------------

    private val stop = setOf("the", "a", "an", "of", "and", "movie", "film", "series", "show", "tv", "season", "full", "hd")
    private val roman = mapOf("ii" to "2", "iii" to "3", "iv" to "4", "vi" to "6", "vii" to "7")

    /** Phonetic-ish key of one word, folding common Indian-English spelling variants. */
    internal fun key(word: String): String {
        roman[word]?.let { return it }
        var t = word
        for ((a, b) in listOf("zh" to "l", "aa" to "a", "ee" to "i", "ii" to "i", "oo" to "u", "ou" to "u", "th" to "t", "dh" to "d", "bh" to "b",
            "kh" to "k", "gh" to "g", "ph" to "f", "sh" to "s", "ck" to "k", "q" to "k", "w" to "v", "x" to "ks")) t = t.replace(a, b)
        if (t.length > 1) t = t[0] + t.substring(1).replace('y', 'i')
        return t.replace(Regex("(.)\\1+"), "$1")
    }

    /** Words of a title or query: accents stripped, letters and digits split ("kgf2" → kgf, 2), stop words dropped. */
    internal fun words(s: String): List<String> {
        val plain = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase().replace("&", " and ").replace(Regex("(?<=[a-z])(?=[0-9])|(?<=[0-9])(?=[a-z])"), " ")
        val split = plain.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        // Dotted acronyms come apart into single letters ("K.G.F" → k g f): join consecutive single letters back ("kgf").
        val raw = mutableListOf<String>()
        var joining = false
        for (w in split) {
            val single = w.length == 1 && w[0].isLetter()
            if (single && joining) raw[raw.lastIndex] = raw.last() + w else raw += w
            joining = single
        }
        val kept = raw.filter { it !in stop }.ifEmpty { raw }
        return kept.map(::key).filter { it.isNotEmpty() }
    }

    /** Optimal-string-alignment (Damerau–Levenshtein) distance: insert, delete, substitute, swap neighbours. */
    internal fun osa(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }

    /** Jaro–Winkler similarity (0..1), rewarding a shared beginning – good for short names. */
    internal fun jaroWinkler(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.isEmpty() || b.isEmpty()) return 0f
        val window = (maxOf(a.length, b.length) / 2 - 1).coerceAtLeast(0)
        val am = BooleanArray(a.length); val bm = BooleanArray(b.length)
        var m = 0
        for (i in a.indices) for (j in maxOf(0, i - window)..minOf(b.length - 1, i + window)) {
            if (!bm[j] && a[i] == b[j]) { am[i] = true; bm[j] = true; m++; break }
        }
        if (m == 0) return 0f
        var t = 0; var k = 0
        for (i in a.indices) if (am[i]) { while (!bm[k]) k++; if (a[i] != b[k]) t++; k++ }
        val jaro = (m.toFloat() / a.length + m.toFloat() / b.length + (m - t / 2f) / m) / 3f
        var p = 0; while (p < minOf(4, a.length, b.length) && a[p] == b[p]) p++
        return jaro + p * 0.1f * (1 - jaro)
    }

    private fun typos(len: Int) = when { len <= 3 -> 0; len <= 6 -> 1; else -> 2 }

    /** How well one query word matches one title word (0..1). [last] = the word still being typed. */
    internal fun wordScore(q: String, t: String, last: Boolean): Float {
        if (q == t) return 1f
        val numeric = q.all(Char::isDigit) || t.all(Char::isDigit)
        if (numeric) return 0f
        if (q.length >= 2 && t.startsWith(q)) return if (last) 0.95f else 0.88f
        if (q.length >= 4 && q.startsWith(t) && t.length >= 3) return 0.8f
        val allowed = typos(q.length)
        if (allowed > 0) {
            val d = osa(q, t)
            if (d <= allowed) return 0.97f - 0.12f * d - if (q[0] != t[0]) 0.06f else 0f
            // A misspelt word that is still being typed: compare with the start of the title word.
            if (last && t.length > q.length) {
                val dp = osa(q, t.take(q.length))
                if (dp <= allowed) return 0.86f - 0.1f * dp
            }
        }
        // Soft fallback only between words of similar length (a long nonsense word must not "match" a 2-letter one).
        if (q.length < 4 || t.length < 3 || kotlin.math.abs(q.length - t.length) > 2) return 0f
        val jw = jaroWinkler(q, t)
        return if (jw >= 0.86f) jw * 0.8f else 0f
    }

    /**
     * Search relevance of [title] for what the user typed (0..1). ≥ 0.55 is worth showing; ≥ 0.85 is a confident hit.
     * [year] = the item's production year (a year typed in the query must agree).
     */
    fun match(query: String, title: String, year: Int? = null): Float {
        val qAll = words(query)
        val qYear = qAll.firstOrNull { it.length == 4 && it.all(Char::isDigit) && (it.startsWith("19") || it.startsWith("20")) }?.toInt()
        val q = qAll.filter { it != qYear?.toString() }.ifEmpty { return if (qYear != null && qYear == year) 0.6f else 0f }
        val t = words(title)
        if (t.isEmpty()) return 0f
        // Word by word: each query word takes its best title word.
        val used = mutableListOf<Int>()
        var sum = 0f
        q.forEachIndexed { i, w ->
            var best = 0f; var at = -1
            t.forEachIndexed { j, tw -> val sc = wordScore(w, tw, last = i == q.lastIndex); if (sc > best) { best = sc; at = j } }
            sum += best; if (at >= 0 && best > 0f) used += at
        }
        val coverage = sum / q.size
        val titleCovered = used.distinct().size.toFloat() / t.size
        val inOrder = used.size > 1 && used.zipWithNext().all { (a, b) -> b > a }
        var s = coverage * (0.88f + 0.12f * titleCovered) + if (inOrder) 0.03f else 0f
        // Spacing-insensitive comparison of the whole thing ("spiderman" vs "Spider-Man").
        val qc = q.joinToString(""); val tc = t.joinToString("")
        val compact = when {
            qc == tc -> 1f
            qc.length >= 3 && tc.startsWith(qc) -> 0.78f + 0.2f * qc.length / tc.length
            else -> (1f - osa(qc, tc).toFloat() / maxOf(qc.length, tc.length)).let { if (it >= 0.75f) it * 0.95f else 0f }
        }
        s = maxOf(s, compact)
        if (qYear != null && year != null) s += if (qYear == year) 0.08f else -0.12f
        return s.coerceIn(0f, 1f)
    }
}
