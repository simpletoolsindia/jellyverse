package com.sridhar.harbor.testutil

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

/** Saves a PNG of the screen for failed tests to the app's external files dir (…/files/test-failures). */
class ScreenshotOnFailure : TestWatcher() {
    override fun failed(e: Throwable?, description: Description) = capture("${description.testClass.simpleName}_${description.methodName}")

    companion object {
        fun capture(name: String) {
            val inst = InstrumentationRegistry.getInstrumentation()
            val dir = File(inst.targetContext.getExternalFilesDir(null), "test-failures").apply { mkdirs() }
            runCatching {
                inst.uiAutomation.takeScreenshot()?.let { bmp ->
                    File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                }
            }
        }
    }
}
