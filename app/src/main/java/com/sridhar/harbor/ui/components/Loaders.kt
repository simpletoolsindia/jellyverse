package com.sridhar.harbor.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sridhar.harbor.ui.theme.Harbor
import kotlin.math.PI
import kotlin.math.sin

/*
 * JellyVerse loaders – the logo comes alive instead of a stock spinner.
 * The bell pumps (a quick squeeze, a slow release, surging forward on each beat), the tentacles ripple
 * in a travelling wave, and bubbles peel off behind. Everything is drawn from one clock per loader.
 */

/** The logo's bell in its 108-unit design space (same path as the launcher icon). */
private fun bellPath() = Path().apply {
    moveTo(45f, 29f); cubicTo(63f, 29f, 80f, 43f, 83f, 54f); cubicTo(80f, 65f, 63f, 79f, 45f, 79f)
    quadraticTo(49f, 70.7f, 45f, 62.3f); quadraticTo(49f, 54f, 45f, 45.7f); quadraticTo(49f, 37.3f, 45f, 29f); close()
}

/** Jellyfish pulse: 0 = relaxed, 1 = fully squeezed. Snappy contraction, slow graceful release. */
private fun pump(t: Float): Float = if (t < 0.28f) FastOutSlowInEasing.transform(t / 0.28f) else 1f - FastOutSlowInEasing.transform((t - 0.28f) / 0.72f)

/**
 * Draws the swimming jellyfish so the 108-unit box maps to [box] px with its top-left at [origin].
 * [t] is the 0..1 beat clock.
 */
private fun DrawScope.jelly(origin: Offset, box: Float, t: Float, color: Color, accent: Color, bell: Path, bubbles: Boolean) {
    val c = pump(t)
    val sx = 1f + 0.07f * c; val sy = 1f - 0.15f * c; val surge = 3.5f * c
    withTransform({ translate(origin.x, origin.y); scale(box / 108f, box / 108f, Offset.Zero) }) {
        // Soft glow that flares on every beat.
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.18f + 0.22f * c), Color.Transparent), Offset(64f + surge, 54f), 34f), 34f, Offset(64f + surge, 54f))
        // Tentacles: a travelling sine wave, stretched when the bell squeezes, pinned to the skirt tips.
        val phase = t * 2f * PI.toFloat()
        for ((k, spec) in listOf(Triple(45.7f, 18f, 0.8f), Triple(54f, 12f, 1f), Triple(62.3f, 18f, 0.8f)).withIndex()) {
            val (ay, len, alpha) = spec
            val y0 = 54f + (ay - 54f) * sy
            val l = len * (1f + 0.28f * c)
            val p = Path()
            for (i in 0..12) {
                val f = i / 12f
                val x = 45.5f + surge - l * f
                // Parallel ripples (small stagger) so the tentacles flow together and never cross.
                val y = y0 + 2.3f * f * sin(phase - f * 2.2f * PI.toFloat() + k * 0.5f)
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, color.copy(alpha = alpha), style = Stroke(width = 3f, cap = StrokeCap.Round))
        }
        // Bell, squeezed around its centre and surging forward.
        withTransform({ translate(surge, 0f); scale(sx, sy, Offset(62f, 54f)) }) {
            drawPath(bell, color)
            drawArc(accent, 205f, 70f, false, Offset(50f, 36.5f), androidx.compose.ui.geometry.Size(30f, 30f), style = Stroke(2.6f, cap = StrokeCap.Round))
        }
        // Bubbles peel off behind, staggered a third of a beat apart.
        if (bubbles) for (k in 0..2) {
            val b = (t + k / 3f) % 1f
            val x = 30f - 10f * b; val y = 54f + (k - 1) * 9f - 12f * b
            drawCircle(color.copy(alpha = 0.55f * sin(PI.toFloat() * b)), 1.2f + 1.4f * b, Offset(x, y), style = Stroke(1.1f))
        }
    }
}

/**
 * Brand loading indicator – drop-in for an indeterminate CircularProgressIndicator.
 * [strokeWidth] is accepted for call-site compatibility and ignored.
 */
@Composable
fun JellyLoader(modifier: Modifier = Modifier, color: Color = Color.White, accent: Color = Harbor.Sky, @Suppress("UNUSED_PARAMETER") strokeWidth: Dp = Dp.Unspecified) {
    val bell = remember { bellPath() }
    val t = rememberInfiniteTransition(label = "jelly").animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "beat")
    Canvas(modifier.size(48.dp).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        val s = minOf(size.width, size.height)
        // The mark's visible content spans x 20..88 of the 108 box – centre that span in the canvas.
        val box = s * 108f / 68f
        jelly(Offset((size.width - s) / 2f - 20f * box / 108f, (size.height - s) / 2f - 20f * box / 108f), box, t.value, color, accent, bell, bubbles = s > 28.dp.toPx())
    }
}

/**
 * Brand loading bar. Indeterminate: a tiny jellyfish swims the track pulling a rippling comet wake.
 * With [progress]: the filled part is a living wave with the jellyfish riding its tip.
 * [rider] = false draws just the wake/wave (for hairline bars such as video buffering).
 */
@Composable
fun TideBar(modifier: Modifier = Modifier, progress: (() -> Float)? = null, rider: Boolean = true,
            color: Color = Harbor.Violet, accent: Color = Harbor.Sky, thickness: Dp = 3.dp) {
    val bell = remember { bellPath() }
    val clock = rememberInfiniteTransition(label = "tide")
    val beat = clock.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "beat")
    val swim = clock.animateFloat(-0.15f, 1.15f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "swim")
    val h = if (rider) maxOf(thickness, 16.dp) else thickness
    Canvas(modifier.fillMaxWidth().height(h).semantics {
        progressBarRangeInfo = progress?.let { ProgressBarRangeInfo(it().coerceIn(0f, 1f), 0f..1f) } ?: ProgressBarRangeInfo.Indeterminate
    }) {
        val cy = size.height / 2f; val th = thickness.toPx(); val w = size.width
        drawLine(Color.White.copy(alpha = .12f), Offset(0f, cy), Offset(w, cy), th, StrokeCap.Round)
        val amp = if (rider) th * 0.9f else 0f
        val phase = beat.value * 2f * PI.toFloat()
        fun wave(from: Float, to: Float, brush: Brush, fade: Boolean) {
            if (to - from < 1f) return
            val p = Path(); val steps = ((to - from) / 6f).toInt().coerceIn(2, 200)
            for (i in 0..steps) {
                val x = from + (to - from) * i / steps
                val env = if (fade) i.toFloat() / steps else 1f            // wake calms toward its tail
                val y = cy + amp * env * sin(x / (th * 4f) - phase * 2f)
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, brush, style = Stroke(th, cap = StrokeCap.Round))
        }
        val head: Float
        if (progress == null) {
            head = w * swim.value
            val tail = head - w * 0.38f
            wave(tail.coerceAtLeast(0f), head.coerceIn(0f, w), Brush.horizontalGradient(listOf(Color.Transparent, color, accent), tail, head), fade = true)
        } else {
            head = w * progress().coerceIn(0f, 1f)
            wave(0f, head, Brush.horizontalGradient(listOf(color, accent), 0f, head.coerceAtLeast(1f)), fade = false)
        }
        if (rider && head in -h.toPx()..w + h.toPx()) {
            val box = size.height * 108f / 50f   // bell is 50 units tall → fills the bar height
            jelly(Offset(head - 60f * box / 108f, cy - 54f * box / 108f), box, beat.value, Color.White, accent, bell, bubbles = false)
        }
    }
}

/**
 * "Adrift" – no network. The pump stalls: the jellyfish bobs slowly with limp tentacles while signal arcs
 * above it light up one by one, searching, then die out.
 */
@Composable
fun AdriftJelly(modifier: Modifier = Modifier, color: Color = Color.White, accent: Color = Harbor.Sky) {
    val bell = remember { bellPath() }
    val clock = rememberInfiniteTransition(label = "adrift")
    val drift = clock.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "drift")
    val search = clock.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "search")
    Canvas(modifier.size(120.dp)) {
        val s = minOf(size.width, size.height)
        val u = s / 108f
        val tw = 2f * PI.toFloat()
        // Signal arcs: light up inner → outer, hold, then all fade – a search that never connects.
        val q = search.value
        val arcCenter = Offset(size.width / 2f, s * 0.36f)
        for (i in 0..2) {
            val on = ((q * 4f) - i).coerceIn(0f, 1f) * (1f - ((q - 0.75f) / 0.25f).coerceIn(0f, 1f))
            val r = (10f + i * 9f) * u
            drawArc(Color.White.copy(alpha = 0.12f), 225f, 90f, false, Offset(arcCenter.x - r, arcCenter.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(3.2f * u, cap = StrokeCap.Round))
            drawArc(accent.copy(alpha = 0.9f * on), 225f, 90f, false, Offset(arcCenter.x - r, arcCenter.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(3.2f * u, cap = StrokeCap.Round))
        }
        drawCircle(accent.copy(alpha = 0.9f), 2.6f * u, arcCenter)
        // The jellyfish, drifting: slow bob and sway, a weak half-pump now and then.
        val d = drift.value
        val bob = sin(d * tw) * 4f
        val tilt = sin(d * tw + 1.2f) * 6f
        val box = s * 0.62f
        val o = Offset(size.width / 2f - 58f * box / 108f, s * 0.62f - 54f * box / 108f + bob * u)
        withTransform({ rotate(tilt, Offset(size.width / 2f, s * 0.62f + bob * u)) }) {
            jelly(o, box, (d * 2f) % 1f * 0.35f, color.copy(alpha = 0.85f), accent, bell, bubbles = false)
        }
    }
}

/**
 * "Glitch" – playback failed. The play-shaped bell tears into offset bands with an RGB split in short
 * bursts, then settles and sags, like a signal that keeps dropping.
 */
@Composable
fun GlitchJelly(modifier: Modifier = Modifier, color: Color = Color.White) {
    val bell = remember { bellPath() }
    val t = rememberInfiniteTransition(label = "glitch").animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "t")
    Canvas(modifier.size(120.dp)) {
        val s = minOf(size.width, size.height)
        val box = s * 1.05f
        val origin = Offset((size.width - box) / 2f - 6f * box / 108f, (size.height - box) / 2f)
        val v = t.value
        val burst = v < 0.22f || v in 0.5f..0.58f                    // two tears per cycle
        val step = (v * 60f).toInt()                                   // re-roll the tear ~27×/s
        fun rnd(k: Int) = ((sin((step * 12.9898f + k * 78.233f)) * 43758.547f) % 1f + 1f) % 1f
        val sag = if (burst) 0f else sin(v * PI.toFloat()) * 2.5f
        withTransform({ translate(origin.x, origin.y); scale(box / 108f, box / 108f, Offset.Zero) }) {
            val split = if (burst) 2.2f + rnd(9) * 2.5f else 0.8f
            // RGB ghosts
            withTransform({ translate(-split, sag) }) { drawPath(bell, Harbor.Rose.copy(alpha = 0.55f)) }
            withTransform({ translate(split, sag) }) { drawPath(bell, Harbor.Sky.copy(alpha = 0.55f)) }
            if (!burst) withTransform({ translate(0f, sag) }) { drawPath(bell, color) }
            else for (b in 0 until 6) {                                // horizontal slices shoved sideways
                val top = 26f + b * 9f
                val dx = (rnd(b) - 0.5f) * 9f
                withTransform({ clipRect(0f, top, 108f, top + 9f); translate(dx, 0f) }) { drawPath(bell, color) }
            }
            // Dead pixels flicking off the edge during a tear
            if (burst) for (k in 0..4) drawRect(if (k % 2 == 0) Harbor.Sky else color,
                Offset(84f + rnd(k + 20) * 10f, 30f + rnd(k + 40) * 46f), androidx.compose.ui.geometry.Size(2.4f, 2.4f))
        }
    }
}
