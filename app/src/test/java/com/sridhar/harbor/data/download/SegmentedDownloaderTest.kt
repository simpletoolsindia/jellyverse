package com.sridhar.harbor.data.download

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class SegmentedDownloaderTest {
    private val server = MockWebServer()
    private val data = Random(7).nextBytes(40 shl 20)
    private val ranges = AtomicInteger()

    @After fun tearDown() = server.shutdown()

    private fun serve(slowFirstPart: Boolean) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val r = request.getHeader("Range") ?: return MockResponse().setBody(Buffer().write(data))
                val (a, b) = r.removePrefix("bytes=").split('-').map { it.toLong() }
                val end = minOf(b, data.size - 1L)
                if (a > 0) ranges.incrementAndGet()
                val res = MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes $a-$end/${data.size}")
                    .setBody(Buffer().write(data, a.toInt(), (end - a + 1).toInt()))
                // The first part trickles (a slow connection) so the others have to steal from it.
                if (slowFirstPart && a == 0L && end > 0) res.throttleBody(256 * 1024, 100, TimeUnit.MILLISECONDS)
                return res
            }
        }
        server.start()
    }

    private fun download(dest: File): Boolean = runBlocking {
        val dl = SegmentedDownloader(mockk(relaxed = true), OkHttpClient())
        val result = CompletableDeferred<Boolean>()
        dl.enqueue("k", "t", server.url("/file.bin").toString(), dest) { result.complete(it) }
        withTimeout(60_000) { result.await() }
    }

    @Test fun downloadsInPartsAndReassemblesExactly() {
        serve(slowFirstPart = false)
        val dest = File.createTempFile("jvdl", ".bin").apply { delete() }
        assertThat(download(dest)).isTrue()
        assertThat(dest.readBytes().contentEquals(data)).isTrue()
        assertThat(File(dest.path + ".part.meta").exists()).isFalse()
    }

    @Test fun fastPartsStealFromTheSlowOne() {
        serve(slowFirstPart = true)
        val dest = File.createTempFile("jvdl", ".bin").apply { delete() }
        assertThat(download(dest)).isTrue()
        assertThat(dest.readBytes().contentEquals(data)).isTrue()
        // 8 initial parts → 7 non-zero ranges; anything more is work stealing.
        assertThat(ranges.get()).isGreaterThan(7)
    }
}
