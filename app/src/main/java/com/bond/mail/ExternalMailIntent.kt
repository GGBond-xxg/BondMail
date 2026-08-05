package com.bond.mail

import android.content.Intent
import android.net.Uri
import androidx.core.net.MailTo

/**
 * A one-shot request from another app to write an e-mail.
 *
 * Keeping this independent from the navigation state lets a cold-start request survive startup
 * preload and the biometric gate, while [requestId] makes subsequent singleTop intents observable.
 */
data class ExternalComposeRequest(
    val requestId: Long,
    val to: String = "",
    val cc: String = "",
    val bcc: String = "",
    val subject: String = "",
    val body: String = "",
    val attachmentUris: List<String> = emptyList(),
)

internal object ExternalMailIntentParser {
    fun parse(intent: Intent, requestId: Long): ExternalComposeRequest? {
        val action = intent.action
        val mailTo = intent.data
            ?.takeIf(MailTo::isMailTo)
            ?.let { uri -> runCatching { MailTo.parse(uri) }.getOrNull() }

        val supported = when (action) {
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> mailTo != null
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> true
            else -> false
        }
        if (!supported) return null

        val to = joinAddressFields(
            mailTo?.to,
            intent.stringValues(Intent.EXTRA_EMAIL),
        )
        val cc = joinAddressFields(
            mailTo?.cc,
            intent.stringValues(Intent.EXTRA_CC),
        )
        val bcc = joinAddressFields(
            mailTo?.bcc,
            intent.stringValues(Intent.EXTRA_BCC),
        )
        val subject = mailTo?.subject
            ?.takeIf(String::isNotBlank)
            ?: intent.charSequenceValue(Intent.EXTRA_SUBJECT)
        val body = mailTo?.body
            ?.takeIf(String::isNotBlank)
            ?: intent.charSequenceValue(Intent.EXTRA_TEXT)
                .ifBlank { intent.charSequenceValue(Intent.EXTRA_HTML_TEXT) }

        return ExternalComposeRequest(
            requestId = requestId,
            to = to,
            cc = cc,
            bcc = bcc,
            subject = subject,
            body = body,
            attachmentUris = intent.streamUris().map(Uri::toString),
        )
    }

    private fun joinAddressFields(
        mailToValue: String?,
        extraValues: List<String>,
    ): String = buildList {
        mailToValue?.trim()?.takeIf(String::isNotBlank)?.let(::add)
        extraValues.map(String::trim).filter(String::isNotBlank).forEach(::add)
    }.distinct().joinToString(", ")

    private fun Intent.charSequenceValue(key: String): String =
        extras?.get(key)?.let { value ->
            when (value) {
                is CharSequence -> value.toString()
                else -> value.toString()
            }
        }.orEmpty()

    private fun Intent.stringValues(key: String): List<String> =
        when (val value = extras?.get(key)) {
            is String -> listOf(value)
            is Array<*> -> value.filterIsInstance<String>()
            is Iterable<*> -> value.filterIsInstance<String>()
            else -> emptyList()
        }

    private fun Intent.streamUris(): List<Uri> = buildList {
        when (val stream = extras?.get(Intent.EXTRA_STREAM)) {
            is Uri -> add(stream)
            is Array<*> -> stream.filterIsInstance<Uri>().forEach(::add)
            is Iterable<*> -> stream.filterIsInstance<Uri>().forEach(::add)
        }
        clipData?.let { clips ->
            repeat(clips.itemCount) { index ->
                clips.getItemAt(index).uri?.let(::add)
            }
        }
    }.distinctBy(Uri::toString)
}
