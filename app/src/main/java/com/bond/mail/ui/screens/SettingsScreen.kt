package com.bond.mail.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.bond.mail.BuildConfig
import com.bond.mail.data.settings.MailDensity
import com.bond.mail.data.settings.PushAccessState
import com.bond.mail.data.settings.RemoteImagePolicy
import com.bond.mail.data.settings.ThemeColor
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.SettingsViewModel
import com.bond.mail.ui.i18n.SupportedLanguages
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.motion.BondMotionDuration
import com.bond.mail.ui.motion.BondMotionEasing
import com.bond.mail.ui.motion.BondStaggeredEntranceState
import com.bond.mail.ui.motion.LocalThemeRevealController
import com.bond.mail.ui.motion.ObserveLazyListChromeVisibility
import com.bond.mail.ui.motion.bondMotionEnabled
import com.bond.mail.ui.motion.bondStaggeredEntrance
import com.bond.mail.ui.motion.rememberBondPressInteraction
import com.bond.mail.ui.motion.rememberBondPressResetter
import com.bond.mail.ui.motion.rememberBondStaggeredEntranceState
import com.bond.mail.ui.theme.bondSurfaces
import com.bond.mail.ui.theme.BondSwitch
import com.bond.mail.ui.theme.LocalUiStyle
import com.bond.mail.ui.theme.MiuixActionSetting
import com.bond.mail.ui.theme.MiuixDropdownSetting
import com.bond.mail.ui.theme.MiuixPermissionSetting
import com.bond.mail.ui.theme.MiuixSectionTitle
import com.bond.mail.ui.theme.MiuixSettingsCard
import com.bond.mail.ui.theme.MiuixSettingsDivider
import com.bond.mail.ui.theme.MiuixSwitchSetting

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    notificationPermissionGranted: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onOpenBackgroundSettings: () -> Unit,
    onOpenPushSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    chromeVisible: Boolean,
    onChromeVisibilityChanged: (Boolean) -> Unit,
    chromeControllerEnabled: Boolean = true,
    staggeredEntranceEnabled: Boolean = false,
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val downloadFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModel.attachmentDownloadTreeUri(uri.toString())
    }
    val themeRevealController = LocalThemeRevealController.current
    val motionEnabled = bondMotionEnabled()
    val entranceState = rememberBondStaggeredEntranceState(
        enabled = motionEnabled && staggeredEntranceEnabled,
    )
    val listState = rememberLazyListState()
    val selectedDownloadFolderName by produceState<String?>(
        initialValue = null,
        key1 = settings.attachmentDownloadTreeUri,
        key2 = context,
    ) {
        val treeUri = settings.attachmentDownloadTreeUri.takeIf(String::isNotBlank)
        value = treeUri?.let { value ->
            // DocumentFile resolves the provider-backed display name through a ContentResolver
            // query. On Xiaomi devices the provider can be cold after memory cleanup, so doing
            // this while the lazy item first enters the viewport blocks the UI thread and causes
            // the first downward scroll to visibly hitch.
            withContext(Dispatchers.IO) {
                runCatching {
                    DocumentFile.fromTreeUri(context, Uri.parse(value))?.name
                }.getOrNull()
            }
        }
    }
    val listBottomContentPadding by animateDpAsState(
        targetValue = if (chromeVisible) 108.dp else 18.dp,
        animationSpec = tween(
            durationMillis = BondMotionDuration.ChromeReveal,
            easing = BondMotionEasing.Standard,
        ),
        label = "settings-list-bottom-content-padding",
    )
    ObserveLazyListChromeVisibility(
        listState = listState,
        visible = chromeVisible,
        onVisibilityChanged = onChromeVisibilityChanged,
        enabled = chromeControllerEnabled,
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.bondSurfaces.page)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 18.dp,
            bottom = listBottomContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .bondStaggeredEntrance(entranceState, index = 0),
            ) {
                Text(
                    text = tr("settings"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = tr("settings_subtitle"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        item {
            StaggeredSettingsItem(entranceState, index = 1) {
                SectionTitle(tr("language"))
            }
        }
        item {
            StaggeredSettingsItem(entranceState, index = 2) {
                SettingsCard {
                    DropdownSettingRow(
                        title = tr("language"),
                        options = SupportedLanguages.options.map { language ->
                            language.code to tr(language.labelKey)
                        },
                        selected = settings.languageCode,
                        onSelect = viewModel::language,
                    )
                }
            }
        }

        item {
            StaggeredSettingsItem(entranceState, index = 3) {
                SectionTitle(tr("appearance"))
            }
        }
        item {
            StaggeredSettingsItem(entranceState, index = 4) {
                SettingsCard {
                DropdownSettingRow(
                    title = tr("ui_style"),
                    options = listOf(UiStyle.MIUIX, UiStyle.MATERIAL3).map { style ->
                        style to when (style) {
                            UiStyle.MATERIAL3 -> tr("ui_style_material3")
                            UiStyle.MIUIX -> tr("ui_style_miuix")
                        }
                    },
                    selected = settings.uiStyle,
                    onSelect = viewModel::uiStyle,
                )
                SettingsDivider()
                DropdownSettingRow(
                    title = tr("list_density"),
                    options = MailDensity.entries.map { density ->
                        density to when (density) {
                            MailDensity.COMFORTABLE -> tr("comfortable")
                            MailDensity.STANDARD -> tr("standard")
                            MailDensity.COMPACT -> tr("compact")
                        }
                    },
                    selected = settings.density,
                    onSelect = viewModel::density,
                )
                SettingsDivider()
                DropdownSettingRow(
                    title = tr("theme_mode"),
                    options = ThemeMode.entries.map { mode ->
                        mode to when (mode) {
                            ThemeMode.SYSTEM -> tr("follow_system")
                            ThemeMode.LIGHT -> tr("light")
                            ThemeMode.DARK -> tr("dark")
                        }
                    },
                    selected = settings.themeMode,
                    onSelect = viewModel::theme,
                    onSelectAt = { mode, origin ->
                        if (mode != settings.themeMode) {
                            themeRevealController?.switchTo(mode, origin, motionEnabled)
                                ?: viewModel.theme(mode)
                        }
                    },
                )
                if (settings.uiStyle == UiStyle.MATERIAL3) {
                    SettingsDivider()
                    SwitchSettingRow(
                        title = tr("dynamic_color"),
                        subtitle = tr("dynamic_color_desc"),
                        checked = settings.dynamicColor,
                        onChecked = viewModel::dynamic,
                    )
                    if (!settings.dynamicColor) {
                        SettingsDivider()
                        DropdownSettingRow(
                            title = tr("theme_color"),
                            options = ThemeColor.entries.map { color ->
                                color to tr("theme_color_${color.name.lowercase()}")
                            },
                            selected = settings.themeColor,
                            onSelect = viewModel::themeColor,
                        )
                    }
                }
                }
            }
        }

        item {
            StaggeredSettingsItem(entranceState, index = 5) {
                SectionTitle(tr("sync"))
            }
        }
        item {
            StaggeredSettingsItem(entranceState, index = 6) {
                SettingsCard {
                DropdownSettingRow(
                    title = tr("sync_interval"),
                    options = listOf(1, 5, 10, 15).map { minutes ->
                        minutes to tr("minutes_short", minutes.toString())
                    },
                    selected = settings.syncMinutes,
                    onSelect = viewModel::syncMinutes,
                )
                SettingsDivider()
                SettingsActionRow(
                    icon = Icons.Default.Cloud,
                    title = tr("push_settings_title"),
                    subtitle = when (settings.pushAccessState) {
                        PushAccessState.MISSING -> tr("push_access_missing_short")
                        PushAccessState.VERIFYING -> tr("push_access_verifying")
                        PushAccessState.VERIFIED -> tr("push_access_verified_short")
                        PushAccessState.REJECTED -> tr("push_access_rejected_short")
                        PushAccessState.FAILED -> tr("push_access_failed_short")
                    },
                    onClick = onOpenPushSettings,
                )
                }
            }
        }

        item {
            StaggeredSettingsItem(entranceState, index = 7) {
                SectionTitle(tr("permissions"))
            }
        }
        item {
            StaggeredSettingsItem(entranceState, index = 8) {
                SettingsCard {
                PermissionStatusRow(
                    icon = Icons.Default.Notifications,
                    title = tr("message_notifications"),
                    subtitle = tr("message_notifications_desc"),
                    granted = notificationPermissionGranted,
                    onAuthorize = onOpenNotificationSettings,
                )
                SettingsDivider()
                SettingsActionRow(
                    icon = Icons.Default.BatterySaver,
                    title = tr("background_run_settings"),
                    subtitle = tr("background_run_settings_note_short"),
                    onClick = onOpenBackgroundSettings,
                )
                }
            }
        }

        item {
            StaggeredSettingsItem(entranceState, index = 9) {
                SectionTitle(tr("privacy_security"))
            }
        }
        item {
            StaggeredSettingsItem(entranceState, index = 10) {
                SettingsCard {
                DropdownSettingRow(
                    title = tr("remote_images"),
                    options = RemoteImagePolicy.entries.map { policy ->
                        policy to when (policy) {
                            RemoteImagePolicy.ALWAYS -> tr("always_load")
                            RemoteImagePolicy.WIFI_ONLY -> tr("wifi_only")
                            RemoteImagePolicy.NEVER -> tr("never_load")
                        }
                    },
                    selected = settings.remoteImagePolicy,
                    onSelect = viewModel::remoteImages,
                )
                SettingsDivider()
                SettingsActionRow(
                    icon = Icons.Default.Folder,
                    title = tr("attachment_download_folder"),
                    subtitle = selectedDownloadFolderName
                        ?: tr("attachment_download_folder_not_selected"),
                    onClick = { downloadFolderPicker.launch(null) },
                )
                }
            }
        }

        item {
            StaggeredSettingsItem(entranceState, index = 11) {
                SectionTitle(tr("about"))
            }
        }
        item {
            StaggeredSettingsItem(entranceState, index = 12) {
                SettingsCard {
                    SettingsActionRow(
                        icon = Icons.Default.Info,
                        title = tr("about_bondmail"),
                        subtitle = tr("version_label", BuildConfig.VERSION_NAME),
                        onClick = onOpenAbout,
                    )
                }
            }
        }
    }
}

@Composable
private fun StaggeredSettingsItem(
    state: BondStaggeredEntranceState,
    index: Int,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bondStaggeredEntrance(state, index = index),
    ) {
        content()
    }
}

@Composable
private fun SectionTitle(text: String) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixSectionTitle(text)
        return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixSettingsCard(content)
        return
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.bondSurfaces.content),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

@Composable
private fun <T> DropdownSettingRow(
    title: String,
    subtitle: String? = null,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onSelectAt: ((T, Offset) -> Unit)? = null,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
        MiuixDropdownSetting(
            title = title,
            summary = subtitle,
            labels = options.map { it.second },
            selectedIndex = selectedIndex,
            onSelectedIndexChange = { index, centerInWindow ->
                val value = options[index].first
                if (value != selected) {
                    onSelectAt?.invoke(value, centerInWindow) ?: onSelect(value)
                }
            },
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }
    var popupMounted by remember { mutableStateOf(false) }
    var selectorCenterInWindow by remember { mutableStateOf(Offset.Unspecified) }
    var selectorSize by remember { mutableStateOf(IntSize.Zero) }
    var pendingSelection by remember { mutableStateOf<Pair<T, Offset>?>(null) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()
    val density = LocalDensity.current

    LaunchedEffect(pendingSelection) {
        val (value, origin) = pendingSelection ?: return@LaunchedEffect
        // Draw the closed menu before a theme/style change repaints the root UI.
        withFrameNanos { }
        pendingSelection = null
        onSelectAt?.invoke(value, origin) ?: onSelect(value)
    }
    LaunchedEffect(expanded) {
        if (expanded) {
            popupMounted = true
        } else if (popupMounted) {
            delay(130L)
            popupMounted = false
        }
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = BondMotionDuration.EffectShort),
        label = "settings-dropdown-arrow",
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .widthIn(min = 154.dp, max = 210.dp)
                     .onGloballyPositioned { coordinates ->
                         selectorCenterInWindow = coordinates.boundsInWindow().center
                         selectorSize = coordinates.size
                     },
        ) {
            Surface(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border =
                    BorderStroke(
                        1.dp,
                        if (expanded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                color =
                    if (expanded) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
                    },
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
                    )
                }
            }
            if (popupMounted) {
                val popupWidth = with(density) {
                    selectorSize.width.toDp().coerceIn(190.dp, 230.dp)
                }
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(
                        x = 0,
                        y = selectorSize.height + with(density) { 4.dp.roundToPx() },
                    ),
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    val popupProgress by animateFloatAsState(
                        targetValue = if (expanded) 1f else 0f,
                        animationSpec = tween(durationMillis = if (expanded) 190 else 130),
                        label = "settings-dropdown-popup-progress",
                    )
                        Surface(
                            modifier = Modifier
                                .width(popupWidth)
                                .graphicsLayer {
                                    alpha = popupProgress
                                    scaleX = 0.92f + 0.08f * popupProgress
                                    scaleY = 0.92f + 0.08f * popupProgress
                                    transformOrigin = TransformOrigin(0.86f, 0f)
                                },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.bondSurfaces.popup,
                            tonalElevation = 6.dp,
                            shadowElevation = 10.dp,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                        // Material DropdownMenu always inserts an 8dp vertical margin. This custom
                        // anchored surface keeps Material ripple but removes those empty bands.
                            Column {
                            options.forEach { (value, label) ->
                                val isSelected = value == selected
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 52.dp),
                                    text = {
                                        Text(
                                            text = label,
                                            fontWeight = if (isSelected) {
                                                FontWeight.SemiBold
                                            } else {
                                                FontWeight.Normal
                                            },
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                    },
                                    trailingIcon = {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    },
                                    onClick = {
                                        expanded = false
                                        if (value != selected) {
                                            pendingSelection = value to selectorCenterInWindow
                                        }
                                    },
                                )
                            }
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixSwitchSetting(
            title = title,
            summary = subtitle,
            checked = checked,
            onCheckedChange = onChecked,
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, end = 12.dp),
            )
        }
        BondSwitch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun PermissionStatusRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    onAuthorize: () -> Unit,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixPermissionSetting(
            icon = icon,
            title = title,
            summary = subtitle,
            status = if (granted) tr("permission_allowed") else tr("permission_go_authorize"),
            actionRequired = !granted,
            onClick = onAuthorize,
        )
        return
    }
    SettingsRowShell(icon = icon, title = title, subtitle = subtitle) {
        if (granted) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = tr("permission_allowed"),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        } else {
            TextButton(onClick = onAuthorize) {
                Text(tr("permission_go_authorize"))
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixActionSetting(
            icon = icon,
            title = title,
            summary = subtitle,
            onClick = onClick,
        )
        return
    }
    val pressResetter = rememberBondPressResetter()
    key(pressResetter.epoch) {
        val interactionSource = rememberBondPressInteraction()
        Surface(
            onClick = { pressResetter.resetThen(onClick) },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
            interactionSource = interactionSource,
        ) {
            SettingsRowShell(icon = icon, title = title, subtitle = subtitle) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsRowShell(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(10.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        trailing()
    }
}

@Composable
private fun SettingsDivider() {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixSettingsDivider()
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(56.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
    }
}
