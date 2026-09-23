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

@Composable
internal fun AiSettingsDialog(onSaved: () -> Unit = {}, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CredentialStore(context) }
    var provider by remember { mutableStateOf(store.aiProvider()) }
    val saved = remember(provider) { store.aiConfig(provider) }
    var endpoint by remember(provider) { mutableStateOf(saved?.endpoint.orEmpty()) }
    var model by remember(provider) { mutableStateOf(saved?.model.orEmpty()) }
    var secret by remember(provider) { mutableStateOf(saved?.key.orEmpty()) }
    var menu by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    fun config() = AiConfig(provider, endpoint.trim(), model.trim(), secret.trim())
    fun stop() { job?.cancel(); job = null; busy = false; status = null }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("ai_settings")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                OutlinedButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(tr(provider.labelKey) + " ▾") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    AiProvider.entries.forEach { option -> DropdownMenuItem(text = { Text(tr(option.labelKey)) }, onClick = {
                        stop(); provider = option; menu = false
                    }) }
                }
            }
            Text(tr("ai_settings_note"), style = MaterialTheme.typography.bodySmall)
            if (provider == AiProvider.COMPATIBLE) OutlinedTextField(endpoint, { endpoint = it; status = null },
                label = { Text(tr("ai_endpoint")) }, placeholder = { Text("https://api.example.com/v1") },
                enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            else Text("generativelanguage.googleapis.com", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(model, { model = it; status = null }, label = { Text(tr("ai_model")) },
                enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(secret, { secret = it; status = null }, label = { Text("API Key") },
                enabled = !busy, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(tr("ai_test_note"), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                if (busy) stop() else {
                    val snapshot = config()
                    busy = true; status = null
                    job = scope.launch {
                        try {
                            withTimeout(120_000) { requestMailAi(snapshot, listOf(AiTurn("system", "Reply briefly."), AiTurn("user", "Hello"))) }
                            status = "ai_test_ok"
                        } catch (_: TimeoutCancellationException) { status = "ai_network"
                        } catch (e: CancellationException) { throw e
                        } catch (e: AiFailure) { status = e.reason
                        } catch (_: Exception) { status = "ai_network"
                        } finally { busy = false }
                    }
                }
            }) { Text(tr(if (busy) "cancel" else "translation_test")) }
            status?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall) }
            TextButton(enabled = !busy, onClick = {
                runCatching { store.delete(provider.storageKey()) }.onSuccess {
                    secret = ""; status = "ai_key_removed"; onSaved()
                }.onFailure { status = "translation_save_failed" }
            }) { Text(tr("translation_remove_key")) }
        }
    }, confirmButton = {
        TextButton(enabled = !busy, onClick = {
            try { store.saveAi(config()); onSaved(); onDismiss() }
            catch (e: AiFailure) { status = e.reason }
            catch (_: Exception) { status = "translation_save_failed" }
        }) { Text(tr("save")) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("cancel")) } })
}

private data class AiAnswer(val task: String, val text: String, val draft: Boolean)

@Composable
internal fun MailAiDialog(subject: String, html: String?, plain: String, bodyReady: Boolean,
    onUseReply: (String) -> Unit, onDismiss: () -> Unit,
    loadConfig: (CredentialStore) -> AiConfig? = { it.aiConfig(it.aiProvider()) },
    generate: suspend (AiConfig, List<AiTurn>) -> String = ::requestMailAi) {
    val context = LocalContext.current
    val store = remember { CredentialStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    val config = remember(revision) { loadConfig(store) }
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
                val messages = aiMessages(subject, body, language, previous, task)
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
                    Text(config?.let { "${strings.text(it.provider.labelKey)} · ${it.model}\n${java.net.URI(aiDisplayEndpoint(it)).host.orEmpty()}" }
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
    if (settings) AiSettingsDialog(onSaved = { reset(); revision++ }, onDismiss = { settings = false })
}

private fun aiDisplayEndpoint(config: AiConfig) = if (config.provider == AiProvider.GEMINI)
    "https://generativelanguage.googleapis.com" else config.endpoint
