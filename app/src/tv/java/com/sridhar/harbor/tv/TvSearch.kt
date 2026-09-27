package com.sridhar.harbor.tv

import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.jellyfin.BaseItem
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.theme.Harbor
import com.sridhar.harbor.ui.watch.SearchViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(FlowPreview::class)
@Composable
fun TvSearch(onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    val jf = container.jellyfin
    val vm = viewModel { SearchViewModel(container) }
    LaunchedEffect(Unit) { snapshotFlow { vm.query.trim() }.debounce(400).distinctUntilChanged().collect { vm.search(it) } }
    val suggestions by produceState<List<BaseItem>>(emptyList()) { value = runCatching { jf.randomUnwatched(null) }.getOrDefault(emptyList()) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.query = it }
    }
    val shown = if (vm.query.length >= 2) vm.results else suggestions

    LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row {
                    val fm = androidx.compose.ui.platform.LocalFocusManager.current
                    OutlinedTextField(vm.query, { vm.query = it }, Modifier.width(560.dp)
                        .onFocusChanged { com.sridhar.harbor.remote.RemoteServer.fieldFocused(it.isFocused, "Search") }.onPreviewKeyEvent { e ->
                        if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key == androidx.compose.ui.input.key.Key.DirectionDown)
                            fm.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) else false
                    }, singleLine = true, shape = RoundedCornerShape(16.dp),
                        placeholder = { Text(stringResource(R.string.search_movies_shows)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Harbor.Surface, focusedContainerColor = Harbor.SurfaceHigh,
                            focusedBorderColor = Color.White, unfocusedBorderColor = Color.Transparent))
                    Spacer(Modifier.width(14.dp))
                    TvButton(stringResource(R.string.voice), Icons.Rounded.Mic) {
                        runCatching { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)) }
                    }
                }
                Text(if (vm.query.length >= 2) stringResource(R.string.s_1_s_results, vm.results.size) else stringResource(R.string.something_new_to_watch), color = Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 28.dp))
            }
        }
        items(shown, key = { it.id }) { item ->
            PosterTile(item.name, jf.posterUrl(cfg, item, 320), width = 170.dp, onFocus = {}) { onOpen(item.seriesId ?: item.id) }
        }
    }
}
