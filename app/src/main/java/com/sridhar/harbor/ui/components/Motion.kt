package com.sridhar.harbor.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * True when decorative motion should be skipped: Ken Burns drift, auto-gliding rows, staggered entrances,
 * shimmer. On by default for low-end TVs/phones (see AppContainer.lowEnd) and when Android's animations are off;
 * the user can force Full or Reduced in Settings → Appearance.
 */
@Composable
fun reducedMotion(): Boolean {
    val c = LocalContainer.current
    val mode by c.motionMode.collectAsState()
    return c.reducedMotion(mode)
}
