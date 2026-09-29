package com.sridhar.harbor.data.reco

import android.content.Context
import com.sridhar.harbor.data.HarborJson
import com.sridhar.harbor.data.ai.LocalLlm
import com.sridhar.harbor.data.ai.ModelState
import com.sridhar.harbor.data.jellyfin.JellyfinRepository
import com.sridhar.harbor.data.parental.ParentalControls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Personal recommendations – only with the user's consent (asked once on Home, switchable in Settings).
 *
 * 1. Embeddings: [Recommender] builds item vectors + a taste profile from the Jellyfin watch history (on device).
 * 2. On-device AI (optional): the local LLM reads a summary of the viewing pattern (favourite genres, recent
 *    titles) and picks + explains the best few from the embedding shortlist. Nothing leaves the phone.
 */
class RecoRepository(context: Context, private val jellyfin: JellyfinRepository, private val parental: ParentalControls, private val llm: () -> LocalLlm) {
    private val prefs = context.getSharedPreferences("harbor_reco", Context.MODE_PRIVATE)

    /** null = not asked yet, true / false = the user's answer. */
    private val _consent = MutableStateFlow(if (prefs.contains("consent")) prefs.getBoolean("consent", false) else null)
    val consent: StateFlow<Boolean?> = _consent.asStateFlow()
    private val _useAi = MutableStateFlow(prefs.getBoolean("use_ai", true))
    val useAi: StateFlow<Boolean> = _useAi.asStateFlow()

    private val _recs = MutableStateFlow(Recommendations.EMPTY)
    val recs: StateFlow<Recommendations> = _recs.asStateFlow()
    /** Picks chosen and explained by the on-device LLM. */
    private val _aiPicks = MutableStateFlow<List<Pick>>(emptyList())
    val aiPicks: StateFlow<List<Pick>> = _aiPicks.asStateFlow()
    val aiThinking = MutableStateFlow(false)

    private var computedAt = 0L
    private var aiAt = 0L
    private val lock = Mutex()

    fun setConsent(on: Boolean) {
        prefs.edit().putBoolean("consent", on).apply(); _consent.value = on
        if (!on) { _recs.value = Recommendations.EMPTY; _aiPicks.value = emptyList(); computedAt = 0; aiAt = 0 }
    }

    fun setUseAi(on: Boolean) {
        prefs.edit().putBoolean("use_ai", on).apply(); _useAi.value = on
        if (!on) { _aiPicks.value = emptyList(); aiAt = 0 }
    }

    suspend fun refresh(force: Boolean = false) = lock.withLock {
        if (_consent.value != true) return@withLock
        if (force || System.currentTimeMillis() - computedAt > 6 * 3_600_000L || _recs.value === Recommendations.EMPTY) {
            val corpus = runCatching { jellyfin.recoCorpus() }.getOrNull() ?: return@withLock
            val keep: (com.sridhar.harbor.data.jellyfin.BaseItem) -> Boolean = { !parental.hideFromHome(it) }
            _recs.value = withContext(Dispatchers.Default) { Recommender.compute(corpus, keep) }
            profileText = withContext(Dispatchers.Default) { describeTaste(corpus) }
            computedAt = System.currentTimeMillis()
        }
    }

    private var profileText = ""

    /** Runs the LLM pass (slow on phones: call off the UI path; cached for 12 h). */
    suspend fun refreshAi() {
        val l = llm()
        if (_consent.value != true || !_useAi.value) return
        if (l.state.value !is ModelState.Ready && l.state.value !is ModelState.Loaded) return
        if (System.currentTimeMillis() - aiAt < 12 * 3_600_000L && _aiPicks.value.isNotEmpty()) return
        val shortlist = _recs.value.forYou.take(12)
        if (shortlist.size < 4 || profileText.isBlank()) return
        aiThinking.value = true
        try {
            val list = shortlist.mapIndexed { i, p ->
                "$i. ${p.item.name} (${p.item.year ?: "?"}) – ${p.item.genres.take(3).joinToString("/")}"
            }.joinToString("\n")
            val prompt = "<|im_start|>system\nYou recommend films. Reply with JSON only: " +
                "[{\"n\":<number>,\"why\":\"<max 8 words, why it suits this viewer>\"}] – exactly 5 items, best first.<|im_end|>\n" +
                "<|im_start|>user\nViewer: $profileText\n\nCandidates:\n$list<|im_end|>\n<|im_start|>assistant\n"
            val out = l.complete(prompt, temperature = 0.2f)
            val json = Regex("\\[.*]", RegexOption.DOT_MATCHES_ALL).find(out)?.value ?: return
            val picks = runCatching {
                HarborJson.parseToJsonElement(json).jsonArray.mapNotNull { e ->
                    val o = e.jsonObject
                    val n = o["n"]?.jsonPrimitive?.int ?: return@mapNotNull null
                    shortlist.getOrNull(n)?.let { Pick(it.item, it.score, o["why"]?.jsonPrimitive?.content?.take(60).orEmpty().ifBlank { it.reason }) }
                }.distinctBy { it.item.id }
            }.getOrDefault(emptyList())
            if (picks.size >= 3) { _aiPicks.value = picks; aiAt = System.currentTimeMillis() }
        } finally { aiThinking.value = false }
    }

    /** "Loves Action, Thriller, Drama; recently watched Leo, Master, Vikram; prefers 2020s Tamil films." */
    private fun describeTaste(all: List<com.sridhar.harbor.data.jellyfin.BaseItem>): String {
        val seen = all.filter { it.userData?.played == true || (it.userData?.playCount ?: 0) > 0 || it.userData?.lastPlayedDate != null }
        if (seen.isEmpty()) return ""
        val genres = seen.flatMap { it.genres }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(4).joinToString { it.key }
        val recent = seen.sortedByDescending { it.userData?.lastPlayedDate.orEmpty() }.take(6).joinToString { it.name }
        val favs = seen.filter { it.userData?.isFavorite == true }.take(4).joinToString { it.name }
        val decades = seen.mapNotNull { it.year?.let { y -> y / 10 * 10 } }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return buildString {
            append("favourite genres $genres; recently watched $recent")
            if (favs.isNotBlank()) append("; favourites $favs")
            decades?.let { append("; mostly ${it}s films") }
        }
    }
}
