package com.sridhar.harbor.ui.ai

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/**
 * The app's face: a little Shih Tzu wearing headphones – cream face, long brown ear fur, two top-knot pigtails,
 * big sparkly eyes, rosy cheeks, status lights on one ear cup (homelab) and a play-button tag on the collar (media). Drawn in a 100-unit box with plain Android graphics,
 * so the same art serves the animated in-app mascot ([JellyBuddy]) and the notification icon.
 *
 * [open] eye openness (1 open, 0 closed) · [sway] pigtail wiggle (-1..1) · [bob] head bob in units ·
 * [tilt] head tilt in degrees · [tongue] 0..1 (panting while it thinks).
 */
object PetArt {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()

    private const val FUR = 0xFF9A5F2C.toInt()
    private const val FUR_DARK = 0xFF6E3F1A.toInt()
    private const val FUR_LIGHT = 0xFFC48A52.toInt()
    private const val CREAM = 0xFFF4EBDD.toInt()
    private const val WHITE = 0xFFFCF8F2.toInt()

    @Synchronized
    fun draw(c: Canvas, px: Float, open: Float = 1f, sway: Float = 0f, bob: Float = 0f, tilt: Float = 0f, tongue: Float = 0f, tile: Boolean = true) {
        c.save()
        val s = px / 100f
        c.scale(s, s)
        p.reset(); p.isAntiAlias = true
        // Round tile
        if (tile) {
            p.alpha = 255; p.shader = LinearGradient(0f, 0f, 100f, 100f, 0xFF27324F.toInt(), 0xFF101828.toInt(), Shader.TileMode.CLAMP)
            c.drawCircle(50f, 50f, 50f, p)
            p.alpha = 255; p.shader = RadialGradient(50f, 40f, 42f, 0x33FFD9A8, 0x00FFD9A8, Shader.TileMode.CLAMP)
            c.drawCircle(50f, 50f, 50f, p)
            p.shader = null
        }
        c.save()
        c.translate(0f, bob)
        c.rotate(tilt, 50f, 62f)

        // Long, wavy ear fur hanging down both sides (behind the head)
        for (side in listOf(-1f, 1f)) {
            val cx = 50f + side * 25f
            p.alpha = 255; p.shader = LinearGradient(0f, 34f, 0f, 90f, FUR, FUR_DARK, Shader.TileMode.CLAMP)
            path.reset()
            path.moveTo(cx - side * 6f, 36f)
            path.cubicTo(cx + side * 12f, 40f, cx + side * 16f, 70f, cx + side * 10f, 88f)
            // ragged bottom edge
            path.quadTo(cx + side * 7f, 84f, cx + side * 4f, 90f)
            path.quadTo(cx + side * 1f, 85f, cx - side * 3f, 89f)
            path.quadTo(cx - side * 5f, 83f, cx - side * 9f, 86f)
            path.cubicTo(cx - side * 12f, 70f, cx - side * 12f, 48f, cx - side * 6f, 36f)
            path.close()
            c.drawPath(path, p)
            p.shader = null
            // strands
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.1f; p.strokeCap = Paint.Cap.ROUND; p.color = FUR_LIGHT
            for (k in 0..2) {
                val x0 = cx + side * (k * 4f - 3f)
                path.reset(); path.moveTo(x0, 46f); path.quadTo(x0 + side * 4f, 64f, x0 + side * 2f, 82f); c.drawPath(path, p)
            }
            p.style = Paint.Style.FILL
        }

        // Headphones band over the head (media), drawn behind the pigtails
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = 4.2f
        p.alpha = 255; p.shader = LinearGradient(20f, 20f, 80f, 20f, 0xFF6E56CF.toInt(), 0xFF38BDF8.toInt(), Shader.TileMode.CLAMP)
        r.set(21f, 20f, 79f, 76f); c.drawArc(r, 200f, 140f, false, p)
        p.shader = null; p.style = Paint.Style.FILL

        // Two top-knot pigtails: fluffy sprays of hair fanning out from a dark hair tie; they wiggle
        for (side in listOf(-1f, 1f)) {
            val bx = 50f + side * 12f; val by = 31f
            for (k in 0..5) {
                val ang = Math.toRadians((-90.0 + side * (8.0 + k * 11.0)) + sway * 7.0 * side).toFloat()   // up and outward
                val len = 15f + (k % 3) * 3.5f
                val tx = bx + kotlin.math.cos(ang) * len; val ty = by + kotlin.math.sin(ang) * len
                val nx = -kotlin.math.sin(ang) * 2.2f; val ny = kotlin.math.cos(ang) * 2.2f
                path.reset()
                path.moveTo(bx + nx, by + ny)
                path.quadTo((bx + tx) / 2 + nx * 1.4f + side * 1.5f, (by + ty) / 2 + ny * 1.4f, tx, ty)
                path.quadTo((bx + tx) / 2 - nx * 0.6f, (by + ty) / 2 - ny * 0.6f, bx - nx, by - ny)
                path.close()
                p.alpha = 255   // a shader is drawn with the paint's alpha – don't inherit the translucent highlight
                p.alpha = 255; p.shader = LinearGradient(bx, by, tx, ty, if (k % 2 == 0) FUR else FUR_DARK, FUR_LIGHT, Shader.TileMode.CLAMP)
                c.drawPath(path, p)
            }
            p.shader = null
            p.color = 0xFF1E1E24.toInt(); r.set(bx - 3.6f, by - 2.2f, bx + 3.6f, by + 2.4f); c.drawOval(r, p)
            p.color = 0x55FFFFFF; r.set(bx - 2f, by - 1.6f, bx + 0.5f, by - 0.4f); c.drawOval(r, p)
        }

        // Head
        p.color = CREAM; r.set(23f, 28f, 77f, 82f); c.drawOval(r, p)
        // Brown cap and eye patches
        p.color = FUR; r.set(30f, 28f, 70f, 44f); c.drawOval(r, p)
        p.color = FUR; r.set(26f, 38f, 47f, 60f); c.drawOval(r, p)
        r.set(53f, 38f, 74f, 60f); c.drawOval(r, p)
        // White blaze down the forehead
        p.color = CREAM; r.set(44f, 28f, 56f, 58f); c.drawOval(r, p)
        // Fluffy white beard: a scalloped muzzle (the classic Shih Tzu "chrysanthemum" face)
        p.color = WHITE; r.set(33f, 55f, 67f, 79f); c.drawOval(r, p)
        for (k in 0..6) { val a = Math.toRadians(20.0 + k * 23.0); c.drawCircle(50f + kotlin.math.cos(a).toFloat() * 15f, 68f + kotlin.math.sin(a).toFloat() * 11f, 4.2f, p) }
        // Wispy fur around the face edge
        p.color = CREAM; p.style = Paint.Style.STROKE; p.strokeWidth = 1.1f; p.strokeCap = Paint.Cap.ROUND
        for (k in 0..4) {
            val y = 58f + k * 4f
            c.drawLine(31f - k * 0.3f, y, 27.5f - k * 0.6f, y + 2.5f, p)
            c.drawLine(69f + k * 0.3f, y, 72.5f + k * 0.6f, y + 2.5f, p)
        }
        p.style = Paint.Style.FILL

        // Eyes: big brown, with a shine; blink squashes them
        for (ex in listOf(39f, 61f)) {
            val ey = 51f
            if (open > 0.25f) {
                c.save(); c.scale(1f, open.coerceIn(0.05f, 1f), ex, ey)
                p.color = 0xFF2B170A.toInt(); c.drawCircle(ex, ey, 6.6f, p)
                p.color = 0xFF6B3A17.toInt(); c.drawCircle(ex, ey + 0.4f, 5.2f, p)
                p.color = 0xFF140A04.toInt(); c.drawCircle(ex, ey + 0.4f, 3.2f, p)
                p.color = 0xFFFFFFFF.toInt(); c.drawCircle(ex + 2f, ey - 2.2f, 2.1f, p)
                c.drawCircle(ex - 1.8f, ey + 2.2f, 0.7f, p)
                c.restore()
            } else {
                p.color = 0xFF2B170A.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 1.6f; p.strokeCap = Paint.Cap.ROUND
                r.set(ex - 5f, ey - 3f, ex + 5f, ey + 3f); c.drawArc(r, 20f, 140f, false, p)
                p.style = Paint.Style.FILL
            }
        }

        // Rosy cheeks
        p.color = 0x55FF8FA3; r.set(30f, 58f, 38f, 63f); c.drawOval(r, p); r.set(62f, 58f, 70f, 63f); c.drawOval(r, p)
        // Soft light on the forehead
        p.alpha = 255; p.shader = RadialGradient(50f, 38f, 14f, 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP); c.drawCircle(50f, 38f, 14f, p); p.shader = null

        // Nose
        p.color = 0xFF151515.toInt(); r.set(44.5f, 58.5f, 55.5f, 65.5f); c.drawOval(r, p)
        p.color = 0x99FFFFFF.toInt(); r.set(47f, 59.6f, 50.5f, 61.4f); c.drawOval(r, p)
        // Mouth
        p.color = 0xFF5A3A2A.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 1.3f; p.strokeCap = Paint.Cap.ROUND
        path.reset(); path.moveTo(50f, 65.5f); path.lineTo(50f, 68f)
        path.moveTo(45f, 68f); path.quadTo(47.5f, 71f, 50f, 68f); path.quadTo(52.5f, 71f, 55f, 68f)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
        if (tongue > 0.05f) {
            p.color = 0xFFF27C8E.toInt(); r.set(47f, 68.5f, 53f, 68.5f + 7f * tongue); c.drawRoundRect(r, 3f, 3f, p)
            p.color = 0xFFD9576B.toInt(); p.strokeWidth = 0.8f; p.style = Paint.Style.STROKE
            c.drawLine(50f, 69.5f, 50f, 67.5f + 6f * tongue, p); p.style = Paint.Style.FILL
        }
        // Chin fur wisps
        p.color = CREAM; p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f
        for (i in -3..3) c.drawLine(50f + i * 3.6f, 77f, 50f + i * 4.4f, 82f, p)
        p.style = Paint.Style.FILL

        // Headphone ear cups over the ear fur, with two little green status lights (a homelab nod)
        for (side in listOf(-1f, 1f)) {
            val cx = 50f + side * 27f
            p.alpha = 255
            p.alpha = 255; p.shader = LinearGradient(cx, 44f, cx, 64f, 0xFF7C64E0.toInt(), 0xFF4B3BAE.toInt(), Shader.TileMode.CLAMP)
            r.set(cx - 6.5f, 44f, cx + 6.5f, 64f); c.drawRoundRect(r, 5.5f, 5.5f, p)
            p.shader = null
            p.color = 0x6638BDF8; r.set(cx - 4f, 47f, cx + 4f, 61f); c.drawRoundRect(r, 3.5f, 3.5f, p)
        }
        p.color = 0xFF4ADE80.toInt(); c.drawCircle(23f, 50.5f, 1.1f, p); p.color = 0xFF86EFAC.toInt(); c.drawCircle(23f, 54.5f, 1.1f, p)

        // Collar + bell
        p.color = 0xFFE0507A.toInt(); r.set(33f, 78f, 67f, 85f); c.drawRoundRect(r, 3.5f, 3.5f, p)
        // Gold tag with a play button (media)
        p.alpha = 255; p.shader = RadialGradient(48.5f, 86f, 6.5f, 0xFFFFE08A.toInt(), 0xFFC9971F.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(50f, 88.5f, 5f, p); p.shader = null
        p.color = 0xFFFFFFFF.toInt()
        path.reset(); path.moveTo(48.6f, 86.3f); path.lineTo(52.6f, 88.5f); path.lineTo(48.6f, 90.7f); path.close(); c.drawPath(path, p)
        c.restore()
        c.restore()
    }
}
