package com.sridhar.harbor.data.reco

import com.sridhar.harbor.data.HarborJson
import com.sridhar.harbor.data.jellyfin.ItemsResult
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Prints recommendations for a real library dump (skipped unless -Dreco.corpus points at one). */
class RecommenderLiveTest {
    @Test fun printForRealLibrary() {
        val path = System.getProperty("reco.corpus") ?: System.getenv("RECO_CORPUS")
        assumeTrue(path != null && File(path).exists())
        val items = HarborJson.decodeFromString(ItemsResult.serializer(), File(path!!).readText()).items
        val t0 = System.currentTimeMillis()
        val r = Recommender.compute(items)
        println("RECO ${items.size} titles in ${System.currentTimeMillis() - t0} ms")
        r.forYou.take(12).forEach { println("RECO for-you: ${it.item.name} (${it.item.year}) – ${it.reason}  [%.2f]".format(it.score)) }
        r.because.forEach { row -> println("RECO because ${row.seed.name}: " + row.picks.take(6).joinToString { "${it.item.name} (${it.reason})" }) }
    }
}
