package com.sridhar.harbor.tools

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sridhar.harbor.data.ai.LocalLlm
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Manual probe for the on-device model: `adb shell am instrument -w -e class com.sridhar.harbor.tools.LlmProbe
 * com.sridhar.jellyverse.test/androidx.test.runner.AndroidJUnitRunner`, then read `adb logcat -s LlmProbe`.
 */
@RunWith(AndroidJUnit4::class)
class LlmProbe {
    @Test fun probe() { runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val llm = LocalLlm(ctx)
        Log.i("LlmProbe", "model=${llm.model.value.displayName} state=${llm.state.value}")
        val t0 = System.currentTimeMillis()
        var first = 0L
        val out = llm.complete("<|im_start|>system\nYou are a helpful assistant. Answer in one short sentence.<|im_end|>\n" +
            "<|im_start|>user\nWhat is the capital of France?<|im_end|>\n<|im_start|>assistant\n") { if (first == 0L) first = System.currentTimeMillis() - t0 }
        Log.i("LlmProbe", "first token after ${first} ms, total ${System.currentTimeMillis() - t0} ms, reply=[$out]")
    } }

    /** Same shape as the assistant: a long system prompt (≈1.5k tokens) before a short question. */
    @Test fun probeLong() { runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val llm = LocalLlm(ctx)
        val tools = (1..32).joinToString("\n") { "- tool_$it(query: string): looks up item $it in the user's media library and returns names and years" }
        val t0 = System.currentTimeMillis()
        var first = 0L
        val out = llm.complete("<|im_start|>system\nYou are a media assistant. To use a tool reply <tool_call>{...}</tool_call>. Tools:\n$tools<|im_end|>\n" +
            "<|im_start|>user\nhello who are you<|im_end|>\n<|im_start|>assistant\n") { if (first == 0L) first = System.currentTimeMillis() - t0 }
        Log.i("LlmProbe", "LONG: prompt ${tools.length} chars, first token after ${first} ms, total ${System.currentTimeMillis() - t0} ms, reply=[${out.take(200)}]")
    } }
}
