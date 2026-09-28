package com.sridhar.harbor.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sridhar.harbor.R
import com.sridhar.harbor.data.jellyfin.LibraryIndex
import com.sridhar.harbor.ui.theme.Harbor

private enum class Facet { Language, Year, Genre }

/**
 * Language / Year / Genre filters for a library. The [chip] slot lets phone (Material chips) and TV (focus-ring chips)
 * render them in their own style; the picker dialog is shared and works with touch and D-pad alike.
 */
@Composable
fun LibraryFilterBar(vm: LibraryViewModel, chip: @Composable (label: String, active: Boolean, dropdown: Boolean, onClick: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf<Facet?>(null) }
    val idx = vm.index
    Row(Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chip(vm.language?.let { LibraryIndex.displayName(it) } ?: stringResource(R.string.f_language), vm.language != null, true) { open = Facet.Language }
        chip(vm.decade?.let { "${it}s" } ?: stringResource(R.string.year), vm.decade != null, true) { open = Facet.Year }
        chip(vm.genre ?: stringResource(R.string.f_genre), vm.genre != null, true) { open = Facet.Genre }
        if (vm.activeFilters > 0) chip(stringResource(R.string.f_clear), false, false) { vm.clearFilters() }
    }
    when (open) {
        Facet.Language -> FacetDialog(stringResource(R.string.f_language),
            idx?.languages.orEmpty().map { (code, n) -> code to "${LibraryIndex.displayName(code)}  $n" }, vm.language, loading = idx == null,
            onPick = { vm.selectLanguage(it); open = null }, onDismiss = { open = null })
        Facet.Year -> FacetDialog(stringResource(R.string.year), idx?.decades.orEmpty().map { it to "${it}s" }, vm.decade, loading = idx == null,
            onPick = { vm.selectDecade(it); open = null }, onDismiss = { open = null })
        Facet.Genre -> FacetDialog(stringResource(R.string.f_genre), idx?.genres.orEmpty().map { it to it }, vm.genre, loading = idx == null,
            onPick = { vm.selectGenre(it); open = null }, onDismiss = { open = null })
        null -> {}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FacetDialog(title: String, options: List<Pair<T, String>>, selected: T?, loading: Boolean, onPick: (T?) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    val colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Harbor.Violet, selectedLabelColor = Harbor.Fg)
    Dialog(onDismiss) {
        Column(Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(20.dp)).background(Harbor.Surface).padding(20.dp).testTag("facet_dialog")) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Harbor.Fg)
            Spacer(Modifier.height(12.dp))
            FlowRow(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected == null, { onPick(null) }, { Text(stringResource(R.string.f_any)) }, colors = colors, modifier = Modifier.focusRequester(first))
                options.forEach { (v, label) -> FilterChip(selected == v, { onPick(v) }, { Text(label) }, colors = colors) }
            }
            if (loading) Text(stringResource(R.string.loading_2), color = Harbor.TextDim, fontSize = 13.sp)
            TextButton(onDismiss, Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.close)) }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
