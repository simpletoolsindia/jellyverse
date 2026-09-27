package com.sridhar.harbor.ui.music

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Tasteful gradient pairs for generated covers (no neon, no purple haze). */
private val coverPalettes = listOf(
    Color(0xFF1F80E0) to Color(0xFF0B2A55), Color(0xFF0FA3B1) to Color(0xFF0B3440), Color(0xFFE0584A) to Color(0xFF4A1512),
    Color(0xFFE9A23B) to Color(0xFF4F2E07), Color(0xFF3CB371) to Color(0xFF0E3B25), Color(0xFF7D8CA3) to Color(0xFF1F2733),
    Color(0xFFD6457A) to Color(0xFF45112A), Color(0xFF2E6FD6) to Color(0xFF161E3B), Color(0xFFB8733D) to Color(0xFF3B200C),
)

fun seedColors(seed: String): Pair<Color, Color> = coverPalettes[abs(seed.hashCode()) % coverPalettes.size]

/**
 * Album art with a designed fallback: libraries full of untagged downloads shouldn't look broken.
 * The generated cover is deterministic per title, so the same album always gets the same look.
 */
@Composable
fun CoverArt(url: String?, title: String, modifier: Modifier = Modifier, shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)) {
    Box(modifier.clip(shape)) {
        SubcomposeAsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            loading = { GeneratedCover(title) }, error = { GeneratedCover(title) },
        )
    }
}

@Composable
fun GeneratedCover(title: String, modifier: Modifier = Modifier) {
    val (a, b) = remember(title) { seedColors(title) }
    BoxWithConstraints(modifier.fillMaxSize().background(Brush.linearGradient(listOf(a, b), start = Offset.Zero, end = Offset.Infinite))) {
        val big = maxWidth > 96.dp
        // Vinyl grooves – a quiet texture that reads as "music" at any size.
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width * 0.92f, size.height * 0.95f)
            for (i in 1..7) drawCircle(Color.White.copy(alpha = 0.06f), radius = size.minDimension * 0.16f * i, center = c, style = Stroke(1.dp.toPx()))
        }
        if (maxWidth < 72.dp) {
            // Thumbnails: the title is already printed beside them – show a bold initial instead.
            Text(title.trim().firstOrNull()?.uppercase() ?: "♪", Modifier.align(Alignment.Center), color = Color.White, fontWeight = FontWeight.Black, fontSize = (maxWidth.value * 0.45f).sp)
        } else Text(
            title.ifBlank { "♪" }, Modifier.align(Alignment.TopStart).padding(if (big) 14.dp else 6.dp),
            color = Color.White, fontWeight = FontWeight.Black, fontSize = if (big) 22.sp else 11.sp, lineHeight = if (big) 24.sp else 12.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
    }
}

private val colorCache = HashMap<String, Int>()

/** Dark, readable accent color sampled from artwork (falls back to the generated cover's color). */
@Composable
fun rememberArtColor(url: String?, seed: String): Color {
    val ctx = LocalContext.current
    val fallback = remember(seed) { lerp(seedColors(seed).first, Color.Black, 0.45f) }
    var target by remember(url, seed) { mutableStateOf(url?.let { colorCache[it]?.let(::Color) } ?: fallback) }
    LaunchedEffect(url) {
        if (url == null || colorCache.containsKey(url)) return@LaunchedEffect
        val c = withContext(Dispatchers.IO) {
            runCatching {
                val img = SingletonImageLoader.get(ctx).execute(ImageRequest.Builder(ctx).data(url).allowHardware(false).size(96).build()).image ?: return@runCatching null
                val p = Palette.from(img.toBitmap()).maximumColorCount(12).generate()
                val sw = p.darkVibrantSwatch ?: p.vibrantSwatch ?: p.dominantSwatch ?: p.darkMutedSwatch
                sw?.rgb?.let { lerp(Color(it), Color.Black, 0.35f) }
            }.getOrNull()
        }
        if (c != null) { colorCache[url] = c.toArgb(); target = c }
    }
    return animateColorAsState(target, tween(700), label = "artColor").value
}

/** Three bouncing bars that mark the song that's playing. */
@Composable
fun EqualizerBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = Harbor.Violet) {
    val t = rememberInfiniteTransition(label = "eq")
    val bars = listOf(420, 300, 520).map { d -> t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(d), RepeatMode.Reverse), label = "bar$d") }
    Row(modifier.size(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEach { h ->
            Box(Modifier.width(3.dp).fillMaxHeight().graphicsLayer { scaleY = if (playing) h.value else 0.3f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) }
                .background(color, androidx.compose.foundation.shape.RoundedCornerShape(1.dp)))
        }
    }
}
