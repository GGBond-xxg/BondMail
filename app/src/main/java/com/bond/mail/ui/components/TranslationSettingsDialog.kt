package com.bond.mail.ui.components

import com.bond.mail.ui.theme.BondSecondaryButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bond.mail.data.mail.*
import com.bond.mail.data.security.CredentialStore
import com.bond.mail.ui.i18n.tr
import org.json.JSONObject
import kotlinx.coroutines.*

@Composable
internal fun TranslationSettingsDialog(initialProvider: TranslationProvider? = null,
    onProviderSaved: (TranslationProvider) -> Unit = {}, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CredentialStore(context) }
    var provider by remember(initialProvider) { mutableStateOf(initialProvider ?: store.translationProvider()) }
    val saved = remember(provider) { store.translationCredentials(provider) }
    var id by remember(provider) { mutableStateOf(saved?.id.orEmpty()) }
    var secret by remember(provider) { mutableStateOf(saved?.secret.orEmpty()) }
    var region by remember(provider) { mutableStateOf(saved?.region.orEmpty()) }
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { testJob?.cancel() } }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(tr("translation_settings")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TranslationProviderPicker(provider) { testJob?.cancel(); testing = false; testResult = null; provider = it; error = false }
                Text(tr("translation_credentials_note"), style = MaterialTheme.typography.bodySmall)
                Text(tr(if (provider == TranslationProvider.GOOGLE || provider == TranslationProvider.MICROSOFT)
                    "translation_global_note" else "translation_china_note"), style = MaterialTheme.typography.bodySmall)
                if (provider.needsId) OutlinedTextField(value = id, onValueChange = { id = it },
                    label = { Text(if (provider == TranslationProvider.ALIYUN) "AccessKey ID" else "App ID") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = secret, onValueChange = { secret = it },
                    label = { Text(when (provider) {
                        TranslationProvider.ALIYUN -> "AccessKey Secret"
                        TranslationProvider.YOUDAO -> "App Secret"
                        else -> "API Key"
                    }) }, visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (provider == TranslationProvider.MICROSOFT) OutlinedTextField(value = region,
                    onValueChange = { region = it }, label = { Text(tr("translation_region")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(tr("translation_test_note"), style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !testing && secret.isNotBlank() && (!provider.needsId || id.isNotBlank()), onClick = {
                    val selected = provider
                    val credentials = TranslationCredentials(id.trim(), secret.trim(), region.trim())
                    testing = true; testResult = null
                    testJob = scope.launch {
                        try {
                            withTimeout(45_000) { translateBody("Hello.", "zh", credentials, selected) }
                            testResult = "translation_test_ok"
                        } catch (_: TimeoutCancellationException) { testResult = "translation_network"
                        } catch (e: CancellationException) { throw e
                        } catch (e: TranslationFailure) { testResult = e.reason
                        } catch (_: Exception) { testResult = "translation_network"
                        } finally { testing = false }
                    }
                }) { Text(tr(if (testing) "translation_working" else "translation_test")) }
                testResult?.let { Text(tr(it)) }
                if (error) Text(tr("translation_save_failed"), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { store.delete(provider.credentialKey()); id = ""; secret = ""; region = "" }) {
                    Text(tr("translation_remove_key"))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = secret.isNotBlank() && (!provider.needsId || id.isNotBlank()), onClick = {
                runCatching {
                    store.save(provider.credentialKey(), JSONObject().put("id", id.trim())
                        .put("secret", secret.trim()).put("region", region.trim()).toString())
                    store.save(TRANSLATION_ACTIVE_KEY, provider.name)
                }.onSuccess { onProviderSaved(provider); onDismiss() }.onFailure { error = true }
            }) { Text(tr("save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("cancel")) } },
    )
}

@Composable
internal fun TranslationProviderPicker(provider: TranslationProvider, modifier: Modifier = Modifier, enabled: Boolean = true, onSelect: (TranslationProvider) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        BondSecondaryButton(onClick = { expanded = true }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
            Text(tr(provider.labelKey) + " ▾", maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TranslationProvider.entries.forEach { option ->
                DropdownMenuItem(text = { Text(tr(option.labelKey)) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}
