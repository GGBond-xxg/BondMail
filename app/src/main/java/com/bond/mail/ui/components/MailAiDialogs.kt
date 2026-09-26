package com.bond.mail.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bond.mail.data.ai.*
import com.bond.mail.data.mail.translationBodyText
import com.bond.mail.data.security.CredentialStore
import com.bond.mail.ui.i18n.LocalJsonStrings
import com.bond.mail.ui.i18n.tr
import kotlinx.coroutines.*

private data class AiAnswer(val task: String, val text: String, val draft: Boolean)

@Composable
internal fun MailAiDialog(subject: String, html: String?, plain: String, bodyReady: Boolean,
    onUseReply: (String) -> Unit, onDismiss: () -> Unit,
    loadConfig: (CredentialStore) -> AiConfig? = { it.activeAiConfig() },
    generate: suspend (AiConfig, List<AiTurn>) -> String = ::requestMailAi) {
    val context = LocalContext.current
    val store = remember { CredentialStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    val config = remember(revision) { loadConfig(store) }
    var skillsOpen by remember { mutableStateOf(false) }
    var skillsRevision by remember { mutableIntStateOf(0) }
    val activeSkill = remember(skillsRevision, revision) { runCatching { store.replySkills().active }.getOrNull() }
    var settings by remember { mutableStateOf(false) }
    val body = remember(html, plain) { translationBodyText(html, plain) }
    var question by remember { mutableStateOf("") }
    var answers by remember { mutableStateOf(emptyList<AiAnswer>()) }
    var history by remember { mutableStateOf(emptyList<AiTurn>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val strings = LocalJsonStrings.current
    val language = strings.locale.toLanguageTag()
    val ready = bodyReady && body.isNotBlank() && subject.length + body.length <= AI_CONTEXT_LIMIT
    fun reset() { job?.cancel(); job = null; busy = false; answers = emptyList(); history = emptyList(); status = null }
    fun ask(task: String, draft: Boolean = false, conversation: Boolean = false) {
        val selected = config ?: run { settings = true; return }
        val previous = if (conversation) history else emptyList()
        busy = true; status = null
        job = scope.launch {
            try {
                val messages = aiMessages(subject, body, language, previous, task, if (draft) activeSkill?.content else null)
                val answer = withTimeout(120_000) { generate(selected, messages) }
                history = previous + listOf(AiTurn("user", task), AiTurn("assistant", answer))
                answers = (if (conversation) answers else emptyList()) + AiAnswer(task, answer, draft)
                if (conversation) question = ""
            } catch (_: TimeoutCancellationException) { status = "ai_network"
            } catch (e: CancellationException) { throw e
            } catch (e: AiFailure) { status = e.reason
            } catch (_: Exception) { status = "ai_network"
            } finally { busy = false }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr("ai_title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(top = 10.dp))
                    TextButton(enabled = !busy, onClick = { settings = true }) { Text(tr("ai_settings_short")) }
                    TextButton(onClick = onDismiss) { Text(tr("close")) }
                }
                Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(subject, style = MaterialTheme.typography.titleMedium)
                    Text(config?.let { "${it.displayName.ifBlank { strings.text(it.provider.labelKey) }} · ${it.model}\n${java.net.URI(aiDisplayEndpoint(it)).host.orEmpty()}" }
                        ?: tr("ai_not_configured"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text(tr("ai_mail_note"), style = MaterialTheme.typography.bodySmall)
                    if (!ready) Text(tr(if (subject.length + body.length > AI_CONTEXT_LIMIT) "ai_mail_too_long" else "ai_body_unavailable"),
                        color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), enabled = ready && !busy,
                            onClick = { ask(strings.text("ai_summary_prompt")) }) { Text(tr("ai_summary")) }
                        OutlinedButton(modifier = Modifier.weight(1f), enabled = ready && !busy,
                            onClick = { ask(strings.text("ai_tasks_prompt")) }) { Text(tr("ai_tasks")) }
                    }
                    OutlinedButton(enabled = !busy, onClick = { skillsOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("ai_skills_title") + ": " + (activeSkill?.name ?: tr("ai_skills_none")))
                    }
                    OutlinedTextField(question, { question = it.take(AI_QUESTION_LIMIT) }, enabled = !busy,
                        label = { Text(tr("ai_question")) }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), enabled = ready && !busy,
                            onClick = { ask(strings.text("ai_reply_prompt") + question, draft = true) }) { Text(tr("ai_reply")) }
                        FilledTonalButton(modifier = Modifier.weight(1f), enabled = ready && !busy && question.isNotBlank(),
                            onClick = { ask(question, conversation = true) }) { Text(tr("ai_ask")) }
                    }
                    answers.forEach { answer ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(answer.task, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                SelectionContainer { Text(answer.text, style = MaterialTheme.typography.bodyLarge) }
                                if (answer.draft) TextButton(enabled = !busy, onClick = { onUseReply(answer.text) }) { Text(tr("ai_use_reply")) }
                            }
                        }
                    }
                    status?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(tr("ai_working"), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                    if (busy) TextButton(onClick = { job?.cancel(); busy = false }) { Text(tr("cancel")) }
                    else if (answers.isNotEmpty()) TextButton(onClick = { reset() }) { Text(tr("ai_clear")) }
                }
            }
        }
    }
    if (skillsOpen) ReplySkillsDialog(onChanged = { skillsRevision++ }, onDismiss = { skillsOpen = false })
    if (settings) AiSettingsDialog(onSaved = { reset(); revision++ }, onDismiss = { settings = false })
}

private fun aiDisplayEndpoint(config: AiConfig) = if (config.provider == AiProvider.GEMINI)
    "https://generativelanguage.googleapis.com" else config.endpoint
