package com.bond.mail.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.bond.mail.ui.motion.bondMotionEnabled
import com.bond.mail.ui.motion.BondMotionDuration
import com.bond.mail.ui.motion.rememberBondPressResetter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bond.mail.data.ai.*
import com.bond.mail.data.security.CredentialStore
import com.bond.mail.ui.i18n.tr
import kotlinx.coroutines.*
import java.util.UUID

@Composable
internal fun AiSettingsDialog(credentialStore: CredentialStore? = null, onSaved: () -> Unit = {}, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AiSettingsScreen(credentialStore, onSaved, onDismiss)
    }
}

@Composable
internal fun AiSettingsScreen(credentialStore: CredentialStore? = null, onSaved: () -> Unit = {},
    onBack: () -> Unit, onOpenReplySkills: (() -> Unit)? = null) {
    if (onOpenReplySkills != null) {
        AiSettingsContent(credentialStore, onSaved, onBack, onOpenReplySkills)
    } else {
        // The mail assistant hosts settings in a dialog, outside the app NavHost.
        var skillsOpen by remember { mutableStateOf(false) }
        val duration = if (bondMotionEnabled()) BondMotionDuration.SharedAxis else 0
        AnimatedContent(targetState = skillsOpen, transitionSpec = {
            val direction = if (targetState) 1 else -1
            (slideInHorizontally(tween(duration)) { it * direction } + fadeIn(tween(duration))) togetherWith
                (slideOutHorizontally(tween(duration)) { -it * direction / 4 } + fadeOut(tween(duration)))
        }, label = "ai-reply-skills-navigation") { skills ->
            if (skills) ReplySkillsScreen(onChanged = onSaved, onDismiss = { skillsOpen = false })
            else AiSettingsContent(credentialStore, onSaved, onBack, { skillsOpen = true })
        }
    }
}

@Composable
private fun AiSettingsContent(credentialStore: CredentialStore?, onSaved: () -> Unit,
    onBack: () -> Unit, onOpenReplySkills: () -> Unit) {
    val context = LocalContext.current
    val store = remember { credentialStore ?: CredentialStore(context) }
    var loadError by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf(runCatching { store.aiProfiles() }.getOrElse {
        loadError = true; AiProfiles(emptyList(), null)
    }) }
    var editing by remember { mutableStateOf<AiProfile?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AiProfile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun save(next: AiProfiles): Boolean = try {
        store.saveAiProfiles(next); profiles = next; onSaved(); true
    } catch (_: Exception) { error = "translation_save_failed"; false }
    if (editorOpen) {
        AiProfileEditor(editing, onSave = { profile ->
            val next = profiles.entries.filterNot { it.id == profile.id } + profile
            if (save(AiProfiles(next, profile.id))) { editorOpen = false; error = null }
            else throw AiFailure("translation_save_failed")
        }, onDismiss = { editorOpen = false })
    }
    AiSettingsPage(tr("ai_settings"), onBack, dialog = false, footer = {
        TextButton(enabled = !loadError, onClick = { editing = null; editorOpen = true }) { Text(tr("ai_add_profile")) }
    }) {
        val pressResetter = rememberBondPressResetter()
        key(pressResetter.epoch) {
            OutlinedButton(onClick = { pressResetter.resetThen(onOpenReplySkills) }) { Text(tr("ai_skills_title")) }
        }
        Text(tr("ai_profiles_note"), style = MaterialTheme.typography.bodySmall)
        if (loadError) Text(tr("ai_profiles_load_error"), color = MaterialTheme.colorScheme.error)
        if (profiles.entries.isEmpty() && !loadError) Text(tr("ai_not_configured"))
        profiles.entries.forEach { profile ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    Text(profile.config.model, style = MaterialTheme.typography.bodyMedium)
                    if (profile.note.isNotBlank()) Text(profile.note, style = MaterialTheme.typography.bodySmall)
                    Text(if (profile.config.provider == AiProvider.GEMINI) "generativelanguage.googleapis.com" else profile.config.endpoint,
                        style = MaterialTheme.typography.bodySmall)
                    if (profile.id == profiles.activeId) Text(tr("ai_active_profile"), color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { save(AiProfiles(profiles.entries, profile.id)) }, enabled = profile.id != profiles.activeId) { Text(tr("ai_activate")) }
                        TextButton(onClick = { editing = profile; editorOpen = true }) { Text(tr("ai_edit_profile")) }
                        TextButton(onClick = { deleting = profile }) { Text(tr("delete")) }
                    }
                }
            }
        }
        error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
    }
    deleting?.let { target -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(tr("ai_delete_profile")) },
        text = { Text(target.name) }, confirmButton = { TextButton(onClick = {
            val remaining = profiles.entries.filterNot { it.id == target.id }
            // Do not silently switch future email uploads to a different provider after deletion.
            if (save(AiProfiles(remaining, profiles.activeId.takeUnless { it == target.id }))) deleting = null
        }) { Text(tr("delete")) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("cancel")) } }) }
}

@Composable
private fun AiSettingsPage(title: String, onDismiss: () -> Unit, dialog: Boolean = true,
    footer: @Composable RowScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val page: @Composable () -> Unit = {
        Surface(
            modifier = if (dialog) Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(0.92f)
                else Modifier.fillMaxSize(),
            shape = if (dialog) MaterialTheme.shapes.extraLarge else androidx.compose.ui.graphics.RectangleShape,
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(if (dialog) Modifier else Modifier.systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (!dialog) IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("back"))
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(8.dp))
                    if (dialog) TextButton(onClick = onDismiss) { Text(tr("close")) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) { content(); Spacer(Modifier.height(12.dp)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End, content = footer)
            }
        }
    }
    if (dialog) Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center) { page() }
    } else page()
}

@Composable
private fun AiProfileEditor(initial: AiProfile?, onSave: (AiProfile) -> Unit, onDismiss: () -> Unit) {
    val customLabel = tr("ai_custom")
    val siliconFlowLabel = tr("ai_siliconflow")
    var preset by remember { mutableStateOf(aiPresets.firstOrNull { it.id == initial?.presetId } ?: aiPresets.first()) }
    var name by remember { mutableStateOf(initial?.name ?: preset.label) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var protocol by remember { mutableStateOf(initial?.config?.provider ?: preset.protocol) }
    var endpoint by remember { mutableStateOf(initial?.config?.endpoint ?: preset.endpoint) }
    var auth by remember { mutableStateOf(initial?.config?.auth ?: preset.auth) }
    var fullUrl by remember { mutableStateOf(initial?.config?.fullEndpoint ?: false) }
    var model by remember { mutableStateOf(initial?.config?.model ?: preset.models.firstOrNull().orEmpty()) }
    var secret by remember { mutableStateOf(initial?.config?.key.orEmpty()) }
    var models by remember { mutableStateOf((initial?.models.orEmpty() + preset.models).distinct()) }
    val visibleModels = remember(models) { selectableAiModels(models) }
    var picker by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    fun config() = AiConfig(protocol, endpoint.trim(), model.trim(), secret.trim(), auth, fullUrl, name.trim())
    fun runAction(action: suspend () -> String) {
        busy = true; status = null
        job = scope.launch {
            try { status = withTimeout(120_000) { action() } }
            catch (_: TimeoutCancellationException) { status = "ai_network" }
            catch (e: CancellationException) { throw e }
            catch (e: AiFailure) { status = e.reason }
            catch (_: Exception) { status = "ai_network" }
            finally { busy = false }
        }
    }
    AiSettingsPage(tr(if (initial == null) "ai_add_profile" else "ai_edit_profile"), onDismiss, footer = {
        if (busy) TextButton(onClick = { job?.cancel(); busy = false }) { Text(tr("cancel")) }
        else TextButton(onClick = {
            try {
                if (name.isBlank()) throw AiFailure("ai_name_required")
                val selected = config(); aiRequestUrl(selected)
                onSave(AiProfile(initial?.id ?: UUID.randomUUID().toString(), name.trim(), preset.id, note.trim(), selected,
                    (models + selected.model).distinct()))
            } catch (e: AiFailure) { status = e.reason }
            catch (_: Exception) { status = "translation_save_failed" }
        }) { Text(tr("ai_save_activate")) }
    }) {
        AiChoice(tr("ai_preset"), preset.id, aiPresets.map { it.id to if (it.id == "siliconflow") siliconFlowLabel else it.label.ifBlank { customLabel } }, !busy) { id ->
            preset = aiPresets.first { it.id == id }
            name = if (preset.id == "siliconflow") siliconFlowLabel else preset.label.ifBlank { customLabel }; protocol = preset.protocol; endpoint = preset.endpoint
            auth = preset.auth; fullUrl = false; model = preset.models.firstOrNull().orEmpty(); models = preset.models
            secret = ""; status = null
        }
        OutlinedTextField(name, { name = it.take(80) }, label = { Text(tr("ai_profile_name")) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it.take(240) }, label = { Text(tr("ai_profile_note")) }, enabled = !busy, maxLines = 3, modifier = Modifier.fillMaxWidth())
        if (preset.id == "custom") AiChoice(tr("ai_protocol"), protocol.name,
            AiProvider.entries.map { it.name to tr(it.labelKey) }, !busy) {
            protocol = AiProvider.valueOf(it); models = emptyList(); model = ""; secret = ""; status = null
        }
        if (protocol == AiProvider.COMPATIBLE) {
            OutlinedTextField(endpoint, { endpoint = it; models = emptyList(); status = null }, label = { Text(tr("ai_endpoint")) },
                enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (preset.id == "custom") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr("ai_full_url"), modifier = Modifier.weight(1f).padding(top = 12.dp))
                    Switch(fullUrl, { fullUrl = it }, enabled = !busy)
                }
                AiChoice(tr("ai_auth"), auth.name, AiAuth.entries.map { it.name to when (it) {
                    AiAuth.BEARER -> "Authorization: Bearer"; AiAuth.API_KEY -> "api-key"; AiAuth.X_API_KEY -> "x-api-key"
                } }, !busy) { auth = AiAuth.valueOf(it) }
            }
        } else Text("generativelanguage.googleapis.com", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(secret, { secret = it; status = null }, label = { Text("API Key") }, enabled = !busy,
            visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker = true }, enabled = !busy && models.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(tr("ai_select_model")) }
            OutlinedButton(onClick = {
                val snapshot = config()
                runAction { models = fetchAiModels(snapshot); if (selectableAiModels(models).isEmpty()) "ai_models_empty" else "ai_models_loaded" }
            }, enabled = !busy && secret.isNotBlank(), modifier = Modifier.weight(1f)) { Text(tr("ai_fetch_models")) }
        }
        OutlinedTextField(model, { model = it; status = null }, label = { Text(tr("ai_model_manual")) },
            enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(tr("ai_models_note"), style = MaterialTheme.typography.bodySmall)
        Text(tr("ai_models_filter_note"), style = MaterialTheme.typography.bodySmall)
        Text(tr("ai_test_note"), style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = !busy && secret.isNotBlank() && model.isNotBlank(), onClick = {
            val snapshot = config()
            runAction { requestMailAi(snapshot, listOf(AiTurn("system", "Reply briefly."), AiTurn("user", "Hello"))); "ai_test_ok" }
        }) { Text(tr("translation_test")) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall) }
    }
    if (picker) {
        var search by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { picker = false }, title = { Text(tr("ai_select_model")) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(search, { search = it }, label = { Text(tr("ai_search_model")) }, singleLine = true)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    val matches = visibleModels.filter { it.contains(search, ignoreCase = true) }
                    if (matches.isEmpty()) item { Text(tr("ai_models_no_match")) }
                    items(matches, key = { it }) { option ->
                        TextButton(onClick = { model = option; picker = false }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(option)
                                if (aiModelCapability(option) == AiModelCapability.UNVERIFIED) {
                                    Text(tr("ai_model_unverified"), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { picker = false }) { Text(tr("close")) } })
    }
}

@Composable
private fun AiChoice(label: String, selected: String, options: List<Pair<String, String>>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(Modifier.fillMaxWidth().onSizeChanged { anchorWidth = it.width }) {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(options.firstOrNull { it.first == selected }?.second.orEmpty() + " ▾")
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false },
                modifier = Modifier.width(with(density) { anchorWidth.toDp() })) {
                options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false }) }
            }
        }
    }
}
