package com.sridhar.harbor.ui.admin

import com.sridhar.harbor.L10n
import com.sridhar.harbor.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sridhar.harbor.data.jellyfin.admin.JellyfinAdminRepository
import com.sridhar.harbor.data.jellyfin.admin.LibraryFolder
import com.sridhar.harbor.data.jellyfin.admin.PolicyEditor
import com.sridhar.harbor.data.jellyfin.admin.PolicyFlag
import com.sridhar.harbor.ui.components.GradientButton
import com.sridhar.harbor.ui.components.LocalContainer
import com.sridhar.harbor.ui.components.MessageState
import com.sridhar.harbor.ui.components.friendly
import com.sridhar.harbor.ui.components.glass
import com.sridhar.harbor.ui.components.rememberConfig
import com.sridhar.harbor.ui.theme.Harbor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.coroutines.launch

class AdminUserViewModel(private val repo: JellyfinAdminRepository, val userId: String, initialName: String) : ViewModel() {
    var name by mutableStateOf(initialName)
    var policy by mutableStateOf<JsonObject?>(null); private set
    var libraries by mutableStateOf<List<LibraryFolder>>(emptyList()); private set
    var error by mutableStateOf<String?>(null); private set
    var saving by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var deleted by mutableStateOf(false); private set

    /** Pending policy edits, keyed by UserPolicy field. */
    val changes = mutableStateMapOf<String, JsonElement>()
    private var savedName = initialName

    init { load() }

    fun load() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        error = null
        runCatching {
            policy = repo.policy(userId)
            libraries = runCatching { repo.libraries() }.getOrDefault(emptyList())
        }.onFailure { error = it.friendly() }
    }

    private fun current(key: String): JsonElement? = changes[key] ?: policy?.get(key)

    fun flag(key: String) = (current(key) as? JsonPrimitive)?.booleanOrNull ?: false
    fun setFlag(key: String, on: Boolean) { changes[key] = JsonPrimitive(on) }

    val allFolders get() = flag("EnableAllFolders")
    val folders: Set<String> get() = (current("EnabledFolders") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet().orEmpty()
    fun setAllFolders(on: Boolean) = changes.putAll(PolicyEditor.libraryAccess(on, folders))
    fun toggleFolder(id: String) = changes.putAll(PolicyEditor.libraryAccess(false, if (id in folders) folders - id else folders + id))

    val bitrateMbps: String get() = ((current("RemoteClientBitrateLimit") as? JsonPrimitive)?.longOrNull ?: 0L).let { if (it <= 0) "" else (it / 1_000_000.0).toString().removeSuffix(".0") }
    fun setBitrate(text: String) = changes.putAll(PolicyEditor.bitrateLimit(text.toDoubleOrNull()))
    val maxSessions: String get() = (current("MaxActiveSessions")?.jsonPrimitive?.content?.toIntOrNull() ?: 0).let { if (it <= 0) "" else it.toString() }
    fun setMaxSessions(text: String) { changes["MaxActiveSessions"] = JsonPrimitive(text.toIntOrNull()?.coerceAtLeast(0) ?: 0) }

    val dirty get() = changes.isNotEmpty() || name.trim() != savedName

    fun save() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        saving = true
        runCatching {
            if (name.trim() != savedName && name.isNotBlank()) { repo.rename(userId, name); savedName = name.trim() }
            if (changes.isNotEmpty()) { repo.updatePolicy(userId, changes.toMap()); policy = PolicyEditor.merge(policy ?: JsonObject(emptyMap()), changes.toMap()); changes.clear() }
        }.onSuccess { message = L10n.s(R.string.saved) }.onFailure { message = it.friendly() }
        saving = false
    }

    fun setPassword(pw: String) = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        runCatching { repo.setPassword(userId, pw) }.onSuccess { message = L10n.s(R.string.password_updated) }.onFailure { message = it.friendly() }
    }

    fun resetPassword() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        runCatching { repo.resetPassword(userId) }.onSuccess { message = L10n.s(R.string.password_removed_they_can_sign_in) }.onFailure { message = it.friendly() }
    }

    fun delete() = viewModelScope.launch(com.sridhar.harbor.CrashGuard) {
        runCatching { repo.deleteUser(userId) }.onSuccess { deleted = true }.onFailure { message = it.friendly() }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun AdminUserScreen(userId: String, name: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val cfg = rememberConfig()
    val vm = viewModel(key = "admin-user-$userId") { AdminUserViewModel(container.admin, userId, name) }
    val isSelf = userId == cfg.jellyfinUserId
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
    LaunchedEffect(vm.deleted) { if (vm.deleted) onBack() }
    var confirmDelete by remember { mutableStateOf(false) }
    var newPw by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Harbor.Ink,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(vm.name.ifBlank { name }) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
                actions = { TextButton(vm::save, enabled = vm.dirty && !vm.saving, modifier = Modifier.testTag("save_user")) { Text(if (vm.saving) "Saving…" else "Save") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Harbor.Ink),
            )
        },
    ) { pad ->
        val policy = vm.policy
        when {
            policy == null && vm.error != null -> MessageState(stringResource(R.string.couldn_t_load_user), vm.error, Modifier.padding(pad), onRetry = { vm.load() })
            policy == null -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { com.sridhar.harbor.ui.components.JellyLoader() }
            else -> LazyColumn(Modifier.fillMaxSize().padding(pad).semantics { testTagsAsResourceId = true }.testTag("user_editor"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SectionTitle(stringResource(R.string.profile)) }
                item {
                    OutlinedTextField(vm.name, { vm.name = it }, Modifier.fillMaxWidth().testTag("user_name"), label = { Text(stringResource(R.string.name)) }, singleLine = true)
                }
                item { SectionTitle(stringResource(R.string.password)) }
                item {
                    Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(newPw, { newPw = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.new_password)) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GradientButton(stringResource(R.string.set_password), { vm.setPassword(newPw); newPw = "" }, Modifier.weight(1f), enabled = newPw.length >= 4)
                            OutlinedButton(vm::resetPassword, Modifier.weight(1f)) { Text(stringResource(R.string.remove)) }
                        }
                    }
                }
                PolicyEditor.flags.groupBy { it.group }.forEach { (group, flags) ->
                    item(key = "g-$group") { SectionTitle(group) }
                    item(key = "f-$group") {
                        Column(Modifier.fillMaxWidth().glass()) {
                            flags.forEach { f ->
                                // You can't lock yourself out: own admin / disabled switches are read-only.
                                val locked = isSelf && f.key in setOf("IsAdministrator", "IsDisabled")
                                PolicySwitch(f, vm.flag(f.key), enabled = !locked) { vm.setFlag(f.key, it) }
                            }
                        }
                    }
                }
                item { SectionTitle(stringResource(R.string.library_access)) }
                item {
                    Column(Modifier.fillMaxWidth().glass()) {
                        PolicySwitch(PolicyFlag("EnableAllFolders", stringResource(R.string.access_to_all_libraries), ""), vm.allFolders) { vm.setAllFolders(it) }
                        if (!vm.allFolders) vm.libraries.forEach { l ->
                            Row(Modifier.fillMaxWidth().clickable { vm.toggleFolder(l.itemId) }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(l.itemId in vm.folders, { vm.toggleFolder(l.itemId) })
                                Text(l.name)
                            }
                        }
                    }
                }
                item { SectionTitle(stringResource(R.string.limits)) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(vm.bitrateMbps, vm::setBitrate, Modifier.weight(1f), label = { Text(stringResource(R.string.remote_bitrate_mbps)) }, placeholder = { Text(stringResource(R.string.unlimited)) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(vm.maxSessions, vm::setMaxSessions, Modifier.weight(1f), label = { Text(stringResource(R.string.max_sessions)) }, placeholder = { Text(stringResource(R.string.unlimited)) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
                if (!isSelf) item {
                    Spacer(Modifier.height(12.dp))
                    TextButton({ confirmDelete = true }, Modifier.fillMaxWidth().testTag("delete_user")) { Text(stringResource(R.string.delete_user), color = Harbor.Rose) }
                }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_1_s, vm.name)) },
        text = { Text(stringResource(R.string.their_jellyfin_account_watch_history_and)) },
        confirmButton = { TextButton({ confirmDelete = false; vm.delete() }, Modifier.testTag("confirm_delete")) { Text(stringResource(R.string.delete), color = Harbor.Rose) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun PolicySwitch(f: PolicyFlag, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("flag_${f.key}"),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(f.label, fontWeight = FontWeight.SemiBold, color = if (enabled) MaterialTheme.colorScheme.onSurface else Harbor.TextDim)
            f.hint?.let { Text(it, color = Harbor.TextDim, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked, onChange, enabled = enabled)
    }
}
