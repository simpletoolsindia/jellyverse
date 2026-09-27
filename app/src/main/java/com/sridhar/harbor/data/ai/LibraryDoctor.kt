package com.sridhar.harbor.data.ai

import com.sridhar.harbor.data.HarborJson
import com.sridhar.harbor.data.jellyfin.JellyfinRepository
import com.sridhar.harbor.data.jellyfin.LibraryItem
import com.sridhar.harbor.data.jellyfin.RemoteSearchResult
import com.sridhar.harbor.data.ssh.SshRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

enum class IssueKind(val label: String) { Unidentified("Not matched"), WrongMatch("Wrong match?"), MessyName("Messy name"), NoPoster("No poster"), Misfiled("Series in Movies") }
enum class FixStatus { Pending, Applying, Applied, Skipped, Failed }

data class Candidate(val result: RemoteSearchResult, val raw: JsonObject, val kind: String, val score: Float)

data class DoctorIssue(
    val item: LibraryItem,
    val kinds: Set<IssueKind>,
    val parsed: ParsedTitle,
    val aiTitle: ParsedTitle? = null,
    val candidates: List<Candidate> = emptyList(),
    val chosen: Int = 0,
    val status: FixStatus = FixStatus.Pending,
    val error: String? = null,
) {
    val best get() = candidates.getOrNull(chosen)
    val confident get() = (best?.score ?: 0f) >= 0.8f && best?.kind == item.type
}

data class MovePlan(val itemId: String, val label: String, val fromHost: String, val toHost: String, val commands: String, val done: Boolean? = null, val error: String? = null)

/**
 * Finds badly-named / unmatched library entries, figures out the real title (rules + on-device Qwen),
 * asks Jellyfin's metadata providers for matches and applies the chosen one so Jellyfin downloads posters.
 */
class LibraryDoctor(
    private val jf: JellyfinRepository, private val llm: LocalLlm, private val ssh: SshRepository,
    private val prefs: android.content.SharedPreferences,
) {
    /** Corrections the user typed by hand become extra few-shot examples for Qwen. */
    fun learn(raw: String, title: String, year: Int?, series: Boolean) {
        val entry = HarborJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), kotlinx.serialization.json.buildJsonObject {
            put("raw", kotlinx.serialization.json.JsonPrimitive(raw.take(120)))
            put("out", kotlinx.serialization.json.JsonPrimitive("{\"title\":\"${title.replace("\"", "")}\",\"year\":${year ?: "null"},\"type\":\"${if (series) "series" else "movie"}\"}"))
        })
        val list = (prefs.getStringSet("learned", emptySet()).orEmpty() + entry).toList().takeLast(12).toSet()
        prefs.edit().putStringSet("learned", list).apply()
    }

    private fun learned(): List<Pair<String, String>> = prefs.getStringSet("learned", emptySet()).orEmpty().mapNotNull {
        runCatching { HarborJson.parseToJsonElement(it).jsonObject.let { o -> o["raw"]!!.jsonPrimitive.content to o["out"]!!.jsonPrimitive.content } }.getOrNull()
    }.takeLast(3)

    private val examples = listOf(
        "www.1tamilmv.com_aranmanai_2026.mp4" to "{\"title\":\"Aranmanai\",\"year\":2026,\"type\":\"movie\"}",
        "www.1TamilMV.rocks - Leo (2023) Tamil HQ HDRip - 1080p - x264 - [Tam + Tel] - 2.4GB.mkv" to "{\"title\":\"Leo\",\"year\":2023,\"type\":\"movie\"}",
        "Jailer.2023.1080p.AMZN.WEB-DL.DDP5.1.H.264-TamilBlasters.mkv" to "{\"title\":\"Jailer\",\"year\":2023,\"type\":\"movie\"}",
        "Breaking.Bad.S03E07.720p.BluRay.x264-DEMAND" to "{\"title\":\"Breaking Bad\",\"year\":null,\"type\":\"series\"}",
        "AlienEarth (2025)" to "{\"title\":\"Alien: Earth\",\"year\":2025,\"type\":\"series\"}",
        "13th Some Lessons Aren't Taught In Classrooms (2025)" to "{\"title\":\"13th\",\"year\":2025,\"type\":\"movie\"}",
    )


    suspend fun findIssues(): List<DoctorIssue> = jf.libraryItems().mapNotNull { item ->
        val parsed = TitleCleaner.parse(DoctorRules.sourceName(item))
        val kinds = DoctorRules.issueKinds(item, parsed)
        if (kinds.isEmpty()) null
        else DoctorIssue(item, if (TitleCleaner.looksMessy(item.name)) kinds + IssueKind.MessyName else kinds, parsed)
    }.sortedWith(compareBy<DoctorIssue> { IssueKind.WrongMatch !in it.kinds }.thenBy { it.item.name.lowercase() })

    /** Ask Qwen to extract the clean title. Few-shot, JSON-only, temperature ~0. */
    suspend fun aiParse(raw: String): ParsedTitle? {
        if (llm.state.value !is ModelState.Loaded && llm.state.value !is ModelState.Ready) return null
        val prompt = buildString {
            append("<|im_start|>system\nYou extract the real movie or TV series title from messy file names. ")
            append("Remove website names, quality, codec, language, size, uploader tags, YouTube ids and taglines. ")
            append("Reply ONLY with JSON {\"title\":string,\"year\":number|null,\"type\":\"movie\"|\"series\"}.<|im_end|>\n")
            (examples + learned()).forEach { (input, output) ->
                append("<|im_start|>user\n").append(input).append("<|im_end|>\n<|im_start|>assistant\n").append(output).append("<|im_end|>\n")
            }
            append("<|im_start|>user\n").append(raw.take(200)).append("<|im_end|>\n<|im_start|>assistant\n")
        }
        val out = runCatching { llm.complete(prompt, temperature = 0.05f) }.getOrNull() ?: return null
        val json = Regex("""\{[^{}]*\}""").find(out)?.value ?: return null
        return runCatching {
            val o = HarborJson.parseToJsonElement(json).jsonObject
            val title = o["title"]!!.jsonPrimitive.content.trim()
            if (title.isBlank()) null
            else ParsedTitle(title, o["year"]?.jsonPrimitive?.intOrNull, if (o["type"]?.jsonPrimitive?.content == "series") "series" else "movie")
        }.getOrNull()
    }

    /** Search several title variants across Movie and Series providers; best matches first. */
    suspend fun identify(issue: DoctorIssue, useAi: Boolean): DoctorIssue {
        val ai = if (useAi) aiParse(DoctorRules.sourceName(issue.item)) else null
        val primary = ai ?: issue.parsed
        val titles = buildList {
            ai?.let { add(it.title) }
            add(issue.parsed.title)
            val words = issue.parsed.title.split(' ').filter { it.isNotBlank() }
            if (words.size > 3) add(words.take(3).joinToString(" "))
            if (words.size > 2) add(words.take(2).joinToString(" "))
            if (words.size > 1 && words[0].length > 3) add(words[0])
        }.distinct()
        val kinds = if (issue.item.type == "Series" || primary.type == "series") listOf("Series", "Movie") else listOf("Movie", "Series")
        val found = mutableListOf<Candidate>()
        loop@ for (kind in kinds) for (t in titles) {
            val results = runCatching { jf.remoteSearch(issue.item.id, kind, t, primary.year) }.getOrDefault(emptyList())
            // Always score against the full title (never the shortened query) so partial words can't look confident.
            results.forEach { (r, raw) ->
                val sc = maxOf(DoctorRules.score(primary, r, kind, issue.item.type), DoctorRules.score(issue.parsed, r, kind, issue.item.type))
                found += Candidate(r, raw, kind, sc)
            }
            if ((found.maxOfOrNull { it.score } ?: 0f) >= 0.92f) break@loop
        }
        val ranked = found.distinctBy { it.kind + (it.result.providerIds["Tmdb"] ?: it.result.name + it.result.year) }.sortedByDescending { it.score }.take(6)
        val kindsOut = if (ranked.firstOrNull()?.kind == "Series" && issue.item.type == "Movie") issue.kinds + IssueKind.Misfiled else issue.kinds
        return issue.copy(aiTitle = ai, candidates = ranked, chosen = 0, kinds = kindsOut)
    }

    /** Apply a match: Jellyfin replaces metadata and downloads poster/backdrop/logo. */
    suspend fun apply(issue: DoctorIssue): DoctorIssue {
        val c = issue.best ?: return issue.copy(status = FixStatus.Failed, error = "No match selected")
        if (c.kind != issue.item.type) return issue.copy(status = FixStatus.Failed, error = "This is a ${c.kind.lowercase()} – move it to the right library with Organize")
        return runCatching { jf.applyMatch(issue.item.id, c.raw) }
            .fold({ issue.copy(status = FixStatus.Applied, error = null) }, { issue.copy(status = FixStatus.Failed, error = it.message) })
    }

    // ---------------- file organisation over SSH ----------------

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"
    private fun safe(s: String) = s.replace(Regex("""[\\/:*?"<>|]"""), " ").replace(Regex("""\s+"""), " ").trim().trimEnd('.')

    /** Container path → host path using Jellyfin's docker mounts. */
    suspend fun hostMounts(): List<Pair<String, String>> {
        val host = ssh.primary ?: throw IOException("Add your homelab SSH login first (Lab tab)")
        return ssh.exec(host, "docker inspect jellyfin --format '{{range .Mounts}}{{.Destination}}|{{.Source}}{{println}}{{end}}'")
            .lines().mapNotNull { l -> l.split('|').takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }
            .sortedByDescending { it.first.length }
    }

    private fun toHost(path: String, mounts: List<Pair<String, String>>) =
        mounts.firstOrNull { path.startsWith(it.first + "/") || path == it.first }?.let { it.second + path.removePrefix(it.first) }

    /**
     * Plans "Title (Year)/Title (Year).ext" for movies and "Show/Season NN/Show SNNENN.ext" for misfiled episodes.
     * Nothing is touched until [executeMoves].
     */
    suspend fun planMoves(issues: List<DoctorIssue>): List<MovePlan> {
        val mounts = hostMounts()
        val folders = jf.virtualFolders()
        val movieRoot = folders.firstOrNull { it.collectionType == "movies" }?.locations?.firstOrNull()?.let { toHost(it, mounts) }
        val tvRoot = folders.firstOrNull { it.collectionType == "tvshows" && it.locations.isNotEmpty() }?.locations?.firstOrNull()?.let { toHost(it, mounts) }
        return issues.filter { it.status == FixStatus.Applied || it.best != null }.mapNotNull { issue ->
            val path = issue.item.path ?: return@mapNotNull null
            val src = toHost(path, mounts) ?: return@mapNotNull null
            val best = issue.best
            val name = safe(best?.result?.name ?: issue.aiTitle?.title ?: issue.parsed.title)
            val year = best?.result?.year ?: issue.aiTitle?.year ?: issue.parsed.year
            val ext = src.substringAfterLast('.', "mkv")
            val srcDir = src.substringBeforeLast('/')
            val misfiled = IssueKind.Misfiled in issue.kinds || best?.kind == "Series" && issue.item.type == "Movie"
            if (misfiled && tvRoot != null) {
                val p = issue.parsed
                val season = p.season ?: 1
                val dstDir = "$tvRoot/$name/Season %02d".format(season)
                val file = if (p.episode != null) "$name S%02dE%02d.$ext".format(season, p.episode) else src.substringAfterLast('/')
                val dst = "$dstDir/$file"
                if (dst == src) null else MovePlan(issue.item.id, "$name → TV Shows", src, dst,
                    "mkdir -p ${q(dstDir)} && mv -n ${q(src)} ${q(dst)} && rmdir ${q(srcDir)} 2>/dev/null; test -e ${q(dst)}")
            } else if (issue.item.type == "Movie" && movieRoot != null && srcDir.startsWith(movieRoot)) {
                val clean = if (year != null) "$name ($year)" else name
                val dstDir = "$movieRoot/$clean"
                val dst = "$dstDir/$clean.$ext"
                if (dst == src) null else {
                    // Per-movie folder → rename the folder (keeps subtitles/extras); loose file in root → create folder.
                    val cmd = if (srcDir != movieRoot && srcDir != dstDir)
                        "test ! -e ${q(dstDir)} && mv -n ${q(srcDir)} ${q(dstDir)} && mv -n ${q(dstDir + "/" + src.substringAfterLast('/'))} ${q(dst)}; test -e ${q(dst)}"
                    else "mkdir -p ${q(dstDir)} && mv -n ${q(src)} ${q(dst)}; test -e ${q(dst)}"
                    MovePlan(issue.item.id, clean, src, dst, cmd)
                }
            } else null
        }
    }

    suspend fun executeMoves(plans: List<MovePlan>, onEach: (MovePlan) -> Unit): List<MovePlan> {
        val host = ssh.primary ?: throw IOException("No SSH host")
        val out = plans.map { p ->
            val r = runCatching { ssh.exec(host, "(${p.commands}) && echo HARBOR_OK || echo HARBOR_FAIL", 120_000) }
            val res = r.fold(
                { txt -> if (txt.contains("HARBOR_OK")) p.copy(done = true) else p.copy(done = false, error = txt.replace("HARBOR_FAIL", "").trim().ifBlank { "Target exists or source missing" }) },
                { e -> p.copy(done = false, error = e.message) })
            onEach(res); res
        }
        if (out.any { it.done == true }) runCatching { jf.refreshLibrary() }
        return out
    }
}
