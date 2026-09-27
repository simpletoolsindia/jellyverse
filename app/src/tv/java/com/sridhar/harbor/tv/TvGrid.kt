package com.sridhar.harbor.tv

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.jellyfin.BaseItem
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.theme.Harbor
import com.sridhar.harbor.ui.watch.LibFilter
import com.sridhar.harbor.ui.watch.LibSort
import com.sridhar.harbor.ui.watch.LibraryViewModel

@Composable
fun TvGrid(collectionType: String, onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val view by produceState<BaseItem?>(null, collectionType) {
        value = runCatching { container.jellyfin.views().firstOrNull { it.collectionType == collectionType } }.getOrNull()
    }
    val v = view ?: return Box(Modifier.fillMaxSize(), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader(color = Harbor.VioletSoft) }
    TvGridContent(v, onOpen)
}

@Composable
private fun TvGridContent(view: BaseItem, onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    val jf = container.jellyfin
    val vm = viewModel(key = "tvlib-${view.id}") { LibraryViewModel(container, view.id, view.collectionType) }
    var focused by remember { mutableStateOf<BaseItem?>(null) }
    val grid = rememberLazyGridState()
    val nearEnd by remember { derivedStateOf { (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) > grid.layoutInfo.totalItemsCount - 14 } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }

    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop(focused?.let { jf.backdropUrl(cfg, it, 1280) }, preview = focused)
        LazyVerticalGrid(GridCells.Adaptive(132.dp), Modifier.fillMaxSize(), grid,
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 36.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(30.dp)) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(view.name, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
                    Text(focused?.let { listOfNotNull(it.name, it.year?.toString()).joinToString(" · ") } ?: stringResource(R.string.s_1_s_titles, vm.total), color = Harbor.TextDim, fontSize = 16.sp)
                    Row(Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LibSort.entries.forEach { s -> Chip(s.label, vm.sort == s) { vm.selectSort(s) } }
                        Box(Modifier.padding(horizontal = 8.dp))
                        LibFilter.entries.forEach { f -> Chip(f.label, vm.filter == f) { vm.selectFilter(f) } }
                    }
                    com.sridhar.harbor.ui.watch.LibraryFilterBar(vm) { label, active, dropdown, onClick -> Chip(if (dropdown) "$label ▾" else "✕ $label", active, onClick) }
                }
            }
            items(vm.items, key = { it.id }) { item ->
                // TV-sized poster that fills its cell, with the title and year under it (never hidden behind the next row).
                androidx.compose.foundation.layout.BoxWithConstraints {
                    val cellWidth = maxWidth
                    Column {
                        PosterTile(item.name, jf.posterUrl(cfg, item, 320), width = cellWidth, progress = item.progress,
                            badge = item.userData?.unplayedCount?.takeIf { it > 0 }?.toString(), onFocus = { focused = item }) { onOpen(item.id) }
                        Text(item.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        Text(listOfNotNull(item.year?.toString(), item.officialRating, item.communityRating?.let { "★ %.1f".format(it) }).joinToString(" · "),
                            color = Harbor.TextDim, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
            if (vm.loading) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader(color = Harbor.VioletSoft) }
            }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(label, maxLines = 1, softWrap = false, color = if (focused) Color.Black else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(when { focused -> Color.White; selected -> Harbor.Violet; else -> Color.White.copy(.1f) })
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp))
}
