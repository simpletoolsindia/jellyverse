package com.sridhar.harbor.data.jellyfin

import java.time.Instant
import java.time.temporal.IsoFields
import java.time.ZoneOffset
import kotlin.random.Random

/** Weekly-rotating Top 10: rating + freshness, weighted random draw seeded by the ISO week. Pure, testable. */
object Top10 {
    fun pick(pool: List<BaseItem>, now: Instant, size: Int = 10): List<BaseItem> {
        if (pool.size <= size) return pool
        val day = 86_400L
        val scored = pool.distinctBy { it.id }.map { item ->
            val rating = (item.communityRating ?: 6.0).toDouble()
            val ageDays = item.dateCreated?.let { runCatching { (now.epochSecond - Instant.parse(it).epochSecond) / day }.getOrNull() }
            val fresh = when { ageDays == null -> 0.0; ageDays < 30 -> 1.2; ageDays < 120 -> 0.6; else -> 0.0 }
            item to (rating + fresh)
        }.sortedByDescending { it.second }.take(30)
        val week = now.atZone(ZoneOffset.UTC).let { it.get(IsoFields.WEEK_BASED_YEAR) * 100 + it.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) }
        val rnd = Random(week)
        // Efraimidis–Spirakis weighted sampling: key = u^(1/w); higher score → more likely, still varied week to week.
        // The 3 strongest (incl. fresh hits) always make it; the other places rotate every week.
        val fixed = scored.take(3)
        val rotating = scored.drop(3).map { (item, w) -> (item to w) to Math.pow(rnd.nextDouble(), 1.0 / (w * w)) }
            .sortedByDescending { it.second }.take(size - fixed.size).map { it.first }
        return (fixed + rotating).sortedByDescending { it.second }.map { it.first }
    }
}
