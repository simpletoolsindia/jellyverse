package com.sridhar.harbor.data.ai

import kotlinx.coroutines.launch
import com.sridhar.harbor.data.download.DlStatus
import kotlinx.coroutines.isActive
import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device models, all published by Google's LiteRT community in the `.litertlm` format (no login needed) and
 * run by LiteRT-LM on the CPU. Each file carries its own chat template, so every family (Qwen, Gemma, Phi, …)
 * gets its native prompt format.
 */
enum class LlmModel(
    val id: String, val displayName: String, val maker: String, val params: String, val emoji: String,
    val repo: String, val file: String, val sizeMb: Int, val minRamGb: Int, val blurb: String, val thinks: Boolean = false,
) {
    // ---- Tiny: any phone ----
    SmolLM2Tiny("smollm2-135m", "SmolLM2 · 135M", "Hugging Face", "135 M", "🐣", "SmolLM2-135M-Instruct", "SmolLM2_135M_Instruct.litertlm",
        143, 2, "The smallest here. Instant replies; best for simple commands."),
    SmolLM2("smollm2-360m", "SmolLM2 · 360M", "Hugging Face", "360 M", "🤗", "SmolLM2-360M-Instruct", "SmolLM2_360M_instruct.litertlm",
        374, 2, "Trained for on-device use. Snappy on older phones."),
    Qwen3Small("qwen3-0.6b", "Qwen 3 · 0.6B", "Alibaba", "0.6 B", "✨", "Qwen3-0.6B", "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
        345, 3, "Recommended. Latest Qwen, tiny and good at calling tools."),
    Qwen2Small("qwen2-0.5b", "Qwen 2 · 0.5B Instruct", "Alibaba", "0.5 B", "🚀", "Qwen2-0.5B-Instruct", "Qwen2_0.5B_Instruct.litertlm",
        647, 3, "Tiny and fast. Runs on almost any phone."),
    MiniCpm1B("minicpm5-1b", "MiniCPM 5 · 1B", "OpenBMB", "1 B", "🐝", "MiniCPM5-1B", "minicpm_wi4b32_wi8_afp32.litertlm",
        793, 4, "Built for phones: strong answers for its size, quick decode."),
    Olmo1B("olmo2-1b", "OLMo 2 · 1B Instruct", "Ai2", "1 B", "🔬", "OLMo-2-1B-Instruct", "OLMo-2-1B-Instruct_q4_block32_ekv4096.litertlm",
        931, 4, "Fully open model – weights, data and training all public."),
    // ---- Mid-range phones ----
    Qwen3("qwen3-1.7b", "Qwen 3 · 1.7B", "Alibaba", "1.7 B", "🌟", "Qwen3-1.7B", "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
        977, 6, "Smarter tool use than 0.6B and still quick. Great all-rounder."),
    MiniCpm2B("minicpm5-2b", "MiniCPM 5 · 2B", "OpenBMB", "2 B", "🐝", "MiniCPM5-2B", "MiniCPM5-2B_int4.litertlm",
        1554, 6, "Bigger MiniCPM – noticeably better answers, 4-bit to stay light."),
    Qwen25("qwen2.5-1.5b", "Qwen 2.5 · 1.5B Instruct", "Alibaba", "1.5 B", "⚡", "Qwen2.5-1.5B-Instruct", "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        1598, 6, "Proven sweet spot for mid-range phones."),
    VibeThinker("vibethinker-1.5b", "VibeThinker · 1.5B", "Weibo", "1.5 B", "🧠", "VibeThinker-1.5B", "VibeThinker-1.5B.litertlm",
        1568, 6, "Reasoning specialist: thinks before it answers (slower).", thinks = true),
    DeepSeekR1("deepseek-r1-1.5b", "DeepSeek-R1 Distill · 1.5B", "DeepSeek", "1.5 B", "🐋", "DeepSeek-R1-Distill-Qwen-1.5B",
        "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm", 1833, 6, "Step-by-step reasoning in a small package (slower).", thinks = true),
    SmolLM3("smollm3-3b", "SmolLM3 · 3B", "Hugging Face", "3 B", "🤗", "SmolLM3-3B", "SmolLM3-3B_q4_block32_ekv4096.litertlm",
        2002, 8, "Newest SmolLM – multilingual, 4-bit for speed."),
    Qwen35("qwen3.5-2b", "Qwen 3.5 · 2B", "Alibaba", "2 B", "🔮", "Qwen3.5-2B", "Qwen3.5-2B_int8.litertlm",
        2117, 8, "The mid-size Qwen 3.5. Strong multilingual chat."),
    // ---- Flagship phones ----
    Ministral3B("ministral3-3b", "Ministral 3 · 3B Instruct", "Mistral AI", "3 B", "🌬️", "Ministral-3-3B-Instruct-2512",
        "Ministral-3-3B-Instruct-2512_q4_block32_ekv4096.litertlm", 2341, 8, "Mistral's edge model: crisp, well-structured answers."),
    JanNano("jan-nano", "Jan Nano · 4B", "Menlo", "4 B", "🛠️", "Jan-nano", "model.litertlm",
        2474, 8, "Tuned for agents and tool calling."),
    Gemma4E2B("gemma4-e2b", "Gemma 4 · E2B Instruct", "Google", "2.3 B effective", "💎", "gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm",
        2588, 8, "Gemma 4's phone tier. 140+ languages, best all-round quality."),
    Qwen3Instruct4B("qwen3-4b-2507", "Qwen 3 · 4B Instruct 2507", "Alibaba", "4 B", "⚒️", "Qwen3-4B-Instruct-2507", "qwen3_4b_instruct_2507_mixed_int4.litertlm",
        2659, 8, "Refreshed Qwen3 4B – big gains and no thinking preamble."),
    Qwen3Think4B("qwen3-4b", "Qwen 3 · 4B", "Alibaba", "4 B", "🧩", "Qwen3-4B", "qwen3_4b_mixed_int4.litertlm",
        2659, 8, "The original Qwen3 4B with hybrid thinking."),
    Gemma4E4B("gemma4-e4b", "Gemma 4 · E4B Instruct", "Google", "4 B effective", "💎", "gemma-4-E4B-it-litert-lm", "gemma-4-E4B-it.litertlm",
        3660, 12, "The larger Gemma 4 edge model for flagship phones."),
    Phi4Mini("phi4-mini", "Phi-4 · Mini Instruct", "Microsoft", "3.8 B", "🎓", "Phi-4-mini-instruct", "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        3910, 12, "Excellent at maths and reasoning for its size."),
    DeepSeekR17B("deepseek-r1-7b", "DeepSeek-R1 Distill · 7B", "DeepSeek", "7 B", "🐋", "DeepSeek-R1-Distill-Qwen-7B",
        "DeepSeek-R1-Distill-Qwen-7B_q4_block32_ekv4096.litertlm", 4532, 12, "Deep reasoning. Flagship only; expect slow tokens.", thinks = true),
    Qwen3Big("qwen3-8b", "Qwen 3 · 8B", "Alibaba", "8 B", "👑", "Qwen3-8B", "qwen3_8b_mixed_int4.litertlm",
        4887, 16, "The largest here. Best answers if your phone can hold it."),
    ;

    val sizeBytes: Long get() = sizeMb * 1_000_000L
    val url: String get() = "https://huggingface.co/litert-community/$repo/resolve/main/$file"
    /** Some repos ship a generic "model.litertlm": store under a unique name so two can live side by side. */
    val localFile: String get() = if (file == "model.litertlm") "$id.litertlm" else file

    companion object {
        val DEFAULT = Qwen3Small
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

sealed interface ModelState {
    data object Missing : ModelState
    data class Downloading(val fraction: Float) : ModelState
    data object Ready : ModelState          // on disk, not loaded
    data object Loading : ModelState
    data object Loaded : ModelState
    data class Failed(val message: String) : ModelState
}

/**
 * Runs the chosen model fully on the device – no prompt leaves it. Callers still write prompts in ChatML
 * (`<|im_start|>role … <|im_end|>`); they are turned into structured messages here and rendered with the model's
 * own template, so the same prompts work for Qwen, Gemma, Phi and the rest.
 */
class LocalLlm(private val context: Context, private val downloader: com.sridhar.harbor.data.download.SegmentedDownloader) {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)
    private val dir = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("harbor_ai", Context.MODE_PRIVATE)

    private val _model = MutableStateFlow(LlmModel.of(prefs.getString("model", null)))
    val model: StateFlow<LlmModel> = _model.asStateFlow()
    private fun fileOf(m: LlmModel) = File(dir, m.localFile)
    val modelFile: File get() = fileOf(_model.value)

    /** Device RAM in GB, to flag models that won't fit. */
    val deviceRamGb: Int = context.getSystemService(ActivityManager::class.java).let { am ->
        ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem.let { ((it + (1L shl 29)) shr 30).toInt() }
    }

    private fun onDisk(m: LlmModel) = fileOf(m).let { it.exists() && it.length() > m.sizeBytes * 9 / 10 }
    fun isDownloaded(m: LlmModel) = onDisk(m)

    private val _state = MutableStateFlow<ModelState>(if (onDisk(_model.value)) ModelState.Ready else ModelState.Missing)
    val state: StateFlow<ModelState> = _state

    /** Models on disk, for the catalogue's Installed badges. */
    private val _downloaded = MutableStateFlow(LlmModel.entries.filter { File(dir, it.localFile).let { f -> f.exists() && f.length() > it.sizeBytes * 9 / 10 } }.toSet())
    val downloaded: StateFlow<Set<LlmModel>> = _downloaded.asStateFlow()

    private var engine: Engine? = null
    private val lock = Mutex()

    // ---- keeping the device cool: the model only stays in memory while it's useful ----
    /** Replies in progress (a loaded model must not be closed under them). */
    private val active = java.util.concurrent.atomic.AtomicInteger(0)
    /** Open conversations, so leaving the app can stop a reply mid-way. */
    private val running = java.util.concurrent.ConcurrentHashMap.newKeySet<com.google.ai.edge.litertlm.Conversation>()
    @Volatile private var inBackground = false
    private var unloadJob: kotlinx.coroutines.Job? = null

    private companion object {
        const val IDLE_UNLOAD_MS = 3 * 60_000L      // unused for 3 min in the app → free it
        const val BACKGROUND_GRACE_MS = 10_000L     // left the app: quick app switches don't pay a reload
    }

    /** All app screens stopped (home button, recents, closed): stop any reply and free the model shortly after. */
    fun onAppBackground() { inBackground = true; scheduleUnload(BACKGROUND_GRACE_MS) }

    fun onAppForeground() {
        inBackground = false
        unloadJob?.cancel()
        if (engine != null && active.get() == 0) scheduleUnload(IDLE_UNLOAD_MS)
    }

    /** The system is short of memory: let go of the model now (it reloads on the next question). */
    fun onLowMemory() { scheduleUnload(0) }

    private fun scheduleUnload(afterMs: Long) {
        unloadJob?.cancel()
        unloadJob = scope.launch { kotlinx.coroutines.delay(afterMs); unload() }
    }

    /**
     * Frees the model's memory and CPU. In the background a reply still running is stopped first (its partial text
     * is kept as the answer); in the app, an active reply is never interrupted – we just try again later.
     */
    private suspend fun unload() {
        if (active.get() > 0) {
            if (!inBackground) return
            running.forEach { runCatching { it.cancelProcess() } }
            kotlinx.coroutines.withTimeoutOrNull(8_000) { while (active.get() > 0) kotlinx.coroutines.delay(100) }
            if (active.get() > 0) { scheduleUnload(5_000); return }
        }
        lock.withLock {
            if (active.get() > 0) return
            val e = engine ?: return
            engine = null
            withContext(Dispatchers.Default) { runCatching { e.close() } }
            if (_state.value == ModelState.Loaded) _state.value = ModelState.Ready
            if (com.sridhar.harbor.BuildConfig.DEBUG) android.util.Log.i("LocalLlm", "model unloaded (background=$inBackground)")
        }
    }

    init {
        // 2.9 and earlier used a MediaPipe .task file that LiteRT-LM can't load – free the space.
        File(dir, "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task").delete()
        prefs.edit().remove("download_id").apply()
        // The current model's state follows its download (progress, done, failed).
        scope.launch {
            downloader.state.collect { all ->
                val m = _model.value
                all[key(m)]?.let { st ->
                    _state.value = when (st.status) {
                        DlStatus.Queued, DlStatus.Running -> ModelState.Downloading(st.fraction)
                        DlStatus.Done -> ModelState.Ready
                        DlStatus.Failed -> ModelState.Failed("Download failed – check your connection")
                        DlStatus.Cancelled -> if (onDisk(m)) ModelState.Ready else ModelState.Missing
                    }
                }
            }
        }
    }

    private fun key(m: LlmModel) = "llm:${m.id}"

    /** Download progress of every model being fetched (several can download at once). */
    val progress: StateFlow<Map<String, com.sridhar.harbor.data.download.DlState>> get() = downloader.state
    fun downloadFraction(m: LlmModel): Float? = downloader.state.value[key(m)]?.takeIf { it.status == DlStatus.Running || it.status == DlStatus.Queued }?.fraction
    fun keyOf(m: LlmModel) = key(m)

    /** Switch models. A downloaded one is ready at once; others show the Download button. */
    fun select(m: LlmModel) {
        if (m == _model.value) return
        prefs.edit().putString("model", m.id).apply()
        engine?.close(); engine = null
        _model.value = m
        _state.value = when {
            onDisk(m) -> ModelState.Ready
            downloader.isActive(key(m)) -> ModelState.Downloading(downloadFraction(m) ?: 0f)
            else -> ModelState.Missing
        }
    }

    /** Download the current model. */
    fun startDownload() = download(_model.value)

    /** Download any model in parallel with others; the first one to finish becomes current if none is usable yet. */
    fun download(m: LlmModel) {
        if (downloader.isActive(key(m)) || onDisk(m)) return
        downloader.enqueue(key(m), m.displayName, m.url, fileOf(m)) { ok ->
            if (!ok) return@enqueue
            _downloaded.value = _downloaded.value + m
            if (!onDisk(_model.value)) select(m)
            if (m == _model.value) _state.value = ModelState.Ready
        }
        if (m == _model.value) _state.value = ModelState.Downloading(0f)
    }

    fun cancelDownload(m: LlmModel) = downloader.cancel(key(m), fileOf(m))

    /** Resume model downloads interrupted by the app closing. */
    fun resumeDownloads() = LlmModel.entries.filter { !onDisk(it) && downloader.hasPartial(fileOf(it)) }.forEach(::download)

    /** Kept for callers that poll; progress now arrives through [progress]. */
    fun refreshDownload() {}

    /** Deletes the current model's file (other downloaded models stay). */
    fun delete() = delete(_model.value)

    fun delete(m: LlmModel) {
        downloader.cancel(key(m), fileOf(m))
        if (m == _model.value) { engine?.close(); engine = null; _state.value = ModelState.Missing }
        fileOf(m).delete()
        _downloaded.value = LlmModel.entries.filter(::onDisk).toSet()
    }


    private suspend fun ensureLoaded(): Engine = lock.withLock {
        engine?.let { return it }
        if (!modelFile.exists()) throw IllegalStateException("Download the AI model first")
        _state.value = ModelState.Loading
        withContext(Dispatchers.Default) {
            runCatching {
                Engine(EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU(),
                    maxNumTokens = 4096,
                    cacheDir = context.cacheDir.absolutePath,
                )).also { it.initialize() }
            }.onFailure { _state.value = ModelState.Failed(it.message ?: "Model failed to load") }.getOrThrow()
        }.also { engine = it; _state.value = ModelState.Loaded }
    }

    fun tokens(text: String): Int = text.length / 3

    private data class Turn(val role: String, val text: String)

    /** `<|im_start|>role\ntext<|im_end|>` blocks → turns (a trailing open assistant turn is dropped). */
    private fun parseChatMl(prompt: String): List<Turn> =
        Regex("<\\|im_start\\|>(system|user|assistant)\\n(.*?)(?:<\\|im_end\\|>|$)", RegexOption.DOT_MATCHES_ALL)
            .findAll(prompt).map { Turn(it.groupValues[1], it.groupValues[2].trim()) }
            .filterNot { it.role == "assistant" && it.text.isEmpty() }.toList()

    /**
     * Generate a reply to a ChatML prompt. [onPartial] receives the accumulated text as it streams. Low temperature
     * keeps tool calls and JSON well-formed on small models; "thinking" is switched off (or stripped) so answers
     * arrive quickly.
     */
    suspend fun complete(prompt: String, temperature: Float = 0.2f, onPartial: (String) -> Unit = {}): String {
        // Nothing runs the model while the app is in the background – that's what heats the phone.
        if (inBackground) throw IllegalStateException("AI paused while JellyVerse is in the background")
        active.incrementAndGet()
        unloadJob?.cancel()
        try { return generate(prompt, temperature, onPartial) }
        finally { if (active.decrementAndGet() == 0) scheduleUnload(if (inBackground) 0 else IDLE_UNLOAD_MS) }
    }

    private suspend fun generate(prompt: String, temperature: Float, onPartial: (String) -> Unit): String {
        val llm = ensureLoaded()
        val turns = parseChatMl(prompt).ifEmpty { listOf(Turn("user", prompt)) }
        val system = turns.firstOrNull { it.role == "system" }?.text
        val rest = turns.filter { it.role != "system" }
        val lastIdx = rest.indexOfLast { it.role == "user" }
        val last = rest.getOrNull(lastIdx) ?: Turn("user", prompt)
        val history = (if (lastIdx > 0) rest.subList(0, lastIdx) else emptyList()).map {
            if (it.role == "user") Message.user(it.text) else Message.model(it.text)
        }
        val m = _model.value
        return withContext(Dispatchers.Default) {
            val conv = llm.createConversation(ConversationConfig(
                systemInstruction = system?.let { Contents.of(it) },
                initialMessages = history,
                samplerConfig = SamplerConfig(20, 0.9, temperature.toDouble(), 7),
                thinkingConfig = ThinkingConfig(false),
            ))
            running += conv
            val sb = StringBuilder()
            var stoppedEarly = false   // we cancelled on purpose: the text so far is the answer
            try {
                conv.sendMessageAsync(last.text).collect { msg ->
                    msg.contents.contents.filterIsInstance<Content.Text>().forEach { sb.append(it.text) }
                    if (com.sridhar.harbor.BuildConfig.DEBUG) android.util.Log.v("LocalLlm", "chunk → ${sb.length} chars")
                    val visible = clean(sb.toString(), m)
                    if (visible.isNotEmpty()) onPartial(visible)
                    // Stop early once a tool call closes – saves time on-device.
                    if (!stoppedEarly && sb.contains("</tool_call>")) { stoppedEarly = true; runCatching { conv.cancelProcess() } }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // LiteRT-LM ends the stream with a CancellationException ("Task cancelled") after cancelProcess();
                // only a cancellation of *our* caller must propagate.
                if (!coroutineContext.isActive) { runCatching { conv.cancelProcess() }; throw e }
                if (!stoppedEarly && sb.isEmpty()) throw IllegalStateException(e.message ?: "Generation stopped")
            } catch (e: Exception) {
                if (sb.isEmpty()) throw e   // partial output after a stop is still a usable answer
            } finally {
                running -= conv
                runCatching { conv.close() }
            }
            clean(sb.toString(), m)
        }
    }

    /** Drop reasoning blocks and stray end-of-turn markers. */
    private fun clean(text: String, m: LlmModel): String {
        var t = text.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
        if (m.thinks || t.contains("<think>")) t = when {
            t.contains("</think>") -> t.substringAfter("</think>")
            t.contains("<think>") -> ""
            else -> t
        }
        return t.substringBefore("<|im_end|>").substringBefore("<end_of_turn>").trim()
    }
}
