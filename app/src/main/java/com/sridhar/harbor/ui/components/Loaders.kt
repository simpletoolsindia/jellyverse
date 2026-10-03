package com.sridhar.harbor.ui.components
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background

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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.drawText
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sridhar.harbor.ui.theme.Harbor
import kotlin.math.PI
import kotlin.math.sin

/*
 * JellyVerse loaders – the logo (the Shih Tzu) comes alive instead of a stock spinner.
 * The bell pumps (a quick squeeze, a slow release, surging forward on each beat), the tentacles ripple
 * in a travelling wave, and bubbles peel off behind. Everything is drawn from one clock per loader.
 */

/** The logo's bell in its 108-unit design space (same path as the launcher icon). */
internal fun bellPath() = Path().apply {
    moveTo(45f, 29f); cubicTo(63f, 29f, 80f, 43f, 83f, 54f); cubicTo(80f, 65f, 63f, 79f, 45f, 79f)
    quadraticTo(49f, 70.7f, 45f, 62.3f); quadraticTo(49f, 54f, 45f, 45.7f); quadraticTo(49f, 37.3f, 45f, 29f); close()
}

/**
 * Draws the Shih Tzu mascot (no tile) with its 100-unit art box at [topLeft], [box] px wide – the app's logo,
 * used by the loaders and the launch intro. [tint] draws it as a flat coloured silhouette (glitch ghosts).
 */
internal fun DrawScope.dog(topLeft: Offset, box: Float, open: Float = 1f, sway: Float = 0f, bob: Float = 0f, tilt: Float = 0f,
                           tongue: Float = 0f, alpha: Float = 1f, tint: Color? = null) = drawIntoCanvas { c ->
    val n = c.nativeCanvas
    val layer = alpha < 1f || tint != null
    if (layer) n.saveLayer(topLeft.x - box * .1f, topLeft.y - box * .1f, topLeft.x + box * 1.1f, topLeft.y + box * 1.1f,
        android.graphics.Paint().apply {
            this.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
            if (tint != null) colorFilter = android.graphics.PorterDuffColorFilter(tint.toArgb(), android.graphics.PorterDuff.Mode.SRC_IN)
        })
    n.save(); n.translate(topLeft.x, topLeft.y)
    com.sridhar.harbor.ui.ai.PetArt.draw(n, box, open, sway, bob, tilt, tongue, tile = false)
    n.restore()
    if (layer) n.restore()
}

/** Jellyfish pulse: 0 = relaxed, 1 = fully squeezed. Snappy contraction, slow graceful release. */
private fun pump(t: Float): Float = if (t < 0.28f) FastOutSlowInEasing.transform(t / 0.28f) else 1f - FastOutSlowInEasing.transform((t - 0.28f) / 0.72f)

/**
 * Draws the swimming jellyfish so the 108-unit box maps to [box] px with its top-left at [origin].
 * [t] is the 0..1 beat clock.
 */
internal fun DrawScope.jelly(origin: Offset, box: Float, t: Float, color: Color, accent: Color, bell: Path, bubbles: Boolean) {
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
 * A soft, shape-shifting gradient blob (in the spirit of Material 3 Expressive's morphing loader) turns slowly
 * while the white jellyfish pumps on top of it; a thin comet arc orbits the rim.
 * [strokeWidth] is accepted for call-site compatibility and ignored.
 */
@Composable
fun JellyLoader(modifier: Modifier = Modifier, color: Color = Color.White, accent: Color = Harbor.Sky, @Suppress("UNUSED_PARAMETER") strokeWidth: Dp = Dp.Unspecified) {
    val bell = remember { bellPath() }
    val blob = remember { Path() }
    val clock = rememberInfiniteTransition(label = "jelly")
    val t = clock.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "beat")
    val spin = clock.animateFloat(0f, 360f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "spin")
    val morph = clock.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "morph")
    val violet = Harbor.Violet
    Canvas(modifier.size(48.dp).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        val s = minOf(size.width, size.height)
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = s * 0.40f
        // Morphing blob: 5 soft lobes ↔ 8 tighter ones, rotating.
        val m = morph.value
        val rot = spin.value * PI.toFloat() / 180f
        blob.reset()
        for (i in 0..72) {
            val a = i / 72f * 2f * PI.toFloat()
            val k = 1f + (1f - m) * 0.075f * kotlin.math.cos(5f * (a - rot)) + m * 0.055f * kotlin.math.cos(8f * (a + rot * 0.6f))
            val x = c.x + r * k * kotlin.math.cos(a); val y = c.y + r * k * sin(a)
            if (i == 0) blob.moveTo(x, y) else blob.lineTo(x, y)
        }
        blob.close()
        drawPath(blob, Brush.linearGradient(listOf(violet, accent), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)))
        // Comet arc on the rim.
        val ring = s * 0.47f
        drawArc(Brush.sweepGradient(listOf(Color.Transparent, accent.copy(alpha = .9f)), c), spin.value * 2f, 100f, false,
            Offset(c.x - ring, c.y - ring), androidx.compose.ui.geometry.Size(ring * 2, ring * 2), style = Stroke(s * 0.035f, cap = StrokeCap.Round))
        // The jellyfish, centred in the blob.
        val box = s * 0.62f * 108f / 68f
        // The dog running: quick bounce, paws pedalling, speed lines and a puff of dust behind it.
        val dogBox = s * 0.64f
        val run = (t.value * 2f) % 1f                               // two strides per beat
        val tw = 2f * PI.toFloat()
        val bounce = kotlin.math.abs(sin(run * PI.toFloat())) * dogBox * 0.06f
        val top = Offset(c.x - dogBox / 2f, c.y - dogBox * 0.56f - bounce)
        // paws (behind the collar), alternating
        for (i in 0..1) {
            val ph = run * tw + i * PI.toFloat()
            drawOval(Color(0xFFF4EBDD), Offset(c.x + (i - 0.5f) * dogBox * 0.26f - dogBox * 0.05f + sin(ph) * dogBox * 0.05f,
                c.y + dogBox * 0.28f - bounce + kotlin.math.cos(ph).coerceAtLeast(0f) * -dogBox * 0.05f), androidx.compose.ui.geometry.Size(dogBox * 0.12f, dogBox * 0.08f))
        }
        dog(top, dogBox, sway = sin(run * tw), tilt = -6f, tongue = 0.8f)
        // speed lines on the left
        for (i in 0..2) {
            val o = ((run + i / 3f) % 1f)
            val y = c.y - dogBox * 0.15f + i * dogBox * 0.18f
            val x0 = c.x - dogBox * (0.55f + 0.25f * o)
            drawLine(Color.White.copy(alpha = 0.55f * (1f - o)), Offset(x0, y), Offset(x0 - dogBox * 0.18f, y), dogBox * 0.03f, StrokeCap.Round)
        }
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
            val box = size.height * 1.25f        // the dog's face fills the bar height, riding the tip
            dog(Offset(head - box / 2f, cy - box * 0.55f), box, sway = sin(beat.value * 2f * PI.toFloat()), bob = sin(beat.value * 2f * PI.toFloat()) * 2f)
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
            // Sleepy dog (eyes closed), drifting.
            dog(Offset(size.width / 2f - box * 0.5f, o.y), box, open = 0.1f, sway = sin(d * tw) * 0.4f, alpha = 0.92f)
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
            withTransform({ translate(-split, sag) }) { dog(Offset(4f, 4f), 100f, tint = Harbor.Rose, alpha = 0.55f) }
            withTransform({ translate(split, sag) }) { dog(Offset(4f, 4f), 100f, tint = Harbor.Sky, alpha = 0.55f) }
            if (!burst) withTransform({ translate(0f, sag) }) { dog(Offset(4f, 4f), 100f, open = 0.1f) }
            else for (b in 0 until 6) {                                // horizontal slices shoved sideways
                val top = 26f + b * 9f
                val dx = (rnd(b) - 0.5f) * 9f
                withTransform({ clipRect(0f, top, 108f, top + 9f); translate(dx, 0f) }) { dog(Offset(4f, 4f), 100f) }
            }
            // Dead pixels flicking off the edge during a tear
            if (burst) for (k in 0..4) drawRect(if (k % 2 == 0) Harbor.Sky else color,
                Offset(84f + rnd(k + 20) * 10f, 30f + rnd(k + 40) * 46f), androidx.compose.ui.geometry.Size(2.4f, 2.4f))
        }
    }
}

/**
 * A cute little dinosaur (for empty states): round green body, back spikes, tiny arms, big eyes and blush.
 * It bobs, blinks, swishes its tail and now and then lets out a tiny "rawr".
 */
@Composable
fun CuteDino(modifier: Modifier = Modifier) {
    val still = reducedMotion()
    val clock = rememberInfiniteTransition(label = "dino")
    val t = clock.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "t")
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    Canvas(modifier.size(120.dp)) {
        val u = minOf(size.width, size.height) / 100f
        val v = if (still) 0f else t.value
        val tw = 2f * PI.toFloat()
        val bob = sin(v * tw) * 1.5f * u
        val green = Color(0xFF5BC27A); val dark = Color(0xFF3E9B5C); val belly = Color(0xFFCFF2C2)
        fun o(x: Float, y: Float) = Offset(x * u, y * u + bob)
        fun sz(w: Float, h: Float) = androidx.compose.ui.geometry.Size(w * u, h * u)
        // shadow
        drawOval(Color.Black.copy(alpha = .12f), Offset(22 * u, 88 * u), sz(52f, 7f))
        // tail (swishing)
        val sw = sin(v * tw * 2) * 4f
        drawPath(Path().apply { moveTo(30 * u, 72 * u + bob); quadraticTo(12 * u, (70 + sw) * u + bob, 6 * u, (60 + sw) * u + bob); quadraticTo(18 * u, 78 * u + bob, 34 * u, 82 * u + bob); close() }, green)
        // body
        drawOval(green, o(26f, 50f), sz(46f, 40f))
        drawOval(belly, o(38f, 60f), sz(26f, 26f))
        // back spikes
        for (i in 0..3) {
            val bx = 30f + i * 9f; val by = 52f - i * 2f
            drawPath(Path().apply { moveTo(bx * u, (by + 6) * u + bob); lineTo((bx + 4) * u, (by - 4) * u + bob); lineTo((bx + 8) * u, (by + 6) * u + bob); close() }, dark)
        }
        // feet + arms
        drawOval(dark, o(32f, 84f), sz(12f, 7f)); drawOval(dark, o(54f, 84f), sz(12f, 7f))
        drawOval(green, o(62f, 64f), sz(8f, 5f))
        // head
        drawOval(green, o(48f, 22f), sz(40f, 34f))
        drawOval(belly, o(66f, 38f), sz(20f, 12f))
        // eye (blink)
        val blink = if (!still && (v % 0.5f) in 0.46f..0.5f) 0.15f else 1f
        drawOval(Color.White, Offset(60 * u, (28 + 5 * (1 - blink)) * u + bob), sz(11f, 11f * blink))
        if (blink > 0.5f) { drawCircle(Color(0xFF1E2A22), 3.2f * u, o(67f, 34f)); drawCircle(Color.White, 1.2f * u, o(68.5f, 32.5f)) }
        // nostril, smile, blush
        drawCircle(dark, 1f * u, o(83f, 36f))
        drawArc(Color(0xFF1E2A22), 10f, 120f, false, o(70f, 38f), sz(12f, 8f), style = Stroke(1.6f * u, cap = StrokeCap.Round))
        drawCircle(Color(0xFFFF9AA8).copy(alpha = .7f), 3f * u, o(62f, 44f))
        // tiny "rawr"
        if (!still && v in 0.70f..0.95f) drawText(measurer.measure("rawr!", androidx.compose.ui.text.TextStyle(fontSize = (9 * u).toSp(), color = dark,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black)), topLeft = Offset(74 * u, 6 * u), alpha = 1f - kotlin.math.abs(v - 0.82f) * 6f)
    }
}

/**
 * Health bar with a little dino walking at the tip of the fill. Its mood follows the value:
 * calm & green below 60 %, sweating & amber from 60 %, panicking red with "!!" above 85 %.
 */
@Composable
fun DinoMeter(fraction: Float, modifier: Modifier = Modifier, height: Dp = 30.dp) {
    val still = reducedMotion()
    val f = fraction.coerceIn(0f, 1f)
    val shown = androidx.compose.animation.core.animateFloatAsState(f, tween(700), label = "f")
    val clock = rememberInfiniteTransition(label = "dinometer")
    val t = clock.animateFloat(0f, 1f, infiniteRepeatable(tween(if (f > 0.85f) 420 else if (f > 0.6f) 700 else 1000, easing = LinearEasing)), label = "t")
    val mood = when { f > 0.85f -> 2; f > 0.6f -> 1; else -> 0 }
    val barColor = when (mood) { 2 -> Harbor.Rose; 1 -> Harbor.Amber; else -> Color(0xFF34C77B) }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().height(height)) {
        val trackH = 8.dp.toPx(); val y = size.height - trackH / 2f - 1f
        val v = if (still) 0f else t.value
        val dinoW = size.height * 1.05f
        val usable = size.width - dinoW * 0.3f
        val tip = (usable * shown.value).coerceAtLeast(dinoW * 0.55f)
        drawLine(Color.Gray.copy(alpha = .18f), Offset(trackH / 2, y), Offset(size.width - trackH / 2, y), trackH, StrokeCap.Round)
        drawLine(barColor, Offset(trackH / 2, y), Offset(tip, y), trackH, StrokeCap.Round)
        // the dino stands on the fill's tip
        val u = dinoW / 40f
        val shake = if (mood == 2 && !still) sin(v * 2f * PI.toFloat() * 4) * 1.2f * u else 0f
        val ox = tip - dinoW * 0.62f + shake
        val oy = y - trackH / 2f - 30f * u + if (!still) -kotlin.math.abs(sin(v * 2f * PI.toFloat())) * 1.5f * u else 0f
        fun o(x: Float, yy: Float) = Offset(ox + x * u, oy + yy * u)
        val green = Color(0xFF5BC27A); val dark = Color(0xFF3E9B5C)
        // legs (walking)
        val step = if (still) 0f else sin(v * 2f * PI.toFloat() * 2)
        drawRoundRect(dark, o(12f + step * 2f, 22f), androidx.compose.ui.geometry.Size(4f * u, 8f * u), androidx.compose.ui.geometry.CornerRadius(2f * u))
        drawRoundRect(dark, o(20f - step * 2f, 22f), androidx.compose.ui.geometry.Size(4f * u, 8f * u), androidx.compose.ui.geometry.CornerRadius(2f * u))
        // tail, body, spikes, head
        drawPath(Path().apply { moveTo(ox + 8f * u, oy + 18f * u); lineTo(ox + 0f * u, oy + 14f * u + step * u); lineTo(ox + 9f * u, oy + 23f * u); close() }, green)
        drawOval(green, o(6f, 10f), androidx.compose.ui.geometry.Size(22f * u, 16f * u))
        for (i in 0..2) drawPath(Path().apply { val bx = ox + (9f + i * 5f) * u; val by = oy + (11f - i) * u
            moveTo(bx, by + 2 * u); lineTo(bx + 2.5f * u, by - 3 * u); lineTo(bx + 5f * u, by + 2 * u); close() }, dark)
        drawOval(green, o(20f, 2f), androidx.compose.ui.geometry.Size(17f * u, 14f * u))
        drawCircle(Color(0xFFCFF2C2), 5f * u, o(26f, 18f))
        // face by mood
        drawCircle(Color.White, 2.6f * u, o(30f, 7f)); drawCircle(Color(0xFF1E2A22), 1.4f * u, o(30.8f, 7.2f))
        when (mood) {
            0 -> drawArc(Color(0xFF1E2A22), 10f, 140f, false, o(28f, 9f), androidx.compose.ui.geometry.Size(6f * u, 4f * u), style = Stroke(1.2f * u, cap = StrokeCap.Round))
            1 -> { drawLine(Color(0xFF1E2A22), o(29f, 12f), o(34f, 12f), 1.2f * u, StrokeCap.Round)
                   drawCircle(Color(0xFF7CC8FF), 1.6f * u, o(22f, 3f + (v * 4f) % 4f)) }   // sweat drop
            else -> { drawOval(Color(0xFF1E2A22), o(30f, 10.5f), androidx.compose.ui.geometry.Size(3.5f * u, 3f * u))
                      drawCircle(Color(0xFF7CC8FF), 1.6f * u, o(21f, 2f + (v * 6f) % 5f)); drawCircle(Color(0xFF7CC8FF), 1.3f * u, o(37f, 3f + ((v + .5f) * 6f) % 5f))
                      drawText(measurer.measure("!!", androidx.compose.ui.text.TextStyle(fontSize = (8 * u).toSp(), color = Harbor.Rose,
                          fontWeight = androidx.compose.ui.text.font.FontWeight.Black)), topLeft = o(34f, -6f)) }
        }
        if (mood > 0) drawCircle(Color(0xFFFF9AA8).copy(alpha = .6f), 1.8f * u, o(26f, 10f))
    }
}

/** Red "● LIVE" pill with a softly pulsing dot – live radio and Live TV. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier, small: Boolean = false) {
    val a = if (reducedMotion()) 1f else rememberInfiniteTransition(label = "live").animateFloat(1f, .35f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a").value
    androidx.compose.foundation.layout.Row(modifier
        .then(Modifier.background(Color(0xFFE5303D), androidx.compose.foundation.shape.RoundedCornerShape(50)))
        .padding(horizontal = if (small) 6.dp else 8.dp, vertical = if (small) 2.dp else 3.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.size(if (small) 5.dp else 6.dp).background(Color.White.copy(alpha = a), androidx.compose.foundation.shape.CircleShape))
        androidx.compose.foundation.layout.Spacer(Modifier.width(if (small) 3.dp else 4.dp))
        androidx.compose.material3.Text("LIVE", color = Color.White, fontSize = if (small) 9.sp else 11.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = 0.8.sp)
    }
}

/**
 * Circular progress used for every download / scan / install. A gradient sweep with a glowing tip that eases to
 * the new value; [progress] = null spins (indeterminate). [label] shows the percentage in the middle, or pass
 * [content] for an icon instead.
 */
@Composable
fun ProgressRing(progress: Float?, modifier: Modifier = Modifier, size: Dp = 44.dp, stroke: Dp = 4.dp,
                 color: Color = Harbor.Violet, accent: Color = Harbor.Sky, label: Boolean = true,
                 content: (@Composable () -> Unit)? = null) {
    val target = progress?.coerceIn(0f, 1f) ?: 0f
    val animated = androidx.compose.animation.core.animateFloatAsState(target, androidx.compose.animation.core.spring(stiffness = 60f), label = "ring")
    val clock = rememberInfiniteTransition(label = "ringSpin")
    val spin = clock.animateFloat(0f, 360f, infiniteRepeatable(tween(if (progress == null) 1100 else 2600, easing = LinearEasing)), label = "spin")
    val breathe = clock.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    androidx.compose.foundation.layout.Box(modifier.size(size).semantics {
        progressBarRangeInfo = progress?.let { ProgressBarRangeInfo(target, 0f..1f) } ?: ProgressBarRangeInfo.Indeterminate
    }, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2f + sw * 0.6f
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(Color.White.copy(alpha = .10f), 0f, 360f, false, tl, arcSize, style = Stroke(sw))
            val start: Float; val sweep: Float
            if (progress == null) {                       // indeterminate: a comet arc that breathes as it spins
                start = spin.value - 90f; sweep = 70f + 160f * breathe.value
            } else {
                start = -90f; sweep = 360f * animated.value
            }
            if (sweep > 0.5f) withTransform({ rotate(start, center) }) {
                drawArc(Brush.sweepGradient(0f to color.copy(alpha = if (progress == null) 0f else 1f), (sweep / 360f).coerceAtLeast(0.01f) to accent, center = center),
                    0f, sweep, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                // Glowing tip – also slowly circles on a determinate ring so a stalled download still looks alive.
                val a = Math.toRadians(sweep.toDouble())
                val r = arcSize.width / 2f
                val tip = Offset(center.x + r * kotlin.math.cos(a).toFloat(), center.y + r * kotlin.math.sin(a).toFloat())
                drawCircle(accent.copy(alpha = .35f + .25f * breathe.value), sw * (1.3f + .5f * breathe.value), tip)
                drawCircle(Color.White, sw * 0.45f, tip)
            }
            if (progress != null && progress < 1f) {      // faint orbiting spark on the track
                val a = Math.toRadians((spin.value - 90f).toDouble()); val r = arcSize.width / 2f
                drawCircle(accent.copy(alpha = .5f), sw * .35f, Offset(center.x + r * kotlin.math.cos(a).toFloat(), center.y + r * kotlin.math.sin(a).toFloat()))
            }
        }
        when {
            content != null -> content()
            label && progress != null -> androidx.compose.material3.Text("${(target * 100).toInt()}%",
                fontSize = (size.value * 0.24f).coerceIn(9f, 22f).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Harbor.Fg)
        }
    }
}
