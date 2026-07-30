package com.bond.mail.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bond.mail.data.model.AuthType
import com.bond.mail.data.model.MailAuthMechanism
import com.bond.mail.data.model.MailSecurity
import com.bond.mail.data.db.ACCOUNT_DISPLAY_NAME_MAX_LENGTH
import com.bond.mail.data.model.ProviderRegistry
import com.bond.mail.ui.AddAccountViewModel
import com.bond.mail.ui.components.GroupedListSurface
import com.bond.mail.ui.components.ProviderAvatar
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.theme.bondSurfaces

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderPickerScreen(
    onBack: () -> Unit,
    onProviderSelected: (String) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.bondSurfaces.page,
        topBar = {
            TopAppBar(
                title = { Text(tr("select_provider_title")) },
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
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(ProviderRegistry.providers.filter { it.visibleInPicker }, key = { it.id }) { provider ->
                val providerLabel = if (provider.id == "custom") tr("provider_other") else provider.label
                val itemShape = RoundedCornerShape(22.dp)
                GroupedListSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = itemShape,
                    containerColor = MaterialTheme.bondSurfaces.content,
                    onClick = { onProviderSelected(provider.id) },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProviderAvatar(provider, 46.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(providerLabel, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (provider.authType == AuthType.OAUTH2) {
                                    tr("provider_oauth")
                                } else {
                                    tr("provider_imap_smtp")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountCredentialsScreen(
    viewModel: AddAccountViewModel,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val provider by viewModel.selectedProvider.collectAsState()
    val username by viewModel.username.collectAsState()
    val suffix by viewModel.suffix.collectAsState()
    val displayName by viewModel.displayName.collectAsState()
    val secret by viewModel.secret.collectAsState()
    val customLoginName by viewModel.customLoginName.collectAsState()
    val customImapHost by viewModel.customImapHost.collectAsState()
    val customImapPort by viewModel.customImapPort.collectAsState()
    val customImapSecurity by viewModel.customImapSecurity.collectAsState()
    val customSmtpHost by viewModel.customSmtpHost.collectAsState()
    val customSmtpPort by viewModel.customSmtpPort.collectAsState()
    val customSmtpSecurity by viewModel.customSmtpSecurity.collectAsState()
    val customAuthMechanism by viewModel.customAuthMechanism.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val savedAccountId by viewModel.savedAccountId.collectAsState()
    val oauthConfiguration by viewModel.oauthConfiguration.collectAsState()
    val oauthConfigurationError by viewModel.oauthConfigurationError.collectAsState()
    var suffixMenu by remember { mutableStateOf(false) }
    var apiConfigurationExpanded by rememberSaveable(provider.id) {
        mutableStateOf(!oauthConfiguration.configured)
    }
    var apiJson by rememberSaveable(provider.id) { mutableStateOf("") }
    val googleAuthorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val host = activity
        if (result.resultCode == Activity.RESULT_OK && host != null) {
            viewModel.finishGoogleOAuth(host, result.data)
        } else {
            viewModel.cancelOAuth()
        }
    }
    val oauthJsonPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
            }.onSuccess { imported ->
                apiJson = imported
                viewModel.saveOAuthConfiguration(imported)
            }.onFailure {
                viewModel.reportOAuthConfigurationReadFailure()
            }
        }
    }

    LaunchedEffect(savedAccountId) {
        savedAccountId?.let(onSaved)
    }

    BackHandler(enabled = suffixMenu) {
        suffixMenu = false
    }

    val providerLabel = if (provider.id == "custom") tr("provider_other") else provider.label

    Scaffold(
        containerColor = MaterialTheme.bondSurfaces.page,
        topBar = {
            TopAppBar(
                title = { Text(providerLabel) },
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
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProviderAvatar(provider, 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(providerLabel, style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (provider.authType == AuthType.OAUTH2) {
                                tr("provider_oauth")
                            } else {
                                tr("provider_imap_smtp")
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (provider.authType == AuthType.OAUTH2) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        Text(
                            tr("oauth_required"),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                error?.let { failure ->
                    item {
                        Text(
                            tr(failure.key, *failure.args.toTypedArray()),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                item {
                    Button(
                        onClick = {
                            val host = activity
                            if (host == null) {
                                viewModel.reportOAuthHostUnavailable()
                            } else {
                                viewModel.startOAuth(host) { pendingIntent ->
                                    googleAuthorizationLauncher.launch(
                                        IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                                    )
                                }
                            }
                        },
                        enabled = !busy && activity != null && oauthConfiguration.configured,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(27.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(tr("oauth_connecting"))
                        } else {
                            Text(
                                if (provider.id == "gmail") {
                                    tr("continue_with_google")
                                } else {
                                    tr("continue_with_microsoft")
                                },
                            )
                        }
                    }
                }

                item {
                    Text(
                        tr("oauth_privacy_note"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item {
                    HorizontalDivider()
                }

                item {
                    TextButton(
                        onClick = { apiConfigurationExpanded = !apiConfigurationExpanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (apiConfigurationExpanded) {
                                tr("oauth_api_hide")
                            } else {
                                tr("oauth_api_configure")
                            },
                        )
                    }
                }

                if (apiConfigurationExpanded) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                if (provider.id == "gmail") {
                                    tr("oauth_google_json_hint")
                                } else {
                                    tr("oauth_microsoft_json_hint")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            oauthConfigurationError?.let { failure ->
                                Text(
                                    tr(failure.key, *failure.args.toTypedArray()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (oauthConfiguration.configured) {
                                Text(
                                    tr(
                                        "oauth_api_configured",
                                        oauthConfiguration.clientIdHint,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            OutlinedTextField(
                                value = apiJson,
                                onValueChange = { apiJson = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(tr("oauth_api_json")) },
                                placeholder = { Text("{ … }") },
                                minLines = 4,
                                maxLines = 9,
                                enabled = !busy,
                                shape = RoundedCornerShape(18.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.bondSurfaces.input,
                                    unfocusedContainerColor = MaterialTheme.bondSurfaces.input,
                                    disabledContainerColor = MaterialTheme.bondSurfaces.input,
                                ),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        oauthJsonPicker.launch(arrayOf("application/json", "text/json", "text/plain"))
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(tr("oauth_api_select_file"))
                                }
                                Button(
                                    onClick = { viewModel.saveOAuthConfiguration(apiJson) },
                                    enabled = !busy && apiJson.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(tr("oauth_api_save"))
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    MailboxAddressField(
                        value = username,
                        onValueChange = viewModel::pasteOrSetUsername,
                        placeholder = if (provider.suffixes.isEmpty()) {
                            tr("email_address")
                        } else {
                            tr("username")
                        },
                        suffix = suffix,
                        suffixes = provider.suffixes,
                        expanded = suffixMenu,
                        onExpandedChange = { suffixMenu = it },
                        onSuffixSelected = { value ->
                            viewModel.suffix.value = value
                            suffixMenu = false
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (provider.id == "custom") {
                    item {
                        OutlinedTextField(
                            value = customLoginName,
                            onValueChange = { viewModel.customLoginName.value = it },
                            label = { Text(tr("login_name")) },
                            placeholder = { Text(tr("login_name_email_default")) },
                            supportingText = { Text(tr("login_name_hint")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !busy,
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.bondSurfaces.input,
                                unfocusedContainerColor = MaterialTheme.bondSurfaces.input,
                                disabledContainerColor = MaterialTheme.bondSurfaces.input,
                            ),
                        )
                    }

                    item {
                        Text(
                            tr("incoming_server"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    item {
                        MailServerFields(
                            host = customImapHost,
                            onHostChange = { viewModel.customImapHost.value = it.trim() },
                            port = customImapPort,
                            onPortChange = {
                                viewModel.customImapPort.value = it.filter(Char::isDigit).take(5)
                            },
                            hostLabel = tr("imap_server"),
                            enabled = !busy,
                        )
                    }
                    item {
                        MailOptionSelector(
                            label = tr("connection_security"),
                            options = MailSecurity.entries,
                            selected = customImapSecurity,
                            optionLabel = { security ->
                                tr(
                                    when (security) {
                                        MailSecurity.SSL_TLS -> "security_ssl_tls"
                                        MailSecurity.STARTTLS -> "security_starttls"
                                        MailSecurity.NONE -> "security_none"
                                    },
                                )
                            },
                            onSelected = { viewModel.customImapSecurity.value = it },
                            enabled = !busy,
                        )
                    }

                    item {
                        Text(
                            tr("outgoing_server"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    item {
                        MailServerFields(
                            host = customSmtpHost,
                            onHostChange = { viewModel.customSmtpHost.value = it.trim() },
                            port = customSmtpPort,
                            onPortChange = {
                                viewModel.customSmtpPort.value = it.filter(Char::isDigit).take(5)
                            },
                            hostLabel = tr("smtp_server"),
                            enabled = !busy,
                        )
                    }
                    item {
                        MailOptionSelector(
                            label = tr("connection_security"),
                            options = MailSecurity.entries,
                            selected = customSmtpSecurity,
                            optionLabel = { security ->
                                tr(
                                    when (security) {
                                        MailSecurity.SSL_TLS -> "security_ssl_tls"
                                        MailSecurity.STARTTLS -> "security_starttls"
                                        MailSecurity.NONE -> "security_none"
                                    },
                                )
                            },
                            onSelected = { viewModel.customSmtpSecurity.value = it },
                            enabled = !busy,
                        )
                    }
                    item {
                        MailOptionSelector(
                            label = tr("authentication_method"),
                            options = MailAuthMechanism.entries,
                            selected = customAuthMechanism,
                            optionLabel = { mechanism ->
                                tr(
                                    when (mechanism) {
                                        MailAuthMechanism.AUTO -> "auth_auto"
                                        MailAuthMechanism.LOGIN -> "auth_login"
                                        MailAuthMechanism.PLAIN -> "auth_plain"
                                    },
                                )
                            },
                            onSelected = { viewModel.customAuthMechanism.value = it },
                            enabled = !busy,
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { viewModel.displayName.value = it.take(ACCOUNT_DISPLAY_NAME_MAX_LENGTH) },
                        label = { Text(tr("display_name")) },
                        supportingText = { Text("${displayName.length}/$ACCOUNT_DISPLAY_NAME_MAX_LENGTH") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !busy,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.bondSurfaces.input,
                            unfocusedContainerColor = MaterialTheme.bondSurfaces.input,
                            disabledContainerColor = MaterialTheme.bondSurfaces.input,
                        ),
                    )
                }

                item {
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { viewModel.secret.value = it },
                        label = {
                            Text(
                                if (provider.id == "custom") tr("password") else tr("authorization_code"),
                            )
                        },
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
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !busy,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.bondSurfaces.input,
                            unfocusedContainerColor = MaterialTheme.bondSurfaces.input,
                            disabledContainerColor = MaterialTheme.bondSurfaces.input,
                        ),
                    )
                }

                item {
                    Text(
                        when {
                            provider.id == "custom" -> tr("custom_mail_tip")
                            provider.netEaseClientId -> tr("netease_tip")
                            else -> tr("imap_smtp_tip")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                error?.let { failure ->
                    item {
                        Text(
                            tr(failure.key, *failure.args.toTypedArray()),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                item {
                    Button(
                        onClick = viewModel::save,
                        enabled = !busy &&
                            username.isNotBlank() &&
                            secret.isNotBlank() &&
                            (
                                provider.id != "custom" ||
                                    (
                                        customImapHost.isNotBlank() &&
                                            customSmtpHost.isNotBlank() &&
                                            (customImapPort.toIntOrNull() ?: 0) in 1..65535 &&
                                            (customSmtpPort.toIntOrNull() ?: 0) in 1..65535
                                        )
                                ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(27.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(tr("connecting"))
                        } else {
                            Text(tr("connect_and_save"))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun MailServerFields(
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    hostLabel: String,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        OutlinedTextField(
            value = host,
            onValueChange = onHostChange,
            label = { Text(hostLabel) },
            placeholder = { Text("mail.example.com") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = enabled,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.bondSurfaces.input,
                unfocusedContainerColor = MaterialTheme.bondSurfaces.input,
                disabledContainerColor = MaterialTheme.bondSurfaces.input,
            ),
        )
        OutlinedTextField(
            value = port,
            onValueChange = onPortChange,
            label = { Text(tr("port")) },
            modifier = Modifier.width(104.dp),
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.bondSurfaces.input,
                unfocusedContainerColor = MaterialTheme.bondSurfaces.input,
                disabledContainerColor = MaterialTheme.bondSurfaces.input,
            ),
        )
    }
}

@Composable
private fun <T> MailOptionSelector(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            options.forEach { option ->
                if (option == selected) {
                    Button(
                        onClick = { onSelected(option) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 9.dp),
                    ) {
                        Text(optionLabel(option), maxLines = 1, fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelected(option) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 9.dp),
                    ) {
                        Text(optionLabel(option), maxLines = 1, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MailboxAddressField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    suffix: String,
    suffixes: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSuffixSelected: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val active = focused || expanded
    val borderColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(140),
        label = "mailbox-address-border",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (active) 2.dp else 1.dp,
        animationSpec = tween(140),
        label = "mailbox-address-border-width",
    )

    Surface(
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.bondSurfaces.input,
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 18.dp, end = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.merge(
                        TextStyle(color = MaterialTheme.colorScheme.onSurface),
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isBlank()) {
                                Text(
                                    placeholder,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                    },
                )
            }

            if (suffixes.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 88.dp, max = 210.dp)
                        .fillMaxHeight(),
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .clickable(
                                enabled = enabled && suffixes.size > 1,
                                onClick = { onExpandedChange(!expanded) },
                            )
                            .padding(start = 6.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(30.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "@$suffix",
                            modifier = Modifier.widthIn(max = 158.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (suffixes.size > 1) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = tr("select_domain"),
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = expanded && suffixes.size > 1,
                        onDismissRequest = { onExpandedChange(false) },
                        modifier = Modifier.widthIn(min = 150.dp, max = 240.dp),
                        shape = RoundedCornerShape(18.dp),
                        containerColor = MaterialTheme.bondSurfaces.popup,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        suffixes.forEachIndexed { index, candidate ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "@$candidate",
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip,
                                    )
                                },
                                trailingIcon = {
                                    if (candidate == suffix) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                },
                                onClick = { onSuffixSelected(candidate) },
                            )
                            if (index != suffixes.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
