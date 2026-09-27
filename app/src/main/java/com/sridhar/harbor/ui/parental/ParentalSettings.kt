package com.sridhar.harbor.ui.parental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.R
import com.sridhar.harbor.ui.components.CreatePinDialog
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.PinDialog
import com.sridhar.harbor.ui.theme.Harbor

/** One-line status for the settings row. */
@Composable
fun parentalSummary(): String {
    val s by LocalContainer.current.parental.state.collectAsState()
    return when {
        !s.enabled -> stringResource(R.string.parental_off)
        s.locked.isNotEmpty() -> stringResource(R.string.parental_on_locks, s.locked.size)
        else -> stringResource(R.string.parental_on)
    }
}

/** Set up / manage parental control. Managing an existing setup needs the PIN first. */
@Composable
fun ParentalSettingsDialog(onDismiss: () -> Unit) {
    val pc = LocalContainer.current.parental
    val s by pc.state.collectAsState()
    var verified by remember { mutableStateOf(pc.isUnlocked()) }
    var creating by remember { mutableStateOf(!s.enabled) }
    val wrong = stringResource(R.string.pin_wrong)

    when {
        creating -> CreatePinDialog(onDismiss = { if (s.enabled) creating = false else onDismiss() }) { pin -> pc.setPin(pin); creating = false; verified = true }
        !verified -> PinDialog(stringResource(R.string.pin_enter), stringResource(R.string.parental_title), onDismiss) { if (pc.verify(it)) { verified = true; null } else wrong }
        else -> AlertDialog(onDismissRequest = onDismiss, containerColor = Harbor.Surface,
            icon = { Icon(Icons.Rounded.FamilyRestroom, null, tint = Harbor.Sky) },
            title = { Text(stringResource(R.string.parental_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.parental_protect_adult), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.parental_protect_adult_hint), color = Harbor.TextDim, fontSize = 12.sp)
                        }
                        Switch(s.protectAdult, pc::setProtectAdult, colors = SwitchDefaults.colors(checkedTrackColor = Harbor.Violet))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.parental_show_menu), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.parental_show_menu_hint), color = Harbor.TextDim, fontSize = 12.sp)
                        }
                        Switch(s.showMenu, pc::setShowMenu, colors = SwitchDefaults.colors(checkedTrackColor = Harbor.Violet))
                    }
                    if (s.locked.isNotEmpty()) Text(stringResource(R.string.parental_on_locks, s.locked.size), color = Harbor.TextDim, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton({ creating = true }) { Text(stringResource(R.string.parental_change_pin)) }
                        TextButton({ pc.relock(); onDismiss() }) { Text(stringResource(R.string.parental_lock_now), color = Harbor.Amber) }
                    }
                    TextButton({ pc.clearPin(); onDismiss() }, Modifier.padding(top = 0.dp)) { Text(stringResource(R.string.parental_remove), color = Harbor.Rose) }
                }
            },
            confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.done)) } })
    }
}
