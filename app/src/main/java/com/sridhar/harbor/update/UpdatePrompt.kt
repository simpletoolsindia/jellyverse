package com.sridhar.harbor.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sridhar.harbor.R
import com.sridhar.harbor.ui.components.HarborLogo
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.TideBar
import com.sridhar.harbor.ui.player.focusRing
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.launch

/** App-wide "new version on GitHub" popup: what's new → download with progress → Android installer. */
@Composable
fun UpdatePrompt() {
    val updater = LocalContainer.current.updater
    if (!updater.enabled) return
    val state by updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { updater.checkIfDue() }

    val info = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.ReadyToInstall -> s.info
        else -> return
    }
    Dialog(onDismissRequest = { if (state is UpdateState.Available) updater.dismiss() }) {
        Column(Modifier.widthIn(max = 480.dp).clip(RoundedCornerShape(28.dp)).background(Harbor.Surface).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HarborLogo(48.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.update_title), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("${updater.currentVersion}  →  ${info.version}", color = Harbor.Sky, fontWeight = FontWeight.SemiBold)
                }
            }
            if (info.notes.isNotBlank()) {
                Text(stringResource(R.string.update_whats_new), color = Harbor.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                Box(Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .04f)).verticalScroll(rememberScrollState()).padding(12.dp)) {
                    Text(info.notes.replace("**", ""), fontSize = 14.sp, color = Color.White.copy(alpha = .85f))
                }
            }
            Spacer(Modifier.size(18.dp))
            val first = remember { FocusRequester() }
            LaunchedEffect(state::class) { runCatching { first.requestFocus() } }
            when (val s = state) {
                is UpdateState.Downloading -> {
                    Text(stringResource(R.string.update_downloading, (s.progress * 100).toInt()), color = Harbor.TextDim, fontSize = 13.sp)
                    Spacer(Modifier.size(8.dp))
                    TideBar(Modifier.fillMaxWidth(), progress = { s.progress })
                }
                is UpdateState.ReadyToInstall -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ updater.install(ctx) }, Modifier.focusRequester(first).focusRing()) { Text(stringResource(R.string.update_install), fontWeight = FontWeight.Bold) }
                }
                else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ updater.dismiss() }, Modifier.focusRing()) { Text(stringResource(R.string.update_later), color = Harbor.TextDim) }
                    TextButton({ scope.launch(com.sridhar.harbor.CrashGuard) { updater.download() } }, Modifier.focusRequester(first).focusRing()) {
                        Text(stringResource(R.string.update_now), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
