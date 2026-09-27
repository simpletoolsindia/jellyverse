package com.sridhar.harbor.ui.lock

import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.sridhar.harbor.ui.components.GradientButton
import com.sridhar.harbor.ui.components.HarborLogo
import com.sridhar.harbor.ui.theme.Harbor

class LockPrefs(context: Context) {
    private val p = context.getSharedPreferences("harbor_lock", Context.MODE_PRIVATE)
    var enabled: Boolean get() = p.getBoolean("enabled", false); set(v) = p.edit().putBoolean("enabled", v).apply()
}

/** Covers the app until the user authenticates (content isn't even composed while locked). */
@Composable
fun LockGate(locked: Boolean, onUnlock: () -> Unit, content: @Composable () -> Unit) {
    AnimatedContent(locked, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "lock") { isLocked ->
        if (!isLocked) content()
        else Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Harbor.Violet.copy(.10f), Harbor.Ink), radius = 1400f)), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(32.dp)) {
                HarborLogo(96.dp)
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.jellyverse_is_locked), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.unlock_to_control_your_homelab), color = Harbor.TextDim)
                Spacer(Modifier.height(28.dp))
                GradientButton(stringResource(R.string.unlock), onUnlock, icon = Icons.Rounded.Fingerprint)
                Spacer(Modifier.height(28.dp))
                com.sridhar.harbor.ui.components.MadeWithLove()
            }
        }
    }
}
