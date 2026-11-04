package com.bond.mail.data.mail

import java.util.Base64
import java.util.UUID
import javax.activation.DataHandler
import javax.mail.Part
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

/** Shared by SMTP and IMAP draft/sent copies. No transport or network access. */
internal object OutgoingMimeBody {
    fun applyTo(part: Part, text: String, html: String?) {
        if (html.isNullOrBlank()) {
            part.setContent(text, "text/plain; charset=UTF-8")
            return
        }
        val images = linkedMapOf<String, Pair<String, ByteArray>>()
        // The MIME parser resolves original CID images to data URLs for the reader. Restore real
        // inline MIME parts: Gmail/Outlook commonly strip data URLs from received HTML.
        val resolved = Regex("data:(image/[a-zA-Z0-9.+-]+);base64,([a-zA-Z0-9+/=]+)").replace(html) { match ->
            val dataUrl = match.value
            val existing = images[dataUrl]
            if (existing != null) return@replace "cid:${existing.first}"
            val bytes = runCatching { Base64.getDecoder().decode(match.groupValues[2]) }.getOrNull()
                ?: return@replace dataUrl
            val cid = "${UUID.randomUUID()}@bondmail.inline"
            images[dataUrl] = cid to bytes
            "cid:$cid"
        }
        val alternative = MimeMultipart("alternative")
        alternative.addBodyPart(MimeBodyPart().apply { setText(text, "UTF-8") })
        val htmlPart = MimeBodyPart().apply { setContent(resolved, "text/html; charset=UTF-8") }
        if (images.isEmpty()) {
            alternative.addBodyPart(htmlPart)
        } else {
            val related = MimeMultipart("related")
            related.addBodyPart(htmlPart)
            images.forEach { (url, image) ->
                related.addBodyPart(MimeBodyPart().apply {
                    dataHandler = DataHandler(ByteArrayDataSource(image.second, url.substringAfter("data:").substringBefore(';')))
                    disposition = Part.INLINE
                    setHeader("Content-ID", "<${image.first}>")
                })
            }
            alternative.addBodyPart(MimeBodyPart().apply { setContent(related) })
        }
        part.setContent(alternative, alternative.contentType)
    }
}
