package com.sridhar.harbor.update

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
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
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
    // Check on launch and whenever the app comes back to the foreground (at most every 20 h); and continue an
    // install the user just allowed in "Install unknown apps".
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            updater.resumeAfterPermission(ctx)
            runCatching { updater.checkIfDue() }
        }
    }

    val info = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.ReadyToInstall -> s.info
        is UpdateState.Installing -> s.info
        is UpdateState.Failed -> s.info ?: return
        else -> return
    }
    val busy = state is UpdateState.Downloading || state is UpdateState.Installing
    Dialog(onDismissRequest = { if (!busy) updater.dismiss() }) {
        Column(Modifier.widthIn(max = 480.dp).clip(RoundedCornerShape(28.dp)).background(Harbor.Surface).padding(24.dp)
            .animateContentSize(androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.sridhar.harbor.ui.ai.JellyBuddy(52.dp, busy = busy)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.update_title), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("${updater.currentVersion}  →  ${info.version}", color = Harbor.Sky, fontWeight = FontWeight.SemiBold)
                }
            }
            if (info.notes.isNotBlank()) {
                Text(stringResource(R.string.update_whats_new), color = Harbor.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                Box(Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(14.dp)).background(Harbor.line(.04f)).verticalScroll(rememberScrollState()).padding(12.dp)) {
                    Text(releaseNotes(info.notes), fontSize = 14.sp, color = Harbor.Fg.copy(alpha = .85f), lineHeight = 20.sp)
                }
            }
            Spacer(Modifier.size(18.dp))
            val first = remember { FocusRequester() }
            LaunchedEffect(state::class) { runCatching { first.requestFocus() } }
            androidx.compose.animation.AnimatedContent(state::class, label = "upd",
                transitionSpec = { (androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 3 }) togetherWith androidx.compose.animation.fadeOut() }) { _ ->
                when (val s = state) {
                    is UpdateState.Downloading -> Column {
                        Text(stringResource(R.string.update_downloading, (s.progress * 100).toInt()), color = Harbor.TextDim, fontSize = 13.sp)
                        Spacer(Modifier.size(8.dp))
                        val p by androidx.compose.animation.core.animateFloatAsState(s.progress, label = "p")
                        TideBar(Modifier.fillMaxWidth(), progress = { p })
                    }
                    is UpdateState.Installing -> Column {
                        Text(stringResource(R.string.update_installing), color = Harbor.TextDim, fontSize = 13.sp)
                        Spacer(Modifier.size(8.dp))
                        TideBar(Modifier.fillMaxWidth())
                    }
                    is UpdateState.ReadyToInstall -> Column {
                        if (android.os.Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls())
                            Text(stringResource(R.string.update_allow_hint), color = Harbor.TextDim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton({ updater.install(ctx) }, Modifier.focusRequester(first).focusRing()) { Text(stringResource(R.string.update_install), fontWeight = FontWeight.Bold) }
                        }
                    }
                    is UpdateState.Failed -> Column {
                        Text(stringResource(R.string.update_failed_title), color = Harbor.Rose, fontWeight = FontWeight.Bold)
                        Text(s.message, color = Harbor.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton({ updater.dismiss() }, Modifier.focusRing()) { Text(stringResource(R.string.update_later), color = Harbor.TextDim) }
                            TextButton({
                                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(updater.releasePage(info.version))).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }, Modifier.focusRing()) { Text(stringResource(R.string.update_github)) }
                            TextButton({ updater.startDownload() }, Modifier.focusRequester(first).focusRing()) { Text(stringResource(R.string.update_retry), fontWeight = FontWeight.Bold) }
                        }
                    }
                    else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton({ updater.dismiss() }, Modifier.focusRing()) { Text(stringResource(R.string.update_later), color = Harbor.TextDim) }
                        TextButton({ updater.startDownload() }, Modifier.focusRequester(first).focusRing()) {
                            Text(stringResource(R.string.update_now), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** GitHub release notes (Markdown) as readable text: headings bold, bullets, tables as "a · b · c", no markup. */
internal fun releaseNotes(md: String): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    val lines = md.lines().map { it.trimEnd() }
    var first = true
    for (raw in lines) {
        val l = raw.trim()
        if (l.matches(Regex("""\|?\s*:?-{2,}.*"""))) continue                       // table separator
        if (l.startsWith("## ") && !l.startsWith("### ")) continue                    // the "JellyVerse x.y" title repeats the header
        if (l.isEmpty()) { if (!first) append("\n"); continue }
        if (!first) append("\n")
        first = false
        val text = l.replace("**", "").replace("`", "")
        when {
            text.startsWith("#") -> withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) { append(text.trimStart('#').trim()) }
            text.startsWith("- ") || text.startsWith("* ") -> append("•  " + text.drop(2))
            text.startsWith("|") -> append(text.trim('|').split('|').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("  ·  "))
            else -> append(text)
        }
    }
}
