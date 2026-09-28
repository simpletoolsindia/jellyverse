package com.sridhar.harbor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sridhar.harbor.ui.theme.Harbor

/** Material window width buckets: phones, big phones / small tablets & landscape, tablets & desktops. */
enum class WidthClass { Compact, Medium, Expanded }

fun widthClassOf(widthDp: Int): WidthClass = when {
    widthDp < 600 -> WidthClass.Compact
    widthDp < 840 -> WidthClass.Medium
    else -> WidthClass.Expanded
}

@Composable
fun widthClass(): WidthClass = widthClassOf(LocalConfiguration.current.screenWidthDp)

/** Max width for reading screens (lists, forms, settings) so lines don't stretch across a tablet. */
val ReadableWidth: Dp = 760.dp

/**
 * Centres a list/form screen in a column no wider than [max]. On phones it's a no-op;
 * on tablets and landscape the side gutters are painted in the app background.
 */
@Composable
fun Readable(max: Dp = ReadableWidth, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Harbor.Ink), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = max).fillMaxWidth().fillMaxHeight()) { content() }
    }
}

/**
 * Extra bottom space taken by floating chrome that screens don't own (the music mini player).
 * Provided by the nav host; FABs, snackbars, action bars and list padding add it so nothing hides underneath.
 */
val LocalMiniPlayerInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

/** Height of the floating bottom tab bar that screens float buttons above; 0 when landscape phones use the side rail. */
val LocalBottomBarInset = androidx.compose.runtime.compositionLocalOf { 80.dp }
