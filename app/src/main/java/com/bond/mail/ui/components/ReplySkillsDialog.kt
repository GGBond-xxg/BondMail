package com.bond.mail.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import com.bond.mail.ui.motion.bondMotionEnabled
import com.bond.mail.ui.motion.BondMotionDuration
import kotlinx.coroutines.flow.first
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.theme.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bond.mail.data.ai.*
import com.bond.mail.data.security.CredentialStore
import com.bond.mail.ui.i18n.tr
import kotlinx.coroutines.*
import java.util.UUID

@Composable
internal fun ReplySkillsDialog(onChanged: () -> Unit = {}, onDismiss: () -> Unit) {
    val visible = remember { MutableTransitionState(false) }
    val duration = if (bondMotionEnabled()) BondMotionDuration.SharedAxis else 0
    LaunchedEffect(Unit) {
        visible.targetState = true
        snapshotFlow { visible.isIdle && !visible.currentState && !visible.targetState }.first { it }
        onDismiss()
    }
    Dialog(onDismissRequest = { visible.targetState = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AnimatedVisibility(visibleState = visible,
            enter = slideInHorizontally(tween(duration)) { it } + fadeIn(tween(duration)),
            exit = slideOutHorizontally(tween(duration)) { it } + fadeOut(tween(duration))) {
            ReplySkillsScreen(onChanged, onDismiss = { visible.targetState = false })
        }
    }
}

@Composable
internal fun ReplySkillsScreen(onChanged: () -> Unit = {}, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CredentialStore(context) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var skills by remember { mutableStateOf(try { store.replySkills() } catch (_: Exception) {
        loadFailed = true; error = "ai_skill_load_failed"; ReplySkills()
    }) }
    var editing by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var linkImport by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ReplySkill?>(null) }
    val scope = rememberCoroutineScope()
    fun save(next: ReplySkills) {
        try { store.saveReplySkills(next); skills = next; error = null; onChanged() }
        catch (_: Exception) { error = "translation_save_failed" }
    }
    fun importText(loader: suspend () -> String) {
        busy = true; error = null
        scope.launch {
            try { content = loader(); editingId = null; name = ""; editing = true; importing = false; linkImport = false }
            catch (e: CancellationException) { throw e }
            catch (e: AiFailure) { error = e.reason }
            catch (_: Exception) { error = "ai_skill_import_failed" }
            finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importText {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use(::readReplySkill) ?: throw AiFailure("ai_skill_import_failed")
            }
        }
    }
    fun back() {
        if (editing) { editing = false; error = null } else onDismiss()
    }
    BackHandler { back() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.bondSurfaces.page) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            BondTopAppBar(title = tr(if (editing) "ai_edit_profile" else "ai_skills_title"), navigationIcon = {
                BondIconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("back")) }
            }, actions = {
                if (!editing) BondIconButton(enabled = !busy && !loadFailed && skills.items.size < 20,
                    onClick = { importing = true; error = null }) { Icon(Icons.Default.Add, tr("ai_skill_import")) }
            })
            key(editing, editingId) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (editing) {
                        Text(tr("ai_skill_preview_note"), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(name, { name = it.take(80) }, label = { Text(tr("ai_skill_name")) },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(content, { content = it }, label = { Text(tr("ai_skill_content")) },
                            minLines = 10, modifier = Modifier.fillMaxWidth(), isError = content.length > REPLY_SKILL_LIMIT,
                            supportingText = { Text("${content.length} / $REPLY_SKILL_LIMIT") })
                    } else {
                        Text(tr("ai_skill_list_note"), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SkillCard {
                            Row(Modifier.fillMaxWidth().selectable(selected = skills.active == null,
                                enabled = !loadFailed && !busy, role = Role.RadioButton,
                                onClick = { save(skills.copy(activeId = null)) }).padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("ai_skills_none"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                RadioButton(selected = skills.active == null, onClick = null, enabled = !loadFailed && !busy)
                            }
                        }
                        skills.items.forEach { item ->
                            key(item.id) {
                                var menu by remember { mutableStateOf(false) }
                                SkillCard {
                                    Row(Modifier.fillMaxWidth().selectable(selected = skills.activeId == item.id,
                                        enabled = !busy, role = Role.RadioButton,
                                        onClick = { save(skills.copy(activeId = item.id)) }).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                                            Text(item.content.lineSequence().filter { it.isNotBlank() }.joinToString(" "),
                                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (skills.activeId == item.id) Text(tr("ai_skill_active"),
                                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        }
                                        RadioButton(selected = skills.activeId == item.id, onClick = null, enabled = !busy)
                                        Box {
                                            BondIconButton(enabled = !busy, onClick = { menu = true }) { Icon(Icons.Default.MoreVert, tr("more")) }
                                            DropdownMenu(menu, { menu = false }) {
                                                DropdownMenuItem(text = { Text(tr("ai_edit_profile")) }, onClick = {
                                                    menu = false; editingId = item.id; name = item.name; content = item.content; editing = true; error = null
                                                })
                                                DropdownMenuItem(text = { Text(tr("delete"), color = MaterialTheme.colorScheme.error) },
                                                    onClick = { menu = false; deleting = item })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (skills.items.isEmpty() && !loadFailed) SkillCard {
                            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(tr("ai_skill_empty"), style = MaterialTheme.typography.titleMedium)
                                Text(tr("ai_skill_empty_note"), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FilledTonalButton(enabled = !busy, onClick = { importing = true; error = null }) { Text(tr("ai_skill_import")) }
                            }
                        }
                        TextButton(onClick = { showHelp = true }) { Text(tr("ai_skill_help")) }
                    }
                    error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (editing) Button(modifier = Modifier.fillMaxWidth().padding(16.dp),
                enabled = name.isNotBlank() && content.isNotBlank() && content.length <= REPLY_SKILL_LIMIT,
                onClick = {
                    try {
                        val item = ReplySkill(editingId ?: UUID.randomUUID().toString(), name.trim(), parseReplySkill(content.toByteArray(Charsets.UTF_8)))
                        save(ReplySkills(skills.items.filterNot { it.id == item.id } + item, item.id))
                        if (error == null) editing = false
                    } catch (e: AiFailure) { error = e.reason }
                }) { Text(tr("ai_skill_save_use")) }
        }
    }
    if (importing) AlertDialog(onDismissRequest = { if (!busy) importing = false },
        title = { Text(tr("ai_skill_import")) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("ai_skill_preview_note"))
                BondSecondaryButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    importing = false; picker.launch(arrayOf("text/*", "application/octet-stream"))
                }) { Text(tr("ai_skill_file")) }
                BondSecondaryButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    importing = false; linkImport = true; url = ""
                }) { Text(tr("ai_skill_from_link")) }
            }
        }, confirmButton = { TextButton(onClick = { importing = false }) { Text(tr("cancel")) } })
    if (linkImport) AlertDialog(onDismissRequest = { if (!busy) { linkImport = false; error = null } },
        title = { Text(tr("ai_skill_from_link")) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(url, { url = it }, enabled = !busy, label = { Text(tr("ai_skill_url")) }, modifier = Modifier.fillMaxWidth())
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(enabled = !busy && url.isNotBlank(), onClick = {
            val source = url; importText { fetchReplySkill(source) }
        }) { Text(tr("ai_skill_fetch")) } }, dismissButton = {
            TextButton(enabled = !busy, onClick = { linkImport = false; error = null }) { Text(tr("cancel")) }
        })
    if (showHelp) AlertDialog(onDismissRequest = { showHelp = false }, title = { Text(tr("ai_skill_help")) },
        text = { Text(tr("ai_skills_note"), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { showHelp = false }) { Text(tr("close")) } })
    deleting?.let { item -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(tr("delete")) }, text = { Text(item.name) },
        confirmButton = { TextButton(onClick = {
            save(ReplySkills(skills.items.filterNot { it.id == item.id }, skills.activeId.takeUnless { it == item.id })); deleting = null
        }) { Text(tr("delete")) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("cancel")) } }) }
}

@Composable
private fun SkillCard(content: @Composable ColumnScope.() -> Unit) {
    if (LocalUiStyle.current == UiStyle.MIUIX) MiuixSettingsCard(content)
    else Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.bondSurfaces.content), content = content)
}
