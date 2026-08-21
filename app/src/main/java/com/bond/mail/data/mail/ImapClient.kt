package com.bond.mail.data.mail

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.bond.mail.data.db.AccountEntity
import com.bond.mail.data.db.FolderEntity
import com.bond.mail.data.db.MessageEntity
import com.bond.mail.data.model.AuthType
import com.bond.mail.data.model.MailAuthMechanism
import com.bond.mail.data.model.MailProvider
import com.bond.mail.data.model.MailSecurity
import com.bond.mail.data.model.mailLoginName
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.MimeMessage
import javax.mail.internet.InternetAddress
import javax.mail.search.FromStringTerm
import kotlin.math.max

internal data class RemoteFlagState(
    val remoteUid: Long,
    val unread: Boolean,
    val starred: Boolean,
)

internal data class ImapSyncResult(
    val folder: FolderEntity,
    val newHeaders: List<MessageEntity>,
    val recentFlags: List<RemoteFlagState>,
    val uidValidityChanged: Boolean,
)

internal data class RemoteAppendResult(
    val remoteFolder: String,
    val remoteUid: Long,
)

class ImapClient(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * JavaMail used to reconnect TLS for every header refresh, body open, and flag update. Keep one
     * authenticated IMAP store per mailbox for a short idle window. Repository account mutexes
     * serialize access, while the JavaMail folder connection pool can reuse the already negotiated
     * session. This is the largest latency difference users notice beside clients such as K-9.
     */
    private val storePool = ConcurrentHashMap<String, PooledStore>()

    suspend fun test(provider: MailProvider, email: String, secret: String) = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        MailLog.d(MailLog.IMAP, "test start provider=${provider.id} host=${provider.imapHost}:${provider.imapPort} account=${MailLog.accountHint(email)}")
        // Account setup is immediately followed by the first INBOX synchronization. Keep the
        // authenticated store in the same lane so that step reuses the TLS/login session instead of
        // opening a second connection just after validation succeeds.
        withStore(provider, email, secret, operation = "test", poolLane = "sync") { store -> check(store.isConnected) }
        MailLog.d(MailLog.IMAP, "test success provider=${provider.id} elapsed=${System.currentTimeMillis() - startedAt}ms")
    }

    /**
     * Drop every cached authenticated Store for one mailbox.
     *
     * This is required before validating a replacement app password. Reusing an already
     * authenticated IMAP connection would make an invalid new password appear to work until the
     * pooled socket is closed later.
     */
    suspend fun invalidate(provider: MailProvider, email: String) = withContext(Dispatchers.IO) {
        val normalizedEmail = if (provider.netEaseClientId) {
            email.trim().lowercase()
        } else {
            email.trim()
        }
        val keyPrefix = "${provider.id}|${normalizedEmail.lowercase()}|"
        storePool.entries.toList().forEach { (key, pooled) ->
            if (key.startsWith(keyPrefix) && storePool.remove(key, pooled)) {
                runCatching { pooled.store.close() }
            }
        }
    }

    /**
     * Fast INBOX synchronization.
     *
     * Initial sync uses the server's last sequence numbers because some providers do not return
     * a reliable UIDNEXT value immediately. Later syncs use UIDNEXT when available. Only envelope
     * fields and flags are fetched; the MIME body is downloaded when the message is opened.
     */
    internal suspend fun syncInbox(
        account: AccountEntity,
        provider: MailProvider,
        secret: String,
        knownFolder: FolderEntity?,
        localMessageCount: Int,
        localMaxUid: Long?,
        initialWindow: Int = 40,
        maxNewMessages: Int = 80,
    ): ImapSyncResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        withStore(provider, account.mailLoginName, secret, operation = "headers", poolLane = "sync") { store ->
            val folder = store.getFolder("INBOX") as IMAPFolder
            folder.open(Folder.READ_ONLY)
            try {
                val messageCount = folder.messageCount.coerceAtLeast(0)
                val uidValidity = runCatching { folder.uidValidity }.getOrDefault(0L).coerceAtLeast(0L)
                val reportedUidNext = runCatching { folder.uidNext }.getOrDefault(-1L)
                val validityChanged = knownFolder != null &&
                    knownFolder.uidValidity > 0L &&
                    uidValidity > 0L &&
                    knownFolder.uidValidity != uidValidity
                // Local rows are the source of truth. A previous interrupted sync may have
                // advanced the folder cursor without inserting the latest window. Repair an empty
                // or partial cache before trusting UIDNEXT for later incremental refreshes.
                val expectedLocalWindow = minOf(messageCount, initialWindow)
                val hasLatestWindow = localMessageCount >= expectedLocalWindow
                val canIncrementByUid = knownFolder != null &&
                    !validityChanged &&
                    hasLatestWindow &&
                    localMaxUid != null &&
                    localMaxUid > 0L

                val remoteMessagesUnsorted: List<Message> = when {
                    messageCount == 0 -> emptyList()
                    canIncrementByUid -> {
                        val firstMissingUid = localMaxUid!! + 1L
                        if (reportedUidNext > 0L) {
                            val endUid = reportedUidNext - 1L
                            if (endUid < firstMissingUid) {
                                emptyList()
                            } else {
                                val startUid = max(firstMissingUid, endUid - maxNewMessages + 1L)
                                folder.getMessagesByUID(startUid, endUid).filterNotNull()
                            }
                        } else {
                            // NetEase frequently returns no UIDNEXT. The old path therefore
                            // downloaded the newest 30-40 envelopes on every pull-to-refresh even
                            // when nothing changed. Resolve only the last sequence number's UID,
                            // then request a bounded UID window instead of enumerating every UID
                            // between the local cursor and the end of a large mailbox.
                            runCatching {
                                val lastRemoteUid = folder.getUID(folder.getMessage(messageCount))
                                if (lastRemoteUid < firstMissingUid) {
                                    emptyList()
                                } else {
                                    val startUid = max(
                                        firstMissingUid,
                                        lastRemoteUid - maxNewMessages + 1L,
                                    )
                                    folder.getMessagesByUID(startUid, lastRemoteUid).filterNotNull()
                                }
                            }.onFailure { error ->
                                MailLog.w(
                                    MailLog.IMAP,
                                    "UID range fallback provider=${provider.id} cause=${MailLog.causeSummary(error)}",
                                    error,
                                )
                            }.getOrElse {
                                val firstSequence = max(1, messageCount - initialWindow + 1)
                                folder.getMessages(firstSequence, messageCount).toList()
                            }
                        }
                    }
                    else -> {
                        val firstSequence = max(1, messageCount - initialWindow + 1)
                        folder.getMessages(firstSequence, messageCount).toList()
                    }
                }

                // Fetch UID together with envelope/flags before sorting. Calling getUID() on every
                // sequence message before this batched FETCH can make JavaMail issue one UID query
                // per row on NetEase, turning a 40-message first sync into tens of seconds. K-9
                // obtains the complete header window with one FETCH and only then orders the rows.
                val headerFetchStartedAt = System.currentTimeMillis()
                if (remoteMessagesUnsorted.isNotEmpty()) {
                    val profile = FetchProfile().apply {
                        add(FetchProfile.Item.ENVELOPE)
                        add(FetchProfile.Item.FLAGS)
                        add(UIDFolder.FetchProfileItem.UID)
                        add("Message-ID")
                    }
                    folder.fetch(remoteMessagesUnsorted.toTypedArray(), profile)
                }
                val headerFetchFinishedAt = System.currentTimeMillis()
                val remoteMessages = remoteMessagesUnsorted.sortedByDescending { message ->
                    folder.getUID(message)
                }
                MailLog.d(
                    MailLog.IMAP,
                    "header window provider=${provider.id} messages=${remoteMessages.size} " +
                        "initial=${!canIncrementByUid} fetch=${headerFetchFinishedAt - headerFetchStartedAt}ms",
                )

                val headers = ArrayList<MessageEntity>(remoteMessages.size)
                for (message in remoteMessages) {
                    val uid = folder.getUID(message)
                    if (uid <= 0L) continue
                    val parsed = MimeParser.parseHeader(message)
                    val entity = MessageEntity(
                        id = "${account.id}:INBOX:$uid",
                        accountId = account.id,
                        folderType = "INBOX",
                        remoteFolder = folder.fullName,
                        remoteUid = uid,
                        internetMessageId = parsed.internetMessageId,
                        senderName = parsed.senderName,
                        senderAddress = parsed.senderAddress,
                        recipients = parsed.recipients,
                        cc = parsed.cc,
                        subject = parsed.subject,
                        preview = "",
                        bodyText = "",
                        bodyHtml = null,
                        bodyLoaded = false,
                        bodyParserVersion = 0,
                        receivedAt = parsed.receivedAt,
                        unread = !message.isSet(Flags.Flag.SEEN),
                        starred = message.isSet(Flags.Flag.FLAGGED),
                        hasAttachments = false,
                        htmlContentHash = null,
                    )
                    headers += entity
                }

                val recentFlags = if (messageCount > 0) {
                    val flagMessages = if (!canIncrementByUid && remoteMessages.isNotEmpty()) {
                        // Initial/repair windows already fetched FLAGS above; do not request them a
                        // second time from the same messages.
                        remoteMessages.take(FLAG_REFRESH_WINDOW)
                    } else {
                        val firstFlagSequence = max(1, messageCount - FLAG_REFRESH_WINDOW + 1)
                        folder.getMessages(firstFlagSequence, messageCount).toList().also { messages ->
                            if (messages.isNotEmpty()) {
                                val profile = FetchProfile().apply {
                                    add(FetchProfile.Item.FLAGS)
                                    add(UIDFolder.FetchProfileItem.UID)
                                }
                                folder.fetch(messages.toTypedArray(), profile)
                            }
                        }
                    }
                    flagMessages.mapNotNull { message ->
                        val uid = folder.getUID(message)
                        if (uid <= 0L) null else RemoteFlagState(
                            remoteUid = uid,
                            unread = !message.isSet(Flags.Flag.SEEN),
                            starred = message.isSet(Flags.Flag.FLAGGED),
                        )
                    }
                } else {
                    emptyList()
                }

                val latestFetchedUid = remoteMessages.maxOfOrNull { folder.getUID(it) }?.coerceAtLeast(0L) ?: 0L
                val stableUidNext = when {
                    reportedUidNext > 0L -> reportedUidNext
                    latestFetchedUid > 0L -> latestFetchedUid + 1L
                    knownFolder != null -> knownFolder.uidNext
                    else -> 0L
                }

                MailLog.d(
                    MailLog.IMAP,
                    "sync success provider=${provider.id} remoteCount=$messageCount newHeaders=${headers.size} flags=${recentFlags.size} elapsed=${System.currentTimeMillis() - startedAt}ms",
                )
                ImapSyncResult(
                    folder = FolderEntity(
                        id = "${account.id}:INBOX",
                        accountId = account.id,
                        remoteName = folder.fullName,
                        canonicalType = "INBOX",
                        uidValidity = uidValidity,
                        uidNext = stableUidNext,
                        messageCount = messageCount,
                        unreadCount = folder.unreadMessageCount.coerceAtLeast(0),
                        lastSyncAt = System.currentTimeMillis(),
                    ),
                    newHeaders = headers,
                    recentFlags = recentFlags,
                    uidValidityChanged = validityChanged,
                )
            } finally {
                folder.close(false)
            }
        }
    }

    /** Synchronize a server-backed mailbox other than Inbox on demand. */
    internal suspend fun syncSpecialFolder(
        account: AccountEntity,
        provider: MailProvider,
        secret: String,
        canonicalType: String,
        knownFolder: FolderEntity?,
        localMessageCount: Int,
        localMaxUid: Long?,
        initialWindow: Int = 50,
    ): ImapSyncResult = withContext(Dispatchers.IO) {
        require(canonicalType in setOf("SENT", "DRAFTS", "SPAM", "TRASH"))
        withStore(provider, account.mailLoginName, secret, operation = "folder-${canonicalType.lowercase()}", poolLane = "sync") { store ->
            val folder = resolveCanonicalFolder(store, provider, canonicalType, createIfMissing = false)
                ?: return@withStore ImapSyncResult(
                    folder = FolderEntity(
                        id = "${account.id}:$canonicalType",
                        accountId = account.id,
                        remoteName = canonicalType,
                        canonicalType = canonicalType,
                        messageCount = -1,
                        lastSyncAt = System.currentTimeMillis(),
                    ),
                    newHeaders = emptyList(),
                    recentFlags = emptyList(),
                    uidValidityChanged = false,
                )
            folder.open(Folder.READ_ONLY)
            try {
                val messageCount = folder.messageCount.coerceAtLeast(0)
                val uidValidity = runCatching { folder.uidValidity }.getOrDefault(0L).coerceAtLeast(0L)
                val reportedUidNext = runCatching { folder.uidNext }.getOrDefault(-1L)
                val validityChanged = knownFolder != null && knownFolder.uidValidity > 0L &&
                    uidValidity > 0L && knownFolder.uidValidity != uidValidity
                // These are user-visible server folders, not append-only inbox cursors.
                // Fetch the latest bounded window every time so a draft deleted/sent in webmail is
                // removed locally and a server-created Sent copy is reconciled immediately.
                val remoteMessages = if (messageCount == 0) {
                    emptyList()
                } else {
                    val firstSequence = max(1, messageCount - initialWindow + 1)
                    folder.getMessages(firstSequence, messageCount).toList()
                }

                if (remoteMessages.isNotEmpty()) {
                    val profile = FetchProfile().apply {
                        add(FetchProfile.Item.ENVELOPE)
                        add(FetchProfile.Item.FLAGS)
                        add(UIDFolder.FetchProfileItem.UID)
                        add("Message-ID")
                    }
                    folder.fetch(remoteMessages.toTypedArray(), profile)
                }

                val headers = remoteMessages.mapNotNull { message ->
                    val uid = folder.getUID(message)
                    if (uid <= 0L) return@mapNotNull null
                    val parsed = MimeParser.parseHeader(message)
                    MessageEntity(
                        id = "${account.id}:$canonicalType:$uid",
                        accountId = account.id,
                        folderType = canonicalType,
                        remoteFolder = folder.fullName,
                        remoteUid = uid,
                        internetMessageId = parsed.internetMessageId,
                        senderName = parsed.senderName.ifBlank { account.displayName },
                        senderAddress = parsed.senderAddress.ifBlank { account.email },
                        recipients = parsed.recipients,
                        cc = parsed.cc,
                        subject = parsed.subject,
                        preview = "",
                        bodyText = "",
                        bodyHtml = null,
                        bodyLoaded = false,
                        bodyParserVersion = 0,
                        receivedAt = parsed.receivedAt,
                        unread = canonicalType !in setOf("SENT", "DRAFTS") &&
                            !message.isSet(Flags.Flag.SEEN),
                        starred = message.isSet(Flags.Flag.FLAGGED),
                        hasAttachments = false,
                        deliveryState = "REMOTE",
                    )
                }.sortedWith(compareByDescending<MessageEntity> { it.receivedAt }.thenByDescending { it.remoteUid })

                val latestUid = headers.maxOfOrNull(MessageEntity::remoteUid) ?: 0L
                ImapSyncResult(
                    folder = FolderEntity(
                        id = "${account.id}:$canonicalType",
                        accountId = account.id,
                        remoteName = folder.fullName,
                        canonicalType = canonicalType,
                        uidValidity = uidValidity,
                        uidNext = when {
                            reportedUidNext > 0L -> reportedUidNext
                            latestUid > 0L -> latestUid + 1L
                            knownFolder != null -> knownFolder.uidNext
                            else -> 0L
                        },
                        messageCount = messageCount,
                        unreadCount = 0,
                        lastSyncAt = System.currentTimeMillis(),
                    ),
                    newHeaders = headers,
                    recentFlags = emptyList(),
                    uidValidityChanged = validityChanged,
                )
            } finally {
                folder.close(false)
            }
        }
    }

    /** Append a prepared RFC822 message to the server's Sent or Drafts folder. */
    internal suspend fun appendPreparedMessage(
        account: AccountEntity,
        provider: MailProvider,
        secret: String,
        canonicalType: String,
        raw: ByteArray,
        internetMessageId: String,
        replaceFolder: String? = null,
        replaceUid: Long? = null,
    ): RemoteAppendResult = withContext(Dispatchers.IO) {
        require(canonicalType == "SENT" || canonicalType == "DRAFTS")
        withStore(provider, account.mailLoginName, secret, operation = "append-${canonicalType.lowercase()}", poolLane = "interactive") { store ->
            val target = resolveCanonicalFolder(store, provider, canonicalType, createIfMissing = true)
                ?: error("Unable to resolve $canonicalType folder")

            // Gmail and Microsoft mailboxes can create their own Sent copy after SMTP acceptance.
            // Poll by normalized Message-ID before adding an IMAP FCC copy. This avoids a real remote
            // duplicate while keeping providers that need an explicit APPEND responsive.
            if (canonicalType == "SENT") {
                val autoCopyLikely = provider.id in setOf("gmail", "outlook", "m365", "163", "126") ||
                    provider.netEaseClientId
                val probeDelays = if (autoCopyLikely) {
                    // Delays are incremental. Keep the placeholder in its already-sent state while
                    // waiting, allowing slower Sent indexing (especially Microsoft/NetEase) to win
                    // before BondMail creates its own FCC copy.
                    longArrayOf(0L, 500L, 900L, 1_500L, 2_400L, 3_600L)
                } else {
                    longArrayOf(0L, 500L)
                }
                for (delayMs in probeDelays) {
                    if (delayMs > 0L) Thread.sleep(delayMs)
                    val existingUid = findRecentMessageUid(target, internetMessageId)
                    if (existingUid > 0L) {
                        MailLog.d(
                            MailLog.IMAP,
                            "sent copy reused provider=${provider.id} uid=$existingUid probeDelay=$delayMs",
                        )
                        return@withStore RemoteAppendResult(target.fullName, existingUid)
                    }
                }
            } else {
                // Draft upload workers can be interrupted after APPEND but before Room is updated.
                // Reuse that stable Message-ID on retry. When editing an existing remote draft, the
                // old UID is intentionally ignored so the changed content is appended first and the
                // previous server copy is deleted only after acceptance.
                val existingUid = findRecentMessageUid(target, internetMessageId)
                if (existingUid > 0L && existingUid != replaceUid) {
                    return@withStore RemoteAppendResult(target.fullName, existingUid)
                }
            }
            val mime = MimeMessage(lenientMimeSession(), ByteArrayInputStream(raw)).apply {
                if (canonicalType == "DRAFTS") setFlag(Flags.Flag.DRAFT, true)
                if (canonicalType == "SENT") setFlag(Flags.Flag.SEEN, true)
            }
            target.appendMessages(arrayOf(mime))

            // Replace an older server draft only after the new copy has been accepted.
            if (canonicalType == "DRAFTS" && !replaceFolder.isNullOrBlank() && replaceUid != null && replaceUid > 0L) {
                runCatching {
                    val oldFolder = store.getFolder(replaceFolder) as IMAPFolder
                    oldFolder.open(Folder.READ_WRITE)
                    try {
                        oldFolder.getMessageByUID(replaceUid)?.setFlag(Flags.Flag.DELETED, true)
                    } finally {
                        oldFolder.close(true)
                    }
                }.onFailure { error ->
                    MailLog.w(MailLog.IMAP, "old draft cleanup failed uid=$replaceUid cause=${MailLog.causeSummary(error)}")
                }
            }

            val postAppendDelays = longArrayOf(0L, 250L, 500L, 900L)
            for (delayMs in postAppendDelays) {
                if (delayMs > 0L) Thread.sleep(delayMs)
                val appendedUid = findRecentMessageUid(target, internetMessageId)
                if (appendedUid > 0L) {
                    MailLog.d(
                        MailLog.IMAP,
                        "message append indexed provider=${provider.id} folder=$canonicalType uid=$appendedUid delay=$delayMs",
                    )
                    return@withStore RemoteAppendResult(target.fullName, appendedUid)
                }
            }
            MailLog.w(
                MailLog.IMAP,
                "message append accepted but uid pending provider=${provider.id} folder=$canonicalType",
            )
            RemoteAppendResult(target.fullName, 0L)
        }
    }

    private fun findRecentMessageUid(folder: IMAPFolder, internetMessageId: String): Long {
        folder.open(Folder.READ_ONLY)
        return try {
            findRecentMessagesByInternetMessageId(folder, internetMessageId, limit = 64)
                .lastOrNull()
                ?.let(folder::getUID)
                ?.coerceAtLeast(0L)
                ?: 0L
        } finally {
            folder.close(false)
        }
    }

    private fun findRecentMessagesByInternetMessageId(
        folder: IMAPFolder,
        internetMessageId: String,
        limit: Int,
    ): List<Message> {
        val normalized = normalizeInternetMessageId(internetMessageId)
        if (normalized.isBlank()) return emptyList()
        val count = folder.messageCount.coerceAtLeast(0)
        if (count == 0) return emptyList()
        val first = max(1, count - limit.coerceAtLeast(1) + 1)
        val recent = folder.getMessages(first, count).toList()
        val profile = FetchProfile().apply {
            add(UIDFolder.FetchProfileItem.UID)
            add("Message-ID")
        }
        folder.fetch(recent.toTypedArray(), profile)
        return recent.filter { message ->
            normalizeInternetMessageId(message.getHeader("Message-ID")?.firstOrNull()) == normalized
        }
    }

    private fun normalizeInternetMessageId(value: String?): String = value
        .orEmpty()
        .trim()
        .trim('<', '>')
        .replace(Regex("\\s+"), "")
        .lowercase()

    private fun resolveCanonicalFolder(
        store: Store,
        provider: MailProvider,
        canonicalType: String,
        createIfMissing: Boolean,
    ): IMAPFolder? {
        if (canonicalType == "INBOX") {
            return store.getFolder("INBOX").takeIf(Folder::exists) as? IMAPFolder
        }
        val desiredAttributes = when (canonicalType) {
            "SENT" -> setOf("\\Sent")
            "DRAFTS" -> setOf("\\Drafts")
            "SPAM" -> setOf("\\Junk", "\\Spam")
            "TRASH" -> setOf("\\Trash")
            else -> emptySet()
        }
        val candidates = runCatching { store.defaultFolder.list("*").toList() }.getOrDefault(emptyList())
        candidates.filterIsInstance<IMAPFolder>().firstOrNull { folder ->
            runCatching {
                folder.attributes.any { attribute ->
                    desiredAttributes.any { desired ->
                        attribute.equals(desired, ignoreCase = true)
                    }
                }
            }
                .getOrDefault(false)
        }?.let { return it }

        val aliases = when (canonicalType) {
            "SENT" -> listOf("sent", "sentitems", "sentmessages", "已发送", "已发送邮件", "寄件备份", "寄件備份")
            "DRAFTS" -> listOf("draft", "drafts", "草稿", "草稿箱")
            "SPAM" -> listOf("spam", "junk", "junkemail", "垃圾邮件", "垃圾郵件")
            "TRASH" -> listOf(
                "trash",
                "deleted",
                "deleteditems",
                "bin",
                "垃圾箱",
                "垃圾桶",
                "已删除",
                "已刪除",
                "已删除邮件",
                "已刪除郵件",
            )
            else -> emptyList()
        }
        candidates.filterIsInstance<IMAPFolder>().firstOrNull { folder ->
            val normalized = normalizeFolderName(folder.fullName)
            aliases.any { alias -> normalized == normalizeFolderName(alias) || normalized.endsWith(normalizeFolderName(alias)) }
        }?.let { return it }

        val preferredNames = when {
            provider.id == "gmail" && canonicalType == "SENT" -> listOf("[Gmail]/Sent Mail", "Sent")
            provider.id == "gmail" && canonicalType == "DRAFTS" -> listOf("[Gmail]/Drafts", "Drafts")
            provider.id == "gmail" && canonicalType == "SPAM" -> listOf("[Gmail]/Spam", "Spam")
            provider.id == "gmail" && canonicalType == "TRASH" -> listOf("[Gmail]/Trash", "Trash")
            provider.id in setOf("outlook", "m365") && canonicalType == "SENT" -> listOf("Sent Items", "Sent")
            provider.id in setOf("outlook", "m365") && canonicalType == "DRAFTS" -> listOf("Drafts")
            provider.id in setOf("outlook", "m365") && canonicalType == "SPAM" -> listOf("Junk Email", "Junk")
            provider.id in setOf("outlook", "m365") && canonicalType == "TRASH" -> listOf("Deleted Items", "Trash")
            provider.netEaseClientId && canonicalType == "SENT" -> listOf("已发送", "Sent")
            provider.netEaseClientId && canonicalType == "DRAFTS" -> listOf("草稿箱", "Drafts")
            provider.netEaseClientId && canonicalType == "SPAM" -> listOf("垃圾邮件", "Spam")
            provider.netEaseClientId && canonicalType == "TRASH" -> listOf("已删除", "垃圾箱", "Trash")
            canonicalType == "SENT" -> listOf("Sent", "Sent Items")
            canonicalType == "DRAFTS" -> listOf("Drafts")
            canonicalType == "SPAM" -> listOf("Junk", "Spam", "Junk Email")
            canonicalType == "TRASH" -> listOf("Trash", "Deleted Items", "Deleted")
            else -> emptyList()
        }
        preferredNames.forEach { name ->
            val folder = store.getFolder(name)
            if (folder.exists()) return folder as? IMAPFolder
        }
        if (!createIfMissing) return null
        preferredNames.forEach { name ->
            val folder = store.getFolder(name)
            if (runCatching { folder.create(Folder.HOLDS_MESSAGES) }.getOrDefault(false) || folder.exists()) {
                return folder as? IMAPFolder
            }
        }
        return null
    }

    private fun normalizeFolderName(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\s_./\\-]+"), "")

    suspend fun loadMessageBody(
        account: AccountEntity,
        provider: MailProvider,
        secret: String,
        local: MessageEntity,
        markSeen: Boolean = true,
        interactive: Boolean = markSeen,
    ): MessageEntity = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        MailLog.d(
            MailLog.IMAP,
            "body start provider=${provider.id} uid=${local.remoteUid} account=${MailLog.accountHint(account.email)}",
        )

        val connectStartedAt = System.currentTimeMillis()
        withStore(
            provider,
            account.email,
            secret,
            operation = "body",
            // An explicit open can use BODY.PEEK while still owning the interactive Store lane.
            // Keeping lane selection independent from \Seen prevents a cancelled background
            // prefetch and the first unread open from touching the same JavaMail Store concurrently.
            poolLane = if (interactive) "interactive" else "prefetch",
        ) { store ->
            val connectedAt = System.currentTimeMillis()
            val folder = store.getFolder(local.remoteFolder) as IMAPFolder
            folder.open(if (markSeen && local.unread) Folder.READ_WRITE else Folder.READ_ONLY)
            val folderOpenedAt = System.currentTimeMillis()
            try {
                val message = folder.getMessageByUID(local.remoteUid)
                    ?: error("Message no longer exists on the server")
                val profile = FetchProfile().apply {
                    add(FetchProfile.Item.ENVELOPE)
                    add(FetchProfile.Item.FLAGS)
                    add(FetchProfile.Item.CONTENT_INFO)
                    add(FetchProfile.Item.SIZE)
                    add(UIDFolder.FetchProfileItem.UID)
                    add("Message-ID")
                }
                folder.fetch(arrayOf(message), profile)
                val structureFetchedAt = System.currentTimeMillis()
                val advertisedSize = runCatching { message.size }.getOrDefault(-1)
                if (advertisedSize in 1..INTERACTIVE_FULL_FETCH_MAX_BYTES) {
                    // For ordinary attachment mail, one complete PEEK fetch is faster and more
                    // reliable than many lazy section reads against servers such as 163. The cap
                    // keeps large videos/archives out of memory; those continue using partial MIME
                    // section loading.
                    val bodyProfile = FetchProfile().apply {
                        add(IMAPFolder.FetchProfileItem.MESSAGE)
                        add(FetchProfile.Item.FLAGS)
                        add(UIDFolder.FetchProfileItem.UID)
                    }
                    folder.fetch(arrayOf(message), bodyProfile)
                    MailLog.d(
                        MailLog.IMAP,
                        "body materialized provider=${provider.id} uid=${local.remoteUid} " +
                            "bytes=$advertisedSize mode=single-peek",
                    )
                }
                val parsed = parseInteractiveMessage(
                    message = message,
                    provider = provider,
                    remoteUid = local.remoteUid,
                    advertisedSize = advertisedSize,
                )
                val parsedAt = System.currentTimeMillis()

                if (parsed.html.isNullOrBlank() && parsed.text.isBlank() && parsed.attachments.isEmpty()) {
                    error("No displayable MIME body or attachment was found")
                }

                if (markSeen && local.unread) {
                    message.setFlag(Flags.Flag.SEEN, true)
                }

                val result = local.copy(
                    internetMessageId = parsed.internetMessageId,
                    senderName = parsed.senderName,
                    senderAddress = parsed.senderAddress,
                    recipients = parsed.recipients,
                    cc = parsed.cc,
                    subject = parsed.subject,
                    preview = MimeParser.preview(parsed.text),
                    bodyText = parsed.text,
                    bodyHtml = parsed.html?.takeIf { it.isNotBlank() },
                    bodyLoaded = true,
                    bodyParserVersion = MimeParser.CURRENT_VERSION,
                    receivedAt = parsed.receivedAt,
                    unread = if (markSeen && local.unread) false else !message.isSet(Flags.Flag.SEEN),
                    starred = message.isSet(Flags.Flag.FLAGGED),
                    hasAttachments = parsed.hasAttachments,
                    attachmentsJson = MailAttachmentCodec.encode(parsed.attachments),
                    htmlContentHash = parsed.html?.takeIf { it.isNotBlank() }?.let(MimeParser::hash),
                )
                MailLog.d(
                    MailLog.IMAP,
                    "body success provider=${provider.id} uid=${local.remoteUid} " +
                        "type=${message.contentType.substringBefore(';')} htmlChars=${parsed.html?.length ?: 0} " +
                        "textChars=${parsed.text.length} connect=${connectedAt - connectStartedAt}ms " +
                        "open=${folderOpenedAt - connectedAt}ms structure=${structureFetchedAt - folderOpenedAt}ms " +
                        "parse=${parsedAt - structureFetchedAt}ms total=${System.currentTimeMillis() - startedAt}ms",
                )
                result
            } finally {
                folder.close(false)
            }
        }
    }

    /**
     * Download a small first-screen body window with one IMAP BODY fetch. This mirrors K-9's
     * network strategy: message sizes are fetched once, messages below the automatic-download
     * ceiling are requested together, and large messages remain header-only until opened.
     */
    internal suspend fun prefetchMessageBodies(
        account: AccountEntity,
        provider: MailProvider,
        secret: String,
        locals: List<MessageEntity>,
        maximumMessageBytes: Int,
    ): List<MessageEntity> = withContext(Dispatchers.IO) {
        if (locals.isEmpty()) return@withContext emptyList()

        val startedAt = System.currentTimeMillis()
        withStore(
            provider = provider,
            email = account.email,
            secret = secret,
            operation = "body-batch",
            poolLane = "sync",
        ) { store ->
            val loaded = ArrayList<MessageEntity>(locals.size)
            var skippedLarge = 0

            locals.groupBy(MessageEntity::remoteFolder).forEach folderLoop@ { (remoteFolder, folderLocals) ->
                val folder = store.getFolder(remoteFolder) as IMAPFolder
                folder.open(Folder.READ_ONLY)
                try {
                    val localByUid = folderLocals.associateBy(MessageEntity::remoteUid)
                    val messages = folder.getMessagesByUID(
                        folderLocals.map(MessageEntity::remoteUid).distinct().toLongArray(),
                    ).filterNotNull()
                    if (messages.isEmpty()) return@folderLoop

                    val metadataProfile = FetchProfile().apply {
                        add(FetchProfile.Item.ENVELOPE)
                        add(FetchProfile.Item.FLAGS)
                        add(FetchProfile.Item.CONTENT_INFO)
                        add(FetchProfile.Item.SIZE)
                        add(UIDFolder.FetchProfileItem.UID)
                        add("Message-ID")
                    }
                    folder.fetch(messages.toTypedArray(), metadataProfile)

                    val smallMessages = messages.filter { message ->
                        val size = runCatching { message.size }.getOrDefault(-1)
                        val eligible = size in 1..maximumMessageBytes
                        if (!eligible && size > maximumMessageBytes) skippedLarge += 1
                        eligible
                    }
                    if (smallMessages.isEmpty()) return@folderLoop

                    val bodyProfile = FetchProfile().apply {
                        // android-mail translates MESSAGE to a full PEEK body request when
                        // mail.imaps.peek=true, so background prefetch never changes \Seen.
                        add(IMAPFolder.FetchProfileItem.MESSAGE)
                        add(FetchProfile.Item.FLAGS)
                        add(UIDFolder.FetchProfileItem.UID)
                    }
                    folder.fetch(smallMessages.toTypedArray(), bodyProfile)

                    smallMessages.forEach messageLoop@ { message ->
                        val uid = folder.getUID(message)
                        val local = localByUid[uid] ?: return@messageLoop
                        runCatching {
                            val parsed = MimeParser.parse(message)
                            if (parsed.html.isNullOrBlank() && parsed.text.isBlank()) {
                                error("No displayable MIME body was found")
                            }
                            local.copy(
                                internetMessageId = parsed.internetMessageId,
                                senderName = parsed.senderName,
                                senderAddress = parsed.senderAddress,
                                recipients = parsed.recipients,
                                cc = parsed.cc,
                                subject = parsed.subject,
                                preview = MimeParser.preview(parsed.text),
                                bodyText = parsed.text,
                                bodyHtml = parsed.html?.takeIf(String::isNotBlank),
                                bodyLoaded = true,
                                bodyParserVersion = MimeParser.CURRENT_VERSION,
                                receivedAt = parsed.receivedAt,
                                // Never let a background BODY request alter user-visible flags.
                                unread = local.unread,
                                starred = local.starred,
                                hasAttachments = parsed.hasAttachments,
                                attachmentsJson = MailAttachmentCodec.encode(parsed.attachments),
                                htmlContentHash = parsed.html
                                    ?.takeIf(String::isNotBlank)
                                    ?.let(MimeParser::hash),
                                remoteImageAllowed = local.remoteImageAllowed,
                            )
                        }.onSuccess { parsedMessage ->
                            loaded += parsedMessage
                        }.onFailure { error ->
                            MailLog.w(
                                MailLog.IMAP,
                                "body batch parse failed provider=${provider.id} uid=$uid " +
                                    "cause=${MailLog.causeSummary(error)}",
                                error,
                            )
                        }
                    }
                } finally {
                    folder.close(false)
                }
            }

            MailLog.d(
                MailLog.IMAP,
                "body batch success provider=${provider.id} requested=${locals.size} " +
                    "loaded=${loaded.size} skippedLarge=$skippedLarge " +
                    "limitBytes=$maximumMessageBytes elapsed=${System.currentTimeMillis() - startedAt}ms",
            )
            loaded
        }
    }

    suspend fun setSeen(
        account: AccountEntity,
        provider: MailProvider,
        secret: String,
        message: MessageEntity,
        seen: Boolean,
    ) = withContext(Dispatchers.IO) {
        withStore(provider, account.mailLoginName, secret, operation = "seen", poolLane = "interactive") { store ->
            val folder = store.getFolder(message.remoteFolder) as IMAPFolder
            folder.open(Folder.READ_WRITE)
            try {
                folder.getMessageByUID(message.remoteUid)?.setFlag(Flags.Flag.SEEN, seen)
                MailLog.d(
                    MailLog.IMAP,
                    "seen success provider=${provider.id} uid=${message.remoteUid} seen=$seen",
                )
            } finally {
                folder.close(false)
            }
        }
    }

    /** Submit one IMAP STORE operation per remote folder instead of reconnecting per row. */
    suspend fun setSeenBatch(
        account: AccountEntity,
        provider: MailProvider,
        secret: String,
        messages: List<MessageEntity>,
        seen: Boolean,
    ) = withContext(Dispatchers.IO) {
        val remoteMessages = messages.filter { it.remoteUid > 0L }
        if (remoteMessages.isEmpty()) return@withContext
        withStore(
            provider,
            account.mailLoginName,
            secret,
            operation = "seen-batch",
            poolLane = "interactive",
        ) { store ->
            remoteMessages.groupBy(MessageEntity::remoteFolder).forEach { (remoteFolder, locals) ->
                val folder = store.getFolder(remoteFolder) as IMAPFolder
                folder.open(Folder.READ_WRITE)
                try {
                    val targets = folder.getMessagesByUID(
                        locals.map(MessageEntity::remoteUid).distinct().toLongArray(),
                    ).filterNotNull().toTypedArray()
                    if (targets.isNotEmpty()) {
                        folder.setFlags(targets, Flags(Flags.Flag.SEEN), seen)
                    }
                    MailLog.d(
                        MailLog.IMAP,
                        "seen batch success provider=${provider.id} folder=$remoteFolder " +
                            "requested=${locals.size} matched=${targets.size} seen=$seen",
                    )
                } finally {
                    folder.close(false)
                }
            }
        }
    }

    internal suspend fun downloadAttachment(
        account: AccountEntity,
        provider: MailProvider,
        secret: String,
        message: MessageEntity,
        attachmentIndex: Int,
    ): MailAttachmentData = withContext(Dispatchers.IO) {
        withStore(
            provider,
            account.mailLoginName,
            secret,
            operation = "attachment",
            poolLane = "interactive",
        ) { store ->
            val folder = store.getFolder(message.remoteFolder) as IMAPFolder
            folder.open(Folder.READ_ONLY)
            try {
                val remote = folder.getMessageByUID(message.remoteUid)
                    ?: error("Message no longer exists on the server")
                MimeParser.readAttachment(remote, attachmentIndex)
                    ?: error("Attachment is no longer available")
            } finally {
                folder.close(false)
            }
        }
    }

    suspend fun setFlagged(
        account: AccountEntity,
        provider: MailProvider,
        secret: String,
        message: MessageEntity,
        flagged: Boolean,
    ) = withContext(Dispatchers.IO) {
        withStore(provider, account.mailLoginName, secret, operation = "flag", poolLane = "interactive") { store ->
            val folder = store.getFolder(message.remoteFolder) as IMAPFolder
            folder.open(Folder.READ_WRITE)
            try {
                folder.getMessageByUID(message.remoteUid)?.setFlag(Flags.Flag.FLAGGED, flagged)
            } finally {
                folder.close(false)
            }
        }
    }

    suspend fun move(
        account: AccountEntity,
        provider: MailProvider,
        secret: String,
        message: MessageEntity,
        targetCanonicalType: String,
    ) = withContext(Dispatchers.IO) {
        require(targetCanonicalType in setOf("INBOX", "SPAM", "TRASH"))
        withStore(provider, account.mailLoginName, secret, operation = "move", poolLane = "interactive") { store ->
            val source = store.getFolder(message.remoteFolder) as IMAPFolder
            val target = resolveCanonicalFolder(
                store = store,
                provider = provider,
                canonicalType = targetCanonicalType,
                createIfMissing = targetCanonicalType == "TRASH",
            ) ?: error("Unable to resolve $targetCanonicalType folder")
            if (source.fullName.equals(target.fullName, ignoreCase = true)) return@withStore

            source.open(Folder.READ_WRITE)
            var expungeSource = false
            try {
                val remote = source.getMessageByUID(message.remoteUid)
                    ?: findRecentMessagesByInternetMessageId(
                        folder = source,
                        internetMessageId = message.internetMessageId.orEmpty(),
                        limit = 96,
                    ).lastOrNull()
                if (remote == null) {
                    MailLog.d(
                        MailLog.IMAP,
                        "move already absent provider=${provider.id} folder=${message.remoteFolder} uid=${message.remoteUid}",
                    )
                } else {
                    // COPY + \Deleted works on every IMAP server supported by JavaMail, including
                    // providers that do not advertise RFC 6851 MOVE.
                    source.copyMessages(arrayOf(remote), target)
                    remote.setFlag(Flags.Flag.DELETED, true)
                    expungeSource = true
                    MailLog.d(
                        MailLog.IMAP,
                        "move success provider=${provider.id} from=${source.fullName} " +
                            "to=${target.fullName} uid=${source.getUID(remote).coerceAtLeast(0L)}",
                    )
                }
            } finally {
                source.close(expungeSource)
            }
        }
    }

    /** Move every message from an exact sender address in one remote folder. */
    suspend fun moveSenderMessages(
        account: AccountEntity,
        provider: MailProvider,
        secret: String,
        sourceRemoteFolder: String,
        senderAddress: String,
        targetCanonicalType: String,
    ): Int = withContext(Dispatchers.IO) {
        require(targetCanonicalType in setOf("INBOX", "SPAM", "TRASH"))
        withStore(
            provider,
            account.mailLoginName,
            secret,
            operation = "move-sender",
            poolLane = "interactive",
        ) { store ->
            val source = store.getFolder(sourceRemoteFolder) as IMAPFolder
            val target = resolveCanonicalFolder(
                store = store,
                provider = provider,
                canonicalType = targetCanonicalType,
                createIfMissing = targetCanonicalType == "TRASH",
            ) ?: error("Unable to resolve $targetCanonicalType folder")
            source.open(Folder.READ_WRITE)
            var expungeSource = false
            try {
                val normalizedSender = senderAddress.trim().lowercase(Locale.ROOT)
                val matches = source.search(FromStringTerm(senderAddress)).filter { remote ->
                    remote.from.orEmpty().any { address ->
                        val value = (address as? InternetAddress)?.address ?: address.toString()
                        value.trim().lowercase(Locale.ROOT) == normalizedSender
                    }
                }.toTypedArray()
                if (matches.isNotEmpty()) {
                    source.copyMessages(matches, target)
                    matches.forEach { remote -> remote.setFlag(Flags.Flag.DELETED, true) }
                    expungeSource = true
                }
                MailLog.d(
                    MailLog.IMAP,
                    "move sender success provider=${provider.id} from=${source.fullName} " +
                        "to=${target.fullName} matches=${matches.size}",
                )
                matches.size
            } finally {
                source.close(expungeSource)
            }
        }
    }

    suspend fun delete(
        account: AccountEntity,
        provider: MailProvider,
        secret: String,
        message: MessageEntity,
    ) = withContext(Dispatchers.IO) {
        withStore(provider, account.mailLoginName, secret, operation = "delete", poolLane = "interactive") { store ->
            val folder = store.getFolder(message.remoteFolder) as IMAPFolder
            folder.open(Folder.READ_WRITE)
            try {
                val remote = folder.getMessageByUID(message.remoteUid)
                    ?: findRecentMessagesByInternetMessageId(
                        folder = folder,
                        internetMessageId = message.internetMessageId.orEmpty(),
                        limit = 96,
                    ).lastOrNull()
                if (remote == null) {
                    // The row may already have been removed on another client. Treat that state as
                    // successful deletion instead of restoring a stale local record.
                    MailLog.d(
                        MailLog.IMAP,
                        "delete already absent provider=${provider.id} folder=${message.remoteFolder} uid=${message.remoteUid}",
                    )
                } else {
                    remote.setFlag(Flags.Flag.DELETED, true)
                    MailLog.d(
                        MailLog.IMAP,
                        "delete success provider=${provider.id} folder=${message.remoteFolder} " +
                            "uid=${folder.getUID(remote).coerceAtLeast(0L)}",
                    )
                }
            } finally {
                folder.close(true)
            }
        }
    }

    suspend fun deleteByInternetMessageId(
        account: AccountEntity,
        provider: MailProvider,
        secret: String,
        canonicalType: String,
        internetMessageId: String,
    ): Int = withContext(Dispatchers.IO) {
        withStore(provider, account.mailLoginName, secret, operation = "delete-by-id", poolLane = "interactive") { store ->
            val folder = resolveCanonicalFolder(store, provider, canonicalType, createIfMissing = false)
                ?: return@withStore 0
            folder.open(Folder.READ_WRITE)
            try {
                val matches = findRecentMessagesByInternetMessageId(folder, internetMessageId, limit = 128)
                matches.forEach { it.setFlag(Flags.Flag.DELETED, true) }
                MailLog.d(
                    MailLog.IMAP,
                    "delete by message-id provider=${provider.id} folder=$canonicalType matches=${matches.size}",
                )
                matches.size
            } finally {
                folder.close(true)
            }
        }
    }

    private fun <T> withStore(
        provider: MailProvider,
        email: String,
        secret: String,
        operation: String,
        poolLane: String,
        block: (Store) -> T,
    ): T {
        cleanupIdleStores()
        val normalizedEmail = if (provider.netEaseClientId) {
            email.trim().lowercase()
        } else {
            email.trim()
        }
        val key = "${provider.id}|${normalizedEmail.lowercase()}|$poolLane"
        val now = System.currentTimeMillis()
        val existing = storePool[key]
        val reusable = existing?.takeIf { pooled ->
            now - pooled.lastUsedAt <= STORE_IDLE_TIMEOUT_MS &&
                runCatching { pooled.store.isConnected }.getOrDefault(false)
        }
        val canReuse = reusable != null
        val store = if (reusable != null) {
            MailLog.d(
                MailLog.IMAP,
                "connect reuse provider=${provider.id} mode=$operation lane=$poolLane " +
                    "account=${MailLog.accountHint(normalizedEmail)} idle=${now - reusable.lastUsedAt}ms",
            )
            reusable.store
        } else {
            existing?.let { stale -> evictStore(key, stale) }
            connect(provider, normalizedEmail, secret, operation).also { connected ->
                storePool[key] = PooledStore(connected, now)
            }
        }

        return try {
            runStoreBlock(key, store, block)
        } catch (failure: Throwable) {
            storePool[key]?.takeIf { pooled -> pooled.store === store }?.let { evictStore(key, it) }
            if (!canReuse || failure is javax.mail.AuthenticationFailedException) throw failure

            // Mobile NATs and providers may silently close an idle socket while JavaMail still
            // reports it as connected. Retry the operation once with a fresh authenticated Store.
            MailLog.w(
                MailLog.IMAP,
                "pooled store retry provider=${provider.id} mode=$operation lane=$poolLane " +
                    "cause=${MailLog.causeSummary(failure)}",
                failure,
            )
            val fresh = connect(provider, normalizedEmail, secret, operation)
            val pooled = PooledStore(fresh, System.currentTimeMillis())
            storePool[key] = pooled
            try {
                runStoreBlock(key, fresh, block)
            } catch (retryFailure: Throwable) {
                storePool[key]?.takeIf { it.store === fresh }?.let { evictStore(key, it) }
                throw retryFailure
            }
        }
    }

    private fun <T> runStoreBlock(
        key: String,
        store: Store,
        block: (Store) -> T,
    ): T = block(store).also {
        storePool[key]?.takeIf { pooled -> pooled.store === store }?.lastUsedAt =
            System.currentTimeMillis()
    }

    private fun evictStore(key: String, pooled: PooledStore) {
        if (storePool.remove(key, pooled)) runCatching { pooled.store.close() }
    }


    private fun cleanupIdleStores() {
        val now = System.currentTimeMillis()
        storePool.entries.toList().forEach { (key, pooled) ->
            if (now - pooled.lastUsedAt > STORE_IDLE_TIMEOUT_MS && storePool.remove(key, pooled)) {
                runCatching { pooled.store.close() }
            }
        }
    }

    private fun connect(
        provider: MailProvider,
        email: String,
        secret: String,
        operation: String,
    ): Store {
        // NetEase logins are case-insensitive and are more reliable with a normalized local part.
        // Other providers keep the exact address the user entered for authentication.
        val loginEmail = if (provider.netEaseClientId) email.trim().lowercase() else email.trim()
        var lastFailure: Throwable? = null

        // App startup can race Android's network validation after Wi-Fi/mobile switching. Give the
        // active network a short chance to become usable before opening a TLS socket.
        waitForUsableNetwork()
        repeat(3) { attempt ->
            val attemptNumber = attempt + 1
            val protocol = if (provider.imapSecurity == MailSecurity.SSL_TLS) "imaps" else "imap"
            val propertyPrefix = "mail.$protocol"
            val props = Properties().apply {
                put("mail.store.protocol", protocol)
                put("$propertyPrefix.host", provider.imapHost)
                put("$propertyPrefix.port", provider.imapPort.toString())
                put("$propertyPrefix.ssl.enable", (provider.imapSecurity == MailSecurity.SSL_TLS).toString())
                if (provider.imapSecurity == MailSecurity.STARTTLS) {
                    put("$propertyPrefix.starttls.enable", "true")
                    put("$propertyPrefix.starttls.required", "true")
                }
                put("$propertyPrefix.connectiontimeout", when (attempt) {
                    0 -> "10000"
                    1 -> "15000"
                    else -> "20000"
                })
                val bodyTimeout = if (operation.startsWith("body")) 45000 else 25000
                put("$propertyPrefix.timeout", bodyTimeout.toString())
                put("$propertyPrefix.writetimeout", bodyTimeout.toString())
                put("$propertyPrefix.peek", "true")
                // Fetch MIME sections lazily. This mirrors Thunderbird's partial-body strategy:
                // text/HTML and referenced inline images are downloaded when the parser accesses
                // them, while large regular attachments are not pulled just to open the message.
                put("$propertyPrefix.partialfetch", "true")
                put("$propertyPrefix.fetchsize", "262144")
                put("$propertyPrefix.connectionpoolsize", "2")
                put("$propertyPrefix.connectionpooltimeout", STORE_IDLE_TIMEOUT_MS.toString())
                put("$propertyPrefix.compress.enable", "true")
                if (provider.imapSecurity != MailSecurity.NONE) {
                    put("$propertyPrefix.ssl.checkserveridentity", "true")
                }
                // Keep JavaMail tolerant of real-world multipart output generated by mobile and
                // web clients. Apple Mail accepts these messages, while strict JavaMail parsing can
                // otherwise expose a multipart/mixed shell with no readable child body.
                put("mail.mime.parameters.strict", "false")
                put("mail.mime.decodeparameters", "true")
                put("mail.mime.base64.ignoreerrors", "true")
                put("mail.mime.multipart.ignoremissingendboundary", "true")
                put("mail.mime.multipart.allowempty", "true")

                // Start with the platform default TLS negotiation. The second pass pins TLS 1.2
                // for older NetEase/mobile middleboxes; the final pass returns to the default.
                if (attempt == 1 && provider.imapSecurity != MailSecurity.NONE) {
                    put("$propertyPrefix.ssl.protocols", "TLSv1.2")
                }

                if (provider.authType == AuthType.OAUTH2) {
                    // android-mail contains a built-in XOAUTH2 authenticator. The access token is supplied
                    // through Store.connect() as the password value, but LOGIN/PLAIN must be
                    // explicitly disabled so the server never interprets the token as a password.
                    put("$propertyPrefix.auth.mechanisms", "XOAUTH2")
                    put("$propertyPrefix.auth.login.disable", "true")
                    put("$propertyPrefix.auth.plain.disable", "true")
                } else {
                    when (provider.authMechanism) {
                        MailAuthMechanism.LOGIN -> {
                            put("$propertyPrefix.auth.mechanisms", "LOGIN")
                            put("$propertyPrefix.auth.plain.disable", "true")
                            put("$propertyPrefix.auth.login.disable", "false")
                        }
                        MailAuthMechanism.PLAIN -> {
                            put("$propertyPrefix.auth.mechanisms", "PLAIN")
                            put("$propertyPrefix.auth.login.disable", "true")
                            put("$propertyPrefix.auth.plain.disable", "false")
                        }
                        MailAuthMechanism.AUTO -> Unit
                    }
                }
            }
            val session = Session.getInstance(props)
            val store = session.getStore(protocol)
            val attemptStartedAt = System.currentTimeMillis()
            MailLog.d(
                MailLog.IMAP,
                "connect attempt=$attemptNumber/3 provider=${provider.id} mode=$operation host=${provider.imapHost}:${provider.imapPort} account=${MailLog.accountHint(loginEmail)} tls=${if (attempt == 1) "TLSv1.2" else "default"} network=${networkSummary()}",
            )
            try {
                store.connect(provider.imapHost, provider.imapPort, loginEmail, secret)
                if (provider.netEaseClientId) {
                    // ID improves NetEase compatibility, but a server-side ID failure must not
                    // discard an already authenticated connection.
                    runCatching {
                        (store as? IMAPStore)?.id(
                            mapOf(
                                "name" to "BondMail",
                                "version" to "0.2.38.0",
                                "vendor" to "Bond",
                                "os" to "Android",
                            ),
                        )
                    }
                }
                MailLog.d(
                    MailLog.IMAP,
                    "connect success attempt=$attemptNumber provider=${provider.id} elapsed=${System.currentTimeMillis() - attemptStartedAt}ms",
                )
                return store
            } catch (failure: Throwable) {
                lastFailure = failure
                MailLog.w(
                    MailLog.IMAP,
                    "connect failed attempt=$attemptNumber provider=${provider.id} elapsed=${System.currentTimeMillis() - attemptStartedAt}ms cause=${MailLog.causeSummary(failure)} network=${networkSummary()}",
                    failure,
                )
                runCatching { store.close() }
                if (failure is javax.mail.AuthenticationFailedException) throw failure
                if (attempt < 2) {
                    waitForUsableNetwork()
                    Thread.sleep(if (attempt == 0) 1_200L else 2_400L)
                }
            }
        }
        throw lastFailure ?: IllegalStateException("Unable to connect to IMAP server")
    }

    private data class PooledStore(
        val store: Store,
        @Volatile var lastUsedAt: Long,
    )

    private companion object {
        const val STORE_IDLE_TIMEOUT_MS = 2 * 60 * 1000L
        const val FLAG_REFRESH_WINDOW = 24
        const val INTERACTIVE_FULL_FETCH_MAX_BYTES = 8 * 1024 * 1024
        const val RAW_MIME_FALLBACK_MAX_BYTES = 20 * 1024 * 1024
    }

    private fun parseInteractiveMessage(
        message: Message,
        provider: MailProvider,
        remoteUid: Long,
        advertisedSize: Int,
    ): ParsedMail {
        val primary = runCatching {
            MimeParser.parse(message).requireDisplayBody()
        }
        primary.getOrNull()?.let { return it }

        val primaryFailure = primary.exceptionOrNull()
            ?: IllegalStateException("No displayable MIME body was found")
        if (advertisedSize > RAW_MIME_FALLBACK_MAX_BYTES) throw primaryFailure

        MailLog.w(
            MailLog.IMAP,
            "body parser fallback provider=${provider.id} uid=$remoteUid bytes=$advertisedSize " +
                "cause=${MailLog.causeSummary(primaryFailure)}",
        )

        val output = SizeLimitedByteArrayOutputStream(RAW_MIME_FALLBACK_MAX_BYTES)
        runCatching { message.writeTo(output) }
            .onFailure { writeFailure ->
                primaryFailure.addSuppressed(writeFailure)
                throw primaryFailure
            }
        val raw = output.toByteArray()
        val localHeader = MimeParser.parseHeader(message)

        val javaMailFallback = runCatching {
            val localMessage = MimeMessage(
                lenientMimeSession(),
                ByteArrayInputStream(raw),
            )
            MimeParser.parse(localMessage).requireDisplayBody()
        }
        javaMailFallback.getOrNull()?.let { parsed ->
            MailLog.d(
                MailLog.IMAP,
                "body parser fallback success provider=${provider.id} uid=$remoteUid mode=javamail " +
                    "htmlChars=${parsed.html?.length ?: 0} textChars=${parsed.text.length} " +
                    "attachments=${parsed.attachments.size}",
            )
            return parsed
        }

        val rawFallback = runCatching {
            MimeParser.parseRaw(raw, localHeader).requireDisplayBody()
        }
        return rawFallback.onSuccess { parsed ->
            MailLog.d(
                MailLog.IMAP,
                "body parser fallback success provider=${provider.id} uid=$remoteUid mode=raw " +
                    "htmlChars=${parsed.html?.length ?: 0} textChars=${parsed.text.length} " +
                    "attachments=${parsed.attachments.size}",
            )
        }.onFailure { fallbackFailure ->
            javaMailFallback.exceptionOrNull()?.let(primaryFailure::addSuppressed)
            primaryFailure.addSuppressed(fallbackFailure)
            MailLog.w(
                MailLog.IMAP,
                "body parser fallback failed provider=${provider.id} uid=$remoteUid " +
                    "cause=${MailLog.causeSummary(fallbackFailure)}",
                fallbackFailure,
            )
        }.getOrElse { throw primaryFailure }
    }

    private fun ParsedMail.requireDisplayBody(): ParsedMail {
        if (html.isNullOrBlank() && text.isBlank() && attachments.isEmpty()) {
            error("No displayable MIME body or attachment was found")
        }
        return this
    }

    private fun lenientMimeSession(): Session = Session.getInstance(
        Properties().apply {
            put("mail.mime.parameters.strict", "false")
            put("mail.mime.decodeparameters", "true")
            put("mail.mime.base64.ignoreerrors", "true")
            put("mail.mime.multipart.ignoremissingendboundary", "true")
            put("mail.mime.multipart.allowempty", "true")
        },
    )

    private class SizeLimitedByteArrayOutputStream(
        private val maximumBytes: Int,
    ) : OutputStream() {
        private val delegate = ByteArrayOutputStream(minOf(maximumBytes, 256 * 1024))

        override fun write(value: Int) {
            ensureCapacity(1)
            delegate.write(value)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            ensureCapacity(length)
            delegate.write(buffer, offset, length)
        }

        fun toByteArray(): ByteArray = delegate.toByteArray()

        private fun ensureCapacity(nextBytes: Int) {
            if (delegate.size() + nextBytes > maximumBytes) {
                throw IOException("MIME fallback exceeds ${maximumBytes / 1024} KiB")
            }
        }
    }

    private fun networkSummary(): String {
        val network = connectivityManager.activeNetwork ?: return "none"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "unknown"
        val transports = buildList {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cell")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
        }.joinToString("+").ifBlank { "other" }
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return "$transports/validated=$validated"
    }

    private fun waitForUsableNetwork() {
        // NET_CAPABILITY_VALIDATED can stay false on working VPNs, captive-network transitions, or
        // some vendor ROMs. Waiting 2.8 seconds before every IMAP connection made BondMail feel much
        // slower than clients that simply try the socket. Accept an INTERNET-capable network
        // immediately and only wait briefly when Android has not exposed any usable network yet.
        repeat(4) {
            val network = connectivityManager.activeNetwork
            val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
            if (capabilities != null &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ) {
                return
            }
            Thread.sleep(150L)
        }
    }

}
