package com.bond.mail.ui

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bond.mail.AppContainer
import com.bond.mail.BuildConfig
import com.bond.mail.ExternalComposeRequest
import com.bond.mail.data.db.ACCOUNT_DISPLAY_NAME_MAX_LENGTH
import com.bond.mail.data.db.AccountEntity
import com.bond.mail.data.db.MessageEntity
import com.bond.mail.data.db.MessageListRow
import com.bond.mail.data.model.AuthType
import com.bond.mail.data.model.UiFailure
import com.bond.mail.data.model.visibleEmail
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.data.update.AppUpdateChecker
import com.bond.mail.data.update.AppUpdateCheckResult
import com.bond.mail.data.update.AppUpdateInfo
import com.bond.mail.data.update.AppUpdateInstaller
import com.bond.mail.data.update.UpdatePromptStore
import com.bond.mail.ui.components.AccountAvatar
import com.bond.mail.ui.components.FloatingCircleAction
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.motion.BondMotionDuration
import com.bond.mail.ui.motion.BondMotionEasing
import com.bond.mail.ui.motion.BondMotionSpring
import com.bond.mail.ui.motion.BondBackScreen
import com.bond.mail.ui.motion.animateChromeOffset
import com.bond.mail.ui.motion.TelegramMailTransition
import com.bond.mail.ui.motion.bondForwardEnter
import com.bond.mail.ui.motion.bondMotionEnabled
import com.bond.mail.ui.motion.bondNavigationSourceHold
import com.bond.mail.ui.motion.bondPressTransform
import com.bond.mail.ui.motion.bondTopLevelFade
import com.bond.mail.ui.motion.rememberBondPressInteraction
import com.bond.mail.ui.motion.rememberBondPressScale
import com.bond.mail.ui.theme.bondSurfaces
import com.bond.mail.ui.theme.BondAlertDialog as AlertDialog
import com.bond.mail.ui.theme.BondIconButton as IconButton
import com.bond.mail.ui.theme.BondMenuEntry
import com.bond.mail.ui.theme.BondPopupMenu
import com.bond.mail.ui.theme.BondPrimaryButton
import com.bond.mail.ui.theme.BondSecondaryButton
import com.bond.mail.ui.theme.BondTextAction
import com.bond.mail.ui.theme.BondTextField
import com.bond.mail.ui.screens.AccountCredentialsScreen
import com.bond.mail.ui.screens.AboutScreen
import com.bond.mail.ui.screens.AppLicenseScreen
import com.bond.mail.ui.screens.ComposeScreen
import com.bond.mail.ui.screens.ContactsScreen
import com.bond.mail.ui.screens.DetailScreen
import com.bond.mail.ui.screens.HomeScreen
import com.bond.mail.ui.screens.OpenSourceLicensesScreen
import com.bond.mail.ui.screens.PrivacyPolicyScreen
import com.bond.mail.ui.screens.ProviderPickerScreen
import com.bond.mail.ui.screens.PushSettingsScreen
import com.bond.mail.ui.screens.SettingsScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

private const val MAIN = "main"
private const val PROVIDERS = "providers"
private const val CREDENTIALS = "credentials/{providerId}"
private const val DETAIL = "detail/{messageId}"
private const val ABOUT = "about"
private const val PUSH_SETTINGS = "settings/push"
private const val OPEN_SOURCE_LICENSES = "about/open-source"
private const val APP_LICENSE = "about/app-license"
private const val PRIVACY_POLICY = "about/privacy"
private const val DETAIL_SNAPSHOT_LIMIT = 16
private const val COLLAPSED_ACCOUNT_LIMIT = 3

private fun parseStringArray(raw: String): List<String> = runCatching {
    val array = JSONArray(raw)
    buildList(array.length()) {
        repeat(array.length()) { index ->
            array.optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }
}.getOrDefault(emptyList())

private fun MessageEntity.withLatestListState(row: MessageListRow): MessageEntity = copy(
    accountId = row.accountId,
    folderType = row.folderType,
    senderName = row.senderName,
    senderAddress = row.senderAddress,
    recipients = row.recipients,
    subject = row.subject,
    preview = row.preview.ifBlank { preview },
    receivedAt = row.receivedAt,
    unread = row.unread,
    starred = row.starred,
    deliveryState = row.deliveryState,
)

@Composable
fun MailApp(
    container: AppContainer,
    initialMessageId: String?,
    externalComposeRequest: ExternalComposeRequest?,
    selectedMainTab: Int,
    onSelectedMainTabChange: (Int) -> Unit,
    onExternalComposeRequestConsumed: (Long) -> Unit,
    onFirstContentReady: () -> Unit = {},
) {
    val loadedSettings by container.settings.settings.collectAsState(initial = null)
    val settings = loadedSettings ?: AppSettings()
    val nav = rememberNavController()
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val hostView = LocalView.current
    val motionEnabled = bondMotionEnabled()
    val appScope = rememberCoroutineScope()
    val mailboxSnapshotLayer = rememberGraphicsLayer()
    val providersSnapshotLayer = rememberGraphicsLayer()
    val aboutSnapshotLayer = rememberGraphicsLayer()
    val updateChecker = remember { AppUpdateChecker() }
    val updatePromptStore = remember(context) { UpdatePromptStore(context.applicationContext) }
    val updateInstaller = remember(context) { AppUpdateInstaller(context.applicationContext) }
    var availableUpdate by remember { mutableStateOf(updatePromptStore.pendingUpdate()) }
    var updateAvailableDot by remember { mutableStateOf(updatePromptStore.hasPendingUpdate()) }
    var updateChecking by remember { mutableStateOf(false) }
    val alreadyLatestVersionMessage = tr("already_latest_version")
    val updateCheckFailedMessage = tr("update_check_failed")
    val updateDownloadingMessage = tr("update_downloading")
    // SplashScreen's keep condition cancels the host view's pre-draw, so waiting for a completed
    // draw would deadlock until the Activity safety timeout. Layout does run underneath the splash:
    // release it only after MAIN has a real full-size layout, then the exit listener keeps the
    // overlay for the following display frame while that already-laid-out content is painted.
    val firstContentReadyPosted = remember { AtomicBoolean(false) }

    val homeVm: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = viewModelFactory { HomeViewModel(container) },
    )
    val settingsVm: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = viewModelFactory { SettingsViewModel(container) },
    )
    // Keep the composer warm. The sheet can then be shown without constructing its ViewModel
    // on the first touch, which avoids a visible first-open hitch.
    val composeVm: ComposeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = viewModelFactory { ComposeViewModel(container) },
    )
    val accounts by homeVm.accounts.collectAsState()

    var composeTo by rememberSaveable { mutableStateOf("") }
    var composeCc by rememberSaveable { mutableStateOf("") }
    var composeBcc by rememberSaveable { mutableStateOf("") }
    var composeSubject by rememberSaveable { mutableStateOf("") }
    var composeBody by rememberSaveable { mutableStateOf("") }
    var composeAccountId by rememberSaveable { mutableStateOf("") }
    var composeAttachmentUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var composeDraftTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var composeSourceMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    var composeVisible by rememberSaveable { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<MessageListRow?>(null) }
    var providersBackBackground by remember {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    var credentialsBackBackground by remember {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    var aboutBackBackground by remember {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    var pushSettingsBackBackground by remember {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    var aboutChildBackBackground by remember {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    // MainTabs leaves composition while the reader is open. Keep the list chrome state one level
    // above NavHost so returning to a scrolled mailbox never draws a visible toolbar/dock for one
    // frame before its restored LazyList state hides them again.
    var mainChromeVisible by remember { mutableStateOf(true) }
    var snapshotNavigationInFlight by remember { mutableStateOf(false) }
    // Navigation can compose the destination in the same frame as the click callback. Keep a
    // synchronous, non-state snapshot so the first detail frame always has subject/sender data
    // instead of briefly rendering an empty page while Room emits the full entity.
    val detailInitialSnapshots = remember { LinkedHashMap<String, MessageEntity>() }
    val detailOpenSeenRequests = remember { LinkedHashMap<String, Boolean>() }

    fun rememberDetailSnapshot(snapshot: MessageEntity) {
        detailInitialSnapshots.remove(snapshot.id)
        detailInitialSnapshots[snapshot.id] = snapshot
        while (detailInitialSnapshots.size > DETAIL_SNAPSHOT_LIMIT) {
            val oldest = detailInitialSnapshots.keys.firstOrNull() ?: break
            detailInitialSnapshots.remove(oldest)
            detailOpenSeenRequests.remove(oldest)
        }
    }

    fun navigateOnce(route: String) {
        nav.navigate(route) { launchSingleTop = true }
    }

    fun navigateAfterSnapshot(
        route: String,
        capture: suspend () -> androidx.compose.ui.graphics.ImageBitmap,
        store: (androidx.compose.ui.graphics.ImageBitmap?) -> Unit,
    ) {
        if (snapshotNavigationInFlight) return
        snapshotNavigationInFlight = true
        appScope.launch {
            try {
                val snapshot = try {
                    capture()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    null
                }
                store(snapshot)
                // The forward transition now keeps this source destination alive while the new
                // page covers it, so its release/ripple finishes normally after this copy.
                navigateOnce(route)
                withFrameNanos { }
            } finally {
                snapshotNavigationInFlight = false
            }
        }
    }

    fun popBackStackOnce() {
        nav.popBackStack()
    }

    fun prepareCompose(email: String = "") {
        composeAccountId = homeVm.selectedAccount.value.orEmpty()
        composeTo = email
        composeCc = ""
        composeBcc = ""
        composeSubject = ""
        composeBody = ""
        composeAttachmentUris = emptyList()
        composeDraftTaskId = null
        composeSourceMessageId = null
        composeVisible = true
    }

    fun openDraft(message: MessageListRow) {
        appScope.launch {
            val localTask = message.localTaskId?.let { container.repository.draftNow(it) }
            if (localTask != null) {
                composeAccountId = localTask.accountId
                composeTo = localTask.recipients
                composeCc = localTask.cc
                composeBcc = localTask.bcc
                composeSubject = localTask.subject
                composeBody = localTask.bodyText
                composeAttachmentUris = parseStringArray(localTask.attachmentsJson)
                composeDraftTaskId = localTask.id
                composeSourceMessageId = localTask.sourceMessageId
                composeVisible = true
            } else {
                // Open the editor immediately with the cached header, then replace the body fields
                // when IMAP finishes. Remote drafts should feel like local drafts even on a slow
                // provider instead of leaving the user on the folder screen for several seconds.
                composeAccountId = message.accountId
                composeTo = message.recipients
                composeCc = ""
                composeBcc = ""
                composeSubject = message.subject
                composeBody = message.preview
                composeAttachmentUris = emptyList()
                composeDraftTaskId = null
                composeSourceMessageId = message.id
                composeVisible = true
                val remote = container.repository.ensureBodyLoaded(
                    messageId = message.id,
                    markSeen = false,
                    priority = true,
                ) ?: message.toInitialMessage()
                composeAccountId = remote.accountId
                composeTo = remote.recipients
                composeCc = remote.cc
                composeBcc = ""
                composeSubject = remote.subject
                composeBody = remote.bodyText
                composeAttachmentUris = emptyList()
                composeDraftTaskId = null
                composeSourceMessageId = remote.id
            }
        }
    }

    fun replyFromList(message: MessageListRow) {
        val replySubject = if (message.subject.startsWith("Re:", true)) {
            message.subject
        } else {
            "Re: ${message.subject}"
        }
        val initialBody = "\n\n---\n${message.preview}"
        composeAccountId = message.accountId
        composeTo = message.senderAddress
        composeCc = ""
        composeBcc = ""
        composeSubject = replySubject
        composeBody = initialBody
        composeAttachmentUris = emptyList()
        composeDraftTaskId = null
        composeSourceMessageId = null
        composeVisible = true
        appScope.launch {
            val loaded = container.repository.ensureBodyLoaded(
                messageId = message.id,
                markSeen = false,
                priority = true,
            ) ?: return@launch
            // Do not overwrite text the user has already started typing while the body loads.
            if (
                composeVisible &&
                composeAccountId == message.accountId &&
                composeTo == message.senderAddress &&
                composeSubject == replySubject &&
                composeBody == initialBody
            ) {
                composeBody = "\n\n---\n${loaded.bestForwardText()}"
            }
        }
    }

    fun forwardFromList(message: MessageListRow) {
        val forwardSubject = if (message.subject.startsWith("Fwd:", true)) {
            message.subject
        } else {
            "Fwd: ${message.subject}"
        }
        val initialBody = "\n\n--- Forwarded message ---\nFrom: ${message.senderAddress}\n" +
            "Subject: ${message.subject}\n\n${message.preview}"
        composeAccountId = message.accountId
        composeTo = ""
        composeCc = ""
        composeBcc = ""
        composeSubject = forwardSubject
        composeBody = initialBody
        composeAttachmentUris = emptyList()
        composeDraftTaskId = null
        composeSourceMessageId = null
        composeVisible = true
        appScope.launch {
            val loaded = container.repository.ensureBodyLoaded(
                messageId = message.id,
                markSeen = false,
                priority = true,
            ) ?: return@launch
            if (composeVisible && composeSubject == forwardSubject && composeBody == initialBody) {
                composeBody = "\n\n--- Forwarded message ---\nFrom: ${loaded.senderAddress}\n" +
                    "Subject: ${loaded.subject}\n\n${loaded.bestForwardText()}"
            }
        }
    }

    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    var notificationPermissionGranted by remember { mutableStateOf(hasNotificationPermission()) }
    var showPermissionGuide by rememberSaveable { mutableStateOf(false) }
    var previousAccountCount by rememberSaveable { mutableIntStateOf(accounts.size) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionGranted = granted || hasNotificationPermission()
        showPermissionGuide = false
        appScope.launch {
            container.settings.setNotificationPermissionPromptDismissed(true)
            container.settings.setNotifications(notificationPermissionGranted)
        }
    }

    LaunchedEffect(
        notificationPermissionGranted,
        settings.notificationPermissionPromptDismissed,
    ) {
        showPermissionGuide = !notificationPermissionGranted &&
            !settings.notificationPermissionPromptDismissed
    }

    DisposableEffect(lifecycle, homeVm, container) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            container.setAppForeground(true)
            homeVm.onAppForegrounded()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    container.setAppForeground(true)
                    homeVm.onAppForegrounded()
                    updateInstaller.installIfReady()
                    val grantedNow = hasNotificationPermission()
                    notificationPermissionGranted = grantedNow
                    if (grantedNow) {
                        showPermissionGuide = false
                        appScope.launch { container.settings.setNotifications(true) }
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    container.setAppForeground(false)
                    homeVm.onAppBackgrounded()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            container.setAppForeground(false)
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationPermissionGranted = true
            showPermissionGuide = false
            appScope.launch {
                container.settings.setNotificationPermissionPromptDismissed(true)
                container.settings.setNotifications(true)
            }
        }
    }

    fun rejectNotificationPermissionGuide() {
        showPermissionGuide = false
        appScope.launch {
            container.settings.setNotificationPermissionPromptDismissed(true)
            container.settings.setNotifications(false)
        }
    }

    fun openNotificationSettings() {
        val notificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        runCatching { context.startActivity(notificationIntent) }
            .onFailure {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
    }

    LaunchedEffect(accounts.size) {
        if (
            accounts.size > previousAccountCount &&
            !notificationPermissionGranted &&
            !settings.notificationPermissionPromptDismissed
        ) {
            showPermissionGuide = true
        }
        previousAccountCount = accounts.size
    }

    LaunchedEffect(
        loadedSettings,
        notificationPermissionGranted,
    ) {
        val currentSettings = loadedSettings ?: return@LaunchedEffect
        // Keep the server-side FCM interval and the legal WorkManager fallback in sync.
        container.scheduler.scheduleBackgroundSync(
            enabled = true,
            intervalMinutes = currentSettings.syncMinutes,
        )
    }

    LaunchedEffect(initialMessageId) {
        if (!initialMessageId.isNullOrBlank()) {
            // A notification can replace a reader that was opened from the list. Never let that
            // deep link inherit the previous reader's list snapshot.
            selectedMessage = null
            navigateOnce("detail/${Uri.encode(initialMessageId)}")
        }
    }

    LaunchedEffect(externalComposeRequest?.requestId) {
        val request = externalComposeRequest ?: return@LaunchedEffect
        composeAccountId = homeVm.selectedAccount.value.orEmpty()
        composeTo = request.to
        composeCc = request.cc
        composeBcc = request.bcc
        composeSubject = request.subject
        composeBody = request.body
        composeAttachmentUris = request.attachmentUris
        composeDraftTaskId = null
        composeSourceMessageId = null
        composeVisible = true
        onExternalComposeRequestConsumed(request.requestId)
    }

    fun openBackgroundSettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    LaunchedEffect(settings.syncMinutes) {
        while (true) {
            delay(settings.syncMinutes.coerceAtLeast(1) * 60_000L)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) homeVm.refreshSilently()
        }
    }

    fun cancelUpdatePrompt(update: AppUpdateInfo) {
        updatePromptStore.cancel(update)
        updateAvailableDot = true
        availableUpdate = null
    }

    fun ignoreUpdatePrompt(update: AppUpdateInfo) {
        updatePromptStore.ignore(update)
        updateAvailableDot = false
        availableUpdate = null
    }

    fun checkForUpdates() {
        if (updateChecking) return
        updateChecking = true
        appScope.launch {
            when (val result = updateChecker.check()) {
                is AppUpdateCheckResult.Available -> availableUpdate = result.update
                AppUpdateCheckResult.UpToDate -> Toast.makeText(
                    context,
                    alreadyLatestVersionMessage,
                    Toast.LENGTH_SHORT,
                ).show()
                AppUpdateCheckResult.Failed -> Toast.makeText(
                    context,
                    updateCheckFailedMessage,
                    Toast.LENGTH_LONG,
                ).show()
            }
            updateChecking = false
        }
    }

    fun openUpdateDownload(update: AppUpdateInfo) {
        updatePromptStore.updateStarted()
        updateAvailableDot = false
        availableUpdate = null
        if (updateInstaller.start(update)) {
            Toast.makeText(context, updateDownloadingMessage, Toast.LENGTH_SHORT).show()
            appScope.launch { updateInstaller.awaitAndInstall() }
        } else {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releasePageUrl)))
            }
        }
    }

    fun openMailboxMessage(message: MessageListRow) {
        if (message.folderType == "DRAFTS" || message.deliveryState == "DRAFT") {
            openDraft(message)
            return
        }
        appScope.launch {
            // Keep the list's read state live underneath the reader before starting its cover
            // motion. MainTabs now remains composed while detail is open, so later sync results and
            // refresh motion are also visible during a predictive-back preview.
            homeVm.openMessage(message)
            withFrameNanos { }
            val openedMessage = if (message.unread) message.copy(unread = false) else message
            val initialSnapshot = detailInitialSnapshots[message.id]
                ?.withLatestListState(openedMessage)
                ?: openedMessage.toInitialMessage()
            rememberDetailSnapshot(initialSnapshot)
            detailOpenSeenRequests[message.id] = message.unread
            selectedMessage = openedMessage
            navigateOnce("detail/${Uri.encode(message.id)}")
        }
    }

    @Composable
    fun MailboxLayer() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    if (
                        coordinates.size.width > 0 &&
                        coordinates.size.height > 0 &&
                        firstContentReadyPosted.compareAndSet(false, true)
                    ) {
                        hostView.post { onFirstContentReady() }
                    }
                }
                .drawWithContent {
                    mailboxSnapshotLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(mailboxSnapshotLayer)
                },
        ) {
            MainTabs(
                container = container,
                homeVm = homeVm,
                settingsVm = settingsVm,
                settings = settings,
                selectedTab = selectedMainTab,
                onSelectedTabChange = onSelectedMainTabChange,
                notificationPermissionGranted = notificationPermissionGranted,
                showPermissionGuide = showPermissionGuide,
                onRequestNotificationPermission = ::requestNotificationPermission,
                onDismissPermissionGuide = ::rejectNotificationPermissionGuide,
                onOpenNotificationSettings = ::openNotificationSettings,
                onOpenBackgroundSettings = ::openBackgroundSettings,
                onOpenAbout = {
                    providersBackBackground = null
                    credentialsBackBackground = null
                    pushSettingsBackBackground = null
                    aboutChildBackBackground = null
                    navigateAfterSnapshot(
                        route = ABOUT,
                        capture = { mailboxSnapshotLayer.toImageBitmap() },
                        store = { aboutBackBackground = it },
                    )
                },
                onOpenPushSettings = {
                    providersBackBackground = null
                    credentialsBackBackground = null
                    aboutBackBackground = null
                    aboutChildBackBackground = null
                    navigateAfterSnapshot(
                        route = PUSH_SETTINGS,
                        capture = { mailboxSnapshotLayer.toImageBitmap() },
                        store = { pushSettingsBackBackground = it },
                    )
                },
                onAddAccount = {
                    aboutBackBackground = null
                    pushSettingsBackBackground = null
                    aboutChildBackBackground = null
                    credentialsBackBackground = null
                    navigateAfterSnapshot(
                        route = PROVIDERS,
                        capture = { mailboxSnapshotLayer.toImageBitmap() },
                        store = { providersBackBackground = it },
                    )
                },
                mainChromeVisible = mainChromeVisible,
                onMainChromeVisibilityChanged = { mainChromeVisible = it },
                onOpenMessage = ::openMailboxMessage,
                onReplyMessage = ::replyFromList,
                onForwardMessage = ::forwardFromList,
                onCompose = ::prepareCompose,
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.bondSurfaces.page,
    ) {
        Box(Modifier.fillMaxSize()) {
                // Keep the mailbox alive below every destination. In particular, predictive back
                // from a reader must reveal current Room/refresh state rather than an old bitmap.
                MailboxLayer()
                NavHost(
                    navController = nav,
                    startDestination = MAIN,
                    enterTransition = {
                        // Mail detail owns its content-ready opening transform. All other secondary
                        // destinations use the same right-edge cover motion without moving the
                        // source page underneath.
                        if (targetState.destination.route == DETAIL) {
                            androidx.compose.animation.EnterTransition.None
                        } else {
                            bondForwardEnter(enabled = motionEnabled)
                        }
                    },
                    exitTransition = { bondNavigationSourceHold(enabled = motionEnabled) },
                    popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                    popExitTransition = { androidx.compose.animation.ExitTransition.None },
                ) {
                    composable(
                        route = MAIN,
                    ) {
                        // The mailbox is the persistent layer below NavHost. Keeping this
                        // destination intentionally empty avoids tearing it down for a reader and
                        // lets its refresh/new-mail state remain live during predictive back.
                    }

                    composable(
                        route = PROVIDERS,
                    ) {
                        BondBackScreen(
                            backgroundSnapshot = providersBackBackground,
                            motionEnabled = motionEnabled,
                            onBackCommitted = ::popBackStackOnce,
                        ) { requestBack ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawWithContent {
                                        providersSnapshotLayer.record {
                                            this@drawWithContent.drawContent()
                                        }
                                        drawLayer(providersSnapshotLayer)
                                    },
                            ) {
                                ProviderPickerScreen(
                                    onBack = requestBack,
                                    onProviderSelected = { providerId ->
                                        navigateAfterSnapshot(
                                            route = "credentials/${Uri.encode(providerId)}",
                                            capture = { providersSnapshotLayer.toImageBitmap() },
                                            store = { credentialsBackBackground = it },
                                        )
                                    },
                                )
                            }
                        }
                    }

                    composable(
                        route = CREDENTIALS,
                        arguments = listOf(navArgument("providerId") { type = NavType.StringType }),
                    ) { entry ->
                        val providerId = Uri.decode(entry.arguments?.getString("providerId").orEmpty())
                        val vm: AddAccountViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                            key = "add-$providerId",
                            factory = viewModelFactory { AddAccountViewModel(container, providerId) },
                        )
                        BondBackScreen(
                            backgroundSnapshot = credentialsBackBackground,
                            motionEnabled = motionEnabled,
                            onBackCommitted = ::popBackStackOnce,
                        ) { requestBack ->
                            AccountCredentialsScreen(
                                viewModel = vm,
                                onBack = requestBack,
                                onSaved = { accountId ->
                                    homeVm.selectMailbox(accountId, "INBOX")
                                    nav.popBackStack(MAIN, false)
                                    homeVm.refreshAccount(accountId, initialDelayMs = 800L)
                                    showPermissionGuide = !notificationPermissionGranted &&
                                        !settings.notificationPermissionPromptDismissed
                                },
                            )
                        }
                    }

                    composable(
                        route = PUSH_SETTINGS,
                    ) {
                        BondBackScreen(
                            backgroundSnapshot = pushSettingsBackBackground,
                            motionEnabled = motionEnabled,
                            onBackCommitted = ::popBackStackOnce,
                        ) { requestBack ->
                            PushSettingsScreen(
                                viewModel = settingsVm,
                                onBack = requestBack,
                            )
                        }
                    }

                    composable(
                        route = ABOUT,
                    ) {
                        BondBackScreen(
                            backgroundSnapshot = aboutBackBackground,
                            motionEnabled = motionEnabled,
                            onBackCommitted = ::popBackStackOnce,
                        ) { requestBack ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawWithContent {
                                        aboutSnapshotLayer.record {
                                            this@drawWithContent.drawContent()
                                        }
                                        drawLayer(aboutSnapshotLayer)
                                    },
                            ) {
                                AboutScreen(
                                    onBack = requestBack,
                                    updateAvailable = updateAvailableDot,
                                    updateChecking = updateChecking,
                                    onCheckForUpdates = ::checkForUpdates,
                                    onOpenSourceLicenses = {
                                        navigateAfterSnapshot(
                                            route = OPEN_SOURCE_LICENSES,
                                            capture = { aboutSnapshotLayer.toImageBitmap() },
                                            store = { aboutChildBackBackground = it },
                                        )
                                    },
                                    onOpenAppLicense = {
                                        navigateAfterSnapshot(
                                            route = APP_LICENSE,
                                            capture = { aboutSnapshotLayer.toImageBitmap() },
                                            store = { aboutChildBackBackground = it },
                                        )
                                    },
                                    onOpenPrivacyPolicy = {
                                        navigateAfterSnapshot(
                                            route = PRIVACY_POLICY,
                                            capture = { aboutSnapshotLayer.toImageBitmap() },
                                            store = { aboutChildBackBackground = it },
                                        )
                                    },
                                )
                            }
                        }
                    }

                    composable(
                        route = OPEN_SOURCE_LICENSES,
                    ) {
                        BondBackScreen(
                            backgroundSnapshot = aboutChildBackBackground,
                            motionEnabled = motionEnabled,
                            onBackCommitted = ::popBackStackOnce,
                        ) { requestBack ->
                            OpenSourceLicensesScreen(onBack = requestBack)
                        }
                    }

                    composable(
                        route = APP_LICENSE,
                    ) {
                        BondBackScreen(
                            backgroundSnapshot = aboutChildBackBackground,
                            motionEnabled = motionEnabled,
                            onBackCommitted = ::popBackStackOnce,
                        ) { requestBack ->
                            AppLicenseScreen(onBack = requestBack)
                        }
                    }

                    composable(
                        route = PRIVACY_POLICY,
                    ) {
                        BondBackScreen(
                            backgroundSnapshot = aboutChildBackBackground,
                            motionEnabled = motionEnabled,
                            onBackCommitted = ::popBackStackOnce,
                        ) { requestBack ->
                            PrivacyPolicyScreen(onBack = requestBack)
                        }
                    }

                    composable(
                        route = DETAIL,
                        arguments = listOf(navArgument("messageId") { type = NavType.StringType }),
                        enterTransition = { androidx.compose.animation.EnterTransition.None },
                        exitTransition = { androidx.compose.animation.ExitTransition.None },
                        popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                        popExitTransition = { androidx.compose.animation.ExitTransition.None },
                    ) { entry ->
                        val messageId = Uri.decode(entry.arguments?.getString("messageId").orEmpty())
                        // NavHost may retain destination state briefly after a pop. Key the whole
                        // reader transition by ID so no Animatable or frozen WebView frame can be
                        // restored for a different message.
                        key(messageId) {
                            TelegramMailTransition(
                                // The persistent MailboxLayer directly below this destination is
                                // the transition background; no GPU readback is needed before open.
                                backgroundSnapshot = null,
                                motionEnabled = motionEnabled,
                                onBackCommitted = ::popBackStackOnce,
                            ) { requestBack, reportContentReady ->
                                DetailScreen(
                                    container = container,
                                    messageId = messageId,
                                    initialMessage = detailInitialSnapshots[messageId]
                                        ?: selectedMessage
                                            ?.takeIf { it.id == messageId }
                                            ?.toInitialMessage(),
                                    markSeenOnOpen = detailOpenSeenRequests[messageId]
                                        ?: selectedMessage?.takeIf { it.id == messageId }?.unread
                                        ?: false,
                                    settings = settings,
                                    onFirstContentReady = reportContentReady,
                                    onMessageSnapshot = ::rememberDetailSnapshot,
                                    onBack = requestBack,
                                    onDelete = { message ->
                                        detailInitialSnapshots.remove(message.id)
                                        detailOpenSeenRequests.remove(message.id)
                                        if (selectedMessage?.id == message.id) selectedMessage = null
                                        homeVm.deleteFromDetail(message)
                                        requestBack()
                                    },
                                    onMoveSenderToSpam = { message ->
                                        detailInitialSnapshots.remove(message.id)
                                        detailOpenSeenRequests.remove(message.id)
                                        if (selectedMessage?.id == message.id) selectedMessage = null
                                        homeVm.moveSenderToSpamFromDetail(message)
                                    },
                                    onRestoreSenderFromSpam = { message ->
                                        detailInitialSnapshots.remove(message.id)
                                        detailOpenSeenRequests.remove(message.id)
                                        if (selectedMessage?.id == message.id) selectedMessage = null
                                        homeVm.restoreSenderFromSpamFromDetail(message)
                                    },
                                    onReply = { to, subject, body ->
                                        composeAccountId = detailInitialSnapshots[messageId]?.accountId
                                            ?: selectedMessage
                                                ?.takeIf { it.id == messageId }
                                                ?.accountId
                                                .orEmpty()
                                        composeTo = to
                                        composeCc = ""
                                        composeBcc = ""
                                        composeSubject = subject
                                        composeBody = body
                                        composeAttachmentUris = emptyList()
                                        composeDraftTaskId = null
                                        composeSourceMessageId = null
                                        composeVisible = true
                                    },
                                    onForward = { subject, body ->
                                        composeAccountId = detailInitialSnapshots[messageId]?.accountId
                                            ?: selectedMessage
                                                ?.takeIf { it.id == messageId }
                                                ?.accountId
                                                .orEmpty()
                                        composeTo = ""
                                        composeCc = ""
                                        composeBcc = ""
                                        composeSubject = subject
                                        composeBody = body
                                        composeAttachmentUris = emptyList()
                                        composeDraftTaskId = null
                                        composeSourceMessageId = null
                                        composeVisible = true
                                    },
                                )
                            }
                        }
                    }
                }

                if (composeVisible) {
                    ComposeScreen(
                        viewModel = composeVm,
                        initialAccountId = composeAccountId,
                        initialTo = composeTo,
                        initialCc = composeCc,
                        initialBcc = composeBcc,
                        initialSubject = composeSubject,
                        initialBody = composeBody,
                        initialAttachmentUris = composeAttachmentUris,
                        draftTaskId = composeDraftTaskId,
                        sourceMessageId = composeSourceMessageId,
                        onBack = { composeVisible = false },
                        onQueued = { composeVisible = false },
                    )
                }

                availableUpdate?.takeIf {
                    (currentRoute == MAIN || currentRoute == ABOUT) &&
                        !showPermissionGuide && !composeVisible
                }?.let { update ->
                    AlertDialog(
                        onDismissRequest = { cancelUpdatePrompt(update) },
                        title = { Text(tr("update_available_title")) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    tr(
                                        "update_available_body",
                                        BuildConfig.VERSION_NAME,
                                        update.version,
                                    ),
                                )
                                if (update.releaseNotes.isNotBlank()) {
                                    HorizontalDivider()
                                    Text(
                                        text = update.releaseNotes,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 260.dp)
                                            .verticalScroll(rememberScrollState()),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            BondTextAction(
                                text = tr("update_now"),
                                primary = true,
                                onClick = { openUpdateDownload(update) },
                            )
                        },
                        dismissButton = {
                            BondTextAction(
                                text = tr("update_cancel"),
                                onClick = { cancelUpdatePrompt(update) },
                            )
                        },
                        neutralButton = {
                            BondTextAction(
                                text = tr("ignore_update"),
                                onClick = { ignoreUpdatePrompt(update) },
                            )
                        },
                    )
                }
            }
        }
    }

@Composable
private fun MainTabs(
    container: AppContainer,
    homeVm: HomeViewModel,
    settingsVm: SettingsViewModel,
    settings: AppSettings,
    selectedTab: Int,
    onSelectedTabChange: (Int) -> Unit,
    notificationPermissionGranted: Boolean,
    showPermissionGuide: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onDismissPermissionGuide: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBackgroundSettings: () -> Unit,
    onOpenPushSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onAddAccount: () -> Unit,
    mainChromeVisible: Boolean,
    onMainChromeVisibilityChanged: (Boolean) -> Unit,
    onOpenMessage: (MessageListRow) -> Unit,
    onReplyMessage: (MessageListRow) -> Unit,
    onForwardMessage: (MessageListRow) -> Unit,
    onCompose: (String) -> Unit,
) {
    val accounts by homeVm.accounts.collectAsState()
    val selectedAccountId by homeVm.selectedAccount.collectAsState()
    val currentFolder by homeVm.folder.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val stateHolder = rememberSaveableStateHolder()
    val motionEnabled = bondMotionEnabled()

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val latestSelectedTab by rememberUpdatedState(selectedTab)
    val latestOnSelectedTabChange by rememberUpdatedState(onSelectedTabChange)
    val latestOnMainChromeVisibilityChanged by rememberUpdatedState(onMainChromeVisibilityChanged)
    val selectTab = remember {
        { target: Int ->
            if (target != latestSelectedTab) {
                latestOnSelectedTabChange(target)
                latestOnMainChromeVisibilityChanged(true)
            }
        }
    }

    BackHandler(enabled = !drawerState.isOpen && selectedTab != 0) {
        selectTab(0)
    }

    fun chooseMailbox(accountId: String?, folder: String) {
        homeVm.selectMailbox(accountId, folder)
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = selectedTab == 0,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.86f),
                drawerContainerColor = MaterialTheme.bondSurfaces.drawer,
            ) {
                MailDrawerContent(
                    accounts = accounts,
                    selectedAccountId = selectedAccountId,
                    currentFolder = currentFolder,
                    onChooseMailbox = ::chooseMailbox,
                    onAddAccount = {
                        scope.launch { drawerState.close() }
                        onAddAccount()
                    },
                    onSaveAccountSettings = { id, name, email, avatar, secret, onFinished ->
                        settingsVm.saveAccountSettings(id, name, email, avatar, secret) { failure ->
                            if (failure == null && secret.isNotBlank()) {
                                // Reconnect immediately with the newly verified app password so the
                                // repaired mailbox does not wait for the next periodic sync.
                                homeVm.refreshAccount(id, initialDelayMs = 250L, silent = true)
                            }
                            onFinished(failure)
                        }
                    },
                    onStartOAuthReauthorization = { id, name, activity, launchGoogle, onFinished ->
                        settingsVm.startOAuthReauthorization(
                            accountId = id,
                            displayName = name,
                            activity = activity,
                            launchGoogleResolution = launchGoogle,
                            onFinished = { success, failure ->
                                if (success) {
                                    // The provider token cache and the persisted OAuth identity are
                                    // now current. Refresh only this mailbox so the user immediately
                                    // sees that the repaired account can receive mail again.
                                    homeVm.refreshAccount(id, initialDelayMs = 250L, silent = true)
                                }
                                onFinished(success, failure)
                            },
                        )
                    },
                    onFinishGoogleOAuthReauthorization = { id, name, activity, data, onFinished ->
                        settingsVm.finishGoogleOAuthReauthorization(
                            accountId = id,
                            displayName = name,
                            activity = activity,
                            data = data,
                            onFinished = { success, failure ->
                                if (success) {
                                    homeVm.refreshAccount(id, initialDelayMs = 250L, silent = true)
                                }
                                onFinished(success, failure)
                            },
                        )
                    },
                    onDeleteAccount = { accountId ->
                        if (selectedAccountId == accountId) homeVm.selectedAccount.value = null
                        settingsVm.deleteAccount(accountId)
                    },
                    onReorderAccounts = { ids -> settingsVm.reorderAccounts(ids) },
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        selectTab(2)
                    },
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { clip = false },
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    bondTopLevelFade(enabled = motionEnabled)
                },
                contentKey = { it },
                label = "main-tab-fade",
            ) { tab ->
                stateHolder.SaveableStateProvider(tab) {
                    when (tab) {
                        0 -> HomeScreen(
                            viewModel = homeVm,
                            settings = settings,
                            notificationPermissionGranted = notificationPermissionGranted,
                            showPermissionGuide = showPermissionGuide,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onDismissPermissionGuide = onDismissPermissionGuide,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onAddAccount = onAddAccount,
                            onOpenMessage = onOpenMessage,
                            onReplyMessage = onReplyMessage,
                            onForwardMessage = onForwardMessage,
                            onCompose = { onCompose("") },
                            chromeVisible = mainChromeVisible,
                            onChromeVisibilityChanged = { visible ->
                                if (tab == selectedTab) onMainChromeVisibilityChanged(visible)
                            },
                            chromeControllerEnabled = tab == selectedTab,
                        )

                        1 -> ContactsScreen(
                            container = container,
                            settings = settings,
                            onCompose = onCompose,
                            chromeVisible = mainChromeVisible,
                            onChromeVisibilityChanged = { visible ->
                                if (tab == selectedTab) onMainChromeVisibilityChanged(visible)
                            },
                            chromeControllerEnabled = tab == selectedTab,
                        )

                        else -> SettingsScreen(
                            viewModel = settingsVm,
                            notificationPermissionGranted = notificationPermissionGranted,
                            onOpenNotificationSettings = onOpenNotificationSettings,
                            onOpenBackgroundSettings = onOpenBackgroundSettings,
                            onOpenPushSettings = onOpenPushSettings,
                            onOpenAbout = onOpenAbout,
                            chromeVisible = mainChromeVisible,
                            onChromeVisibilityChanged = { visible ->
                                if (tab == selectedTab) onMainChromeVisibilityChanged(visible)
                            },
                            chromeControllerEnabled = tab == selectedTab,
                        )
                    }
                }
            }

            val dockOffset = animateChromeOffset(
                visible = mainChromeVisible,
                hiddenOffset = 112.dp,
                label = "bottom-dock-slide",
            )
            FloatingBottomDock(
                selectedTab = selectedTab,
                onSelectTab = selectTab,
                onCompose = { onCompose("") },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { translationY = dockOffset.toPx() },
            )
        }
    }
}

@Composable
private fun FloatingBottomDock(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onCompose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.bondSurfaces.dock.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)),
            tonalElevation = 0.dp,
            // The border already separates the floating dock. A large physical shadow becomes a
            // bright rectangular band when the home screen is frozen under the mail transition.
            shadowElevation = 0.dp,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                val itemWidth = maxWidth / 3
                val indicatorWidth = itemWidth - 8.dp
                val indicatorX by animateDpAsState(
                    targetValue = itemWidth * selectedTab.toFloat() + 4.dp,
                    animationSpec = BondMotionSpring.NavigationIndicator,
                    label = "bottom-dock-indicator",
                )

                Surface(
                    modifier = Modifier
                        .offset { IntOffset(indicatorX.roundToPx(), 0) }
                        .width(indicatorWidth)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 1.dp,
                    shadowElevation = 1.dp,
                ) {}

                Row(modifier = Modifier.fillMaxWidth()) {
                    BottomDockItem(
                        selected = selectedTab == 0,
                        icon = Icons.Default.Email,
                        label = tr("mail"),
                        modifier = Modifier.weight(1f),
                    ) { onSelectTab(0) }
                    BottomDockItem(
                        selected = selectedTab == 1,
                        icon = Icons.Default.Contacts,
                        label = tr("contacts"),
                        modifier = Modifier.weight(1f),
                    ) { onSelectTab(1) }
                    BottomDockItem(
                        selected = selectedTab == 2,
                        icon = Icons.Default.Settings,
                        label = tr("settings"),
                        modifier = Modifier.weight(1f),
                    ) { onSelectTab(2) }
                }
            }
        }
        FloatingCircleAction(
            onClick = onCompose,
            modifier = Modifier.size(58.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Default.Create, contentDescription = tr("compose_mail"))
        }
    }
}

@Composable
private fun BottomDockItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val motionEnabled = bondMotionEnabled()
    val interactionSource = rememberBondPressInteraction()
    val pressScale by rememberBondPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.92f,
        enabled = motionEnabled,
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 4.dp)
            .height(48.dp)
            .bondPressTransform(pressScale),
        shape = RoundedCornerShape(24.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        interactionSource = interactionSource,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MailDrawerContent(
    accounts: List<AccountEntity>,
    selectedAccountId: String?,
    currentFolder: String,
    onChooseMailbox: (String?, String) -> Unit,
    onAddAccount: () -> Unit,
    onSaveAccountSettings: (String, String, String, String, String, (UiFailure?) -> Unit) -> Unit,
    onStartOAuthReauthorization: (
        String,
        String,
        Activity,
        (PendingIntent) -> Unit,
        (Boolean, UiFailure?) -> Unit,
    ) -> Unit,
    onFinishGoogleOAuthReauthorization: (
        String,
        String,
        Activity,
        Intent?,
        (Boolean, UiFailure?) -> Unit,
    ) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onReorderAccounts: (List<String>) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var accountsExpanded by rememberSaveable { mutableStateOf(false) }
    val drawerMotionEnabled = bondMotionEnabled()
    val orderedAccounts = remember { mutableStateListOf<AccountEntity>() }
    var editTarget by remember { mutableStateOf<AccountEntity?>(null) }
    var editDisplayName by remember { mutableStateOf("") }
    var editDisplayEmail by remember { mutableStateOf("") }
    var editAvatarText by remember { mutableStateOf("") }
    var replacementSecret by remember { mutableStateOf("") }
    var editBusy by remember { mutableStateOf(false) }
    var editFailure by remember { mutableStateOf<UiFailure?>(null) }
    var deleteTarget by remember { mutableStateOf<AccountEntity?>(null) }
    var pendingGoogleReauthorizationAccountId by remember { mutableStateOf<String?>(null) }
    var pendingGoogleReauthorizationDisplayName by remember { mutableStateOf("") }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun finishEditAuthorization(success: Boolean, failure: UiFailure?) {
        editBusy = false
        editFailure = failure
        pendingGoogleReauthorizationAccountId = null
        pendingGoogleReauthorizationDisplayName = ""
        if (success) editTarget = null
    }

    val googleReauthorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val accountId = pendingGoogleReauthorizationAccountId
        val host = activity
        if (
            accountId == null ||
            host == null ||
            result.resultCode != Activity.RESULT_OK ||
            result.data == null
        ) {
            finishEditAuthorization(success = false, failure = null)
            return@rememberLauncherForActivityResult
        }
        onFinishGoogleOAuthReauthorization(
            accountId,
            pendingGoogleReauthorizationDisplayName,
            host,
            result.data,
            ::finishEditAuthorization,
        )
    }

    LaunchedEffect(accounts) {
        val currentIds = orderedAccounts.map { it.id }
        val incomingIds = accounts.map { it.id }
        if (currentIds != incomingIds) {
            orderedAccounts.clear()
            orderedAccounts.addAll(accounts)
        } else {
            accounts.forEachIndexed { index, account -> orderedAccounts[index] = account }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(vertical = 14.dp),
    ) {
        DrawerAllMailboxesRow(
            selected = selectedAccountId == null && currentFolder == "INBOX",
            expanded = accountsExpanded,
            showExpand = orderedAccounts.size > COLLAPSED_ACCOUNT_LIMIT,
            onSelect = { onChooseMailbox(null, "INBOX") },
            onToggleExpanded = { accountsExpanded = !accountsExpanded },
        )

        Spacer(Modifier.height(8.dp))
        val maxReorderIndex = if (accountsExpanded) {
            orderedAccounts.lastIndex
        } else {
            minOf(COLLAPSED_ACCOUNT_LIMIT - 1, orderedAccounts.lastIndex)
        }
        @Composable
        fun DrawerAccountRow(account: AccountEntity) {
            DraggableAccountRow(
                account = account,
                selected = selectedAccountId == account.id && currentFolder == "INBOX",
                orderedAccounts = orderedAccounts,
                maxReorderIndex = maxReorderIndex,
                onSelect = { onChooseMailbox(account.id, "INBOX") },
                onEdit = {
                    editTarget = account
                    editDisplayName = account.displayName
                    editDisplayEmail = account.visibleEmail
                    editAvatarText = account.avatarText.orEmpty()
                    replacementSecret = ""
                    editBusy = false
                    editFailure = null
                    pendingGoogleReauthorizationAccountId = null
                    pendingGoogleReauthorizationDisplayName = ""
                },
                onDelete = { deleteTarget = account },
                onDragStarted = { accountsExpanded = true },
                onOrderCommitted = { onReorderAccounts(orderedAccounts.map { it.id }) },
            )
        }
        val expandedAccountEnter = if (drawerMotionEnabled) {
            expandVertically(
                animationSpec = tween(
                    durationMillis = BondMotionDuration.SharedAxis,
                    easing = BondMotionEasing.EmphasizedDecelerate,
                ),
                expandFrom = Alignment.Top,
            ) + slideInVertically(
                animationSpec = tween(
                    durationMillis = BondMotionDuration.ElementEnter,
                    easing = BondMotionEasing.EmphasizedDecelerate,
                ),
                initialOffsetY = { -minOf(it / 4, 28) },
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = BondMotionDuration.FadeThrough,
                    easing = BondMotionEasing.EmphasizedDecelerate,
                ),
            )
        } else {
            expandVertically(animationSpec = snap(), expandFrom = Alignment.Top) +
                fadeIn(animationSpec = snap())
        }
        val expandedAccountExit = if (drawerMotionEnabled) {
            shrinkVertically(
                animationSpec = tween(
                    durationMillis = BondMotionDuration.SharedAxis,
                    easing = BondMotionEasing.EmphasizedAccelerate,
                ),
                shrinkTowards = Alignment.Top,
            ) + slideOutVertically(
                animationSpec = tween(
                    durationMillis = BondMotionDuration.EffectShort,
                    easing = BondMotionEasing.EmphasizedAccelerate,
                ),
                targetOffsetY = { -minOf(it / 5, 24) },
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = BondMotionDuration.EffectShort,
                    easing = BondMotionEasing.EmphasizedAccelerate,
                ),
            )
        } else {
            shrinkVertically(animationSpec = snap(), shrinkTowards = Alignment.Top) +
                fadeOut(animationSpec = snap())
        }
        orderedAccounts.forEachIndexed { index, account ->
            key(account.id) {
                AnimatedVisibility(
                    visible = accountsExpanded || index < COLLAPSED_ACCOUNT_LIMIT,
                    enter = expandedAccountEnter,
                    exit = expandedAccountExit,
                ) {
                    DrawerAccountRow(account)
                }
            }
        }

        NavigationDrawerItem(
            label = { Text(tr("add_mailbox")) },
            selected = false,
            onClick = onAddAccount,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

        val folderItems = listOf(
            Triple("INBOX", tr("inbox"), Icons.Default.Inbox),
            Triple("UNREAD", tr("unread_mail"), Icons.Default.MarkEmailUnread),
            Triple("STARRED", tr("starred"), Icons.Default.Star),
            Triple("SENT", tr("sent"), Icons.AutoMirrored.Filled.Send),
            Triple("DRAFTS", tr("drafts"), Icons.Default.Drafts),
            Triple("SPAM", tr("spam"), Icons.Default.Report),
            Triple("TRASH", tr("trash"), Icons.Default.Delete),
        )
        folderItems.forEach { (folder, label, icon) ->
            NavigationDrawerItem(
                label = { Text(label) },
                selected = currentFolder == folder,
                onClick = { onChooseMailbox(selectedAccountId, folder) },
                icon = { Icon(icon, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
        NavigationDrawerItem(
            label = { Text(tr("settings")) },
            selected = false,
            onClick = onOpenSettings,
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }

    editTarget?.let { account ->
        val usesAppPassword = account.authType == AuthType.APP_PASSWORD.name
        val displayEmailValid = editDisplayEmail.trim().let { candidate ->
            candidate.length == account.email.trim().length &&
                candidate.equals(account.email.trim(), ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = {
                if (!editBusy) editTarget = null
            },
            title = { Text(tr("edit_mail_account")) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AccountAvatar(
                            account = account.copy(
                                avatarText = editAvatarText.trim().ifBlank { null },
                            ),
                            size = 48.dp,
                        )
                        BondTextField(
                            modifier = Modifier.weight(1f),
                            value = editAvatarText,
                            onValueChange = {
                                editAvatarText = it.take(16)
                                editFailure = null
                            },
                            enabled = !editBusy,
                            singleLine = true,
                            label = tr("account_avatar"),
                            placeholder = tr("avatar_placeholder"),
                            supportingText = tr("avatar_single_glyph_hint"),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        listOf("😀", "📮", "✉️", "⭐").forEach { emoji ->
                            IconButton(
                                onClick = {
                                    editAvatarText = emoji
                                    editFailure = null
                                },
                                enabled = !editBusy,
                            ) {
                                Text(emoji)
                            }
                        }
                    }
                    BondTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = editDisplayEmail,
                        onValueChange = {
                            editDisplayEmail = it
                            editFailure = null
                        },
                        enabled = !editBusy,
                        singleLine = true,
                        label = tr("display_email"),
                        isError = editDisplayEmail.isNotBlank() && !displayEmailValid,
                        supportingText = if (displayEmailValid) {
                            tr("display_email_case_hint")
                        } else {
                            tr("error_display_email_case_only")
                        },
                    )
                    BondTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = editDisplayName,
                        onValueChange = {
                            editDisplayName = it.take(ACCOUNT_DISPLAY_NAME_MAX_LENGTH)
                            editFailure = null
                        },
                        enabled = !editBusy,
                        singleLine = true,
                        label = tr("display_name"),
                        supportingText = "${editDisplayName.length}/$ACCOUNT_DISPLAY_NAME_MAX_LENGTH",
                    )
                    if (usesAppPassword) {
                        BondTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = replacementSecret,
                            onValueChange = {
                                replacementSecret = it
                                editFailure = null
                            },
                            enabled = !editBusy,
                            singleLine = true,
                            label = tr("replace_authorization_code"),
                            placeholder = tr("leave_blank_keep_current"),
                            supportingText = tr("authorization_code_update_note"),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus(force = true)
                                },
                            ),
                        )
                    } else {
                        Text(
                            tr("oauth_reauthorization_note"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BondSecondaryButton(
                            enabled = !editBusy && editDisplayName.isNotBlank(),
                            onClick = {
                                val host = activity
                                if (host == null) {
                                    editFailure = UiFailure("error_oauth_failed")
                                    return@BondSecondaryButton
                                }
                                editBusy = true
                                editFailure = null
                                onStartOAuthReauthorization(
                                    account.id,
                                    editDisplayName.trim(),
                                    host,
                                    { pendingIntent ->
                                        pendingGoogleReauthorizationAccountId = account.id
                                        pendingGoogleReauthorizationDisplayName = editDisplayName.trim()
                                        runCatching {
                                            googleReauthorizationLauncher.launch(
                                                IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                                            )
                                        }.onFailure {
                                            finishEditAuthorization(
                                                success = false,
                                                failure = UiFailure("error_oauth_failed"),
                                            )
                                        }
                                    },
                                    ::finishEditAuthorization,
                                )
                            },
                        ) {
                            if (editBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(tr("reauthorize_mailbox"))
                        }
                    }
                    editFailure?.let { failure ->
                        Text(
                            tr(failure.key, *failure.args.toTypedArray()),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                BondPrimaryButton(
                    enabled = !editBusy && editDisplayName.isNotBlank() && displayEmailValid,
                    onClick = {
                        editBusy = true
                        editFailure = null
                        onSaveAccountSettings(
                            account.id,
                            editDisplayName.trim(),
                            editDisplayEmail.trim(),
                            editAvatarText.trim(),
                            replacementSecret,
                        ) { failure ->
                            editBusy = false
                            editFailure = failure
                            if (failure == null) editTarget = null
                        }
                    },
                ) {
                    if (editBusy && usesAppPassword) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (replacementSecret.isBlank()) tr("save") else tr("verify_and_save"))
                }
            },
            dismissButton = {
                BondTextAction(
                    text = tr("cancel"),
                    enabled = !editBusy,
                    onClick = { editTarget = null },
                )
            },
        )
    }

    deleteTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(tr("confirm_delete_account_title")) },
            text = { Text("${account.displayName}\n${tr("delete_account_local_only")}") },
            confirmButton = {
                BondTextAction(
                    text = tr("delete"),
                    destructive = true,
                    onClick = {
                        onDeleteAccount(account.id)
                        deleteTarget = null
                    },
                )
            },
            dismissButton = {
                BondTextAction(text = tr("cancel"), onClick = { deleteTarget = null })
            },
        )
    }
}

@Composable
private fun DrawerAllMailboxesRow(
    selected: Boolean,
    expanded: Boolean,
    showExpand: Boolean,
    onSelect: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    val motionEnabled = bondMotionEnabled()
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (motionEnabled) {
            tween(
                durationMillis = BondMotionDuration.SharedAxis,
                easing = BondMotionEasing.EmphasizedDecelerate,
            )
        } else {
            snap()
        },
        label = "drawer-mailbox-chevron",
    )
    Surface(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(26.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.AllInbox, contentDescription = null)
            Text(
                tr("all_mailboxes"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            )
            if (showExpand) {
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (expanded) tr("collapse_accounts") else tr("expand_accounts"),
                        modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            }
        }
    }
}

@Composable
private fun DraggableAccountRow(
    account: AccountEntity,
    selected: Boolean,
    orderedAccounts: MutableList<AccountEntity>,
    maxReorderIndex: Int,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDragStarted: () -> Unit,
    onOrderCommitted: () -> Unit,
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val rowHeightPx = with(density) { 74.dp.toPx() }
    var dragOffset by remember(account.id) { mutableFloatStateOf(0f) }
    var dragging by remember(account.id) { mutableStateOf(false) }
    var orderChanged by remember(account.id) { mutableStateOf(false) }
    var dragStartOrder by remember(account.id) { mutableStateOf<List<AccountEntity>?>(null) }
    var menuExpanded by remember(account.id) { mutableStateOf(false) }
    val currentMaxReorderIndex by rememberUpdatedState(maxReorderIndex)

    Surface(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .zIndex(if (dragging) 2f else 0f),
        shape = RoundedCornerShape(18.dp),
        // A solid primaryContainer made the selected row and the account avatar use effectively
        // the same blue in dark themes. A restrained primary wash keeps selection obvious while
        // preserving the avatar's circular silhouette and the drawer's visual hierarchy.
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        else MaterialTheme.bondSurfaces.section,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        } else {
            null
        },
        shadowElevation = if (dragging) 8.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .pointerInput(account.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            onDragStarted()
                            dragging = true
                            orderChanged = false
                            dragStartOrder = orderedAccounts.toList()
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd = {
                            dragOffset = 0f
                            if (orderChanged) onOrderCommitted()
                            dragging = false
                            orderChanged = false
                            dragStartOrder = null
                        },
                        onDragCancel = {
                            dragOffset = 0f
                            if (orderChanged) {
                                dragStartOrder?.let { originalOrder ->
                                    orderedAccounts.clear()
                                    orderedAccounts.addAll(originalOrder)
                                }
                            }
                            dragging = false
                            orderChanged = false
                            dragStartOrder = null
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount.y
                            var currentIndex = orderedAccounts.indexOfFirst { it.id == account.id }
                            if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                            while (
                                dragOffset > rowHeightPx / 2f &&
                                currentIndex < currentMaxReorderIndex.coerceAtMost(orderedAccounts.lastIndex)
                            ) {
                                val moved = orderedAccounts.removeAt(currentIndex)
                                orderedAccounts.add(currentIndex + 1, moved)
                                dragOffset -= rowHeightPx
                                currentIndex += 1
                                orderChanged = true
                            }
                            while (dragOffset < -rowHeightPx / 2f && currentIndex > 0) {
                                val moved = orderedAccounts.removeAt(currentIndex)
                                orderedAccounts.add(currentIndex - 1, moved)
                                dragOffset += rowHeightPx
                                currentIndex -= 1
                                orderChanged = true
                            }
                        },
                    )
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountAvatar(
                    account = account,
                    size = 42.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        account.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        account.visibleEmail,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            BondPopupMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                entries = listOf(
                    BondMenuEntry(
                        text = tr("edit_mail_account"),
                        icon = Icons.Default.Edit,
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    ),
                    BondMenuEntry(
                        text = tr("delete"),
                        icon = Icons.Default.Delete,
                        destructive = true,
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    ),
                ),
            ) {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = tr("more"),
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
    }
}


private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
