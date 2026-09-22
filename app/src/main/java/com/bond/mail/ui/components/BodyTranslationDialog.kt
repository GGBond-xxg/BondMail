package com.bond.mail.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bond.mail.data.mail.*
import com.bond.mail.data.security.CredentialStore
import com.bond.mail.ui.i18n.LocalJsonStrings
import com.bond.mail.ui.i18n.tr
import kotlinx.coroutines.*

@Composable
fun BodyTranslationDialog(html: String?, plain: String, onResult: ((String, Boolean) -> Unit)? = null, onDismiss: () -> Unit) {
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
    var translated by remember(provider, target, html, plain) { mutableStateOf<String?>(null) }
    var bilingual by remember { mutableStateOf(false) }
    var showOriginal by remember(provider, target) { mutableStateOf(false) }
    var error by remember(provider, target) { mutableStateOf<String?>(null) }
    var request by remember(provider, target) { mutableIntStateOf(0) }
    var busy by remember(provider, target) { mutableStateOf(false) }
    var configure by remember { mutableStateOf(false) }
    LaunchedEffect(html, plain) {
        original = withContext(Dispatchers.Default) { translationBodyText(html, plain) }
        extracted = true
    }
    LaunchedEffect(request, provider, target, html, plain) {
        if (request == 0) return@LaunchedEffect
        error = null
        translated = cache.read(original, provider, target)
        if (translated != null) { showOriginal = false; return@LaunchedEffect }
        val credentials = store.translationCredentials(provider)
        if (credentials == null) { error = "translation_not_configured"; return@LaunchedEffect }
        busy = true
        try {
            translated = withTimeout(180_000) { translateBody(original, target, credentials, provider) }
            cache.write(original, provider, target, translated.orEmpty())
            showOriginal = false
        } catch (_: TimeoutCancellationException) { error = "translation_failed"
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: TranslationFailure) { error = failure.reason
        } catch (_: Exception) { error = "translation_network"
        } finally { busy = false }
    }
    if (configure) TranslationSettingsDialog { configure = false; provider = store.translationProvider() }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(tr("translate_body")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!busy) Column {
                    TranslationProviderPicker(provider) { provider = it }
                    Box {
                        OutlinedButton(onClick = { languagesOpen = true }) { Text(languages[target].orEmpty() + " ▾") }
                        DropdownMenu(expanded = languagesOpen, onDismissRequest = { languagesOpen = false }) {
                            languages.forEach { (code, label) -> DropdownMenuItem(text = { Text(label) },
                                onClick = { target = code; languagesOpen = false }) }
                        }
                    }
                }
                Text(tr("translation_send_note"), style = MaterialTheme.typography.bodySmall)
                if (extracted && original.isBlank()) Text(tr("translation_empty"))
                if (error != null) Text(tr(error!!), color = MaterialTheme.colorScheme.error)
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(tr("translation_working"))
                } else if (translated != null) {
                    Text(tr(if (showOriginal) "translation_original" else "translation_result"),
                        style = MaterialTheme.typography.labelLarge)
                    SelectionContainer {
                        Text(if (showOriginal) original else translated.orEmpty(),
                            Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState()))
                    }
                    TextButton(onClick = { showOriginal = !showOriginal }) {
                        Text(tr(if (showOriginal) "translation_result" else "translation_original"))
                    }
                }
                if (!busy && translated != null && onResult != null) Row { Checkbox(bilingual, { bilingual = it }); Text(tr("translation_bilingual")) }
                if (!busy && translated != null && onResult != null) TextButton(onClick = { onResult(translated!!, bilingual); onDismiss() }) { Text(tr("translation_inline")) }
                if (!busy) TextButton(onClick = { configure = true }) { Text(tr("translation_settings")) }
            }
        },
        confirmButton = {
            if (!busy && translated == null) TextButton(enabled = original.isNotBlank(), onClick = { request++ }) {
                Text(tr(if (error == null) "translate_body" else "retry"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("close")) } },
    )
}
