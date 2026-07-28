package com.bond.mail.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bond.mail.AppContainer
import com.bond.mail.NewMailNotificationMode
import com.bond.mail.data.auth.GoogleAuthorizationStep
import com.bond.mail.data.auth.OAuthAuthorizationCancelledException
import com.bond.mail.data.auth.OAuthGrant
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.bond.mail.data.db.MessageListRow
import com.bond.mail.data.model.AuthType
import com.bond.mail.data.model.ProviderRegistry
import com.bond.mail.data.model.UiFailure
import com.bond.mail.data.performance.UiPerformanceGate
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.data.settings.MailDensity
import com.bond.mail.data.settings.RemoteImagePolicy
import com.bond.mail.data.settings.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val accounts = container.repository.accounts.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        container.repository.startupAccountsSnapshot(),
    )
    val selectedAccount = MutableStateFlow<String?>(null)
    val folder = MutableStateFlow("INBOX")
    val searchQuery = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<UiFailure?>(null)
    val foregroundSession = MutableStateFlow(0L)
    private var refreshJob: Job? = null
    private var refreshingAccountId: String? = null
    private var folderSelectionJob: Job? = null
    private var stagedMailboxSelection: FolderSnapshotKey? = null
    private var refreshGeneration: Long = 0L
    private var activeRefreshGeneration: Long = 0L
    private var appForeground: Boolean = true
    private var recoveryPending: Boolean = false
    private var recoveryScheduledThisForeground: Boolean = false
    private var foregroundRecoveryJob: Job? = null
    // Room can emit an older unread row between the click and the asynchronous database write.
    // Keep an in-memory read overlay so returning immediately from detail never resurrects the
    // unread styling while the remote \Seen operation is still queued.
    private val optimisticReadIds = ConcurrentHashMap.newKeySet<String>()

    private fun applyOptimisticRead(rows: List<MessageListRow>): List<MessageListRow> =
        if (optimisticReadIds.isEmpty()) rows
        else rows.map { row ->
            if (row.id in optimisticReadIds && row.unread) row.copy(unread = false) else row
        }

    private data class FolderSnapshotKey(val accountId: String?, val folderType: String)

    private val startupInbox = container.repository.startupInboxSnapshot()
    private val folderSnapshots = mutableMapOf(
        FolderSnapshotKey(accountId = null, folderType = "INBOX") to startupInbox,
    )
    private val _messages = MutableStateFlow(startupInbox)
    val messages: StateFlow<List<MessageListRow>> = _messages
    val contentReady = MutableStateFlow(container.repository.hasStartupSnapshot())

    init {
        viewModelScope.launch {
            combine(selectedAccount, folder, searchQuery) { account, f, q -> Triple(account, f, q) }
                .collectLatest { (account, f, q) ->
                    if (q.isBlank()) {
                        val snapshotKey = FolderSnapshotKey(account, f)
                        val staged = stagedMailboxSelection
                        if (staged != null && snapshotKey != staged) {
                            // selectedAccount and folder are public StateFlows for simple UI binding.
                            // A staged target suppresses their one intermediate combine emission.
                            return@collectLatest
                        }
                        if (snapshotKey == staged) stagedMailboxSelection = null
                        val memorySnapshot = folderSnapshots[snapshotKey]
                        if (memorySnapshot != null) {
                            _messages.value = applyOptimisticRead(memorySnapshot)
                            contentReady.value = true
                        } else {
                            contentReady.value = false
                        }

                        // Room is the persistent cache. Keep one in-memory snapshot per folder too,
                        // so changing chips never produces a blank frame before the Flow emits.
                        val diskSnapshot = applyOptimisticRead(container.repository.messagesNow(account, f))
                        folderSnapshots[snapshotKey] = diskSnapshot
                        _messages.value = diskSnapshot
                        contentReady.value = true
                        container.repository.messages(account, f).collect { cached ->
                            val visibleRows = applyOptimisticRead(cached)
                            folderSnapshots[snapshotKey] = visibleRows
                            _messages.value = visibleRows
                            contentReady.value = true
                        }
                    } else {
                        contentReady.value = true
                        container.repository.search(account, q).collect { results ->
                            _messages.value = applyOptimisticRead(results)
                        }
                    }
                }
        }
        viewModelScope.launch {
            accounts.collect { list ->
                val selected = selectedAccount.value
                if (selected != null && list.none { it.id == selected }) {
                    selectedAccount.value = null
                }
                if (list.any { it.enabled && it.lastSyncAt == null }) {
                    scheduleForegroundRecovery(delayMs = 1_300L)
                }
            }
        }
        // Fill a tiny local body window only after the first interaction window. OEM refresh-rate
        // governors often drop to 30 Hz when MIME prefetch starts during the first scroll after
        // process restore, so this background work deliberately waits until the UI is settled.
        container.repository.scheduleBodyPrefetch(limit = 12, initialDelayMs = 1_800L)
    }

    /**
     * Opening a mail is itself a read action. Update every in-memory folder snapshot before
     * navigation so the list behind the predictive-back surface already reflects the read state,
     * then commit \Seen remotely and prime the body on the IO lane.
     */
    fun openMessage(message: MessageListRow) {
        if (message.unread) {
            optimisticReadIds += message.id
            val readRow = message.copy(unread = false)
            _messages.value = _messages.value.map { row -> if (row.id == message.id) readRow else row }
            folderSnapshots.keys.toList().forEach { key ->
                folderSnapshots[key] = folderSnapshots[key].orEmpty().map { row ->
                    if (row.id == message.id) readRow else row
                }
            }
        }
        // Install the repository's shared open-flight before navigation composes the destination
        // in the same frame. The detail screen then awaits this exact result rather than issuing a
        // second IMAP BODY request.
        viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try {
                container.repository.prepareMessageForOpen(
                    messageId = message.id,
                    markSeen = message.unread,
                )
            } finally {
                // Room already contains the optimistic local read state at this point. Drop
                // the overlay so a future genuine server-side mark-unread can be observed.
                optimisticReadIds -= message.id
            }
        }
    }

    /** Warm the current viewport after scrolling settles; never changes unread state. */
    fun prefetchVisibleBodies(messageIds: List<String>) {
        container.repository.scheduleVisibleBodyPrefetch(messageIds)
    }


    /**
     * Preload the target folder from memory/Room before changing the visible chip. This keeps either
     * cached rows or the stable empty state on screen instead of showing a transient blank canvas.
     */
    fun selectFolder(targetFolder: String) {
        selectMailbox(selectedAccount.value, targetFolder)
    }

    fun selectMailbox(accountId: String?, targetFolder: String) {
        if (
            accountId == selectedAccount.value &&
            targetFolder == folder.value &&
            searchQuery.value.isBlank()
        ) return
        folderSelectionJob?.cancel()
        folderSelectionJob = viewModelScope.launch {
            val snapshotKey = FolderSnapshotKey(accountId, targetFolder)
            val snapshot = runCatching {
                folderSnapshots[snapshotKey]
                    ?: applyOptimisticRead(container.repository.messagesNow(accountId, targetFolder)).also {
                        folderSnapshots[snapshotKey] = it
                    }
            }.getOrElse { failure ->
                error.value = container.repository.failure(failure)
                emptyList()
            }
            stagedMailboxSelection = snapshotKey
            _messages.value = snapshot
            contentReady.value = true
            searchQuery.value = ""
            selectedAccount.value = accountId
            folder.value = targetFolder
            if (targetFolder == "SENT" || targetFolder == "DRAFTS") {
                refreshAccount(accountId, initialDelayMs = 120L, silent = true, folderType = targetFolder)
            }
        }
    }

    /** Refresh the currently selected mailbox, or all mailboxes when "All" is selected. */
    fun refresh() = refreshAccount(selectedAccount.value, silent = false)

    /** Periodic foreground refresh keeps cached mail current without showing transient banners. */
    fun refreshSilently() = refreshAccount(selectedAccount.value, silent = true)

    /**
     * Called by the root lifecycle observer. Any visible refresh state belongs to the foreground
     * interaction only; a JavaMail socket may finish safely in the background but cannot later
     * publish a stale spinner or error banner into a new foreground session.
     */
    fun onAppBackgrounded() {
        if (!appForeground) return
        appForeground = false
        recoveryScheduledThisForeground = false
        foregroundRecoveryJob?.cancel()
        foregroundRecoveryJob = null
        if (refreshJob?.isActive == true) recoveryPending = true
        refreshGeneration += 1L
        foregroundSession.value += 1L
        busy.value = false
        error.value = null
    }

    fun onAppForegrounded() {
        appForeground = true
        recoveryScheduledThisForeground = false
        scheduleForegroundRecovery(delayMs = if (recoveryPending) 350L else 900L)
    }

    private fun scheduleForegroundRecovery(delayMs: Long) {
        if (!appForeground || recoveryScheduledThisForeground) return
        if (foregroundRecoveryJob?.isActive == true) return
        foregroundRecoveryJob = viewModelScope.launch {
            delay(delayMs)
            if (!appForeground || recoveryScheduledThisForeground) return@launch
            val unsynced = accounts.value.filter { it.enabled && it.lastSyncAt == null }
            val needsRecovery = recoveryPending || unsynced.isNotEmpty()
            if (!needsRecovery) return@launch

            recoveryScheduledThisForeground = true
            recoveryPending = false
            // The normal add-account flow starts its own refresh after navigation settles. Do not
            // duplicate that session; only take over when no current foreground generation exists.
            if (
                refreshJob?.isActive == true &&
                activeRefreshGeneration == refreshGeneration
            ) {
                return@launch
            }
            val targetAccount = when {
                unsynced.size == 1 -> unsynced.first().id
                unsynced.isNotEmpty() -> null
                else -> selectedAccount.value
            }
            refreshAccount(targetAccount, silent = true)
        }
    }

    /**
     * Refresh one mailbox without waiting behind unrelated accounts. A new account can therefore
     * populate immediately even when another provider is temporarily unreachable.
     */
    fun refreshAccount(
        accountId: String?,
        initialDelayMs: Long = 0L,
        silent: Boolean = false,
        folderType: String = folder.value,
    ) {
        if (
            refreshJob?.isActive == true &&
            refreshingAccountId == accountId &&
            activeRefreshGeneration == refreshGeneration
        ) return

        refreshJob?.cancel()
        val generation = refreshGeneration + 1L
        refreshGeneration = generation
        activeRefreshGeneration = generation
        refreshingAccountId = accountId
        val publishBusy = !silent && appForeground
        if (publishBusy) busy.value = true

        val job = viewModelScope.launch {
            try {
                if (initialDelayMs > 0L) delay(initialDelayMs)
                if (silent) {
                    UiPerformanceGate.awaitBackgroundWindow(
                        settleDelayMs = 1_500L,
                        maximumWaitMs = 20_000L,
                    )
                }
                withTimeout(60_000L) {
                    if (folderType == "SENT" || folderType == "DRAFTS") {
                        if (accountId == null) {
                            container.syncFolderAll(
                                folderType,
                                NewMailNotificationMode.CONSUME_SILENTLY,
                            )
                        } else {
                            container.syncFolder(
                                accountId,
                                folderType,
                                NewMailNotificationMode.CONSUME_SILENTLY,
                            )
                        }
                    } else if (accountId == null) {
                        container.syncAllAndNotify(NewMailNotificationMode.CONSUME_SILENTLY)
                    } else {
                        container.syncAccountAndNotify(
                            accountId,
                            NewMailNotificationMode.CONSUME_SILENTLY,
                        )
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                if (!silent && appForeground && generation == refreshGeneration) {
                    error.value = UiFailure("error_sync_timeout")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (!silent && appForeground && generation == refreshGeneration) {
                    val targetAccount = if (accountId != null) {
                        accounts.value.firstOrNull { it.id == accountId }
                    } else {
                        accounts.value.singleOrNull()
                    }
                    val endpoint = targetAccount
                        ?.let { account -> ProviderRegistry.byId(account.providerId) }
                        ?.let { provider -> "${provider.imapHost}:${provider.imapPort}" }
                    error.value = container.repository.failure(failure, endpoint)
                }
            } finally {
                if (activeRefreshGeneration == generation) {
                    if (!silent) busy.value = false
                    refreshingAccountId = null
                    refreshJob = null
                }
            }
        }
        refreshJob = job
    }

    fun toggleUnread(message: MessageListRow) = viewModelScope.launch {
        if (message.deliveryState != "REMOTE") return@launch
        if (message.unread) optimisticReadIds += message.id else optimisticReadIds -= message.id
        try {
            runCatching { container.repository.toggleUnread(message.id) }
                .onFailure { error.value = container.repository.failure(it) }
        } finally {
            optimisticReadIds -= message.id
        }
    }

    fun toggleStarred(message: MessageListRow) = viewModelScope.launch {
        if (message.deliveryState != "REMOTE") return@launch
        runCatching { container.repository.toggleStarred(message.id) }
            .onFailure { error.value = container.repository.failure(it) }
    }

    fun delete(message: MessageListRow) = viewModelScope.launch {
        optimisticReadIds -= message.id
        runCatching {
            if (message.folderType == "DRAFTS" || message.deliveryState == "DRAFT") {
                container.repository.discardDraft(message)
            } else {
                container.repository.deleteMessage(message.id)
            }
        }
            .onFailure { error.value = container.repository.failure(it) }
    }

    fun markRead(message: MessageListRow) = viewModelScope.launch {
        if (!message.unread) return@launch
        optimisticReadIds += message.id
        try {
            runCatching { container.repository.toggleUnread(message.id) }
                .onFailure { error.value = container.repository.failure(it) }
        } finally {
            optimisticReadIds -= message.id
        }
    }

    fun markAllRead(messages: List<MessageListRow>) = viewModelScope.launch {
        messages.filter { it.unread }.forEach { message ->
            optimisticReadIds += message.id
            try {
                runCatching { container.repository.toggleUnread(message.id) }
                    .onFailure { error.value = container.repository.failure(it) }
            } finally {
                optimisticReadIds -= message.id
            }
        }
    }

    fun markAllUnread(messages: List<MessageListRow>) = viewModelScope.launch {
        messages.filterNot { it.unread }.forEach { message ->
            runCatching { container.repository.toggleUnread(message.id) }
                .onFailure { error.value = container.repository.failure(it) }
        }
    }

    fun deleteMany(messages: List<MessageListRow>) = viewModelScope.launch {
        messages.forEach { message ->
            runCatching {
                if (message.folderType == "DRAFTS" || message.deliveryState == "DRAFT") {
                    container.repository.discardDraft(message)
                } else {
                    container.repository.deleteMessage(message.id)
                }
            }
                .onFailure { error.value = container.repository.failure(it) }
        }
    }

    fun clearError() {
        error.value = null
    }
}

class AddAccountViewModel(
    private val container: AppContainer,
    providerId: String,
) : ViewModel() {
    val selectedProvider = MutableStateFlow(ProviderRegistry.byId(providerId))
    val username = MutableStateFlow("")
    val suffix = MutableStateFlow(selectedProvider.value.suffixes.firstOrNull().orEmpty())
    val displayName = MutableStateFlow("")
    val secret = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<UiFailure?>(null)
    val savedAccountId = MutableStateFlow<String?>(null)
    val oauthConfiguration = MutableStateFlow(
        container.oauth.configurationInfo(selectedProvider.value.id),
    )
    val oauthConfigurationError = MutableStateFlow<UiFailure?>(
        oauthConfiguration.value.errorKey?.let(::UiFailure),
    )

    fun saveOAuthConfiguration(rawJson: String) {
        val providerId = selectedProvider.value.id
        runCatching {
            container.oauth.saveClientConfiguration(providerId, rawJson)
        }.onSuccess { info ->
            oauthConfiguration.value = info
            oauthConfigurationError.value = null
            error.value = null
        }.onFailure { failure ->
            val key = when {
                failure.message == "google_client_required" ->
                    "error_oauth_google_client_invalid"
                providerId == "outlook" || providerId == "m365" ->
                    "error_oauth_microsoft_json_invalid"
                else -> "error_oauth_config_invalid"
            }
            oauthConfiguration.value = container.oauth.configurationInfo(providerId)
            oauthConfigurationError.value = UiFailure(key)
            error.value = UiFailure(key)
        }
    }

    fun reportOAuthConfigurationReadFailure() {
        oauthConfigurationError.value = UiFailure("error_oauth_config_read")
    }

    fun pasteOrSetUsername(value: String) {
        val trimmed = value.trim()
        val provider = selectedProvider.value
        if (provider.suffixes.isEmpty()) {
            // Generic/custom providers accept the complete address in the single input field.
            username.value = trimmed
        } else if ('@' in trimmed) {
            username.value = trimmed.substringBefore('@')
            val found = trimmed.substringAfter('@').lowercase()
            if (provider.suffixes.contains(found)) suffix.value = found
        } else {
            username.value = trimmed
        }
    }

    fun save() = viewModelScope.launch {
        if (busy.value) return@launch
        val provider = selectedProvider.value
        if (provider.authType == AuthType.OAUTH2) {
            error.value = UiFailure("error_oauth_required")
            return@launch
        }
        val email = if (suffix.value.isBlank()) username.value.trim() else "${username.value.trim()}@${suffix.value}"
        busy.value = true
        error.value = null
        runCatching {
            container.repository.addAppPasswordAccount(provider.id, email, displayName.value, secret.value)
        }.onSuccess { account ->
            savedAccountId.value = account.id
        }.onFailure {
            val endpoint = "${provider.imapHost}:${provider.imapPort}"
            error.value = container.repository.failure(it, endpoint)
        }
        busy.value = false
    }

    /**
     * Start the provider-owned OAuth consent flow. Microsoft completes directly through MSAL;
     * Google may return a PendingIntent that the composable launches through Activity Result APIs.
     * Access tokens are only kept long enough to validate IMAP/SMTP and are never stored by
     * BondMail.
     */
    fun startOAuth(
        activity: Activity,
        launchGoogleResolution: (PendingIntent) -> Unit,
    ) = viewModelScope.launch {
        if (busy.value) return@launch
        val provider = selectedProvider.value
        if (provider.authType != AuthType.OAUTH2) return@launch
        if (!oauthConfiguration.value.configured) {
            error.value = UiFailure("error_oauth_config_required")
            return@launch
        }

        busy.value = true
        error.value = null
        var awaitingGoogleResolution = false
        try {
            when (provider.id) {
                "gmail" -> when (val step = container.oauth.beginGoogleAuthorization(activity)) {
                    is GoogleAuthorizationStep.Authorized -> saveOAuthGrant(step.grant)
                    is GoogleAuthorizationStep.RequiresResolution -> {
                        launchGoogleResolution(step.pendingIntent)
                        awaitingGoogleResolution = true
                        return@launch
                    }
                }

                "outlook", "m365" -> {
                    saveOAuthGrant(container.oauth.authorizeMicrosoft(activity, provider.id))
                }

                else -> error.value = UiFailure("error_oauth_failed")
            }
        } catch (_: OAuthAuthorizationCancelledException) {
            // User cancellation is not an application error. Restore the button quietly.
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            // Failures before an OAuthGrant exists belong to the provider sign-in/consent flow.
            val failureKey = if (
                provider.id == "gmail" &&
                failure is ApiException &&
                failure.statusCode == CommonStatusCodes.DEVELOPER_ERROR
            ) {
                "error_oauth_google_android_mismatch"
            } else {
                "error_oauth_failed"
            }
            error.value = UiFailure(failureKey)
            if (failureKey == "error_oauth_google_android_mismatch") {
                oauthConfigurationError.value = UiFailure(failureKey)
            }
        } finally {
            // A launched Google resolution owns the state until its ActivityResult callback.
            if (!awaitingGoogleResolution) busy.value = false
        }
    }

    /** Complete Google's consent PendingIntent after Activity Result returns. */
    fun finishGoogleOAuth(activity: Activity, data: Intent?) = viewModelScope.launch {
        if (selectedProvider.value.id != "gmail") {
            busy.value = false
            return@launch
        }
        if (data == null) {
            cancelOAuth()
            return@launch
        }

        busy.value = true
        error.value = null
        try {
            saveOAuthGrant(container.oauth.finishGoogleAuthorization(activity, data))
        } catch (_: OAuthAuthorizationCancelledException) {
            // Same behavior as pressing Back in the provider UI.
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            val failureKey = if (
                failure is ApiException &&
                failure.statusCode == CommonStatusCodes.DEVELOPER_ERROR
            ) {
                "error_oauth_google_android_mismatch"
            } else {
                "error_oauth_failed"
            }
            error.value = UiFailure(failureKey)
            if (failureKey == "error_oauth_google_android_mismatch") {
                oauthConfigurationError.value = UiFailure(failureKey)
            }
        } finally {
            busy.value = false
        }
    }

    fun cancelOAuth() {
        busy.value = false
        error.value = null
    }

    fun reportOAuthHostUnavailable() {
        busy.value = false
        error.value = UiFailure("error_oauth_failed")
    }

    fun clearError() {
        error.value = null
    }

    private suspend fun saveOAuthGrant(grant: OAuthGrant) {
        val provider = selectedProvider.value
        runCatching { container.repository.addOAuthAccount(grant) }
            .onSuccess { account -> savedAccountId.value = account.id }
            .onFailure { failure ->
                val endpoint = "${provider.imapHost}:${provider.imapPort}"
                error.value = container.repository.failure(failure, endpoint)
            }
    }

}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings = container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val accounts = container.repository.accounts.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        container.repository.startupAccountsSnapshot(),
    )
    fun theme(value: ThemeMode) = viewModelScope.launch { container.settings.setTheme(value) }
    fun dynamic(value: Boolean) = viewModelScope.launch {
        container.settings.setDynamic(value)
        container.settings.setMonetBrandIcons(value)
    }
    fun density(value: MailDensity) = viewModelScope.launch { container.settings.setDensity(value) }
    fun monetBrandIcons(value: Boolean) = viewModelScope.launch { container.settings.setMonetBrandIcons(value) }
    fun syncMinutes(value: Int) = viewModelScope.launch {
        container.settings.setSyncMinutes(value)
        container.scheduler.scheduleBackgroundSync(enabled = true, intervalMinutes = value)
    }
    fun notifications(value: Boolean) = viewModelScope.launch { container.settings.setNotifications(value) }
    fun biometric(value: Boolean) = viewModelScope.launch { container.settings.setBiometric(value) }
    fun remoteImages(value: RemoteImagePolicy) = viewModelScope.launch { container.settings.setRemoteImages(value) }
    fun language(value: String) = viewModelScope.launch { container.settings.setLanguage(value) }
    fun renameAccount(id: String, displayName: String) = viewModelScope.launch { container.repository.updateAccountDisplayName(id, displayName) }
    fun saveAccountSettings(
        id: String,
        displayName: String,
        replacementSecret: String,
        onFinished: (UiFailure?) -> Unit,
    ) = viewModelScope.launch {
        runCatching {
            if (replacementSecret.isNotBlank()) {
                container.repository.updateAppPassword(id, replacementSecret)
            }
            // Validate the replacement credential first. A mistyped authorization code should
            // not partially save the dialog by changing only the display name.
            container.repository.updateAccountDisplayName(id, displayName)
        }.onSuccess {
            onFinished(null)
        }.onFailure { error ->
            onFinished(container.repository.failure(error))
        }
    }

    /** Renew an OAuth mailbox in place while preserving all locally cached mail. */
    fun startOAuthReauthorization(
        accountId: String,
        displayName: String,
        activity: Activity,
        launchGoogleResolution: (PendingIntent) -> Unit,
        onFinished: (success: Boolean, failure: UiFailure?) -> Unit,
    ) = viewModelScope.launch {
        val account = accounts.value.firstOrNull { it.id == accountId }
        if (account == null) {
            onFinished(false, UiFailure("error_connection_failed"))
            return@launch
        }
        val provider = runCatching { ProviderRegistry.byId(account.providerId) }.getOrNull()
        if (provider?.authType != AuthType.OAUTH2) {
            onFinished(false, UiFailure("error_oauth_required"))
            return@launch
        }

        try {
            when (provider.id) {
                "gmail" -> when (
                    val step = container.oauth.beginGoogleAuthorization(
                        activity = activity,
                        accountEmail = account.email,
                    )
                ) {
                    is GoogleAuthorizationStep.Authorized -> {
                        saveOAuthReauthorization(account.id, displayName, step.grant)
                        onFinished(true, null)
                    }
                    is GoogleAuthorizationStep.RequiresResolution -> {
                        launchGoogleResolution(step.pendingIntent)
                    }
                }

                "outlook", "m365" -> {
                    val grant = container.oauth.authorizeMicrosoft(
                        activity = activity,
                        providerId = provider.id,
                        loginHint = account.email,
                        forceLogin = true,
                    )
                    saveOAuthReauthorization(account.id, displayName, grant)
                    onFinished(true, null)
                }

                else -> onFinished(false, UiFailure("error_oauth_failed"))
            }
        } catch (_: OAuthAuthorizationCancelledException) {
            onFinished(false, null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            val endpoint = "${provider.imapHost}:${provider.imapPort}"
            onFinished(false, container.repository.failure(failure, endpoint))
        }
    }

    fun finishGoogleOAuthReauthorization(
        accountId: String,
        displayName: String,
        activity: Activity,
        data: Intent?,
        onFinished: (success: Boolean, failure: UiFailure?) -> Unit,
    ) = viewModelScope.launch {
        if (data == null) {
            onFinished(false, null)
            return@launch
        }
        try {
            val grant = container.oauth.finishGoogleAuthorization(activity, data)
            saveOAuthReauthorization(accountId, displayName, grant)
            onFinished(true, null)
        } catch (_: OAuthAuthorizationCancelledException) {
            onFinished(false, null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            val account = accounts.value.firstOrNull { it.id == accountId }
            val provider = account?.let { runCatching { ProviderRegistry.byId(it.providerId) }.getOrNull() }
            val endpoint = provider?.let { "${it.imapHost}:${it.imapPort}" }
            onFinished(false, container.repository.failure(failure, endpoint))
        }
    }

    private suspend fun saveOAuthReauthorization(
        accountId: String,
        displayName: String,
        grant: OAuthGrant,
    ) {
        container.repository.reauthorizeOAuthAccount(accountId, grant)
        container.repository.updateAccountDisplayName(accountId, displayName)
    }
    fun reorderAccounts(ids: List<String>) = viewModelScope.launch { container.repository.reorderAccounts(ids) }
    fun deleteAccount(id: String) = viewModelScope.launch { container.repository.deleteAccount(id) }
}

class ComposeViewModel(private val container: AppContainer) : ViewModel() {
    val accounts = container.repository.accounts.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        container.repository.startupAccountsSnapshot(),
    )
    val sending = MutableStateFlow(false)
    val error = MutableStateFlow<UiFailure?>(null)
    val savedContacts = container.repository.savedContacts.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList(),
    )

    fun queue(
        accountId: String,
        to: String,
        cc: String,
        bcc: String,
        subject: String,
        body: String,
        attachmentUris: List<String>,
        draftTaskId: String? = null,
        sourceMessageId: String? = null,
        onQueued: () -> Unit,
    ) = viewModelScope.launch {
        if (sending.value) return@launch
        sending.value = true
        error.value = null
        runCatching {
            val resolvedTo = container.repository.resolveRecipients(to)
            val resolvedCc = container.repository.resolveRecipients(cc)
            val resolvedBcc = container.repository.resolveRecipients(bcc)
            val task = container.repository.queueSend(
                accountId = accountId,
                recipients = resolvedTo,
                cc = resolvedCc,
                bcc = resolvedBcc,
                subject = subject,
                body = body,
                attachmentUris = attachmentUris,
                draftTaskId = draftTaskId,
                sourceMessageId = sourceMessageId,
            )
            container.scheduler.send(task.id)
        }.onSuccess { onQueued() }
            .onFailure { error.value = container.repository.failure(it) }
        sending.value = false
    }

    fun saveDraft(
        accountId: String,
        to: String,
        cc: String,
        bcc: String,
        subject: String,
        body: String,
        attachmentUris: List<String>,
        existingTaskId: String?,
        sourceMessageId: String?,
        onSaved: () -> Unit,
    ) = viewModelScope.launch {
        if (sending.value || accountId.isBlank()) return@launch
        sending.value = true
        error.value = null
        runCatching {
            val resolvedTo = container.repository.resolveRecipients(to)
            val resolvedCc = container.repository.resolveRecipients(cc)
            val resolvedBcc = container.repository.resolveRecipients(bcc)
            val task = container.repository.saveDraft(
                accountId = accountId,
                recipients = resolvedTo,
                cc = resolvedCc,
                bcc = resolvedBcc,
                subject = subject,
                body = body,
                attachmentUris = attachmentUris,
                existingTaskId = existingTaskId,
                sourceMessageId = sourceMessageId,
            )
            container.scheduler.saveDraft(task.id)
        }.onSuccess { onSaved() }
            .onFailure { error.value = container.repository.failure(it) }
        sending.value = false
    }

    fun discardDraft(
        taskId: String?,
        sourceMessageId: String?,
        onDiscarded: () -> Unit,
    ) = viewModelScope.launch {
        runCatching { container.repository.discardDraft(taskId, sourceMessageId) }
            .onSuccess { onDiscarded() }
            .onFailure { error.value = container.repository.failure(it) }
    }
}

@Suppress("UNCHECKED_CAST")
fun <T : ViewModel> viewModelFactory(create: () -> T): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
}
