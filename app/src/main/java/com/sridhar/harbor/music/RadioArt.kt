package com.sridhar.harbor.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import java.io.ByteArrayOutputStream

/**
 * Cover art for things that have none – live stations and radio recordings – so the media notification, lock screen
 * and car display get a colourful card (Android tints its media player from the artwork) instead of a blank square.
 * Colours are derived from the name, so each station keeps its own look. No text: Android draws the title over it.
 */
object RadioArt {
    private val cache = LruCache<String, ByteArray>(12)

    fun png(name: String, recording: Boolean): ByteArray {
        val key = "$recording|$name"
        cache.get(key)?.let { return it }
        val px = 384
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val h = (name.hashCode() and 0x7fffffff) % 360
        val a = android.graphics.Color.HSVToColor(floatArrayOf(h.toFloat(), .62f, .55f))
        val z = android.graphics.Color.HSVToColor(floatArrayOf(((h + 48) % 360).toFloat(), .75f, .22f))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, px.toFloat(), px.toFloat(), a, z, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, px.toFloat(), px.toFloat(), p)
        // Soft light bloom, top-left.
        p.shader = RadialGradient(px * .25f, px * .2f, px * .7f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, px.toFloat(), px.toFloat(), p)
        p.shader = null
        // Broadcast rings (or a record dot for recordings).
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = px * .028f
        val cx = px * .70f; val cy = px * .40f
        for (i in 1..3) {
            p.color = android.graphics.Color.argb(200 - i * 45, 255, 255, 255)
            val r = px * .09f * i * 1.25f
            c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -60f, 120f, false, p)
            c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 120f, 120f, false, p)
        }
        p.style = Paint.Style.FILL
        p.color = if (recording) 0xFFE8505B.toInt() else 0xFFFFFFFF.toInt()
        c.drawCircle(cx, cy, px * .06f, p)
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, out)
        b.recycle()
        return out.toByteArray().also { cache.put(key, it) }
    }
}
