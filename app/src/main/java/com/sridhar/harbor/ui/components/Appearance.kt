package com.sridhar.harbor.ui.components

import androidx.compose.runtime.collectAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.R
import com.sridhar.harbor.ui.theme.Harbor
import com.sridhar.harbor.ui.theme.HomeSection
import com.sridhar.harbor.ui.theme.HomeStyle
import com.sridhar.harbor.ui.theme.Looks
import com.sridhar.harbor.ui.theme.Skin
import com.sridhar.harbor.ui.theme.ThemeMode

/**
 * Settings → Appearance: colour theme, dark / light, Home layout and which Home rows show.
 * Everything applies live. Works with touch and with a TV remote (every option takes focus and shows a ring).
 */
@Composable
fun AppearancePicker(modifier: Modifier = Modifier, tv: Boolean = false) {
    val look = Looks.look
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Palette, null, tint = Harbor.TextDim)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.look_title), style = MaterialTheme.typography.labelLarge, color = Harbor.TextDim)
        }
        // Colour themes
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Skin.entries.forEach { s -> SkinSwatch(s, s == look.skin) { Looks.update { it.copy(skin = s) } } }
        }
        // Mode
        Label(stringResource(R.string.look_mode))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { m ->
                Chip(stringResource(when (m) { ThemeMode.System -> R.string.look_mode_system; ThemeMode.Dark -> R.string.look_mode_dark; ThemeMode.Light -> R.string.look_mode_light }),
                    look.mode == m) { Looks.update { it.copy(mode = m) } }
            }
        }
        // Animations: Auto = reduced on low-end devices
        val c = LocalContainer.current
        val motion by c.motionMode.collectAsState()
        Label(stringResource(R.string.look_motion) + if (motion == "auto") "  ·  " + stringResource(if (c.reducedMotion("auto")) R.string.look_motion_auto_reduced else R.string.look_motion_auto_full) else "")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("auto" to R.string.look_motion_auto, "full" to R.string.look_motion_full, "reduced" to R.string.look_motion_reduced).forEach { (id, label) ->
                Chip(stringResource(label), motion == id) { c.setMotionMode(id) }
            }
        }
        // Home layout
        Label(stringResource(R.string.look_home_layout))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LayoutCard(stringResource(R.string.look_layout_spotlight), stringResource(R.string.look_layout_spotlight_hint), HomeStyle.Spotlight, look.home == HomeStyle.Spotlight, Modifier.weight(1f)) {
                Looks.update { it.copy(homeStyle = HomeStyle.Spotlight.takeIf { h -> h != it.skin.home }) }
            }
            LayoutCard(stringResource(R.string.look_layout_billboard), stringResource(R.string.look_layout_billboard_hint), HomeStyle.Billboard, look.home == HomeStyle.Billboard, Modifier.weight(1f)) {
                Looks.update { it.copy(homeStyle = HomeStyle.Billboard.takeIf { h -> h != it.skin.home }) }
            }
        }
        // Rows on Home
        Label(stringResource(R.string.look_home_rows))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeSection.entries.forEach { sec ->
                if (tv && sec == HomeSection.Shortcuts) return@forEach   // the TV side menu already has these
                Chip(stringResource(when (sec) {
                    HomeSection.Shortcuts -> R.string.look_row_shortcuts; HomeSection.Continue -> R.string.continue_watching; HomeSection.ForYou -> R.string.reco_for_you
                    HomeSection.NextUp -> R.string.next_up; HomeSection.Top10 -> R.string.look_row_top10; HomeSection.LiveTv -> R.string.home_live_rows; HomeSection.Latest -> R.string.look_row_latest
                }), look.shows(sec), check = true) {
                    Looks.update { l -> l.copy(hidden = if (sec in l.hidden) l.hidden - sec else l.hidden + sec) }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelMedium, color = Harbor.TextDim)

@Composable
private fun Chip(text: String, selected: Boolean, check: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(text) },
        leadingIcon = if (check && selected) { { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) } } else null,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        border = if (focused) androidx.compose.foundation.BorderStroke(3.dp, Harbor.Fg) else null,
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Harbor.Violet, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White),
    )
}

@Composable
private fun SkinSwatch(skin: Skin, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (focused || selected) 1.06f else 1f, spring(dampingRatio = 0.6f), label = "swatch")
    val ring by animateColorAsState(if (selected) skin.primary else if (focused) Harbor.Fg else Color.Transparent, label = "ring")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp)) {
        Box(
            Modifier.scale(grow).size(72.dp).clip(RoundedCornerShape(20.dp)).border(3.dp, ring, RoundedCornerShape(20.dp))
                .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).padding(5.dp)
                .clip(RoundedCornerShape(15.dp)).background(skin.darkN.bg),
        ) {
            // A tiny poster row and a CTA in the theme's colour: what the app will look like.
            Row(Modifier.padding(7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { i -> Box(Modifier.width(13.dp).height(20.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.18f + 0.1f * i))) }
            }
            Box(Modifier.align(Alignment.BottomStart).padding(7.dp).width(34.dp).height(10.dp).clip(RoundedCornerShape(5.dp))
                .background(Brush.horizontalGradient(listOf(skin.primary, skin.deep))))
            if (selected) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(skin.primary), Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(skin.title, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) Harbor.Fg else Harbor.TextDim, maxLines = 1)
    }
}

@Composable
private fun LayoutCard(title: String, hint: String, style: HomeStyle, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val ring by animateColorAsState(if (selected) Harbor.Violet else if (focused) Harbor.Fg else Harbor.line(0.08f), label = "layoutRing")
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).border(if (selected || focused) 2.dp else 1.dp, ring, RoundedCornerShape(18.dp))
            .background(Harbor.Surface).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).padding(12.dp),
    ) {
        // Mini wireframe of the layout.
        Box(Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(10.dp)).background(Harbor.SurfaceHigh)) {
            when (style) {
                HomeStyle.Spotlight -> Row(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(0.5f).fillMaxHeight(0.75f).clip(RoundedCornerShape(5.dp)).background(Harbor.line(0.14f)))
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(Brush.verticalGradient(listOf(Harbor.Violet.copy(alpha = .8f), Harbor.Coral))))
                    Box(Modifier.weight(0.5f).fillMaxHeight(0.75f).clip(RoundedCornerShape(5.dp)).background(Harbor.line(0.14f)))
                }
                HomeStyle.Billboard -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f).background(Brush.verticalGradient(listOf(Harbor.Violet.copy(alpha = .7f), Harbor.SurfaceHigh)))) {
                        Box(Modifier.align(Alignment.BottomStart).padding(6.dp).width(26.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color.White))
                    }
                    Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(4) { i -> Row(verticalAlignment = Alignment.Bottom) {
                            Text("${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Harbor.TextDim)
                            Box(Modifier.width(10.dp).height(15.dp).clip(RoundedCornerShape(2.dp)).background(Harbor.line(0.18f)))
                        } }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.Bold, color = Harbor.Fg)
        Text(hint, fontSize = 12.sp, color = Harbor.TextDim, lineHeight = 15.sp)
    }
}
