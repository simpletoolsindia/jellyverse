package com.sridhar.harbor.data.reco

import com.sridhar.harbor.data.jellyfin.JellyfinRepository
import com.sridhar.harbor.data.parental.ParentalControls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps "Recommended for you" fresh: pulls the library + watch history from Jellyfin at most every 6 hours
 * (or when asked), builds the embeddings and taste profile on a background thread, and publishes the result.
 */
class RecoRepository(private val jellyfin: JellyfinRepository, private val parental: ParentalControls) {
    private val _recs = MutableStateFlow(Recommendations.EMPTY)
    val recs: StateFlow<Recommendations> = _recs.asStateFlow()
    private var computedAt = 0L
    private val lock = Mutex()

    suspend fun refresh(force: Boolean = false) = lock.withLock {
        if (!force && System.currentTimeMillis() - computedAt < 6 * 3_600_000L && _recs.value !== Recommendations.EMPTY) return@withLock
        val corpus = runCatching { jellyfin.recoCorpus() }.getOrNull() ?: return@withLock
        _recs.value = withContext(Dispatchers.Default) { Recommender.compute(corpus, keep = { !parental.hideFromHome(it) }) }
        computedAt = System.currentTimeMillis()
    }
}
