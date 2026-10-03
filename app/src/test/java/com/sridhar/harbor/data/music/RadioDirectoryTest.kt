package com.sridhar.harbor.data.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class RadioDirectoryTest {
    private val dir = listOf(RadioDirectory.Entry("tn-a", "A FM", "https://a/1"), RadioDirectory.Entry("tn-b", "B FM", "https://b/1"))

    @Test fun parsesTheRepoFileAndRejectsGarbage() {
        val repo = File("../radio/stations.json").takeIf { it.exists() } ?: File("radio/stations.json")
        val parsed = RadioDirectory.parse(repo.readText())
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.size).isAtLeast(20)
        assertThat(parsed.first().id).isEqualTo("tn-suryan")
        assertThat(parsed.all { it.url.startsWith("http") }).isTrue()
        assertThat(RadioDirectory.parse("<html>rate limited</html>")).isNull()
        assertThat(RadioDirectory.parse("""{"stations":[]}""")).isNull()
        assertThat(RadioDirectory.parse("""{"stations":[{"id":"x","name":"X","url":"ftp://nope"},{"id":"y","name":"Y","url":"https://y"}]}""")!!.map { it.id }).containsExactly("y")
    }

    @Test fun newInstallGetsEveryPresetFirst() {
        val mine = listOf(RadioStation(id = "u1", name = "Mine", url = "https://mine"))
        val merged = RadioDirectory.merge(mine, dir, firstTime = true, edited = emptySet())
        assertThat(merged.map { it.id }).containsExactly("tn-a", "tn-b", "u1").inOrder()
    }

    @Test fun laterSyncsFixLinksButNeverReAddRemovedOrTouchEdited() {
        // The user removed tn-b and edited tn-a's link themselves.
        val mine = listOf(RadioStation(id = "tn-a", name = "A FM", url = "https://my-own-a"), RadioStation(id = "u1", name = "Mine", url = "https://mine"))
        val moved = listOf(RadioDirectory.Entry("tn-a", "A FM", "https://a/2"), RadioDirectory.Entry("tn-b", "B FM", "https://b/2"))
        assertThat(RadioDirectory.merge(mine, moved, firstTime = false, edited = setOf("tn-a"))).isEqualTo(mine)
        // Not edited: the fixed link from the repo is applied, nothing re-added.
        val fixed = RadioDirectory.merge(mine, moved, firstTime = false, edited = emptySet())
        assertThat(fixed.map { it.id to it.url }).containsExactly("tn-a" to "https://a/2", "u1" to "https://mine").inOrder()
    }

    @Test fun upgradeFromBuiltInPresetsAddsNoDuplicates() {
        val old = listOf(RadioStation(id = "tn-a", name = "A FM", url = "https://a/1"), RadioStation(id = "tn-b", name = "B FM", url = "https://b/1"))
        assertThat(RadioDirectory.merge(old, dir, firstTime = true, edited = emptySet())).isEqualTo(old)
    }

    @Test fun upgradeKeepsLinksTheUserChanged() {
        val repo = File("../radio/stations.json").takeIf { it.exists() } ?: File("radio/stations.json")
        val entries = RadioDirectory.parse(repo.readText())!!
        val shipped = entries.map { RadioStation(id = it.id, name = it.name, url = it.url) }
        assertThat(RadioDirectory.userEdited(shipped, entries)).isEmpty()
        val changed = shipped.mapIndexed { i, s -> if (i == 2) s.copy(url = "https://my.own/stream") else s }
        assertThat(RadioDirectory.userEdited(changed, entries)).containsExactly(entries[2].id)
    }
}
