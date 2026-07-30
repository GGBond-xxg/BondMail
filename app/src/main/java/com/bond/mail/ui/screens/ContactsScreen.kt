package com.bond.mail.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bond.mail.AppContainer
import com.bond.mail.data.db.SavedContactEntity
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.ui.components.ContactAvatar
import com.bond.mail.ui.components.GroupedListSurface
import com.bond.mail.ui.components.FloatingCircleAction
import com.bond.mail.ui.components.MailContentDefaults
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.motion.ObserveLazyListChromeVisibility
import com.bond.mail.ui.motion.BondMotionDuration
import com.bond.mail.ui.motion.BondMotionEasing
import com.bond.mail.ui.motion.animateChromeOffset
import com.bond.mail.ui.motion.animateToTopWithMomentum
import com.bond.mail.ui.motion.bondMotionEnabled
import com.bond.mail.ui.theme.bondSurfaces
import kotlinx.coroutines.launch

@Composable
fun ContactsScreen(
    container: AppContainer,
    settings: AppSettings,
    onCompose: (String) -> Unit,
    chromeVisible: Boolean,
    onChromeVisibilityChanged: (Boolean) -> Unit,
    chromeControllerEnabled: Boolean = true,
) {
    val contacts by container.repository.contacts.collectAsState(
        initial = container.repository.startupContactsSnapshot(),
    )
    val savedContacts by container.repository.savedContacts.collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var showAddContact by rememberSaveable { mutableStateOf(false) }
    var editingContactId by rememberSaveable { mutableStateOf<String?>(null) }
    var contactName by rememberSaveable { mutableStateOf("") }
    var contactEmail by rememberSaveable { mutableStateOf("") }
    var contactAvatar by rememberSaveable { mutableStateOf("") }
    var contactError by remember { mutableStateOf<String?>(null) }
    var contactSaving by remember { mutableStateOf(false) }
    var deleteContactTarget by remember { mutableStateOf<SavedContactEntity?>(null) }
    var contactDeleting by remember { mutableStateOf(false) }
    var deleteContactError by remember { mutableStateOf<String?>(null) }
    val invalidContactLabel = tr("invalid_contact")
    val invalidContactAvatarLabel = tr("error_contact_avatar_single_glyph")
    val deleteContactFailureLabel = tr("error_delete_contact_failed")
    val frequentContacts = remember(savedContacts, query) {
        savedContacts.filter { contact ->
            query.isBlank() ||
                contact.name.contains(query, ignoreCase = true) ||
                contact.email.contains(query, ignoreCase = true)
        }
    }
    val filteredContacts = remember(contacts, savedContacts, query) {
        val savedAddresses = savedContacts.mapTo(mutableSetOf()) { it.email.lowercase() }
        contacts
            .asSequence()
            .filterNot { it.senderAddress.lowercase() in savedAddresses }
            .filter { contact ->
                query.isBlank() ||
                    contact.senderName.contains(query, ignoreCase = true) ||
                    contact.senderAddress.contains(query, ignoreCase = true)
            }
            .sortedBy { contact ->
                contact.senderName.ifBlank { contact.senderAddress }.lowercase()
            }
            .toList()
    }


    val listState = rememberLazyListState()
    val motionEnabled = bondMotionEnabled()
    val scrollScope = rememberCoroutineScope()
    val listBottomContentPadding by animateDpAsState(
        targetValue = if (chromeVisible) 112.dp else 18.dp,
        animationSpec = tween(
            durationMillis = BondMotionDuration.ChromeReveal,
            easing = BondMotionEasing.Standard,
        ),
        label = "contact-list-bottom-content-padding",
    )
    val scrollToTopBottomPadding by animateDpAsState(
        targetValue = if (chromeVisible) 88.dp else 18.dp,
        animationSpec = tween(
            durationMillis = BondMotionDuration.ChromeReveal,
            easing = BondMotionEasing.Standard,
        ),
        label = "contact-list-scroll-top-bottom-padding",
    )
    val showScrollToTop by remember(listState) {
        derivedStateOf {
            val partiallyScrolled =
                listState.firstVisibleItemIndex > 0 &&
                    listState.firstVisibleItemScrollOffset > 420
            val scrolledAwayFromTop =
                listState.firstVisibleItemIndex >= 4 || partiallyScrolled
            listState.canScrollForward && scrolledAwayFromTop
        }
    }
    ObserveLazyListChromeVisibility(
        listState = listState,
        visible = chromeVisible,
        onVisibilityChanged = onChromeVisibilityChanged,
        enabled = chromeControllerEnabled,
    )

    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topChromeHeight = statusBarInset + 80.dp
    val topChromeOffset = animateChromeOffset(
        visible = chromeVisible,
        hiddenOffset = -topChromeHeight,
        label = "contacts-top-chrome-slide",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.bondSurfaces.page),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MailContentDefaults.HorizontalInset,
                end = MailContentDefaults.HorizontalInset,
                top = topChromeHeight + 2.dp,
                bottom = listBottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(MailContentDefaults.ItemSpacing),
        ) {
            item(key = "contacts-title") {
                Text(
                    if (frequentContacts.isEmpty()) tr("contacts") else tr("saved_contacts"),
                    modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (frequentContacts.isNotEmpty()) {
                itemsIndexed(
                    items = frequentContacts,
                    key = { _, contact -> "saved-${contact.id}" },
                    contentType = { _, _ -> "saved-contact-card" },
                ) { index, contact ->
                    ContactItem(
                        name = contact.name,
                        email = contact.email,
                        customAvatarText = contact.avatarText,
                        settings = settings,
                        shape = MailContentDefaults.itemShape(index, frequentContacts.size),
                        onCompose = onCompose,
                        onEdit = {
                            editingContactId = contact.id
                            contactName = contact.name
                            contactEmail = contact.email
                            contactAvatar = contact.avatarText.orEmpty()
                            contactError = null
                            showAddContact = true
                        },
                    )
                }
                if (filteredContacts.isNotEmpty()) {
                    item(key = "all-contacts-title") {
                        Text(
                            tr("contacts"),
                            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 2.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (filteredContacts.isEmpty() && frequentContacts.isEmpty()) {
                item(key = "contacts-empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (query.isBlank()) tr("no_contacts") else tr("no_search_results"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = filteredContacts,
                    key = { _, contact -> contact.senderAddress.lowercase() },
                    contentType = { _, _ -> "contact-card" },
                ) { index, contact ->
                    ContactItem(
                        name = contact.senderName.ifBlank { contact.senderAddress },
                        email = contact.senderAddress,
                        customAvatarText = null,
                        settings = settings,
                        shape = MailContentDefaults.itemShape(index, filteredContacts.size),
                        onCompose = onCompose,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = topChromeOffset.toPx() },
            color = MaterialTheme.bondSurfaces.chrome,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    color = MaterialTheme.bondSurfaces.input,
                    tonalElevation = 1.dp,
                    shadowElevation = 1.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (query.isBlank()) {
                                        Text(
                                            tr("search_contacts"),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                        IconButton(
                            onClick = {
                                editingContactId = null
                                contactName = ""
                                contactEmail = ""
                                contactAvatar = ""
                                contactError = null
                                showAddContact = true
                            },
                        ) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = tr("add_contact"),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showScrollToTop,
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

        if (showAddContact) {
            AlertDialog(
                onDismissRequest = { showAddContact = false },
                title = {
                    Text(
                        if (editingContactId == null) tr("add_contact") else tr("edit_contact"),
                    )
                },
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
                            ContactAvatar(
                                name = contactName,
                                email = contactEmail,
                                customText = contactAvatar,
                                size = 52.dp,
                                monet = settings.dynamicColor && settings.monetBrandIcons,
                            )
                            OutlinedTextField(
                                value = contactAvatar,
                                onValueChange = {
                                    contactAvatar = it.take(32)
                                    contactError = null
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text(tr("contact_avatar")) },
                                placeholder = { Text(tr("avatar_placeholder")) },
                                supportingText = { Text(tr("contact_avatar_hint")) },
                                singleLine = true,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    contactAvatar = ""
                                    contactError = null
                                },
                            ) {
                                Text(tr("avatar_auto"))
                            }
                            listOf("😀", "📮", "✉️", "⭐").forEach { emoji ->
                                TextButton(
                                    onClick = {
                                        contactAvatar = emoji
                                        contactError = null
                                    },
                                ) {
                                    Text(emoji)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = contactName,
                            onValueChange = {
                                contactName = it
                                contactError = null
                            },
                            label = { Text(tr("contact_name")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        OutlinedTextField(
                            value = contactEmail,
                            onValueChange = {
                                contactEmail = it
                                contactError = null
                            },
                            label = { Text(tr("email_address")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done,
                            ),
                        )
                        contactError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !contactSaving,
                        onClick = {
                            scrollScope.launch {
                                contactSaving = true
                                runCatching {
                                    container.repository.saveContact(
                                        name = contactName,
                                        email = contactEmail,
                                        avatarText = contactAvatar,
                                        contactId = editingContactId,
                                    )
                                }.onSuccess {
                                    showAddContact = false
                                }.onFailure { error ->
                                    contactError = if (
                                        error.message.orEmpty().contains("Contact avatar", ignoreCase = true)
                                    ) {
                                        invalidContactAvatarLabel
                                    } else {
                                        invalidContactLabel
                                    }
                                }
                                contactSaving = false
                            }
                        },
                    ) { Text(tr("save")) }
                },
                dismissButton = {
                    Row {
                        if (editingContactId != null) {
                            TextButton(
                                enabled = !contactSaving,
                                onClick = {
                                    deleteContactTarget = savedContacts.firstOrNull {
                                        it.id == editingContactId
                                    }
                                    deleteContactError = null
                                    showAddContact = false
                                },
                            ) {
                                Text(
                                    tr("delete_contact"),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        TextButton(
                            enabled = !contactSaving,
                            onClick = { showAddContact = false },
                        ) {
                            Text(tr("cancel"))
                        }
                    }
                },
            )
        }

        deleteContactTarget?.let { contact ->
            AlertDialog(
                onDismissRequest = {
                    if (!contactDeleting) deleteContactTarget = null
                },
                title = { Text(tr("confirm_delete_contact_title")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${contact.name}\n${tr("delete_contact_local_only")}")
                        deleteContactError?.let { error ->
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !contactDeleting,
                        onClick = {
                            scrollScope.launch {
                                contactDeleting = true
                                runCatching {
                                    container.repository.deleteSavedContact(contact.id)
                                }.onSuccess {
                                    deleteContactTarget = null
                                    editingContactId = null
                                }.onFailure {
                                    deleteContactError = deleteContactFailureLabel
                                }
                                contactDeleting = false
                            }
                        },
                    ) {
                        Text(
                            tr("delete"),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !contactDeleting,
                        onClick = { deleteContactTarget = null },
                    ) {
                        Text(tr("cancel"))
                    }
                },
            )
        }
    }
}

@Composable
private fun ContactItem(
    name: String,
    email: String,
    customAvatarText: String?,
    settings: AppSettings,
    shape: androidx.compose.ui.graphics.Shape,
    onCompose: (String) -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    GroupedListSurface(
        onClick = { onCompose(email) },
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        containerColor = MaterialTheme.bondSurfaces.content,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 78.dp)
                .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactAvatar(
                name = name,
                email = email,
                customText = customAvatarText,
                size = 46.dp,
                monet = settings.dynamicColor && settings.monetBrandIcons,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 21.sp),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    email,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onEdit ?: { onCompose(email) },
                modifier = Modifier.size(40.dp).clip(CircleShape),
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = if (onEdit == null) tr("compose_mail") else tr("edit_contact"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
