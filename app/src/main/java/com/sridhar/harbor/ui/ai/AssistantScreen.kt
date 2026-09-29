package com.sridhar.harbor.ui.ai

import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.ai.AiCard
import com.sridhar.harbor.data.ai.AiNav
import com.sridhar.harbor.data.ai.ChatItem
import com.sridhar.harbor.data.ai.ModelState
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.enterRise
import com.sridhar.harbor.ui.components.pressable
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.launch

class AssistantViewModel(private val c: AppContainer) : ViewModel() {
    /** Homelab (tools) and general Chat keep separate conversations. */
    var chatMode by mutableStateOf(false)
    private val homeItems = mutableStateListOf<ChatItem>()
    private val chatItems = mutableStateListOf<ChatItem>()
    val items get() = if (chatMode) chatItems else homeItems
    var busy by mutableStateOf(false); private set
    var nav by mutableStateOf<AiNav?>(null)

    fun send(text: String) {
        if (text.isBlank() || busy) return
        val items = items   // the conversation this message belongs to, even if the mode flips mid-reply
        val chat = chatMode
        val history = items.toList()
        items += ChatItem.User(text.trim())
        busy = true
        viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
            runCatching {
                val onItem: (ChatItem, Boolean) -> Unit = { item, replace ->
                    if (replace && items.isNotEmpty() && (items.last() is ChatItem.Bot || items.last() is ChatItem.ToolUse && item is ChatItem.ToolUse)) items[items.lastIndex] = item
                    else if (replace && items.isNotEmpty() && items.last() is ChatItem.Bot && (items.last() as ChatItem.Bot).text.isEmpty()) items[items.lastIndex] = item
                    else items += item
                }
                if (chat) c.assistant.chat(text.trim(), history, onItem) else c.assistant.ask(text.trim(), history, onItem)
            }.onFailure { items += ChatItem.Bot("⚠ ${it.message}") }
            // Drop empty streaming placeholders.
            items.removeAll { it is ChatItem.Bot && it.text.isBlank() }
            when (val target = c.assistant.pendingNav) {
                is AiNav.Cast -> {
                    c.assistant.pendingNav = null
                    runCatching {
                        val device = c.cast.connect(target.device)
                        val cfg = c.config.value!!
                        val item = c.jellyfin.item(target.id)
                        c.cast.load(item.id, c.jellyfin.castUrl(item.id), item.seriesName ?: item.name, item.episodeLabel ?: item.year?.toString(),
                            c.jellyfin.posterUrl(cfg, item), (item.userData?.positionTicks ?: 0) / 10_000)
                        device
                    }.onSuccess { items += ChatItem.Bot("📺 Now playing on $it. Use the cast bar to control playback.") }
                     .onFailure { items += ChatItem.Bot("⚠ Cast failed: ${it.message}") }
                }
                null -> {}
                else -> { nav = target; c.assistant.pendingNav = null }
            }
            busy = false
        }
    }

    fun confirm(item: ChatItem.Confirm, ok: Boolean) {
        val idx = items.indexOf(item).takeIf { it >= 0 } ?: return
        if (!ok) { items[idx] = item.copy(state = "cancelled"); return }
        items[idx] = item.copy(state = "running")
        viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
            val r = runCatching { item.action() }.getOrElse { "Failed: ${it.message}" }
            items[idx] = item.copy(state = "done"); items += ChatItem.Bot(r)
        }
    }
}

private val chatSuggestions = listOf(
    "Solve 3x + 7 = 22", "Explain recursion simply", "Write a Kotlin function to reverse a string", "What is 15% of 240?",
    "Summarise the theory of relativity", "Fix this SQL: SELEC * FROM users", "Give me a 3-day workout plan",
)

private val suggestions = listOf(
    "What's downloading?", "Server health", "Fix wrong posters", "Suggest a thriller", "Continue watching",
    "Limit downloads to 5 MB/s", "What's missing?", "Coming this week", "Turtle mode on", "Pause all",
)

@Composable
fun AssistantScreen(onBack: () -> Unit, onNav: (AiNav) -> Unit) {
    val container = LocalContainer.current
    val vm = viewModel { AssistantViewModel(container) }
    val state by container.llm.state.collectAsState()
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(vm.items.size, (vm.items.lastOrNull() as? ChatItem.Bot)?.text?.length) { if (vm.items.isNotEmpty()) list.animateScrollToItem(vm.items.size) }
    LaunchedEffect(vm.nav) { vm.nav?.let { onNav(it); vm.nav = null } }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.send(it) }
    }

    Column(Modifier.fillMaxSize().background(Harbor.Ink).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            AiOrb(34.dp, busy = vm.busy)
            Spacer(Modifier.width(10.dp))
            // Tap the model line to switch models (the catalogue).
            var catalog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            if (catalog) ModelCatalog(onDismiss = { catalog = false })
            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { catalog = true }.padding(vertical = 2.dp)) {
                Text(stringResource(R.string.jellyverse_ai), fontWeight = FontWeight.Bold)
                Text(when (state) { ModelState.Loaded, ModelState.Ready -> L10n.s(R.string.qwen_0_5b_on_device_1, container.llm.model.value.displayName, container.assistant.tools.size.toString()) + "  ▾"; ModelState.Loading -> L10n.s(R.string.loading_model)
                    else -> stringResource(R.string.command_mode_download_model_for_full) }, fontSize = 11.sp, color = Harbor.TextDim)
            }
        }
        // Homelab = actions on your server and library (tools); Chat = anything else (code, maths, writing…).
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp)).background(Harbor.Surface).padding(4.dp)) {
            listOf(false to stringResource(R.string.ai_mode_homelab), true to stringResource(R.string.ai_mode_chat)).forEach { (chat, label) ->
                val on = vm.chatMode == chat
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(if (on) Harbor.Violet else Color.Transparent)
                    .clickable(enabled = !vm.busy) { vm.chatMode = chat }, contentAlignment = Alignment.Center) {
                    Text(label, color = if (on) Color.White else Harbor.TextDim, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state !is ModelState.Loaded && state !is ModelState.Ready) item { ModelCard() }
            if (vm.items.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AiOrb(96.dp)
                    Spacer(Modifier.padding(8.dp))
                    Text(stringResource(if (vm.chatMode) R.string.ai_chat_title else R.string.ask_about_your_homelab), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(if (vm.chatMode) R.string.ai_chat_hint else R.string.search_play_request_manage_downloads_check),
                        color = Harbor.TextDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                }
            }
            itemsIndexed(vm.items) { i, item -> ChatRow(item, Modifier.enterRise(0), onNav) { ok -> vm.confirm(item as ChatItem.Confirm, ok) } }
            if (vm.busy && (vm.items.lastOrNull() as? ChatItem.Bot)?.streaming != true) item { TypingDots() }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(if (vm.chatMode) chatSuggestions else suggestions) { s ->
                Text(s, fontSize = 12.sp, color = Harbor.VioletSoft, modifier = Modifier.clip(RoundedCornerShape(50)).background(Harbor.Violet.copy(.14f))
                    .border(1.dp, Harbor.Violet.copy(.3f), RoundedCornerShape(50)).clickable(enabled = !vm.busy) { vm.send(s) }.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.ask_jellyverse)) }, maxLines = 4, shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Harbor.Surface, focusedContainerColor = Harbor.Surface, unfocusedBorderColor = Color.Transparent))
            Spacer(Modifier.width(8.dp))
            if (input.isBlank()) Box(Modifier.size(50.dp).clip(CircleShape).background(Harbor.Surface).pressable {
                runCatching { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)) }
            }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Mic, stringResource(R.string.voice), tint = Harbor.VioletSoft) }
            else Box(Modifier.size(50.dp).clip(CircleShape).background(Harbor.accent).pressable(enabled = !vm.busy) { vm.send(input); input = "" },
                contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.send), tint = Color.White) }
        }
    }
}

@Composable
private fun ChatRow(item: ChatItem, modifier: Modifier, onNav: (AiNav) -> Unit, onConfirm: (Boolean) -> Unit) {
    when (item) {
        is ChatItem.User -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(item.text, color = Color.White, modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                .background(Harbor.accentH).padding(horizontal = 14.dp, vertical = 10.dp))
        }
        is ChatItem.Bot -> Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            AiOrb(26.dp, busy = item.streaming); Spacer(Modifier.width(8.dp))
            Column(Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp)).background(Harbor.Surface)
                .padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RichReply(item.text + if (item.streaming) " ▍" else "")
            }
        }
        is ChatItem.ToolUse -> {
            var open by remember { mutableStateOf(false) }
            Column(modifier.padding(start = 34.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(.3f)).border(1.dp, Harbor.Mint.copy(.25f), RoundedCornerShape(14.dp))
                .clickable { open = !open }.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Build, null, tint = Harbor.Mint, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
                    Text(item.tool + (if (item.args.isNotBlank()) "(${item.args})" else "()"), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Harbor.Mint,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    if (item.viaRouter) Text(stringResource(R.string.router), fontSize = 10.sp, color = Harbor.TextDim)
                }
                if (open) Text(item.result, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Harbor.TextDim, modifier = Modifier.padding(top = 6.dp))
            }
        }
        is ChatItem.Cards -> LazyRow(modifier.padding(start = 34.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(item.cards) { card -> CardTile(card) { onNav(card.nav) } }
        }
        is ChatItem.Confirm -> Row(modifier.padding(start = 34.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Harbor.Amber.copy(.1f))
            .border(1.dp, Harbor.Amber.copy(.35f), RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.prompt, modifier = Modifier.weight(1f), fontSize = 13.sp)
            when (item.state) {
                "pending" -> { TextButton({ onConfirm(false) }) { Text(stringResource(R.string.cancel), color = Harbor.TextDim) }; TextButton({ onConfirm(true) }) { Text(stringResource(R.string.confirm), color = Harbor.Amber, fontWeight = FontWeight.Bold) } }
                "running" -> Text("…", color = Harbor.Amber)
                "done" -> Text("✓", color = Harbor.Mint, fontWeight = FontWeight.Bold)
                else -> Text(stringResource(R.string.cancelled), color = Harbor.TextDim, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CardTile(card: AiCard, onClick: () -> Unit) {
    Column(Modifier.width(104.dp).pressable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp))) { NetImage(card.image, Modifier.fillMaxSize(), fallback = card.title) }
        Text(card.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        card.subtitle?.let { Text(it, fontSize = 10.sp, color = Harbor.TextDim, maxLines = 1) }
    }
}

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(Modifier.padding(start = 34.dp).clip(RoundedCornerShape(16.dp)).background(Harbor.Surface).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.size(7.dp).graphicsLayer { alpha = a; translationY = (1f - a) * 4f }.clip(CircleShape).background(Harbor.VioletSoft))
        }
    }
}


/** Minimal Markdown for replies: ```code``` blocks (monospace, scrollable, Copy) and **bold**. */
@Composable
private fun RichReply(text: String) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    text.split("```").forEachIndexed { i, part ->
        if (i % 2 == 1) {
            val lang = part.substringBefore('\n').trim().takeIf { it.length in 1..15 && !it.contains(' ') }
            val code = (if (lang != null) part.substringAfter('\n') else part).trimEnd()
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Harbor.Ink)) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(lang ?: "code", color = Harbor.TextDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton({ clipboard.setText(androidx.compose.ui.text.AnnotatedString(code)) }) {
                        Text(stringResource(R.string.ai_copy), fontSize = 12.sp)
                    }
                }
                Text(code, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp, color = Harbor.Fg,
                    modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    softWrap = false)
            }
        } else if (part.isNotBlank()) {
            Text(androidx.compose.ui.text.buildAnnotatedString {
                part.trim().split("**").forEachIndexed { j, seg ->
                    if (j % 2 == 1) withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) { append(seg) } else append(seg)
                }
            }, color = Harbor.Fg.copy(.92f))
        }
    }
}
