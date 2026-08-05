package com.bond.mail.ui.screens

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.bond.mail.BuildConfig
import com.bond.mail.data.settings.MailDensity
import com.bond.mail.data.settings.PushAccessState
import com.bond.mail.data.settings.RemoteImagePolicy
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.ui.SettingsViewModel
import com.bond.mail.ui.i18n.SupportedLanguages
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.motion.BondMotionDuration
import com.bond.mail.ui.motion.BondMotionEasing
import com.bond.mail.ui.motion.LocalThemeRevealController
import com.bond.mail.ui.motion.ObserveLazyListChromeVisibility
import com.bond.mail.ui.motion.bondMotionEnabled
import com.bond.mail.ui.motion.rememberBondPressInteraction
import com.bond.mail.ui.motion.rememberBondPressResetter
import com.bond.mail.ui.theme.bondSurfaces

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
) {
    val settings by viewModel.settings.collectAsState()
    val themeRevealController = LocalThemeRevealController.current
    val motionEnabled = bondMotionEnabled()
    val listState = rememberLazyListState()
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
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
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

        item { SectionTitle(tr("language")) }
        item {
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

        item { SectionTitle(tr("appearance")) }
        item {
            SettingsCard {
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
                SettingsDivider()
                SwitchSettingRow(
                    title = tr("dynamic_color"),
                    subtitle = tr("dynamic_color_desc"),
                    checked = settings.dynamicColor,
                    onChecked = viewModel::dynamic,
                )
            }
        }

        item { SectionTitle(tr("sync")) }
        item {
            SettingsCard {
                DropdownSettingRow(
                    title = tr("sync_interval"),
                    options = listOf(1, 5, 10, 15).map { minutes ->
                        minutes to tr("minutes_short", minutes.toString())
                    },
                    selected = settings.syncMinutes,
                    onSelect = viewModel::syncMinutes,
                )
                Text(
                    text = tr("sync_interval_note_short"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
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
                    },
                    onClick = onOpenPushSettings,
                )
            }
        }

        item { SectionTitle(tr("permissions")) }
        item {
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

        item { SectionTitle(tr("privacy_security")) }
        item {
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
                Text(
                    text = tr("remote_images_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = tr("biometric_lock"),
                    subtitle = tr("biometric_lock_desc"),
                    checked = settings.biometricLock,
                    onChecked = viewModel::biometric,
                )
            }
        }

        item { SectionTitle(tr("about")) }
        item {
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

@Composable
private fun SectionTitle(text: String) {
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
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onSelectAt: ((T, Offset) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var selectorCenterInWindow by remember { mutableStateOf(Offset.Unspecified) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()
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
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier =
                Modifier
                    .widthIn(min = 154.dp, max = 210.dp)
                    .onGloballyPositioned { coordinates ->
                        selectorCenterInWindow = coordinates.boundsInWindow().center
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
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 190.dp, max = 230.dp),
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.bondSurfaces.popup,
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                border =
                    BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
            ) {
                options.forEach { (value, label) ->
                    val isSelected = value == selected
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                fontWeight =
                                    if (isSelected) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    },
                                color =
                                    if (isSelected) {
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
                                onSelectAt?.invoke(value, selectorCenterInWindow)
                                    ?: onSelect(value)
                            }
                        },
                    )
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
        Switch(checked = checked, onCheckedChange = onChecked)
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
