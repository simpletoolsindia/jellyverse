package com.sridhar.harbor.data.music

import com.sridhar.harbor.data.HarborJson
import kotlinx.serialization.Serializable

/**
 * The preset FM stations are not compiled into the app: they live in the public repo (radio/stations.json) and are
 * downloaded, cached on the device and refreshed from there. This object is the pure part – parsing the file and
 * merging it into the user's own station list – so it can be unit-tested without Android.
 */
object RadioDirectory {
    /** raw.githubusercontent.com first; jsDelivr mirrors the same file where GitHub's raw host is blocked. */
    val URLS = listOf(
        "https://raw.githubusercontent.com/simpletoolsindia/jellyverse/main/radio/stations.json",
        "https://cdn.jsdelivr.net/gh/simpletoolsindia/jellyverse@main/radio/stations.json",
    )

    /**
     * SHA-256 (first 16 hex) of the links the app used to ship built in (≤ 2.23). On the first sync after an upgrade,
     * a preset whose link isn't one of these was changed by the user – it's kept as theirs. Hashes only: no links
     * in the app.
     */
    private val SHIPPED = setOf("6d4d58a6b1420e20", "05ab4d031b6a4629", "0976a74c0871fc70", "48439e50a82dd298", "05d3bcb4f2815677", "2d0123df7b5f0c56", "b5610aef2f4ca604", "05a84b0b2a4e3a44", "056628c82bca9042", "e88389b24f492110", "b5204143bf1872bf", "25d40b678a3bfecf", "76f8b220c85e7353", "bf5322fe4d6edea4", "f02b55e7db9d72d4", "4c41e4622fff4911", "251ed20c333426f3", "b14068f70bef16a7", "dd592ef969aaa41a", "3afa6d4abed2c9c8", "72b4c9237c09725e", "bfe8bc1ca023cd74", "be093fa6acde0af4", "3fb94f2d3b2bc8e8", "44a4788c4c2b136c", "ae325d2e4b3597f6", "38ca0d7bec194e5a", "059f24e600f52678", "eb1840daf74e5b61")

    fun hash(url: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        .joinToString("") { "%02x".format(it) }.take(16)

    /** Upgrade from built-in presets: ids of preset stations whose link the user changed (to keep as edited). */
    fun userEdited(current: List<RadioStation>, directory: List<Entry>): Set<String> {
        val ids = directory.map { it.id }.toSet()
        return current.filter { it.id in ids && hash(it.url) !in SHIPPED }.map { it.id }.toSet()
    }

    @Serializable
    data class Entry(val id: String, val name: String, val url: String, val group: String = "")

    @Serializable
    data class File(val version: Int = 1, val stations: List<Entry> = emptyList())

    /** Parses the directory; null if it isn't one (HTML error page, truncated download…). Bad rows are dropped. */
    fun parse(text: String): List<Entry>? = runCatching { HarborJson.decodeFromString(File.serializer(), text) }.getOrNull()
        ?.stations?.filter { it.id.isNotBlank() && it.name.isNotBlank() && (it.url.startsWith("http://") || it.url.startsWith("https://")) }
        ?.distinctBy { it.id }?.takeIf { it.isNotEmpty() }

    /**
     * Merges [directory] into the user's [current] stations.
     *  - [firstTime]: add every directory station (in directory order, ahead of the user's own), skipping links the
     *    user already has.
     *  - Always: a directory station the user still has, and never edited ([edited] ids), gets the directory's
     *    current name and link – a fixed link in the repo reaches everyone.
     *  - Stations the user removed stay removed; the user's own stations are untouched.
     */
    fun merge(current: List<RadioStation>, directory: List<Entry>, firstTime: Boolean, edited: Set<String>): List<RadioStation> {
        val byId = directory.associateBy { it.id }
        val updated = current.map { s ->
            val d = byId[s.id]
            if (d != null && s.id !in edited) s.copy(name = d.name, url = d.url) else s
        }
        if (!firstTime) return updated
        val haveIds = updated.map { it.id }.toSet(); val haveUrls = updated.map { it.url }.toSet()
        val added = directory.filter { it.id !in haveIds && it.url !in haveUrls }.map { RadioStation(id = it.id, name = it.name, url = it.url) }
        return added + updated
    }
}
