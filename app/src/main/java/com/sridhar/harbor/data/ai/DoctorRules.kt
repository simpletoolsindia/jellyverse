package com.sridhar.harbor.data.ai

import com.sridhar.harbor.data.jellyfin.LibraryItem
import com.sridhar.harbor.data.jellyfin.RemoteSearchResult
import kotlin.math.abs

/** Pure Library Doctor heuristics: what is wrong with an item, which name to trust, and how good a metadata candidate is. */
object DoctorRules {

    fun issueKinds(item: LibraryItem, parsed: ParsedTitle): Set<IssueKind> = buildSet {
        if (!item.identified) add(IssueKind.Unidentified)
        if (item.imageTags["Primary"] == null) add(IssueKind.NoPoster)
        // Matched, but to something that looks nothing like the file (e.g. "Cobra (2022).mkv" → "1972: Munich's Black September").
        val sim = TitleCleaner.similarity(parsed.title, item.name)
        val yearOff = parsed.year != null && item.year != null && abs(parsed.year - item.year) > 1
        // Title AND year must disagree – alternate/foreign titles with the right year are left alone.
        if (item.identified && item.path != null && parsed.title.length >= 3 && sim < 0.2f && yearOff) add(IssueKind.WrongMatch)
        if (item.type == "Movie" && parsed.type == "series") add(IssueKind.Misfiled)
    }

    /** The file name usually carries the real title; the folder is a fallback for generic file names. */
    fun sourceName(item: LibraryItem): String {
        val path = item.path ?: return item.name
        val file = path.substringAfterLast('/')
        val folder = path.substringBeforeLast('/', "").substringAfterLast('/')
        val fp = TitleCleaner.parse(file); val dp = TitleCleaner.parse(folder)
        return when {
            folder.isBlank() -> file
            fp.title.length < 2 || fp.title.lowercase() in setOf("movie", "video", "sample", "main") -> folder
            fp.year == null && dp.year != null && TitleCleaner.similarity(fp.title, dp.title) > 0.5f -> folder
            else -> file
        }
    }

    /** 0..1 confidence that [r] is the right metadata for [target]: title similarity, year agreement, poster, kind. */
    fun score(target: ParsedTitle, r: RemoteSearchResult, kind: String, itemType: String): Float {
        val sim = TitleCleaner.similarity(target.title, r.name)
        val yr = when {
            target.year == null || r.year == null -> 0.1f
            target.year == r.year -> 0.3f
            abs(target.year - r.year) == 1 -> 0.15f
            else -> -0.2f
        }
        val poster = if (r.imageUrl != null) 0.05f else 0f
        val kindPenalty = if ((kind == "Series") != (itemType == "Series")) -0.05f else 0f
        return (sim * 0.7f + yr + poster + kindPenalty).coerceIn(0f, 1f)
    }
}
