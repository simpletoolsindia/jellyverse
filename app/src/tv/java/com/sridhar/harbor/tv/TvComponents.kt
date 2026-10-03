package com.sridhar.harbor.tv
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn

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
import androidx.compose.foundation.lazy.itemsIndexed
import com.sridhar.harbor.ui.components.enterRise
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value; translationY = -6.dp.toPx() * ring.value; shadowElevation = 36f * ring.value
            ambientShadowColor = Harbor.Sky; spotShadowColor = Harbor.Sky; this.shape = shape; clip = false }
        .onFocusChanged { if (it.isFocused != focused) { focused = it.isFocused; if (it.isFocused) onFocus() } }
        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
        .drawBehind {
            if (ring.value > 0f) drawRoundRect(Harbor.Fg.copy(alpha = ring.value), cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()),
                topLeft = androidx.compose.ui.geometry.Offset(-3.dp.toPx(), -3.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()))
        }
}

@Composable
fun PosterTile(title: String, image: String?, width: Dp = 150.dp, progress: Float = 0f, badge: String? = null, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.width(width).aspectRatio(2f / 3f).onFocusChanged { focused = it.isFocused }.tvFocusable(onFocus = onFocus, onClick = onClick).clip(RoundedCornerShape(14.dp)).background(Harbor.Surface)) {
        NetImage(image, Modifier.fillMaxSize(), fallback = title)
        // Prime-style: the focused card shows its title over a soft gradient.
        androidx.compose.animation.AnimatedVisibility(focused, Modifier.align(Alignment.BottomCenter), enter = fadeIn(tween(160)), exit = fadeOut(tween(100))) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.85f)))).padding(start = 10.dp, end = 10.dp, top = 28.dp, bottom = 10.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
            }
        }
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
fun LandscapeTile(title: String, subtitle: String?, image: String?, width: Dp = 300.dp, progress: Float = 0f, onFocus: () -> Unit, onMenu: (() -> Unit)? = null, onClick: () -> Unit) {
    Column(Modifier.width(width)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            // Menu key, or holding OK, opens the tile's options (e.g. remove from Continue watching).
            .then(if (onMenu == null) Modifier else Modifier.onPreviewKeyEvent { e ->
                val ne = e.nativeKeyEvent
                val menu = ne.keyCode == android.view.KeyEvent.KEYCODE_MENU && ne.action == android.view.KeyEvent.ACTION_DOWN
                val hold = (ne.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || ne.keyCode == android.view.KeyEvent.KEYCODE_ENTER) &&
                    ne.action == android.view.KeyEvent.ACTION_DOWN && ne.repeatCount == 1
                if (menu || hold) { onMenu(); true }
                else ne.repeatCount > 1 && (ne.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || ne.keyCode == android.view.KeyEvent.KEYCODE_ENTER)
            })
            .tvFocusable(focusedScale = 1.06f, onFocus = onFocus, onClick = onClick).clip(RoundedCornerShape(14.dp)).background(Harbor.Surface)) {
            NetImage(image, Modifier.fillMaxSize(), fallback = title)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.75f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, color = Color.White.copy(.75f), fontSize = 12.sp, maxLines = 1) }
            }
            if (progress > 0f) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(Harbor.line(.25f))) {
                Box(Modifier.fillMaxWidth(progress).height(4.dp).background(Harbor.accentH))
            }
        }
    }
}

@Composable
fun <T> TvRow(title: String, items: List<T>, key: (T) -> Any, content: @Composable (T) -> Unit) {
    if (items.isEmpty()) return
    Column(Modifier.padding(bottom = 22.dp)) {
        Text(title, color = Harbor.Fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            itemsIndexed(items, key = { _, it -> key(it) }) { i, it -> Box(Modifier.animateItem().enterRise(i)) { content(it) } }
        }
    }
}

@Composable
fun TvButton(text: String, icon: ImageVector?, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // OTT-style pills: Play is solid white (Netflix / Prime), the rest frosted glass that turns white on focus.
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "btn")
    val solid = focused || primary
    Row(
        modifier.onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale; shadowElevation = if (focused) 18f else 0f; shape = RoundedCornerShape(50); clip = false }
            .clip(RoundedCornerShape(50))
            .background(if (solid) Color.White else Color.White.copy(alpha = 0.16f))
            .then(if (primary && !focused) Modifier.border(0.dp, Color.Transparent, RoundedCornerShape(50)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 26.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (solid) Color.Black else Color.White
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(9.dp)) }
        Text(text, color = fg, fontWeight = if (primary) FontWeight.Black else FontWeight.Bold, fontSize = 17.sp, maxLines = 1, softWrap = false)
        if (primary && focused) { Spacer(Modifier.width(2.dp)) }
    }
}

/** Full-bleed ambient backdrop that cross-fades whenever focus lands on a new title. */
@Composable
fun AmbientBackdrop(url: String?, drift: Boolean = false, preview: com.sridhar.harbor.data.jellyfin.BaseItem? = null, onTrailer: (Boolean) -> Unit = {}) {
    // Optional slow Ken Burns push-in so the hero feels alive; each new backdrop starts its own drift.
    Box(Modifier.fillMaxSize().background(Harbor.Ink).clipToBounds()) {
        val reduced = com.sridhar.harbor.ui.components.reducedMotion()
        Crossfade(url, animationSpec = tween(if (reduced) 220 else 450), label = "ambient") { u ->
            val zoom = remember(u) { androidx.compose.animation.core.Animatable(1f) }
            if (drift && !reduced) LaunchedEffect(u) { zoom.animateTo(1.08f, tween(12_000, easing = androidx.compose.animation.core.LinearEasing)) }
            NetImage(u, Modifier.fillMaxSize().graphicsLayer { alpha = 0.9f; scaleX = zoom.value; scaleY = zoom.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.7f, 0.3f) })
        }
        // Hotstar-style: rest on a title and its trailer fades in behind the same scrims.
        com.sridhar.harbor.ui.components.TrailerPreview(preview, Modifier.fillMaxSize(), onPlaying = onTrailer)
        // Cinematic scrims (Netflix / Prime): solid on the left where the text sits, open on the right, deep at the foot.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Harbor.Ink, 0.32f to Harbor.Ink.copy(.88f), 0.62f to Harbor.Ink.copy(.25f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Harbor.Ink.copy(.35f), 0.25f to Color.Transparent, 0.6f to Harbor.Ink.copy(.6f), 1f to Harbor.Ink)))
    }
}

@Composable
fun MetaLine(parts: List<String?>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        parts.filterNotNull().filter { it.isNotBlank() }.forEachIndexed { i, p ->
            if (i > 0) Box(Modifier.size(4.dp).clip(RoundedCornerShape(50)).background(Harbor.Fg.copy(.5f)))
            Text(p, color = Harbor.Fg.copy(.85f), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
        }
    }
}

/** Netflix-style boxed age rating ("U/A 13+", "R"…). */
@Composable
fun RatingBadge(rating: String?) {
    if (rating.isNullOrBlank()) return
    Box(Modifier.border(1.5.dp, Harbor.Fg.copy(.6f), RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 1.dp)) {
        Text(rating, color = Harbor.Fg.copy(.9f), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Red "TOP 10" badge with the rank (Netflix). */
@Composable
fun Top10Badge(rank: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFE50914)).padding(horizontal = 5.dp, vertical = 2.dp)) {
            Text("TOP\n10", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, lineHeight = 9.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text("#$rank", color = Harbor.Fg, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}
