package com.sridhar.harbor.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * True while this screen is in the foreground. Screens stay composed under the full-screen player, so anything
 * that decodes, streams or loops (trailer previews, rotating spotlights) must check this and stop while hidden.
 */
@Composable
fun rememberResumed(): Boolean {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    return state.isAtLeast(Lifecycle.State.RESUMED)
}
