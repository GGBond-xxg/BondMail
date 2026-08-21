package com.bond.mail.data.mail

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** Keeps stable links between a MIME attachment and the SAF document chosen by the user. */
internal class AttachmentDownloadStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun downloadedUri(messageId: String, index: Int, info: MailAttachmentInfo): Uri? {
        val raw = preferences.getString(key(messageId, index, info), null) ?: return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        val readable = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
        if (!readable) preferences.edit().remove(key(messageId, index, info)).apply()
        return uri.takeIf { readable }
    }

    suspend fun save(
        treeUri: Uri,
        messageId: String,
        index: Int,
        info: MailAttachmentInfo,
        identityInfo: MailAttachmentInfo = info,
        bytes: ByteArray,
    ): Uri = withContext(Dispatchers.IO) {
        val directory = DocumentFile.fromTreeUri(context, treeUri)
            ?.takeIf { it.exists() && it.isDirectory && it.canWrite() }
            ?: error("The selected download folder is unavailable")
        val baseName = safeFileName(info.name, index)
        val displayName = uniqueName(directory, baseName)
        val target = directory.createFile(
            info.contentType.substringBefore(';').ifBlank { "application/octet-stream" },
            displayName,
        ) ?: error("Unable to create attachment file")
        try {
            context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: error("Unable to write attachment file")
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        }
        preferences.edit().putString(key(messageId, index, identityInfo), target.uri.toString()).apply()
        target.uri
    }

    private fun uniqueName(directory: DocumentFile, requested: String): String {
        if (directory.findFile(requested) == null) return requested
        val dot = requested.lastIndexOf('.').takeIf { it > 0 } ?: requested.length
        val stem = requested.substring(0, dot)
        val extension = requested.substring(dot)
        for (suffix in 1..999) {
            val candidate = "$stem ($suffix)$extension"
            if (directory.findFile(candidate) == null) return candidate
        }
        return "$stem-${System.currentTimeMillis()}$extension"
    }

    private fun safeFileName(raw: String, index: Int): String = raw
        .replace(Regex("[^\\p{L}\\p{N}._ ()\\[\\]-]+"), "_")
        .trim(' ', '.')
        .take(180)
        .ifBlank { "attachment-${index + 1}" }

    private fun key(messageId: String, index: Int, info: MailAttachmentInfo): String {
        val input = "$messageId|$index|${info.name}|${info.contentType}|${info.sizeBytes}"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        private const val PREFERENCES_NAME = "bond_mail_downloaded_attachments"
    }
}
