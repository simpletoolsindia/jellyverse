package com.sridhar.harbor.radio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/**
 * "Jelly", JellyVerse's assistant mascot, as a bitmap for notifications (Android doesn't animate notification
 * images; the in-app version, [com.sridhar.harbor.ui.ai.JellyBuddy], blinks, bobs and waves its tentacles).
 */
object Avatar {
    private var cached: Bitmap? = null

    fun bitmap(ctx: Context): Bitmap = cached ?: draw((96 * ctx.resources.displayMetrics.density).toInt()).also { cached = it }

    fun draw(px: Int): Bitmap {
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val s = px / 100f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Round tile
        p.shader = LinearGradient(0f, 0f, px.toFloat(), px.toFloat(), 0xFF1E1B4B.toInt(), 0xFF0F172A.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(50 * s, 50 * s, 50 * s, p)
        // Tentacles
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 5 * s; p.strokeCap = Paint.Cap.ROUND; p.color = 0xFF8EC5FF.toInt()
        for (x in listOf(36f, 50f, 64f)) {
            val path = Path().apply { moveTo(x * s, 58 * s); cubicTo((x - 6) * s, 68 * s, (x + 6) * s, 74 * s, x * s, 84 * s) }
            c.drawPath(path, p)
        }
        // Bell
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 18 * s, 0f, 62 * s, 0xFF8B5CF6.toInt(), 0xFF38BDF8.toInt(), Shader.TileMode.CLAMP)
        c.drawArc(RectF(22 * s, 18 * s, 78 * s, 86 * s), 180f, 180f, true, p)
        c.drawRect(22 * s, 51.5f * s, 78 * s, 60 * s, p)
        // Shine
        p.shader = null; p.color = 0x55FFFFFF
        c.drawOval(RectF(32 * s, 24 * s, 46 * s, 32 * s), p)
        // Eyes + smile
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(41 * s, 44 * s, 6 * s, p); c.drawCircle(59 * s, 44 * s, 6 * s, p)
        p.color = 0xFF0F172A.toInt()
        c.drawCircle(42 * s, 45 * s, 3 * s, p); c.drawCircle(60 * s, 45 * s, 3 * s, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.6f * s
        c.drawArc(RectF(44 * s, 47 * s, 56 * s, 56 * s), 20f, 140f, false, p)
        return b
    }
}
