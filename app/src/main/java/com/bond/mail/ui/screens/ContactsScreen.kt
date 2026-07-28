package com.bond.mail.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bond.mail.AppContainer
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.ui.components.BrandAvatar
import com.bond.mail.ui.components.GroupedListSurface
import com.bond.mail.ui.components.FloatingCircleAction
import com.bond.mail.ui.components.MailContentDefaults
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.motion.ObserveLazyListChromeVisibility
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
    var query by rememberSaveable { mutableStateOf("") }
    val filteredContacts = remember(contacts, query) {
        contacts
            .asSequence()
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
    val showScrollToTop by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex >= 4 ||
                (listState.firstVisibleItemIndex > 0 && listState.firstVisibleItemScrollOffset > 420)
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
                bottom = 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(MailContentDefaults.ItemSpacing),
        ) {
            item(key = "contacts-title") {
                Text(
                    tr("contacts"),
                    modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (filteredContacts.isEmpty()) {
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
                    GroupedListSurface(
                        onClick = { onCompose(contact.senderAddress) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                        shape = MailContentDefaults.itemShape(index, filteredContacts.size),
                        containerColor = MaterialTheme.bondSurfaces.content,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 78.dp)
                                .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BrandAvatar(
                                contact.senderName,
                                contact.senderAddress,
                                46.dp,
                                settings.dynamicColor && settings.monetBrandIcons,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    contact.senderName.ifBlank { contact.senderAddress },
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 16.sp,
                                        lineHeight = 21.sp,
                                    ),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    contact.senderAddress,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        lineHeight = 18.sp,
                                    ),
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = { onCompose(contact.senderAddress) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = tr("compose_mail"),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
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
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showScrollToTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 88.dp),
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
    }
}
