package com.bond.mail.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.bond.mail.data.mail.*
import com.bond.mail.data.security.CredentialStore
import com.bond.mail.ui.i18n.LocalJsonStrings
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.theme.BondIconButton
import kotlinx.coroutines.*

internal val translationLanguages = linkedMapOf("zh" to "简体中文", "zh-TW" to "繁體中文", "en" to "English",
    "ja" to "日本語", "ko" to "한국어", "fr" to "Français", "de" to "Deutsch", "es" to "Español")

@Stable
internal class InlineMailTranslationState(provider: TranslationProvider, target: String) {
    var provider by mutableStateOf(provider)
    var target by mutableStateOf(target)
    var result by mutableStateOf<TranslatedHtmlMail?>(null)
    var showOriginal by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var progress by mutableStateOf(0 to 0)
    var error by mutableStateOf<String?>(null)
    var configure by mutableStateOf(false)
    var request by mutableIntStateOf(0)
    val displayed: TranslatedHtmlMail? get() = result.takeUnless { showOriginal }
    fun reset() { request = 0; result = null; showOriginal = false; error = null }
    fun translateOrCancel() {
        if (busy) request = 0
        else if (result != null) showOriginal = false
        else request++
    }
}

@Composable
internal fun rememberInlineMailTranslation(messageId: String, subject: String, html: String?, plain: String, senderAddress: String): InlineMailTranslationState {
    val context = LocalContext.current
    val store = remember { CredentialStore(context) }
    val cache = remember { TranslationCache(context) }
    val locale = LocalJsonStrings.current.locale
    val state = remember(messageId, subject, html, plain) {
        InlineMailTranslationState(store.translationProvider(), store.translationTarget(locale))
    }
    LaunchedEffect(state, state.request, state.provider, state.target) {
        if (state.request == 0) return@LaunchedEffect
        state.busy = true; state.error = null
        try {
            val provider = state.provider
            val target = state.target
            val result = withTimeout(600_000) {
                translateHtmlMail(subject, html, plain, onProgress = { done, total -> state.progress = done to total }, prepareDocument = { document ->
                    val domain = senderAddress.substringAfterLast('@').lowercase()
                    if (domain == "ifastgb.com" || domain.endsWith(".ifastgb.com")) markIfastFooter(document)
                }) { text ->
                    cache.read(text, provider, target) ?: run {
                        val credentials = store.translationCredentials(provider) ?: throw TranslationFailure("translation_not_configured")
                        translateBody(text, target, credentials, provider).also { cache.write(text, provider, target, it) }
                    }
                }
            }
            state.result = result; state.showOriginal = false
        } catch (_: TimeoutCancellationException) { state.error = "translation_network"
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: TranslationFailure) {
            state.error = failure.reason
            if (failure.reason == "translation_not_configured") state.configure = true
        } catch (_: Exception) { state.error = "translation_network"
        } finally { state.busy = false }
    }
    if (state.configure) TranslationSettingsDialog(initialProvider = state.provider, onProviderSaved = { savedProvider ->
        state.reset(); state.provider = savedProvider
    }) { state.configure = false }
    return state
}

/** Both controls only select options; network requests start at the bottom Translate button. */
@Composable
internal fun InlineTranslationSelectors(state: InlineMailTranslationState) {
    val context = LocalContext.current
    val store = remember { CredentialStore(context) }
    var languagesOpen by remember { mutableStateOf(false) }
    var providersOpen by remember { mutableStateOf(false) }
    com.bond.mail.ui.theme.BondPopupMenu(languagesOpen, { languagesOpen = false },
        translationLanguages.map { (code, label) -> com.bond.mail.ui.theme.BondMenuEntry(label, {
            runCatching { store.save(TRANSLATION_TARGET_KEY, code) }
                .onSuccess { state.reset(); state.target = code }
                .onFailure { state.error = "translation_save_failed" }
            languagesOpen = false
        }, selected = state.target == code) }) {
        BondIconButton(enabled = !state.busy, onClick = { languagesOpen = true }) {
            Icon(Icons.Outlined.Language, contentDescription = tr("translation_target_language") + ": " + translationLanguages[state.target])
        }
    }
    com.bond.mail.ui.theme.BondPopupMenu(providersOpen, { providersOpen = false },
        TranslationProvider.entries.map { provider -> com.bond.mail.ui.theme.BondMenuEntry(tr(provider.labelKey), {
            runCatching { store.save(TRANSLATION_ACTIVE_KEY, provider.name) }
                .onSuccess { state.reset(); state.provider = provider }
                .onFailure { state.error = "translation_save_failed" }
            providersOpen = false
        }, selected = state.provider == provider) } + com.bond.mail.ui.theme.BondMenuEntry(tr("translation_settings"), {
            providersOpen = false; state.configure = true
        })) {
        BondIconButton(enabled = !state.busy, onClick = { providersOpen = true }) {
            Icon(Icons.Outlined.Key, contentDescription = tr("translation_provider") + ": " + tr(state.provider.labelKey))
        }
    }
}
