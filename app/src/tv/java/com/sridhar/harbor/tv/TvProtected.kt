package com.sridhar.harbor.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.R
import com.sridhar.harbor.ui.components.JellyLoader
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.PinDialog
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.parental.ProtectedTitlesViewModel
import com.sridhar.harbor.ui.theme.Harbor

/** TV "🔞 18+" menu: PIN first, then every 18+ / locked title. */
@Composable
fun TvProtected(onOpen: (String) -> Unit, onCancel: () -> Unit) {
    val c = LocalContainer.current
    val cfg = rememberConfig()
    val vm = viewModel { ProtectedTitlesViewModel(c) }
    if (!vm.unlocked) {
        val wrong = stringResource(R.string.pin_wrong)
        PinDialog(stringResource(R.string.pin_enter), stringResource(R.string.adult_menu_title), onDismiss = onCancel) { if (vm.submitPin(it)) null else wrong }
        return
    }
    Column(Modifier.fillMaxSize().padding(start = 120.dp, top = 40.dp, end = 40.dp)) {
        Text(stringResource(R.string.adult_menu_title), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
        val list = vm.items
        when {
            list == null && vm.error != null -> Text(vm.error.orEmpty(), color = Harbor.Rose, modifier = Modifier.padding(top = 24.dp))
            list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { JellyLoader() }
            list.isEmpty() -> Text(stringResource(R.string.adult_empty), color = Harbor.TextDim, modifier = Modifier.padding(top = 24.dp))
            // Adaptive columns + generous vertical spacing so the focused (scaled-up) poster never covers the next row.
            else -> LazyVerticalGrid(GridCells.Adaptive(132.dp), contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp, start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(30.dp)) {
                items(list, key = { it.id }) { item ->
                    androidx.compose.foundation.layout.BoxWithConstraints {
                        val w = maxWidth
                        Column {
                            PosterTile(item.seriesName ?: item.name, c.jellyfin.posterUrl(cfg, item, 300), width = w, onFocus = {}) { onOpen(item.id) }
                            Text(item.seriesName ?: item.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                            Text(listOfNotNull(item.year?.toString(), item.officialRating).joinToString(" · "), color = Harbor.TextDim, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
