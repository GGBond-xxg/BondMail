package com.bond.mail.data.mail

import androidx.sqlite.db.SimpleSQLiteQuery
import java.time.LocalDate
import java.time.ZoneId

/** Parameterized substring search preserves Chinese matching without relying on FTS tokenization. */
internal fun mailSearchQuery(account: String?, input: String, limit: Int = 100): SimpleSQLiteQuery {
    val predicates = mutableListOf<String>()
    val args = mutableListOf<Any>()
    if (account != null) { predicates += "accountId = ?"; args += account }
    fun like(raw: String) = "%" + raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    Regex("\"[^\"]*\"|\\S+").findAll(input).map { it.value.removeSurrounding("\"") }.forEach { word ->
        val value = word.substringAfter(':', "")
        val date = if (word.startsWith("after:") || word.startsWith("before:")) runCatching {
            LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull() else null
        when {
            word == "has:attachment" -> predicates += "hasAttachments = 1"
            word.startsWith("from:") && value.isNotBlank() -> {
                predicates += "(senderAddress LIKE ? ESCAPE '\\' OR senderName LIKE ? ESCAPE '\\')"
                args += like(value); args += like(value)
            }
            date != null -> { predicates += "receivedAt ${if (word.startsWith("after:")) ">=" else "<"} ?"; args += date }
            else -> {
                predicates += "(senderName LIKE ? ESCAPE '\\' OR senderAddress LIKE ? ESCAPE '\\' OR subject LIKE ? ESCAPE '\\' OR bodyText LIKE ? ESCAPE '\\')"
                repeat(4) { args += like(word) }
            }
        }
    }
    args += limit.coerceIn(1, 10_000)
    return SimpleSQLiteQuery("SELECT id, accountId, folderType, senderName, senderAddress, recipients, subject, preview, receivedAt, unread, starred, deliveryState, NULL AS localTaskId FROM messages WHERE " +
        predicates.joinToString(" AND ").ifBlank { "1" } + " ORDER BY receivedAt DESC, id DESC LIMIT ?", args.toTypedArray())
}

internal fun messageIds(header: String?): Set<String> = Regex("<[^<>\\s]+>").findAll(header.orEmpty()).map { it.value }.toSet()

/** Connected components by References/In-Reply-To, never by subject. Account boundaries are strict. */
internal fun conversationGroups(rows: List<com.bond.mail.data.db.MailIndexRow>): List<List<com.bond.mail.data.db.MailIndexRow>> {
    val parent = mutableMapOf<String, String>()
    fun root(key: String): String { var r = key; while (parent[r] != null && parent[r] != r) r = parent.getValue(r); return r }
    rows.forEach { row ->
        val ids = messageIds(row.internetMessageId) + messageIds(row.inReplyTo) + messageIds(row.referencesHeader)
        val keys = ids.map { "${row.accountId}:$it" } + "${row.accountId}:local:${row.id}"
        val first = root(keys.first())
        keys.forEach { parent[root(it)] = first }
    }
    return rows.groupBy { root("${it.accountId}:local:${it.id}") }.values.map { it.sortedBy { row -> row.receivedAt } }
        .sortedByDescending { it.last().receivedAt }
}
