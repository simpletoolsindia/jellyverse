package com.sridhar.harbor.data.reco

import com.sridhar.harbor.data.jellyfin.BaseItem
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** One recommendation: the title, how well it matches (0–1) and a short human reason. */
data class Pick(val item: BaseItem, val score: Double, val reason: String)

/** "Because you watched X": the seed title and its nearest unwatched neighbours. */
data class BecauseRow(val seed: BaseItem, val picks: List<Pick>)

data class Recommendations(val forYou: List<Pick>, val because: List<BecauseRow>) {
    companion object { val EMPTY = Recommendations(emptyList(), emptyList()) }
}

/**
 * Content-based recommender that runs on the phone.
 *
 * Every title becomes a sparse **embedding**: weighted features for its genres, tags, studios, director, lead cast,
 * decade, age rating and overview keywords, scaled by TF-IDF (a feature shared by few titles says more than
 * "Drama") and L2-normalised. Your **taste profile** is the blend of what you've watched in Jellyfin – recent
 * watches, favourites and rewatches weigh more, old ones fade (90-day half-life). Films you started and gave up on
 * pull the profile *away* from what they are (negative feedback). Unwatched titles are ranked by cosine similarity
 * to the profile, lightly boosted by rating and by being newly added to the library, then diversified (MMR) so one
 * genre can't take over the row.
 */
object Recommender {
    private typealias Vec = Map<String, Double>

    private val STOP = ("a an and are as at be but by for from has have he her his in into is it its of on or she " +
        "that the their them they this to was were when where which while who whose will with after before about " +
        "against between during over under again than then once only own same so very can just his her one two new " +
        "must life man woman find finds world young old story film movie series together takes take gets get").split(' ').toSet()

    private fun raw(item: BaseItem): Map<String, Double> {
        val f = HashMap<String, Double>()
        fun add(k: String, w: Double) { f[k] = (f[k] ?: 0.0) + w }
        item.genres.forEach { add("g:" + it.lowercase(), 2.5) }
        item.tags.take(12).forEach { add("t:" + it.lowercase(), 1.5) }
        item.studios.take(3).forEach { add("s:" + it.name.lowercase(), 1.0) }
        item.people.filter { it.type == "Director" }.take(2).forEach { add("p:" + it.name.lowercase(), 2.2) }
        item.people.filter { it.type == "Actor" }.take(5).forEachIndexed { i, p -> add("p:" + p.name.lowercase(), 2.4 - i * 0.35) }   // the lead star is a strong taste signal
        item.people.filter { it.type == "Composer" }.take(1).forEach { add("p:" + it.name.lowercase(), 1.0) }
        item.year?.let { add("d:" + (it / 10 * 10), 0.4) }
        item.officialRating?.let { add("r:" + it.uppercase(), 0.4) }
        add("type:" + item.type.lowercase(), 0.25)
        item.overview.orEmpty().lowercase().split(Regex("[^\\p{L}]+"))
            .filter { it.length > 3 && it !in STOP }.distinct().take(30).forEach { add("w:$it", 0.35) }
        return f
    }

    private fun norm(v: MutableMap<String, Double>): Vec {
        val n = sqrt(v.values.sumOf { it * it }); if (n > 0) v.replaceAll { _, x -> x / n }; return v
    }

    private fun cos(a: Vec, b: Vec): Double {
        val (s, l) = if (a.size < b.size) a to b else b to a
        var d = 0.0; for ((k, x) in s) l[k]?.let { d += x * it }; return d
    }

    private fun lastPlayed(i: BaseItem): Instant? = i.userData?.lastPlayedDate?.let { runCatching { Instant.parse(it) }.getOrNull() }

    /** Something the user has actually watched (a film finished or started far, a show with episodes seen). */
    private fun watched(i: BaseItem): Boolean = i.userData?.let { u ->
        u.played || u.playCount > 0 || (u.playedPercentage ?: 0.0) > 20 || (i.type == "Series" && u.lastPlayedDate != null)
    } ?: false

    /** A film started and dropped early, weeks ago: a "not for me" signal. Series are excluded (people binge in bits). */
    private fun abandoned(i: BaseItem, now: Instant): Boolean = i.userData?.let { u ->
        val pct = u.playedPercentage ?: 0.0
        i.type == "Movie" && !u.played && u.playCount <= 1 && pct in 1.0..35.0 && !u.isFavorite &&
            (lastPlayed(i)?.let { now.epochSecond - it.epochSecond > 21 * 86_400L } ?: false)
    } ?: false

    private fun addedDaysAgo(i: BaseItem, now: Instant): Double? =
        i.dateCreated?.let { runCatching { Instant.parse(it) }.getOrNull() }?.let { (now.epochSecond - it.epochSecond) / 86_400.0 }

    fun compute(all: List<BaseItem>, keep: (BaseItem) -> Boolean = { true }, now: Instant = Instant.now()): Recommendations {
        if (all.size < 5) return Recommendations.EMPTY
        val raws = all.associate { it.id to raw(it) }
        // IDF over the whole library.
        val df = HashMap<String, Int>(); raws.values.forEach { r -> r.keys.forEach { df[it] = (df[it] ?: 0) + 1 } }
        val n = all.size.toDouble()
        val emb: Map<String, Vec> = raws.mapValues { (_, r) ->
            norm(r.mapValuesTo(HashMap()) { (k, w) -> w * ln(1 + n / (1 + (df[k] ?: 0))) })
        }

        val dropped = all.filter { abandoned(it, now) }.map { it.id }.toSet()
        val seen = all.filter { watched(it) && it.id !in dropped }
        if (seen.isEmpty()) return Recommendations.EMPTY
        // Taste profile: recency-decayed, favourites and rewatches count more.
        val profile = HashMap<String, Double>()
        for (it in seen) {
            val days = lastPlayed(it)?.let { t -> (now.epochSecond - t.epochSecond) / 86_400.0 } ?: 365.0
            var w = exp(-ln(2.0) * days.coerceAtLeast(0.0) / 90.0) + 0.15
            if (it.userData?.isFavorite == true) w += 1.5
            w *= 1 + ln(1.0 + (it.userData?.playCount ?: 0).coerceAtMost(5))
            if ((it.userData?.playedPercentage ?: 0.0) >= 85 || it.userData?.played == true) w *= 1.25   // finished it
            emb[it.id]?.forEach { (k, x) -> profile[k] = (profile[k] ?: 0.0) + w * x }
        }
        // Negative feedback (Rocchio): drift away from what was dropped – but never below zero, so one bad pick
        // can't erase a genre you otherwise love.
        val pos = HashMap(profile)
        all.filter { it.id in dropped }.forEach { d ->
            emb[d.id]?.forEach { (k, x) -> if (k in profile) profile[k] = maxOf((profile[k] ?: 0.0) - 0.35 * x, (pos[k] ?: 0.0) * 0.3) }
        }
        val prof = norm(profile)

        // Candidates need real metadata (a genre plus cast or a synopsis) – bare files would win on noise – and
        // duplicate copies of a film count once.
        val candidates = all.filter { !watched(it) && it.id !in dropped && it.userData?.isFavorite != true && keep(it) &&
                it.genres.isNotEmpty() && (it.people.isNotEmpty() || (it.overview?.length ?: 0) > 40) }
            .distinctBy { it.name.lowercase().trim() to it.year }
        // Sparse vectors score deceptively high cosines: scale by how much we actually know about the title.
        val coverage = candidates.associate { it.id to ((raws[it.id]?.size ?: 0) / 10.0).coerceIn(0.5, 1.0) }
        val scored = candidates.map { c ->
            val sim = cos(prof, emb.getValue(c.id)) * coverage.getValue(c.id)
            val quality = ((c.communityRating ?: 6.0).coerceIn(0.0, 10.0) / 10.0)
            // Fresh in the library: a gentle nudge that fades over ~3 weeks, only for things that already match.
            val fresh = addedDaysAgo(c, now)?.let { d -> exp(-d.coerceAtLeast(0.0) / 21.0) } ?: 0.0
            c to (0.85 * sim + 0.15 * quality * sim.coerceAtLeast(0.05)) * (1 + 0.18 * fresh)
        }.sortedByDescending { it.second }.take(120)

        // MMR: relevance minus similarity to what's already picked, so the row stays varied.
        val picked = ArrayList<Pair<BaseItem, Double>>()
        val pool = scored.toMutableList()
        while (picked.size < 20 && pool.isNotEmpty()) {
            val best = pool.maxBy { (c, s) -> 0.72 * s - 0.28 * (picked.maxOfOrNull { cos(emb.getValue(it.first.id), emb.getValue(c.id)) } ?: 0.0) }
            picked += best; pool.remove(best)
        }
        val forYou = picked.map { (c, s) -> Pick(c, s, reason(prof, emb.getValue(c.id))) }

        // "Because you watched": the three most recent distinct watches, nearest unwatched neighbours of each.
        val used = forYou.take(8).map { it.item.id }.toMutableSet()
        val seeds = seen.filter { keep(it) && (it.userData?.isFavorite == true || it.userData?.played == true || (it.userData?.playedPercentage ?: 0.0) > 50 || it.type == "Series") }
            .ifEmpty { seen.filter { keep(it) } }.sortedByDescending { lastPlayed(it) ?: Instant.EPOCH }.take(3)
        val because = seeds.mapNotNull { seed ->
            val sv = emb.getValue(seed.id)
            val picks = candidates.asSequence().filter { it.id !in used }
                .map { it to cos(sv, emb.getValue(it.id)) * coverage.getValue(it.id) }.filter { it.second > 0.03 }
                .sortedByDescending { it.second }.take(12)
                .map { (c, s) -> Pick(c, s, reason(sv, emb.getValue(c.id))) }.toList()
            used += picks.take(4).map { it.item.id }
            if (picks.size >= 3) BecauseRow(seed, picks) else null
        }
        return Recommendations(forYou, because)
    }

    /** The two strongest shared features, in words: "Thriller · Vijay Sethupathi". */
    private fun reason(a: Vec, b: Vec): String =
        b.keys.filter { it in a && !it.startsWith("w:") && !it.startsWith("type:") && !it.startsWith("r:") && !it.startsWith("d:") }
            .sortedByDescending { (a[it] ?: 0.0) * (b[it] ?: 0.0) }.take(2)
            .joinToString(" · ") { k ->
                val v = k.substringAfter(':')
                when { k.startsWith("d:") -> "${v}s"; else -> v.split(' ').joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } } }
            }
}
