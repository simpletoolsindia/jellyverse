package com.sridhar.harbor.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sridhar.harbor.ui.ai.PetArt
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Not a test – renders the Shih Tzu logo (PetArt) into the raster brand assets: Play icon, TV launcher banner,
 * Play TV banner and feature graphics. Only runs with `-e logo export`; files land in the app's external
 * files dir under "logo".
 */
@RunWith(AndroidJUnit4::class)
class LogoExport {
    private val out by lazy {
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "logo").apply { mkdirs() }
    }

    private fun bg(c: Canvas, w: Float, h: Float, glowX: Float, glowY: Float, glowR: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w, h, 0xFF141A2B.toInt(), 0xFF17284A.toInt(), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(glowX, glowY, glowR, 0x33FFC27A, 0x00FFC27A, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h, p)
    }

    private fun tile(c: Canvas, l: Float, t: Float, size: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(l, t, l + size, t + size, 0xFF27324F.toInt(), 0xFF101828.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(l, t, l + size, t + size), size * 0.26f, size * 0.26f, p)
        p.shader = RadialGradient(l + size / 2, t + size * .42f, size * .46f, 0x40FFD9A8, 0x00FFD9A8, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(l, t, l + size, t + size), size * 0.26f, size * 0.26f, p)
        val box = size * 0.86f
        c.save(); c.translate(l + (size - box) / 2, t + (size - box) / 2 - size * 0.02f); PetArt.draw(c, box, tile = false); c.restore()
    }

    private fun banner(w: Int, h: Int, name: String, jpg: Boolean) {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        val tileSize = h * 0.56f; val tl = w * 0.08f; val tt = (h - tileSize) / 2
        bg(c, w.toFloat(), h.toFloat(), tl + tileSize / 2, h / 2f, h * 0.8f)
        tile(c, tl, tt, tileSize)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFFFFFFF.toInt(); p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); p.textSize = h * 0.17f
        val tx = tl + tileSize + w * 0.055f
        c.drawText("JellyVerse", tx, h * 0.50f, p)
        p.color = 0xFFB8C0D0.toInt(); p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); p.textSize = h * 0.075f
        c.drawText("Your whole media universe", tx, h * 0.64f, p)
        File(out, name).outputStream().use { b.compress(if (jpg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 95, it) }
    }

    @Test fun export() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("logo") == "export")
        // Play icon: full-bleed square (Play rounds it)
        Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also { b ->
            val c = Canvas(b)
            bg(c, 512f, 512f, 256f, 220f, 300f)
            val box = 430f
            c.save(); c.translate((512 - box) / 2, (512 - box) / 2 - 6f); PetArt.draw(c, box, tile = false); c.restore()
            File(out, "icon.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        banner(640, 360, "tv_banner.png", jpg = false)
        banner(1280, 720, "tvBanner.jpg", jpg = true)
        banner(1024, 500, "featureGraphic.jpg", jpg = true)
    }
}
