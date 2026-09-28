package com.sridhar.harbor.ui.users

import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.AppContainer
import com.sridhar.harbor.data.seerr.JellyfinImportUser
import com.sridhar.harbor.data.seerr.Permission
import com.sridhar.harbor.data.seerr.SeerrRepository
import com.sridhar.harbor.data.seerr.SeerrUser
import com.sridhar.harbor.ui.components.GradientButton
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.NetImage
import com.sridhar.harbor.ui.components.Pill
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.coroutines.launch

class UsersViewModel(private val c: AppContainer) : ViewModel() {
    var users by mutableStateOf<List<SeerrUser>>(emptyList()); private set
    var me by mutableStateOf<SeerrUser?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(true); private set
    var importable by mutableStateOf<List<JellyfinImportUser>?>(null); private set
    var message by mutableStateOf<String?>(null)

    init { load() }

    fun load() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        loading = true; error = null
        runCatching { me = c.seerr.me(); users = c.seerr.users() }.onFailure { error = it.friendly() }
        loading = false
    }

    fun loadImportable() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        importable = null
        importable = runCatching {
            val existing = users.mapNotNull { it.jellyfinUsername?.lowercase() }.toSet()
            c.seerr.importableJellyfinUsers().filter { it.username.lowercase() !in existing }
        }.getOrElse { message = it.friendly(); emptyList() }
    }

    private fun act(msg: String, block: suspend () -> Unit) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        runCatching { block() }.onSuccess { message = msg; load() }.onFailure { message = it.friendly() }
    }

    fun save(u: SeerrUser, perms: Long) = act(L10n.s(R.string.permissions_saved_for_1_s, u.name)) { c.seerr.setPermissions(u.id, perms) }
    fun delete(u: SeerrUser) = act(L10n.s(R.string.s_1_s_removed, u.name)) { c.seerr.deleteUser(u.id) }
    fun import(ids: List<String>) = act("Imported ${ids.size} user${if (ids.size == 1) "" else "s"}") { c.seerr.importJellyfinUsers(ids) }
}

@Composable
fun UsersScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    val vm = viewModel { UsersViewModel(container) }
    var editing by remember { mutableStateOf<SeerrUser?>(null) }
    var importing by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
    val canManage = vm.me?.has(Permission.MANAGE_USERS) == true

    Scaffold(
        containerColor = Harbor.Ink,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.users)) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
                actions = { if (canManage) IconButton({ importing = true; vm.loadImportable() }) { Icon(Icons.Rounded.PersonAdd, stringResource(R.string.import_from_jellyfin)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Harbor.Ink),
            )
        },
    ) { pad ->
        when {
            vm.loading && vm.users.isEmpty() -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() }
            vm.error != null -> MessageState(stringResource(R.string.couldn_t_load_users), vm.error, Modifier.padding(pad), onRetry = { vm.load() })
            !canManage -> MessageState(stringResource(R.string.no_permission), stringResource(R.string.your_jellyseerr_account_can_t_manage), Modifier.padding(pad), icon = Icons.Rounded.Group)
            else -> LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.users, key = { it.id }) { u ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Harbor.Surface).clickable { editing = u }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(CircleShape)) { NetImage(SeerrRepository.avatar(cfg, u.avatar), Modifier.fillMaxSize(), fallback = u.name.take(1)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(u.name, fontWeight = FontWeight.Bold)
                                if (u.id == vm.me?.id) Text(stringResource(R.string.you), color = Harbor.TextDim)
                            }
                            u.email?.let { Text(it, color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                when {
                                    u.isAdmin -> Pill(stringResource(R.string.admin), Harbor.Coral)
                                    u.permissions and Permission.MANAGE_REQUESTS != 0L -> Pill(stringResource(R.string.manager), Harbor.Violet)
                                    else -> Pill(stringResource(R.string.user), Harbor.Sky)
                                }
                                Pill(when (u.userType) { 1 -> L10n.s(R.string.plex); 2 -> L10n.s(R.string.local); 3 -> "Jellyfin"; 4 -> L10n.s(R.string.emby); else -> L10n.s(R.string.account) }, Harbor.TextDim)
                                Pill(stringResource(R.string.s_1_s_requests, u.requestCount), Harbor.TextDim)
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { u -> EditUserSheet(u, isSelf = u.id == vm.me?.id, onDismiss = { editing = null }, onSave = { vm.save(u, it); editing = null }, onDelete = { vm.delete(u); editing = null }) }
    if (importing) ImportSheet(vm.importable, onDismiss = { importing = false }, onImport = { vm.import(it); importing = false })
}

@Composable
private fun EditUserSheet(u: SeerrUser, isSelf: Boolean, onDismiss: () -> Unit, onSave: (Long) -> Unit, onDelete: () -> Unit) {
    var perms by remember(u.id) { mutableLongStateOf(u.permissions) }
    var confirm by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Harbor.Surface) {
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
            item {
                Text(u.name, style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.permissions), color = Harbor.TextDim)
                Spacer(Modifier.height(8.dp))
            }
            items(Permission.editable) { (bit, label) ->
                val on = perms and bit != 0L
                val lockedByAdmin = bit != Permission.ADMIN && perms and Permission.ADMIN != 0L
                Row(Modifier.fillMaxWidth().clickable(enabled = !lockedByAdmin && !(isSelf && bit == Permission.ADMIN)) { perms = perms xor bit }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), color = if (lockedByAdmin) Harbor.TextDim else com.sridhar.harbor.ui.theme.Harbor.Fg)
                    Switch(on || lockedByAdmin, { perms = perms xor bit }, enabled = !lockedByAdmin && !(isSelf && bit == Permission.ADMIN))
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                GradientButton(stringResource(R.string.save_permissions), { onSave(perms) }, Modifier.fillMaxWidth(), enabled = perms != u.permissions)
                if (!isSelf && u.id != 1) TextButton({ confirm = true }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.delete_user), color = Harbor.Rose) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.delete_1_s, u.name)) },
        text = { Text(stringResource(R.string.their_jellyseerr_account_and_requests_will)) },
        confirmButton = { TextButton({ confirm = false; onDelete() }) { Text(stringResource(R.string.delete), color = Harbor.Rose) } },
        dismissButton = { TextButton({ confirm = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ImportSheet(users: List<JellyfinImportUser>?, onDismiss: () -> Unit, onImport: (List<String>) -> Unit) {
    var chosen by remember { mutableStateOf(emptySet<String>()) }
    ModalBottomSheet(onDismiss, containerColor = Harbor.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.import_from_jellyfin), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.give_jellyfin_users_access_to_jellyseerr), color = Harbor.TextDim)
            Spacer(Modifier.height(12.dp))
            when {
                users == null -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() }
                users.isEmpty() -> Text(stringResource(R.string.everyone_is_already_imported), modifier = Modifier.padding(vertical = 16.dp))
                else -> {
                    users.forEach { u ->
                        Row(Modifier.fillMaxWidth().clickable { chosen = if (u.id in chosen) chosen - u.id else chosen + u.id }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(u.id in chosen, { on -> chosen = if (on) chosen + u.id else chosen - u.id })
                            Text(u.username)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    GradientButton(stringResource(R.string.import_1_s, chosen.size), { onImport(chosen.toList()) }, Modifier.fillMaxWidth(), enabled = chosen.isNotEmpty())
                }
            }
        }
    }
}
