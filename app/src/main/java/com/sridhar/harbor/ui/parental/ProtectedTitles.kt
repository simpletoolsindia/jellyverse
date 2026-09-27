package com.sridhar.harbor.ui.parental

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.R
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.jellyfin.BaseItem
import com.sridhar.harbor.ui.components.JellyLoader
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.PinDialog
import com.sridhar.harbor.ui.components.PosterCard
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.launch

/** Loads every 18+ / locked title (shared by the phone screen and the TV menu). */
class ProtectedTitlesViewModel(private val c: AppContainer) : ViewModel() {
    var unlocked by mutableStateOf(c.parental.isUnlocked()); private set
    var items by mutableStateOf<List<BaseItem>?>(null); private set
    var error by mutableStateOf<String?>(null); private set

    init { if (unlocked) load() }

    fun submitPin(pin: String): Boolean = c.parental.verify(pin).also { if (it) { unlocked = true; load() } }

    fun load() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        error = null
        runCatching {
            val all = c.jellyfin.ratedTitles()
            c.parental.refreshAdultSeries { all }
            val listed = all.filter(c.parental::isProtected)
            // Individually locked episodes / items that aren't a top-level Movie or Series.
            val extraIds = c.parental.state.value.locked - listed.map { it.id }.toSet()
            listed + (if (extraIds.isEmpty()) emptyList() else runCatching { c.jellyfin.itemsByIds(extraIds.toList()) }.getOrDefault(emptyList()))
        }.onSuccess { items = it }.onFailure { error = it.friendly() }
    }
}

@Composable
fun ProtectedTitlesScreen(onItem: (String) -> Unit, onBack: () -> Unit) {
    val c = LocalContainer.current
    val cfg = rememberConfig()
    val vm = viewModel { ProtectedTitlesViewModel(c) }
    if (!vm.unlocked) {
        val wrong = stringResource(R.string.pin_wrong)
        Box(Modifier.fillMaxSize().background(Harbor.Ink))
        PinDialog(stringResource(R.string.pin_enter), stringResource(R.string.adult_menu_title), onDismiss = onBack) { if (vm.submitPin(it)) null else wrong }
        return
    }
    Column(Modifier.fillMaxSize().background(Harbor.Ink).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.adult_menu_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton({ c.parental.relock(); onBack() }) { Icon(Icons.Rounded.Lock, stringResource(R.string.parental_lock_now), tint = Harbor.Amber) }
        }
        val list = vm.items
        when {
            vm.error != null && list == null -> MessageState(stringResource(R.string.couldn_t_reach_jellyfin), vm.error, onRetry = { vm.load() })
            list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { JellyLoader() }
            list.isEmpty() -> MessageState(stringResource(R.string.adult_empty), null, icon = Icons.Rounded.Lock)
            else -> LazyVerticalGrid(GridCells.Adaptive(112.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(list, key = { it.id }) { item ->
                    PosterCard(c.jellyfin.posterUrl(cfg, item), item.seriesName ?: item.name, listOfNotNull(item.year?.toString(), item.officialRating).joinToString(" · "), width = 112.dp) { onItem(item.id) }
                }
            }
        }
    }
}
