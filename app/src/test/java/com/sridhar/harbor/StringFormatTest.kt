package com.sridhar.harbor

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/** Every string with format arguments must be a valid format: a lone "%" (e.g. "100% on") crashes getString(). */
class StringFormatTest {
    @Test fun formatStringsAreValid() {
        val res = listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }
        val entry = Regex("""<string name="([^"]+)"([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val spec = Regex("""%(\d+\$)?([a-zA-Z%])""")
        val bad = res.walk().filter { it.name.startsWith("strings") && it.extension == "xml" }.flatMap { f ->
            entry.findAll(f.readText()).mapNotNull { m ->
                val (name, attrs, value) = m.destructured
                if ("formatted=\"false\"" in attrs || !Regex("""%(\d+\$)?[sd]""").containsMatchIn(value)) return@mapNotNull null
                val stray = value.split("%%").joinToString("").let { v -> v.count { it == '%' } != spec.findAll(v).count() ||
                    spec.findAll(v).any { it.groupValues[2] !in setOf("s", "d", "f") } }
                if (stray) "${f.parentFile.name}/$name: $value" else null
            }
        }.toList()
        assertWithMessage("Strings with an unescaped % (use %%):\n" + bad.joinToString("\n")).that(bad).isEmpty()
    }
}
