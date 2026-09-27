package com.sridhar.harbor.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.R
import com.sridhar.harbor.remote.RemoteServer
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.delay

/** Pairing code while a phone pairs, and a short "phone connected" toast afterwards. Never takes focus. */
@Composable
fun TvRemoteOverlay() {
    val code by RemoteServer.pairingCode.collectAsState()
    val paired by RemoteServer.justPaired.collectAsState()
    LaunchedEffect(paired) { if (paired != null) { delay(3000); RemoteServer.justPaired.value = null } }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(code != null, Modifier.align(Alignment.Center), enter = fadeIn() + scaleIn(initialScale = .9f), exit = fadeOut()) {
            Column(Modifier.clip(RoundedCornerShape(28.dp)).background(Harbor.Surface).border(1.dp, Harbor.Violet.copy(alpha = .5f), RoundedCornerShape(28.dp))
                .padding(horizontal = 56.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📱", fontSize = 40.sp)
                Text(stringResource(R.string.remote_pair_title), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text(stringResource(R.string.remote_pair_hint), color = Harbor.TextDim, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    code.orEmpty().forEach { d ->
                        Box(Modifier.size(78.dp, 96.dp).clip(RoundedCornerShape(16.dp)).background(Harbor.Ink).border(2.dp, Harbor.Sky, RoundedCornerShape(16.dp)), Alignment.Center) {
                            Text(d.toString(), color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(paired != null, Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
            enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            Text("✓  " + stringResource(R.string.remote_paired_tv), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF12301F)).border(1.dp, Harbor.Mint.copy(alpha = .5f), RoundedCornerShape(50))
                    .padding(horizontal = 24.dp, vertical = 12.dp))
        }
    }
}
