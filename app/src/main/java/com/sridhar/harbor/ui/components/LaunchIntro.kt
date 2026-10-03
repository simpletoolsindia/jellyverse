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
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.min

private val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * Brand intro played once per cold start (~1.3 s, tap / any key skips):
 * the blue tile springs in, the jellyfish pumps twice, darts out leaving a light trail,
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
    val bell = remember { bellPath() }
    val measurer = rememberTextMeasurer()
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
        dart.animateTo(1f, tween(420, easing = CubicBezierEasing(0.5f, 0f, 0.9f, 0.4f)))
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
        // Soft brand glow that breathes with the tile.
        drawCircle(Brush.radialGradient(listOf(Harbor.Violet.copy(alpha = 0.35f * tile.value), Color.Transparent), Offset(cx, cy), s * 0.6f), s * 0.6f, Offset(cx, cy))

        // The jellyfish is already where the system splash left it; the blue tile blooms in behind it.
        val full = s * 0.30f
        val t = full * tile.value.coerceAtLeast(0f)
        val ftl = Offset(cx - full / 2f, cy - full / 2f)
        if (t > 1f) {
            val tl = Offset(cx - t / 2f, cy - t / 2f)
            drawRoundRect(Brush.linearGradient(listOf(Color(0xFF2B8FF2), Color(0xFF1766C8), Color(0xFF0A3F9E)), tl, Offset(tl.x + t, tl.y + t)),
                tl, Size(t, t), CornerRadius(t * 0.26f))
        }
        val d = dart.value
        val box = full * 1.16f
        if (d == 0f) jelly(Offset(ftl.x - full * 0.08f, ftl.y - full * 0.08f), box, beat.value % 1f, Color.White, Harbor.Violet, bell, bubbles = false)
        else clipRect(ftl.x, ftl.y, ftl.x + full, ftl.y + full) {
            // Darts out of the tile, stretching a light trail behind it.
            val dx = d * full * 1.3f
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .55f)), ftl.x + dx - full * 0.9f, ftl.x + dx),
                Offset(ftl.x, cy - full * 0.07f), Size(dx, full * 0.14f))
            withTransform({ translate(dx, 0f) }) {
                jelly(Offset(ftl.x - full * 0.08f, ftl.y - full * 0.08f), box, beat.value % 1f, Color.White, Harbor.Violet, bell, bubbles = false)
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
