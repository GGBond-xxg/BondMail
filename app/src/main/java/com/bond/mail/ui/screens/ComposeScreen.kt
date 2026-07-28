package com.bond.mail.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bond.mail.ui.ComposeViewModel
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.motion.BondMotionDuration
import com.bond.mail.ui.motion.BondMotionEasing
import com.bond.mail.ui.theme.bondSurfaces
import kotlinx.coroutines.launch

private data class SelectedAttachment(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?,
)

private val commonRecipientDomains = listOf(
    "gmail.com",
    "outlook.com",
    "qq.com",
    "163.com",
    "icloud.com",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    viewModel: ComposeViewModel,
    initialAccountId: String = "",
    initialTo: String,
    initialCc: String = "",
    initialBcc: String = "",
    initialSubject: String,
    initialBody: String,
    initialAttachmentUris: List<String> = emptyList(),
    draftTaskId: String? = null,
    sourceMessageId: String? = null,
    onBack: () -> Unit,
    onQueued: () -> Unit,
) {
    val accounts by viewModel.accounts.collectAsState()
    val savedContacts by viewModel.savedContacts.collectAsState()
    val sending by viewModel.sending.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var accountId by rememberSaveable { mutableStateOf(initialAccountId) }
    var accountMenu by remember { mutableStateOf(false) }
    var to by rememberSaveable { mutableStateOf(initialTo) }
    var cc by rememberSaveable { mutableStateOf(initialCc) }
    var bcc by rememberSaveable { mutableStateOf(initialBcc) }
    var subject by rememberSaveable { mutableStateOf(initialSubject) }
    var body by rememberSaveable { mutableStateOf(initialBody) }
    var recipientExtrasExpanded by rememberSaveable { mutableStateOf(false) }
    var recipientFocused by remember { mutableStateOf(false) }
    var queuedClose by remember { mutableStateOf(false) }
    var resolvedClose by remember { mutableStateOf(false) }
    var sheetHasOpened by remember { mutableStateOf(false) }
    var showDraftDecision by remember { mutableStateOf(false) }
    val attachments = remember { mutableStateListOf<SelectedAttachment>() }

    // ComposeScreen is reused by FAB, contact, reply and forward entry points. Explicitly seed the
    // draft whenever a new invocation enters composition so a previous draft never flashes first.
    LaunchedEffect(
        initialAccountId,
        initialTo,
        initialCc,
        initialBcc,
        initialSubject,
        initialBody,
        initialAttachmentUris,
        draftTaskId,
        sourceMessageId,
    ) {
        accountId = initialAccountId
        to = initialTo
        cc = initialCc
        bcc = initialBcc
        subject = initialSubject
        body = initialBody
        recipientExtrasExpanded = false
        recipientFocused = false
        attachments.clear()
        initialAttachmentUris.distinct().forEach { raw ->
            runCatching { Uri.parse(raw) }
                .getOrNull()
                ?.let { uri -> attachments += queryAttachment(context.contentResolver, uri) }
        }
    }

    LaunchedEffect(accounts) {
        if (accountId.isBlank() || accounts.none { it.id == accountId }) {
            accountId = accounts.firstOrNull()?.id.orEmpty()
        }
    }

    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { selectedUris ->
        val remainingSlots = (10 - attachments.size).coerceAtLeast(0)
        selectedUris
            .asSequence()
            .filter { uri -> attachments.none { it.uri == uri } }
            .distinct()
            .take(remainingSlots)
            .forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                attachments += queryAttachment(context.contentResolver, uri)
            }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hasMeaningfulDraft = to.isNotBlank() || cc.isNotBlank() || bcc.isNotBlank() ||
            subject.isNotBlank() || body.isNotBlank() || attachments.isNotEmpty()
        val confirmDraftClose by rememberUpdatedState(
            hasMeaningfulDraft && !queuedClose && !resolvedClose,
        )
        val sheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Hidden,
            skipHiddenState = false,
            confirmValueChange = { target ->
                when {
                    target == SheetValue.Hidden && confirmDraftClose -> {
                        showDraftDecision = true
                        false
                    }
                    else -> true
                }
            },
        )
        val scaffoldState = rememberBottomSheetScaffoldState(sheetState)
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val sheetTopInset = statusBarTop + 8.dp
        val availableSheetHeight = (maxHeight - sheetTopInset).coerceAtLeast(1.dp)
        val peekHeight = availableSheetHeight * 0.72f
        val scrimAlpha by animateFloatAsState(
            targetValue = if (sheetState.targetValue == SheetValue.Hidden) 0f else 0.18f,
            animationSpec = tween(
                durationMillis = BondMotionDuration.ElementEnter,
                easing = BondMotionEasing.Standard,
            ),
            label = "compose-sheet-scrim",
        )

        fun queueCurrentMessage() {
            if (sending || accountId.isBlank() || to.isBlank()) return
            viewModel.queue(
                accountId = accountId,
                to = to,
                cc = cc,
                bcc = bcc,
                subject = subject,
                body = body,
                attachmentUris = attachments.map { it.uri.toString() },
                draftTaskId = draftTaskId,
                sourceMessageId = sourceMessageId,
            ) {
                scope.launch {
                    queuedClose = true
                    sheetState.hide()
                }
            }
        }

        fun requestClose() {
            if (hasMeaningfulDraft) {
                showDraftDecision = true
            } else {
                scope.launch { sheetState.hide() }
            }
        }

        fun expandForInput() {
            if (sheetState.targetValue != SheetValue.Expanded) {
                scope.launch { sheetState.expand() }
            }
        }

        LaunchedEffect(sheetState) {
            snapshotFlow { sheetState.currentValue }.collect { value ->
                if (value != SheetValue.Hidden) {
                    sheetHasOpened = true
                } else if (sheetHasOpened) {
                    if (queuedClose) onQueued() else onBack()
                }
            }
        }

        LaunchedEffect(Unit) {
            sheetState.partialExpand()
        }

        // Back always exits the composer, even when the sheet was manually expanded to full height.
        // Dragging remains available for partial <-> expanded, but navigation needs one predictable
        // backward step instead of forcing the user to press Back twice.
        BackHandler { requestClose() }

        BottomSheetScaffold(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = sheetTopInset),
            scaffoldState = scaffoldState,
            sheetPeekHeight = peekHeight,
            sheetMaxWidth = maxWidth,
            sheetShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            sheetContainerColor = MaterialTheme.bondSurfaces.sheet,
            sheetContentColor = MaterialTheme.colorScheme.onSurface,
            sheetTonalElevation = 0.dp,
            sheetShadowElevation = 12.dp,
            sheetDragHandle = null,
            sheetSwipeEnabled = true,
            containerColor = Color.Transparent,
            sheetContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BottomSheetDefaults.DragHandle()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = ::requestClose) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = tr("back"),
                            )
                        }
                        Text(
                            tr("compose_mail"),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Box {
                            IconButton(
                                enabled = !sending && attachments.size < 10,
                                onClick = { attachmentPicker.launch(arrayOf("*/*")) },
                            ) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    contentDescription = tr("add_attachment"),
                                )
                            }
                            if (attachments.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-2).dp, y = 2.dp)
                                        .size(18.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            attachments.size.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(
                            enabled = !sending && accountId.isNotBlank() && to.isNotBlank(),
                            onClick = ::queueCurrentMessage,
                        ) {
                            SendProgressIcon(sending = sending, contentDescription = tr("send"))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            // Keep the sheet chrome fixed when the keyboard opens. Only the
                            // scrollable editor viewport needs to avoid the IME; padding the
                            // entire sheet made the toolbar and field outlines jump upward too.
                            .imePadding()
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box {
                            Surface(
                                onClick = { accountMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.bondSurfaces.input,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        accounts.firstOrNull { it.id == accountId }
                                            ?.let { "${it.displayName} · ${it.email}" }
                                            ?: tr("from"),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Icon(
                                        Icons.Default.ExpandMore,
                                        contentDescription = tr("select_account"),
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = accountMenu,
                                onDismissRequest = { accountMenu = false },
                            ) {
                                accounts.forEach { account ->
                                    DropdownMenuItem(
                                        text = { Text("${account.displayName} · ${account.email}") },
                                        onClick = {
                                            accountId = account.id
                                            accountMenu = false
                                        },
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = to,
                            onValueChange = { to = it },
                            label = { Text(tr("to")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged {
                                    recipientFocused = it.isFocused
                                    if (it.isFocused) expandForInput()
                                },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        recipientExtrasExpanded = !recipientExtrasExpanded
                                    },
                                ) {
                                    Icon(
                                        if (recipientExtrasExpanded) {
                                            Icons.Default.ExpandLess
                                        } else {
                                            Icons.Default.ExpandMore
                                        },
                                        contentDescription = if (recipientExtrasExpanded) {
                                            tr("cancel")
                                        } else {
                                            "${tr("cc")}, ${tr("bcc")}"
                                        },
                                    )
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.bondSurfaces.input,
                                unfocusedContainerColor = MaterialTheme.bondSurfaces.input,
                                disabledContainerColor = MaterialTheme.bondSurfaces.input,
                            ),
                        )

                        AnimatedVisibility(
                            visible = recipientExtrasExpanded,
                            enter = fadeIn(
                                tween(BondMotionDuration.ElementEnter),
                            ) + expandVertically(
                                animationSpec = tween(
                                    BondMotionDuration.ElementEnter,
                                    easing = BondMotionEasing.EmphasizedDecelerate,
                                ),
                            ),
                            exit = fadeOut(
                                tween(BondMotionDuration.EffectShort),
                            ) + shrinkVertically(
                                animationSpec = tween(
                                    BondMotionDuration.EffectShort,
                                    easing = BondMotionEasing.EmphasizedAccelerate,
                                ),
                            ),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = cc,
                                    onValueChange = { cc = it },
                                    label = { Text(tr("cc")) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { if (it.isFocused) expandForInput() },
                                    singleLine = true,
                                    shape = RoundedCornerShape(18.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.bondSurfaces.input,
                                        unfocusedContainerColor = MaterialTheme.bondSurfaces.input,
                                        disabledContainerColor = MaterialTheme.bondSurfaces.input,
                                    ),
                                )
                                OutlinedTextField(
                                    value = bcc,
                                    onValueChange = { bcc = it },
                                    label = { Text(tr("bcc")) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { if (it.isFocused) expandForInput() },
                                    singleLine = true,
                                    shape = RoundedCornerShape(18.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.bondSurfaces.input,
                                        unfocusedContainerColor = MaterialTheme.bondSurfaces.input,
                                        disabledContainerColor = MaterialTheme.bondSurfaces.input,
                                    ),
                                )
                            }
                        }

                        val contactSuggestions = remember(to, recipientFocused, savedContacts) {
                            if (!recipientFocused) {
                                emptyList()
                            } else {
                                val token = currentRecipientToken(to)
                                if (token.isBlank()) emptyList()
                                else savedContacts
                                    .asSequence()
                                    .filter { contact ->
                                        contact.name.contains(token, ignoreCase = true) ||
                                            contact.email.contains(token, ignoreCase = true)
                                    }
                                    .take(4)
                                    .toList()
                            }
                        }
                        AnimatedVisibility(
                            visible = contactSuggestions.isNotEmpty(),
                            enter = fadeIn(tween(BondMotionDuration.EffectShort)) +
                                expandVertically(animationSpec = tween(BondMotionDuration.ElementEnter)),
                            exit = fadeOut(tween(BondMotionDuration.EffectQuick)) +
                                shrinkVertically(animationSpec = tween(BondMotionDuration.EffectShort)),
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.bondSurfaces.section,
                            ) {
                                Column {
                                    contactSuggestions.forEach { contact ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    to = applyContactRecipient(to, contact.email)
                                                }
                                                .padding(horizontal = 16.dp, vertical = 11.dp),
                                        ) {
                                            Text(
                                                contact.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                contact.email,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val domainSuggestions = remember(to, recipientFocused) {
                            if (recipientFocused) recipientDomainSuggestions(to) else emptyList()
                        }
                        AnimatedVisibility(
                            visible = domainSuggestions.isNotEmpty(),
                            enter = fadeIn(tween(BondMotionDuration.EffectShort)) +
                                expandVertically(
                                    animationSpec = tween(BondMotionDuration.ElementEnter),
                                ),
                            exit = fadeOut(tween(BondMotionDuration.EffectQuick)) +
                                shrinkVertically(
                                    animationSpec = tween(BondMotionDuration.EffectShort),
                                ),
                        ) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(domainSuggestions, key = { it }) { domain ->
                                    SuggestionChip(
                                        onClick = { to = applyRecipientDomain(to, domain) },
                                        label = { Text("@$domain") },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.bondSurfaces.section,
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    )
                                }
                            }
                        }

                        if (attachments.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(attachments, key = { it.uri.toString() }) { attachment ->
                                    InputChip(
                                        selected = true,
                                        onClick = {},
                                        label = {
                                            Column {
                                                Text(
                                                    attachment.displayName,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                attachment.sizeBytes?.let { bytes ->
                                                    Text(
                                                        formatAttachmentSize(bytes),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = { attachments.remove(attachment) },
                                                modifier = Modifier.size(28.dp),
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = tr("remove_attachment"),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = { Text(tr("subject")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { if (it.isFocused) expandForInput() },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.bondSurfaces.input,
                                unfocusedContainerColor = MaterialTheme.bondSurfaces.input,
                                disabledContainerColor = MaterialTheme.bondSurfaces.input,
                            ),
                        )
                        OutlinedTextField(
                            value = body,
                            onValueChange = { body = it },
                            label = { Text(tr("body")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { if (it.isFocused) expandForInput() }
                                // A 250 dp field cannot fit together with the headers and IME,
                                // so focus relocation scrolls the whole sheet far past its top.
                                // Bound the editor and let OutlinedTextField scroll long bodies
                                // internally while keeping its complete outline visible.
                                .heightIn(min = 140.dp, max = 220.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.bondSurfaces.input,
                                unfocusedContainerColor = MaterialTheme.bondSurfaces.input,
                                disabledContainerColor = MaterialTheme.bondSurfaces.input,
                            ),
                        )
                        error?.let {
                            Text(
                                tr(it.key, *it.args.toTypedArray()),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        // Bottom send/attachment actions were intentionally removed. The stable
                        // top app bar contains both actions and never changes the sheet's height.
                        Box(Modifier.height(24.dp))
                    }
                }
            },
        ) { _ ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        indication = null,
                        interactionSource = remember {
                            androidx.compose.foundation.interaction.MutableInteractionSource()
                        },
                    ) {
                        requestClose()
                    },
            )
        }

        if (showDraftDecision) {
            AlertDialog(
                onDismissRequest = { showDraftDecision = false },
                title = { Text(tr("save_draft_title")) },
                text = { Text(tr("save_draft_message")) },
                confirmButton = {
                    TextButton(
                        enabled = !sending && accountId.isNotBlank(),
                        onClick = {
                            viewModel.saveDraft(
                                accountId = accountId,
                                to = to,
                                cc = cc,
                                bcc = bcc,
                                subject = subject,
                                body = body,
                                attachmentUris = attachments.map { it.uri.toString() },
                                existingTaskId = draftTaskId,
                                sourceMessageId = sourceMessageId,
                            ) {
                                showDraftDecision = false
                                resolvedClose = true
                                scope.launch { sheetState.hide() }
                            }
                        },
                    ) { Text(tr("save_draft")) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { showDraftDecision = false }) {
                            Text(tr("cancel"))
                        }
                        TextButton(
                            enabled = !sending,
                            onClick = {
                                viewModel.discardDraft(draftTaskId, sourceMessageId) {
                                    showDraftDecision = false
                                    resolvedClose = true
                                    scope.launch { sheetState.hide() }
                                }
                            },
                        ) {
                            Text(tr("discard_draft"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        }
    }
}

private fun recipientDomainSuggestions(input: String): List<String> {
    val tokenStart = maxOf(input.lastIndexOf(','), input.lastIndexOf(';')) + 1
    val token = input.substring(tokenStart).trim()
    if (token.isBlank() || token.any { character -> character.isWhitespace() }) return emptyList()

    val localPart = token.substringBefore('@').trim()
    if (localPart.isBlank()) return emptyList()

    val hasAt = '@' in token
    val typedDomain = if (hasAt) token.substringAfter('@').trim().lowercase() else ""
    if (hasAt && commonRecipientDomains.any { it.equals(typedDomain, ignoreCase = true) }) {
        return emptyList()
    }

    return commonRecipientDomains
        .asSequence()
        .filter { domain -> typedDomain.isBlank() || domain.startsWith(typedDomain, ignoreCase = true) }
        .take(3)
        .toList()
}

private fun currentRecipientToken(input: String): String {
    val tokenStart = maxOf(input.lastIndexOf(','), input.lastIndexOf(';')) + 1
    return input.substring(tokenStart).trim()
}

private fun applyContactRecipient(input: String, email: String): String {
    val tokenStart = maxOf(input.lastIndexOf(','), input.lastIndexOf(';')) + 1
    val prefix = input.substring(0, tokenStart)
    val spacing = if (prefix.isNotEmpty() && !prefix.endsWith(' ')) " " else ""
    return "$prefix$spacing$email"
}

private fun applyRecipientDomain(input: String, domain: String): String {
    val tokenStart = maxOf(input.lastIndexOf(','), input.lastIndexOf(';')) + 1
    val prefix = input.substring(0, tokenStart)
    val token = input.substring(tokenStart).trim()
    val localPart = token.substringBefore('@').trim()
    if (localPart.isBlank()) return input

    val spacing = if (prefix.isNotEmpty() && !prefix.endsWith(' ')) " " else ""
    return "$prefix$spacing$localPart@$domain"
}

private fun queryAttachment(
    resolver: android.content.ContentResolver,
    uri: Uri,
): SelectedAttachment = runCatching {
    var displayName = "attachment"
    var sizeBytes: Long? = null
    resolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                displayName = cursor.getString(nameIndex).orEmpty().ifBlank { "attachment" }
            }
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
        }
    }
    SelectedAttachment(uri = uri, displayName = displayName, sizeBytes = sizeBytes)
}.getOrElse {
    SelectedAttachment(uri = uri, displayName = "attachment", sizeBytes = null)
}

private fun formatAttachmentSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
}

@Composable
private fun SendProgressIcon(sending: Boolean, contentDescription: String?) {
    if (sending) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
    } else {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = contentDescription)
    }
}
