package com.bond.mail.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bond.mail.data.db.AccountEntity
import com.bond.mail.data.db.MessageListRow
import com.bond.mail.data.performance.UiPerformanceGate
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.ui.HomeViewModel
import com.bond.mail.ui.components.MailContentDefaults
import com.bond.mail.ui.components.MessageCard
import com.bond.mail.ui.components.FloatingCircleAction
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.motion.BondMotionDuration
import com.bond.mail.ui.motion.BondMotionEasing
import com.bond.mail.ui.motion.BondMotionSpring
import com.bond.mail.ui.motion.ObserveLazyListChromeVisibility
import com.bond.mail.ui.motion.animateChromeOffset
import com.bond.mail.ui.motion.animateToTopWithMomentum
import com.bond.mail.ui.motion.bondFadeThrough
import com.bond.mail.ui.motion.bondMotionEnabled
import com.bond.mail.ui.motion.bondPressTransform
import com.bond.mail.ui.motion.rememberBondPressInteraction
import com.bond.mail.ui.motion.rememberBondPressResetter
import com.bond.mail.ui.motion.rememberBondPressScale
import com.bond.mail.ui.theme.bondSurfaces
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    settings: AppSettings,
    notificationPermissionGranted: Boolean,
    showPermissionGuide: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onDismissPermissionGuide: () -> Unit,
    onOpenDrawer: () -> Unit,
    onAddAccount: () -> Unit,
    onOpenMessage: (MessageListRow) -> Unit,
    onReplyMessage: (MessageListRow) -> Unit,
    onCompose: () -> Unit,
    chromeVisible: Boolean,
    onChromeVisibilityChanged: (Boolean) -> Unit,
    chromeControllerEnabled: Boolean = true,
) {
    val accounts by viewModel.accounts.collectAsState()
    val savedContacts by viewModel.savedContacts.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val selectedAccountId by viewModel.selectedAccount.collectAsState()
    val currentFolder by viewModel.folder.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val foregroundSession by viewModel.foregroundSession.collectAsState()
    val contentReady by viewModel.contentReady.collectAsState()
    val failure by viewModel.error.collectAsState()
    val motionEnabled = bondMotionEnabled()
    var topNotice by remember { mutableStateOf<String?>(null) }
    val accountById = remember(accounts) { accounts.associateBy { it.id } }
    val contactAvatarByEmail = remember(savedContacts) {
        savedContacts
            .filter { !it.avatarText.isNullOrBlank() }
            .associate { it.email.trim().lowercase() to it.avatarText }
    }
    val selectedAccount = accountById[selectedAccountId]
    // Rows already present when a mailbox becomes active are cache restoration, not a new-mail
    // animation. IDs added later by the staged first sync receive a short K-9-style reveal.
    val revealedMessageIds = remember(selectedAccountId, currentFolder) {
        messages.mapTo(mutableSetOf()) { message -> message.id }
    }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchSourceBounds by remember { mutableStateOf<Rect?>(null) }
    var pendingSearchMessage by remember { mutableStateOf<MessageListRow?>(null) }
    var topMenu by remember { mutableStateOf(false) }
    var confirmDeleteSelection by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    val inSelectionMode = selectedIds.isNotEmpty()
    val selectedMessages = messages.filter { message -> message.id in selectedIds }
    val allVisibleSelected = messages.isNotEmpty() && messages.all { message -> message.id in selectedIds }
    val selectionWillMarkRead = selectedMessages.any { message -> message.unread }
    var selectionDisplayCount by remember { mutableIntStateOf(1) }
    var searchTransformActive by remember { mutableStateOf(false) }
    val searchOverlayActive = showSearch || searchTransformActive
    var previousChromeMode by remember {
        mutableStateOf<Pair<Boolean, Boolean>?>(null)
    }
    LaunchedEffect(inSelectionMode, searchOverlayActive) {
        val previousMode = previousChromeMode
        when {
            inSelectionMode -> onChromeVisibilityChanged(true)
            // Search owns the full result viewport. Keep the floating dock below the screen for
            // the whole container transform so it cannot cover results or be restored by scroll.
            searchOverlayActive -> onChromeVisibilityChanged(false)
            // Restore normal chrome only when a real selection/search session has just ended.
            // HomeScreen is recreated after closing a reader; writing true on that initial
            // composition made a scrolled mailbox flash its top and bottom bars back on.
            previousMode?.first == true || previousMode?.second == true ->
                onChromeVisibilityChanged(true)
        }
        previousChromeMode = inSelectionMode to searchOverlayActive
    }
    LaunchedEffect(selectedIds.size) {
        // AnimatedContent keeps the outgoing selection toolbar alive during fade-through. Preserve
        // its last non-zero count so clearing the final item never renders a one-frame "0 selected".
        if (selectedIds.isNotEmpty()) selectionDisplayCount = selectedIds.size
    }

    LaunchedEffect(foregroundSession) {
        topNotice = null
    }

    val failureText = failure?.let { tr(it.key, *it.args.toTypedArray()) }
    LaunchedEffect(failureText) {
        failureText?.let { message ->
            topNotice = message
            viewModel.clearError()
            delay(3_200L)
            if (topNotice == message) topNotice = null
        }
    }

    val folderItems = listOf(
        FolderUi("INBOX", tr("inbox"), Icons.Filled.Inbox),
        FolderUi("UNREAD", tr("unread_mail"), Icons.Filled.MarkEmailUnread),
        FolderUi("STARRED", tr("starred"), Icons.Filled.Star),
        FolderUi("SENT", tr("sent"), Icons.AutoMirrored.Filled.Send),
        FolderUi("DRAFTS", tr("drafts"), Icons.Filled.Drafts),
        FolderUi("SPAM", tr("spam"), Icons.Filled.Report),
        FolderUi("TRASH", tr("trash"), Icons.Filled.Delete),
    )

    fun toggleSelection(message: MessageListRow) {
        if (selectedIds.contains(message.id)) selectedIds.remove(message.id) else selectedIds.add(message.id)
    }

    fun clearSelection() = selectedIds.clear()

    BackHandler(enabled = inSelectionMode) { clearSelection() }

    if (accounts.isEmpty()) {
        Scaffold { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .statusBarsPadding(),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "notification-permission") {
                    Box(Modifier.fillMaxWidth()) {
                        AnimatedVisibility(
                            visible = !notificationPermissionGranted && showPermissionGuide,
                            enter = expandVertically(
                                expandFrom = Alignment.Top,
                                animationSpec = tween(260),
                            ) + fadeIn(animationSpec = tween(180)),
                            exit = shrinkVertically(
                                shrinkTowards = Alignment.Top,
                                animationSpec = tween(260),
                            ) + fadeOut(animationSpec = tween(150)),
                        ) {
                            Column {
                                NotificationPermissionCard(
                                    onAllow = onRequestNotificationPermission,
                                    onReject = onDismissPermissionGuide,
                                )
                                Spacer(Modifier.height(18.dp))
                            }
                        }
                    }
                }
                item {
                    Surface(
                        modifier = Modifier.animateItem(),
                        shape = RoundedCornerShape(30.dp),
                        tonalElevation = 2.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(tr("welcome_title"), style = MaterialTheme.typography.headlineSmall)
                            Text(tr("welcome_desc"), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onAddAccount) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(tr("add_mailbox"))
                            }
                        }
                    }
                }
            }
        }
        return
    }

    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topChromeHeight = statusBarInset + 68.dp
    val effectiveChromeVisible = chromeVisible || inSelectionMode || searchOverlayActive
    val topChromeOffset = animateChromeOffset(
        visible = effectiveChromeVisible,
        hiddenOffset = -topChromeHeight,
        label = "home-top-chrome-slide",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.bondSurfaces.page),
    ) {
        val listState = rememberLazyListState()
        val scrollScope = rememberCoroutineScope()
        val listBottomContentPadding by animateDpAsState(
            targetValue = if (chromeVisible) 112.dp else 18.dp,
            animationSpec = tween(
                durationMillis = BondMotionDuration.ChromeReveal,
                easing = BondMotionEasing.Standard,
            ),
            label = "mail-list-bottom-content-padding",
        )
        val scrollToTopBottomPadding by animateDpAsState(
            targetValue = if (chromeVisible) 88.dp else 18.dp,
            animationSpec = tween(
                durationMillis = BondMotionDuration.ChromeReveal,
                easing = BondMotionEasing.Standard,
            ),
            label = "mail-list-scroll-top-bottom-padding",
        )
        val showScrollToTop by remember(listState) {
            derivedStateOf {
                val partiallyScrolled =
                    listState.firstVisibleItemIndex > 0 &&
                        listState.firstVisibleItemScrollOffset > 420
                val scrolledAwayFromTop =
                    listState.firstVisibleItemIndex >= 4 || partiallyScrolled
                // The saved index is restored before LazyColumn completes its first measure, while
                // canScrollForward temporarily falls back to false. Depending on that layout-only
                // flag made the button disappear for one frame after closing a reader and replay
                // its enter animation. A non-zero restored viewport is sufficient here.
                scrolledAwayFromTop
            }
        }
        val messageIds = remember(messages) { messages.map { message -> message.id } }
        val messageIndexById = remember(messageIds) {
            messageIds.withIndex().associate { indexed -> indexed.value to indexed.index }
        }
        LaunchedEffect(listState, messageIds) {
            snapshotFlow {
                val visibleMessageIndexes = listState.layoutInfo.visibleItemsInfo
                    .mapNotNull { info -> (info.key as? String)?.let(messageIndexById::get) }
                if (visibleMessageIndexes.isEmpty()) {
                    emptyList()
                } else {
                    val first = (visibleMessageIndexes.minOrNull()!! - 1).coerceAtLeast(0)
                    val lastExclusive = (visibleMessageIndexes.maxOrNull()!! + 4)
                        .coerceAtMost(messageIds.size)
                    messageIds.subList(first, lastExclusive)
                }
            }.collectLatest { visibleWindow ->
                if (visibleWindow.isEmpty()) return@collectLatest
                // Let flings and list-reveal animation settle. Only a stable viewport is worth
                // warming, otherwise fast scrolling would turn into unnecessary IMAP traffic.
                delay(240L)
                viewModel.prefetchVisibleBodies(visibleWindow)
            }
        }
        var verticalPointerGesture by remember { mutableStateOf(false) }
        var swipeGesturesReady by remember { mutableStateOf(true) }
        LaunchedEffect(listState.isScrollInProgress) {
            if (listState.isScrollInProgress) {
                swipeGesturesReady = false
            } else {
                // A short idle gate prevents the same diagonal gesture or settling fling from
                // being reinterpreted as a horizontal mail action.
                delay(140)
                swipeGesturesReady = true
            }
        }
        ObserveLazyListChromeVisibility(
            listState = listState,
            visible = chromeVisible,
            onVisibilityChanged = onChromeVisibilityChanged,
            enabled = chromeControllerEnabled && !inSelectionMode && !searchOverlayActive,
            onScrollInProgressChanged = UiPerformanceGate::setMailListScrolling,
        )
        ElasticRefreshContainer(
            refreshing = busy,
            onRefresh = viewModel::refresh,
            enabled = !inSelectionMode,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            val touchSlop = viewConfiguration.touchSlop
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial,
                                )
                                var totalX = 0f
                                var totalY = 0f
                                var directionResolved = false
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: event.changes.firstOrNull()
                                            ?: break
                                        if (!change.pressed) break
                                        totalX += change.position.x - change.previousPosition.x
                                        totalY += change.position.y - change.previousPosition.y
                                        if (
                                            !directionResolved &&
                                            maxOf(abs(totalX), abs(totalY)) >= touchSlop
                                        ) {
                                            directionResolved = true
                                            // Treat an ambiguous diagonal gesture as vertical.
                                            // Horizontal mail actions only win when X is clearly
                                            // dominant, which prevents a normal list scroll from
                                            // exposing read/star actions.
                                            verticalPointerGesture =
                                                abs(totalX) <= abs(totalY) * 1.35f
                                        }
                                    }
                                } finally {
                                    verticalPointerGesture = false
                                }
                            }
                        },
                    contentPadding = PaddingValues(
                        top = topChromeHeight + 6.dp,
                        bottom = listBottomContentPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(MailContentDefaults.ItemSpacing),
                ) {
                item(key = "folder-strip") {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(folderItems, key = { it.id }) { folder ->
                            FolderChip(
                                label = folder.label,
                                icon = folder.icon,
                                selected = currentFolder == folder.id,
                                onClick = { viewModel.selectFolder(folder.id) },
                            )
                        }
                    }
                }

                item(key = "notification-permission") {
                    Box(Modifier.fillMaxWidth()) {
                        AnimatedVisibility(
                            visible = !notificationPermissionGranted && showPermissionGuide,
                            enter = expandVertically(
                                expandFrom = Alignment.Top,
                                animationSpec = tween(260),
                            ) + fadeIn(animationSpec = tween(180)),
                            exit = shrinkVertically(
                                shrinkTowards = Alignment.Top,
                                animationSpec = tween(260),
                            ) + fadeOut(animationSpec = tween(150)),
                        ) {
                            Box(Modifier.padding(horizontal = 8.dp)) {
                                NotificationPermissionCard(
                                    onAllow = onRequestNotificationPermission,
                                    onReject = onDismissPermissionGuide,
                                )
                            }
                        }
                    }
                }

                if (messages.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(320.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (!contentReady && busy) tr("refreshing") else tr("no_messages"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = messages,
                        key = { _, message -> message.id },
                        contentType = { _, _ -> "mail-card" },
                    ) { index, message ->
                        val messageSelected = selectedIds.contains(message.id)
                        val itemShape = MailContentDefaults.animatedItemShape(
                            index = index,
                            itemCount = messages.size,
                            selected = messageSelected,
                        )
                        val dismissState = rememberSwipeToDismissBoxState(
                            positionalThreshold = { totalDistance -> totalDistance * 0.50f },
                            confirmValueChange = { value ->
                                if (inSelectionMode) return@rememberSwipeToDismissBoxState false
                                when (currentFolder) {
                                    "DRAFTS" -> when (value) {
                                        SwipeToDismissBoxValue.StartToEnd -> onOpenMessage(message)
                                        SwipeToDismissBoxValue.EndToStart -> viewModel.delete(message)
                                        else -> Unit
                                    }
                                    "TRASH" -> when (value) {
                                        SwipeToDismissBoxValue.StartToEnd -> onReplyMessage(message)
                                        SwipeToDismissBoxValue.EndToStart ->
                                            viewModel.permanentlyDelete(message)
                                        else -> Unit
                                    }
                                    else -> when (value) {
                                        // Inbox and Spam share read/star gestures.
                                        SwipeToDismissBoxValue.StartToEnd ->
                                            viewModel.toggleUnread(message)
                                        SwipeToDismissBoxValue.EndToStart ->
                                            viewModel.toggleStarred(message)
                                        else -> Unit
                                    }
                                }
                                false
                            },
                        )
                        val animateArrival = remember(message.id) {
                            revealedMessageIds.add(message.id)
                        }
                        ProgressiveMailRow(
                            messageId = message.id,
                            animateArrival = animateArrival,
                            staggerIndex = index.coerceAtMost(7),
                            modifier = Modifier.animateItem(),
                        ) {
                            SwipeToDismissBox(
                                state = dismissState,
                                modifier = Modifier.padding(horizontal = MailContentDefaults.HorizontalInset),
                                gesturesEnabled = swipeGesturesReady &&
                                    !verticalPointerGesture &&
                                    !inSelectionMode &&
                                    (
                                        message.deliveryState == "REMOTE" ||
                                            currentFolder == "DRAFTS"
                                        ),
                                backgroundContent = {
                                    SwipeActionBackground(
                                        message = message,
                                        folderType = currentFolder,
                                        direction = dismissState.dismissDirection,
                                        progress = dismissState.progress,
                                        shape = itemShape,
                                    )
                                },
                            ) {
                                MessageCard(
                                    message = message,
                                    account = accountById[message.accountId],
                                    contactAvatarText = contactAvatarByEmail[
                                        message.contactAddressKey()
                                    ],
                                    density = settings.density,
                                    monetBrandIcons = settings.dynamicColor && settings.monetBrandIcons,
                                    selected = messageSelected,
                                    selectionMode = inSelectionMode,
                                    shape = itemShape,
                                    onOpen = {
                                        if (inSelectionMode) toggleSelection(message) else onOpenMessage(message)
                                    },
                                    onLongClick = { toggleSelection(message) },
                                    onStar = { viewModel.toggleStarred(message) },
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showScrollToTop && !inSelectionMode && !searchOverlayActive,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = scrollToTopBottomPadding),
            enter = fadeIn(tween(150)) +
                scaleIn(initialScale = 0.78f, animationSpec = tween(180)) +
                slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(180)),
            exit = fadeOut(tween(120)) +
                scaleOut(targetScale = 0.80f, animationSpec = tween(150)) +
                slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(150)),
        ) {
            FloatingCircleAction(
                onClick = {
                    scrollScope.launch {
                        listState.animateToTopWithMomentum(motionEnabled)
                    }
                },
                modifier = Modifier.size(48.dp),
                containerColor = MaterialTheme.bondSurfaces.popup.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = tr("scroll_to_top"),
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = topChromeOffset.toPx() },
            color = MaterialTheme.bondSurfaces.chrome,
            tonalElevation = 0.dp,
            // A full-width physical shadow is copied into the frozen reader backdrop as a dark
            // rectangular strip in light mode. The chrome/page color boundary is sufficient.
            shadowElevation = 0.dp,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    AnimatedContent(
                        targetState = inSelectionMode,
                        transitionSpec = { bondFadeThrough(motionEnabled) },
                        label = "top-bar-fade-through",
                    ) { selecting ->
                        if (selecting) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = ::clearSelection) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = tr("back"),
                                    )
                                }
                                Text(
                                    "${if (selectedIds.isNotEmpty()) selectedIds.size else selectionDisplayCount} ${tr("selected_count")}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        if (allVisibleSelected) {
                                            clearSelection()
                                        } else {
                                            selectedIds.clear()
                                            selectedIds.addAll(messages.map { message -> message.id })
                                        }
                                    },
                                ) {
                                    Icon(
                                        if (allVisibleSelected) Icons.Default.Close else Icons.Default.SelectAll,
                                        contentDescription = tr(
                                            if (allVisibleSelected) "cancel_select_all" else "select_all",
                                        ),
                                    )
                                }
                                when (currentFolder) {
                                    "TRASH" -> {
                                        IconButton(
                                            onClick = {
                                                viewModel.moveToInbox(selectedMessages.toList())
                                                clearSelection()
                                            },
                                        ) {
                                            Icon(
                                                Icons.Default.RestoreFromTrash,
                                                contentDescription = tr("restore_mail"),
                                            )
                                        }
                                        IconButton(onClick = { confirmDeleteSelection = true }) {
                                            Icon(
                                                Icons.Default.DeleteForever,
                                                contentDescription = tr("delete_permanently"),
                                            )
                                        }
                                    }
                                    else -> {
                                        if (currentFolder == "SPAM") {
                                            IconButton(
                                                onClick = {
                                                    viewModel.moveToInbox(selectedMessages.toList())
                                                    clearSelection()
                                                },
                                            ) {
                                                Icon(
                                                    Icons.Default.Inbox,
                                                    contentDescription = tr("move_to_inbox"),
                                                )
                                            }
                                        }
                                        IconButton(onClick = { confirmDeleteSelection = true }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = tr(
                                                    if (currentFolder == "DRAFTS") {
                                                        "discard_draft"
                                                    } else {
                                                        "delete"
                                                    },
                                                ),
                                            )
                                        }
                                        if (currentFolder != "DRAFTS") {
                                            IconButton(
                                                onClick = {
                                                    val target = selectedMessages.toList()
                                                    if (selectionWillMarkRead) {
                                                        viewModel.markAllRead(target)
                                                    } else {
                                                        viewModel.markAllUnread(target)
                                                    }
                                                    clearSelection()
                                                },
                                            ) {
                                                Icon(
                                                    if (selectionWillMarkRead) {
                                                        Icons.Default.MarkEmailRead
                                                    } else {
                                                        Icons.Default.MarkEmailUnread
                                                    },
                                                    contentDescription = tr(
                                                        if (selectionWillMarkRead) {
                                                            "mark_read"
                                                        } else {
                                                            "mark_unread"
                                                        },
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TopActionButton(
                                    onClick = onOpenDrawer,
                                    containerColor = Color.Transparent,
                                ) {
                                    Icon(
                                        Icons.Default.Menu,
                                        contentDescription = tr("open_navigation_drawer"),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    selectedAccount?.displayName ?: tr("mail"),
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Keep the closed search action inside the same translated
                                    // top-bar row as menu/more/add. The overlay only owns the
                                    // container-transform interval, so ordinary scroll hide/show
                                    // cannot drift onto a separate animation path.
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .onGloballyPositioned { coordinates ->
                                                searchSourceBounds = coordinates.boundsInRoot()
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (!searchOverlayActive) {
                                            TopActionButton(
                                                onClick = {
                                                    pendingSearchMessage = null
                                                    showSearch = true
                                                },
                                            ) {
                                                Icon(
                                                    Icons.Default.Search,
                                                    contentDescription = tr("search"),
                                                )
                                            }
                                        }
                                    }
                                    TopActionButton(onClick = onAddAccount) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = tr("add_mailbox"),
                                        )
                                    }
                                    Box {
                                        TopActionButton(onClick = { topMenu = true }) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = tr("more"),
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = topMenu,
                                            onDismissRequest = { topMenu = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(tr("select_all")) },
                                                onClick = {
                                                    selectedIds.clear()
                                                    selectedIds.addAll(messages.map { it.id })
                                                    topMenu = false
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(tr("mark_all_read")) },
                                                onClick = {
                                                    viewModel.markAllRead(messages)
                                                    topMenu = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = busy,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    enter = fadeIn(tween(BondMotionDuration.EffectShort)),
                    exit = fadeOut(tween(BondMotionDuration.EffectShort)),
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                    )
                }
            }
        }

        SearchContainerOverlay(
            expanded = showSearch,
            sourceBounds = searchSourceBounds,
            query = query,
            onQueryChange = { viewModel.searchQuery.value = it },
            results = messages,
            accountById = accountById,
            contactAvatarByEmail = contactAvatarByEmail,
            settings = settings,
            onOpenMessage = { message ->
                pendingSearchMessage = message
                showSearch = false
            },
            onDismiss = {
                pendingSearchMessage = null
                showSearch = false
            },
            onClosed = {
                viewModel.searchQuery.value = ""
                pendingSearchMessage?.let { pending ->
                    pendingSearchMessage = null
                    onOpenMessage(pending)
                }
            },
            onTransformActivityChanged = { active -> searchTransformActive = active },
        )

        AnimatedVisibility(
            visible = topNotice != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(160)),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(120)),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        topNotice.orEmpty(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = { topNotice = null }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = tr("cancel"))
                    }
                }
            }
        }

        if (confirmDeleteSelection) {
            AlertDialog(
                onDismissRequest = { confirmDeleteSelection = false },
                title = {
                    Text(
                        tr(
                            if (currentFolder == "TRASH") {
                                "confirm_permanent_delete_title"
                            } else {
                                "confirm_delete_message_title"
                            },
                        ),
                    )
                },
                text = {
                    Text(
                        when {
                            currentFolder == "TRASH" && selectedIds.size <= 1 ->
                                tr("confirm_permanent_delete_body")
                            currentFolder == "TRASH" ->
                                tr(
                                    "confirm_permanent_delete_messages_body",
                                    selectedIds.size.toString(),
                                )
                            selectedIds.size <= 1 -> tr("confirm_delete_message_body")
                            else -> tr(
                                "confirm_delete_messages_body",
                                selectedIds.size.toString(),
                            )
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val target = messages.filter { it.id in selectedIds }
                            if (currentFolder == "TRASH") {
                                viewModel.permanentlyDeleteMany(target)
                            } else {
                                viewModel.deleteMany(target)
                            }
                            clearSelection()
                            confirmDeleteSelection = false
                        },
                    ) {
                        Text(
                            tr(
                                if (currentFolder == "TRASH") {
                                    "delete_permanently"
                                } else {
                                    "delete"
                                },
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteSelection = false }) { Text(tr("cancel")) }
                },
            )
        }
    }
}

private fun MessageListRow.contactAddressKey(): String {
    val outgoing = folderType == "SENT" || folderType == "DRAFTS"
    val raw = if (outgoing) recipients.substringBefore(',') else senderAddress
    return raw
        .trim()
        .substringAfterLast('<')
        .substringBefore('>')
        .trim()
        .lowercase()
}

@Composable
private fun ProgressiveMailRow(
    messageId: String,
    animateArrival: Boolean,
    staggerIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motionEnabled = bondMotionEnabled()
    var visible by remember(messageId) {
        mutableStateOf(!animateArrival || !motionEnabled)
    }

    LaunchedEffect(messageId, animateArrival, motionEnabled) {
        if (animateArrival && motionEnabled) {
            // 18 ms is less than two frames on a 90 Hz panel and looked like one bulk flash.
            // Keep each new row at zero height until its turn so the list grows one mail at a time.
            delay(staggerIndex.coerceIn(0, 7) * 90L)
        }
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = tween(
                durationMillis = 260,
                easing = BondMotionEasing.EmphasizedDecelerate,
            ),
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = 210,
                easing = BondMotionEasing.EmphasizedDecelerate,
            ),
        ) + slideInVertically(
            initialOffsetY = { height -> height / 5 },
            animationSpec = tween(
                durationMillis = 260,
                easing = BondMotionEasing.EmphasizedDecelerate,
            ),
        ),
    ) {
        Box(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun ElasticRefreshContainer(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val motionEnabled = bondMotionEnabled()
    val triggerPx = with(density) { 80.dp.toPx() }
    val maximumPx = with(density) { 128.dp.toPx() }
    var pullOffsetPx by remember { mutableFloatStateOf(0f) }
    var thresholdHapticSent by remember { mutableStateOf(false) }
    val latestRefreshing by rememberUpdatedState(refreshing)
    val latestOnRefresh by rememberUpdatedState(onRefresh)
    val latestEnabled by rememberUpdatedState(enabled)

    suspend fun settle(target: Float, initialVelocity: Float = 0f) {
        if (!motionEnabled) {
            pullOffsetPx = target
            return
        }
        animate(
            initialValue = pullOffsetPx,
            targetValue = target,
            initialVelocity = initialVelocity,
            animationSpec = BondMotionSpring.Settle,
        ) { value, _ -> pullOffsetPx = value }
    }

    val nestedScrollConnection = remember(triggerPx, maximumPx, motionEnabled) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!latestEnabled) return Offset.Zero
                if (available.y < 0f && pullOffsetPx > 0f && !latestRefreshing) {
                    val consumedY = available.y.coerceAtLeast(-pullOffsetPx)
                    pullOffsetPx = (pullOffsetPx + consumedY).coerceAtLeast(0f)
                    if (pullOffsetPx < triggerPx) thresholdHapticSent = false
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!latestEnabled) return Offset.Zero
                if (
                    source == NestedScrollSource.UserInput &&
                    available.y > 0f &&
                    !latestRefreshing
                ) {
                    val fraction = (pullOffsetPx / maximumPx).coerceIn(0f, 1f)
                    val resistance = (0.56f - fraction * 0.38f).coerceAtLeast(0.18f)
                    pullOffsetPx = (pullOffsetPx + available.y * resistance).coerceAtMost(maximumPx)
                    val armed = pullOffsetPx >= triggerPx
                    if (armed && !thresholdHapticSent) {
                        thresholdHapticSent = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    } else if (!armed) {
                        thresholdHapticSent = false
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!latestEnabled) return Velocity.Zero
                if (latestRefreshing || pullOffsetPx <= 0f) return Velocity.Zero
                val shouldRefresh = pullOffsetPx >= triggerPx
                if (shouldRefresh) latestOnRefresh()
                settle(target = 0f, initialVelocity = available.y)
                thresholdHapticSent = false
                return Velocity(0f, available.y)
            }
        }
    }

    LaunchedEffect(refreshing) {
        if (!refreshing && pullOffsetPx > 0f) settle(0f)
        if (!refreshing) thresholdHapticSent = false
    }

    LaunchedEffect(enabled) {
        if (!enabled && pullOffsetPx > 0f) settle(0f)
        if (!enabled) thresholdHapticSent = false
    }

    // Pulling still moves the list with resistance and threshold haptics, but deliberately draws no
    // second spinner. Once released, the existing 3dp progress line in the top chrome is the only
    // synchronization indicator.
    Box(modifier.nestedScroll(nestedScrollConnection)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, pullOffsetPx.roundToInt()) },
        ) {
            content()
        }
    }
}

@Composable
private fun SwipeActionBackground(
    message: MessageListRow,
    folderType: String,
    direction: SwipeToDismissBoxValue,
    progress: Float,
    shape: Shape,
) {
    val startAction = direction == SwipeToDismissBoxValue.StartToEnd
    val endAction = direction == SwipeToDismissBoxValue.EndToStart
    val destructiveAction = endAction && folderType in setOf("DRAFTS", "TRASH")
    val background = when {
        startAction -> MaterialTheme.colorScheme.secondaryContainer
        destructiveAction -> MaterialTheme.colorScheme.errorContainer
        endAction -> MaterialTheme.colorScheme.tertiaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        startAction -> MaterialTheme.colorScheme.onSecondaryContainer
        destructiveAction -> MaterialTheme.colorScheme.onErrorContainer
        endAction -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> Color.Transparent
    }
    val iconScale = 0.72f + progress.coerceIn(0f, 1f) * 0.28f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(background)
            .padding(horizontal = 24.dp),
        contentAlignment = if (startAction) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        if (startAction || endAction) {
            Icon(
                imageVector = when {
                    folderType == "DRAFTS" && startAction -> Icons.Default.Edit
                    folderType == "DRAFTS" -> Icons.Default.Delete
                    folderType == "TRASH" && startAction -> Icons.AutoMirrored.Filled.Reply
                    folderType == "TRASH" -> Icons.Default.DeleteForever
                    startAction && message.unread -> Icons.Default.MarkEmailRead
                    startAction -> Icons.Default.MarkEmailUnread
                    message.starred -> Icons.Outlined.StarBorder
                    else -> Icons.Default.Star
                },
                contentDescription = when {
                    folderType == "DRAFTS" && startAction -> tr("continue_draft")
                    folderType == "DRAFTS" -> tr("discard_draft")
                    folderType == "TRASH" && startAction -> tr("reply")
                    folderType == "TRASH" -> tr("delete_permanently")
                    startAction && message.unread -> tr("mark_read")
                    startAction -> tr("mark_unread")
                    message.starred -> tr("unstar")
                    else -> tr("star")
                },
                tint = contentColor,
                modifier = Modifier.graphicsLayer {
                    alpha = progress.coerceIn(0f, 1f)
                    scaleX = iconScale
                    scaleY = iconScale
                },
            )
        }
    }
}

@Composable
private fun TopActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.bondSurfaces.popup,
    content: @Composable () -> Unit,
) {
    val pressResetter = rememberBondPressResetter()
    key(pressResetter.epoch) {
        val motionEnabled = bondMotionEnabled()
        val interactionSource = rememberBondPressInteraction()
        val pressScale by rememberBondPressScale(
            interactionSource = interactionSource,
            pressedScale = 0.90f,
            enabled = motionEnabled,
        )
        Surface(
            onClick = { pressResetter.resetThen(onClick) },
            modifier = modifier
                .size(44.dp)
                .bondPressTransform(pressScale),
            shape = CircleShape,
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            tonalElevation = 0.dp,
            interactionSource = interactionSource,
        ) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
    }
}

private data class FolderUi(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
private fun FolderChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val width by animateDpAsState(
        targetValue = if (selected) 122.dp else 52.dp,
        animationSpec = tween(220),
        label = "folder-chip-width",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(if (selected) 190 else 90),
        label = "folder-chip-label",
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.width(width).height(52.dp),
        shape = RoundedCornerShape(26.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.bondSurfaces.section,
        border = if (selected) null else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (selected) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
            if (selected) {
                Text(
                    label,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .graphicsLayer { alpha = labelAlpha },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SearchContainerOverlay(
    expanded: Boolean,
    sourceBounds: Rect?,
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<MessageListRow>,
    accountById: Map<String, AccountEntity>,
    contactAvatarByEmail: Map<String, String?>,
    settings: AppSettings,
    onOpenMessage: (MessageListRow) -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
    onTransformActivityChanged: (Boolean) -> Unit,
) {
    val motionEnabled = bondMotionEnabled()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    var searchHasFocus by remember { mutableStateOf(false) }
    var hasOpened by remember { mutableStateOf(expanded) }
    val latestOnClosed by rememberUpdatedState(onClosed)
    val latestOnTransformActivityChanged by rememberUpdatedState(onTransformActivityChanged)

    val normalizedProgress = progress.value.coerceIn(0f, 1f)
    val transformActive = expanded || progress.isRunning || normalizedProgress > 0.001f
    val showCollapsedContainer = transformActive

    BackHandler(enabled = transformActive) { onDismiss() }

    LaunchedEffect(expanded, motionEnabled) {
        if (expanded) {
            hasOpened = true
            latestOnTransformActivityChanged(true)
            if (motionEnabled) {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = BondMotionDuration.ContainerTransform,
                        easing = BondMotionEasing.EmphasizedDecelerate,
                    ),
                )
            } else {
                progress.snapTo(1f)
            }
            if (expanded && !searchHasFocus) {
                searchHasFocus = true
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        } else {
            // Keep the keyboard and the expanded root constraints in place until the container has
            // returned to its source. Hiding the IME at the beginning of the reverse animation
            // resizes the root mid-flight and causes the one-frame jump seen in the recording.
            if (motionEnabled && progress.value > 0.001f) {
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = BondMotionDuration.SharedAxis,
                        easing = BondMotionEasing.EmphasizedAccelerate,
                    ),
                )
            } else {
                progress.snapTo(0f)
            }
            if (searchHasFocus) {
                searchHasFocus = false
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
            latestOnTransformActivityChanged(false)
            if (hasOpened) {
                hasOpened = false
                latestOnClosed()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            latestOnTransformActivityChanged(false)
            if (searchHasFocus) {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val fallbackSourceX = (maxWidth - 152.dp).coerceAtLeast(12.dp)
        val sourceX = sourceBounds?.let { with(density) { it.left.toDp() } } ?: fallbackSourceX
        // boundsInRoot already includes the top bar's graphics-layer translation. Adding the
        // animated offset a second time made search travel twice as far as menu/more/add.
        val sourceY = sourceBounds?.let { with(density) { it.top.toDp() } } ?: statusInset + 12.dp
        val sourceWidth = sourceBounds?.let { with(density) { it.width.toDp() } } ?: 44.dp
        val sourceHeight = sourceBounds?.let { with(density) { it.height.toDp() } } ?: 44.dp
        val targetX = 12.dp
        val targetY = statusInset + 8.dp
        val targetWidth = (maxWidth - 24.dp).coerceAtLeast(56.dp)
        val targetHeight = 56.dp

        fun interpolate(start: Dp, end: Dp): Dp =
            start + (end - start) * normalizedProgress

        val searchX = interpolate(sourceX, targetX)
        val searchY = interpolate(sourceY, targetY)
        val searchWidth = interpolate(sourceWidth, targetWidth)
        val searchHeight = interpolate(sourceHeight, targetHeight)
        val cornerRadius = interpolate(sourceHeight / 2f, 28.dp)
        val iconX = interpolate(
            ((sourceWidth - 24.dp) / 2f).coerceAtLeast(0.dp),
            16.dp,
        )
        val scrimAlpha = 0.34f * normalizedProgress
        val fieldAlpha = ((normalizedProgress - 0.18f) / 0.42f).coerceIn(0f, 1f)
        val panelAlpha = ((normalizedProgress - 0.28f) / 0.44f).coerceIn(0f, 1f)
        val panelScale = 0.985f + 0.015f * panelAlpha

        if (transformActive && scrimAlpha > 0.001f) {
            val noRipple = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = noRipple,
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        if (transformActive && normalizedProgress > 0.12f) {
            Surface(
                modifier = Modifier
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = statusInset + 72.dp,
                        bottom = 12.dp,
                    )
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = panelAlpha
                        scaleX = panelScale
                        scaleY = panelScale
                    },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.bondSurfaces.content,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
            ) {
                when {
                    query.isBlank() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(tr("search"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    results.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(tr("no_search_results"))
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 10.dp),
                            contentPadding = PaddingValues(
                                start = MailContentDefaults.HorizontalInset,
                                end = MailContentDefaults.HorizontalInset,
                                bottom = 18.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(MailContentDefaults.ItemSpacing),
                        ) {
                            itemsIndexed(
                                items = results,
                                key = { _, message -> message.id },
                            ) { index, message ->
                                MessageCard(
                                    message = message,
                                    account = accountById[message.accountId],
                                    contactAvatarText = contactAvatarByEmail[
                                        message.contactAddressKey()
                                    ],
                                    density = settings.density,
                                    monetBrandIcons = settings.dynamicColor && settings.monetBrandIcons,
                                    shape = MailContentDefaults.itemShape(index, results.size),
                                    onOpen = { onOpenMessage(message) },
                                    onLongClick = {},
                                    onStar = {},
                                )
                            }
                        }
                    }
                }
            }
        }

        // During the transform this Surface temporarily takes ownership from the top-bar button.
        // Outside the transform, the real button stays in the shared top-bar row and therefore uses
        // exactly the same scroll translation as its siblings.
        if (showCollapsedContainer) {
            Surface(
                modifier = Modifier
                    .offset(x = searchX, y = searchY)
                    .size(width = searchWidth, height = searchHeight),
                shape = RoundedCornerShape(cornerRadius),
                color = MaterialTheme.bondSurfaces.popup,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 0.dp,
                shadowElevation = 6.dp * fieldAlpha,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = tr("search"),
                        modifier = Modifier
                            .offset(x = iconX)
                            .size(24.dp),
                    )
                    if (transformActive && fieldAlpha > 0.001f) {
                        TextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(focusRequester)
                                .graphicsLayer { alpha = fieldAlpha },
                            singleLine = true,
                            placeholder = { Text(tr("search")) },
                            leadingIcon = { Spacer(Modifier.size(24.dp)) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (query.isNotEmpty()) onQueryChange("") else onDismiss()
                                    },
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = tr("cancel"))
                                }
                            },
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationPermissionCard(
    onAllow: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("notification_permission_title"), style = MaterialTheme.typography.titleMedium)
            Text(
                tr("notification_permission_desc"),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.End),
            ) {
                TextButton(onClick = onReject) {
                    Text(tr("notification_permission_reject"))
                }
                Button(onClick = onAllow) {
                    Text(tr("notification_permission_allow"))
                }
            }
        }
    }
}
