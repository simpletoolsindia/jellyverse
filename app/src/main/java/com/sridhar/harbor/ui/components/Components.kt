@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.sridhar.harbor.ui.components

import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.ServerConfig
import com.sridhar.harbor.ui.theme.Harbor

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No container") }

@Composable
fun ProvideContainer(container: AppContainer, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalContainer provides container, content = content)

@Composable
fun rememberConfig(): ServerConfig {
    val c = LocalContainer.current
    val cfg by c.config.collectAsState()
    return cfg ?: ServerConfig()
}

fun Modifier.shimmer(): Modifier = composed {
    if (reducedMotion()) return@composed drawWithContent { drawRect(Harbor.SurfaceHigh) }   // static placeholder
    val t = rememberInfiniteTransition(label = "shimmer")
    // A soft diagonal sheen glides across, with a slow "breath" on the base so long waits still feel alive.
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Restart), label = "x")
    val glow by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "glow")
    val tint = Harbor.Violet
    drawWithContent {
        drawRect(Harbor.SurfaceHigh)
        drawRect(tint.copy(alpha = 0.025f + 0.035f * glow))
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Harbor.line(0.05f), Harbor.line(0.10f), Harbor.line(0.05f), Color.Transparent),
                start = androidx.compose.ui.geometry.Offset(size.width * x, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width * (x + 0.8f), size.height),
            )
        )
    }
}

/** Content that just replaced a skeleton: fades and rises in once (skipped on reduced motion). */
fun Modifier.fadeInOnce(): Modifier = composed {
    if (reducedMotion()) return@composed this
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(320, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) }
    graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 14.dp.toPx() }
}

/** Placeholder for a title page (backdrop, poster, title, buttons, text) while it loads. */
@Composable
fun SkeletonDetail(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(300.dp).shimmer())
        Row(Modifier.padding(horizontal = 20.dp).offset(y = (-60).dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.width(110.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).shimmer())
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(180.dp).height(26.dp).clip(RoundedCornerShape(8.dp)).shimmer())
                Box(Modifier.width(120.dp).height(14.dp).clip(RoundedCornerShape(6.dp)).shimmer())
            }
        }
        Column(Modifier.padding(horizontal = 20.dp).offset(y = (-40).dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).shimmer())
            repeat(4) { i -> Box(Modifier.fillMaxWidth(if (i == 3) 0.6f else 1f).height(14.dp).clip(RoundedCornerShape(6.dp)).shimmer()) }
        }
    }
}

/** Placeholder list rows (artwork + two lines) for songs, albums, requests… */
@Composable
fun SkeletonRows(count: Int = 6, modifier: Modifier = Modifier, circle: Boolean = false) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(count) { i ->
            Row(Modifier.enterRise(i), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(if (circle) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(10.dp)).shimmer())
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(0.55f + (i % 3) * 0.1f).height(14.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                    Box(Modifier.fillMaxWidth(0.35f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                }
            }
        }
    }
}

fun Modifier.glass(shape: RoundedCornerShape = RoundedCornerShape(20.dp)) =
    this.clip(shape).background(Harbor.line(0.06f)).border(1.dp, Harbor.line(0.08f), shape)

@Composable
fun NetImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, fallback: String? = null, alignment: Alignment = Alignment.Center) {
    if (url != null && url.startsWith(com.sridhar.harbor.data.jellyfin.COLLAGE)) { Collage(url.removePrefix(com.sridhar.harbor.data.jellyfin.COLLAGE), modifier, fallback); return }
    SubcomposeAsyncImage(
        model = url, contentDescription = null, contentScale = contentScale, modifier = modifier, alignment = alignment,
        loading = { Box(Modifier.fillMaxSize().shimmer()) },
        error = {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Harbor.SurfaceHigh, Harbor.Surface))),
                contentAlignment = Alignment.Center) {
                if (fallback != null) Text(fallback, style = MaterialTheme.typography.labelLarge, color = Harbor.TextDim,
                    modifier = Modifier.padding(8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        },
    )
}

@Composable
fun PosterCard(
    imageUrl: String?, title: String, subtitle: String? = null, width: Dp = 124.dp,
    progress: Float = 0f, badge: (@Composable () -> Unit)? = null, played: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(Modifier.width(width).then(
        if (onLongClick == null) Modifier.pressable(onClick = onClick)
        else Modifier.combinedClickable(onLongClick = { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onLongClick() }, onClick = onClick),
    )) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))) {
            NetImage(imageUrl, Modifier.fillMaxSize(), fallback = title)
            if (badge != null) Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { badge() }
            if (played) Icon(Icons.Rounded.CheckCircle, null, tint = Harbor.Mint,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).background(Color.Black.copy(.5f), CircleShape))
            if (progress > 0f) ProgressStrip(progress, Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Harbor.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun WideCard(imageUrl: String?, title: String, subtitle: String?, progress: Float, width: Dp = 260.dp, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(Modifier.width(width).then(
        if (onLongClick == null) Modifier.pressable(onClick = onClick)
        else Modifier.combinedClickable(onLongClick = { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onLongClick() }, onClick = onClick)
    )) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))) {
            NetImage(imageUrl, Modifier.fillMaxSize(), fallback = title)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .55f)))))
            Box(Modifier.align(Alignment.Center).size(44.dp).glass(RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
            }
            if (progress > 0f) ProgressStrip(progress, Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Harbor.TextDim, maxLines = 1)
    }
}

@Composable
fun ProgressStrip(progress: Float, modifier: Modifier = Modifier) {
    val animated = animateFloatAsState(progress.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessLow), label = "strip")
    Box(modifier.fillMaxWidth().height(4.dp).drawBehind {
        drawRect(Harbor.line(0.18f))
        drawRect(Harbor.accentH, size = size.copy(width = size.width * animated.value))
    })
}

/** Pill progress bar; the fraction animates and is read only in the draw phase. */
@Composable
fun GradientProgress(progress: Float, modifier: Modifier = Modifier, height: Dp = 6.dp, brush: Brush = Harbor.accentH) {
    val animated = animateFloatAsState(progress.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessLow), label = "progress")
    Box(modifier.fillMaxWidth().height(height).drawBehind {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Harbor.line(0.08f), cornerRadius = r)
        val w = size.width * animated.value
        if (w > 0f) drawRoundRect(brush, size = size.copy(width = w.coerceAtLeast(size.height)), cornerRadius = r)
    })
}

/**
 * Tactile press feedback: springs down to [pressedScale] while held.
 * Scale is applied in graphicsLayer so it never recomposes the content.
 */
fun Modifier.pressable(pressedScale: Float = 0.95f, enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) pressedScale else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "press")
    this.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
}

/** Items fade + rise in once, staggered by [index] — for lists that appear after loading. */
fun Modifier.enterRise(index: Int): Modifier = composed {
    if (reducedMotion()) return@composed this   // low-end: items appear at once, no per-item animation
    val shown = remember { Animatable(0f) }
    // Stagger only the first screenful; items revealed by scrolling later rise at once (no lag on fast flings).
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(if (index < 12) 380 else 260, delayMillis = if (index < 12) index * 40 else 0, easing = FastOutSlowInEasing)) }
    graphicsLayer { alpha = shown.value; translationY = (1f - shown.value) * 28.dp.toPx() }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, color = Harbor.VioletSoft) }
    }
}

@Composable
fun <T> Rail(
    title: String, items: List<T>, key: (T) -> Any, action: String? = null, onAction: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    Column(Modifier.padding(vertical = 10.dp)) {
        SectionHeader(title, action = action, onAction = onAction)
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(items, key = { _, it -> key(it) }) { i, it -> Box(Modifier.animateItem().enterRise(i)) { content(it) } }
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Harbor.line(.08f)),
        contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(16.dp),
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = if (enabled) 1f else .5f }.background(Harbor.accentH),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) { Icon(icon, null, tint = Color.White); Spacer(Modifier.width(8.dp)) }
                Text(text, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MessageState(title: String, message: String?, modifier: Modifier = Modifier, icon: ImageVector = Icons.Rounded.CloudOff, onRetry: (() -> Unit)? = null, actionLabel: String = "Try again") {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Errors (the default cloud icon) get the drifting jellyfish; everything else keeps its icon badge.
        if (icon == Icons.Rounded.CloudOff) AdriftJelly(Modifier.size(120.dp))
        else Box(Modifier.size(72.dp).clip(CircleShape).background(Harbor.accent), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Harbor.TextDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (onRetry != null) { Spacer(Modifier.height(16.dp)); TextButton(onClick = onRetry) { Text(actionLabel, color = Harbor.VioletSoft) } }
    }
}

fun Throwable.friendly(): String = when (this) {
    is retrofit2.HttpException -> when (code()) {
        401, 403 -> L10n.s(R.string.not_authorised_sign_in_again_in)
        404 -> L10n.s(R.string.not_found_on_server)
        else -> L10n.s(R.string.server_error_http_1_s, code())
    }
    is java.net.UnknownHostException -> L10n.s(R.string.can_t_resolve_server_are_you)
    is java.net.ConnectException, is java.net.SocketTimeoutException -> L10n.s(R.string.server_unreachable_check_the_address_and)
    else -> message ?: javaClass.simpleName
}

/** JellyVerse brand mark (a play-shaped jellyfish bell swimming forward) on its blue tile. */
@Composable
fun HarborLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.28f))) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.sridhar.harbor.R.drawable.ic_launcher_background), null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop)
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.sridhar.harbor.R.drawable.ic_launcher_foreground), stringResource(R.string.jellyverse), Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop)
    }
}

/** TV remotes: ▲/▼ move between form fields instead of being swallowed by the text caret. */
fun Modifier.dpadFieldNav(): Modifier = composed {
    val fm = androidx.compose.ui.platform.LocalFocusManager.current
    // On TV, a paired phone pops its keyboard as soon as a text field is focused.
    this.onFocusChanged { com.sridhar.harbor.remote.RemoteServer.fieldFocused(it.isFocused) }.onPreviewKeyEvent { e ->
        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (e.key) {
            androidx.compose.ui.input.key.Key.DirectionDown -> fm.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
            androidx.compose.ui.input.key.Key.DirectionUp -> fm.moveFocus(androidx.compose.ui.focus.FocusDirection.Up)
            else -> false
        }
    }
}

/** Brand footer shown on welcome, lock and settings screens. */
@Composable
fun MadeWithLove(modifier: Modifier = Modifier) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    // TVs usually have no browser – there the credit is plain text and never takes remote focus.
    val tv = androidx.compose.ui.platform.LocalContext.current.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
    Row(modifier.clip(RoundedCornerShape(50)).then(if (tv) Modifier else Modifier.clickable { runCatching { uri.openUri("https://simpletools.in") } })
        .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.made_with), color = Harbor.TextDim, fontSize = 12.sp)
        Text("❤", color = Harbor.Rose, fontSize = 12.sp)
        Text(stringResource(R.string.by), color = Harbor.TextDim, fontSize = 12.sp)
        Text(stringResource(R.string.simpletools_in), fontSize = 12.sp, fontWeight = FontWeight.Bold, style = androidx.compose.ui.text.TextStyle(brush = Harbor.accentH))
    }
}

/** Poster for a collection without artwork: its movies' posters tiled 2×2 (or fewer), fading in. */
@Composable
private fun Collage(collectionId: String, modifier: Modifier, fallback: String?) {
    val jf = LocalContainer.current.jellyfin
    val urls by androidx.compose.runtime.produceState<List<String>?>(null, collectionId) { value = jf.collagePosters(collectionId) }
    val list = urls
    when {
        list == null -> Box(modifier.shimmer())
        list.isEmpty() -> NetImage(null, modifier, fallback = fallback)
        list.size == 1 -> NetImage(list[0], modifier, fallback = fallback)
        else -> androidx.compose.foundation.layout.Column(modifier.background(Harbor.Ink)) {
            val rows = if (list.size >= 4) list.chunked(2) else listOf(list.take(2))
            rows.forEach { row ->
                androidx.compose.foundation.layout.Row(Modifier.weight(1f).fillMaxWidth()) {
                    row.forEach { u -> NetImage(u, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}

/** Placeholder shelf (title bar + card outlines) that shimmers while a row loads – feels faster than a spinner. */
@Composable
fun SkeletonShelf(cardWidth: Dp = 124.dp, aspect: Float = 2f / 3f, count: Int = 6, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 10.dp)) {
        Box(Modifier.padding(horizontal = 20.dp).width(160.dp).height(18.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(count) { i ->
                Box(Modifier.enterRise(i).width(cardWidth).aspectRatio(aspect).clip(RoundedCornerShape(14.dp)).shimmer())
            }
        }
    }
}
