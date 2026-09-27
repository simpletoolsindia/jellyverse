package com.sridhar.harbor.i18n

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Every UI string must exist in Tamil with exactly the same format placeholders (a mismatch crashes at runtime). */
class TranslationsTest {
    private val res = listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }

    private fun load(dir: String): Map<String, String> =
        File(res, dir).listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }.orEmpty().flatMap { f ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f).getElementsByTagName("string")
            (0 until nodes.length).map { (nodes.item(it) as Element).let { e -> e.getAttribute("name") to e.textContent } }
        }.toMap()

    private val en = load("values")
    private val ta = load("values-ta")
    private fun placeholders(s: String) = Regex("""%(\d+\$)?[sd]|%%""").findAll(s).map { it.value }.sorted().toList()

    @Test fun everyUiStringIsTranslated() {
        val ui = load("values").keys.filter { File(res, "values/strings_ui.xml").readText().contains("name=\"$it\"") }
        assertWithMessage("Missing Tamil strings").that(ui.filterNot { it in ta }).isEmpty()
    }

    @Test fun placeholdersMatch() {
        val bad = ta.filter { (k, v) -> en[k] != null && placeholders(en.getValue(k)) != placeholders(v) }.keys
        assertWithMessage("Placeholder mismatch").that(bad).isEmpty()
    }

    @Test fun noOrphanTranslations() {
        assertWithMessage("Tamil strings with no English source").that(ta.keys - en.keys).isEmpty()
    }

    @Test fun brandNamesStayInLatinScript() {
        listOf("Jellyfin", "qBittorrent", "Sonarr", "Radarr", "Jellyseerr", "JellyVerse").forEach { brand ->
            val lost = en.filter { (k, v) -> v.contains(brand) && ta[k]?.contains(brand) == false }.keys
            assertWithMessage("'$brand' was translated away in").that(lost).isEmpty()
        }
    }
}
