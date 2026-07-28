package com.bond.mail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.bond.mail.BuildConfig
import com.bond.mail.data.settings.MailDensity
import com.bond.mail.data.settings.RemoteImagePolicy
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.ui.SettingsViewModel
import com.bond.mail.ui.i18n.SupportedLanguages
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.motion.ObserveLazyListChromeVisibility
import com.bond.mail.ui.theme.bondSurfaces

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    notificationPermissionGranted: Boolean,
    onOpenNotificationSettings: () -> Unit,
    chromeVisible: Boolean,
    onChromeVisibilityChanged: (Boolean) -> Unit,
    chromeControllerEnabled: Boolean = true,
) {
    val settings by viewModel.settings.collectAsState()
    val optionHeight = when (settings.density) {
        MailDensity.COMFORTABLE -> 58.dp
        MailDensity.STANDARD -> 50.dp
        MailDensity.COMPACT -> 44.dp
    }
    val listState = rememberLazyListState()
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
            .statusBarsPadding()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle(tr("language")) }
        item {
            OptionGroupCard {
                SupportedLanguages.options.forEach { language ->
                    VerticalOptionRow(
                        label = tr(language.labelKey),
                        selected = settings.languageCode == language.code,
                        rowHeight = optionHeight,
                        onClick = { viewModel.language(language.code) },
                    )
                }
            }
        }

        item { SectionTitle(tr("appearance")) }
        item {
            OptionGroupCard {
                Text(
                    tr("theme_mode"),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
                ThemeMode.entries.forEach { mode ->
                    VerticalOptionRow(
                        label = when (mode) {
                            ThemeMode.SYSTEM -> tr("follow_system")
                            ThemeMode.LIGHT -> tr("light")
                            ThemeMode.DARK -> tr("dark")
                        },
                        selected = settings.themeMode == mode,
                        rowHeight = optionHeight,
                        onClick = { viewModel.theme(mode) },
                    )
                }
                SwitchRow(tr("dynamic_color"), settings.dynamicColor, viewModel::dynamic)
            }
        }
        item {
            OptionGroupCard {
                Text(
                    tr("list_density"),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
                MailDensity.entries.forEach { density ->
                    VerticalOptionRow(
                        label = when (density) {
                            MailDensity.COMFORTABLE -> tr("comfortable")
                            MailDensity.STANDARD -> tr("standard")
                            MailDensity.COMPACT -> tr("compact")
                        },
                        selected = settings.density == density,
                        rowHeight = optionHeight,
                        onClick = { viewModel.density(density) },
                    )
                }
            }
        }

        item { SectionTitle(tr("sync")) }
        item {
            OptionGroupCard {
                Text(
                    tr("sync_interval"),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
                listOf(1, 5, 10, 15).forEach { minutes ->
                    VerticalOptionRow(
                        label = tr("minutes_short", minutes.toString()),
                        selected = settings.syncMinutes == minutes,
                        rowHeight = optionHeight,
                        onClick = { viewModel.syncMinutes(minutes) },
                    )
                }
                Text(
                    tr("sync_interval_note"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }

        item { SectionTitle(tr("permissions")) }
        item {
            OptionGroupCard {
                PermissionStatusRow(
                    label = tr("message_notifications"),
                    granted = notificationPermissionGranted,
                    onAuthorize = onOpenNotificationSettings,
                )
            }
        }

        item { SectionTitle(tr("privacy_security")) }
        item {
            OptionGroupCard {
                SwitchRow(tr("biometric_lock"), settings.biometricLock, viewModel::biometric)
                Text(
                    tr("remote_images"),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
                RemoteImagePolicy.entries.forEach { policy ->
                    VerticalOptionRow(
                        label = when (policy) {
                            RemoteImagePolicy.ALWAYS -> tr("always_load")
                            RemoteImagePolicy.WIFI_ONLY -> tr("wifi_only")
                            RemoteImagePolicy.NEVER -> tr("never_load")
                        },
                        selected = settings.remoteImagePolicy == policy,
                        rowHeight = optionHeight,
                        onClick = { viewModel.remoteImages(policy) },
                    )
                }
            }
        }
        item {
            Text(
                tr("version_label", BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.titleLarge,
    modifier = Modifier.padding(top = 14.dp),
)

@Composable
private fun OptionGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
private fun VerticalOptionRow(
    label: String,
    selected: Boolean,
    rowHeight: Dp,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(rowHeight),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(
                label,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    granted: Boolean,
    onAuthorize: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (granted) {
            Text(
                text = tr("permission_allowed"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        } else {
            TextButton(onClick = onAuthorize) {
                Text(tr("permission_go_authorize"))
            }
        }
    }
}
