package com.bond.mail.ui.components

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
            try { content = loader(); editingId = null; name = ""; editing = true }
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr("ai_skills_title"), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text(tr("close")) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr("ai_skills_note"), style = MaterialTheme.typography.bodySmall)
                    if (editing) {
                        OutlinedTextField(name, { name = it.take(80) }, label = { Text(tr("ai_skill_name")) }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(content, { content = it }, label = { Text(tr("ai_skill_content")) }, minLines = 8, maxLines = 14, modifier = Modifier.fillMaxWidth())
                        Text("${content.length} / $REPLY_SKILL_LIMIT", style = MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedButton(enabled = !loadFailed && !busy, onClick = { save(skills.copy(activeId = null)) }) {
                            Text(tr(if (skills.active == null) "ai_skills_none_active" else "ai_skills_none"))
                        }
                        skills.items.forEach { item ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                                    if (skills.activeId == item.id) Text(tr("ai_skill_active"), color = MaterialTheme.colorScheme.primary)
                                    Row {
                                        TextButton(enabled = !busy, onClick = { save(skills.copy(activeId = item.id)) }) { Text(tr("ai_activate")) }
                                        TextButton(enabled = !busy, onClick = { editingId = item.id; name = item.name; content = item.content; editing = true; error = null }) { Text(tr("ai_edit_profile")) }
                                        TextButton(enabled = !busy, onClick = { deleting = item }) { Text(tr("delete")) }
                                    }
                                }
                            }
                        }
                        if (skills.items.size < 20 && !loadFailed) {
                            OutlinedButton(enabled = !busy, onClick = { picker.launch(arrayOf("text/*", "application/octet-stream")) }) { Text(tr("ai_skill_file")) }
                            OutlinedTextField(url, { url = it }, enabled = !busy, label = { Text(tr("ai_skill_url")) }, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(enabled = !busy && url.isNotBlank(), onClick = { importText { fetchReplySkill(url) } }) { Text(tr("ai_skill_fetch")) }
                        }
                    }
                    error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                }
                if (editing) Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { editing = false; error = null }) { Text(tr("cancel")) }
                    TextButton(enabled = name.isNotBlank() && content.isNotBlank() && content.length <= REPLY_SKILL_LIMIT, onClick = {
                        try {
                            val item = ReplySkill(editingId ?: UUID.randomUUID().toString(), name.trim(), parseReplySkill(content.toByteArray(Charsets.UTF_8)))
                            save(ReplySkills(skills.items.filterNot { it.id == item.id } + item, item.id))
                            if (error == null) editing = false
                        } catch (e: AiFailure) { error = e.reason }
                    }) { Text(tr("ai_skill_save_use")) }
                }
            }
        }
    }
    deleting?.let { item -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(tr("delete")) }, text = { Text(item.name) },
        confirmButton = { TextButton(onClick = {
            save(ReplySkills(skills.items.filterNot { it.id == item.id }, skills.activeId.takeUnless { it == item.id })); deleting = null
        }) { Text(tr("delete")) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("cancel")) } }) }
}
