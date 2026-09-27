package com.sridhar.harbor.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.R
import com.sridhar.harbor.net.NetworkMonitor
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.delay

/**
 * App-wide connection pill: slides in with the drifting jellyfish when the network drops, flips to a
 * mint "Back online" when it returns, then tucks away. Never blocks touches underneath.
 */
@Composable
fun OfflineBanner() {
    val online by NetworkMonitor.online.collectAsState()
    var wasOffline by remember { mutableStateOf(false) }
    var showBack by remember { mutableStateOf(false) }
    LaunchedEffect(online) {
        if (!online) { wasOffline = true; showBack = false }
        else if (wasOffline) { showBack = true; delay(2200); showBack = false; wasOffline = false }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(!online || showBack,
            enter = slideInVertically(spring(dampingRatio = 0.7f)) { -it * 2 } + fadeIn(),
            exit = slideOutVertically { -it * 2 } + fadeOut()) {
            AnimatedContent(online, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "net") { up ->
                Row(Modifier.shadow(18.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50))
                    .background(if (up) Color(0xFF12301F) else Color(0xFF1A1D26))
                    .border(1.dp, (if (up) Harbor.Mint else Harbor.Sky).copy(alpha = .35f), RoundedCornerShape(50))
                    .padding(start = 6.dp, end = 18.dp, top = 4.dp, bottom = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically) {
                    if (up) { Spacer(Modifier.width(12.dp)); Text("✓", color = Harbor.Mint, fontWeight = FontWeight.Black) }
                    else AdriftJelly(Modifier.size(40.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(if (up) R.string.net_back else R.string.net_offline), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (!up) Text(stringResource(R.string.net_waiting), color = Harbor.TextDim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
