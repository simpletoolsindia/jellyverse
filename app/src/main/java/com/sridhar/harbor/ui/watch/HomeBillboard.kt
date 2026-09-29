package com.sridhar.harbor.ui.watch

import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.R
import com.sridhar.harbor.data.jellyfin.BaseItem
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.pressable
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.components.rememberResumed
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Billboard Home hero, 2026 "soft UI" style: the page glows with the colour of the current title, and the title
 * sits in a floating rounded card – textless backdrop art with the logo on top (no doubled titles), glass pill
 * buttons, swipe or auto-advance. Each page is clipped, so neighbours never bleed into the card.
 */
@Composable
fun BillboardHero(items: List<BaseItem>, onItem: (String) -> Unit, onPlay: (BaseItem) -> Unit) {
    val cfg = rememberConfig()
    val jf = LocalContainer.current.jellyfin
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { items.size }
    val favs = remember { mutableStateMapOf<String, Boolean>() }
    val resumed = rememberResumed()
    LaunchedEffect(pager, items.size, resumed) {
        while (resumed && items.size > 1) {
            delay(7000)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % items.size, animationSpec = tween(700))
        }
    }
    val current = items.getOrNull(pager.currentPage) ?: return
    // Ambient glow: the artwork's own colour washes the top of the page, then fades into the background.
    val glow = com.sridhar.harbor.ui.music.rememberArtColor(jf.backdropUrl(cfg, current, 300), current.name)
    val glowAnim by androidx.compose.animation.animateColorAsState(glow, tween(900), label = "glow")
    val drift by rememberInfiniteTransition(label = "kb").animateFloat(1.02f, 1.09f,
        infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse), label = "z")
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to glowAnim.copy(alpha = if (com.sridhar.harbor.ui.theme.Looks.isDark) .55f else .35f),
        0.75f to Harbor.Ink.copy(alpha = 0f)))) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 64.dp)) {
            HorizontalPager(pager, Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 12.dp) { page ->
                val item = items[page]
                val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
                Box(
                    Modifier.fillMaxWidth().aspectRatio(0.8f)
                        .graphicsLayer { val s = 1f - 0.05f * off.absoluteValue.coerceIn(0f, 1f); scaleX = s; scaleY = s }
                        .shadow(24.dp, RoundedCornerShape(30.dp), ambientColor = glowAnim, spotColor = glowAnim)
                        .clip(RoundedCornerShape(30.dp)).clickable { onItem(item.id) },
                ) {
                    // Parallax inside the clipped card: the art moves slower than the card.
                    NetImage(jf.backdropUrl(cfg, item, 1280), Modifier.fillMaxSize().graphicsLayer {
                        translationX = off * size.width * 0.25f; scaleX = drift; scaleY = drift
                    }, fallback = item.name)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = .88f))))
                    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp)) {
                        val logo = jf.logoUrl(cfg, item)
                        if (logo != null) NetImage(logo, Modifier.width(210.dp).height(72.dp), contentScale = ContentScale.Fit, alignment = Alignment.BottomStart, fallback = item.name)
                        else Text(item.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color.White,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (listOfNotNull(item.year?.toString(), item.communityRating?.let { "★ %.1f".format(it) }) + item.genres.take(2)).forEach { t ->
                                Text(t, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = .16f)).padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.weight(1f).height(48.dp).clip(CircleShape).background(Color.White)
                                .pressable { if (item.type == "Movie") onPlay(item) else onItem(item.id) },
                                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(26.dp)); Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.play), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            val fav = favs[item.id] ?: (item.userData?.isFavorite == true)
                            GlassCircle(if (fav) Icons.Rounded.Check else Icons.Rounded.Add, stringResource(R.string.my_list)) {
                                favs[item.id] = !fav; scope.launch(com.sridhar.harbor.CrashGuard) { runCatching { jf.setFavorite(item.id, !fav) } }
                            }
                            GlassCircle(Icons.Outlined.Info, stringResource(R.string.more_info)) { onItem(item.id) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(items.size) { i ->
                    val active = i == pager.currentPage
                    val w by androidx.compose.animation.core.animateDpAsState(if (active) 18.dp else 6.dp, label = "dot")
                    Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(w).clip(CircleShape).background(if (active) Harbor.Fg else Harbor.line(.25f)))
                }
            }
        }
    }
}

/** Frosted round button used on artwork. */
@Composable
private fun GlassCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f))
        .border(1.dp, Color.White.copy(alpha = .28f), CircleShape).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(10.dp)).pressable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = Harbor.Fg, modifier = Modifier.size(26.dp))
        Text(label, fontSize = 11.sp, color = Harbor.Fg.copy(alpha = .85f))
    }
}

/** Numbered "Top 10" row: a huge outlined rank beside each poster. */
@Composable
fun Top10Row(items: List<BaseItem>, onItem: (String) -> Unit) {
    if (items.isEmpty()) return
    val cfg = rememberConfig()
    val jf = LocalContainer.current.jellyfin
    Column(Modifier.padding(top = 18.dp)) {
        Text(stringResource(R.string.top10_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
                Box(Modifier.width(if (i == 9) 180.dp else 160.dp).height(170.dp).pressable { onItem(item.id) }) {
                    Text("${i + 1}", modifier = Modifier.align(Alignment.BottomStart).offset(y = 22.dp),
                        style = TextStyle(fontSize = 130.sp, fontWeight = FontWeight.Black, letterSpacing = (-10).sp, color = Harbor.Fg.copy(alpha = .9f),
                            drawStyle = Stroke(width = 5f)))
                    NetImage(jf.posterUrl(cfg, item, 400), Modifier.align(Alignment.BottomEnd).width(106.dp).aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp)), fallback = item.name)
                }
            }
        }
    }
}

/**
 * Endless poster marquee that glides on its own and pauses while you touch it – a low-effort way to stumble on
 * something to watch. Posters tilt slightly by their distance from the centre for depth.
 */
@Composable
fun PosterMarquee(title: String, items: List<BaseItem>, onItem: (String) -> Unit) {
    if (items.size < 4) return
    val cfg = rememberConfig()
    val jf = LocalContainer.current.jellyfin
    val state = rememberLazyListState(initialFirstVisibleItemIndex = items.size * 50)
    val dragged by state.interactionSource.collectIsDraggedAsState()
    val resumed = rememberResumed()
    LaunchedEffect(dragged, resumed) {
        if (dragged || !resumed) return@LaunchedEffect
        delay(1200)
        while (true) state.animateScrollBy(240f, tween(4000, easing = LinearEasing))
    }
    Column(Modifier.padding(top = 18.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(10.dp))
        LazyRow(state = state, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(Int.MAX_VALUE / 2) { i ->
                val item = items[i % items.size]
                Box(Modifier.width(118.dp).aspectRatio(2f / 3f).graphicsLayer {
                    val info = state.layoutInfo
                    val me = info.visibleItemsInfo.firstOrNull { it.index == i }
                    if (me != null) {
                        val centre = (info.viewportEndOffset + info.viewportStartOffset) / 2f
                        val d = ((me.offset + me.size / 2f) - centre) / centre
                        rotationY = -d * 14f; val s = 1f - 0.08f * d.absoluteValue.coerceAtMost(1f); scaleX = s; scaleY = s
                        cameraDistance = 12f * density
                    }
                }.clip(RoundedCornerShape(10.dp)).pressable { onItem(item.id) }) {
                    NetImage(jf.posterUrl(cfg, item, 400), Modifier.fillMaxSize(), fallback = item.name)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(count: Int, content: @Composable androidx.compose.foundation.lazy.LazyItemScope.(Int) -> Unit) =
    items(count, itemContent = content)
