package com.sridhar.harbor.ui.ssh

import androidx.compose.runtime.mutableIntStateOf
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sridhar.harbor.data.ssh.SshHost
import com.sridhar.harbor.ui.components.GradientButton
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.Pill
import com.sridhar.harbor.ui.components.enterRise
import com.sridhar.harbor.ui.components.pressable
import com.sridhar.harbor.ui.theme.Harbor

private val palette = listOf(Harbor.Violet, Harbor.Coral, Harbor.Sky, Harbor.Mint, Harbor.Amber, Harbor.Rose)

@Composable
fun HostsScreen(onConnect: (String) -> Unit, onBack: () -> Unit) {
    val container = LocalContainer.current
    val hosts by container.ssh.hosts.collectAsState()
    var editing by remember { mutableStateOf<SshHost?>(null) }
    var creating by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Harbor.Ink)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.statusBarsPadding().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                    Column {
                        Text(stringResource(R.string.terminal), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.ssh_hosts_credentials_are_encrypted_on), color = Harbor.TextDim, fontSize = 12.sp)
                    }
                }
            }
            if (hosts.isEmpty()) item { MessageState(stringResource(R.string.no_hosts_yet), stringResource(R.string.add_your_homelab_to_open_a), icon = Icons.Rounded.Terminal) }
            itemsIndexed(hosts, key = { _, h -> h.id }) { i, h ->
                HostCard(h, primary = i == 0, fingerprint = container.ssh.pinnedFingerprint(h), modifier = Modifier.enterRise(i),
                    onConnect = { onConnect(h.id) }, onEdit = { editing = h },
                    onPrimary = { container.ssh.setPrimary(h) }, onForgetKey = { container.ssh.forgetHostKey(h) },
                    onDelete = { container.ssh.delete(h) })
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(24.dp).size(60.dp).clip(RoundedCornerShape(20.dp))
            .background(Harbor.accent).pressable { creating = true }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, stringResource(R.string.add_host), tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
    if (creating || editing != null) HostEditor(editing, onDismiss = { creating = false; editing = null }) { h ->
        container.ssh.save(h); creating = false; editing = null
    }
}

@Composable
private fun HostCard(
    h: SshHost, primary: Boolean, fingerprint: String?, modifier: Modifier,
    onConnect: () -> Unit, onEdit: () -> Unit, onPrimary: () -> Unit, onForgetKey: () -> Unit, onDelete: () -> Unit,
) {
    val tint = palette[h.color.mod(palette.size)]
    var menu by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Harbor.Surface).pressable(0.97f, onClick = onConnect).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(tint, tint.copy(alpha = .5f)))), contentAlignment = Alignment.Center) {
            Text(h.name.take(2).uppercase(), fontWeight = FontWeight.Black, color = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(h.name, fontWeight = FontWeight.Bold)
                if (primary) { Spacer(Modifier.width(8.dp)); Pill(stringResource(R.string.lab), Harbor.Mint) }
            }
            Text("${h.user}@${h.host}:${h.port}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Harbor.TextDim)
            fingerprint?.let { Text(it.take(24) + "…", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Harbor.TextDim.copy(alpha = .6f)) }
        }
        Box {
            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.connect)) }, { menu = false; onConnect() })
                DropdownMenuItem({ Text(stringResource(R.string.edit)) }, { menu = false; onEdit() })
                if (!primary) DropdownMenuItem({ Text(stringResource(R.string.use_for_lab_dashboard)) }, { menu = false; onPrimary() })
                DropdownMenuItem({ Text(stringResource(R.string.forget_host_key)) }, { menu = false; onForgetKey() })
                DropdownMenuItem({ Text(stringResource(R.string.delete), color = Harbor.Rose) }, { menu = false; onDelete() })
            }
        }
    }
}

@Composable
fun HostEditor(existing: SshHost?, onDismiss: () -> Unit, onSave: (SshHost) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "Homelab") }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var port by remember { mutableStateOf((existing?.port ?: 22).toString()) }
    var user by remember { mutableStateOf(existing?.user.orEmpty()) }
    var pass by remember { mutableStateOf(existing?.password.orEmpty()) }
    var color by remember { mutableIntStateOf(existing?.color ?: 0) }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Harbor.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (existing == null) stringResource(R.string.new_host) else stringResource(R.string.edit_host), style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                palette.forEachIndexed { i, c ->
                    Box(Modifier.size(if (i == color) 30.dp else 24.dp).clip(RoundedCornerShape(50)).background(c).pressable { color = i })
                }
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label)) }, singleLine = true, shape = RoundedCornerShape(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(host, { host = it.trim() }, Modifier.weight(1f), label = { Text(stringResource(R.string.host_ip)) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, Modifier.width(90.dp), label = { Text(stringResource(R.string.port)) }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            OutlinedTextField(user, { user = it.trim() }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.username)) }, singleLine = true, shape = RoundedCornerShape(14.dp))
            OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.password)) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Spacer(Modifier.height(4.dp))
            GradientButton(stringResource(R.string.save_host), {
                onSave((existing ?: SshHost(name = name, host = host, user = user)).copy(
                    name = name.ifBlank { host }, host = host, port = port.toIntOrNull() ?: 22, user = user, password = pass, color = color))
            }, Modifier.fillMaxWidth(), enabled = host.isNotBlank() && user.isNotBlank())
        }
    }
}
