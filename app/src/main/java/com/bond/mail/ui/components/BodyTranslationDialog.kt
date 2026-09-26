package com.bond.mail.ui.components

import com.bond.mail.ui.theme.BondSecondaryButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bond.mail.data.mail.*
import com.bond.mail.data.security.CredentialStore
import com.bond.mail.ui.i18n.LocalJsonStrings
import com.bond.mail.ui.i18n.tr
import kotlinx.coroutines.*

@Composable
internal fun BodyTranslationDialog(html: String?, plain: String, subject: String = "",
    onResult: ((String, String, Boolean) -> Unit)? = null,
    translateText: (suspend (String, TranslationProvider, String) -> String)? = null,
    onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CredentialStore(context) }
    val cache = remember { TranslationCache(context) }
    val locale = LocalJsonStrings.current.locale
    var provider by remember { mutableStateOf(store.translationProvider()) }
    var target by remember { mutableStateOf(if (locale.language == "zh") {
        if (locale.country in setOf("TW", "HK", "MO")) "zh-TW" else "zh"
    } else "en") }
    var languagesOpen by remember { mutableStateOf(false) }
    val languages = linkedMapOf("zh" to "简体中文", "zh-TW" to "繁體中文", "en" to "English",
        "ja" to "日本語", "ko" to "한국어", "fr" to "Français", "de" to "Deutsch", "es" to "Español")
    var original by remember(html, plain) { mutableStateOf("") }
    var extracted by remember(html, plain) { mutableStateOf(false) }
    var translated by remember(provider, target, html, plain, subject) { mutableStateOf<TranslatedMailText?>(null) }
    var mode by remember(provider, target) { mutableIntStateOf(0) }
    var error by remember(provider, target) { mutableStateOf<String?>(null) }
    var request by remember(provider, target) { mutableIntStateOf(0) }
    var busy by remember(provider, target) { mutableStateOf(false) }
    var configure by remember { mutableStateOf(false) }
    LaunchedEffect(html, plain) {
        original = withContext(Dispatchers.Default) { translationBodyText(html, plain) }
        extracted = true
    }
    LaunchedEffect(request, provider, target, html, plain, subject) {
        if (request == 0) return@LaunchedEffect
        error = null; busy = true
        try {
            // Commit both fields together. A partial success is cached but never presented as a
            // completely translated email; retry reuses successful segments without another charge.
            val result = withTimeout(180_000) {
                translateMailText(subject, original) { text ->
                    translateText?.invoke(text, provider, target) ?: cache.read(text, provider, target) ?: run {
                        val credentials = store.translationCredentials(provider)
                            ?: throw TranslationFailure("translation_not_configured")
                        translateBody(text, target, credentials, provider).also {
                            cache.write(text, provider, target, it)
                        }
                    }
                }
            }
            translated = result; mode = 0
        } catch (_: TimeoutCancellationException) { error = "translation_network"
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: TranslationFailure) { error = failure.reason
        } catch (_: Exception) { error = "translation_network"
        } finally { busy = false }
    }
    if (configure) TranslationSettingsDialog { configure = false; provider = store.translationProvider() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(horizontal = 16.dp).widthIn(max = 600.dp).fillMaxWidth().fillMaxHeight(.9f),
            shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("translate_body"), style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TranslationProviderPicker(provider, Modifier.weight(1f), enabled = !busy) { provider = it }
                    Box(Modifier.weight(1f)) {
                        BondSecondaryButton(onClick = { languagesOpen = true }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(languages[target].orEmpty() + " ▾", maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        DropdownMenu(expanded = languagesOpen, onDismissRequest = { languagesOpen = false }) {
                            languages.forEach { (code, label) -> DropdownMenuItem(text = { Text(label) },
                                onClick = { target = code; languagesOpen = false }) }
                        }
                    }
                }
                if (translated != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("translation_tab_result", "translation_tab_compare", "translation_tab_original").forEachIndexed { index, label ->
                        FilterChip(mode == index, { mode = index }, modifier = Modifier.weight(1f), label = {
                            Text(tr(label), style = MaterialTheme.typography.labelMedium, maxLines = 2)
                        })
                    }
                }
                // Only the document scrolls. Selectors and action buttons keep their own space,
                // including with long mail, large system fonts and a small display.
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr("translation_send_note"), style = MaterialTheme.typography.bodySmall)
                    error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
                    if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(tr("translation_working")) }
                    val result = translated
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (result != null && mode != 2) {
                                if (result.subject.isNotBlank()) Text(result.subject, style = MaterialTheme.typography.titleMedium)
                                Text(result.body)
                            }
                            if (result == null || mode != 0) {
                                if (result != null && mode == 1) { HorizontalDivider(); Text(tr("translation_original"), style = MaterialTheme.typography.labelLarge) }
                                if (subject.isNotBlank()) Text(subject, style = MaterialTheme.typography.titleMedium)
                                Text(original)
                            }
                        }
                    }
                    if (extracted && original.isBlank() && subject.isBlank()) Text(tr("translation_empty"))
                }
                HorizontalDivider()
                val result = translated
                if (result != null && onResult != null) Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    onResult(result.subject, result.body, mode == 1); onDismiss()
                }) { Text(tr("translation_inline")) }
                else if (result == null) Button(modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && extracted && (original.isNotBlank() || subject.isNotBlank()), onClick = { request++ }) {
                    Text(tr(if (busy) "translation_working" else if (error == null) "translate_body" else "retry"))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = !busy, modifier = Modifier.weight(1f), onClick = { configure = true }) { Text(tr("translation_settings")) }
                    TextButton(onClick = onDismiss) { Text(tr("close")) }
                }
            }
        }
    }
}
