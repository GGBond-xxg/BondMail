package com.bond.mail.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import kotlinx.coroutines.flow.map
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, createdAt")
    suspend fun allNow(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE enabled = 1 ORDER BY sortOrder, createdAt")
    suspend fun enabledAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun byEmail(email: String): AccountEntity?

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Query("UPDATE accounts SET lastSyncAt = :time, lastError = :error WHERE id = :id")
    suspend fun updateSync(id: String, time: Long?, error: String?)

    @Query("UPDATE accounts SET displayName = :displayName WHERE id = :id")
    suspend fun updateDisplayName(id: String, displayName: String)

    @Query(
        "UPDATE accounts SET displayName = :displayName, displayEmail = :displayEmail, " +
            "avatarText = :avatarText WHERE id = :id",
    )
    suspend fun updateIdentity(
        id: String,
        displayName: String,
        displayEmail: String?,
        avatarText: String?,
    )

    @Query("UPDATE accounts SET displayName = substr(displayName, 1, :maxLength) WHERE length(displayName) > :maxLength")
    suspend fun truncateDisplayNames(maxLength: Int)

    @Query("UPDATE accounts SET oauthAccountId = :oauthAccountId, email = :email, lastError = NULL WHERE id = :id")
    suspend fun updateOAuthIdentity(id: String, oauthAccountId: String, email: String)

    @Query("SELECT MAX(sortOrder) FROM accounts")
    suspend fun maxSortOrder(): Int?

    @Query("UPDATE accounts SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface FolderDao {
    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("SELECT * FROM folders WHERE accountId = :accountId")
    suspend fun forAccount(accountId: String): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE accountId = :accountId AND canonicalType = :canonicalType LIMIT 1")
    suspend fun byCanonicalType(accountId: String, canonicalType: String): FolderEntity?

    @Query("DELETE FROM folders WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)
}

// Large fields must never cross CursorWindow as a whole row (including cached legacy mail).
private const val MESSAGE_METADATA_COLUMNS = "id, accountId, folderType, remoteFolder, remoteUid, internetMessageId, inReplyTo, referencesHeader, senderName, senderAddress, recipients, cc, subject, preview, receivedAt, unread, starred, hasAttachments, bodyLoaded, bodyParserVersion, htmlContentHash, remoteImageAllowed, deliveryState, '' AS bodyText, NULL AS bodyHtml, '[]' AS attachmentsJson"
private const val MESSAGE_CHUNK_BYTES = 64 * 1024

@Dao
interface MessageDao {
    @androidx.room.RawQuery(observedEntities = [MessageEntity::class])
    fun searchAdvanced(query: androidx.sqlite.db.SupportSQLiteQuery): Flow<List<MessageListRow>>

    @Query("SELECT id, accountId, subject, senderAddress, receivedAt, attachmentsJson, internetMessageId, inReplyTo, referencesHeader FROM messages WHERE (:accountId IS NULL OR accountId = :accountId) ORDER BY receivedAt DESC LIMIT :limit")
    fun observeIndex(accountId: String?, limit: Int): Flow<List<MailIndexRow>>

    @Query("SELECT COALESCE(SUM(LENGTH(CAST(bodyText AS BLOB)) + LENGTH(CAST(COALESCE(bodyHtml, '') AS BLOB))), 0) FROM messages WHERE deliveryState = 'REMOTE' AND folderType != 'DRAFTS'")
    suspend fun bodyBytes(): Long

    @Query("UPDATE messages SET bodyText = '', bodyHtml = NULL, bodyLoaded = 0, bodyParserVersion = 0, htmlContentHash = NULL WHERE deliveryState = 'REMOTE' AND folderType != 'DRAFTS'")
    suspend fun clearBodyCache()

    @Query("""
        SELECT id, accountId, folderType, senderName, senderAddress, recipients, subject, preview, receivedAt, unread, starred, deliveryState, NULL AS localTaskId
        FROM messages
        WHERE (:accountId IS NULL OR accountId = :accountId) AND folderType = :folderType
        ORDER BY receivedAt DESC
    """)
    fun observeFolderRows(accountId: String?, folderType: String): Flow<List<MessageListRow>>

    @Query("""
        SELECT id, accountId, folderType, senderName, senderAddress, recipients, subject, preview, receivedAt, unread, starred, deliveryState, NULL AS localTaskId
        FROM messages
        WHERE (:accountId IS NULL OR accountId = :accountId) AND folderType = :folderType
        ORDER BY receivedAt DESC
    """)
    suspend fun folderRowSnapshot(accountId: String?, folderType: String): List<MessageListRow>

    @Query("SELECT id FROM messages WHERE id = :id LIMIT 1")
    fun observeMessageId(id: String): Flow<String?>

    fun observeById(id: String): Flow<MessageEntity?> = observeMessageId(id).map { key -> key?.let { byId(it) } }

    @Query("""
        SELECT """ + MESSAGE_METADATA_COLUMNS + """ FROM messages
        WHERE accountId = :accountId
          AND folderType = :folderType
          AND deliveryState = 'REMOTE'
          AND LOWER(TRIM(senderAddress)) = LOWER(TRIM(:senderAddress))
        ORDER BY receivedAt DESC
    """)
    suspend fun senderFolderMetadataSnapshot(
        accountId: String,
        folderType: String,
        senderAddress: String,
    ): List<MessageEntity>

    @Transaction
    suspend fun senderFolderSnapshot(accountId: String, folderType: String, senderAddress: String): List<MessageEntity> =
        senderFolderMetadataSnapshot(accountId, folderType, senderAddress).map { hydrate(it) }

    @Query("""
        SELECT id, accountId, folderType, senderName, senderAddress, recipients, subject, preview, receivedAt, unread, starred, deliveryState, NULL AS localTaskId
        FROM messages
        WHERE (:accountId IS NULL OR accountId = :accountId) AND starred = 1
        ORDER BY receivedAt DESC
    """)
    fun observeStarredRows(accountId: String?): Flow<List<MessageListRow>>

    @Query("""
        SELECT id, accountId, folderType, senderName, senderAddress, recipients, subject, preview, receivedAt, unread, starred, deliveryState, NULL AS localTaskId
        FROM messages
        WHERE (:accountId IS NULL OR accountId = :accountId) AND starred = 1
        ORDER BY receivedAt DESC
    """)
    suspend fun starredRowSnapshot(accountId: String?): List<MessageListRow>

    @Query("""
        SELECT id, accountId, folderType, senderName, senderAddress, recipients, subject, preview, receivedAt, unread, starred, deliveryState, NULL AS localTaskId
        FROM messages
        WHERE (:accountId IS NULL OR accountId = :accountId)
          AND folderType = 'INBOX'
          AND unread = 1
        ORDER BY receivedAt DESC
    """)
    fun observeUnreadRows(accountId: String?): Flow<List<MessageListRow>>

    @Query("""
        SELECT id, accountId, folderType, senderName, senderAddress, recipients, subject, preview, receivedAt, unread, starred, deliveryState, NULL AS localTaskId
        FROM messages
        WHERE (:accountId IS NULL OR accountId = :accountId)
          AND folderType = 'INBOX'
          AND unread = 1
        ORDER BY receivedAt DESC
    """)
    suspend fun unreadRowSnapshot(accountId: String?): List<MessageListRow>

    @Query("SELECT " + MESSAGE_METADATA_COLUMNS + " FROM messages WHERE id = :id LIMIT 1")
    suspend fun metadataById(id: String): MessageEntity?

    @Transaction
    suspend fun byId(id: String): MessageEntity? = metadataById(id)?.let { hydrate(it) }

    @Query("SELECT substr(CAST(CASE :field WHEN 'text' THEN bodyText WHEN 'html' THEN bodyHtml WHEN 'attachments' THEN attachmentsJson END AS BLOB), :offset, :size) FROM messages WHERE id = :id")
    suspend fun largeFieldChunk(id: String, field: String, offset: Long, size: Int): ByteArray?

    private suspend fun readLargeField(id: String, field: String): String? {
        var offset = 1L // SQLite substr uses a one-based byte offset for BLOB values.
        val bytes = ByteArrayOutputStream()
        while (true) {
            val chunk = largeFieldChunk(id, field, offset, MESSAGE_CHUNK_BYTES) ?: return null
            bytes.write(chunk)
            if (chunk.size < MESSAGE_CHUNK_BYTES) break
            offset += chunk.size
        }
        // Decode once, so UTF-8 code points split across chunks are preserved.
        return bytes.toString(Charsets.UTF_8.name())
    }

    private suspend fun hydrate(row: MessageEntity): MessageEntity = row.copy(
        bodyText = readLargeField(row.id, "text").orEmpty(),
        bodyHtml = readLargeField(row.id, "html"),
        attachmentsJson = readLargeField(row.id, "attachments") ?: "[]",
    )

    @Query("SELECT " + MESSAGE_METADATA_COLUMNS + " FROM messages WHERE accountId = :accountId AND folderType = :folderType ORDER BY remoteUid DESC")
    suspend fun folderMetadataSnapshot(accountId: String, folderType: String): List<MessageEntity>

    @Transaction
    suspend fun folderEntitySnapshot(accountId: String, folderType: String): List<MessageEntity> =
        folderMetadataSnapshot(accountId, folderType).map { hydrate(it) }

    @Query("SELECT " + MESSAGE_METADATA_COLUMNS + " FROM messages WHERE id IN (:ids)")
    suspend fun metadataByIds(ids: List<String>): List<MessageEntity>

    @Transaction
    suspend fun byIds(ids: List<String>): List<MessageEntity> = metadataByIds(ids).map { hydrate(it) }

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Query("SELECT id FROM messages WHERE id IN (:ids)")
    suspend fun existingIds(ids: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM messages WHERE accountId = :accountId AND folderType = :folderType")
    suspend fun countForFolder(accountId: String, folderType: String): Int

    @Query("SELECT MAX(remoteUid) FROM messages WHERE accountId = :accountId AND folderType = :folderType")
    suspend fun maxRemoteUid(accountId: String, folderType: String): Long?

    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("UPDATE messages SET unread = :unread WHERE id = :id")
    suspend fun setUnread(id: String, unread: Boolean)

    @Query("UPDATE messages SET unread = :unread WHERE id IN (:ids)")
    suspend fun setUnread(ids: List<String>, unread: Boolean)

    @Query("UPDATE messages SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean)

    @Query("UPDATE messages SET remoteImageAllowed = :allowed WHERE id = :id")
    suspend fun setRemoteImageAllowed(id: String, allowed: Boolean)

    @Query("UPDATE messages SET deliveryState = :state WHERE id = :id")
    suspend fun setDeliveryState(id: String, state: String)

    @Query("DELETE FROM messages WHERE id = :id AND deliveryState != 'REMOTE'")
    suspend fun deleteLocalPlaceholder(id: String)

    @Query("""
        DELETE FROM messages
        WHERE accountId = :accountId
          AND folderType = :folderType
          AND deliveryState != 'REMOTE'
          AND LOWER(REPLACE(REPLACE(REPLACE(TRIM(internetMessageId), '<', ''), '>', ''), ' ', '')) = :normalizedInternetMessageId
    """)
    suspend fun deleteLocalPlaceholderByInternetMessageId(
        accountId: String,
        folderType: String,
        normalizedInternetMessageId: String,
    )

    @Query("""
        SELECT """ + MESSAGE_METADATA_COLUMNS + """ FROM messages
        WHERE accountId = :accountId
          AND folderType = :folderType
          AND deliveryState = 'REMOTE'
          AND LOWER(REPLACE(REPLACE(REPLACE(TRIM(internetMessageId), '<', ''), '>', ''), ' ', '')) = :normalizedInternetMessageId
        ORDER BY remoteUid DESC
        LIMIT 1
    """)
    suspend fun remoteMetadataByInternetMessageId(
        accountId: String,
        folderType: String,
        normalizedInternetMessageId: String,
    ): MessageEntity?

    @Transaction
    suspend fun remoteByInternetMessageId(accountId: String, folderType: String, normalizedInternetMessageId: String): MessageEntity? =
        remoteMetadataByInternetMessageId(accountId, folderType, normalizedInternetMessageId)?.let { hydrate(it) }

    @Query("""
        UPDATE messages
        SET unread = :unread, starred = :starred
        WHERE accountId = :accountId AND folderType = :folderType AND remoteUid = :remoteUid
    """)
    suspend fun updateFlags(accountId: String, folderType: String, remoteUid: Long, unread: Boolean, starred: Boolean)

    @Query("DELETE FROM messages WHERE accountId = :accountId AND folderType = :folderType")
    suspend fun deleteForFolder(accountId: String, folderType: String)

    @Query("DELETE FROM messages WHERE accountId = :accountId AND folderType = :folderType AND deliveryState = 'REMOTE'")
    suspend fun deleteRemoteForFolder(accountId: String, folderType: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM messages WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)

    @Query("""
        SELECT senderName, senderAddress, MAX(receivedAt) AS lastSeenAt, COUNT(*) AS messageCount
        FROM messages
        WHERE senderAddress != ''
        GROUP BY LOWER(senderAddress)
        ORDER BY lastSeenAt DESC
    """)
    fun observeContacts(): Flow<List<ContactRow>>

    @Query("""
        SELECT senderName, senderAddress, MAX(receivedAt) AS lastSeenAt, COUNT(*) AS messageCount
        FROM messages
        WHERE senderAddress != ''
        GROUP BY LOWER(senderAddress)
        ORDER BY lastSeenAt DESC
    """)
    suspend fun contactsSnapshot(): List<ContactRow>

    @Query("UPDATE messages SET senderName = :name WHERE LOWER(senderAddress) = LOWER(:email)")
    suspend fun applyContactName(email: String, name: String)

    @Query("""
        SELECT id, accountId, folderType, senderName, senderAddress, recipients, subject, preview, receivedAt, unread, starred, deliveryState, NULL AS localTaskId
        FROM messages
        WHERE (:accountId IS NULL OR accountId = :accountId)
          AND (senderName LIKE '%' || :query || '%' OR senderAddress LIKE '%' || :query || '%' OR subject LIKE '%' || :query || '%' OR bodyText LIKE '%' || :query || '%')
        ORDER BY receivedAt DESC
    """)
    fun searchRows(accountId: String?, query: String): Flow<List<MessageListRow>>

    @Query("""
        SELECT id FROM messages
        WHERE (:accountId IS NULL OR accountId = :accountId)
          AND folderType = 'INBOX'
          AND (bodyLoaded = 0 OR bodyParserVersion < :parserVersion OR (bodyHtml IS NULL AND bodyText = ''))
        ORDER BY receivedAt DESC
        LIMIT :limit
    """)
    suspend fun bodyPrefetchCandidates(
        accountId: String?,
        parserVersion: Int,
        limit: Int,
    ): List<String>
}

@Dao
interface SavedContactDao {
    @Query("SELECT * FROM saved_contacts ORDER BY updatedAt DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<SavedContactEntity>>

    @Query("SELECT * FROM saved_contacts ORDER BY updatedAt DESC, name COLLATE NOCASE")
    suspend fun allNow(): List<SavedContactEntity>

    @Query("SELECT * FROM saved_contacts WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun byEmail(email: String): SavedContactEntity?

    @Query("SELECT * FROM saved_contacts WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): SavedContactEntity?

    @Upsert
    suspend fun upsert(contact: SavedContactEntity)

    @Query("DELETE FROM saved_contacts WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface OutboxDao {
    @Query("UPDATE outbox SET state = 'DRAFT', sendAfter = 0, updatedAt = :now WHERE id = :id AND state = 'QUEUED' AND sendAfter > :now")
    suspend fun undoQueued(id: String, now: Long): Int

    @Query("UPDATE outbox SET state = 'SENDING', updatedAt = :now WHERE id = :id AND state IN ('QUEUED','FAILED') AND sendAfter <= :now")
    suspend fun claimSend(id: String, now: Long): Int

    @Query("SELECT * FROM outbox WHERE state IN ('QUEUED','SENDING','FAILED','UNKNOWN') ORDER BY createdAt DESC")
    fun observeSending(): Flow<List<OutboxEntity>>
    @Upsert
    suspend fun upsert(task: OutboxEntity)

    @Query("SELECT * FROM outbox WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE state IN ('QUEUED','FAILED') ORDER BY createdAt LIMIT 20")
    suspend fun pending(): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE state = 'DRAFT' AND (:accountId IS NULL OR accountId = :accountId) ORDER BY updatedAt DESC")
    fun observeDrafts(accountId: String?): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox WHERE state = 'DRAFT' AND (:accountId IS NULL OR accountId = :accountId) ORDER BY updatedAt DESC")
    suspend fun draftSnapshot(accountId: String?): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM outbox WHERE accountId = :accountId AND internetMessageId = :internetMessageId AND state = 'DRAFT'")
    suspend fun deleteDraftByInternetMessageId(accountId: String, internetMessageId: String)

    @Query("""
        DELETE FROM outbox
        WHERE accountId = :accountId
          AND state != 'DRAFT'
          AND LOWER(REPLACE(REPLACE(REPLACE(TRIM(internetMessageId), '<', ''), '>', ''), ' ', '')) = :normalizedInternetMessageId
    """)
    suspend fun deleteSentByInternetMessageId(
        accountId: String,
        normalizedInternetMessageId: String,
    )

    @Query("UPDATE outbox SET remoteFolder = :remoteFolder, remoteUid = :remoteUid, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateRemoteDraft(id: String, remoteFolder: String, remoteUid: Long, updatedAt: Long)

    @Query("UPDATE outbox SET state = :state, lastError = :error, retryCount = retryCount + :retryDelta, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateState(id: String, state: String, error: String?, retryDelta: Int, updatedAt: Long)

    @Query("DELETE FROM outbox WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)
}

@Dao
interface NotificationStateDao {
    @Query("SELECT EXISTS(SELECT 1 FROM notification_state WHERE accountId = :accountId AND folderType = :folderType AND remoteUid = :uid)")
    suspend fun wasNotified(accountId: String, folderType: String, uid: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun mark(entity: NotificationStateEntity)

    @Query("DELETE FROM notification_state WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)
}
