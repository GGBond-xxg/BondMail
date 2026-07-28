package com.bond.mail.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val ACCOUNT_DISPLAY_NAME_MAX_LENGTH = 12

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val email: String,
    val displayName: String,
    val enabled: Boolean = true,
    val authType: String,
    val oauthAccountId: String? = null,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    val createdAt: Long,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
)

@Entity(
    tableName = "folders",
    indices = [Index(value = ["accountId", "remoteName"], unique = true)]
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val remoteName: String,
    val canonicalType: String,
    val uidValidity: Long = 0,
    val uidNext: Long = 0,
    val messageCount: Int = 0,
    val unreadCount: Int = 0,
    val lastSyncAt: Long? = null,
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["accountId", "folderType", "remoteUid"], unique = true),
        Index(value = ["receivedAt"]),
        Index(value = ["senderAddress"]),
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val folderType: String,
    val remoteFolder: String,
    val remoteUid: Long,
    val internetMessageId: String? = null,
    val senderName: String,
    val senderAddress: String,
    val recipients: String = "",
    val cc: String = "",
    val subject: String,
    val preview: String,
    val bodyText: String,
    val bodyHtml: String?,
    @ColumnInfo(defaultValue = "1") val bodyLoaded: Boolean = true,
    @ColumnInfo(defaultValue = "0") val bodyParserVersion: Int = 0,
    val receivedAt: Long,
    val unread: Boolean,
    val starred: Boolean,
    val hasAttachments: Boolean,
    @ColumnInfo(defaultValue = "'[]'") val attachmentsJson: String = "[]",
    val htmlContentHash: String? = null,
    val remoteImageAllowed: Boolean = false,
    @ColumnInfo(defaultValue = "'REMOTE'") val deliveryState: String = "REMOTE",
)

/**
 * Lightweight projection used by the inbox/search lists. Keeping large HTML/text bodies out of
 * every LazyColumn row avoids copying and comparing megabytes of data while the user scrolls.
 */
data class MessageListRow(
    val id: String,
    val accountId: String,
    val folderType: String,
    val senderName: String,
    val senderAddress: String,
    val recipients: String,
    val subject: String,
    val preview: String,
    val receivedAt: Long,
    val unread: Boolean,
    val starred: Boolean,
    val deliveryState: String,
    val localTaskId: String? = null,
) {
    fun toInitialMessage(): MessageEntity = MessageEntity(
        id = id,
        accountId = accountId,
        folderType = folderType,
        remoteFolder = folderType,
        remoteUid = 0L,
        senderName = senderName,
        senderAddress = senderAddress,
        recipients = recipients,
        subject = subject,
        preview = preview,
        bodyText = "",
        bodyHtml = null,
        bodyLoaded = false,
        receivedAt = receivedAt,
        unread = unread,
        starred = starred,
        hasAttachments = false,
        deliveryState = deliveryState,
    )
}

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val recipients: String,
    val cc: String = "",
    val bcc: String = "",
    val subject: String,
    val bodyText: String,
    @ColumnInfo(defaultValue = "'[]'") val attachmentsJson: String = "[]",
    @ColumnInfo(defaultValue = "''") val internetMessageId: String = "",
    val state: String = "QUEUED",
    val remoteFolder: String? = null,
    val remoteUid: Long? = null,
    val sourceMessageId: String? = null,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "notification_state",
    primaryKeys = ["accountId", "folderType", "remoteUid"]
)
data class NotificationStateEntity(
    val accountId: String,
    val folderType: String,
    val remoteUid: Long,
    val notifiedAt: Long,
)

data class ContactRow(
    val senderName: String,
    val senderAddress: String,
    val lastSeenAt: Long,
    val messageCount: Int,
)

@Entity(
    tableName = "saved_contacts",
    indices = [Index(value = ["email"], unique = true)],
)
data class SavedContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val createdAt: Long,
    val updatedAt: Long,
)
