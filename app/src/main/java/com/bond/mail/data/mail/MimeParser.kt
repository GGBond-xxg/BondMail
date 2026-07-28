package com.bond.mail.data.mail

import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLConnection
import java.net.URLDecoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import javax.mail.Address
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.internet.ContentType
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimePart
import javax.mail.internet.MimeUtility
import kotlin.math.min

internal data class ParsedMailHeader(
    val senderName: String,
    val senderAddress: String,
    val recipients: String,
    val cc: String,
    val subject: String,
    val receivedAt: Long,
    val internetMessageId: String?,
)

internal data class ParsedMail(
    val senderName: String,
    val senderAddress: String,
    val recipients: String,
    val cc: String,
    val subject: String,
    val text: String,
    val html: String?,
    val hasAttachments: Boolean,
    val attachments: List<MailAttachmentInfo>,
    val receivedAt: Long,
    val internetMessageId: String?,
)

internal object MimeParser {
    /** Bump this whenever cached MIME output must be fetched and parsed again once. */
    const val CURRENT_VERSION = 8

    private const val MAX_INLINE_IMAGE_BYTES = 5 * 1024 * 1024
    private const val MAX_TEXT_PART_BYTES = 12 * 1024 * 1024
    private const val MAX_MIME_DEPTH = 32

    fun parseHeader(message: Message): ParsedMailHeader {
        val from = message.from?.firstOrNull() as? InternetAddress
        return ParsedMailHeader(
            senderName = decodeSenderName(from),
            senderAddress = decodeAddress(from),
            recipients = addresses(message.getRecipients(Message.RecipientType.TO)),
            cc = addresses(message.getRecipients(Message.RecipientType.CC)),
            subject = decodeSubject(message.subject),
            receivedAt = (message.receivedDate ?: message.sentDate)?.time ?: System.currentTimeMillis(),
            internetMessageId = message.getHeader("Message-ID")?.firstOrNull(),
        )
    }

    fun parse(message: Message): ParsedMail {
        val header = parseHeader(message)
        val collector = BodyCollector()
        collect(
            part = message,
            result = collector,
            depth = 0,
            inheritedBaseUri = htmlBaseUri(message),
        )

        val bestHtmlCandidate = collector.bestHtml()
        // Thunderbird resolves inline attachments only when the selected HTML actually references
        // them. Defer image body reads until the best HTML candidate is known so opening a message
        // does not download every decorative/unused MIME image part.
        val inlineImages = bestHtmlCandidate
            ?.let { candidate -> loadReferencedInlineImages(candidate.value, collector.inlineCandidates) }
            .orEmpty()
        val bestHtml = bestHtmlCandidate?.let { candidate ->
            resolveInlineImages(candidate.value, inlineImages, candidate.baseUri)
        }
        val bestText = collector.bestText()
        val cleanText = bestText
            .ifBlank { bestHtml?.let { Jsoup.parse(it).text() }.orEmpty() }
            .replace("\u0000", "")
            .trim()

        MailLog.d(
            MailLog.IMAP,
            "mime parsed type=${safeContentType(message).substringBefore(';')} " +
                "htmlCandidates=${collector.htmlCandidates.size} textCandidates=${collector.textCandidates.size} " +
                "inlineParts=${collector.inlineCandidates.size} inlineAliases=${inlineImages.size} " +
                "attachments=${collector.attachments.size} htmlChars=${bestHtml?.length ?: 0} " +
                "textChars=${cleanText.length}",
        )

        return ParsedMail(
            senderName = header.senderName,
            senderAddress = header.senderAddress,
            recipients = header.recipients,
            cc = header.cc,
            subject = header.subject,
            text = cleanText,
            html = bestHtml?.takeIf { it.isNotBlank() },
            hasAttachments = collector.hasAttachments || collector.attachments.isNotEmpty(),
            attachments = collector.attachments.toList(),
            receivedAt = header.receivedAt,
            internetMessageId = header.internetMessageId,
        )
    }

    /**
     * Last-resort RFC822 parser for malformed multipart mail that JavaMail exposes as an empty
     * MimeMultipart. The parser intentionally extracts only displayable text/HTML and attachment
     * metadata; binary payloads are never retained in memory after this call.
     */
    fun parseRaw(raw: ByteArray, header: ParsedMailHeader): ParsedMail {
        val collector = BodyCollector()
        collectRawEntity(raw, collector, depth = 0)
        val bestHtml = collector.bestHtml()?.value
        val cleanText = collector.bestText()
            .ifBlank { bestHtml?.let { Jsoup.parse(it).text() }.orEmpty() }
            .replace("\u0000", "")
            .trim()
        MailLog.d(
            MailLog.IMAP,
            "mime raw fallback htmlCandidates=${collector.htmlCandidates.size} " +
                "textCandidates=${collector.textCandidates.size} attachments=${collector.attachments.size} " +
                "htmlChars=${bestHtml?.length ?: 0} textChars=${cleanText.length}",
        )
        return ParsedMail(
            senderName = header.senderName,
            senderAddress = header.senderAddress,
            recipients = header.recipients,
            cc = header.cc,
            subject = header.subject,
            text = cleanText,
            html = bestHtml?.takeIf(String::isNotBlank),
            hasAttachments = collector.hasAttachments || collector.attachments.isNotEmpty(),
            attachments = collector.attachments.toList(),
            receivedAt = header.receivedAt,
            internetMessageId = header.internetMessageId,
        )
    }

    fun preview(text: String): String = text.replace(Regex("\\s+"), " ").trim().take(160)

    fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun addresses(values: Array<Address>?): String =
        values.orEmpty().mapNotNull { address ->
            when (address) {
                is InternetAddress -> {
                    val personal = decodeText(address.personal).takeIf { it.isNotBlank() }
                    val email = decodeAddress(address)
                    when {
                        personal != null && email.isNotBlank() -> "$personal <$email>"
                        personal != null -> personal
                        else -> email
                    }
                }
                else -> decodeText(address.toString())
            }.trim().takeIf(String::isNotBlank)
        }.joinToString(", ")

    private fun collect(
        part: Part,
        result: BodyCollector,
        depth: Int,
        inheritedBaseUri: String?,
    ) {
        if (depth > MAX_MIME_DEPTH) {
            MailLog.w(MailLog.IMAP, "mime nesting exceeded $MAX_MIME_DEPTH levels")
            return
        }

        val disposition = runCatching { part.disposition }.getOrNull()
        val fileName = runCatching { decodeText(part.fileName) }.getOrNull()
        val contentId = header(part, "Content-ID")?.trim()?.trim('<', '>')?.takeIf(String::isNotBlank)
        val contentLocation = header(part, "Content-Location")?.trim()?.takeIf(String::isNotBlank)
        val isInline = Part.INLINE.equals(disposition, ignoreCase = true)
        val hasInlineReference = contentId != null || contentLocation != null
        val currentBaseUri = htmlBaseUri(part) ?: inheritedBaseUri
        val contentType = safeContentType(part)
        val isTextBody = part.matches("text/html") ||
            part.matches("application/xhtml+xml") ||
            part.matches("text/plain") ||
            part.matches("text/enriched")
        // Some transactional senders put `name=message.html` on the actual body without an INLINE
        // disposition. Treat filenames as attachments for binary parts, but do not discard a valid
        // HTML/plain body solely because it has a name.
        val isAttachment =
            (Part.ATTACHMENT.equals(disposition, ignoreCase = true) && !hasInlineReference) ||
                (!fileName.isNullOrBlank() && !isInline && !hasInlineReference && !isTextBody)

        when {
            part.matches("multipart/*") -> {
                val multipart = runCatching { part.content as? Multipart }.getOrNull()
                if (multipart == null) {
                    MailLog.w(MailLog.IMAP, "multipart content unavailable type=${safeContentType(part)}")
                    return
                }
                for (index in 0 until multipart.count) {
                    collect(
                        part = multipart.getBodyPart(index),
                        result = result,
                        depth = depth + 1,
                        inheritedBaseUri = currentBaseUri,
                    )
                }
            }

            part.matches("message/rfc822") -> {
                if (isAttachment) {
                    // Do not let a long forwarded-message attachment replace the actual message
                    // body when choosing the best HTML candidate.
                    result.hasAttachments = true
                    result.addAttachment(
                        MailAttachmentInfo(
                            name = fileName?.ifBlank { "Forwarded message.eml" } ?: "Forwarded message.eml",
                            contentType = contentType,
                            sizeBytes = partSize(part),
                        ),
                    )
                } else {
                    val nested = runCatching { part.content }.getOrNull()
                    when (nested) {
                        is Part -> collect(
                            part = nested,
                            result = result,
                            depth = depth + 1,
                            inheritedBaseUri = currentBaseUri,
                        )
                        is Multipart -> for (index in 0 until nested.count) {
                            collect(
                                part = nested.getBodyPart(index),
                                result = result,
                                depth = depth + 1,
                                inheritedBaseUri = currentBaseUri,
                            )
                        }
                    }
                }
            }

            (part.matches("text/html") || part.matches("application/xhtml+xml")) && !isAttachment -> {
                readText(part).takeIf(String::isNotBlank)?.let { html ->
                    result.addHtml(html, currentBaseUri)
                }
            }

            part.matches("text/plain") && !isAttachment -> {
                readText(part).takeIf(String::isNotBlank)?.let(result::addText)
            }

            part.matches("text/*") && !isAttachment -> {
                // Covers text/enriched and malformed text subtypes used by a few older senders.
                readText(part).takeIf(String::isNotBlank)?.let(result::addText)
            }

            part.matches("image/*") && !isAttachment -> {
                if (contentId != null || contentLocation != null || isInline) {
                    result.inlineCandidates += InlineImageCandidate(
                        part = part,
                        contentId = contentId,
                        contentLocation = contentLocation,
                        fileName = fileName,
                    )
                } else if (!fileName.isNullOrBlank()) {
                    result.hasAttachments = true
                    result.addAttachment(
                        MailAttachmentInfo(
                            name = fileName,
                            contentType = contentType,
                            sizeBytes = partSize(part),
                        ),
                    )
                }
            }

            else -> {
                if (isAttachment || !fileName.isNullOrBlank()) {
                    result.hasAttachments = true
                    result.addAttachment(
                        MailAttachmentInfo(
                            name = fileName?.trim()?.takeIf(String::isNotBlank)
                                ?: defaultAttachmentName(contentType, result.attachments.size + 1),
                            contentType = contentType,
                            sizeBytes = partSize(part),
                        ),
                    )
                } else if (looksLikeTextualOctetStream(contentType)) {
                    // A few SMTP/IMAP combinations expose the first body part as an unnamed
                    // application/octet-stream even though its payload is plain UTF-8 text. Apple
                    // Mail sniffs this body; JavaMail's strict MIME classification does not. Read a
                    // bounded sample only when there is no filename/disposition and keep binary
                    // attachments untouched.
                    readText(part).takeIf(::looksLikeReadableBody)?.let(result::addText)
                }
            }
        }
    }

    private fun collectRawEntity(
        raw: ByteArray,
        result: BodyCollector,
        depth: Int,
    ) {
        if (depth > MAX_MIME_DEPTH || raw.isEmpty()) return
        val entity = splitRawEntity(raw)
        val contentType = entity.headers.firstValue("content-type")
            .orEmpty()
            .ifBlank { "text/plain; charset=UTF-8" }
        val baseType = contentType.substringBefore(';').trim().lowercase(Locale.ROOT)
        val disposition = entity.headers.firstValue("content-disposition").orEmpty()
        val transferEncoding = entity.headers.firstValue("content-transfer-encoding").orEmpty()
        val fileName = decodeMimeParameter(disposition, "filename")
            ?: decodeMimeParameter(contentType, "name")
        val isInline = disposition.substringBefore(';').trim().equals("inline", ignoreCase = true)
        val isAttachment = disposition.substringBefore(';').trim().equals("attachment", ignoreCase = true) ||
            (!fileName.isNullOrBlank() && !isInline && !baseType.startsWith("text/"))

        when {
            baseType.startsWith("multipart/") -> {
                val boundary = decodeMimeParameter(contentType, "boundary")
                if (boundary.isNullOrBlank()) {
                    MailLog.w(MailLog.IMAP, "raw multipart missing boundary type=$baseType")
                    return
                }
                val children = splitRawMultipart(entity.body, boundary)
                if (children.isEmpty()) {
                    MailLog.w(MailLog.IMAP, "raw multipart empty type=$baseType boundaryLength=${boundary.length}")
                }
                children.forEach { child -> collectRawEntity(child, result, depth + 1) }
            }

            baseType == "message/rfc822" -> {
                if (isAttachment) {
                    result.hasAttachments = true
                    result.addAttachment(
                        MailAttachmentInfo(
                            name = fileName ?: "Forwarded message.eml",
                            contentType = contentType,
                            sizeBytes = entity.body.size.toLong(),
                        ),
                    )
                } else {
                    collectRawEntity(decodeTransferEncoding(entity.body, transferEncoding), result, depth + 1)
                }
            }

            (baseType == "text/html" || baseType == "application/xhtml+xml") && !isAttachment -> {
                decodeRawText(entity.body, contentType, transferEncoding)
                    .takeIf(String::isNotBlank)
                    ?.let { result.addHtml(it, null) }
            }

            baseType.startsWith("text/") && !isAttachment -> {
                decodeRawText(entity.body, contentType, transferEncoding)
                    .takeIf(String::isNotBlank)
                    ?.let(result::addText)
            }

            else -> {
                if (isAttachment || !fileName.isNullOrBlank()) {
                    result.hasAttachments = true
                    result.addAttachment(
                        MailAttachmentInfo(
                            name = fileName ?: defaultAttachmentName(contentType, result.attachments.size + 1),
                            contentType = contentType,
                            sizeBytes = decodedSizeEstimate(entity.body.size.toLong(), transferEncoding),
                        ),
                    )
                } else if (baseType == "application/octet-stream") {
                    decodeRawText(entity.body, contentType, transferEncoding)
                        .takeIf(::looksLikeReadableBody)
                        ?.let(result::addText)
                }
            }
        }
    }

    private data class RawMimeEntity(
        val headers: Map<String, List<String>>,
        val body: ByteArray,
    )

    private fun splitRawEntity(raw: ByteArray): RawMimeEntity {
        val text = raw.toString(StandardCharsets.ISO_8859_1)
        val crlfIndex = text.indexOf("\r\n\r\n")
        val lfIndex = text.indexOf("\n\n")
        val separatorIndex = when {
            crlfIndex >= 0 && (lfIndex < 0 || crlfIndex <= lfIndex) -> crlfIndex
            lfIndex >= 0 -> lfIndex
            else -> -1
        }
        if (separatorIndex < 0) return RawMimeEntity(emptyMap(), raw)
        val separatorLength = if (text.startsWith("\r\n\r\n", separatorIndex)) 4 else 2
        val headerBlock = text.substring(0, separatorIndex)
        val bodyText = text.substring(separatorIndex + separatorLength)
        return RawMimeEntity(
            headers = parseRawHeaders(headerBlock),
            body = bodyText.toByteArray(StandardCharsets.ISO_8859_1),
        )
    }

    private fun parseRawHeaders(block: String): Map<String, List<String>> {
        val unfolded = block.replace(Regex("\\r?\\n[ \\t]+"), " ")
        val result = linkedMapOf<String, MutableList<String>>()
        unfolded.lineSequence().forEach { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@forEach
            val name = line.substring(0, separator).trim().lowercase(Locale.ROOT)
            val value = line.substring(separator + 1).trim()
            result.getOrPut(name) { mutableListOf() } += value
        }
        return result
    }

    private fun Map<String, List<String>>.firstValue(name: String): String? =
        get(name.lowercase(Locale.ROOT))?.firstOrNull()

    private fun splitRawMultipart(body: ByteArray, boundary: String): List<ByteArray> {
        val text = body.toString(StandardCharsets.ISO_8859_1)
        val delimiter = "--$boundary"
        if (!text.contains(delimiter)) return emptyList()
        return text.split(delimiter)
            .drop(1)
            .mapNotNull { chunk ->
                val trimmedStart = chunk.removePrefix("\r\n").removePrefix("\n")
                if (trimmedStart.startsWith("--")) return@mapNotNull null
                val normalized = trimmedStart
                    .removeSuffix("\r\n")
                    .removeSuffix("\n")
                    .removeSuffix("\r\n")
                normalized.takeIf(String::isNotBlank)
                    ?.toByteArray(StandardCharsets.ISO_8859_1)
            }
    }

    private fun decodeMimeParameter(raw: String, name: String): String? {
        if (raw.isBlank()) return null
        val pattern = Regex(
            "(?i)(?:^|;)\\s*${Regex.escape(name)}(\\*)?\\s*=\\s*(?:\\\"([^\\\"]*)\\\"|'([^']*)'|([^;]*))",
        )
        val match = pattern.find(raw) ?: return null
        val encoded = match.groupValues[1].isNotBlank()
        val value = sequenceOf(match.groupValues[2], match.groupValues[3], match.groupValues[4])
            .firstOrNull(String::isNotBlank)
            ?.trim()
            ?: return null
        val decodedParameter = if (encoded) {
            val encodedValue = value.substringAfter("''", value)
            runCatching { URLDecoder.decode(encodedValue, StandardCharsets.UTF_8.name()) }
                .getOrDefault(encodedValue)
        } else value
        return decodeText(decodedParameter).trim().takeIf(String::isNotBlank)
    }

    private fun decodeRawText(body: ByteArray, contentType: String, transferEncoding: String): String {
        val decoded = decodeTransferEncoding(body, transferEncoding)
        if (decoded.isEmpty()) return ""
        val declared = declaredCharset(contentType)
        val candidates = buildList {
            declared?.let(::add)
            add(StandardCharsets.UTF_8)
            runCatching { Charset.forName("GB18030") }.getOrNull()?.let(::add)
            runCatching { Charset.forName("Big5") }.getOrNull()?.let(::add)
            runCatching { Charset.forName("windows-1252") }.getOrNull()?.let(::add)
        }.distinctBy { it.name().lowercase(Locale.ROOT) }
        return candidates
            .map { charset -> decoded.toString(charset) }
            .minByOrNull(::decodePenalty)
            .orEmpty()
            .removePrefix("\uFEFF")
            .replace("\u0000", "")
    }

    private fun decodeTransferEncoding(body: ByteArray, encoding: String): ByteArray = when {
        encoding.equals("base64", ignoreCase = true) -> runCatching {
            Base64.getMimeDecoder().decode(body)
        }.getOrDefault(body)
        encoding.equals("quoted-printable", ignoreCase = true) -> decodeQuotedPrintable(body)
        else -> body
    }

    private fun decodeQuotedPrintable(input: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(input.size)
        var index = 0
        while (index < input.size) {
            val value = input[index].toInt() and 0xFF
            if (value == '='.code && index + 1 < input.size) {
                val next = input[index + 1].toInt() and 0xFF
                if (next == '\r'.code && index + 2 < input.size && input[index + 2].toInt() == '\n'.code) {
                    index += 3
                    continue
                }
                if (next == '\n'.code) {
                    index += 2
                    continue
                }
                if (index + 2 < input.size) {
                    val hex = "${input[index + 1].toInt().toChar()}${input[index + 2].toInt().toChar()}"
                    val decoded = hex.toIntOrNull(16)
                    if (decoded != null) {
                        output.write(decoded)
                        index += 3
                        continue
                    }
                }
            }
            output.write(value)
            index += 1
        }
        return output.toByteArray()
    }

    private fun decodedSizeEstimate(encodedSize: Long, transferEncoding: String): Long = when {
        transferEncoding.equals("base64", ignoreCase = true) -> encodedSize * 3L / 4L
        else -> encodedSize
    }

    private fun htmlBaseUri(part: Part): String? =
        listOfNotNull(header(part, "Content-Base"), header(part, "Content-Location"))
            .map(String::trim)
            .firstOrNull { value ->
                value.startsWith("https://", ignoreCase = true) ||
                    value.startsWith("http://", ignoreCase = true)
            }

    private fun readText(part: Part): String {
        val directContent = runCatching { part.content }.getOrNull()
        if (directContent is String) return directContent.replace("\u0000", "")
        if (directContent is ByteArray) {
            return decodeBytes(directContent, part).replace("\u0000", "")
        }

        val decodedStream = when (directContent) {
            is InputStream -> directContent
            else -> runCatching { part.inputStream }.getOrNull()
        }
        val decoded = decodedStream?.let { stream ->
            runCatching {
                stream.use { decodeBytes(readLimitedBytes(it, MAX_TEXT_PART_BYTES), part) }
            }.getOrDefault("")
        }.orEmpty().replace("\u0000", "")
        if (decoded.isNotBlank()) return decoded

        // Some IMAPBodyPart implementations return an empty decoded stream after a full MESSAGE
        // fetch but still expose the transfer-encoded raw stream. Decode that stream explicitly.
        val mimePart = part as? MimePart ?: return ""
        val mimeBodyPart = part as? MimeBodyPart ?: return ""
        return runCatching {
            val raw = mimeBodyPart.rawInputStream
            val transferEncoding = runCatching { mimePart.encoding }.getOrNull()
            val decodedRaw = if (transferEncoding.isNullOrBlank()) raw else MimeUtility.decode(raw, transferEncoding)
            decodedRaw.use { decodeBytes(readLimitedBytes(it, MAX_TEXT_PART_BYTES), part) }
        }.onFailure { error ->
            MailLog.w(
                MailLog.IMAP,
                "mime text read failed type=${safeContentType(part).substringBefore(';')} " +
                    "cause=${MailLog.causeSummary(error)}",
            )
        }.getOrDefault("").replace("\u0000", "")
    }

    private fun decodeBytes(bytes: ByteArray, part: Part): String {
        if (bytes.isEmpty()) return ""

        val declared = declaredCharset(part)
        val candidates = buildList {
            declared?.let(::add)
            add(StandardCharsets.UTF_8)
            runCatching { Charset.forName("GB18030") }.getOrNull()?.let(::add)
            runCatching { Charset.forName("Big5") }.getOrNull()?.let(::add)
            runCatching { Charset.forName("windows-1252") }.getOrNull()?.let(::add)
        }.distinctBy { it.name().lowercase(Locale.ROOT) }

        return candidates
            .map { charset -> charset to bytes.toString(charset) }
            .minByOrNull { (_, text) -> decodePenalty(text) }
            ?.second
            .orEmpty()
            .removePrefix("\uFEFF")
    }

    private fun declaredCharset(part: Part): Charset? = declaredCharset(safeContentType(part))

    private fun declaredCharset(contentType: String): Charset? {
        val raw = runCatching { ContentType(contentType).getParameter("charset") }.getOrNull()
            ?: CHARSET_PARAMETER.find(contentType)?.groupValues?.getOrNull(1)
        val javaName = runCatching { MimeUtility.javaCharset(raw ?: return null) }.getOrNull()
        return runCatching { Charset.forName(javaName ?: raw) }.getOrNull()
    }

    private fun decodePenalty(text: String): Int {
        val replacement = text.count { it == '\uFFFD' }
        val controls = text.count { char -> char.code in 0..31 && char !in "\r\n\t" }
        val mojibake = listOf("Ã", "Â", "â€", "ðŸ", "锟斤拷")
            .sumOf { marker -> text.windowed(marker.length).count { it == marker } }
        return replacement * 10_000 + controls * 100 + mojibake * 500
    }

    private fun readLimitedBytes(stream: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(min(limit, 64 * 1024))
        val buffer = ByteArray(32 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) error("MIME part exceeds ${limit / 1024} KiB")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun resolveInlineImages(
        html: String,
        inlineImages: Map<String, String>,
        baseUri: String?,
    ): String {
        val document = Jsoup.parse(html, baseUri.orEmpty())
        if (!baseUri.isNullOrBlank() && document.head().selectFirst("base[href]") == null) {
            document.head().prependElement("base").attr("href", baseUri)
        }

        document.select("[src], [background], [poster]").forEach { element ->
            listOf("src", "background", "poster").forEach attributeLoop@ { attribute ->
                if (!element.hasAttr(attribute)) return@attributeLoop
                inlineImages.lookup(element.attr(attribute))
                    ?.let { element.attr(attribute, it) }
            }
        }

        document.select("[srcset]").forEach { element ->
            val resolved = element.attr("srcset").split(',').joinToString(",") { entry ->
                val trimmed = entry.trim()
                val url = trimmed.substringBefore(' ')
                val descriptor = trimmed.substringAfter(' ', missingDelimiterValue = "")
                val replacement = inlineImages.lookup(url) ?: url
                if (descriptor.isBlank()) replacement else "$replacement $descriptor"
            }
            element.attr("srcset", resolved)
        }

        document.select("[style]").forEach { element ->
            var style = element.attr("style")
            inlineImages.forEach { (alias, dataUri) ->
                style = style.replace(
                    Regex("(?i)cid:${Regex.escape(alias.removePrefix("cid:"))}"),
                    dataUri,
                )
            }
            element.attr("style", style)
        }

        var resolved = document.outerHtml()
        inlineImages.forEach { (alias, dataUri) ->
            val cleanAlias = alias.removePrefix("cid:")
            resolved = resolved.replace(Regex("(?i)cid:${Regex.escape(cleanAlias)}"), dataUri)
        }
        return resolved
    }

    private fun Map<String, String>.lookup(rawValue: String): String? {
        val decoded = runCatching { URLDecoder.decode(rawValue, StandardCharsets.UTF_8.name()) }
            .getOrDefault(rawValue)
            .trim()
            .trim('"', '\'', '<', '>')
        val candidates = listOf(
            decoded,
            decoded.removePrefix("cid:"),
            decoded.substringAfterLast('/'),
            decoded.substringAfterLast(':'),
        ).map(::normalizeAlias)
        return candidates.firstNotNullOfOrNull { this[it] }
    }

    private fun loadReferencedInlineImages(
        html: String,
        candidates: List<InlineImageCandidate>,
    ): Map<String, String> {
        if (candidates.isEmpty()) return emptyMap()
        val searchableHtml = runCatching { URLDecoder.decode(html, StandardCharsets.UTF_8.name()) }
            .getOrDefault(html)
            .lowercase(Locale.ROOT)
        val resolved = linkedMapOf<String, String>()

        candidates.forEach { candidate ->
            val aliases = listOfNotNull(
                candidate.contentId,
                candidate.contentLocation,
                candidate.fileName,
            ).flatMap { raw ->
                val clean = normalizeAlias(raw)
                listOf(clean, clean.substringAfterLast('/'), clean.substringAfterLast(':'))
            }.filter(String::isNotBlank).distinct()

            val referenced = aliases.any { alias ->
                searchableHtml.contains("cid:$alias") ||
                    searchableHtml.contains(alias)
            }
            if (!referenced) return@forEach

            val bytes = runCatching {
                candidate.part.inputStream.use { readLimitedBytes(it, MAX_INLINE_IMAGE_BYTES) }
            }.onFailure { error ->
                MailLog.w(
                    MailLog.IMAP,
                    "inline image skipped name=${candidate.fileName.orEmpty()} " +
                        "cause=${MailLog.causeSummary(error)}",
                )
            }.getOrNull() ?: return@forEach

            val declaredType = runCatching { ContentType(candidate.part.contentType).baseType }.getOrNull()
            val guessedType = URLConnection.guessContentTypeFromName(
                candidate.fileName ?: candidate.contentLocation.orEmpty(),
            )
            val mimeType = declaredType
                ?.takeUnless { it.equals("application/octet-stream", ignoreCase = true) }
                ?: guessedType
                ?: "image/png"
            val dataUri = "data:$mimeType;base64,${Base64.getEncoder().encodeToString(bytes)}"
            aliases.forEach { alias -> resolved[alias] = dataUri }
        }

        return resolved
    }

    private fun normalizeAlias(value: String): String = value
        .trim()
        .trim('"', '\'', '<', '>')
        .lowercase(Locale.ROOT)
        .removePrefix("cid:")

    private fun decodeSenderName(address: InternetAddress?): String {
        val personal = decodeText(address?.personal)
        if (personal.isNotBlank()) return stripOuterQuotes(personal)
        return decodeAddress(address)
    }

    private fun decodeAddress(address: InternetAddress?): String {
        val decoded = decodeText(address?.address).trim()
        return EMAIL_PATTERN.find(decoded)?.value ?: decoded.trim('<', '>')
    }

    private fun decodeSubject(subject: String?): String =
        decodeText(subject).ifBlank { "(No subject)" }

    private fun decodeText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var value = raw.replace("\u0000", "").trim()
        repeat(3) {
            val wordsDecoded = ENCODED_WORD.replace(value) { match ->
                runCatching { MimeUtility.decodeWord(match.value) }.getOrDefault(match.value)
            }
            val wholeDecoded = runCatching { MimeUtility.decodeText(wordsDecoded) }
                .getOrDefault(wordsDecoded)
            if (wholeDecoded == value) return@repeat
            value = wholeDecoded
        }
        return stripOuterQuotes(value.trim())
    }

    private fun stripOuterQuotes(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"') {
            trimmed.substring(1, trimmed.length - 1).trim()
        } else trimmed
    }

    private fun Part.matches(mimeType: String): Boolean =
        runCatching { isMimeType(mimeType) }.getOrDefault(false) ||
            contentTypeMatches(safeContentType(this), mimeType)

    private fun contentTypeMatches(actual: String, expected: String): Boolean {
        val normalizedActual = actual.substringBefore(';').trim().lowercase(Locale.ROOT)
        val normalizedExpected = expected.substringBefore(';').trim().lowercase(Locale.ROOT)
        return if (normalizedExpected.endsWith("/*")) {
            normalizedActual.startsWith(normalizedExpected.removeSuffix("*"))
        } else {
            normalizedActual == normalizedExpected
        }
    }

    private fun partSize(part: Part): Long =
        runCatching { part.size.toLong() }.getOrDefault(-1L).takeIf { it >= 0L } ?: -1L

    private fun looksLikeTextualOctetStream(contentType: String): Boolean =
        contentType.substringBefore(';').trim().equals("application/octet-stream", ignoreCase = true)

    private fun looksLikeReadableBody(value: String): Boolean {
        val clean = value.replace("\u0000", "").trim()
        if (clean.isBlank()) return false
        val sample = clean.take(4096)
        val controlCharacters = sample.count { char -> char.code in 0..31 && char !in "\r\n\t" }
        return controlCharacters <= sample.length / 50
    }

    private fun defaultAttachmentName(contentType: String, index: Int): String {
        val extension = when (contentType.substringBefore(';').trim().lowercase(Locale.ROOT)) {
            "application/pdf" -> "pdf"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "text/plain" -> "txt"
            "text/html" -> "html"
            "message/rfc822" -> "eml"
            else -> "bin"
        }
        return "Attachment $index.$extension"
    }

    private fun header(part: Part, name: String): String? =
        runCatching { part.getHeader(name)?.firstOrNull() }.getOrNull()

    private fun safeContentType(part: Part): String =
        runCatching { part.contentType }.getOrDefault("application/octet-stream")

    private fun htmlScore(value: String): Long {
        val document = runCatching { Jsoup.parse(value) }.getOrNull() ?: return value.length.toLong()
        val visibleText = document.text().length
        val visualElements = document.select("img,svg,table,video,button").size
        val styleElements = document.select("style,[style]").size
        return visibleText * 1_000L +
            visualElements * 5_000L +
            styleElements * 200L +
            min(value.length, 1_000_000)
    }

    private val ENCODED_WORD = Regex("""=\?[^?]+\?[bBqQ]\?[^?]*\?=""")
    private val CHARSET_PARAMETER = Regex("""(?i)charset\s*=\s*["']?([^;"'\s]+)""")
    private val EMAIL_PATTERN = Regex(
        """[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9.-]+\.[A-Z]{2,}""",
        RegexOption.IGNORE_CASE,
    )

    private data class HtmlCandidate(
        val value: String,
        val baseUri: String?,
    )

    private data class InlineImageCandidate(
        val part: Part,
        val contentId: String?,
        val contentLocation: String?,
        val fileName: String?,
    )

    private data class BodyCollector(
        val textCandidates: MutableList<String> = mutableListOf(),
        val htmlCandidates: MutableList<HtmlCandidate> = mutableListOf(),
        var hasAttachments: Boolean = false,
        val inlineCandidates: MutableList<InlineImageCandidate> = mutableListOf(),
        val attachments: MutableList<MailAttachmentInfo> = mutableListOf(),
    ) {
        fun addText(value: String) {
            val clean = value.trim()
            if (clean.isNotBlank() && clean !in textCandidates) textCandidates += clean
        }

        fun addHtml(value: String, baseUri: String?) {
            val clean = value.trim()
            if (clean.isNotBlank() && htmlCandidates.none { it.value == clean }) {
                htmlCandidates += HtmlCandidate(clean, baseUri)
            }
        }

        fun addAttachment(value: MailAttachmentInfo) {
            val normalized = value.copy(
                name = value.name.trim().ifBlank { "Attachment" },
                contentType = value.contentType.trim().ifBlank { "application/octet-stream" },
            )
            val duplicate = attachments.any { current ->
                current.name.equals(normalized.name, ignoreCase = true) &&
                    current.contentType.equals(normalized.contentType, ignoreCase = true) &&
                    current.sizeBytes == normalized.sizeBytes
            }
            if (!duplicate) attachments += normalized
        }

        fun bestText(): String = textCandidates.maxByOrNull { it.length }.orEmpty()

        fun bestHtml(): HtmlCandidate? = when (htmlCandidates.size) {
            0 -> null
            1 -> htmlCandidates.first()
            else -> htmlCandidates.maxByOrNull { MimeParser.htmlScore(it.value) }
        }
    }
}
