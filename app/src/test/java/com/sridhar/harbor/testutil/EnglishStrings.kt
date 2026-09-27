package com.sridhar.harbor.testutil

import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Lets plain JVM tests see real English UI text: resolves R.string ids against values/strings*.xml
 * (no Robolectric needed) and plugs the lookup into [L10n].
 */
object EnglishStrings {
    private val byName: Map<String, String> by lazy {
        val dir = listOf("src/main/res/values", "app/src/main/res/values").map(::File).first { it.isDirectory }
        dir.listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }!!.flatMap { f ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val nodes = doc.getElementsByTagName("string")
            (0 until nodes.length).map { i ->
                val e = nodes.item(i) as Element
                e.getAttribute("name") to unescape(e.textContent)
            }
        }.toMap()
    }
    private val byId: Map<Int, String> by lazy {
        R.string::class.java.fields.associate { it.getInt(null) to it.name }
    }

    private fun unescape(s: String) = s.removeSurrounding("\"").replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n").replace("\\@", "@").replace("\\?", "?")

    fun get(id: Int, vararg args: Any?): String {
        val raw = byName[byId[id]] ?: error("Unknown string id $id")
        return if (args.isEmpty()) raw.replace("%%", "%") else String.format(raw, *args)
    }

    fun install() { L10n.testLookup = { id, args -> get(id, *args) } }
}
