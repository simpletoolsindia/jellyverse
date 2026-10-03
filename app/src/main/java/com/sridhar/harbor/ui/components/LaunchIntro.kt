package com.sridhar.harbor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.min

private val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * Brand intro played once per cold start (~1.3 s, tap / any key skips):
 * the navy tile springs in, the Shih Tzu wiggles its pigtails and does a happy hop,
 * and "JellyVerse" is revealed by a sweeping shine before the app fades in underneath.
 */
@Composable
fun LaunchIntro(content: @Composable () -> Unit) {
    var done by rememberSaveable { mutableStateOf(false) }
    // Respect "Remove animations" – no intro at all.
    val animationsOff = remember { android.provider.Settings.Global.getFloat(com.sridhar.harbor.HarborApp.instance?.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    // 0 → 1 while the intro opens like an iris from the tile; the app settles in from a slight zoom underneath.
    val reveal = remember { Animatable(if (done || animationsOff) 1f else 0f) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            val r = reveal.value
            if (r < 1f) { val sc = 1.06f - 0.06f * r; scaleX = sc; scaleY = sc; alpha = 0.35f + 0.65f * r }
        }) { content() }
        if (!done && !animationsOff) Intro(reveal, onFinished = { done = true })
    }
}

@Composable
private fun Intro(reveal: Animatable<Float, *>, onFinished: () -> Unit) {
    val tile = remember { Animatable(0f) }      // 0 → 1 tile grows in
    val beat = remember { Animatable(0f) }      // jellyfish pump clock
    val dart = remember { Animatable(0f) }      // 0 → 1 swims away
    val words = remember { Animatable(0f) }     // wordmark reveal
    val fade = remember { Animatable(1f) }      // whole overlay alpha
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val show = remember { kotlin.random.Random.nextInt(6) }
    val focus = remember { FocusRequester() }

    fun skip() = scope.launch { launch { reveal.animateTo(1f, tween(220)) }; fade.animateTo(0f, tween(180)); onFinished() }

    val container = LocalContainer.current
    LaunchedEffect(Unit) {
        // The system splash stays up until settings are loaded – start only once it has lifted, or nobody sees this.
        container.config.first { it != null }
        kotlinx.coroutines.delay(60)
        runCatching { focus.requestFocus() }
        launch { tile.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f)) }
        launch { beat.animateTo(2f, tween(760, easing = LinearEasing)) }
        kotlinx.coroutines.delay(420)
        launch { words.animateTo(1f, tween(620, easing = EaseOutExpo)) }
        kotlinx.coroutines.delay(360)
        dart.animateTo(1f, tween(1000, easing = LinearEasing))
        // Iris-open from the tile into the app (smoother than a flat cross-fade), then let go.
        launch { kotlinx.coroutines.delay(380); fade.animateTo(0f, tween(220)) }
        reveal.animateTo(1f, tween(600, easing = EaseOutExpo))
        onFinished()
    }

    Canvas(
        Modifier.fillMaxSize().graphicsLayer { alpha = fade.value; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .focusRequester(focus).focusable().onKeyEvent { skip(); true }
            .pointerInput(Unit) { detectTapGestures { skip() } },
    ) {
        drawRect(Harbor.Ink)
        val s = min(size.width, size.height)
        val cx = size.width / 2f; val cy = size.height * 0.44f
        // Soft warm glow that breathes with the tile.
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC27A).copy(alpha = 0.28f * tile.value), Color.Transparent), Offset(cx, cy), s * 0.6f), s * 0.6f, Offset(cx, cy))

        // The jellyfish is already where the system splash left it; the blue tile blooms in behind it.
        val full = s * 0.30f
        val t = full * tile.value.coerceAtLeast(0f)
        val ftl = Offset(cx - full / 2f, cy - full / 2f)
        if (t > 1f) {
            val tl = Offset(cx - t / 2f, cy - t / 2f)
            drawRoundRect(Brush.linearGradient(listOf(Color(0xFF27324F), Color(0xFF101828)), tl, Offset(tl.x + t, tl.y + t)),
                tl, Size(t, t), CornerRadius(t * 0.26f))
        }
        // The dog (where the system splash left it) wiggles its pigtails, then does a happy hop, tongue out.
        val d = dart.value
        // Hand-off from the system splash: Android draws the launcher foreground in a 240dp box centred on screen
        // (the dog is 66% of its 108-unit canvas) – start exactly there, then glide into the tile as it blooms.
        val splashBox = 240.dp.toPx() * 0.66f * 100f / 108f
        val k = tile.value.coerceIn(0f, 1f)
        val box = splashBox + (full * 0.86f - splashBox) * k
        val dogCy = size.height / 2f + (cy - size.height / 2f) * k
        val tw = 2f * kotlin.math.PI.toFloat()
        val pi = kotlin.math.PI.toFloat()
        fun sin(x: Float) = kotlin.math.sin(x)
        // A different little show on every launch (picked once per start).
        var dx = 0f; var dy = 0f; var rot = 0f; var sc = 1f; var open = 1f; var tongue = 0f
        var sway = sin(beat.value * tw); var tilt = 0f
        when (show) {
            0 -> { dy = -sin(d * pi) * full * 0.28f; tilt = sin(d * tw) * 8f; tongue = if (d > 0f) 1f else 0f }                 // happy hop + hearts
            1 -> { dx = sin(d * tw) * full * 0.9f; dy = -kotlin.math.abs(sin(d * tw * 3)) * full * 0.06f                        // zoomies
                   tilt = kotlin.math.cos(d * tw) * 14f; sway = sin(d * tw * 4); tongue = if (d > 0f) 1f else 0f }
            2 -> { val e = androidx.compose.animation.core.FastOutSlowInEasing.transform(d); rot = 360f * e                    // spin + bounce
                   sc = 1f + 0.18f * sin(d * pi); tongue = if (d > 0.5f) 1f else 0f }
            3 -> { dy = sin((d * 1.15f).coerceAtMost(1f) * pi) * full * 0.42f; open = if (d in 0.2f..0.6f) 0f else 1f        // peek-a-boo
                   sc = 1f + 0.12f * (if (d > 0.85f) sin((d - 0.85f) / 0.15f * pi) else 0f) }
            4 -> { open = if (d < 0.35f) 0.05f else 1f; dy = if (d > 0.35f) -sin((d - 0.35f) / 0.65f * pi) * full * 0.3f else 0f // sleepy → wakes up
                   tongue = if (d > 0.5f) 1f else 0f; tilt = if (d < 0.35f) -8f else 0f }
            else -> { tilt = sin(d * tw * 3) * 14f; dx = sin(d * tw * 1.5f) * full * 0.12f; sway = sin(d * tw * 5)            // dance
                      dy = -kotlin.math.abs(sin(d * tw * 3)) * full * 0.05f; tongue = if (d > 0f) 1f else 0f }
        }
        val pivot = Offset(cx + dx, dogCy + dy)
        withTransform({ rotate(rot, pivot); scale(sc, sc, pivot) }) {
            dog(Offset(cx + dx - box / 2f, dogCy + dy - box * 0.51f), box, open = open, sway = sway, tilt = tilt, tongue = tongue)
        }
        // Extras: hearts for the hop, "z z" before the sleepy dog wakes, dust for the zoomies.
        when (show) {
            0 -> if (d > 0f) for (i in 0..2) {
                val t = ((d * 1.4f) - i * 0.18f).coerceIn(0f, 1f); if (t <= 0f) continue
                val hx = cx + (i - 1) * box * 0.35f; val hy = dogCy - box * 0.45f - t * full * 0.5f
                val r = box * 0.06f * (0.6f + t * 0.6f)
                val hp = androidx.compose.ui.graphics.Path().apply {
                    moveTo(hx, hy + r * 0.9f); cubicTo(hx - r * 1.6f, hy - r * 0.2f, hx - r * 0.6f, hy - r * 1.4f, hx, hy - r * 0.4f)
                    cubicTo(hx + r * 0.6f, hy - r * 1.4f, hx + r * 1.6f, hy - r * 0.2f, hx, hy + r * 0.9f); close()
                }
                drawPath(hp, Color(0xFFFF6B8B).copy(alpha = 1f - t * 0.7f))
            }
            1 -> if (d > 0f) for (i in 0..3) {
                val px = cx + dx - kotlin.math.cos(d * tw) * box * (0.45f + i * 0.12f); val py = dogCy + box * 0.42f
                drawCircle(Color(0xFFD9C3A5).copy(alpha = 0.5f - i * 0.1f), box * (0.05f + i * 0.015f), Offset(px, py))
            }
            4 -> if (d < 0.4f) {
                val zs = TextStyle(fontSize = (box / 6f).toSp(), fontWeight = FontWeight.Black, color = Color(0xFF9FB4FF))
                for (i in 0..1) {
                    val t = ((beat.value / 2f) + i * 0.5f) % 1f
                    drawText(measurer.measure("z", zs), topLeft = Offset(cx + box * (0.3f + 0.15f * i + 0.1f * t), dogCy - box * (0.45f + 0.35f * t)), alpha = 1f - t)
                }
            }
        }

        // Wordmark: rises in, then a shine sweeps across it.
        val w = words.value
        if (w > 0f) {
            val style = TextStyle(fontSize = kotlin.math.min((s / 9f).toSp().value, 56f).sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp,
                brush = Brush.horizontalGradient(listOf(Color.White, Harbor.VioletSoft)))
            val layout = measurer.measure("JellyVerse", style)
            val x = cx - layout.size.width / 2f
            val y = cy + s * 0.22f + (1f - w) * 24f
            drawText(layout, topLeft = Offset(x, y), alpha = w)
            val sweep = x - 120f + (layout.size.width + 240f) * w
            clipRect(x, y, x + layout.size.width, y + layout.size.height) {
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .5f), Color.Transparent), sweep - 60f, sweep + 60f),
                    Offset(sweep - 60f, y), Size(120f, layout.size.height.toFloat()))
            }
        }
        // Iris: a growing soft-edged hole shows the app underneath.
        val r = reveal.value
        if (r > 0f) {
            val far = kotlin.math.hypot(size.width, size.height)
            val rad = far * EaseOutExpo.transform(r)
            drawCircle(Brush.radialGradient(0f to Color.Black, 0.85f to Color.Black, 1f to Color.Transparent, center = Offset(cx, cy), radius = rad.coerceAtLeast(1f)),
                rad.coerceAtLeast(1f), Offset(cx, cy), blendMode = androidx.compose.ui.graphics.BlendMode.DstOut)
        }
    }
}
