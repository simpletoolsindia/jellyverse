package com.sridhar.harbor.ui.discover

import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.seerr.MediaStatus
import com.sridhar.harbor.data.seerr.Permission
import com.sridhar.harbor.data.seerr.SeerrDetails
import com.sridhar.harbor.data.seerr.SeerrMedia
import com.sridhar.harbor.data.seerr.SeerrRepository.Companion.tmdb
import com.sridhar.harbor.data.seerr.SeerrUser
import com.sridhar.harbor.ui.components.GradientButton
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.Pill
import com.sridhar.harbor.ui.components.Rail
import com.sridhar.harbor.ui.components.formatRuntime
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.components.glass
import com.sridhar.harbor.ui.player.PlayerActivity
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class SeerrDetailViewModel(private val c: AppContainer, val type: String, val id: Int) : ViewModel() {
    var details by mutableStateOf<SeerrDetails?>(null); private set
    var recs by mutableStateOf<List<SeerrMedia>>(emptyList()); private set
    var me by mutableStateOf<SeerrUser?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var requesting by mutableStateOf(false); private set

    init { load() }

    fun load(fresh: Boolean = false) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        error = null
        runCatching {
            val r = async { c.seerr.recommendations(type, id) }
            val m = async { runCatching { c.seerr.me() }.getOrNull() }
            details = c.seerr.details(type, id, fresh)
            recs = r.await(); me = m.await()
        }.onFailure { error = it.friendly() }
    }

    fun request(seasons: List<Int>?, onResult: (String) -> Unit) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        requesting = true
        runCatching { c.seerr.request(type, id, seasons) }
            .onSuccess { onResult(L10n.s(R.string.requested_it_ll_show_up_in)); load(fresh = true) }
            .onFailure { onResult(it.friendly()) }
        requesting = false
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeerrDetailScreen(type: String, id: Int, onOpen: (String, Int) -> Unit, onBack: () -> Unit, onJellyfin: (String) -> Unit = {}) {
    val container = LocalContainer.current
    val ctx = LocalContext.current
    val vm = viewModel(key = "seerr-$type-$id") { SeerrDetailViewModel(container, type, id) }
    var seasonPicker by remember { mutableStateOf(false) }
    val toast: (String) -> Unit = { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }

    Box(Modifier.fillMaxSize().background(Harbor.Ink)) {
        val d = vm.details
        if (d == null) {
            if (vm.error != null) MessageState(stringResource(R.string.couldn_t_load), vm.error, Modifier.align(Alignment.Center), onRetry = { vm.load() })
            else com.sridhar.harbor.ui.components.JellyLoader(Modifier.align(Alignment.Center))
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(420.dp)) {
                    NetImage(tmdb(d.backdropPath, "w1280"), Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Harbor.scrimBottom()))
                    Row(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.width(110.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))) {
                            NetImage(tmdb(d.posterPath, "w342"), Modifier.fillMaxSize(), fallback = d.displayTitle)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(if (type == "tv") stringResource(R.string.series_2) else stringResource(R.string.movie_2), style = MaterialTheme.typography.labelSmall, color = Harbor.Coral)
                            Text(d.displayTitle, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(d.year, formatRuntime(d.runtime ?: d.episodeRunTime.firstOrNull()),
                                    d.numberOfSeasons?.let { "$it season${if (it == 1) "" else "s"}" }).joinToString("  ·  "),
                                color = Harbor.TextDim,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                d.voteAverage?.takeIf { it > 0 }?.let { Pill("★ %.1f".format(it), Harbor.Amber) }
                                val st = MediaStatus.of(d.mediaInfo?.status)
                                if (st != MediaStatus.Unknown) Pill(st.label, when (st) {
                                    MediaStatus.Available, MediaStatus.Partial -> Harbor.Mint; MediaStatus.Pending -> Harbor.Amber; else -> Harbor.Sky
                                })
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    val status = MediaStatus.of(d.mediaInfo?.status)
                    val canRequest = vm.me?.let { it.has(Permission.REQUEST) || it.has(if (type == "tv") Permission.REQUEST_TV else Permission.REQUEST_MOVIE) } ?: true
                    val jfId = d.mediaInfo?.jellyfinMediaId
                    if (jfId != null && status in setOf(MediaStatus.Available, MediaStatus.Partial)) {
                        GradientButton(stringResource(R.string.watch_in_jellyverse), {
                            if (type == "movie") PlayerActivity.start(ctx, jfId) else onJellyfin(jfId)
                        }, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
                        Spacer(Modifier.height(10.dp))
                    }
                    val requestable = when (type) {
                        "tv" -> requestableSeasons(d).isNotEmpty()
                        else -> status == MediaStatus.Unknown
                    }
                    if (requestable && canRequest) {
                        val label = if (vm.requesting) stringResource(R.string.requesting) else if (type == "tv" && status == MediaStatus.Partial) stringResource(R.string.request_more_seasons) else stringResource(R.string.request)
                        if (jfId != null) OutlinedButton({ if (type == "tv") seasonPicker = true else vm.request(null, toast) }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Rounded.AddCircle, null); Spacer(Modifier.width(8.dp)); Text(label)
                        } else GradientButton(label, { if (type == "tv") seasonPicker = true else vm.request(null, toast) }, Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.AddCircle, enabled = !vm.requesting)
                    } else if (status == MediaStatus.Pending || status == MediaStatus.Processing) {
                        Text(if (status == MediaStatus.Pending) stringResource(R.string.requested_waiting_for_approval) else stringResource(R.string.approved_downloading_now),
                            color = Harbor.Amber, modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(14.dp))
                    }
                    d.trailerUrl?.let { url ->
                        TextButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                            Icon(Icons.Rounded.Movie, null, tint = Harbor.VioletSoft); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.watch_trailer), color = Harbor.VioletSoft)
                        }
                    }
                    d.tagline?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.titleMedium, color = Harbor.VioletSoft)
                    }
                    d.overview?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Color.White.copy(alpha = .85f)) }
                    if (d.genres.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { d.genres.forEach { Pill(it.name, Harbor.VioletSoft) } }
                    }
                    if (type == "tv" && d.seasons.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Text(stringResource(R.string.seasons), style = MaterialTheme.typography.titleLarge)
                        d.seasons.filter { it.seasonNumber > 0 }.forEach { s ->
                            val st = MediaStatus.of(d.mediaInfo?.seasons?.firstOrNull { it.seasonNumber == s.seasonNumber }?.status)
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.s_1_s_episodes, s.episodeCount) + (s.airDate?.let { " · ${it.take(4)}" } ?: ""), color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall)
                                }
                                if (st != MediaStatus.Unknown) Pill(st.label, if (st == MediaStatus.Available) Harbor.Mint else Harbor.Amber)
                            }
                        }
                    }
                }
            }
            if (d.credits.cast.isNotEmpty()) item {
                Rail(stringResource(R.string.cast), d.credits.cast.take(16), key = { it.id }) { p ->
                    Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(72.dp).clip(CircleShape)) { NetImage(tmdb(p.profilePath, "w185"), Modifier.fillMaxSize(), fallback = p.name.take(1)) }
                        Text(p.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.character.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Harbor.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item { Rail(stringResource(R.string.you_might_also_like), vm.recs, key = { "${it.mediaType}-${it.id}" }) { SeerrPoster(it.copy(mediaType = if (it.mediaType.isBlank()) type else it.mediaType), onOpen = onOpen) } }
        }
        IconButton(onBack, Modifier.statusBarsPadding().padding(12.dp).glass(RoundedCornerShape(50))) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = Color.White)
        }
    }

    if (seasonPicker) vm.details?.let { d ->
        val options = requestableSeasons(d)
        var chosen by remember { mutableStateOf(options.toSet()) }
        ModalBottomSheet({ seasonPicker = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Harbor.Surface) {
            Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(stringResource(R.string.request_seasons), style = MaterialTheme.typography.headlineSmall)
                Text(d.displayTitle, color = Harbor.TextDim)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().clickable { chosen = if (chosen.size == options.size) emptySet() else options.toSet() }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(chosen.size == options.size, { chosen = if (it) options.toSet() else emptySet() })
                    Text(stringResource(R.string.all_available_seasons), fontWeight = FontWeight.Bold)
                }
                d.seasons.filter { it.seasonNumber in options }.forEach { s ->
                    Row(Modifier.fillMaxWidth().clickable { chosen = if (s.seasonNumber in chosen) chosen - s.seasonNumber else chosen + s.seasonNumber }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(s.seasonNumber in chosen, { on -> chosen = if (on) chosen + s.seasonNumber else chosen - s.seasonNumber })
                        Text(s.name, Modifier.weight(1f)); Text(stringResource(R.string.s_1_s_ep, s.episodeCount), color = Harbor.TextDim)
                    }
                }
                Spacer(Modifier.height(12.dp))
                GradientButton("Request ${chosen.size} season${if (chosen.size == 1) "" else "s"}", {
                    vm.request(chosen.sorted(), toast); seasonPicker = false
                }, Modifier.fillMaxWidth(), enabled = chosen.isNotEmpty())
            }
        }
    }
}

private fun requestableSeasons(d: SeerrDetails): List<Int> {
    val taken = d.mediaInfo?.seasons.orEmpty().filter { it.status > 1 }.map { it.seasonNumber }.toSet()
    return d.seasons.map { it.seasonNumber }.filter { it > 0 && it !in taken }
}
