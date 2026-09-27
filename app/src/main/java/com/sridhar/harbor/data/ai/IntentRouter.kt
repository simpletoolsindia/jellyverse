package com.sridhar.harbor.data.ai

/** A tool name plus string arguments, resolved without the LLM. */
data class Intent(val tool: String, val args: Map<String, String> = emptyMap())

/**
 * Rule-based intent classifier that runs before the on-device model: obvious commands ("play Leo on tv",
 * "pause all", "cpu usage") resolve instantly and deterministically. Pure – no I/O, no Android.
 */
object IntentRouter {
    val genres = listOf("action", "comedy", "horror", "thriller", "drama", "romance", "science fiction", "sci-fi", "animation", "crime",
        "family", "fantasy", "mystery", "adventure", "documentary", "war", "history", "music", "western")

    fun route(msg: String): Intent? {
        val m = msg.lowercase()
        fun t(n: String, vararg kv: Pair<String, String>) = Intent(n, kv.toMap())
        fun after(vararg w: String) = w.firstNotNullOfOrNull { k -> m.indexOf(k).takeIf { it >= 0 }?.let { msg.substring(it + k.length).trim(' ', '?', '.', '!') } }.orEmpty()
        Regex("""(\d+(?:\.\d+)?)\s*(mb|m)""").find(m)?.let { if ("limit" in m || "speed" in m) return t("set_speed_limit", "download_mbps" to it.groupValues[1], "upload_mbps" to "0") }
        Regex("""^(?:hey harbor,? )?(?:can you |could you |please |pls )?(?:play|watch|put on|start|stream|cast)\s+(.+?)(?:\s+(?:on|in|to)\s+(?:the |my )?(tv|television|chromecast|big screen|[a-z ]{2,20}tv))?\s*(?:please)?[?.!]*$""", RegexOption.IGNORE_CASE)
            .find(msg.trim())?.let { mt ->
                val dev = mt.groupValues[2].ifBlank { if (m.startsWith("cast")) "tv" else "" }
                return t("play", "title" to mt.groupValues[1].removeSuffix(" movie").trim(), "device" to dev)
            }
        return when {
            m.startsWith("magnet:") || m.startsWith("http") -> t("add_download", "link" to msg.trim())
            "pause" in m -> t("pause_downloads", "target" to after("pause").removePrefix("all").ifBlank { "all" })
            "resume" in m || "unpause" in m -> t("resume_downloads", "target" to after("resume").ifBlank { "all" })
            "turtle" in m || "slow mode" in m -> t("slow_mode", "on" to (if ("off" in m) "false" else "true"))
            "unlimited" in m -> t("set_speed_limit", "download_mbps" to "0", "upload_mbps" to "0")
            "restart" in m -> t("restart_service", "name" to after("restart").split(' ').firstOrNull().orEmpty())
            listOf("cpu", "ram", "memory", "temperature", "health", "server", "disk", "storage", "swap").any { it in m } -> t("server_status")
            listOf("downloading", "download status", "torrent", "progress", "speed").any { it in m } -> t("downloads_status")
            "poster" in m || "metadata" in m || "fix" in m || "wrong name" in m || "organi" in m -> t("fix_library")
            "missing" in m -> t("missing_media")
            "upcoming" in m || "this week" in m || "coming" in m -> t("upcoming")
            "continue" in m || "resume watching" in m || "was watching" in m -> t("continue_watching")
            "suggest" in m || "recommend" in m || "what should i watch" in m || "watch tonight" in m ->
                t("suggest", "genre" to listOf("action", "comedy", "horror", "thriller", "drama", "romance", "sci-fi", "animation", "crime", "family").firstOrNull { it in m }.orEmpty())
            listOf("movies", "films", "shows", "series").any { it in m } && (Regex("(19|20)\\d\\d").containsMatchIn(m) || genres.any { it in m }) -> t("smart_search", "query" to msg)
            m.startsWith("request ") || m.startsWith("download ") -> t("request", "title" to msg.substringAfter(' '))
            "pending" in m -> t("pending_requests")
            m.startsWith("find ") || m.startsWith("search ") -> t("find_new", "query" to msg.substringAfter(' '))
            else -> null
        }
    }
}
