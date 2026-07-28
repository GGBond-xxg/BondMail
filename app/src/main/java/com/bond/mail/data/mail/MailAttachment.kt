package com.bond.mail.data.mail

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln
import kotlin.math.pow

/** Lightweight attachment metadata persisted with a message body. */
internal data class MailAttachmentInfo(
    val name: String,
    val contentType: String = "application/octet-stream",
    val sizeBytes: Long = -1L,
)

internal object MailAttachmentCodec {
    fun encode(values: List<MailAttachmentInfo>): String {
        val array = JSONArray()
        values.distinctBy { attachmentKey(it) }.forEach { value ->
            array.put(
                JSONObject()
                    .put("name", value.name.ifBlank { "Attachment" })
                    .put("type", value.contentType.ifBlank { "application/octet-stream" })
                    .put("size", value.sizeBytes),
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<MailAttachmentInfo> = runCatching {
        val array = JSONArray(raw?.takeIf(String::isNotBlank) ?: "[]")
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name").trim().ifBlank { "Attachment" }
                val type = item.optString("type").trim().ifBlank { "application/octet-stream" }
                add(
                    MailAttachmentInfo(
                        name = name,
                        contentType = type,
                        sizeBytes = item.optLong("size", -1L),
                    ),
                )
            }
        }.distinctBy(::attachmentKey)
    }.getOrDefault(emptyList())

    fun formatSize(bytes: Long): String {
        if (bytes < 0L) return ""
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        val exponent = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
        val value = bytes / 1024.0.pow(exponent.toDouble())
        return if (value >= 10.0 || value % 1.0 < 0.05) {
            "%.0f %s".format(value, units[exponent - 1])
        } else {
            "%.1f %s".format(value, units[exponent - 1])
        }
    }

    private fun attachmentKey(value: MailAttachmentInfo): String = buildString {
        append(value.name.trim().lowercase())
        append('|').append(value.contentType.trim().lowercase())
        append('|').append(value.sizeBytes)
    }
}
