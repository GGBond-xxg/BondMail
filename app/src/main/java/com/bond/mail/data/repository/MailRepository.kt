package com.bond.mail.data.repository

import androidx.room.withTransaction
import com.bond.mail.data.auth.OAuthAccountMismatchException
import com.bond.mail.data.auth.OAuthCredentialBroker
import com.bond.mail.data.auth.OAuthGrant
import com.bond.mail.data.auth.OAuthReauthorizationRequiredException
import com.bond.mail.data.db.ACCOUNT_DISPLAY_NAME_MAX_LENGTH
import com.bond.mail.data.db.AccountEntity
import com.bond.mail.data.db.ContactRow
import com.bond.mail.data.db.MailDatabase
import com.bond.mail.data.db.MessageEntity
import com.bond.mail.data.db.MessageListRow
import com.bond.mail.data.db.NotificationStateEntity
import com.bond.mail.data.db.OutboxEntity
import com.bond.mail.data.mail.ImapClient
import com.bond.mail.data.mail.MailAttachmentCodec
import com.bond.mail.data.mail.MailLog
import com.bond.mail.data.mail.MimeParser
import com.bond.mail.data.mail.SmtpClient
import com.bond.mail.data.performance.UiPerformanceGate
import com.bond.mail.data.model.AuthType
import com.bond.mail.data.model.DuplicateAccountException
import com.bond.mail.data.model.ProviderRegistry
import com.bond.mail.data.model.UiFailure
import com.bond.mail.data.security.CredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException

class MailRepository(
    private val database: MailDatabase,
    private val credentials: CredentialStore,
    private val oauth: OAuthCredentialBroker,
    private val imap: ImapClient,
    private val smtp: SmtpClient,
) {
    companion object {
        /**
         * Serialize only the same account. A slow or temporarily unreachable mailbox must not
         * block a newly added mailbox or another account's manual refresh.
         */
        private val accountSyncMutexes = ConcurrentHashMap<String, Mutex>()
        private val interactiveBodyLoadMutexes = ConcurrentHashMap<String, Mutex>()
        private val prefetchBodyLoadMutexes = ConcurrentHashMap<String, Mutex>()
        private val interactiveBodyAccountMutexes = ConcurrentHashMap<String, Mutex>()
        private val prefetchBodyAccountMutexes = ConcurrentHashMap<String, Mutex>()
        // Every local read/star mutation advances this per-account generation. An IMAP refresh
        // records the value before fetching FLAGS and may apply that snapshot only if the value is
        // unchanged. This prevents a slower periodic refresh from restoring an older unread state
        // after the user has already opened the message.
        private val accountFlagGenerations = ConcurrentHashMap<String, AtomicLong>()

        private fun accountSyncMutex(accountId: String): Mutex =
            accountSyncMutexes.getOrPut(accountId) { Mutex() }

        private fun bodyLoadMutex(messageId: String, interactive: Boolean): Mutex {
            val map = if (interactive) interactiveBodyLoadMutexes else prefetchBodyLoadMutexes
            return map.getOrPut(messageId) { Mutex() }
        }

        private fun removeBodyLoadMutex(
            messageId: String,
            interactive: Boolean,
            mutex: Mutex,
        ) {
            val map = if (interactive) interactiveBodyLoadMutexes else prefetchBodyLoadMutexes
            map.remove(messageId, mutex)
        }

        private fun bodyAccountMutex(accountId: String, interactive: Boolean): Mutex {
            val map = if (interactive) interactiveBodyAccountMutexes else prefetchBodyAccountMutexes
            return map.getOrPut(accountId) { Mutex() }
        }

        private fun flagGeneration(accountId: String): Long =
            accountFlagGenerations.getOrPut(accountId) { AtomicLong(0L) }.get()

        private fun advanceFlagGeneration(accountId: String): Long =
            accountFlagGenerations.getOrPut(accountId) { AtomicLong(0L) }.incrementAndGet()

        private fun openSeenRemoteKey(
            accountId: String,
            folderType: String,
            remoteUid: Long,
        ): String = "$accountId\u0000$folderType\u0000$remoteUid"

        private const val ALL_ACCOUNTS_PREFETCH_KEY = "__all_accounts__"
        private const val VISIBLE_WINDOW_PREFETCH_KEY = "__visible_window__"
        private const val INITIAL_VISIBLE_BATCH_SIZE = 8
        private const val INITIAL_VISIBLE_ROW_DELAY_MS = 28L
        private const val INITIAL_FOLLOW_UP_BATCH_SIZE = 4
        private const val INITIAL_FOLLOW_UP_BATCH_DELAY_MS = 56L
        private const val INITIAL_BODY_PREFETCH_LIMIT = 32
        private const val REGULAR_BODY_PREFETCH_LIMIT = 4
        private const val BODY_PREFETCH_CHUNK_SIZE = 8
        private const val SMALL_BODY_AUTO_DOWNLOAD_BYTES = 128 * 1024
    }

    val accounts: Flow<List<AccountEntity>> = database.accountDao().observeAll()
    val contacts = database.messageDao().observeContacts()

    @Volatile
    private var startupSnapshotLoaded = false

    @Volatile
    private var startupAccounts: List<AccountEntity> = emptyList()

    @Volatile
    private var startupInbox: List<MessageListRow> = emptyList()

    @Volatile
    private var startupContacts: List<ContactRow> = emptyList()

    // Thunderbird downloads a small local body window in the background. Do the same, but keep
    // it deliberately small and lower priority than a message the user explicitly opens.
    private val bodyPrefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val bodyPrefetchJobs = ConcurrentHashMap<String, Job>()

    /**
     * The list tap and detail destination can request the same uncached body in one frame. Sharing
     * the result prevents the waiter from immediately repeating a failed IMAP fetch.
     */
    private data class MessageOpenFlight(
        val result: CompletableDeferred<Result<MessageEntity?>> = CompletableDeferred(),
        val markSeenRequested: AtomicBoolean = AtomicBoolean(false),
    )

    private data class PendingOpenSeen(
        val token: Long,
        val messageId: String,
        val accountId: String,
        val folderType: String,
        val remoteUid: Long,
    ) {
        val remoteKey: String = openSeenRemoteKey(accountId, folderType, remoteUid)

        fun matches(message: MessageEntity): Boolean =
            message.id == messageId &&
                message.accountId == accountId &&
                message.folderType == folderType &&
                message.remoteUid == remoteUid
    }

    private val messageOpenFlights = ConcurrentHashMap<String, MessageOpenFlight>()
    private val messageSeenScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(2))
    private val messageSeenJobs = ConcurrentHashMap<String, Job>()
    private val pendingOpenSeenToken = AtomicLong(0L)
    private val pendingOpenSeenByMessage = ConcurrentHashMap<String, PendingOpenSeen>()
    private val pendingOpenSeenByRemote = ConcurrentHashMap<String, Long>()

    /**
     * Load the local database before Compose draws the first inbox frame. The Android launch
     * window remains visible for these few milliseconds, so a process restart never paints an
     * empty mailbox and then replaces it with cached rows.
     */
    suspend fun preloadStartupSnapshot() {
        database.accountDao().truncateDisplayNames(ACCOUNT_DISPLAY_NAME_MAX_LENGTH)
        val snapshot = database.withTransaction {
            Triple(
                database.accountDao().allNow(),
                database.messageDao().folderRowSnapshot(accountId = null, folderType = "INBOX"),
                database.messageDao().contactsSnapshot(),
            )
        }
        startupAccounts = snapshot.first
        startupInbox = snapshot.second
        startupContacts = snapshot.third
        startupSnapshotLoaded = true
        MailLog.d(
            MailLog.APP,
            "startup cache accounts=${startupAccounts.size} inbox=${startupInbox.size} " +
                "contacts=${startupContacts.size}",
        )
    }

    fun startupAccountsSnapshot(): List<AccountEntity> = startupAccounts

    fun startupInboxSnapshot(): List<MessageListRow> = startupInbox

    fun startupContactsSnapshot(): List<ContactRow> = startupContacts

    fun hasStartupSnapshot(): Boolean = startupSnapshotLoaded

    fun messages(accountId: String?, folderType: String): Flow<List<MessageListRow>> = when (folderType) {
        "STARRED" -> database.messageDao().observeStarredRows(accountId)
        "UNREAD" -> database.messageDao().observeUnreadRows(accountId)
        "DRAFTS" -> combine(
            database.messageDao().observeFolderRows(accountId, "DRAFTS"),
            database.outboxDao().observeDrafts(accountId),
        ) { remote, local -> mergeDraftRows(remote, local) }
        else -> database.messageDao().observeFolderRows(accountId, folderType)
    }

    suspend fun messagesNow(accountId: String?, folderType: String): List<MessageListRow> = when (folderType) {
        "STARRED" -> database.messageDao().starredRowSnapshot(accountId)
        "UNREAD" -> database.messageDao().unreadRowSnapshot(accountId)
        "DRAFTS" -> mergeDraftRows(
            database.messageDao().folderRowSnapshot(accountId, "DRAFTS"),
            database.outboxDao().draftSnapshot(accountId),
        )
        else -> database.messageDao().folderRowSnapshot(accountId, folderType)
    }

    fun message(id: String) = database.messageDao().observeById(id)
    suspend fun messageNow(id: String) = database.messageDao().byId(id)
    suspend fun draftNow(taskId: String) = database.outboxDao().byId(taskId)
    fun search(accountId: String?, query: String) = database.messageDao().searchRows(accountId, query)

    suspend fun addAppPasswordAccount(
        providerId: String,
        email: String,
        displayName: String,
        secret: String,
    ): AccountEntity {
        val provider = ProviderRegistry.byId(providerId)
        require(provider.authType == AuthType.APP_PASSWORD) { "OAuth provider must use OAuth flow" }
        val originalEmail = email.trim()
        val normalizedEmail = originalEmail.lowercase()
        require(normalizedEmail.contains('@')) { "Invalid email address" }
        require(secret.isNotBlank()) { "Authorization code is required" }

        if (database.accountDao().byEmail(normalizedEmail) != null) {
            throw DuplicateAccountException()
        }

        // Validate receive and send sequentially. Some providers rate-limit two simultaneous
        // authentication handshakes from the same mobile network and report a false timeout.
        val loginEmail = if (provider.netEaseClientId) normalizedEmail else originalEmail
        imap.test(provider, loginEmail, secret)
        smtp.test(provider, loginEmail, secret)

        // Check again after the network step to close the small race window caused by
        // repeated taps or another account setup screen.
        if (database.accountDao().byEmail(normalizedEmail) != null) {
            throw DuplicateAccountException()
        }

        val account = AccountEntity(
            id = UUID.randomUUID().toString(),
            providerId = providerId,
            email = originalEmail,
            displayName = displayName.trim().take(ACCOUNT_DISPLAY_NAME_MAX_LENGTH).ifBlank { provider.label.take(ACCOUNT_DISPLAY_NAME_MAX_LENGTH) },
            authType = provider.authType.name,
            sortOrder = (database.accountDao().maxSortOrder() ?: -1) + 1,
            createdAt = System.currentTimeMillis(),
        )
        credentials.save(account.id, secret)
        database.accountDao().upsert(account)
        return account
    }

    /**
     * Validate an OAuth token against both mailbox protocols before committing the local account.
     * The short-lived token is never written to Room or CredentialStore; provider SDK caches are
     * used for future silent token acquisition.
     */
    suspend fun addOAuthAccount(grant: OAuthGrant): AccountEntity {
        val provider = ProviderRegistry.byId(grant.providerId)
        require(provider.authType == AuthType.OAUTH2) { "Provider does not use OAuth" }
        val originalEmail = grant.email.trim()
        val normalizedEmail = originalEmail.lowercase()
        require(normalizedEmail.contains('@')) { "Invalid email address" }
        require(grant.accessToken.isNotBlank()) { "OAuth token is required" }
        require(grant.providerAccountId.isNotBlank()) { "OAuth account identity is required" }

        if (database.accountDao().byEmail(normalizedEmail) != null) {
            throw DuplicateAccountException()
        }

        imap.test(provider, originalEmail, grant.accessToken)
        smtp.test(provider, originalEmail, grant.accessToken)

        if (database.accountDao().byEmail(normalizedEmail) != null) {
            throw DuplicateAccountException()
        }

        val account = AccountEntity(
            id = UUID.randomUUID().toString(),
            providerId = grant.providerId,
            email = originalEmail,
            displayName = grant.displayName.trim().take(ACCOUNT_DISPLAY_NAME_MAX_LENGTH).ifBlank { provider.label.take(ACCOUNT_DISPLAY_NAME_MAX_LENGTH) },
            authType = provider.authType.name,
            oauthAccountId = grant.providerAccountId,
            sortOrder = (database.accountDao().maxSortOrder() ?: -1) + 1,
            createdAt = System.currentTimeMillis(),
        )
        database.accountDao().upsert(account)
        return account
    }


    suspend fun updateAccountDisplayName(accountId: String, displayName: String) {
        val clean = displayName.trim().take(ACCOUNT_DISPLAY_NAME_MAX_LENGTH)
        if (clean.isBlank()) return
        database.accountDao().updateDisplayName(accountId, clean)
    }

    /**
     * Renew an existing OAuth mailbox without deleting its Room data, cached bodies or local state.
     * The provider result must resolve to the same mailbox address. Both IMAP and SMTP are verified
     * before the new provider account identity replaces the previous one.
     */
    suspend fun reauthorizeOAuthAccount(accountId: String, grant: OAuthGrant) {
        accountSyncMutex(accountId).withLock {
            val account = database.accountDao().byId(accountId)
                ?: throw IllegalArgumentException("Account not found")
            val provider = ProviderRegistry.byId(account.providerId)
            require(provider.authType == AuthType.OAUTH2) {
                "App-password account cannot be reauthorized using OAuth"
            }
            require(isSameOAuthProvider(account.providerId, grant.providerId)) {
                "OAuth provider does not match the existing account"
            }
            val authorizedEmail = grant.email.trim()
            val sameProviderIdentity = account.oauthAccountId
                ?.takeIf(String::isNotBlank)
                ?.equals(grant.providerAccountId, ignoreCase = true) == true
            val sameMailboxAddress = account.email.trim().equals(authorizedEmail, ignoreCase = true)
            if (!sameProviderIdentity && !sameMailboxAddress) {
                throw OAuthAccountMismatchException(
                    expectedEmail = account.email.trim(),
                    actualEmail = authorizedEmail,
                )
            }
            require(grant.accessToken.isNotBlank()) { "OAuth token is required" }
            require(grant.providerAccountId.isNotBlank()) { "OAuth account identity is required" }

            val duplicate = database.accountDao().byEmail(authorizedEmail.lowercase())
            if (duplicate != null && duplicate.id != account.id) {
                throw DuplicateAccountException()
            }

            val oldLoginEmail = account.email.trim()
            imap.invalidate(provider, oldLoginEmail)
            if (!oldLoginEmail.equals(authorizedEmail, ignoreCase = true)) {
                imap.invalidate(provider, authorizedEmail)
            }
            try {
                // Use the canonical address returned by the provider. Microsoft aliases can differ
                // in casing (and occasionally in their displayed alias) while still representing
                // the same stable MSAL account id.
                imap.test(provider, authorizedEmail, grant.accessToken)
                smtp.test(provider, authorizedEmail, grant.accessToken)
                database.accountDao().updateOAuthIdentity(
                    id = account.id,
                    oauthAccountId = grant.providerAccountId,
                    email = authorizedEmail,
                )
                // A mailbox migrated from an older app-password release may still have an
                // encrypted basic credential. Once OAuth has been fully validated, remove that
                // obsolete secret so BondMail keeps only the provider SDK token cache.
                credentials.delete(account.id)
            } catch (failure: Throwable) {
                // Do not leave a Store authenticated with a token that failed the full two-protocol
                // validation. The previous provider identity remains untouched in Room.
                imap.invalidate(provider, oldLoginEmail)
                if (!oldLoginEmail.equals(authorizedEmail, ignoreCase = true)) {
                    imap.invalidate(provider, authorizedEmail)
                }
                throw failure
            }
        }
    }

    /**
     * Replace the app password / client authorization code for an existing mailbox.
     *
     * The old secret remains active until both IMAP and SMTP have accepted the replacement. This
     * prevents a typo from locking the account out locally. The account sync mutex also prevents a
     * background refresh from racing the credential swap.
     */
    suspend fun updateAppPassword(accountId: String, newSecret: String) {
        val cleanSecret = newSecret.trim()
        require(cleanSecret.isNotBlank()) { "Authorization code is required" }

        accountSyncMutex(accountId).withLock {
            val account = database.accountDao().byId(accountId)
                ?: throw IllegalArgumentException("Account not found")
            val provider = ProviderRegistry.byId(account.providerId)
            require(provider.authType == AuthType.APP_PASSWORD) {
                "OAuth account must be reauthorized using the OAuth flow"
            }

            val loginEmail = if (provider.netEaseClientId) {
                account.email.trim().lowercase()
            } else {
                account.email.trim()
            }

            // A pooled connection may still be authenticated with the old secret. Remove it before
            // testing so the result reflects the value the user just entered.
            imap.invalidate(provider, loginEmail)
            try {
                imap.test(provider, loginEmail, cleanSecret)
                smtp.test(provider, loginEmail, cleanSecret)

                credentials.save(account.id, cleanSecret)
                database.accountDao().updateSync(account.id, account.lastSyncAt, null)
            } catch (failure: Throwable) {
                // IMAP validation deliberately keeps the authenticated Store warm for the next
                // sync. If SMTP rejects the replacement, close that new Store as well. The old
                // encrypted secret is still intact and the next refresh can reconnect with it.
                imap.invalidate(provider, loginEmail)
                throw failure
            }
        }
    }

    suspend fun reorderAccounts(accountIds: List<String>) {
        database.withTransaction {
            accountIds.forEachIndexed { index, accountId ->
                database.accountDao().updateSortOrder(accountId, index)
            }
        }
    }

    suspend fun deleteAccount(accountId: String) {
        val account = database.accountDao().byId(accountId)
        database.withTransaction {
            database.messageDao().deleteForAccount(accountId)
            database.folderDao().deleteForAccount(accountId)
            database.outboxDao().deleteForAccount(accountId)
            database.notificationStateDao().deleteForAccount(accountId)
            database.accountDao().deleteById(accountId)
        }
        account?.let { removed ->
            runCatching {
                imap.invalidate(ProviderRegistry.byId(removed.providerId), removed.email.trim())
            }
        }
        credentials.delete(accountId)
        clearPendingOpenSeenForAccount(accountId)
        accountSyncMutexes.remove(accountId)
        interactiveBodyAccountMutexes.remove(accountId)
        prefetchBodyAccountMutexes.remove(accountId)
        accountFlagGenerations.remove(accountId)
    }

    suspend fun syncAll(): List<MessageEntity> = supervisorScope {
        val enabledAccounts = database.accountDao().enabledAccounts()
        if (enabledAccounts.isEmpty()) return@supervisorScope emptyList()

        // Different accounts use independent IMAP sessions. Synchronize up to three in parallel so
        // one slow provider does not hold every other inbox behind it.
        val dispatcher = Dispatchers.IO.limitedParallelism(3)
        val results = enabledAccounts.map { account ->
            async(dispatcher) { runCatching { syncAccount(account.id) } }
        }.awaitAll()

        val successes = results.mapNotNull { it.getOrNull() }
        if (successes.isEmpty()) {
            throw results.firstNotNullOf { it.exceptionOrNull() }
        }
        successes.flatten()
    }

    suspend fun syncAccount(accountId: String): List<MessageEntity> {
        var wasInitialSync = false
        val result = accountSyncMutex(accountId).withLock {
            val account = database.accountDao().byId(accountId) ?: return@withLock emptyList()
            wasInitialSync = account.lastSyncAt == null
            val provider = ProviderRegistry.byId(account.providerId)
            val startedAt = System.currentTimeMillis()
            MailLog.d(MailLog.APP, "syncAccount start provider=${provider.id} account=${MailLog.accountHint(account.email)}")
            runCatching { syncAccountInternal(account) }
                .onSuccess { synced ->
                    MailLog.d(MailLog.APP, "syncAccount success provider=${provider.id} new=${synced.size} elapsed=${System.currentTimeMillis() - startedAt}ms")
                }
                .onFailure { error ->
                    MailLog.e(MailLog.APP, "syncAccount failed provider=${provider.id} cause=${MailLog.causeSummary(error)}", error)
                }
                .getOrThrow()
        }
        scheduleBodyPrefetch(
            accountId = accountId,
            limit = if (wasInitialSync) INITIAL_BODY_PREFETCH_LIMIT else REGULAR_BODY_PREFETCH_LIMIT,
            initialDelayMs = if (wasInitialSync) 80L else 3_000L,
        )
        return result
    }

    /** Synchronize the remote folder represented by a home chip. */
    suspend fun syncFolder(accountId: String, folderType: String): List<MessageEntity> {
        return when (folderType) {
            "SENT", "DRAFTS" -> accountSyncMutex(accountId).withLock {
                val account = database.accountDao().byId(accountId) ?: return@withLock emptyList()
                syncSpecialFolderInternal(account, folderType)
            }
            // Unread/starred are projections of the Inbox and therefore refresh the same remote
            // folder rather than trying to resolve non-existent server folders.
            else -> syncAccount(accountId)
        }
    }

    suspend fun syncFolderAll(folderType: String): List<MessageEntity> = supervisorScope {
        val accounts = database.accountDao().enabledAccounts()
        if (accounts.isEmpty()) return@supervisorScope emptyList()
        val dispatcher = Dispatchers.IO.limitedParallelism(3)
        accounts.map { account ->
            async(dispatcher) { runCatching { syncFolder(account.id, folderType) } }
        }.awaitAll().flatMap { result -> result.getOrElse { emptyList() } }
    }

    private suspend fun syncSpecialFolderInternal(
        account: AccountEntity,
        folderType: String,
    ): List<MessageEntity> {
        val provider = ProviderRegistry.byId(account.providerId)
        val knownFolder = database.folderDao().byCanonicalType(account.id, folderType)
        val localCount = database.messageDao().countForFolder(account.id, folderType)
        val localMaxUid = database.messageDao().maxRemoteUid(account.id, folderType)
        val result = withMailboxCredential(account) { credential ->
            imap.syncSpecialFolder(
                account = account,
                provider = provider,
                secret = credential,
                canonicalType = folderType,
                knownFolder = knownFolder,
                localMessageCount = localCount,
                localMaxUid = localMaxUid,
            )
        }

        val rows = if (result.uidValidityChanged) {
            result.newHeaders
        } else {
            val existing = database.messageDao()
                .existingIds(result.newHeaders.map(MessageEntity::id))
                .toHashSet()
            result.newHeaders.filterNot { it.id in existing }
        }
        val existingRemoteRows = database.messageDao()
            .folderEntitySnapshot(account.id, folderType)
            .filter { it.deliveryState == "REMOTE" && it.remoteUid > 0L }
        val fetchedUids = result.newHeaders.mapTo(hashSetOf(), MessageEntity::remoteUid)
        val minimumFetchedUid = fetchedUids.minOrNull()
        val staleRemoteRows = when {
            result.uidValidityChanged -> emptyList()
            result.folder.messageCount == 0 -> existingRemoteRows
            minimumFetchedUid != null -> existingRemoteRows.filter { row ->
                row.remoteUid >= minimumFetchedUid && row.remoteUid !in fetchedUids
            }
            else -> emptyList()
        }

        database.withTransaction {
            if (result.uidValidityChanged) {
                database.messageDao().deleteRemoteForFolder(account.id, folderType)
            } else {
                staleRemoteRows.forEach { database.messageDao().deleteById(it.id) }
            }
            if (rows.isNotEmpty()) database.messageDao().upsertAll(rows)
            result.newHeaders.forEach { remote ->
                val internetMessageId = remote.internetMessageId.orEmpty()
                if (internetMessageId.isBlank()) return@forEach
                if (folderType == "SENT") {
                    database.messageDao().deleteLocalPlaceholderByInternetMessageId(
                        accountId = account.id,
                        folderType = "SENT",
                        normalizedInternetMessageId = normalizeInternetMessageId(internetMessageId),
                    )
                } else {
                    database.outboxDao().deleteDraftByInternetMessageId(account.id, internetMessageId)
                }
            }
            database.folderDao().upsert(result.folder)
        }
        MailLog.d(
            MailLog.APP,
            "special folder sync provider=${provider.id} folder=$folderType rows=${rows.size} removed=${staleRemoteRows.size}",
        )
        return rows
    }

    private suspend fun syncAccountInternal(account: AccountEntity): List<MessageEntity> {
        val provider = ProviderRegistry.byId(account.providerId)
        val knownFolder = database.folderDao().byCanonicalType(account.id, "INBOX")
        val localMessageCount = database.messageDao().countForFolder(account.id, "INBOX")
        val localMaxUid = database.messageDao().maxRemoteUid(account.id, "INBOX")
        // Keep an interrupted first sync in baseline mode. Staged inserts may already have written
        // the first visible rows while `lastSyncAt` intentionally remains null; checking only the
        // local row count would then misclassify the remaining historical mail as new arrivals.
        val establishingNotificationBaseline = account.lastSyncAt == null
        val remoteFlagSnapshotGeneration = flagGeneration(account.id)

        val result = withMailboxCredential(account) { credential ->
            imap.syncInbox(
                account = account,
                provider = provider,
                secret = credential,
                knownFolder = knownFolder,
                localMessageCount = localMessageCount,
                localMaxUid = localMaxUid,
            )
        }

        val newMessages = if (result.uidValidityChanged) {
            result.newHeaders
        } else if (result.newHeaders.isEmpty()) {
            emptyList()
        } else {
            val existingIds = database.messageDao()
                .existingIds(result.newHeaders.map(MessageEntity::id))
                .toHashSet()
            result.newHeaders.filterNot { it.id in existingIds }
        }

        persistInboxSync(
            account = account,
            result = result,
            newMessages = newMessages,
            progressive = establishingNotificationBaseline || result.uidValidityChanged,
            remoteFlagSnapshotGeneration = remoteFlagSnapshotGeneration,
        )
        // The first successful mailbox read establishes the local history baseline. Those rows
        // must appear in the inbox but are not newly-arrived notifications. A UIDVALIDITY reset is
        // treated the same way because every remote UID may be renumbered at once.
        return if (establishingNotificationBaseline || result.uidValidityChanged) {
            emptyList()
        } else {
            newMessages
        }
    }

    /**
     * K-9 performs one batched network fetch, but commits each completed message quickly enough for
     * the list to grow while synchronization continues. Reproduce that first-sync experience without
     * turning one IMAP request into dozens of round trips. The newest eight rows are committed one
     * at a time for the first screen; older rows follow in four-row Room emissions. The account cursor
     * is committed only at the end,
     * so a killed process safely resumes as a historical baseline rather than notifying old mail.
     */
    private suspend fun persistInboxSync(
        account: AccountEntity,
        result: com.bond.mail.data.mail.ImapSyncResult,
        newMessages: List<MessageEntity>,
        progressive: Boolean,
        remoteFlagSnapshotGeneration: Long,
    ) {
        val rowsToInsert = (if (result.uidValidityChanged) result.newHeaders else newMessages)
            .sortedWith(compareByDescending<MessageEntity> { it.receivedAt }.thenByDescending { it.remoteUid })

        if (progressive) {
            val firstVisibleRows = rowsToInsert.take(INITIAL_VISIBLE_BATCH_SIZE)
            database.withTransaction {
                if (result.uidValidityChanged) {
                    database.messageDao().deleteForFolder(account.id, "INBOX")
                }
            }

            firstVisibleRows.forEachIndexed { index, row ->
                currentCoroutineContext().ensureActive()
                if (index > 0) delay(INITIAL_VISIBLE_ROW_DELAY_MS)
                database.messageDao().upsert(row)
                if (index == 0) {
                    MailLog.d(
                        MailLog.APP,
                        "initial inbox visible provider=${account.providerId} " +
                            "first=1 total=${rowsToInsert.size}",
                    )
                }
            }

            rowsToInsert
                .drop(firstVisibleRows.size)
                .chunked(INITIAL_FOLLOW_UP_BATCH_SIZE)
                .forEachIndexed { index, batch ->
                    currentCoroutineContext().ensureActive()
                    delay(INITIAL_FOLLOW_UP_BATCH_DELAY_MS)
                    database.messageDao().upsertAll(batch)
                    MailLog.d(
                        MailLog.APP,
                        "initial inbox batch provider=${account.providerId} " +
                            "index=${index + 2} added=${batch.size}",
                    )
                }
        } else if (rowsToInsert.isNotEmpty()) {
            // Normal incremental refreshes are usually only one or two rows. Keep them atomic so a
            // cancelled background refresh cannot insert a message without later notifying it.
            database.messageDao().upsertAll(rowsToInsert)
        }

        currentCoroutineContext().ensureActive()
        database.withTransaction {
            val currentFlagGeneration = flagGeneration(account.id)
            if (currentFlagGeneration == remoteFlagSnapshotGeneration) {
                result.recentFlags.forEach { flags ->
                    val pendingOpenRead = pendingOpenSeenByRemote.containsKey(
                        openSeenRemoteKey(account.id, "INBOX", flags.remoteUid),
                    )
                    database.messageDao().updateFlags(
                        accountId = account.id,
                        folderType = "INBOX",
                        remoteUid = flags.remoteUid,
                        // The server can still report unread while BODY.PEEK is in progress and the
                        // deferred \Seen write is waiting for accountSync. Preserve the explicit
                        // local open intent; a manual mark-unread removes this pending key first.
                        unread = if (pendingOpenRead) false else flags.unread,
                        starred = flags.starred,
                    )
                }
            } else {
                MailLog.d(
                    MailLog.APP,
                    "skip stale flag snapshot provider=${account.providerId} " +
                        "captured=$remoteFlagSnapshotGeneration current=$currentFlagGeneration " +
                        "flags=${result.recentFlags.size}",
                )
            }
            database.folderDao().upsert(result.folder)
            database.accountDao().updateSync(account.id, System.currentTimeMillis(), null)
        }
    }

    suspend fun ensureBodyLoaded(
        messageId: String,
        markSeen: Boolean = true,
        priority: Boolean = markSeen,
    ): MessageEntity? {
        val firstLocal = database.messageDao().byId(messageId) ?: return null
        if (priority) {
            bodyPrefetchJobs.remove(firstLocal.accountId)?.cancel()
            bodyPrefetchJobs.remove(ALL_ACCOUNTS_PREFETCH_KEY)?.cancel()
            bodyPrefetchJobs.remove(VISIBLE_WINDOW_PREFETCH_KEY)?.cancel()
        }
        // Interactive opens must not wait behind a cancelled background JavaMail call. Blocking
        // IMAP I/O does not always observe coroutine cancellation immediately, so foreground and
        // prefetch requests use separate per-message lanes as well as separate Store lanes. The
        // repository single-flight still deduplicates all explicit detail opens.
        val mutex = bodyLoadMutex(messageId, interactive = priority)
        return bodyAccountMutex(firstLocal.accountId, interactive = priority).withLock accountLock@ {
            mutex.withLock bodyLock@ {
                try {
                    val local = database.messageDao().byId(messageId) ?: return@bodyLock null
                    val hasDisplayableBody = !local.bodyHtml.isNullOrBlank() || local.bodyText.isNotBlank()
                    val parserIsCurrent = local.bodyParserVersion >= MimeParser.CURRENT_VERSION
                    if (local.bodyLoaded && hasDisplayableBody && parserIsCurrent) {
                        MailLog.d(MailLog.APP, "body cache hit uid=${local.remoteUid} accountId=${local.accountId}")
                        return@bodyLock local
                    }

                    val account = requireAccount(local.accountId)
                    val provider = ProviderRegistry.byId(account.providerId)
                    val protectsInteractiveSeen = markSeen && local.unread
                    if (protectsInteractiveSeen) {
                        advanceFlagGeneration(local.accountId)
                    }
                    try {
                        MailLog.d(MailLog.APP, "body cache miss provider=${provider.id} uid=${local.remoteUid} account=${MailLog.accountHint(account.email)}")
                        val remote = runCatching {
                            withMailboxCredential(account) { credential ->
                                imap.loadMessageBody(
                                    account = account,
                                    provider = provider,
                                    secret = credential,
                                    local = local,
                                    markSeen = markSeen,
                                    interactive = priority,
                                )
                            }
                        }.onFailure { error ->
                            MailLog.e(
                                MailLog.APP,
                                "body load failed provider=${provider.id} uid=${local.remoteUid} cause=${MailLog.causeSummary(error)}",
                                error,
                            )
                        }.getOrThrow()
                        database.withTransaction {
                            val latest = database.messageDao().byId(messageId) ?: local
                            val merged = remote.copy(
                                // Background prefetch must never change user-controlled flags. Read
                                // the latest row and write the parsed body in one Room transaction so
                                // a simultaneous local mark-read cannot be overwritten between those
                                // two operations.
                                unread = if (markSeen) remote.unread else latest.unread,
                                starred = latest.starred,
                                remoteImageAllowed = latest.remoteImageAllowed,
                            )
                            database.messageDao().upsert(merged)
                            merged
                        }
                    } finally {
                        // A refresh that started at any point during the interactive SEEN operation
                        // must not commit its older FLAGS snapshot afterwards.
                        if (protectsInteractiveSeen) {
                            advanceFlagGeneration(local.accountId)
                        }
                    }
                } finally {
                    removeBodyLoadMutex(messageId, interactive = priority, mutex = mutex)
                }
            }
        }
    }

    /**
     * Prepare one message for the detail screen.
     *
     * Content loading has priority over the remote \Seen side effect. Every caller awaits the same
     * in-flight BODY result, while the local read state is updated immediately and the server flag
     * is serialized only after the body attempt releases the interactive account lane.
     */
    suspend fun prepareMessageForOpen(
        messageId: String,
        markSeen: Boolean,
    ): MessageEntity? {
        val proposed = MessageOpenFlight().also { flight ->
            if (markSeen) flight.markSeenRequested.set(true)
        }
        val flight = messageOpenFlights.putIfAbsent(messageId, proposed) ?: proposed
        val ownsFlight = flight === proposed
        var contentPrepared = false

        try {
            if (markSeen) {
                flight.markSeenRequested.set(true)
                registerPendingOpenSeen(messageId)
                markReadLocally(messageId)
            }

            if (!ownsFlight) {
                val shared = flight.result.await().getOrThrow()
                // The owner normally schedules the remote flag. Keep this waiter-side fallback for
                // the narrow race where a read request joins after the owner has completed its
                // result but before the flight is removed from the map.
                if (shared != null && (markSeen || hasPendingOpenSeen(messageId))) {
                    scheduleRemoteSeenAfterOpen(messageId)
                }
                return shared
            }

            val local = database.messageDao().byId(messageId)
            val result = if (local == null) {
                null
            } else if (local.needsBodyRefreshForOpen()) {
                // Keep BODY.PEEK independent from the read flag. A flag-only failure must never
                // discard an otherwise valid parsed message or delay the first visible HTML frame.
                ensureBodyLoaded(
                    messageId = messageId,
                    markSeen = false,
                    priority = true,
                )
            } else {
                local
            }
            contentPrepared = result != null
            flight.result.complete(Result.success(result))
            return result
        } catch (failure: Throwable) {
            // The owner can be cancelled while Room is updating the optimistic read state, before
            // BODY loading begins. Always release waiters and remove the flight in that case.
            if (ownsFlight && !flight.result.isCompleted) {
                flight.result.complete(Result.failure(failure))
            }
            throw failure
        } finally {
            if (ownsFlight) {
                messageOpenFlights.remove(messageId, flight)
                // Do not spend the next interactive IMAP slot on a flag-only request when BODY
                // itself failed. The preview stays open and a later retry can fetch content first,
                // then submit \Seen. This is especially important for the first uncached unread
                // message.
                if (
                    contentPrepared &&
                    (flight.markSeenRequested.get() || hasPendingOpenSeen(messageId))
                ) {
                    scheduleRemoteSeenAfterOpen(messageId)
                }
            }
        }
    }

    private suspend fun registerPendingOpenSeen(messageId: String): PendingOpenSeen? {
        val current = database.messageDao().byId(messageId) ?: return null
        if (current.deliveryState != "REMOTE" || current.remoteUid <= 0L) return null

        while (true) {
            val existing = pendingOpenSeenByMessage[messageId]
            if (existing != null && existing.matches(current)) {
                pendingOpenSeenByRemote[existing.remoteKey] = existing.token
                advanceFlagGeneration(current.accountId)
                return existing
            }
            if (existing != null) {
                if (pendingOpenSeenByMessage.remove(messageId, existing)) {
                    pendingOpenSeenByRemote.remove(existing.remoteKey, existing.token)
                }
                continue
            }

            val candidate = PendingOpenSeen(
                token = pendingOpenSeenToken.incrementAndGet(),
                messageId = current.id,
                accountId = current.accountId,
                folderType = current.folderType,
                remoteUid = current.remoteUid,
            )
            if (pendingOpenSeenByMessage.putIfAbsent(messageId, candidate) == null) {
                pendingOpenSeenByRemote[candidate.remoteKey] = candidate.token
                // Invalidate any FLAGS snapshot already in progress. Snapshots that begin later also
                // preserve unread=false through pendingOpenSeenByRemote until the server commit ends.
                advanceFlagGeneration(current.accountId)
                return candidate
            }
        }
    }

    private fun hasPendingOpenSeen(messageId: String): Boolean =
        pendingOpenSeenByMessage.containsKey(messageId)

    private fun clearPendingOpenSeen(
        messageId: String,
        expected: PendingOpenSeen? = null,
    ) {
        while (true) {
            val current = pendingOpenSeenByMessage[messageId] ?: return
            if (expected != null && current.token != expected.token) return
            if (pendingOpenSeenByMessage.remove(messageId, current)) {
                pendingOpenSeenByRemote.remove(current.remoteKey, current.token)
                return
            }
        }
    }

    private fun clearPendingOpenSeenForAccount(accountId: String) {
        pendingOpenSeenByMessage.entries.toList().forEach { (messageId, pending) ->
            if (pending.accountId == accountId) clearPendingOpenSeen(messageId, pending)
        }
    }

    private suspend fun markReadLocally(messageId: String) {
        val current = database.messageDao().byId(messageId) ?: return
        if (!current.unread) return
        advanceFlagGeneration(current.accountId)
        database.messageDao().setUnread(current.id, false)
        MailLog.d(
            MailLog.APP,
            "open read local providerAccount=${current.accountId} uid=${current.remoteUid}",
        )
    }

    private fun scheduleRemoteSeenAfterOpen(messageId: String) {
        // The owner and a waiter can both leave the shared BODY flight in the same scheduler turn.
        // Register a lazy job atomically so those two exits never create duplicate IMAP sessions.
        val candidate = messageSeenScope.launch(start = CoroutineStart.LAZY) {
            val current = database.messageDao().byId(messageId) ?: return@launch
            val initialPending = pendingOpenSeenByMessage[messageId] ?: return@launch
            if (!initialPending.matches(current)) {
                clearPendingOpenSeen(messageId, initialPending)
                return@launch
            }

            runCatching {
                bodyAccountMutex(current.accountId, interactive = true).withLock accountLock@ {
                    accountSyncMutex(current.accountId).withLock syncLock@ {
                        val latest = database.messageDao().byId(messageId) ?: return@syncLock null
                        val activePending = pendingOpenSeenByMessage[messageId]
                            ?: return@syncLock null
                        // A stale server FLAGS refresh may temporarily report unread while this job
                        // waits. Only an explicit local read/unread action clears the token, so do
                        // not mistake that remote snapshot for the user's newer choice.
                        if (!activePending.matches(latest)) {
                            clearPendingOpenSeen(messageId, activePending)
                            return@syncLock null
                        }
                        advanceFlagGeneration(latest.accountId)
                        val account = requireAccount(latest.accountId)
                        withMailboxCredential(account) { credential ->
                            imap.setSeen(
                                account = account,
                                provider = ProviderRegistry.byId(account.providerId),
                                secret = credential,
                                message = latest,
                                seen = true,
                            )
                        }
                        activePending
                    }
                }
            }.onSuccess { committedPending ->
                if (committedPending == null) return@onSuccess
                clearPendingOpenSeen(messageId, committedPending)
                advanceFlagGeneration(current.accountId)
                MailLog.d(
                    MailLog.APP,
                    "open read committed providerAccount=${current.accountId} uid=${current.remoteUid}",
                )
            }.onFailure { failure ->
                // Keep both the optimistic local state and pending intent. A later retry/reopen can
                // submit the server flag after content is available without flashing unread again.
                advanceFlagGeneration(current.accountId)
                MailLog.w(
                    MailLog.APP,
                    "open read deferred uid=${current.remoteUid} cause=${MailLog.causeSummary(failure)}",
                )
            }
        }
        val existing = messageSeenJobs.putIfAbsent(messageId, candidate)
        if (existing != null) {
            candidate.cancel()
            return
        }
        candidate.invokeOnCompletion { messageSeenJobs.remove(messageId, candidate) }
        candidate.start()
    }

    private fun MessageEntity.needsBodyRefreshForOpen(): Boolean {
        val hasDisplayableBody = !bodyHtml.isNullOrBlank() || bodyText.isNotBlank() ||
            (bodyLoaded && hasAttachments)
        return !bodyLoaded || !hasDisplayableBody || bodyParserVersion < MimeParser.CURRENT_VERSION
    }

    fun scheduleBodyPrefetch(
        accountId: String? = null,
        limit: Int = 2,
        initialDelayMs: Long = 5_000L,
    ) {
        val key = accountId ?: ALL_ACCOUNTS_PREFETCH_KEY
        bodyPrefetchJobs.remove(key)?.cancel()
        bodyPrefetchJobs[key] = bodyPrefetchScope.launch {
            if (initialDelayMs > 0L) delay(initialDelayMs)
            val fastInitialWindow = initialDelayMs in 0L..1_000L
            if (fastInitialWindow) {
                UiPerformanceGate.awaitUiIdleWindow(
                    settleDelayMs = 180L,
                    maximumWaitMs = 8_000L,
                )
            } else {
                UiPerformanceGate.awaitBackgroundWindow(
                    settleDelayMs = 900L,
                    maximumWaitMs = 30_000L,
                )
            }
            val candidateIds = database.messageDao().bodyPrefetchCandidates(
                accountId = accountId,
                parserVersion = MimeParser.CURRENT_VERSION,
                limit = limit.coerceIn(1, INITIAL_BODY_PREFETCH_LIMIT),
            )
            if (candidateIds.isEmpty()) return@launch

            val candidates = database.messageDao().byIds(candidateIds)
                .associateBy(MessageEntity::id)
                .let { byId -> candidateIds.mapNotNull(byId::get) }

            candidates.groupBy(MessageEntity::accountId).forEach { (candidateAccountId, localMessages) ->
                if (!currentCoroutineContext().isActive) return@launch
                val account = database.accountDao().byId(candidateAccountId) ?: return@forEach
                val provider = ProviderRegistry.byId(account.providerId)
                val chunks = localMessages.chunked(BODY_PREFETCH_CHUNK_SIZE)

                // Keep the newest first-screen bodies in a small fast batch, then continue with
                // additional K-9-style batches. One 24-message BODY command makes the first eight
                // wait for every older response; 8-row chunks keep Cloudflare/transactional mail
                // fast while still warming enough history for later GitHub taps.
                chunks.forEachIndexed { chunkIndex, chunk ->
                    currentCoroutineContext().ensureActive()
                    if (fastInitialWindow) {
                        UiPerformanceGate.awaitUiIdleWindow(
                            settleDelayMs = if (chunkIndex == 0) 120L else 80L,
                            maximumWaitMs = 8_000L,
                        )
                    } else {
                        UiPerformanceGate.awaitBackgroundWindow(
                            settleDelayMs = 600L,
                            maximumWaitMs = 30_000L,
                        )
                    }

                    val batchAttempt = runCatching {
                        // All paths that need both locks acquire the BODY lane first and the
                        // account sync lock second. The opposite order could deadlock a deferred
                        // \Seen commit against a background prefetch immediately after opening the
                        // first unread message.
                        bodyAccountMutex(candidateAccountId, interactive = false).withLock {
                            accountSyncMutex(candidateAccountId).withLock {
                                withMailboxCredential(account) { credential ->
                                    imap.prefetchMessageBodies(
                                        account = account,
                                        provider = provider,
                                        secret = credential,
                                        locals = chunk,
                                        maximumMessageBytes = SMALL_BODY_AUTO_DOWNLOAD_BYTES,
                                    )
                                }
                            }
                        }
                    }.onFailure { error ->
                        if (error !is kotlinx.coroutines.CancellationException) {
                            MailLog.w(
                                MailLog.APP,
                                "body batch prefetch failed provider=${provider.id} " +
                                    "chunk=${chunkIndex + 1}/${chunks.size} count=${chunk.size} " +
                                    "cause=${MailLog.causeSummary(error)}",
                                error,
                            )
                        }
                    }

                    if (batchAttempt.isSuccess) {
                        batchAttempt.getOrThrow()
                            .sortedByDescending(MessageEntity::receivedAt)
                            .forEach { remote ->
                                currentCoroutineContext().ensureActive()
                                if (UiPerformanceGate.isMailListScrolling()) {
                                    UiPerformanceGate.awaitUiIdleWindow(
                                        settleDelayMs = 80L,
                                        maximumWaitMs = 3_000L,
                                    )
                                }
                                val latest = database.messageDao().byId(remote.id) ?: remote
                                database.messageDao().upsert(
                                    remote.copy(
                                        unread = latest.unread,
                                        starred = latest.starred,
                                        remoteImageAllowed = latest.remoteImageAllowed,
                                    ),
                                )
                                // Let Room/Compose expose previews progressively instead of replacing
                                // the whole visible window in a single frame.
                                delay(12L)
                            }
                    } else {
                        // Providers that reject a multi-message BODY fetch still get the previous
                        // safe one-by-one fallback, bounded to the current eight-row chunk.
                        chunk.forEach { local ->
                            currentCoroutineContext().ensureActive()
                            runCatching { ensureBodyLoaded(local.id, markSeen = false) }
                                .onFailure { error ->
                                    if (error !is kotlinx.coroutines.CancellationException) {
                                        MailLog.w(
                                            MailLog.APP,
                                            "body prefetch failed id=${local.id} " +
                                                "cause=${MailLog.causeSummary(error)}",
                                        )
                                    }
                                }
                        }
                    }
                    if (chunkIndex < chunks.lastIndex) delay(24L)
                }
            }
        }.also { job ->
            job.invokeOnCompletion { bodyPrefetchJobs.remove(key, job) }
        }
    }

    /**
     * Warms only the rows currently visible (plus a small look-ahead supplied by the UI).
     *
     * This is intentionally lower priority than an explicit message open and shares the same
     * per-account prefetch lane as the K-9-style background window. It therefore improves the
     * consistency of first taps without opening extra simultaneous IMAP sessions.
     */
    fun scheduleVisibleBodyPrefetch(
        messageIds: List<String>,
        initialDelayMs: Long = 220L,
    ) {
        val orderedIds = messageIds.distinct().take(12)
        if (orderedIds.isEmpty()) return

        val key = VISIBLE_WINDOW_PREFETCH_KEY
        bodyPrefetchJobs.remove(key)?.cancel()
        bodyPrefetchJobs[key] = bodyPrefetchScope.launch {
            if (initialDelayMs > 0L) delay(initialDelayMs)
            UiPerformanceGate.awaitUiIdleWindow(
                settleDelayMs = 180L,
                maximumWaitMs = 5_000L,
            )

            val localById = database.messageDao().byIds(orderedIds)
                .associateBy(MessageEntity::id)
            val candidates = orderedIds.mapNotNull(localById::get)
                .filter { local ->
                    val hasDisplayableBody =
                        !local.bodyHtml.isNullOrBlank() || local.bodyText.isNotBlank()
                    !local.bodyLoaded ||
                        !hasDisplayableBody ||
                        local.bodyParserVersion < MimeParser.CURRENT_VERSION
                }

            candidates.groupBy(MessageEntity::accountId).forEach { (accountId, accountMessages) ->
                currentCoroutineContext().ensureActive()
                val account = database.accountDao().byId(accountId) ?: return@forEach
                val provider = ProviderRegistry.byId(account.providerId)
                accountMessages.chunked(BODY_PREFETCH_CHUNK_SIZE).forEach { chunk ->
                    currentCoroutineContext().ensureActive()
                    val result = runCatching {
                        // Match the global BODY -> account lock order used by interactive
                        // opens and flag mutations; never hold accountSync while waiting for a
                        // BODY lane owned by the pending remote read receipt.
                        bodyAccountMutex(accountId, interactive = false).withLock {
                            accountSyncMutex(accountId).withLock {
                                withMailboxCredential(account) { credential ->
                                    imap.prefetchMessageBodies(
                                        account = account,
                                        provider = provider,
                                        secret = credential,
                                        locals = chunk,
                                        maximumMessageBytes = SMALL_BODY_AUTO_DOWNLOAD_BYTES,
                                    )
                                }
                            }
                        }
                    }.onFailure { error ->
                        if (error !is kotlinx.coroutines.CancellationException) {
                            MailLog.w(
                                MailLog.APP,
                                "visible body batch failed provider=${provider.id} " +
                                    "count=${chunk.size} cause=${MailLog.causeSummary(error)}",
                            )
                        }
                    }.getOrNull().orEmpty()

                    result.sortedByDescending(MessageEntity::receivedAt).forEach { remote ->
                        currentCoroutineContext().ensureActive()
                        val latest = database.messageDao().byId(remote.id) ?: remote
                        database.messageDao().upsert(
                            remote.copy(
                                unread = latest.unread,
                                starred = latest.starred,
                                remoteImageAllowed = latest.remoteImageAllowed,
                            ),
                        )
                        delay(8L)
                    }
                }
            }
        }.also { job ->
            job.invokeOnCompletion { bodyPrefetchJobs.remove(key, job) }
        }
    }

    suspend fun toggleUnread(messageId: String) {
        database.messageDao().byId(messageId)?.let { toggleUnread(it) }
    }

    /** Compatibility entry point for callers outside the detail flow. */
    suspend fun markReadOnOpen(messageId: String) {
        prepareMessageForOpen(messageId = messageId, markSeen = true)
    }

    suspend fun toggleStarred(messageId: String) {
        database.messageDao().byId(messageId)?.let { toggleStarred(it) }
    }

    suspend fun deleteMessage(messageId: String) {
        database.messageDao().byId(messageId)?.let { deleteMessage(it) }
    }

    suspend fun toggleUnread(message: MessageEntity) {
        val current = database.messageDao().byId(message.id) ?: return
        // A manual flag action is newer than the automatic read intent from opening the detail.
        // Clearing it before the optimistic write makes mark-unread win even if the deferred
        // server \Seen job is already queued.
        clearPendingOpenSeen(current.id)
        val oldUnread = current.unread
        val newUnread = !oldUnread
        advanceFlagGeneration(current.accountId)
        database.messageDao().setUnread(current.id, newUnread)
        MailLog.d(
            MailLog.APP,
            "read flag optimistic providerAccount=${current.accountId} uid=${current.remoteUid} unread=$newUnread",
        )
        try {
            bodyAccountMutex(current.accountId, interactive = true).withLock {
                // A remote FLAGS refresh and a user mutation must not cross. If a refresh was
                // already running, the generation check makes its snapshot stale; otherwise this
                // lock commits SEEN before the next refresh can begin.
                accountSyncMutex(current.accountId).withLock {
                    val account = requireAccount(current.accountId)
                    withMailboxCredential(account) { credential ->
                        imap.setSeen(
                            account,
                            ProviderRegistry.byId(account.providerId),
                            credential,
                            current,
                            seen = !newUnread,
                        )
                    }
                }
            }
            advanceFlagGeneration(current.accountId)
            MailLog.d(
                MailLog.APP,
                "read flag committed providerAccount=${current.accountId} uid=${current.remoteUid} unread=$newUnread",
            )
        } catch (failure: Throwable) {
            database.messageDao().setUnread(current.id, oldUnread)
            advanceFlagGeneration(current.accountId)
            MailLog.e(
                MailLog.APP,
                "read flag rollback uid=${current.remoteUid} unread=$oldUnread cause=${MailLog.causeSummary(failure)}",
                failure,
            )
            throw failure
        }
    }

    suspend fun toggleStarred(message: MessageEntity) {
        val current = database.messageDao().byId(message.id) ?: return
        val oldValue = current.starred
        val newValue = !oldValue
        advanceFlagGeneration(current.accountId)
        database.messageDao().setStarred(current.id, newValue)
        try {
            bodyAccountMutex(current.accountId, interactive = true).withLock {
                accountSyncMutex(current.accountId).withLock {
                    val account = requireAccount(current.accountId)
                    withMailboxCredential(account) { credential ->
                        imap.setFlagged(
                            account,
                            ProviderRegistry.byId(account.providerId),
                            credential,
                            current,
                            newValue,
                        )
                    }
                }
            }
            advanceFlagGeneration(current.accountId)
        } catch (failure: Throwable) {
            database.messageDao().setStarred(current.id, oldValue)
            advanceFlagGeneration(current.accountId)
            throw failure
        }
    }

    suspend fun allowRemoteImages(messageId: String) {
        database.messageDao().setRemoteImageAllowed(messageId, true)
    }

    suspend fun deleteMessage(message: MessageEntity) {
        clearPendingOpenSeen(message.id)
        if (message.deliveryState != "REMOTE" || message.remoteUid <= 0L) {
            // A local Sent placeholder can briefly outlive the provider's real Sent row while the
            // server is assigning a UID. Remove matching remote copies by the stable Message-ID
            // before dropping the placeholder, otherwise the next refresh simply makes it reappear.
            if (message.folderType == "SENT" && !message.internetMessageId.isNullOrBlank()) {
                bodyAccountMutex(message.accountId, interactive = true).withLock {
                    accountSyncMutex(message.accountId).withLock {
                        val account = requireAccount(message.accountId)
                        withMailboxCredential(account) { credential ->
                            imap.deleteByInternetMessageId(
                                account = account,
                                provider = ProviderRegistry.byId(account.providerId),
                                secret = credential,
                                canonicalType = "SENT",
                                internetMessageId = message.internetMessageId.orEmpty(),
                            )
                        }
                    }
                }
            }
            database.withTransaction {
                database.messageDao().deleteById(message.id)
                message.id.removePrefix("outbox:")
                    .takeIf { it != message.id }
                    ?.let { database.outboxDao().deleteById(it) }
                message.internetMessageId
                    ?.takeIf(String::isNotBlank)
                    ?.let(::normalizeInternetMessageId)
                    ?.let { normalized ->
                        database.outboxDao().deleteSentByInternetMessageId(message.accountId, normalized)
                    }
            }
            return
        }
        database.messageDao().deleteById(message.id)
        runCatching {
            bodyAccountMutex(message.accountId, interactive = true).withLock {
                accountSyncMutex(message.accountId).withLock {
                    val account = requireAccount(message.accountId)
                    withMailboxCredential(account) { credential ->
                        imap.delete(
                            account,
                            ProviderRegistry.byId(account.providerId),
                            credential,
                            message,
                        )
                    }
                }
            }
        }.onFailure { database.messageDao().upsert(message); throw it }
    }

    suspend fun queueSend(
        accountId: String,
        recipients: String,
        cc: String,
        bcc: String,
        subject: String,
        body: String,
        attachmentUris: List<String> = emptyList(),
        draftTaskId: String? = null,
        sourceMessageId: String? = null,
    ): OutboxEntity {
        require(recipients.isNotBlank()) { "Recipient is required" }
        val account = requireAccount(accountId)
        val now = System.currentTimeMillis()
        val previousDraft = draftTaskId?.let { database.outboxDao().byId(it) }
        val sourceDraft = sourceMessageId?.let { database.messageDao().byId(it) }
        val taskId = previousDraft?.id ?: UUID.randomUUID().toString()
        val attachmentMetadata = smtp.describeAttachments(attachmentUris)
        val internetMessageId = previousDraft?.internetMessageId
            ?.takeIf(String::isNotBlank)
            ?: sourceDraft?.internetMessageId
                ?.takeIf(String::isNotBlank)
            ?: "<$taskId@bondmail.local>"
        val task = OutboxEntity(
            id = taskId,
            accountId = accountId,
            recipients = recipients,
            cc = cc,
            bcc = bcc,
            subject = subject,
            bodyText = body,
            attachmentsJson = JSONArray(attachmentUris.distinct()).toString(),
            internetMessageId = internetMessageId,
            state = "QUEUED",
            remoteFolder = previousDraft?.remoteFolder ?: sourceDraft?.remoteFolder,
            remoteUid = previousDraft?.remoteUid ?: sourceDraft?.remoteUid?.takeIf { it > 0L },
            sourceMessageId = sourceMessageId ?: previousDraft?.sourceMessageId,
            retryCount = 0,
            lastError = null,
            createdAt = previousDraft?.createdAt ?: now,
            updatedAt = now,
        )
        database.withTransaction {
            database.outboxDao().upsert(task)
            sourceDraft?.let { database.messageDao().deleteById(it.id) }
            database.messageDao().upsert(
                MessageEntity(
                    id = "outbox:$taskId",
                    accountId = accountId,
                    folderType = "SENT",
                    remoteFolder = "SENT",
                    remoteUid = -now.coerceAtLeast(1L),
                    internetMessageId = internetMessageId,
                    senderName = account.displayName,
                    senderAddress = account.email,
                    recipients = recipients,
                    cc = cc,
                    subject = subject,
                    preview = MimeParser.preview(body),
                    bodyText = body,
                    bodyHtml = null,
                    bodyLoaded = true,
                    bodyParserVersion = MimeParser.CURRENT_VERSION,
                    receivedAt = now,
                    unread = false,
                    starred = false,
                    hasAttachments = attachmentMetadata.isNotEmpty(),
                    attachmentsJson = MailAttachmentCodec.encode(attachmentMetadata),
                    deliveryState = "QUEUED",
                ),
            )
        }
        return task
    }

    suspend fun sendOutboxTask(taskId: String) {
        val task = database.outboxDao().byId(taskId) ?: return
        if (task.state == "DRAFT") {
            syncDraftTask(taskId)
            return
        }
        val account = requireAccount(task.accountId)
        val placeholderId = "outbox:${task.id}"

        // SMTP acceptance is the irreversible boundary. Once the server accepts the message, mark
        // it Sent immediately and never submit it again merely because copying the RFC822 payload
        // into the IMAP Sent folder failed. WorkManager retries below enter through state=SENT and
        // only reconcile the mailbox copy/draft cleanup.
        if (task.state != "SENT") {
            val startedAt = System.currentTimeMillis()
            database.withTransaction {
                database.outboxDao().updateState(task.id, "SENDING", null, 0, startedAt)
                database.messageDao().setDeliveryState(placeholderId, "SENDING")
            }
            val prepared = runCatching {
                withMailboxCredential(account) { credential ->
                    smtp.send(account, ProviderRegistry.byId(account.providerId), credential, task)
                }
            }.getOrElse { error ->
                database.withTransaction {
                    database.outboxDao().updateState(
                        task.id,
                        "FAILED",
                        failure(error).key,
                        1,
                        System.currentTimeMillis(),
                    )
                    database.messageDao().setDeliveryState(placeholderId, "FAILED")
                }
                throw error
            }
            val finishedAt = System.currentTimeMillis()
            database.withTransaction {
                database.outboxDao().updateState(task.id, "SENT", null, 0, finishedAt)
                database.messageDao().byId(placeholderId)?.let { local ->
                    database.messageDao().upsert(
                        local.copy(
                            internetMessageId = prepared.internetMessageId,
                            receivedAt = finishedAt,
                            deliveryState = "SENT",
                        ),
                    )
                }
            }
        }

        val sentTask = database.outboxDao().byId(task.id) ?: task.copy(state = "SENT")
        accountSyncMutex(account.id).withLock {
            reconcileSentTask(account, sentTask)
        }
    }

    private suspend fun reconcileSentTask(account: AccountEntity, task: OutboxEntity) {
        val placeholderId = "outbox:${task.id}"
        val localBeforeReconcile = database.messageDao().byId(placeholderId)
        val prepared = smtp.prepare(account, task)
        val attachmentMetadata = smtp.describeAttachments(decodeAttachmentUris(task.attachmentsJson))
        val localAttachmentMetadata = database.messageDao().byId(placeholderId)
            ?.let { MailAttachmentCodec.decode(it.attachmentsJson) }
            .orEmpty()
        val effectiveAttachments = attachmentMetadata.ifEmpty { localAttachmentMetadata }
        val encodedAttachments = MailAttachmentCodec.encode(effectiveAttachments)
        val appended = withMailboxCredential(account) { credential ->
            imap.appendPreparedMessage(
                account = account,
                provider = ProviderRegistry.byId(account.providerId),
                secret = credential,
                canonicalType = "SENT",
                raw = prepared.raw,
                internetMessageId = prepared.internetMessageId,
            )
        }

        if (appended.remoteUid > 0L) {
            val remoteId = "${account.id}:SENT:${appended.remoteUid}"
            database.withTransaction {
                val local = database.messageDao().byId(placeholderId)
                val existingRemote = database.messageDao().byId(remoteId)
                val base = existingRemote ?: local ?: MessageEntity(
                    id = remoteId,
                    accountId = account.id,
                    folderType = "SENT",
                    remoteFolder = appended.remoteFolder,
                    remoteUid = appended.remoteUid,
                    internetMessageId = prepared.internetMessageId,
                    senderName = account.displayName,
                    senderAddress = account.email,
                    recipients = task.recipients,
                    cc = task.cc,
                    subject = task.subject,
                    preview = MimeParser.preview(task.bodyText),
                    bodyText = task.bodyText,
                    bodyHtml = null,
                    bodyLoaded = true,
                    bodyParserVersion = MimeParser.CURRENT_VERSION,
                    receivedAt = task.updatedAt,
                    unread = false,
                    starred = false,
                    hasAttachments = effectiveAttachments.isNotEmpty(),
                    attachmentsJson = encodedAttachments,
                    deliveryState = "REMOTE",
                )
                database.messageDao().upsert(
                    base.copy(
                        id = remoteId,
                        accountId = account.id,
                        folderType = "SENT",
                        remoteFolder = appended.remoteFolder,
                        remoteUid = appended.remoteUid,
                        internetMessageId = prepared.internetMessageId,
                        senderName = account.displayName,
                        senderAddress = account.email,
                        recipients = task.recipients,
                        cc = task.cc,
                        subject = task.subject,
                        preview = MimeParser.preview(task.bodyText),
                        bodyText = local?.bodyText?.takeIf(String::isNotBlank) ?: task.bodyText,
                        bodyHtml = local?.bodyHtml ?: existingRemote?.bodyHtml,
                        bodyLoaded = true,
                        bodyParserVersion = MimeParser.CURRENT_VERSION,
                        receivedAt = local?.receivedAt ?: existingRemote?.receivedAt ?: task.updatedAt,
                        unread = false,
                        hasAttachments = effectiveAttachments.isNotEmpty(),
                        attachmentsJson = encodedAttachments,
                        deliveryState = "REMOTE",
                    ),
                )
                database.messageDao().deleteById(placeholderId)
            }
            MailLog.d(
                MailLog.APP,
                "sent placeholder replaced account=${account.id} uid=${appended.remoteUid} attachments=${effectiveAttachments.size}",
            )
        } else {
            database.messageDao().byId(placeholderId)?.let { local ->
                database.messageDao().upsert(
                    local.copy(
                        remoteFolder = appended.remoteFolder,
                        internetMessageId = prepared.internetMessageId,
                        hasAttachments = effectiveAttachments.isNotEmpty(),
                        attachmentsJson = encodedAttachments,
                        deliveryState = "SENT",
                    ),
                )
            }
            MailLog.w(
                MailLog.APP,
                "sent reconciliation pending account=${account.id} messageId=${normalizeInternetMessageId(prepared.internetMessageId)}",
            )
        }

        // A message sent from a draft should disappear from the provider's Drafts folder only
        // after SMTP has accepted it. Failure here is safe to retry because state=SENT skips SMTP.
        val draftFolder = task.remoteFolder
        val draftUid = task.remoteUid
        if (!draftFolder.isNullOrBlank() && draftUid != null && draftUid > 0L) {
            withMailboxCredential(account) { credential ->
                imap.delete(
                    account = account,
                    provider = ProviderRegistry.byId(account.providerId),
                    secret = credential,
                    message = MessageEntity(
                        id = task.sourceMessageId ?: "draft:${task.id}",
                        accountId = task.accountId,
                        folderType = "DRAFTS",
                        remoteFolder = draftFolder,
                        remoteUid = draftUid,
                        internetMessageId = task.internetMessageId,
                        senderName = account.displayName,
                        senderAddress = account.email,
                        recipients = task.recipients,
                        cc = task.cc,
                        subject = task.subject,
                        preview = "",
                        bodyText = "",
                        bodyHtml = null,
                        receivedAt = task.updatedAt,
                        unread = false,
                        starred = false,
                        hasAttachments = false,
                    ),
                )
            }
        }

        runCatching { syncSpecialFolderInternal(account, "SENT") }
            .onFailure { error ->
                MailLog.w(MailLog.APP, "sent refresh deferred cause=${MailLog.causeSummary(error)}")
            }

        // Some providers accept APPEND but index the new UID after the command returns. The Sent
        // refresh above can already have discovered the remote row. Reconcile that row by the
        // stable Message-ID and remove both the local placeholder and worker task atomically, so a
        // delayed UID never leaves two visually identical Sent entries or triggers another APPEND.
        if (appended.remoteUid <= 0L) {
            val normalizedId = normalizeInternetMessageId(prepared.internetMessageId)
            val discoveredRemote = database.messageDao().remoteByInternetMessageId(
                accountId = account.id,
                folderType = "SENT",
                normalizedInternetMessageId = normalizedId,
            )
            if (discoveredRemote != null) {
                database.withTransaction {
                    val local = localBeforeReconcile
                    database.messageDao().upsert(
                        discoveredRemote.copy(
                            senderName = local?.senderName?.takeIf(String::isNotBlank)
                                ?: discoveredRemote.senderName,
                            senderAddress = local?.senderAddress?.takeIf(String::isNotBlank)
                                ?: discoveredRemote.senderAddress,
                            recipients = local?.recipients?.takeIf(String::isNotBlank)
                                ?: discoveredRemote.recipients,
                            cc = local?.cc?.takeIf(String::isNotBlank) ?: discoveredRemote.cc,
                            subject = local?.subject?.takeIf(String::isNotBlank)
                                ?: discoveredRemote.subject,
                            preview = local?.preview?.takeIf(String::isNotBlank)
                                ?: discoveredRemote.preview,
                            bodyText = local?.bodyText?.takeIf(String::isNotBlank)
                                ?: discoveredRemote.bodyText,
                            bodyHtml = local?.bodyHtml ?: discoveredRemote.bodyHtml,
                            bodyLoaded = local?.bodyLoaded == true || discoveredRemote.bodyLoaded,
                            bodyParserVersion = maxOf(
                                local?.bodyParserVersion ?: 0,
                                discoveredRemote.bodyParserVersion,
                            ),
                            receivedAt = local?.receivedAt ?: discoveredRemote.receivedAt,
                            unread = false,
                            hasAttachments = effectiveAttachments.isNotEmpty() || discoveredRemote.hasAttachments,
                            attachmentsJson = encodedAttachments.takeIf { effectiveAttachments.isNotEmpty() }
                                ?: discoveredRemote.attachmentsJson,
                            deliveryState = "REMOTE",
                        ),
                    )
                    database.messageDao().deleteById(placeholderId)
                    database.outboxDao().deleteSentByInternetMessageId(account.id, normalizedId)
                }
                MailLog.d(
                    MailLog.APP,
                    "sent placeholder reconciled after refresh account=${account.id} " +
                        "uid=${discoveredRemote.remoteUid} attachments=${effectiveAttachments.size}",
                )
            }
        }

        runCatching { syncSpecialFolderInternal(account, "DRAFTS") }
        if (appended.remoteUid > 0L) database.outboxDao().deleteById(task.id)
    }

    suspend fun saveDraft(
        accountId: String,
        recipients: String,
        cc: String,
        bcc: String,
        subject: String,
        body: String,
        attachmentUris: List<String> = emptyList(),
        existingTaskId: String? = null,
        sourceMessageId: String? = null,
    ): OutboxEntity {
        val now = System.currentTimeMillis()
        val previous = existingTaskId?.let { database.outboxDao().byId(it) }
        val source = sourceMessageId?.let { database.messageDao().byId(it) }
        val taskId = previous?.id ?: UUID.randomUUID().toString()
        val task = OutboxEntity(
            id = taskId,
            accountId = accountId,
            recipients = recipients,
            cc = cc,
            bcc = bcc,
            subject = subject,
            bodyText = body,
            attachmentsJson = JSONArray(attachmentUris.distinct()).toString(),
            internetMessageId = previous?.internetMessageId
                ?.takeIf(String::isNotBlank)
                ?: source?.internetMessageId
                    ?.takeIf(String::isNotBlank)
                ?: "<$taskId@bondmail.local>",
            state = "DRAFT",
            remoteFolder = previous?.remoteFolder ?: source?.remoteFolder,
            remoteUid = previous?.remoteUid ?: source?.remoteUid?.takeIf { it > 0L },
            sourceMessageId = sourceMessageId ?: previous?.sourceMessageId,
            retryCount = previous?.retryCount ?: 0,
            lastError = null,
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
        )
        database.withTransaction {
            database.outboxDao().upsert(task)
            if (source != null) database.messageDao().deleteById(source.id)
        }
        return task
    }

    suspend fun syncDraftTask(taskId: String) {
        val task = database.outboxDao().byId(taskId) ?: return
        if (task.state != "DRAFT") return
        val account = requireAccount(task.accountId)
        val attachmentMetadata = smtp.describeAttachments(decodeAttachmentUris(task.attachmentsJson))
        runCatching {
            val prepared = smtp.prepare(account, task)
            val appended = withMailboxCredential(account) { credential ->
                imap.appendPreparedMessage(
                    account = account,
                    provider = ProviderRegistry.byId(account.providerId),
                    secret = credential,
                    canonicalType = "DRAFTS",
                    raw = prepared.raw,
                    internetMessageId = prepared.internetMessageId,
                    replaceFolder = task.remoteFolder,
                    replaceUid = task.remoteUid,
                )
            }
            prepared to appended
        }.onSuccess { (prepared, appended) ->
            val now = System.currentTimeMillis()
            if (appended.remoteUid > 0L) {
                database.withTransaction {
                    database.messageDao().upsert(
                        MessageEntity(
                            id = "${account.id}:DRAFTS:${appended.remoteUid}",
                            accountId = account.id,
                            folderType = "DRAFTS",
                            remoteFolder = appended.remoteFolder,
                            remoteUid = appended.remoteUid,
                            internetMessageId = prepared.internetMessageId,
                            senderName = account.displayName,
                            senderAddress = account.email,
                            recipients = task.recipients,
                            cc = task.cc,
                            subject = task.subject,
                            preview = MimeParser.preview(task.bodyText),
                            bodyText = task.bodyText,
                            bodyHtml = null,
                            bodyLoaded = true,
                            bodyParserVersion = MimeParser.CURRENT_VERSION,
                            receivedAt = now,
                            unread = false,
                            starred = false,
                            hasAttachments = attachmentMetadata.isNotEmpty(),
                            attachmentsJson = MailAttachmentCodec.encode(attachmentMetadata),
                            deliveryState = "REMOTE",
                        ),
                    )
                    database.outboxDao().deleteById(task.id)
                }
            } else {
                database.outboxDao().updateRemoteDraft(
                    id = task.id,
                    remoteFolder = appended.remoteFolder,
                    remoteUid = 0L,
                    updatedAt = now,
                )
            }
            runCatching { syncSpecialFolderInternal(account, "DRAFTS") }
        }.onFailure { error ->
            MailLog.w(
                MailLog.APP,
                "draft sync deferred task=$taskId cause=${MailLog.causeSummary(error)}",
            )
            throw error
        }
    }

    suspend fun discardDraft(message: MessageListRow) {
        val taskId = message.localTaskId
        if (!taskId.isNullOrBlank()) {
            val task = database.outboxDao().byId(taskId) ?: return
            database.outboxDao().deleteById(taskId)
            val remoteFolder = task.remoteFolder
            val remoteUid = task.remoteUid
            if (!remoteFolder.isNullOrBlank() && remoteUid != null && remoteUid > 0L) {
                val account = requireAccount(task.accountId)
                runCatching {
                    withMailboxCredential(account) { credential ->
                        imap.delete(
                            account = account,
                            provider = ProviderRegistry.byId(account.providerId),
                            secret = credential,
                            message = MessageEntity(
                                id = "draft:$taskId",
                                accountId = task.accountId,
                                folderType = "DRAFTS",
                                remoteFolder = remoteFolder,
                                remoteUid = remoteUid,
                                subject = task.subject,
                                preview = "",
                                bodyText = "",
                                bodyHtml = null,
                                receivedAt = task.updatedAt,
                                unread = false,
                                starred = false,
                                hasAttachments = false,
                                senderName = "",
                                senderAddress = "",
                            ),
                        )
                    }
                }
            }
        } else {
            deleteMessage(message.id)
        }
    }

    suspend fun discardDraft(taskId: String?, sourceMessageId: String?) {
        if (!taskId.isNullOrBlank()) {
            val task = database.outboxDao().byId(taskId) ?: return
            discardDraft(
                MessageListRow(
                    id = "draft:$taskId",
                    accountId = task.accountId,
                    folderType = "DRAFTS",
                    senderName = "",
                    senderAddress = "",
                    recipients = task.recipients,
                    subject = task.subject,
                    preview = "",
                    receivedAt = task.updatedAt,
                    unread = false,
                    starred = false,
                    deliveryState = "DRAFT",
                    localTaskId = taskId,
                ),
            )
        } else if (!sourceMessageId.isNullOrBlank()) {
            database.messageDao().byId(sourceMessageId)?.let { deleteMessage(it) }
        }
    }

    private fun decodeAttachmentUris(raw: String): List<String> = runCatching {
        val array = JSONArray(raw)
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private fun normalizeInternetMessageId(value: String?): String = value
        .orEmpty()
        .trim()
        .trim('<', '>')
        .replace(Regex("\\s+"), "")
        .lowercase()

    private fun mergeDraftRows(
        remote: List<MessageListRow>,
        local: List<OutboxEntity>,
    ): List<MessageListRow> {
        val localRows = local.map { draft ->
            MessageListRow(
                id = "draft:${draft.id}",
                accountId = draft.accountId,
                folderType = "DRAFTS",
                senderName = "",
                senderAddress = "",
                recipients = draft.recipients,
                subject = draft.subject,
                preview = MimeParser.preview(draft.bodyText),
                receivedAt = draft.updatedAt,
                unread = false,
                starred = false,
                deliveryState = "DRAFT",
                localTaskId = draft.id,
            )
        }
        return (localRows + remote)
            .sortedWith(compareByDescending<MessageListRow> { it.receivedAt }.thenBy { it.id })
    }

    suspend fun shouldNotify(message: MessageEntity): Boolean =
        !database.notificationStateDao().wasNotified(message.accountId, message.folderType, message.remoteUid)

    suspend fun markNotified(message: MessageEntity) {
        database.notificationStateDao().mark(
            NotificationStateEntity(message.accountId, message.folderType, message.remoteUid, System.currentTimeMillis())
        )
    }

    private suspend fun requireAccount(id: String) = database.accountDao().byId(id) ?: error("Account not found")

    private suspend fun requireCredential(account: AccountEntity): String =
        when (authType(account)) {
            AuthType.APP_PASSWORD -> credentials.read(account.id) ?: error("Credential unavailable")
            AuthType.OAUTH2 -> oauth.accessToken(account)
        }

    /**
     * Convert an IMAP/SMTP rejection of an otherwise acquired OAuth token into a reauthorization
     * state. JavaMail reports an expired/revoked token as AuthenticationFailedException, which is
     * different from the provider SDK telling us before connection that consent is required. The
     * UI should offer the same safe "sign in again" action for both cases, while app-password
     * accounts continue to show the normal credential error.
     */
    private suspend fun <T> withMailboxCredential(
        account: AccountEntity,
        operation: suspend (String) -> T,
    ): T {
        try {
            return operation(requireCredential(account))
        } catch (failure: Throwable) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            if (
                authType(account) == AuthType.OAUTH2 &&
                failure !is OAuthReauthorizationRequiredException &&
                isMailboxAuthenticationFailure(failure)
            ) {
                MailLog.w(
                    MailLog.OAUTH,
                    "mail protocol rejected OAuth token provider=${account.providerId} " +
                        "account=${MailLog.accountHint(account.email)}",
                )
                throw OAuthReauthorizationRequiredException(
                    "Mailbox authorization must be renewed",
                    failure,
                )
            }
            throw failure
        }
    }

    private fun authType(account: AccountEntity): AuthType =
        runCatching { AuthType.valueOf(account.authType) }.getOrDefault(AuthType.APP_PASSWORD)

    private fun isMailboxAuthenticationFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        val seen = hashSetOf<Throwable>()
        while (current != null && seen.add(current)) {
            if (current is AuthenticationFailedException) return true
            val message = current.message.orEmpty()
            if (
                message.contains("AUTHENTICATIONFAILED", ignoreCase = true) ||
                message.contains("authentication failed", ignoreCase = true) ||
                message.contains("invalid credentials", ignoreCase = true) ||
                message.contains("invalid_grant", ignoreCase = true) ||
                message.contains("XOAUTH2", ignoreCase = true) &&
                    message.contains("failed", ignoreCase = true)
            ) {
                return true
            }
            current = when (current) {
                is MessagingException -> current.nextException ?: current.cause
                else -> current.cause
            }
        }
        return false
    }

    fun failure(error: Throwable, endpoint: String? = null): UiFailure {
        val root = rootCause(error)
        return when {
            error is DuplicateAccountException || root is DuplicateAccountException -> UiFailure("error_duplicate_account")
            error is OAuthAccountMismatchException ->
                UiFailure("error_oauth_account_mismatch", listOf(error.expectedEmail))
            root is OAuthAccountMismatchException ->
                UiFailure("error_oauth_account_mismatch", listOf(root.expectedEmail))
            error is OAuthReauthorizationRequiredException || root is OAuthReauthorizationRequiredException ->
                UiFailure("error_oauth_reauthorization_required")
            root is AuthenticationFailedException || root.message.orEmpty().contains("authentication", true) -> UiFailure("error_authentication_failed")
            root is SocketTimeoutException || root.message.orEmpty().contains("timed out", true) || root.message.orEmpty().contains("timeout", true) ->
                UiFailure("error_connection_timeout", listOf(endpoint ?: "mail server"))
            root is UnknownHostException -> UiFailure("error_host_unreachable", listOf(endpoint ?: "mail server"))
            root is ConnectException -> UiFailure("error_host_unreachable", listOf(endpoint ?: "mail server"))
            error.message.orEmpty().contains("Invalid email", true) -> UiFailure("error_invalid_email")
            error.message.orEmpty().contains("Authorization code", true) -> UiFailure("error_authorization_required")
            error.message.orEmpty().contains("OAuth provider", true) -> UiFailure("error_oauth_required")
            error.message.orEmpty().contains("OAuth token", true) ||
                error.message.orEmpty().contains("OAuth account", true) -> UiFailure("error_oauth_failed")
            error.message.orEmpty().contains("Recipient is required", true) -> UiFailure("error_recipient_required")
            else -> UiFailure("error_connection_failed")
        }
    }

    private fun isSameOAuthProvider(existingProviderId: String, authorizedProviderId: String): Boolean {
        if (existingProviderId == authorizedProviderId) return true
        val microsoftProviders = setOf("outlook", "m365")
        return existingProviderId in microsoftProviders && authorizedProviderId in microsoftProviders
    }

    private fun rootCause(error: Throwable): Throwable {
        var current: Throwable = error
        val seen = hashSetOf<Throwable>()
        while (seen.add(current)) {
            val next = when (current) {
                is MessagingException -> current.nextException ?: current.cause
                else -> current.cause
            } ?: break
            current = next
        }
        return current
    }
}
