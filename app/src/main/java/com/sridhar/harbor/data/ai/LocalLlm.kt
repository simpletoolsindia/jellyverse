package com.sridhar.harbor.data.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.PromptTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Qwen2.5-0.5B-Instruct (int8), converted for MediaPipe by Google's LiteRT community. */
object QwenModel {
    const val NAME = "Qwen2.5 0.5B Instruct"
    const val FILE = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    const val URL = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/$FILE"
    const val SIZE_BYTES = 546_000_000L
    const val MAX_TOKENS = 1280
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
 * Runs Qwen fully on the phone. No prompt leaves the device.
 * Prompts are formatted with Qwen's ChatML template by the caller; MediaPipe's own templates are disabled.
 */
class LocalLlm(private val context: Context) {
    private val dm = context.getSystemService(DownloadManager::class.java)
    private val dir = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }
    val modelFile = File(dir, QwenModel.FILE)
    private val prefs = context.getSharedPreferences("harbor_ai", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<ModelState>(if (modelFile.exists() && modelFile.length() > 100_000_000) ModelState.Ready else ModelState.Missing)
    val state: StateFlow<ModelState> = _state

    private var engine: LlmInference? = null
    private val lock = Mutex()

    fun startDownload() {
        if (_state.value is ModelState.Downloading) return
        modelFile.delete()
        val id = dm.enqueue(DownloadManager.Request(Uri.parse(QwenModel.URL))
            .setTitle("JellyVerse AI model")
            .setDescription(QwenModel.NAME)
            .setDestinationUri(Uri.fromFile(modelFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED))
        prefs.edit().putLong("download_id", id).apply()
        _state.value = ModelState.Downloading(0f)
    }

    /** Poll DownloadManager; call periodically from UI while downloading. */
    fun refreshDownload() {
        val id = prefs.getLong("download_id", -1)
        if (id < 0) return
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (c == null || !c.moveToFirst()) return
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 } ?: QwenModel.SIZE_BYTES
            _state.value = when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> { prefs.edit().remove("download_id").apply(); ModelState.Ready }
                DownloadManager.STATUS_FAILED -> { prefs.edit().remove("download_id").apply(); ModelState.Failed("Download failed – check your connection") }
                else -> ModelState.Downloading(done.toFloat() / total)
            }
        }
    }

    init { if (prefs.getLong("download_id", -1) >= 0) refreshDownload() }

    fun delete() {
        engine?.close(); engine = null
        modelFile.delete()
        _state.value = ModelState.Missing
    }

    private suspend fun ensureLoaded(): LlmInference = lock.withLock {
        engine?.let { return it }
        if (!modelFile.exists()) throw IllegalStateException("Download the AI model first")
        _state.value = ModelState.Loading
        withContext(Dispatchers.Default) {
            runCatching {
                LlmInference.createFromOptions(context, LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(QwenModel.MAX_TOKENS)
                    .setMaxTopK(40)
                    .build())
            }.onFailure { _state.value = ModelState.Failed(it.message ?: "Model failed to load") }.getOrThrow()
        }.also { engine = it; _state.value = ModelState.Loaded }
    }

    fun tokens(text: String): Int = engine?.sizeInTokens(text) ?: (text.length / 3)

    /**
     * Generate a completion for a raw ChatML prompt. [onPartial] receives the accumulated text as it streams.
     * Low temperature keeps tool calls and JSON well-formed on a 0.5B model.
     */
    suspend fun complete(prompt: String, temperature: Float = 0.2f, onPartial: (String) -> Unit = {}): String {
        val llm = ensureLoaded()
        return withContext(Dispatchers.Default) {
            val session = LlmInferenceSession.createFromOptions(llm, LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(temperature).setTopK(20).setTopP(0.9f).setRandomSeed(7)
                .setPromptTemplates(PromptTemplates.builder()
                    .setUserPrefix("").setUserSuffix("").setModelPrefix("").setModelSuffix("")
                    .setSystemPrefix("").setSystemSuffix("").build())
                .build())
            try {
                session.addQueryChunk(prompt)
                val sb = StringBuilder()
                suspendCancellableCoroutine { cont ->
                    val future = session.generateResponseAsync { partial, done ->
                        sb.append(partial)
                        onPartial(sb.toString())
                        // Stop early once a tool call closes or the turn ends – saves time on-device.
                        val t = sb.toString()
                        if (!done && (t.contains("</tool_call>") || t.contains("<|im_end|>"))) runCatching { session.cancelGenerateResponseAsync() }
                        if (done && cont.isActive) cont.resume(sb.toString())
                    }
                    future.addListener({
                        if (cont.isActive) runCatching { future.get() }.fold({ cont.resume(sb.toString()) }, { e ->
                            if (sb.isNotEmpty()) cont.resume(sb.toString()) else cont.resumeWithException(e)
                        })
                    }, Runnable::run)
                    cont.invokeOnCancellation { runCatching { session.cancelGenerateResponseAsync() } }
                }
            } finally {
                runCatching { session.close() }
            }
        }.substringBefore("<|im_end|>").trim()
    }
}
