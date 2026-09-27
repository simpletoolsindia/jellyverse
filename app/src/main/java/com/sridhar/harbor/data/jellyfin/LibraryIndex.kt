package com.sridhar.harbor.data.jellyfin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable data class IndexStream(@SerialName("Type") val type: String = "", @SerialName("Language") val language: String? = null)
@Serializable data class IndexUserData(@SerialName("Played") val played: Boolean = false, @SerialName("IsFavorite") val favorite: Boolean = false)

@Serializable
data class IndexItem(
    @SerialName("Id") val id: String,
    @SerialName("Type") val type: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("SortName") val sortName: String? = null,
    @SerialName("ProductionYear") val year: Int? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("DateCreated") val dateCreated: String? = null,
    @SerialName("PremiereDate") val premiere: String? = null,
    @SerialName("CommunityRating") val rating: Float? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("MediaStreams") val streams: List<IndexStream> = emptyList(),
    @SerialName("UserData") val userData: IndexUserData = IndexUserData(),
)

@Serializable data class IndexResult(@SerialName("Items") val items: List<IndexItem> = emptyList())

/** One title in a library with the audio languages it can be watched in. */
data class IndexEntry(
    val id: String, val sortName: String, val year: Int?, val genres: List<String>, val dateCreated: String?, val premiere: String?,
    val rating: Float?, val played: Boolean, val favorite: Boolean, val languages: Set<String>,
)

data class LibraryIndex(val entries: List<IndexEntry>) {
    /** Languages by how many titles have them ("tam" to 594 …); undetermined audio is left out. */
    val languages: List<Pair<String, Int>> = entries.flatMap { it.languages }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
    val genres: List<String> = entries.flatMap { it.genres }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    /** Decades present, newest first (2020, 2010, …). */
    val decades: List<Int> = entries.mapNotNull { it.year?.let { y -> y / 10 * 10 } }.distinct().sortedDescending()

    companion object {
        /** Movies: their own audio tracks. Series: the union of their episodes' audio tracks. */
        fun build(titles: List<IndexItem>, episodes: List<IndexItem> = emptyList()): LibraryIndex {
            val epLangs = episodes.groupBy { it.seriesId }.mapValues { (_, eps) -> eps.flatMap { langs(it) }.toSet() }
            return LibraryIndex(titles.map { t ->
                IndexEntry(t.id, (t.sortName ?: t.name).lowercase(), t.year, t.genres, t.dateCreated, t.premiere, t.rating,
                    t.userData.played, t.userData.favorite, if (t.type == "Series") epLangs[t.id].orEmpty() else langs(t))
            })
        }

        private fun langs(i: IndexItem) = i.streams.filter { it.type == "Audio" }.mapNotNull { key(it.language) }.toSet()

        fun key(l: String?): String? = l?.trim()?.takeIf { it.isNotEmpty() && !it.equals("und", true) && !it.equals("unknown", true) }?.let {
            runCatching { Locale.forLanguageTag(it).isO3Language }.getOrNull()?.ifBlank { null } ?: it.lowercase()
        }

        fun displayName(code: String, locale: Locale = Locale.getDefault()): String =
            Locale.forLanguageTag(code).getDisplayLanguage(locale).takeIf { it.isNotBlank() && !it.equals(code, true) }?.replaceFirstChar { it.titlecase(locale) } ?: code.uppercase()
    }
}

/** Library filters that Jellyfin can't do server-side (language), plus client-side sort for the same result. Pure. */
object LibraryQuery {
    data class Filter(val language: String? = null, val genre: String? = null, val decade: Int? = null, val unwatchedOnly: Boolean = false, val favoritesOnly: Boolean = false)

    fun apply(entries: List<IndexEntry>, f: Filter, sortKey: String, descending: Boolean): List<IndexEntry> {
        val filtered = entries.filter { e ->
            (f.language == null || f.language in e.languages) &&
                (f.genre == null || e.genres.any { it.equals(f.genre, true) }) &&
                (f.decade == null || (e.year != null && e.year / 10 * 10 == f.decade)) &&
                (!f.unwatchedOnly || !e.played) && (!f.favoritesOnly || e.favorite)
        }
        val sorted = when {
            sortKey.startsWith("SortName") -> filtered.sortedBy { it.sortName }
            sortKey.startsWith("DateCreated") -> filtered.sortedBy { it.dateCreated.orEmpty() }
            sortKey.startsWith("PremiereDate") -> filtered.sortedWith(compareBy({ it.premiere ?: "" }, { it.year ?: 0 }))
            sortKey.startsWith("CommunityRating") -> filtered.sortedBy { it.rating ?: 0f }
            sortKey == "Random" -> return filtered.shuffled()
            else -> filtered
        }
        return if (descending) sorted.reversed() else sorted
    }

    /** Jellyfin's `years` parameter for a decade: "2020,2021,…,2029". */
    fun yearsParam(decade: Int?): String? = decade?.let { d -> (d..d + 9).joinToString(",") }
}
