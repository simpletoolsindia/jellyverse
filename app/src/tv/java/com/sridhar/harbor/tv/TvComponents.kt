package com.sridhar.harbor.tv

import androidx.compose.runtime.LaunchedEffect
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.theme.Harbor

/** Focus ring + spring scale + ambient glow – the core TV interaction. Scale lives in graphicsLayer (no relayout). */
@Composable
fun Modifier.tvFocusable(
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    focusedScale: Float = 1.08f,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (focused) focusedScale else 1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "tvScale")
    val ring = animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "tvRing")
    return this
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value; shadowElevation = 24f * ring.value; this.shape = shape; clip = false }
        .onFocusChanged { if (it.isFocused != focused) { focused = it.isFocused; if (it.isFocused) onFocus() } }
        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
        .drawBehind {
            if (ring.value > 0f) drawRoundRect(Color.White.copy(alpha = ring.value), cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()),
                topLeft = androidx.compose.ui.geometry.Offset(-3.dp.toPx(), -3.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()))
        }
}

@Composable
fun PosterTile(title: String, image: String?, width: Dp = 150.dp, progress: Float = 0f, badge: String? = null, onFocus: () -> Unit, onClick: () -> Unit) {
    Box(Modifier.width(width).aspectRatio(2f / 3f).tvFocusable(onFocus = onFocus, onClick = onClick).clip(RoundedCornerShape(14.dp)).background(Harbor.Surface)) {
        NetImage(image, Modifier.fillMaxSize(), fallback = title)
        if (progress > 0f) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(Color.Black.copy(.5f))) {
            Box(Modifier.fillMaxWidth(progress).height(4.dp).background(Harbor.accentH))
        }
        badge?.let {
            Text(it, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                .clip(RoundedCornerShape(6.dp)).background(Harbor.accentH).padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

@Composable
fun LandscapeTile(title: String, subtitle: String?, image: String?, width: Dp = 300.dp, progress: Float = 0f, onFocus: () -> Unit, onClick: () -> Unit) {
    Column(Modifier.width(width)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).tvFocusable(focusedScale = 1.06f, onFocus = onFocus, onClick = onClick).clip(RoundedCornerShape(14.dp)).background(Harbor.Surface)) {
            NetImage(image, Modifier.fillMaxSize(), fallback = title)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.75f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, color = Color.White.copy(.75f), fontSize = 12.sp, maxLines = 1) }
            }
            if (progress > 0f) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(Color.White.copy(.25f))) {
                Box(Modifier.fillMaxWidth(progress).height(4.dp).background(Harbor.accentH))
            }
        }
    }
}

@Composable
fun <T> TvRow(title: String, items: List<T>, key: (T) -> Any, content: @Composable (T) -> Unit) {
    if (items.isEmpty()) return
    Column(Modifier.padding(bottom = 22.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(items, key = key) { content(it) }
        }
    }
}

@Composable
fun TvButton(text: String, icon: ImageVector?, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.onFocusChanged { focused = it.isFocused }
            .graphicsLayer { val s = if (focused) 1.06f else 1f; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(12.dp))
            .background(when { focused -> Brush.horizontalGradient(listOf(Color.White, Color.White)); primary -> Harbor.accentH; else -> Brush.horizontalGradient(listOf(Color.White.copy(.14f), Color.White.copy(.14f))) })
            .clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = if (focused) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, softWrap = false)
    }
}

/** Full-bleed ambient backdrop that cross-fades whenever focus lands on a new title. */
@Composable
fun AmbientBackdrop(url: String?, drift: Boolean = false, preview: com.sridhar.harbor.data.jellyfin.BaseItem? = null, onTrailer: (Boolean) -> Unit = {}) {
    // Optional slow Ken Burns push-in so the hero feels alive; each new backdrop starts its own drift.
    Box(Modifier.fillMaxSize().background(Harbor.Ink).clipToBounds()) {
        Crossfade(url, animationSpec = tween(650), label = "ambient") { u ->
            val zoom = remember(u) { androidx.compose.animation.core.Animatable(1f) }
            if (drift) LaunchedEffect(u) { zoom.animateTo(1.08f, tween(12_000, easing = androidx.compose.animation.core.LinearEasing)) }
            NetImage(u, Modifier.fillMaxSize().graphicsLayer { alpha = 0.9f; scaleX = zoom.value; scaleY = zoom.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.7f, 0.3f) })
        }
        // Hotstar-style: rest on a title and its trailer fades in behind the same scrims.
        com.sridhar.harbor.ui.components.TrailerPreview(preview, Modifier.fillMaxSize(), onPlaying = onTrailer)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Harbor.Ink, 0.45f to Harbor.Ink.copy(.75f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to Harbor.Ink.copy(.55f), 1f to Harbor.Ink)))
    }
}

@Composable
fun MetaLine(parts: List<String?>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        parts.filterNotNull().filter { it.isNotBlank() }.forEachIndexed { i, p ->
            if (i > 0) Box(Modifier.size(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(.5f)))
            Text(p, color = Color.White.copy(.85f), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
        }
    }
}
