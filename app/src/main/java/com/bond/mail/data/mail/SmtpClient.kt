package com.bond.mail.data.mail

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.bond.mail.data.db.AccountEntity
import com.bond.mail.data.db.OutboxEntity
import com.bond.mail.data.model.AuthType
import com.bond.mail.data.model.MailProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.DataSource
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.Part
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeUtility

data class PreparedOutgoingMessage(
    val internetMessageId: String,
    val raw: ByteArray,
)

class SmtpClient(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver

    suspend fun test(provider: MailProvider, email: String, secret: String) = withContext(Dispatchers.IO) {
        var lastFailure: Throwable? = null
        val loginEmail = if (provider.netEaseClientId) email.trim().lowercase() else email.trim()
        MailLog.d(MailLog.SMTP, "test start provider=${provider.id} host=${provider.smtpHost}:${provider.smtpPort} account=${MailLog.accountHint(loginEmail)}")
        repeat(3) { attempt ->
            val transport = createSession(provider, loginEmail, secret, compatibilityMode = attempt > 0).getTransport("smtp")
            val startedAt = System.currentTimeMillis()
            try {
                transport.connect(provider.smtpHost, provider.smtpPort, loginEmail, secret)
                transport.close()
                MailLog.d(MailLog.SMTP, "test success provider=${provider.id} attempt=${attempt + 1} elapsed=${System.currentTimeMillis() - startedAt}ms")
                return@withContext
            } catch (failure: Throwable) {
                MailLog.w(
                    MailLog.SMTP,
                    "test failed provider=${provider.id} attempt=${attempt + 1} cause=${MailLog.causeSummary(failure)}",
                    failure,
                )
                lastFailure = failure
                runCatching { transport.close() }
                if (attempt < 2) Thread.sleep(if (attempt == 0) 800L else 1600L)
            }
        }
        throw lastFailure ?: IllegalStateException("Unable to connect to SMTP server")
    }

    suspend fun prepare(
        account: AccountEntity,
        task: OutboxEntity,
    ): PreparedOutgoingMessage = withContext(Dispatchers.IO) {
        val message = createMessage(
            account = account,
            session = Session.getInstance(Properties()),
            task = task,
        )
        message.toPreparedOutgoing(task)
    }

    suspend fun send(
        account: AccountEntity,
        provider: MailProvider,
        secret: String,
        task: OutboxEntity,
    ): PreparedOutgoingMessage = withContext(Dispatchers.IO) {
        val loginEmail = if (provider.netEaseClientId) account.email.lowercase() else account.email
        val session = createSession(provider, loginEmail, secret, compatibilityMode = false)
        val message = createMessage(account, session, task)
        val transport = session.getTransport("smtp")
        try {
            // Connect explicitly so OAuth mailboxes always pass the short-lived access token to
            // the XOAUTH2 mechanism selected in this Session.
            transport.connect(provider.smtpHost, provider.smtpPort, loginEmail, secret)
            transport.sendMessage(message, message.allRecipients)
        } finally {
            runCatching { transport.close() }
        }
        message.toPreparedOutgoing(task)
    }

    internal suspend fun describeAttachments(rawUris: List<String>): List<MailAttachmentInfo> =
        withContext(Dispatchers.IO) {
            rawUris.distinct().mapIndexedNotNull { index, raw ->
                val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return@mapIndexedNotNull null
                queryAttachmentInfo(uri, index)
            }
        }

    private fun createMessage(
        account: AccountEntity,
        session: Session,
        task: OutboxEntity,
    ): MimeMessage {
        val attachmentUris = parseAttachmentUris(task.attachmentsJson)
        return MimeMessage(session).apply {
            setFrom(InternetAddress(account.email, account.displayName))
            if (task.recipients.isNotBlank()) {
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(task.recipients, false))
            }
            if (task.cc.isNotBlank()) setRecipients(Message.RecipientType.CC, InternetAddress.parse(task.cc, false))
            if (task.bcc.isNotBlank()) setRecipients(Message.RecipientType.BCC, InternetAddress.parse(task.bcc, false))
            subject = task.subject
            sentDate = Date(task.updatedAt.coerceAtLeast(task.createdAt))

            if (attachmentUris.isEmpty()) {
                setText(task.bodyText, Charsets.UTF_8.name())
            } else {
                val multipart = MimeMultipart("mixed")
                multipart.addBodyPart(
                    MimeBodyPart().apply {
                        setText(task.bodyText, Charsets.UTF_8.name())
                    },
                )
                attachmentUris.forEachIndexed { index, uri ->
                    val attachment = queryAttachmentInfo(uri, index)
                    val displayName = attachment.name
                    val mimeType = attachment.contentType
                    multipart.addBodyPart(
                        MimeBodyPart().apply {
                            dataHandler = DataHandler(
                                UriAttachmentDataSource(
                                    uri = uri,
                                    contentType = mimeType,
                                    displayName = displayName,
                                ),
                            )
                            fileName = MimeUtility.encodeText(displayName, Charsets.UTF_8.name(), null)
                            disposition = Part.ATTACHMENT
                        },
                    )
                }
                setContent(multipart)
            }
            saveChanges()
            val stableMessageId = task.internetMessageId.ifBlank { "<${task.id}@bondmail.local>" }
            setHeader("Message-ID", stableMessageId)
            if (task.state == "DRAFT") setFlag(javax.mail.Flags.Flag.DRAFT, true)
        }
    }

    private fun MimeMessage.toPreparedOutgoing(task: OutboxEntity): PreparedOutgoingMessage {
        val buffer = ByteArrayOutputStream()
        writeTo(buffer)
        return PreparedOutgoingMessage(
            internetMessageId = getHeader("Message-ID", null)
                ?.takeIf(String::isNotBlank)
                ?: task.internetMessageId.ifBlank { "<${task.id}@bondmail.local>" },
            raw = buffer.toByteArray(),
        )
    }

    private fun parseAttachmentUris(json: String): List<Uri> = runCatching {
        val array = JSONArray(json)
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                array.optString(index)
                    .takeIf(String::isNotBlank)
                    ?.let(Uri::parse)
                    ?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private fun queryAttachmentInfo(uri: Uri, index: Int): MailAttachmentInfo = runCatching {
        var displayName = "attachment-${index + 1}"
        var sizeBytes = -1L
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0) {
                displayName = cursor.getString(nameIndex).orEmpty().trim().ifBlank { displayName }
            }
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                sizeBytes = cursor.getLong(sizeIndex).coerceAtLeast(-1L)
            }
        }
        MailAttachmentInfo(
            name = displayName,
            contentType = contentResolver.getType(uri) ?: "application/octet-stream",
            sizeBytes = sizeBytes,
        )
    }.getOrElse {
        MailAttachmentInfo(
            name = uri.lastPathSegment?.substringAfterLast('/')?.trim()?.takeIf(String::isNotBlank)
                ?: "attachment-${index + 1}",
            contentType = contentResolver.getType(uri) ?: "application/octet-stream",
            sizeBytes = -1L,
        )
    }

    private inner class UriAttachmentDataSource(
        private val uri: Uri,
        private val contentType: String,
        private val displayName: String,
    ) : DataSource {
        override fun getInputStream(): InputStream =
            contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open attachment: $displayName")

        override fun getOutputStream(): OutputStream =
            throw UnsupportedOperationException("Attachments are read-only")

        override fun getContentType(): String = contentType

        override fun getName(): String = displayName
    }

    private fun createSession(provider: MailProvider, email: String, secret: String, compatibilityMode: Boolean): Session {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.host", provider.smtpHost)
            put("mail.smtp.port", provider.smtpPort.toString())
            put("mail.smtp.connectiontimeout", if (compatibilityMode) "18000" else "12000")
            put("mail.smtp.timeout", "30000")
            put("mail.smtp.writetimeout", "30000")
            put("mail.smtp.ssl.checkserveridentity", "true")
            if (compatibilityMode) put("mail.smtp.ssl.protocols", "TLSv1.2")
            if (provider.authType == AuthType.OAUTH2) {
                put("mail.smtp.auth.mechanisms", "XOAUTH2")
                put("mail.smtp.auth.login.disable", "true")
                put("mail.smtp.auth.plain.disable", "true")
            } else if (provider.netEaseClientId) {
                put("mail.smtp.auth.mechanisms", "LOGIN")
                put("mail.smtp.auth.plain.disable", "true")
                put("mail.smtp.auth.login.disable", "false")
            }
            if (provider.smtpSsl) put("mail.smtp.ssl.enable", "true")
            if (provider.smtpStartTls) {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
        }
        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(email, secret)
        })
    }
}
