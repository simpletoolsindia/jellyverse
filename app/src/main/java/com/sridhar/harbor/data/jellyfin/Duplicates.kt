package com.sridhar.harbor.data.jellyfin

/** One file of a title (a separate item or one of its versions). */
data class DupCopy(
    val id: String, val itemName: String, val path: String?, val size: Long, val width: Int?, val height: Int?,
    val videoCodec: String?, val container: String?, val bitrate: Long?,
) {
    val quality: String get() = when {
        (height ?: 0) >= 2000 || (width ?: 0) >= 3600 -> "4K"
        (height ?: 0) >= 1000 || (width ?: 0) >= 1800 -> "1080p"
        (height ?: 0) >= 700 || (width ?: 0) >= 1200 -> "720p"
        height != null -> "${height}p"
        else -> "SD"
    }
    val tier: Int get() = when (quality) { "4K" -> 4; "1080p" -> 3; "720p" -> 2; "SD" -> 0; else -> 1 }
    val fileName: String get() = path?.substringAfterLast('/') ?: itemName
}

data class DupGroup(val key: String, val title: String, val year: Int?, val type: String, val posterItemId: String, val copies: List<DupCopy>) {
    /** The copy worth keeping: highest resolution, then bitrate, then size. */
    /** Resolution tier first (a 1920×800 scope film is still 1080p), then bitrate, then size. */
    val best: DupCopy get() = copies.maxWith(compareBy<DupCopy>({ it.tier }, { it.bitrate ?: 0 }, { it.size }))
    val wastedBytes: Long get() = copies.sumOf { it.size } - best.size
    /** Copies that are really the same file listed twice – deleting one would delete both. */
    val sharedPath: Set<String> get() = copies.groupBy { it.path }.filter { it.key != null && it.value.size > 1 }.values.flatten().map { it.id }.toSet()
    /** Every copy except the best one (and never one whose file is shared). */
    val extras: List<DupCopy> get() = copies.filter { it.id != best.id && it.id !in sharedPath }
}

/** Finds duplicate films / shows: same TMDB / IMDb / TVDB id (else same title + year), or one title with several versions. */
object Duplicates {
    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    fun key(i: BaseItem): String {
        val p = i.providerIds.mapKeys { it.key.lowercase() }
        return p["tmdb"]?.takeIf { it.isNotBlank() }?.let { "tmdb:$it" }
            ?: p["imdb"]?.takeIf { it.isNotBlank() }?.let { "imdb:$it" }
            ?: p["tvdb"]?.takeIf { it.isNotBlank() }?.let { "tvdb:$it" }
            ?: "t:${norm(i.name)}:${i.year ?: 0}"
    }

    fun copiesOf(i: BaseItem): List<DupCopy> =
        if (i.mediaSources.isEmpty()) listOf(DupCopy(i.id, i.name, i.path, 0, null, null, null, null, null))
        else i.mediaSources.map { ms ->
            val v = ms.streams.firstOrNull { it.type == "Video" }
            DupCopy(ms.id, i.name, ms.path ?: i.path, ms.size ?: 0, v?.width, v?.height, v?.codec, ms.container, ms.bitrate)
        }

    fun find(items: List<BaseItem>): List<DupGroup> =
        items.groupBy { "${it.type}|${key(it)}" }.mapNotNull { (k, group) ->
            // The same file can be listed by two libraries (e.g. "Movies" and a mixed folder) – that's not a duplicate.
            val copies = group.flatMap(::copiesOf).distinctBy { it.id }.distinctBy { it.path ?: it.id }
            if (copies.size < 2) null
            else group.first().let { DupGroup(k, it.name, it.year, it.type, it.id, copies) }
        }.sortedByDescending { it.wastedBytes }
}
