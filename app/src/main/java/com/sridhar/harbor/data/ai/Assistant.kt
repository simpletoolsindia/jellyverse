package com.sridhar.harbor.data.ai

import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.HarborJson
import com.sridhar.harbor.data.IncomingTorrent
import com.sridhar.harbor.data.arr.ArrKind
import com.sridhar.harbor.data.qbit.AddTorrentRequest
import com.sridhar.harbor.data.qbit.TorrentPhase
import com.sridhar.harbor.data.seerr.MediaStatus
import com.sridhar.harbor.data.seerr.SeerrRepository
import com.sridhar.harbor.ui.components.formatBytes
import com.sridhar.harbor.ui.components.formatSpeed
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.booleanOrNull

/** Where a tool wants the UI to go. */
sealed interface AiNav {
    data object Doctor : AiNav
    data object Lab : AiNav
    data object Terminal : AiNav
    data object Downloads : AiNav
    data object Manage : AiNav
    data object Discover : AiNav
    data object Requests : AiNav
    data class Item(val id: String) : AiNav
    data class Seerr(val type: String, val id: Int) : AiNav
    data class Play(val id: String) : AiNav
    data class Cast(val id: String, val device: String?) : AiNav
}

data class AiCard(val title: String, val subtitle: String?, val image: String?, val nav: AiNav)

data class ToolResult(
    val text: String,
    val cards: List<AiCard> = emptyList(),
    val nav: AiNav? = null,
    /** Destructive actions wait for the user's tap. */
    val confirm: Pair<String, suspend () -> String>? = null,
)

class AiTool(val name: String, val signature: String, val description: String, val run: suspend (JsonObject) -> ToolResult)

sealed interface ChatItem {
    data class User(val text: String) : ChatItem
    data class Bot(val text: String, val streaming: Boolean = false) : ChatItem
    data class ToolUse(val tool: String, val args: String, val result: String, val viaRouter: Boolean) : ChatItem
    data class Cards(val cards: List<AiCard>) : ChatItem
    data class Confirm(val id: Long, val prompt: String, val action: suspend () -> String, val state: String = "pending") : ChatItem
}

/**
 * Tool-calling agent on top of on-device Qwen2.5-0.5B.
 * The model chooses a tool in Qwen's native <tool_call>{json}</tool_call> format; a small keyword router
 * covers obvious intents if the model answers without calling a tool.
 */
class Assistant(private val c: AppContainer) {

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    private fun JsonObject.num(k: String) = this[k]?.jsonPrimitive?.let { it.doubleOrNull ?: it.contentOrNull?.filter { ch -> ch.isDigit() || ch == '.' }?.toDoubleOrNull() }
    private fun JsonObject.bool(k: String) = this[k]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.contentOrNull?.lowercase() in setOf("on", "yes", "true", "1")) }

    val tools: List<AiTool> = listOf(
        AiTool("search_library", "query", "find a movie or show in my Jellyfin library") { a ->
            val cfg = c.config.value!!
            val hits = c.jellyfin.search(a.str("query")).take(6)
            if (hits.isEmpty()) ToolResult("Nothing in the library matches \"${a.str("query")}\".")
            else ToolResult(hits.joinToString("; ") { "${it.name} (${it.year ?: "?"}, ${it.type})" },
                hits.map { AiCard(it.name, listOfNotNull(it.year?.toString(), it.type).joinToString(" · "), c.jellyfin.posterUrl(cfg, it, 300), AiNav.Item(it.id)) })
        },
        AiTool("play", "title, device", "play a movie/episode; device=\"tv\" casts it to Chromecast") { a ->
            val title = a.str("title")
            // Strict match for playback – never start the wrong film.
            val hits = c.jellyfin.fuzzyFind(title, minScore = 0.8f)
            val hit = hits.firstOrNull { it.type == "Movie" || it.type == "Episode" }
                ?: hits.firstOrNull { it.type == "Series" }?.let { s -> c.jellyfin.nextUpFor(s.id) ?: c.jellyfin.episodes(s.id, null).firstOrNull() }
            if (hit == null) {
                val offers = if (c.config.value?.seerrReady == true) runCatching { c.seerr.search(title).results.filter { it.mediaType != "person" }.take(4) }.getOrDefault(emptyList()) else emptyList()
                return@AiTool ToolResult(
                    "\"$title\" isn't in your library." + if (offers.isNotEmpty()) " Tap one below to request it." else "",
                    offers.map { AiCard(it.displayTitle, listOfNotNull(it.year, it.status.label).joinToString(" · "), SeerrRepository.tmdb(it.posterPath, "w185"), AiNav.Seerr(it.mediaType, it.id)) },
                )
            }
            val label = "${hit.seriesName?.let { "$it – " } ?: ""}${hit.name}"
            val device = a.str("device")
            if (device.isNotBlank() && device.lowercase() !in setOf("phone", "mobile", "here", "this"))
                ToolResult("Casting $label to ${device}…", nav = AiNav.Cast(hit.id, device))
            else ToolResult("Playing $label.", nav = AiNav.Play(hit.id))
        },
        AiTool("smart_search", "query", "describe what you want: genre, year, mood, e.g. 'horror movies from 2024'") { a ->
            val cfg = c.config.value!!
            val items = smartSearch(a.str("query"))
            ToolResult(if (items.isEmpty()) "No matches." else items.take(6).joinToString(", ") { "${it.name} (${it.year ?: "?"})" },
                items.take(10).map { AiCard(it.name, listOfNotNull(it.year?.toString(), it.genres.firstOrNull()).joinToString(" · "), c.jellyfin.posterUrl(cfg, it, 300), AiNav.Item(it.id)) })
        },
        AiTool("continue_watching", "", "what I was watching / resume list") { _ ->
            val cfg = c.config.value!!
            val list = c.jellyfin.resume().take(6)
            ToolResult(if (list.isEmpty()) "Nothing in progress." else list.joinToString("; ") { "${it.seriesName ?: it.name} ${(it.progress * 100).toInt()}%" },
                list.map { AiCard(it.seriesName ?: it.name, it.episodeLabel ?: "${(it.progress * 100).toInt()}% watched", c.jellyfin.thumbUrl(cfg, it, 300), AiNav.Play(it.id)) })
        },
        AiTool("suggest", "genre", "recommend something unwatched to watch tonight") { a ->
            val cfg = c.config.value!!
            val g = a.str("genre").ifBlank { null }
            val items = c.jellyfin.randomUnwatched(g).take(5)
            ToolResult(if (items.isEmpty()) "No unwatched ${g ?: ""} titles found." else "Try: " + items.joinToString(", ") { it.name },
                items.map { AiCard(it.name, listOfNotNull(it.year?.toString(), it.genres.firstOrNull()).joinToString(" · "), c.jellyfin.posterUrl(cfg, it, 300), AiNav.Item(it.id)) })
        },
        AiTool("find_new", "query", "search new movies/series to request (Jellyseerr)") { a ->
            val r = c.seerr.search(a.str("query")).results.filter { it.mediaType != "person" }.take(6)
            ToolResult(r.joinToString("; ") { "${it.displayTitle} (${it.year ?: "?"}, ${it.mediaType}, ${it.status.label})" }.ifBlank { "No results." },
                r.map { AiCard(it.displayTitle, listOfNotNull(it.year, it.status.label).joinToString(" · "), SeerrRepository.tmdb(it.posterPath, "w185"), AiNav.Seerr(it.mediaType, it.id)) })
        },
        AiTool("request", "title", "request a movie or series to be downloaded") { a ->
            val m = c.seerr.search(a.str("title")).results.firstOrNull { it.mediaType != "person" }
                ?: return@AiTool ToolResult("Couldn't find \"${a.str("title")}\".")
            if (m.status != MediaStatus.Unknown) return@AiTool ToolResult("${m.displayTitle} is already ${m.status.label.lowercase()}.")
            ToolResult("Ready to request ${m.displayTitle} (${m.year}).", listOf(AiCard(m.displayTitle, m.year, SeerrRepository.tmdb(m.posterPath, "w185"), AiNav.Seerr(m.mediaType, m.id))),
                confirm = "Request ${m.displayTitle} (${m.year ?: "?"}) ${if (m.mediaType == "tv") "– all seasons" else ""}?" to suspend {
                    val seasons = if (m.mediaType == "tv") c.seerr.details("tv", m.id).seasons.map { it.seasonNumber }.filter { it > 0 } else null
                    c.seerr.request(m.mediaType, m.id, seasons); "Requested ${m.displayTitle} ✓"
                })
        },
        AiTool("downloads_status", "", "what's downloading, speeds, progress") { _ ->
            val parts = mutableListOf<String>()
            if (c.config.value?.qbitReady == true) runCatching {
                val t = c.qbit.torrents(); val tr = c.qbit.transfer()
                val active = t.filter { it.phase == TorrentPhase.Downloading || it.phase == TorrentPhase.Waiting && it.progress < 1f }
                parts += "qBittorrent ↓${formatSpeed(tr.dlSpeed)} ↑${formatSpeed(tr.upSpeed)}, ${active.size} downloading of ${t.size}" +
                    active.take(4).joinToString("") { "; ${it.name.take(40)} ${(it.progress * 100).toInt()}%" }
            }
            if (c.config.value?.aria2Ready == true) runCatching {
                val d = c.aria2.downloads().filter { it.status == "active" }
                parts += "aria2 ${d.size} active" + d.take(3).joinToString("") { "; ${it.name.take(40)} ${(it.progress * 100).toInt()}%" }
            }
            runCatching { c.radarr.queue().size + c.sonarr.queue().size }.getOrNull()?.let { parts += "Sonarr/Radarr queue: $it" }
            ToolResult(parts.joinToString(". ").ifBlank { "No download clients connected." }, nav = null)
        },
        AiTool("pause_downloads", "target", "pause torrents: 'all' or part of a name") { a ->
            val target = a.str("target").ifBlank { "all" }
            val ts = c.qbit.torrents().filter { !it.isStopped && (target == "all" || it.name.contains(target, true)) }
            if (ts.isEmpty()) ToolResult("Nothing matching \"$target\" is running.")
            else ToolResult("${ts.size} torrent(s) will be paused.", confirm = "Pause ${ts.size} torrent(s)?" to suspend { c.qbit.pause(ts.map { it.hash }); "Paused ${ts.size} ✓" })
        },
        AiTool("resume_downloads", "target", "resume torrents: 'all' or part of a name") { a ->
            val target = a.str("target").ifBlank { "all" }
            val ts = c.qbit.torrents().filter { it.isStopped && it.progress < 1f && (target == "all" || it.name.contains(target, true)) }
            if (ts.isEmpty()) ToolResult("No paused unfinished torrents match \"$target\".")
            else { c.qbit.resume(ts.map { it.hash }); ToolResult("Resumed ${ts.size} torrent(s).") }
        },
        AiTool("set_speed_limit", "download_mbps, upload_mbps", "limit torrent speed in MB/s (0 = unlimited)") { a ->
            val dl = ((a.num("download_mbps") ?: 0.0) * 1024 * 1024).toLong(); val ul = ((a.num("upload_mbps") ?: 0.0) * 1024 * 1024).toLong()
            c.qbit.setGlobalLimits(dl, ul)
            if (c.config.value?.aria2Ready == true) runCatching { c.aria2.setLimits(dl, ul) }
            ToolResult("Limits set: ↓ ${if (dl == 0L) "unlimited" else formatSpeed(dl)}, ↑ ${if (ul == 0L) "unlimited" else formatSpeed(ul)}.")
        },
        AiTool("slow_mode", "on", "turn qBittorrent alternative (turtle) speed on/off") { a ->
            val want = a.bool("on") ?: true
            if (c.qbit.altSpeedEnabled() != want) c.qbit.toggleAltSpeed()
            ToolResult("Turtle mode ${if (want) "on 🐢" else "off"}.")
        },
        AiTool("add_download", "link", "download a magnet / http link") { a ->
            val link = a.str("link")
            if (link.isBlank()) return@AiTool ToolResult("Give me a magnet or http link.")
            if (!link.startsWith("magnet:") && c.config.value?.aria2Ready == true) { c.aria2.addUri(listOf(link)); ToolResult("Sent to aria2.") }
            else { c.qbit.add(AddTorrentRequest(urls = link)); ToolResult("Sent to qBittorrent.") }
        },
        AiTool("server_status", "", "homelab CPU, RAM, temperature, disks, problems") { _ ->
            val h = c.ssh.primary ?: return@AiTool ToolResult("SSH isn't set up – add it in the Lab tab.", nav = AiNav.Lab)
            val s = c.homelab.snapshot(h)
            val cts = runCatching { c.homelab.containers(h, false) }.getOrDefault(emptyList())
            val warn = s.warnings(cts)
            ToolResult("CPU ${(s.cpu * 100).toInt()}%, RAM ${(s.mem * 100).toInt()}%, ${s.tempC?.toInt() ?: "?"}°C, swap ${(s.swap * 100).toInt()}%. " +
                s.disks.joinToString(", ") { "${it.mount} ${(it.fraction * 100).toInt()}%" } + ". " +
                "${cts.count { it.state == "running" }}/${cts.size} containers up. " + (if (warn.isEmpty()) "All healthy." else "Issues: " + warn.joinToString("; ")), nav = null)
        },
        AiTool("restart_service", "name", "restart a docker container on the homelab") { a ->
            val h = c.ssh.primary ?: return@AiTool ToolResult("SSH isn't set up.")
            val name = a.str("name").lowercase()
            val ct = c.homelab.containers(h, false).minByOrNull { if (it.name == name) 0 else if (it.name.contains(name)) 1 else 9 }
                ?.takeIf { it.name.contains(name) } ?: return@AiTool ToolResult("No container called \"$name\".")
            ToolResult("${ct.name} is ${ct.status}.", confirm = "Restart ${ct.name}?" to suspend { c.homelab.containerAction(h, ct.name, "restart"); "${ct.name} restarted ✓" })
        },
        AiTool("missing_media", "", "monitored movies/episodes without files (Radarr/Sonarr)") { _ ->
            val m = ArrKind.entries.flatMap { k -> runCatching { c.arr(k).missing() }.getOrDefault(emptyList()) }
            ToolResult(if (m.isEmpty()) "Nothing missing." else "${m.size} missing: " + m.take(8).joinToString(", ") { it.title }, nav = AiNav.Manage)
        },
        AiTool("search_missing", "title", "make Radarr/Sonarr search indexers now for a title (or 'all')") { a ->
            val t = a.str("title")
            if (t.isBlank() || t == "all") {
                ArrKind.entries.forEach { k -> runCatching { c.arr(k).searchAllMissing() } }
                return@AiTool ToolResult("Searching for everything missing.")
            }
            val m = runCatching { c.radarr.movies() }.getOrDefault(emptyList()).firstOrNull { it.title.contains(t, true) }
            if (m != null) { c.radarr.searchMovie(m.id); return@AiTool ToolResult("Radarr is searching for ${m.title}.") }
            val s = runCatching { c.sonarr.series() }.getOrDefault(emptyList()).firstOrNull { it.title.contains(t, true) }
            if (s != null) { c.sonarr.searchSeries(s.id); ToolResult("Sonarr is searching for ${s.title}.") } else ToolResult("\"$t\" isn't in Radarr or Sonarr – try request.")
        },
        AiTool("upcoming", "", "releases coming this week") { _ ->
            val u = ArrKind.entries.flatMap { k -> runCatching { c.arr(k).upcoming(7) }.getOrDefault(emptyList()) }.sortedBy { it.date }
            ToolResult(if (u.isEmpty()) "Nothing scheduled this week." else u.take(8).joinToString("; ") { "${it.title} ${it.subtitle.orEmpty()} ${it.date?.take(10)}" })
        },
        AiTool("fix_library", "", "fix wrong titles / missing posters in Jellyfin (Library Doctor)") { _ ->
            ToolResult("Opening Library Doctor – tap Scan to check every title.", nav = AiNav.Doctor)
        },
        AiTool("pending_requests", "", "requests waiting for approval") { _ ->
            val p = c.seerr.requests("pending", 0, 10).results
            ToolResult(if (p.isEmpty()) "No pending requests." else "${p.size} pending request(s).", nav = AiNav.Requests)
        },
        AiTool("open", "screen", "open a screen: lab, terminal, downloads, manage, discover, requests, doctor") { a ->
            val nav = when (a.str("screen").lowercase()) {
                "lab", "health", "server" -> AiNav.Lab; "terminal", "ssh", "shell" -> AiNav.Terminal
                "downloads", "torrents", "aria2" -> AiNav.Downloads; "manage", "sonarr", "radarr" -> AiNav.Manage
                "discover" -> AiNav.Discover; "requests" -> AiNav.Requests; else -> AiNav.Doctor
            }
            ToolResult("Opening.", nav = nav)
        },
    )

    private val genres = IntentRouter.genres

    /** Natural-language library search: rules first, Qwen refines when available. */
    suspend fun smartSearch(query: String): List<com.sridhar.harbor.data.jellyfin.BaseItem> {
        val q = query.lowercase()
        var year = Regex("(19|20)\\d\\d").find(q)?.value
        var genre = genres.firstOrNull { it in q }?.let { if (it == "sci-fi") "science fiction" else it }
        var type = when { Regex("\\b(series|shows?|tv)\\b").containsMatchIn(q) -> "Series"; Regex("\\b(movies?|films?)\\b").containsMatchIn(q) -> "Movie"; else -> "Movie,Series" }
        var term: String? = q.replace(Regex("(19|20)\\d\\d"), " ").let { t -> genres.fold(t) { acc, g -> acc.replace(g, " ") } }
            .replace(Regex("\\b(find|show|me|some|any|good|best|movies?|films?|series|shows?|tv|from|in|of|the|a|an|with|about|like|please|i|want|to|watch|something|recent|new|old|tamil|telugu|hindi|english|malayalam)\\b"), " ")
            .replace(Regex("\\s+"), " ").trim().ifBlank { null }
        if (c.llm.state.value.let { it is ModelState.Ready || it is ModelState.Loaded } && query.split(' ').size >= 3) runCatching {
            val out = c.llm.complete("<|im_start|>system\nConvert a movie search request to JSON {\"title\":string|null,\"genre\":string|null,\"year\":number|null,\"type\":\"movie\"|\"series\"|null}. Only JSON.<|im_end|>\n" +
                "<|im_start|>user\nscary movies from 2023<|im_end|>\n<|im_start|>assistant\n{\"title\":null,\"genre\":\"horror\",\"year\":2023,\"type\":\"movie\"}<|im_end|>\n" +
                "<|im_start|>user\nthat vijay movie leo<|im_end|>\n<|im_start|>assistant\n{\"title\":\"leo\",\"genre\":null,\"year\":null,\"type\":\"movie\"}<|im_end|>\n" +
                "<|im_start|>user\n${query.take(160)}<|im_end|>\n<|im_start|>assistant\n", temperature = 0.05f)
            val o = HarborJson.parseToJsonElement(Regex("\\{[^{}]*\\}").find(out)!!.value).jsonObject
            o["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }?.let { term = it }
            o["genre"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }?.let { genre = it }
            o["year"]?.jsonPrimitive?.contentOrNull?.takeIf { it.length == 4 }?.let { year = it }
            when (o["type"]?.jsonPrimitive?.contentOrNull) { "movie" -> type = "Movie"; "series" -> type = "Series" }
        }
        val g = genre?.split(' ')?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        val res = c.jellyfin.filtered(term, g, year, type)
        return res.ifEmpty { term?.let { c.jellyfin.fuzzyFind(it) }.orEmpty() }
    }

    private fun systemPrompt() = buildString {
        append("You are JellyVerse AI, a helpful media & homelab assistant running offline on the user's phone. ")
        append("For anything about their movies, shows, downloads or server, call ONE tool by replying only:\n")
        append("<tool_call>{\"name\":\"tool_name\",\"arguments\":{...}}</tool_call>\nTools:\n")
        tools.forEach { t -> append("- ").append(t.name).append("(").append(t.signature).append("): ").append(t.description).append('\n') }
        append("After <tool_response>, reply in one or two short friendly sentences. Never invent results.")
    }

    private val fewShot = "<|im_start|>user\nis dune in my library?<|im_end|>\n<|im_start|>assistant\n<tool_call>{\"name\":\"search_library\",\"arguments\":{\"query\":\"dune\"}}</tool_call><|im_end|>\n" +
        "<|im_start|>user\nlimit downloads to 5 MB/s<|im_end|>\n<|im_start|>assistant\n<tool_call>{\"name\":\"set_speed_limit\",\"arguments\":{\"download_mbps\":5,\"upload_mbps\":0}}</tool_call><|im_end|>\n"

    private val callRx = Regex("""<tool_call>\s*(\{.*?\})\s*(</tool_call>|$)""", RegexOption.DOT_MATCHES_ALL)
    private val bareRx = Regex("""\{\s*"name"\s*:\s*"([a-z_]+)".*\}""", RegexOption.DOT_MATCHES_ALL)

    private fun parseCall(out: String): Pair<AiTool, JsonObject>? {
        val json = callRx.find(out)?.groupValues?.get(1) ?: bareRx.find(out)?.value ?: return null
        val obj = runCatching { HarborJson.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val tool = tools.firstOrNull { it.name == name } ?: return null
        val args = runCatching { obj["arguments"]?.jsonObject }.getOrNull() ?: JsonObject(emptyMap())
        return tool to args
    }

    /** Deterministic fallback for obvious intents (keeps a 0.5B model useful). */
    private fun route(msg: String): Pair<AiTool, JsonObject>? = IntentRouter.route(msg)?.let { intent ->
        tools.firstOrNull { it.name == intent.tool }?.let { it to JsonObject(intent.args.mapValues { (_, v) -> kotlinx.serialization.json.JsonPrimitive(v) }) }
    }

    /** One user turn. Emits chat items as they happen. */
    suspend fun ask(message: String, history: List<ChatItem>, emit: (ChatItem, replaceLast: Boolean) -> Unit) {
        val modelOk = c.llm.state.value.let { it is ModelState.Ready || it is ModelState.Loaded }
        val recent = history.filter { it is ChatItem.User || it is ChatItem.Bot }.takeLast(4)
        val base = buildString {
            append("<|im_start|>system\n").append(systemPrompt()).append("<|im_end|>\n").append(fewShot)
            recent.forEach { h ->
                when (h) {
                    is ChatItem.User -> append("<|im_start|>user\n").append(h.text.take(200)).append("<|im_end|>\n")
                    is ChatItem.Bot -> append("<|im_start|>assistant\n").append(h.text.take(200)).append("<|im_end|>\n")
                    else -> {}
                }
            }
            append("<|im_start|>user\n").append(message.take(300)).append("<|im_end|>\n<|im_start|>assistant\n")
        }

        var first = ""
        // Obvious intents go straight to the right tool – instant and reliable; Qwen handles the open-ended rest.
        var call: Pair<AiTool, JsonObject>? = route(message)
        var viaRouter = call != null
        if (modelOk && call == null) {
            emit(ChatItem.Bot("", streaming = true), false)
            first = runCatching {
                c.llm.complete(base) { partial -> if (!partial.trimStart().startsWith("<tool") && !partial.trimStart().startsWith("{")) emit(ChatItem.Bot(partial, true), true) }
            }.getOrElse { e -> emit(ChatItem.Bot("⚠ ${e.message}"), true); return }
            call = parseCall(first)
        }

        val (tool, args) = call ?: run {
            emit(ChatItem.Bot(first.ifBlank { if (modelOk) "I'm not sure – try asking about your library, downloads or server." else "Download the AI model for free-form chat. Meanwhile I understand commands like “what's downloading”, “server health”, “pause all”, “play Leo”." }), modelOk)
            return
        }
        if (modelOk && !viaRouter) emit(ChatItem.Bot("", true), true)
        val result = runCatching { tool.run(args) }.getOrElse { e -> ToolResult("Tool failed: ${e.message}") }
        emit(ChatItem.ToolUse(tool.name, args.entries.joinToString(", ") { "${it.key}: ${it.value.jsonPrimitive.contentOrNull}" }, result.text.take(300), viaRouter), modelOk && !viaRouter)
        if (result.cards.isNotEmpty()) emit(ChatItem.Cards(result.cards), false)
        result.confirm?.let { (prompt, action) -> emit(ChatItem.Confirm(System.nanoTime(), prompt, action), false) }
        result.nav?.let { pendingNav = it }

        // Answer with the tool's own result: a 0.5B model paraphrasing data tends to invent things.
        emit(ChatItem.Bot(result.text), modelOk && !viaRouter && false)
    }

    /** Navigation requested by the last tool; consumed by the UI. */
    @Volatile var pendingNav: AiNav? = null
}
