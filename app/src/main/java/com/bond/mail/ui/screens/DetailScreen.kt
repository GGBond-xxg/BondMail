package com.bond.mail.ui.screens

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.text.TextUtils
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.bond.mail.AppContainer
import com.bond.mail.data.db.ACCOUNT_DISPLAY_NAME_MAX_LENGTH
import com.bond.mail.data.db.MessageEntity
import com.bond.mail.data.model.visibleEmail
import com.bond.mail.data.mail.MailAttachmentCodec
import com.bond.mail.data.mail.MailAttachmentInfo
import com.bond.mail.data.mail.MailLog
import com.bond.mail.data.mail.MimeParser
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.data.settings.RemoteImagePolicy
import com.bond.mail.ui.components.FloatingCircleAction
import com.bond.mail.ui.components.ContactAvatar
import com.bond.mail.ui.components.brandAvatarPalette
import com.bond.mail.ui.components.contactAvatarText
import com.bond.mail.ui.components.MailContentHeightHint
import com.bond.mail.ui.components.MailWebHeader
import com.bond.mail.ui.components.contactLogoSvgMarkup
import com.bond.mail.ui.components.MailWebViewCache
import com.bond.mail.ui.components.MailWebViewPool
import com.bond.mail.ui.components.PreparedMailDocument
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.motion.BondMotionDuration
import com.bond.mail.ui.motion.BondMotionEasing
import com.bond.mail.ui.motion.animateChromeOffset
import com.bond.mail.ui.motion.bondMotionEnabled
import com.bond.mail.ui.theme.bondSurfaces
import com.bond.mail.ui.theme.BondPrimaryButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val MAIL_OPEN_READY_TIMEOUT_MS = 450L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    container: AppContainer,
    messageId: String,
    initialMessage: MessageEntity?,
    markSeenOnOpen: Boolean,
    settings: AppSettings,
    onFirstContentReady: () -> Unit = {},
    onMessageSnapshot: (MessageEntity) -> Unit,
    onBack: () -> Unit,
    onDelete: (MessageEntity) -> Unit,
    onReply: (String, String, String) -> Unit,
    onForward: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val storedMessage by container.repository.message(messageId).collectAsState(initial = initialMessage)
    // Room invalidation is asynchronous relative to the interactive BODY call. Keep the returned
    // entity in Compose state so the same frame that ends loading can already render the final HTML,
    // instead of briefly loading an empty plain-text document before Room emits the upsert.
    var immediateOpenResult by remember(messageId) { mutableStateOf<MessageEntity?>(null) }
    val accounts by container.repository.accounts.collectAsState(
        initial = container.repository.startupAccountsSnapshot(),
    )
    val savedContacts by container.repository.savedContacts.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnMessageSnapshot by rememberUpdatedState(onMessageSnapshot)
    var moreOpen by remember { mutableStateOf(false) }
    var externalUrl by remember { mutableStateOf<String?>(null) }
    var bodyLoading by remember(messageId) {
        mutableStateOf(initialMessage == null || initialMessage.needsBodyRefresh())
    }
    var bodyLoadFailed by remember(messageId) { mutableStateOf(false) }
    var bodyRetryToken by remember(messageId) { mutableStateOf(0) }
    var renderRetryToken by remember(messageId) { mutableStateOf(0) }
    var renderFailed by remember(messageId) { mutableStateOf(false) }
    var rendererRecoveryCount by remember(messageId) { mutableStateOf(0) }
    var firstContentReadyReported by remember(messageId) { mutableStateOf(false) }
    var activeMailWebView by remember(messageId) { mutableStateOf<WebView?>(null) }
    val mailContentScrollY = remember(messageId) { mutableIntStateOf(0) }
    val reportFirstContentReady = {
        if (!firstContentReadyReported) {
            firstContentReadyReported = true
            onFirstContentReady()
        }
    }
    LaunchedEffect(messageId) {
        // A cached local document normally commits well before this. If the body genuinely needs
        // the network, open the stable native loading sheet rather than leaving the tapped list
        // frozen indefinitely.
        delay(MAIL_OPEN_READY_TIMEOUT_MS)
        reportFirstContentReady()
    }
    // Keep only the top app bar fixed. The bottom action dock still follows scroll direction so it
    // gets out of the way while reading and returns when the user scrolls back.
    var bottomChromeVisible by remember(messageId) { mutableStateOf(true) }
    var confirmDelete by remember(messageId) { mutableStateOf(false) }
    // Use Chromium's native scrolling only. Returning zero keeps the existing WebView listener
    // compatible while disabling BondMail's custom rubber-band displacement.
    val updateTopPull: (Float) -> Float = { 0f }
    val releaseTopPull: () -> Unit = {}
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val detailTopBarHeight = statusBarInset + 64.dp
    val messageContentTopInset = detailTopBarHeight

    val item = remember(storedMessage, immediateOpenResult) {
        mergeImmediateOpenResult(
            stored = storedMessage,
            opened = immediateOpenResult,
        )
    }
    LaunchedEffect(
        messageId,
        item?.id,
        item?.hasDisplayBody(),
        bodyLoading,
    ) {
        if (item == null || (bodyLoading && !item.hasDisplayBody())) {
            // The native loading document is already meaningful destination content. Let it draw
            // once, then start the reader transition immediately instead of keeping the reader
            // off-screen for the generic WebView timeout and exposing an empty background frame.
            withFrameNanos { }
            reportFirstContentReady()
        }
    }
    if (item == null) {
        Scaffold(
            containerColor = MaterialTheme.bondSurfaces.page,
            topBar = {
                TopAppBar(
                    title = { Text(tr("loading")) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("back"))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.bondSurfaces.page,
                        scrolledContainerColor = MaterialTheme.bondSurfaces.chrome,
                    ),
                )
            },
        ) { padding -> Box(Modifier.fillMaxSize().padding(padding)) }
        return
    }

    val owningAccount = remember(accounts, item.accountId) {
        accounts.firstOrNull { account -> account.id == item.accountId }
    }
    val senderName = item.senderName.ifBlank { item.senderAddress }
    val noSubjectLabel = tr("no_subject")
    val attachmentLabel = tr("attachment")
    val detailAttachments = remember(item.attachmentsJson, item.hasAttachments, attachmentLabel) {
        MailAttachmentCodec.decode(item.attachmentsJson).ifEmpty {
            if (item.hasAttachments) listOf(MailAttachmentInfo(name = attachmentLabel)) else emptyList()
        }
    }
    val detailDateLabel = remember(item.receivedAt) { formatDetailMailTime(item.receivedAt) }
    val savedContact = remember(savedContacts, item.senderAddress) {
        savedContacts.firstOrNull { contact ->
            contact.email.equals(item.senderAddress.trim(), ignoreCase = true)
        }
    }
    val customContactAvatar = savedContact?.avatarText?.trim().takeUnless { it.isNullOrBlank() }
    val avatarSvg = remember(senderName, item.senderAddress, customContactAvatar) {
        if (customContactAvatar == null) {
            contactLogoSvgMarkup(context, senderName, item.senderAddress)
        } else {
            null
        }
    }
    val currentMailHeader = remember(
        item.subject,
        noSubjectLabel,
        senderName,
        item.senderAddress,
        owningAccount?.visibleEmail,
        item.recipients,
        detailDateLabel,
        customContactAvatar,
        avatarSvg,
        detailAttachments,
        settings.dynamicColor,
        settings.monetBrandIcons,
    ) {
        MailWebHeader(
            subject = item.subject.ifBlank { noSubjectLabel },
            senderName = senderName,
            senderAddress = item.senderAddress,
            recipient = owningAccount?.visibleEmail ?: item.recipients,
            dateLabel = detailDateLabel,
            avatarText = customContactAvatar ?: contactAvatarText(senderName, item.senderAddress),
            customAvatarText = customContactAvatar,
            avatarSvg = avatarSvg,
            monetBrandIcons = settings.monetBrandIcons,
            attachments = detailAttachments,
        )
    }
    // Freeze all visible title/sender text from the first destination frame. Room later replaces
    // the lightweight list projection with the full MIME entity; letting those equivalent strings
    // swap renderers during the HTML reveal is enough to produce a one-frame width/weight change.
    // Attachments may still arrive with the body and are kept separately below.
    val stableHeaderBase = remember(
        messageId,
        customContactAvatar,
        settings.dynamicColor,
        settings.monetBrandIcons,
    ) {
        currentMailHeader.copy(attachments = emptyList())
    }
    val mailHeader = remember(stableHeaderBase, detailAttachments) {
        stableHeaderBase.copy(attachments = detailAttachments)
    }
    val headerLayout = rememberMailHeaderLayout(mailHeader.subject)

    val previewCandidate = remember(item.preview, item.bodyText) {
        item.preview
            .ifBlank { item.bodyText }
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(420)
    }
    var previewText by remember(messageId) { mutableStateOf(previewCandidate) }
    LaunchedEffect(previewCandidate) {
        // Header-only Room rows can briefly contain an empty preview. Never replace the useful
        // list preview with that empty value while Chromium is still preparing the styled body.
        if (previewText.isBlank() && previewCandidate.isNotBlank()) {
            previewText = previewCandidate
        }
    }

    val stableItem: MessageEntity = item
    LaunchedEffect(
        stableItem.id,
        stableItem.bodyLoaded,
        stableItem.bodyParserVersion,
        stableItem.htmlContentHash,
        stableItem.bodyHtml?.hashCode(),
        stableItem.bodyText.hashCode(),
        stableItem.attachmentsJson,
        stableItem.unread,
        stableItem.starred,
        stableItem.remoteImageAllowed,
    ) {
        if (stableItem.hasDisplayBody()) {
            latestOnMessageSnapshot(stableItem)
        }
    }

    // A notification/deep-link can enter detail without the list-tap snapshot. Capture the first
    // Room unread state once, while still honoring the explicit list-tap request. Keeping this value
    // stable also prevents a later manual "mark unread" action from being undone by recomposition.
    val shouldMarkSeenOnOpen = remember(messageId) {
        markSeenOnOpen || stableItem.unread
    }
    var openSeenIntentSubmitted by remember(messageId) { mutableStateOf(false) }
    LaunchedEffect(
        stableItem.id,
        shouldMarkSeenOnOpen,
        bodyRetryToken,
    ) {
        renderFailed = false
        val needsBody = stableItem.needsBodyRefresh()
        bodyLoading = needsBody
        bodyLoadFailed = false
        val submitOpenSeenIntent = shouldMarkSeenOnOpen && !openSeenIntentSubmitted
        if (submitOpenSeenIntent) openSeenIntentSubmitted = true
        runCatching {
            container.repository.prepareMessageForOpen(
                messageId = stableItem.id,
                markSeen = submitOpenSeenIntent,
            )
        }.onSuccess { opened ->
            if (opened != null) {
                immediateOpenResult = opened
                // Save the complete MIME snapshot before this open coroutine can leave composition.
                // Reopening from the list can then use the full body immediately, even if Room's
                // invalidation or the follow-up LaunchedEffect has not run yet.
                if (opened.hasDisplayBody()) latestOnMessageSnapshot(opened)
            } else if (needsBody) {
                bodyLoadFailed = true
            }
        }.onFailure {
            if (needsBody) bodyLoadFailed = true
        }
        bodyLoading = false
    }

    val imagePolicyAllowsLoading = item.remoteImageAllowed || when (settings.remoteImagePolicy) {
        RemoteImagePolicy.ALWAYS -> true
        RemoteImagePolicy.WIFI_ONLY -> isWifiConnected(context)
        RemoteImagePolicy.NEVER -> false
    }
    var loadImages by remember(item.id) { mutableStateOf(imagePolicyAllowsLoading) }
    var hasRemoteImages by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(item.id, imagePolicyAllowsLoading) {
        if (imagePolicyAllowsLoading) loadImages = true
    }

    val shareLabel = tr("share")
    fun share() {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, item.subject)
            putExtra(
                Intent.EXTRA_TEXT,
                "${item.subject}\n\n${item.senderName} <${item.senderAddress}>\n\n${item.bodyText}",
            )
        }
        context.startActivity(Intent.createChooser(share, shareLabel))
    }

    fun reply() {
        onReply(
            item.senderAddress,
            if (item.subject.startsWith("Re:", true)) item.subject else "Re: ${item.subject}",
            "\n\n---\n${item.bodyText}",
        )
    }

    fun forward() {
        onForward(
            if (item.subject.startsWith("Fwd:", true)) item.subject else "Fwd: ${item.subject}",
            "\n\n--- Forwarded message ---\nFrom: ${item.senderAddress}\nSubject: ${item.subject}\n\n${item.bodyText}",
        )
    }

    val topChromeOffset = animateChromeOffset(
        visible = true,
        hiddenOffset = -detailTopBarHeight,
        label = "detail-top-chrome-slide",
    )
    val bottomChromeOffset = animateChromeOffset(
        visible = bottomChromeVisible,
        hiddenOffset = 124.dp,
        label = "detail-bottom-chrome-slide",
    )
    val remoteButtonBottom by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (bottomChromeVisible) 88.dp else 14.dp,
        animationSpec = tween(
            durationMillis = BondMotionDuration.ChromeReveal,
            easing = BondMotionEasing.Standard,
        ),
        label = "remote-image-bottom-inset",
    )

    Box(modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.bondSurfaces.page,
        ) {}

        // Keep Chromium and the native title/sender raster in one translated container. Applying
        // the pull offset independently to AndroidView and Compose can land on different platform
        // frames, briefly leaving the sender metadata over the first rows of the message body.
        // One parent transform makes the complete mail sheet move atomically.
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
        val hasDisplayBody = item.hasDisplayBody()
        when {
            bodyLoading && !hasDisplayBody -> {
                // Keep the subject/sender shell visible from the very first destination frame.
                // This is intentionally not delayed: an empty page is perceived as a flash even
                // when it only lasts a few frames.
                MailDocumentPlaceholder(
                    showProgress = true,
                    expandedBody = true,
                    header = mailHeader,
                    headerLayout = headerLayout,
                    previewText = "",
                    topContentInset = messageContentTopInset,
                    headerContentVisible = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            bodyLoadFailed && !hasDisplayBody -> {
                Box(Modifier.fillMaxSize()) {
                    // Keep the cached subject/sender and list preview visible. A transient IMAP
                    // error should not replace the opened mail with an unrelated blank page.
                    MailDocumentPlaceholder(
                        showProgress = false,
                        expandedBody = true,
                        header = mailHeader,
                        headerLayout = headerLayout,
                        previewText = previewText,
                        topContentInset = messageContentTopInset,
                        headerContentVisible = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(start = 24.dp, end = 24.dp, bottom = 96.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        tonalElevation = 0.dp,
                        shadowElevation = 4.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = tr("error_connection_failed"),
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            )
                            TextButton(onClick = { bodyRetryToken += 1 }) {
                                Text(tr("retry"))
                            }
                        }
                    }
                }
            }

            else -> {
                val html = item.bodyHtml
                    ?.takeIf(String::isNotBlank)
                    ?: plainTextHtml(item.bodyText)
                MailHtmlView(
                    cacheKey = "${item.id}:${item.htmlContentHash ?: MimeParser.hash(item.bodyText)}:${item.bodyParserVersion}:retry=$renderRetryToken",
                    html = html,
                    header = mailHeader,
                    headerLayout = headerLayout,
                    loadImages = loadImages,
                    // The fixed app bar owns its own viewport area. Keeping Chromium below it
                    // prevents a naturally scrolling sender row from being clipped behind chrome.
                    topContentInset = 0.dp,
                    onTopPullDelta = updateTopPull,
                    onTopPullRelease = releaseTopPull,
                    onWebViewChanged = { webView ->
                        activeMailWebView = webView
                        if (webView == null) mailContentScrollY.intValue = 0
                    },
                    onRemoteImagesChanged = { hasRemoteImages = it },
                    onExternalLink = { externalUrl = it },
                    onChromeVisibilityChanged = { visible -> bottomChromeVisible = visible },
                    onContentScrollChanged = { scrollY ->
                        mailContentScrollY.intValue = scrollY.coerceAtLeast(0)
                    },
                    onRenderStarted = { renderFailed = false },
                    onDocumentReady = reportFirstContentReady,
                    onRenderFailure = {
                        renderFailed = true
                        reportFirstContentReady()
                    },
                    onRendererGone = {
                        if (rendererRecoveryCount < 1) {
                            rendererRecoveryCount += 1
                            renderFailed = false
                            renderRetryToken += 1
                        } else {
                            renderFailed = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 12.dp,
                            top = messageContentTopInset,
                            end = 12.dp,
                        ),
                )
                // Keep one native header renderer alive before, during, and after Chromium's
                // first commit. ColorOS maps Compose SemiBold and CSS weight 600 to visibly
                // different font faces; covering the HTML header with this stable layer prevents
                // sender text from changing weight at the placeholder/WebView handoff.
                MailStableHeaderOverlay(
                    header = mailHeader,
                    headerLayout = headerLayout,
                    topContentInset = 0.dp,
                    contentScrollYpx = mailContentScrollY,
                    topPullOffsetPx = 0f,
                    onTopPullDelta = updateTopPull,
                    onTopPullRelease = releaseTopPull,
                    webView = activeMailWebView,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 12.dp,
                            top = messageContentTopInset,
                            end = 12.dp,
                        ),
                )
                if (renderFailed) {
                    MailContentFailure(
                        topContentInset = messageContentTopInset,
                        onRetry = {
                            renderFailed = false
                            renderRetryToken += 1
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                AnimatedVisibility(
                    visible = hasRemoteImages && !loadImages,
                    modifier = Modifier.align(Alignment.BottomEnd),
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
                ) {
                    RemoteImageFloatingButton(
                        onClick = {
                            loadImages = true
                            scope.launch { container.repository.allowRemoteImages(item.id) }
                        },
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(end = 16.dp, bottom = remoteButtonBottom),
                    )
                }
            }
        }

        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = topChromeOffset.toPx() },
            color = MaterialTheme.bondSurfaces.page,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = owningAccount?.displayName
                            ?.trim()
                            ?.take(ACCOUNT_DISPLAY_NAME_MAX_LENGTH)
                            .orEmpty(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("back"))
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { container.repository.toggleStarred(item) } }) {
                        Icon(
                            if (item.starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = { moreOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (item.unread) tr("mark_read") else tr("mark_unread")) },
                            leadingIcon = {
                                Icon(
                                    if (item.unread) Icons.Default.MarkEmailRead else Icons.Default.MarkEmailUnread,
                                    null,
                                )
                            },
                            onClick = {
                                moreOpen = false
                                scope.launch { container.repository.toggleUnread(item) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("share")) },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = { moreOpen = false; share() },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        }

        MessageActionDock(
            onReply = ::reply,
            onForward = ::forward,
            onShare = ::share,
            onDelete = { confirmDelete = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { translationY = bottomChromeOffset.toPx() }
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("confirm_delete_message_title")) },
            text = { Text(tr("confirm_delete_message_body")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(item)
                    },
                ) { Text(tr("delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(tr("cancel")) }
            },
        )
    }

    externalUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { externalUrl = null },
            title = { Text(tr("open_external_link")) },
            text = { Text("${tr("external_link_desc")}\n\n$url") },
            confirmButton = {
                TextButton(
                    onClick = {
                        externalUrl = null
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                ) { Text(tr("open")) }
            },
            dismissButton = { TextButton(onClick = { externalUrl = null }) { Text(tr("cancel")) } },
        )
    }
}

@Composable
private fun RemoteImageFloatingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(46.dp),
        shape = CircleShape,
        color = MaterialTheme.bondSurfaces.popup.copy(alpha = 0.90f),
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f),
        ),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Image,
                contentDescription = tr("load_images_once"),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun MessageActionDock(
    onReply: () -> Unit,
    onForward: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.bondSurfaces.dock.copy(alpha = 0.94f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
            ),
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    MessageDockItem(
                        icon = Icons.AutoMirrored.Filled.Reply,
                        label = tr("reply"),
                        modifier = Modifier.weight(1f),
                        onClick = onReply,
                    )
                    MessageDockItem(
                        icon = Icons.AutoMirrored.Filled.Forward,
                        label = tr("forward"),
                        modifier = Modifier.weight(1f),
                        onClick = onForward,
                    )
                    MessageDockItem(
                        icon = Icons.Default.Share,
                        label = tr("share"),
                        modifier = Modifier.weight(1f),
                        onClick = onShare,
                    )
                }
            }
        }
        FloatingCircleAction(
            onClick = onDelete,
            modifier = Modifier.size(58.dp),
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ) {
            Icon(Icons.Default.Delete, contentDescription = tr("delete"))
        }
    }
}

@Composable
private fun MessageDockItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = CircleShape,
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MailHtmlView(
    cacheKey: String,
    html: String,
    header: MailWebHeader,
    headerLayout: MailHeaderLayout,
    loadImages: Boolean,
    topContentInset: Dp,
    onTopPullDelta: (Float) -> Float,
    onTopPullRelease: () -> Unit,
    onWebViewChanged: (WebView?) -> Unit,
    onRemoteImagesChanged: (Boolean) -> Unit,
    onExternalLink: (String) -> Unit,
    onChromeVisibilityChanged: (Boolean) -> Unit,
    onContentScrollChanged: (Int) -> Unit,
    onRenderStarted: () -> Unit,
    onDocumentReady: () -> Unit,
    onRenderFailure: () -> Unit,
    onRendererGone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fontScale = density.fontScale.coerceIn(0.75f, 2.50f)
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    val viewportWidthCssPx = (windowWidthPx / density.density).roundToInt().coerceAtLeast(240)
    // WebView scroll callbacks report physical pixels, while the HTML spacer is expressed in
    // density-independent CSS pixels. Convert the chrome thresholds to device pixels so the app
    // bar does not leave an unconsumed blank strip on high-density phones.
    val chromeHideThresholdPx = with(density) { 72.dp.roundToPx() }
    val chromeRevealThresholdPx = with(density) { 56.dp.roundToPx() }
    val gestureHideThresholdPx = with(density) { 52.dp.toPx() }
    val gestureRevealThresholdPx = with(density) { 44.dp.toPx() }
    val topContentInsetCssPx = topContentInset.value.roundToInt()
    val subjectBlockHeightCssPx = headerLayout.subjectBlockHeight.value.roundToInt().coerceAtLeast(1)
    val subjectFontSizeSp = headerLayout.subjectFontSize.value
    val subjectLineHeightSp = headerLayout.subjectLineHeight.value
    val senderBlockHeightCssPx = headerLayout.senderBlockHeight.value.roundToInt().coerceAtLeast(1)
    val senderDomain = remember(header.senderAddress) {
        header.senderAddress.substringAfterLast('@', "").lowercase()
    }
    val foreground = MaterialTheme.colorScheme.onSurface.toArgb()
    val backgroundColor = MaterialTheme.bondSurfaces.page
    val background = backgroundColor.toArgb()
    val link = MaterialTheme.colorScheme.primary.toArgb()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val headerSurface = MaterialTheme.bondSurfaces.content.toArgb()
    val avatarPalette = brandAvatarPalette(
        senderName = header.senderName,
        senderAddress = header.senderAddress,
        monet = header.monetBrandIcons,
    )
    val avatarBackground = avatarPalette.background.toArgb()
    val avatarForeground = avatarPalette.foreground.toArgb()
    val darkMode = backgroundColor.luminance() < 0.5f
    val foregroundCss = remember(foreground) { foreground.toCssColor() }
    val backgroundCss = remember(background) { background.toCssColor() }
    val linkCss = remember(link) { link.toCssColor() }
    val mutedCss = remember(muted) { muted.toCssColor() }
    val headerSurfaceCss = remember(headerSurface) { headerSurface.toCssColor() }
    val avatarBackgroundCss = remember(avatarBackground) { avatarBackground.toCssColor() }
    val avatarForegroundCss = remember(avatarForeground) { avatarForeground.toCssColor() }
    val requestedContentKey = remember(
        cacheKey,
        senderDomain,
        header.attachments,
        foregroundCss,
        backgroundCss,
        linkCss,
        mutedCss,
        headerSurfaceCss,
        avatarBackgroundCss,
        avatarForegroundCss,
        darkMode,
        topContentInsetCssPx,
        subjectBlockHeightCssPx,
        subjectFontSizeSp,
        subjectLineHeightSp,
        senderBlockHeightCssPx,
        viewportWidthCssPx,
        fontScale,
    ) {
        buildString {
            append(cacheKey)
            append("|domain=").append(senderDomain)
            append("|sender=").append(header.senderName.hashCode())
            append("|avatar=").append(header.avatarSvg.hashCode())
            append("|attachments=").append(header.attachments.hashCode())
            append("|fg=").append(foregroundCss)
            append("|bg=").append(backgroundCss)
            append("|link=").append(linkCss)
            append("|muted=").append(mutedCss)
            append("|surface=").append(headerSurfaceCss)
            append("|avatarBg=").append(avatarBackgroundCss)
            append("|avatarFg=").append(avatarForegroundCss)
            append("|dark=").append(darkMode)
            append("|top=").append(topContentInsetCssPx)
            append("|subjectHeight=").append(subjectBlockHeightCssPx)
            append("|subjectFont=").append(subjectFontSizeSp)
            append("|subjectLineHeight=").append(subjectLineHeightSp)
            append("|senderHeight=").append(senderBlockHeightCssPx)
            append("|viewport=").append(viewportWidthCssPx)
            append("|fontScale=").append(fontScale)
        }
    }
    val initialPreparedDocument = remember(
        cacheKey,
        senderDomain,
        header.attachments,
        foregroundCss,
        backgroundCss,
        linkCss,
        mutedCss,
        headerSurfaceCss,
        avatarBackgroundCss,
        avatarForegroundCss,
        darkMode,
        topContentInsetCssPx,
        subjectBlockHeightCssPx,
        subjectFontSizeSp,
        subjectLineHeightSp,
        senderBlockHeightCssPx,
        viewportWidthCssPx,
        fontScale,
    ) {
        MailWebViewCache.peekPreparedDocument(
            key = cacheKey,
            header = header,
            foregroundCss = foregroundCss,
            backgroundCss = backgroundCss,
            linkCss = linkCss,
            mutedCss = mutedCss,
            headerSurfaceCss = headerSurfaceCss,
            avatarBackgroundCss = avatarBackgroundCss,
            avatarForegroundCss = avatarForegroundCss,
            darkMode = darkMode,
            topContentInsetCssPx = topContentInsetCssPx,
            subjectBlockHeightCssPx = subjectBlockHeightCssPx,
            subjectFontSizeSp = subjectFontSizeSp,
            subjectLineHeightSp = subjectLineHeightSp,
            senderBlockHeightCssPx = senderBlockHeightCssPx,
            viewportWidthCssPx = viewportWidthCssPx,
            fontScale = fontScale,
        )
    }
    var prepared by remember(
        cacheKey,
        senderDomain,
        header.attachments,
        foregroundCss,
        backgroundCss,
        linkCss,
        mutedCss,
        headerSurfaceCss,
        avatarBackgroundCss,
        avatarForegroundCss,
        darkMode,
        topContentInsetCssPx,
        subjectBlockHeightCssPx,
        subjectFontSizeSp,
        subjectLineHeightSp,
        senderBlockHeightCssPx,
        viewportWidthCssPx,
        fontScale,
        initialPreparedDocument,
    ) {
        mutableStateOf(initialPreparedDocument)
    }
    val holder = remember { WebContentHolder() }
    val retainedContentAvailable = remember(requestedContentKey) {
        MailWebViewPool.canReuseRetainedContent(requestedContentKey)
    }
    // The exact detached WebView already contains a visually committed page. Keep it visible while
    // AndroidView reattaches the same instance; otherwise every revisit shows the preview for one frame.
    var pageVisible by remember(requestedContentKey) {
        mutableStateOf(retainedContentAvailable)
    }
    // Paint the local HTML before allowing remote image decoding. Large Samsung newsletters can
    // otherwise decode and upload several hero images on the same frames as detail navigation.
    // Enabling WebSettings after the main document commits starts those resources without reloading.
    var mainDocumentCommitted by remember(requestedContentKey) {
        mutableStateOf(retainedContentAvailable)
    }
    var placeholderVisible by remember(requestedContentKey) {
        mutableStateOf(!retainedContentAvailable)
    }
    val latestOnWebViewChanged by rememberUpdatedState(onWebViewChanged)
    val latestOnExternalLink by rememberUpdatedState(onExternalLink)
    val latestOnRemoteImagesChanged by rememberUpdatedState(onRemoteImagesChanged)
    val latestOnChromeVisibilityChanged by rememberUpdatedState(onChromeVisibilityChanged)
    val latestOnContentScrollChanged by rememberUpdatedState(onContentScrollChanged)
    val latestOnRenderStarted by rememberUpdatedState(onRenderStarted)
    val latestOnDocumentReady by rememberUpdatedState(onDocumentReady)
    val latestOnRenderFailure by rememberUpdatedState(onRenderFailure)
    val latestOnRendererGone by rememberUpdatedState(onRendererGone)
    val latestOnTopPullDelta by rememberUpdatedState(onTopPullDelta)
    val latestOnTopPullRelease by rememberUpdatedState(onTopPullRelease)

    LaunchedEffect(
        cacheKey,
        senderDomain,
        header.attachments,
        foregroundCss,
        backgroundCss,
        linkCss,
        mutedCss,
        headerSurfaceCss,
        avatarBackgroundCss,
        avatarForegroundCss,
        darkMode,
        topContentInsetCssPx,
        subjectBlockHeightCssPx,
        subjectFontSizeSp,
        subjectLineHeightSp,
        senderBlockHeightCssPx,
        viewportWidthCssPx,
        fontScale,
    ) {
        // A synchronous LRU hit is already installed by remember(). Do not clear it and re-enter a
        // loading document: doing so made every revisit flash even though no parsing was required.
        if (prepared != null) return@LaunchedEffect

        pageVisible = false
        latestOnRenderStarted()
        prepared = runCatching {
            MailWebViewCache.preparedDocument(
                key = cacheKey,
                html = html,
                header = header,
                foregroundCss = foregroundCss,
                backgroundCss = backgroundCss,
                linkCss = linkCss,
                mutedCss = mutedCss,
                headerSurfaceCss = headerSurfaceCss,
                avatarBackgroundCss = avatarBackgroundCss,
                avatarForegroundCss = avatarForegroundCss,
                darkMode = darkMode,
                topContentInsetCssPx = topContentInsetCssPx,
                subjectBlockHeightCssPx = subjectBlockHeightCssPx,
                subjectFontSizeSp = subjectFontSizeSp,
                subjectLineHeightSp = subjectLineHeightSp,
                senderBlockHeightCssPx = senderBlockHeightCssPx,
                viewportWidthCssPx = viewportWidthCssPx,
                fontScale = fontScale,
            )
        }.onFailure { error ->
            MailLog.e(MailLog.WEB, "document prepare failed cause=${MailLog.causeSummary(error)}", error)
            latestOnRenderFailure()
        }.getOrNull()
    }

    LaunchedEffect(prepared?.hasRemoteImages) {
        latestOnRemoteImagesChanged(prepared?.hasRemoteImages == true)
    }

    LaunchedEffect(pageVisible) {
        placeholderVisible = !pageVisible
    }

    Box(modifier = modifier) {
        val document = prepared
        val placeholderAlpha by animateFloatAsState(
            targetValue = if (placeholderVisible) 1f else 0f,
            // The outer reader transition starts only after Chromium's visual commit. A second
            // placeholder cross-fade would become a visible content swap during that transition.
            animationSpec = snap(),
            label = "mail-framework-fade",
        )
        if (document != null) {
            key(cacheKey) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    holder.rendererGone = false
                    MailWebViewPool.acquire(context, requestedContentKey).apply {
                        animate().cancel()
                        val retainedPage = MailWebViewPool.retainedContentKey(this) == requestedContentKey
                        alpha = if (retainedPage) 1f else 0f
                        translationY = 0f
                        setBackgroundColor(background)
                        isVerticalScrollBarEnabled = true
                        scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                        isHorizontalScrollBarEnabled = false
                        isScrollbarFadingEnabled = true
                        overScrollMode = View.OVER_SCROLL_NEVER
                        isNestedScrollingEnabled = false
                        setLayerType(View.LAYER_TYPE_NONE, null)
                        settings.javaScriptEnabled = false
                        settings.domStorageEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.loadsImagesAutomatically = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                        settings.textZoom = document.textZoomPercent
                        settings.defaultTextEncodingName = "utf-8"
                        settings.mediaPlaybackRequiresUserGesture = true
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                            // Preserve the sender's complete light palette. Algorithmic darkening
                            // changed Google backgrounds without changing its #202124 text, leaving
                            // black text on a dark panel.
                            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
                        }
                        // Keep one complete touch sequence inside Chromium. At the document top,
                        // replace WebView's private stretch effect with a shared pull distance so the
                        // HTML and the native subject/sender layer move as one physical sheet. rawY is
                        // used because the view itself is translated during the gesture.
                        setOnTouchListener { view, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    view.parent?.requestDisallowInterceptTouchEvent(true)
                                    holder.touchLastY = event.rawY
                                    holder.touchTravel = 0f
                                    holder.touchAccumulator = 0f
                                    holder.touchDirection = 0
                                }

                                MotionEvent.ACTION_MOVE -> {
                                    val fingerDelta = event.rawY - holder.touchLastY
                                    val scrollDelta = -fingerDelta
                                    holder.touchLastY = event.rawY
                                    holder.touchTravel += abs(fingerDelta)
                                    val webView = view as? WebView
                                    val shouldDriveTopPull =
                                        ((webView?.scrollY ?: 0) <= 0 && fingerDelta > 0f) ||
                                            holder.topPullActive
                                    if (shouldDriveTopPull) {
                                        val pull = latestOnTopPullDelta(fingerDelta)
                                        holder.topPullActive = pull > 0.5f
                                        holder.touchAccumulator = 0f
                                        holder.touchDirection = 0
                                        if (!holder.requestedChromeVisible) {
                                            holder.requestedChromeVisible = true
                                            latestOnChromeVisibilityChanged(true)
                                        }
                                    }

                                    if (!holder.topPullActive && !shouldDriveTopPull) {
                                        val direction = scrollDelta.compareTo(0f)
                                        if (direction != 0) {
                                            if (
                                                holder.touchDirection != 0 &&
                                                direction != holder.touchDirection
                                            ) {
                                                holder.touchAccumulator = 0f
                                            }
                                            holder.touchAccumulator += scrollDelta
                                            holder.touchDirection = direction

                                            when {
                                                holder.touchAccumulator >= gestureHideThresholdPx &&
                                                    holder.requestedChromeVisible -> {
                                                    holder.requestedChromeVisible = false
                                                    holder.touchAccumulator = 0f
                                                    latestOnChromeVisibilityChanged(false)
                                                }

                                                holder.touchAccumulator <= -gestureRevealThresholdPx &&
                                                    !holder.requestedChromeVisible -> {
                                                    holder.requestedChromeVisible = true
                                                    holder.touchAccumulator = 0f
                                                    latestOnChromeVisibilityChanged(true)
                                                }
                                            }
                                        }
                                    }
                                }

                                MotionEvent.ACTION_UP,
                                MotionEvent.ACTION_CANCEL -> {
                                    view.parent?.requestDisallowInterceptTouchEvent(false)
                                    if (holder.topPullActive) {
                                        holder.topPullActive = false
                                        latestOnTopPullRelease()
                                    }
                                    if (holder.touchTravel >= 8f) {
                                        val webView = view as? WebView
                                        val scale = webView?.scale?.takeIf { it > 0f } ?: 1f
                                        val viewportCss = ((webView?.height ?: 0) / scale).roundToInt()
                                        MailLog.d(
                                            MailLog.WEB,
                                            "gesture travel=${holder.touchTravel.roundToInt()}px " +
                                                "scrollY=${webView?.scrollY ?: 0} " +
                                                "contentCss=${webView?.contentHeight ?: 0} " +
                                                "viewportCss=$viewportCss scale=$scale " +
                                                "canUp=${webView?.canScrollVertically(-1) == true} " +
                                                "canDown=${webView?.canScrollVertically(1) == true}",
                                        )
                                    }
                                    holder.touchAccumulator = 0f
                                    holder.touchDirection = 0
                                }
                            }
                            false
                        }
                        setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                            latestOnContentScrollChanged(scrollY.coerceAtLeast(0))
                            if (scrollY > 0 && holder.topPullActive) {
                                holder.topPullActive = false
                                latestOnTopPullRelease()
                            }
                            if (scrollY <= 8) {
                                holder.chromeScrollAccumulator = 0
                                holder.chromeScrollDirection = 0
                                // Do not immediately undo an upward gesture that has only moved a
                                // short message by a few pixels. Reveal automatically only while the
                                // document is actually moving back toward its top edge.
                                if (oldScrollY > scrollY && !holder.requestedChromeVisible) {
                                    holder.requestedChromeVisible = true
                                    latestOnChromeVisibilityChanged(true)
                                }
                            } else {
                                val delta = scrollY - oldScrollY
                                val direction = delta.compareTo(0)
                                if (direction != 0) {
                                    if (
                                        holder.chromeScrollDirection != 0 &&
                                        direction != holder.chromeScrollDirection
                                    ) {
                                        holder.chromeScrollAccumulator = 0
                                    }
                                    holder.chromeScrollAccumulator += delta
                                    holder.chromeScrollDirection = direction
                                }

                                when {
                                    holder.chromeScrollAccumulator >= chromeHideThresholdPx && holder.requestedChromeVisible -> {
                                        holder.requestedChromeVisible = false
                                        holder.chromeScrollAccumulator = 0
                                        latestOnChromeVisibilityChanged(false)
                                    }
                                    holder.chromeScrollAccumulator <= -chromeRevealThresholdPx && !holder.requestedChromeVisible -> {
                                        holder.requestedChromeVisible = true
                                        holder.chromeScrollAccumulator = 0
                                        latestOnChromeVisibilityChanged(true)
                                    }
                                }
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageCommitVisible(view: WebView?, url: String?) {
                                // The sanitized HTML and CSS are local and already paintable here.
                                // Android guarantees that stale pixels are no longer being drawn at
                                // this point. Start navigation immediately instead of waiting for a
                                // second visual-state callback and two more frames. Remote images can
                                // continue decoding while the page moves in from the right.
                                mainDocumentCommitted = true
                                requestReveal(view, committedVisible = true)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                holder.pageFinishedGeneration = holder.contentGeneration
                                requestReveal(view, committedVisible = false)
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                if (request?.isForMainFrame == true) {
                                    MailLog.w(
                                        MailLog.WEB,
                                        "main document error code=${error?.errorCode} " +
                                            "description=${error?.description?.toString().orEmpty()}",
                                    )
                                    pageVisible = true
                                    latestOnRenderFailure()
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceResponse?,
                            ) {
                                val url = request?.url?.toString().orEmpty()
                                if (request?.isForMainFrame != true && looksLikeImageUrl(url)) {
                                    MailLog.w(
                                        MailLog.WEB,
                                        "image http=${errorResponse?.statusCode ?: 0} host=${request?.url?.host.orEmpty()}",
                                    )
                                }
                            }

                            override fun onRenderProcessGone(
                                view: WebView,
                                detail: RenderProcessGoneDetail,
                            ): Boolean {
                                MailLog.e(
                                    MailLog.WEB,
                                    "renderer gone crashed=${detail.didCrash()} " +
                                        "priority=${detail.rendererPriorityAtExit()}",
                                )
                                pageVisible = false
                                holder.rendererGone = true
                                holder.webView = null
                                latestOnWebViewChanged(null)
                                MailWebViewPool.discard(view)
                                latestOnRendererGone()
                                return true
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean = handleLink(request?.url?.toString())

                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                                handleLink(url)

                            private fun requestReveal(
                                view: WebView?,
                                committedVisible: Boolean,
                            ) {
                                val committedContentKey = holder.contentKey ?: return
                                val generation = holder.contentGeneration
                                if (
                                    holder.visualCommitRequestedGeneration == generation &&
                                    (!committedVisible || holder.hasCommittedContent)
                                ) {
                                    return
                                }
                                holder.visualCommitRequestedGeneration = generation

                                val revealCommittedPage: () -> Unit = reveal@{
                                    if (
                                        holder.contentKey != committedContentKey ||
                                        holder.contentGeneration != generation
                                    ) {
                                        return@reveal
                                    }
                                    holder.hasCommittedContent = true
                                    if (holder.scrollToTopOnCommit) {
                                        holder.scrollToTopOnCommit = false
                                        view?.scrollTo(0, 0)
                                        latestOnContentScrollChanged(0)
                                    }
                                    if (view != null) {
                                        MailWebViewPool.markContentCommitted(view, committedContentKey)
                                    }
                                    // This page is still one full screen to the right. Commit the
                                    // exact WebView pixels synchronously; the outer reader motion is
                                    // the only visual reveal and therefore cannot expose a preview
                                    // document between frames.
                                    view?.showMailDocumentImmediately()
                                    pageVisible = true
                                    placeholderVisible = false
                                    latestOnDocumentReady()
                                }
                                val scheduleReveal: () -> Unit = schedule@{
                                    if (
                                        holder.contentKey != committedContentKey ||
                                        holder.contentGeneration != generation ||
                                        holder.revealScheduledGeneration == generation
                                    ) {
                                        return@schedule
                                    }
                                    holder.revealScheduledGeneration = generation
                                    val revealAfterStableFrames = Runnable {
                                        if (view == null) {
                                            revealCommittedPage()
                                        } else {
                                            view.postOnAnimation {
                                                view.postOnAnimation { revealCommittedPage() }
                                            }
                                        }
                                    }
                                    revealAfterStableFrames.run()
                                }

                                if (view == null) {
                                    scheduleReveal()
                                    return
                                }

                                if (committedVisible) {
                                    revealCommittedPage()
                                    return
                                }

                                // onPageFinished still does not guarantee that the compositor has
                                // submitted the final pixels. Wait for a visual-state callback and
                                // two frames; remote-resource mail gets a small additional settle
                                // window while the useful plain-text preview remains visible below.
                                runCatching {
                                    view.postVisualStateCallback(
                                        generation,
                                        object : WebView.VisualStateCallback() {
                                            override fun onComplete(requestId: Long) {
                                                scheduleReveal()
                                            }
                                        },
                                    )
                                    // Some vendor WebViews omit the visual callback. The generation
                                    // check keeps this fallback harmless when the callback wins.
                                    view.postDelayed({ scheduleReveal() }, 420L)
                                }.onFailure {
                                    scheduleReveal()
                                }
                            }

                            private fun handleLink(url: String?): Boolean {
                                url ?: return false
                                latestOnExternalLink(url)
                                return true
                            }
                        }
                        holder.webView = this
                        latestOnWebViewChanged(this)
                    }
                },
                update = { webView ->
                    webView.setBackgroundColor(background)
                    val allowRemoteResources = loadImages && mainDocumentCommitted
                    runCatching { webView.settings.blockNetworkLoads = !allowRemoteResources }
                    webView.settings.blockNetworkImage = !allowRemoteResources
                    webView.settings.mixedContentMode = if (allowRemoteResources) {
                        WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    } else {
                        WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    }
                    // Desktop newsletters are already scaled as one canvas by the prepared CSS.
                    // Disable WebView's second overview pass in that mode; otherwise Chromium can
                    // independently rescale the already-zoomed canvas and crop its right edge.
                    when (document.layout) {
                        com.bond.mail.ui.components.MailDocumentLayout.DESKTOP_SCALED -> {
                            webView.settings.useWideViewPort = false
                            webView.settings.loadWithOverviewMode = false
                            webView.settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                            webView.settings.textZoom = document.textZoomPercent
                        }
                        com.bond.mail.ui.components.MailDocumentLayout.FLUID -> {
                            webView.settings.useWideViewPort = true
                            webView.settings.loadWithOverviewMode = true
                            webView.settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                            webView.settings.textZoom = document.textZoomPercent
                        }
                    }
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, false)
                    }
                    holder.waitForResourceSettle = document.hasRemoteImages && allowRemoteResources

                    val contentKey = requestedContentKey
                    val retainedContentKey = MailWebViewPool.retainedContentKey(webView)
                    if (
                        holder.contentKey == null &&
                        retainedContentKey == contentKey
                    ) {
                        holder.messageKey = cacheKey
                        holder.contentKey = contentKey
                        holder.hasCommittedContent = true
                        holder.scrollToTopOnCommit = false
                        holder.requestedChromeVisible = true
                        webView.scrollTo(0, 0)
                        webView.postOnAnimation { webView.scrollTo(0, 0) }
                        latestOnContentScrollChanged(0)
                        latestOnChromeVisibilityChanged(true)
                        // This exact WebView still owns the fully committed pixels for this key.
                        // It is acquired preferentially, so showing it immediately avoids replaying
                        // the preview/HTML swap every time the same message is reopened.
                        webView.showMailDocumentImmediately()
                        pageVisible = true
                        latestOnDocumentReady()
                        latestOnWebViewChanged(webView)
                        MailLog.d(
                            MailLog.WEB,
                            "document reuse retained=true layout=${document.layout} " +
                                "desktopWidth=${document.desktopCanvasWidthPx ?: 0}",
                        )
                    }
                    if (holder.contentKey != contentKey) {
                        val messageChanged = holder.messageKey != cacheKey
                        val oldContentKey = holder.contentKey
                        holder.messageKey = cacheKey
                        holder.contentKey = contentKey
                        holder.contentGeneration += 1L
                        holder.visualCommitRequestedGeneration = -1L
                        holder.revealScheduledGeneration = -1L
                        holder.pageFinishedGeneration = -1L
                        holder.commitFallbackPostedGeneration = -1L
                        holder.scrollToTopOnCommit = messageChanged
                        if (messageChanged || !holder.hasCommittedContent) {
                            holder.hasCommittedContent = false
                            mainDocumentCommitted = false
                            if (holder.topPullActive) {
                                holder.topPullActive = false
                                latestOnTopPullRelease()
                            }
                            pageVisible = false
                            webView.scrollTo(0, 0)
                            latestOnContentScrollChanged(0)
                            holder.chromeScrollAccumulator = 0
                            holder.chromeScrollDirection = 0
                            holder.touchAccumulator = 0f
                            holder.touchDirection = 0
                            holder.touchTravel = 0f
                            holder.requestedChromeVisible = true
                            latestOnChromeVisibilityChanged(true)
                        }
                        MailLog.d(
                            MailLog.WEB,
                            "document load changedMessage=$messageChanged images=$loadImages " +
                                "previous=${oldContentKey != null} layout=${document.layout} " +
                                "desktopWidth=${document.desktopCanvasWidthPx ?: 0} " +
                                "topInset=$topContentInsetCssPx",
                        )
                        MailWebViewPool.clearRetainedContent(webView)
                        // Loading remote images rebuilds the same document with a different
                        // network policy. Keep its current pixels on screen until Chromium commits
                        // the replacement; hiding here produced a completely blank mail view.
                        if (messageChanged || !holder.hasCommittedContent) {
                            webView.hideMailDocument()
                        } else {
                            webView.showMailDocumentImmediately()
                        }
                        webView.loadDataWithBaseURL(
                            document.baseUrl ?: "https://mail.bond.invalid/",
                            document.html,
                            "text/html",
                            "UTF-8",
                            null,
                        )
                    }
                    // setBlockNetworkImage(false) makes WebView fetch image resources already
                    // referenced by the current document. Loading images is therefore a resource
                    // policy update, not a new HTML document identity; no reload is needed.
                },
            )
            }
        }

        if (placeholderAlpha > 0.001f) {
            MailDocumentPlaceholder(
                showProgress = false,
                expandedBody = document?.contentHeightHint != MailContentHeightHint.SHORT,
                header = header,
                headerLayout = headerLayout,
                previewText = "",
                topContentInset = topContentInset,
                // MailHtmlView's own modifier already applies the 12 dp document gutters.
                // Applying them again here made the cold-start placeholder narrower than the
                // committed WebView, so the subject and sender row visibly expanded on reveal.
                horizontalContentInset = 0.dp,
                headerContentVisible = true,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = placeholderAlpha },
            )
        }
    }

    DisposableEffect(holder) {
        onDispose {
            latestOnWebViewChanged(null)
            if (holder.topPullActive) {
                holder.topPullActive = false
                latestOnTopPullRelease()
            }
            holder.contentGeneration += 1L
            holder.visualCommitRequestedGeneration = -1L
            holder.webView?.let { webView ->
                holder.webView = null
                if (holder.rendererGone) {
                    MailWebViewPool.discard(webView)
                } else {
                    MailWebViewPool.release(webView)
                }
            }
        }
    }
}

private fun WebView.hideMailDocument() {
    animate().cancel()
    alpha = 0f
    translationY = 0f
}

private fun WebView.showMailDocumentImmediately() {
    animate().cancel()
    alpha = 1f
    translationY = 0f
}

@Composable
private fun MailContentFailure(
    topContentInset: Dp,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.bondSurfaces.page,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topContentInset),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = tr("mail_content_load_failed"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BondPrimaryButton(onClick = onRetry) {
                    Text(tr("retry"))
                }
            }
        }
    }
}

private data class MailHeaderLayout(
    val subjectFontSize: TextUnit,
    val subjectLineHeight: TextUnit,
    val subjectBlockHeight: Dp,
    val senderBlockHeight: Dp,
)

@Composable
private fun rememberMailHeaderLayout(subject: String): MailHeaderLayout {
    val density = LocalDensity.current
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    val textMeasurer = rememberTextMeasurer()
    val availableWidthPx = with(density) {
        // Match the subject to the sender avatar edge: 12 dp document gutters plus 14 dp header
        // insets on each side. Measure at that exact width so long subjects gain another line of
        // height before the sender row is placed, rather than being covered by it.
        (windowWidthPx - 52.dp.roundToPx()).coerceAtLeast(220.dp.roundToPx())
    }
    val fontScale = density.fontScale

    return remember(subject, availableWidthPx, fontScale, textMeasurer) {
        data class SubjectStyle(
            val fontSize: TextUnit,
            val lineHeight: TextUnit,
            val preferredMaxLines: Int,
        )

        // Keep a short subject prominent, but step down before a long transactional subject turns
        // into three oversized lines. Measure every candidate without an artificial line cap:
        // measuring with maxLines can report the capped count even when the full subject needs more
        // room, which makes the following sender row cover the remaining glyphs.
        val candidates = listOf(
            SubjectStyle(22.sp, 28.sp, preferredMaxLines = 1),
            SubjectStyle(20.sp, 26.sp, preferredMaxLines = 2),
            SubjectStyle(18.sp, 24.sp, preferredMaxLines = 3),
        )
        val chosen = candidates.firstOrNull { candidate ->
            val result = textMeasurer.measure(
                text = AnnotatedString(subject),
                style = TextStyle(
                    fontSize = candidate.fontSize,
                    lineHeight = candidate.lineHeight,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                ),
                softWrap = true,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
                constraints = Constraints(maxWidth = availableWidthPx),
            )
            result.lineCount <= candidate.preferredMaxLines
        } ?: candidates.last()

        val finalMeasure = textMeasurer.measure(
            text = AnnotatedString(subject),
            style = TextStyle(
                fontSize = chosen.fontSize,
                lineHeight = chosen.lineHeight,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            ),
            softWrap = true,
            // Normal mail subjects fit in one to three lines. Keep extra room for unusually long
            // subjects instead of silently cropping the last line; pathological subjects still
            // receive a visible ellipsis after six lines.
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = availableWidthPx),
        )
        val measuredTextHeight = with(density) { finalMeasure.size.height.toDp() }
        val blockHeight = (measuredTextHeight + 28.dp).coerceAtLeast(52.dp)
        // Three metadata lines use 20sp + 16sp + 16sp line heights. Grow the fixed spacer with
        // Android's accessibility font scale so the visible Compose header and the hidden HTML
        // header remain exactly aligned instead of clipping or overlapping at larger text sizes.
        val senderBlockHeight = ((52f * fontScale).dp + 26.dp).coerceIn(80.dp, 170.dp)

        MailHeaderLayout(
            subjectFontSize = chosen.fontSize,
            subjectLineHeight = chosen.lineHeight,
            subjectBlockHeight = blockHeight,
            senderBlockHeight = senderBlockHeight,
        )
    }
}

@Composable
private fun MailStableHeaderOverlay(
    header: MailWebHeader,
    headerLayout: MailHeaderLayout,
    topContentInset: Dp,
    contentScrollYpx: State<Int>,
    topPullOffsetPx: Float,
    onTopPullDelta: (Float) -> Float,
    onTopPullRelease: () -> Unit,
    webView: WebView?,
    modifier: Modifier = Modifier,
) {
    val latestWebView by rememberUpdatedState(webView)
    val latestTopPullOffsetPx by rememberUpdatedState(topPullOffsetPx)
    val latestOnTopPullDelta by rememberUpdatedState(onTopPullDelta)
    val latestOnTopPullRelease by rememberUpdatedState(onTopPullRelease)
    val headerDragState = rememberDraggableState { dragDeltaPx ->
        val target = latestWebView ?: return@rememberDraggableState
        if (!target.isAttachedToWindow) return@rememberDraggableState
        if (
            (target.scrollY <= 0 && dragDeltaPx > 0f) ||
            latestTopPullOffsetPx > 0.5f
        ) {
            latestOnTopPullDelta(dragDeltaPx)
        } else {
            target.scrollBy(0, -dragDeltaPx.roundToInt())
        }
    }

    Box(
        modifier = modifier.graphicsLayer { clip = true },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Offset the complete header layer before painting its background. If offset is
                // applied after background/padding, only the text moves while an opaque page-sized
                // rectangle remains pinned over the WebView body.
                .offset {
                    IntOffset(
                        0,
                        -contentScrollYpx.value.coerceAtLeast(0),
                    )
                }
                // Paint the native subject/sender surface edge-to-edge. The child rows already
                // own their readable horizontal insets; applying padding before background made
                // fixed-width newsletters look as if the sender header were shifted to one side.
                .background(MaterialTheme.bondSurfaces.content)
                .padding(top = topContentInset)
                // WebView reports physical pixels and the offset above consumes physical pixels,
                // so there is deliberately no density conversion. Its outer position also moves
                // the pointer hit region; once the header leaves, it cannot block body links.
                // The visible title/sender layer is native Compose and sits above WebView. Give
                // only this header a vertical drag that drives the underlying document directly;
                // body links and gestures remain entirely owned by Chromium.
                .draggable(
                    state = headerDragState,
                    orientation = Orientation.Vertical,
                    enabled = webView != null,
                    onDragStarted = {
                        latestWebView?.let { target ->
                            target.parent?.requestDisallowInterceptTouchEvent(true)
                            target.requestFocus()
                        }
                    },
                    onDragStopped = { velocityPxPerSecond ->
                        val hadTopPull = latestTopPullOffsetPx > 0.5f
                        if (hadTopPull) latestOnTopPullRelease()
                        latestWebView?.let { target ->
                            if (target.isAttachedToWindow && !hadTopPull) {
                                target.flingScroll(0, -velocityPxPerSecond.roundToInt())
                            }
                            target.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    },
                ),
        ) {
            MailSubjectHeader(
                header = header,
                headerLayout = headerLayout,
                contentVisible = true,
            )
            MailSenderHeaderContent(
                header = header,
                headerLayout = headerLayout,
                contentVisible = true,
            )
        }
    }
}

@Composable
private fun MailDocumentPlaceholder(
    showProgress: Boolean,
    expandedBody: Boolean,
    header: MailWebHeader,
    headerLayout: MailHeaderLayout,
    previewText: String,
    topContentInset: Dp,
    horizontalContentInset: Dp = 12.dp,
    headerContentVisible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val skeleton = MaterialTheme.colorScheme.surfaceVariant
    val preview = remember(previewText) {
        previewText
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(420)
    }
    val previewLineCount = remember(preview) {
        if (preview.isBlank()) {
            5
        } else {
            ((preview.length + 39) / 40).coerceIn(2, 7)
        }
    }
    val attachmentRows = header.attachments.size.coerceIn(0, 2)

    Surface(
        modifier = modifier,
        color = MaterialTheme.bondSurfaces.page,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Reserve the floating action dock so the loading sheet fills the readable body rather
            // than continuing underneath the controls. The final WebView may still scroll behind it.
            val bottomDockReserve = 104.dp
            val availableCardHeight = (
                maxHeight - topContentInset - headerLayout.subjectBlockHeight - bottomDockReserve
            ).coerceAtLeast(220.dp)
            val previewHeight = if (preview.isBlank()) {
                8.dp
            } else {
                (previewLineCount * 21 + 34).dp
            }
            val attachmentHeight = if (attachmentRows == 0) {
                0.dp
            } else {
                (attachmentRows * 45 + (attachmentRows - 1) * 8 + 18).dp
            }
            val compactCardHeight = (
                headerLayout.senderBlockHeight + previewHeight + attachmentHeight
            ).coerceIn(220.dp, availableCardHeight)
            val targetCardHeight = if (expandedBody) availableCardHeight else compactCardHeight

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = horizontalContentInset,
                        top = topContentInset,
                        end = horizontalContentInset,
                    ),
            ) {
                MailSubjectHeader(
                    header = header,
                    headerLayout = headerLayout,
                    contentVisible = headerContentVisible,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(targetCardHeight),
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Column(Modifier.fillMaxSize()) {
                        MailSenderHeaderContent(
                            header = header,
                            headerLayout = headerLayout,
                            contentVisible = headerContentVisible,
                        )
                        MailPlaceholderAttachments(header.attachments)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (preview.isNotBlank()) {
                                    Text(
                                        text = preview,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                start = 16.dp,
                                                end = 16.dp,
                                                top = 4.dp,
                                                bottom = 16.dp,
                                            ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp,
                                        maxLines = 7,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                } else if (showProgress) {
                                    MailLoadingSkeleton(
                                        lineFractions = listOf(
                                            0.92f,
                                            0.86f,
                                            0.78f,
                                            0.68f,
                                            0.82f,
                                            0.64f,
                                            0.46f,
                                        ),
                                        color = skeleton,
                                        modifier = Modifier.padding(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 6.dp,
                                            bottom = 18.dp,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun MailPlaceholderAttachments(attachments: List<MailAttachmentInfo>) {
    if (attachments.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.take(2).forEach { attachment ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = lerp(
                    MaterialTheme.bondSurfaces.content,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    0.07f,
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text(text = "📎", fontSize = 20.sp, lineHeight = 20.sp)
                    Text(
                        text = attachment.name,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MailAttachmentCodec.formatSize(attachment.sizeBytes)
                        .takeIf(String::isNotBlank)
                        ?.let { sizeLabel ->
                            Text(
                                text = sizeLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                maxLines = 1,
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun MailLoadingSkeleton(
    lineFractions: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val motionEnabled = bondMotionEnabled()
    val pulse by rememberInfiniteTransition(label = "mail-skeleton-pulse").animateFloat(
        initialValue = 0.52f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 900,
                easing = BondMotionEasing.Standard,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mail-skeleton-alpha",
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        lineFractions.forEach { fraction ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(11.dp),
                shape = RoundedCornerShape(5.dp),
                color = color.copy(alpha = if (motionEnabled) pulse else 0.68f),
            ) {}
        }
    }
}

@Composable
private fun MailSubjectHeader(
    header: MailWebHeader,
    headerLayout: MailHeaderLayout,
    contentVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = header.subject,
        modifier = modifier
            .fillMaxWidth()
            .height(headerLayout.subjectBlockHeight)
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 12.dp)
            .mailHeaderContentVisibility(contentVisible),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = headerLayout.subjectFontSize,
        lineHeight = headerLayout.subjectLineHeight,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
        maxLines = 6,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MailSenderHeaderContent(
    header: MailWebHeader,
    headerLayout: MailHeaderLayout,
    contentVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(headerLayout.senderBlockHeight)
            .padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 11.dp)
            .mailHeaderContentVisibility(contentVisible),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        ContactAvatar(
            name = header.senderName,
            email = header.senderAddress,
            customText = header.customAvatarText,
            size = 46.dp,
            monet = header.monetBrandIcons,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = header.senderName,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    // Compose resolves SemiBold to a synthesized bold face on some ColorOS
                    // builds. Medium most closely matches Chromium's CSS weight 600.
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = header.dateLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                )
                // Reserve the attachment slot even before the MIME body arrives. The icon may
                // become known later, but sender/date text must never reflow when it appears.
                Box(
                    modifier = Modifier.width(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (header.attachments.isNotEmpty()) {
                        Text(
                            text = "📎",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            lineHeight = 16.sp,
                            letterSpacing = 0.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(
                text = header.senderAddress,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = header.recipient,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Modifier.mailHeaderContentVisibility(visible: Boolean): Modifier =
    if (visible) {
        this
    } else {
        this
            .clearAndSetSemantics {}
            .graphicsLayer { alpha = 0f }
    }

private fun mergeImmediateOpenResult(
    stored: MessageEntity?,
    opened: MessageEntity?,
): MessageEntity? {
    if (stored == null) return opened
    if (opened == null || stored.hasDisplayBody() || !opened.hasDisplayBody()) return stored

    // The BODY result is newer only for MIME content. Preserve flags and user-controlled fields
    // from the latest Room row while its invalidation catches up.
    return opened.copy(
        unread = stored.unread,
        starred = stored.starred,
        remoteImageAllowed = stored.remoteImageAllowed,
        deliveryState = stored.deliveryState,
    )
}

private fun MessageEntity.hasDisplayBody(): Boolean =
    !bodyHtml.isNullOrBlank() || bodyText.isNotBlank() || (bodyLoaded && hasAttachments)

private fun MessageEntity.needsBodyRefresh(): Boolean =
    !bodyLoaded || !hasDisplayBody() || bodyParserVersion < MimeParser.CURRENT_VERSION


private fun plainTextHtml(text: String): String =
    "<html><body><pre class=\"bondmail-plain\">${TextUtils.htmlEncode(text)}</pre></body></html>"

private fun formatDetailMailTime(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
    return if (dateTime.toLocalDate() == LocalDate.now(zone)) {
        dateTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    } else {
        dateTime.format(DateTimeFormatter.ofPattern("M/d"))
    }
}

private fun Int.toCssColor(): String = String.format("#%06X", 0xFFFFFF and this)

private class WebContentHolder(
    var contentKey: String? = null,
    var messageKey: String? = null,
    var contentGeneration: Long = 0L,
    var visualCommitRequestedGeneration: Long = -1L,
    var revealScheduledGeneration: Long = -1L,
    var pageFinishedGeneration: Long = -1L,
    var commitFallbackPostedGeneration: Long = -1L,
    var waitForResourceSettle: Boolean = false,
    var hasCommittedContent: Boolean = false,
    var scrollToTopOnCommit: Boolean = false,
    var requestedChromeVisible: Boolean = true,
    var chromeScrollAccumulator: Int = 0,
    var chromeScrollDirection: Int = 0,
    var touchLastY: Float = 0f,
    var touchTravel: Float = 0f,
    var touchAccumulator: Float = 0f,
    var touchDirection: Int = 0,
    var topPullActive: Boolean = false,
    var webView: WebView? = null,
    var rendererGone: Boolean = false,
)

private fun looksLikeImageUrl(url: String): Boolean =
    IMAGE_RESOURCE_PATTERN.containsMatchIn(url)

private val IMAGE_RESOURCE_PATTERN = Regex(
    """(?i)(?:\.(?:png|jpe?g|gif|webp|svg|bmp|ico)(?:[?#]|$)|/images?/|image[_-])""",
)

private fun isWifiConnected(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}
