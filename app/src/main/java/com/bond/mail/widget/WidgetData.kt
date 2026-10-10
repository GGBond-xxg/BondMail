package com.bond.mail.widget

import androidx.room.withTransaction
import com.bond.mail.data.db.MailDatabase

data class WidgetMail(val id: String, val sender: String, val subject: String, val preview: String, val time: Long?, val unread: Boolean, val senderAddress: String = "")
data class WidgetSnapshot(
    val accountName: String = "", val email: String = "", val valid: Boolean = false,
    val unread: Int = 0, val rows: List<WidgetMail> = emptyList(), val syncAt: Long? = null,
    val syncError: Boolean = false, val readError: Boolean = false,
)

/** Sanitize before a RemoteViews payload (including accessibility labels) is constructed. */
fun widgetMail(row: com.bond.mail.data.db.MessageListRow, config: WidgetConfig): WidgetMail = WidgetMail(
    id = row.id,
    sender = if (config.sender) row.senderName.ifBlank { row.senderAddress }.take(160) else "",
    subject = if (config.subject && config.privacy != WidgetPrivacy.HIDE_CONTENT) row.subject.take(240) else "",
    preview = if (config.preview && config.privacy == WidgetPrivacy.NORMAL) row.preview.take(240) else "",
    time = row.receivedAt.takeIf { config.time },
    unread = row.unread,
    senderAddress = if (config.sender) row.senderAddress.take(320) else "",
)

suspend fun widgetSnapshot(database: MailDatabase, config: WidgetConfig): WidgetSnapshot = database.withTransaction {
    val account = database.accountDao().byId(config.accountId) ?: return@withTransaction WidgetSnapshot()
    if (!account.enabled) return@withTransaction WidgetSnapshot()
    WidgetSnapshot(
        account.displayName.ifBlank { account.email }, account.displayEmail ?: account.email, true,
        database.messageDao().widgetUnread(config.accountId),
        database.messageDao().widgetRows(config.accountId).map { widgetMail(it, config) },
        account.lastSyncAt, !account.lastError.isNullOrBlank(),
    )
}
