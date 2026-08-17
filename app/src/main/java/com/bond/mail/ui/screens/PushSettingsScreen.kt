package com.bond.mail.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bond.mail.background.PushAccessConfigStore
import com.bond.mail.data.settings.PushAccessState
import com.bond.mail.ui.SettingsViewModel
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.theme.bondSurfaces
import com.bond.mail.ui.theme.BondPrimaryButton

@Composable
fun PushSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    var serviceOrigin by remember(viewModel) {
        mutableStateOf(
            viewModel.currentPushServiceOrigin()
                .removePrefix("https://"),
        )
    }
    var accessKey by remember { mutableStateOf("") }
    var hasSavedAccessKey by remember(viewModel) {
        mutableStateOf(viewModel.hasSavedPushAccessKey())
    }
    var domainInvalid by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.bondSurfaces.page)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = tr("back"),
                )
            }
            Text(
                text = tr("push_settings_title"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.bondSurfaces.content,
                    ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = tr("push_settings_intro"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = serviceOrigin,
                            onValueChange = { value ->
                                serviceOrigin = value.take(512)
                                domainInvalid = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(tr("push_service_domain")) },
                            placeholder = { Text("push.example.com") },
                            supportingText = if (domainInvalid) {
                                { Text(tr("push_domain_invalid")) }
                            } else {
                                null
                            },
                            isError = domainInvalid,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        OutlinedTextField(
                            value = accessKey,
                            onValueChange = { value -> accessKey = value.take(256) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(tr("push_access_key")) },
                            placeholder = {
                                Text(
                                    if (hasSavedAccessKey) {
                                        tr("push_access_key_saved_hint")
                                    } else {
                                        "pwd"
                                    },
                                )
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                        )

                        val (statusText, statusColor) = when (settings.pushAccessState) {
                            PushAccessState.MISSING ->
                                tr("push_access_missing_short") to
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            PushAccessState.VERIFYING ->
                                tr("push_access_verifying") to MaterialTheme.colorScheme.primary
                            PushAccessState.VERIFIED ->
                                tr("push_access_verified_short") to MaterialTheme.colorScheme.primary
                            PushAccessState.REJECTED ->
                                tr("push_access_rejected_short") to MaterialTheme.colorScheme.error
                            PushAccessState.FAILED ->
                                tr("push_access_failed_short") to MaterialTheme.colorScheme.error
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = statusColor,
                        )
                        BondPrimaryButton(
                            onClick = {
                                val normalizedOrigin =
                                    PushAccessConfigStore.normalizeServiceOrigin(serviceOrigin)
                                if (normalizedOrigin == null) {
                                    domainInvalid = true
                                } else {
                                    serviceOrigin = normalizedOrigin.removePrefix("https://")
                                    viewModel.pushAccessConfig(normalizedOrigin, accessKey)
                                    hasSavedAccessKey = true
                                    accessKey = ""
                                }
                            },
                            enabled = serviceOrigin.isNotBlank() &&
                                (accessKey.isNotBlank() || hasSavedAccessKey) &&
                                settings.pushAccessState != PushAccessState.VERIFYING,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(tr("push_access_verify"))
                        }
                        Text(
                            text = tr("push_access_note_short"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
