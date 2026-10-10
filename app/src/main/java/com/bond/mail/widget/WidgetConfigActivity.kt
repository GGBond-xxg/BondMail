package com.bond.mail.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.bond.mail.MailApplication
import com.bond.mail.MainActivity
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.i18n.loadJsonStrings
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID && !WidgetUpdates.owns(this, id)) { finish(); return }
        lifecycleScope.launch {
            val container = (application as MailApplication).container
            val settings = container.settings.settings.first()
            setContent {
                BondMailTheme(settings) {
                    JsonStringsProvider(settings.languageCode) { Configure(id, settings) }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Configure(id: Int, settings: AppSettings) {
        val container = (application as MailApplication).container
        val accounts by container.repository.accounts.collectAsState(initial = emptyList())
        val store = remember { WidgetStore(this) }
        var encoded by rememberSaveable { mutableStateOf(store.get(id)?.encode()) }
        var selectedSize by rememberSaveable { mutableStateOf(intent.getStringExtra("widget_size")) }
        val isManager = id == AppWidgetManager.INVALID_APPWIDGET_ID && selectedSize == null
        LaunchedEffect(accounts, isManager) {
            if (encoded == null && !isManager && accounts.isNotEmpty()) {
                val initial = accounts.firstOrNull { it.enabled && it.id == store.lastAccount() } ?: accounts.firstOrNull { it.enabled }
                initial?.let { encoded = WidgetConfig(it.id, settings.uiStyle, settings.themeMode).encode() }
            }
        }
        val config = encoded?.let(WidgetConfig::decode)
        var saving by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf(false) }
        var loadedSnapshot by remember { mutableStateOf<Pair<String?, WidgetSnapshot>>(null to WidgetSnapshot()) }
        val snapshot = loadedSnapshot.takeIf { it.first == encoded }?.second ?: WidgetSnapshot()
        LaunchedEffect(encoded, accounts) {
            val result = try { config?.let { withContext(Dispatchers.IO) { widgetSnapshot(container.database, it) } } ?: WidgetSnapshot() }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { WidgetSnapshot(readError = true) }
            loadedSnapshot = encoded to result
        }
        fun change(value: WidgetConfig) { encoded = value.encode(); error = false }
        Scaffold(topBar = { BondTopAppBar(title = tr("widget_settings"), navigationIcon = {
            BondIconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("back")) }
        }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (isManager) {
                    Text(tr("widget_intro"), style = MaterialTheme.typography.bodyMedium)
                    listOf("small" to "2 × 2", "medium" to "4 × 2", "large" to "4 × 4").forEach { (size, label) ->
                        BondSecondaryButton(onClick = { selectedSize = size }, modifier = Modifier.fillMaxWidth()) { Text(tr("widget_add_size", label)) }
                    }
                    WidgetUpdates.ids(this@WidgetConfigActivity).forEach { existing ->
                        val c = store.get(existing)
                        val name = accounts.firstOrNull { it.id == c?.accountId }?.displayName ?: tr("widget_account_missing")
                        BondSecondaryButton(onClick = {
                            startActivity(Intent(this@WidgetConfigActivity, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, existing))
                        }, modifier = Modifier.fillMaxWidth()) { Text("$name · #$existing") }
                    }
                } else if (accounts.none { it.enabled }) {
                    Text(tr("widget_no_accounts"))
                    BondPrimaryButton(onClick = { startActivity(Intent(this@WidgetConfigActivity, MainActivity::class.java)); finish() }) { Text(tr("widget_open_app")) }
                } else if (config != null) {
                    Text(tr("widget_preview_title"), style = MaterialTheme.typography.titleMedium)
                    val size = when (selectedSize ?: AppWidgetManager.getInstance(this@WidgetConfigActivity).getAppWidgetInfo(id)?.provider?.className?.substringAfterLast('.')) {
                        "small", "SmallMailWidget" -> SizeF(160f, 170f)
                        "large", "LargeMailWidget" -> SizeF(320f, 380f)
                        else -> SizeF(320f, 190f)
                    }
                    WidgetPreview(config, snapshot, size, settings.languageCode)
                    Text(tr("widget_independent"), style = MaterialTheme.typography.bodySmall)
                    Choice(tr("widget_account"), config.accountId, accounts.filter { it.enabled }.map { it.id to "${it.displayName} · ${it.email}" }) { change(config.copy(accountId = it)) }
                    Choice(tr("ui_style"), config.style, listOf(UiStyle.MIUIX to "MIUI / HyperOS", UiStyle.MATERIAL3 to "Material 3", UiStyle.LIQUID_GLASS to "Liquid Glass")) { change(config.copy(style = it)) }
                    Choice(tr("theme_mode"), config.mode, listOf(ThemeMode.SYSTEM to tr("follow_system"), ThemeMode.LIGHT to tr("light"), ThemeMode.DARK to tr("dark"))) { change(config.copy(mode = it)) }
                    Choice(tr("widget_privacy"), config.privacy, listOf(WidgetPrivacy.NORMAL to tr("widget_privacy_normal"), WidgetPrivacy.HIDE_PREVIEW to tr("widget_privacy_preview"), WidgetPrivacy.HIDE_CONTENT to tr("widget_privacy_content"))) { change(config.copy(privacy = it)) }
                    Text(tr("widget_display"), style = MaterialTheme.typography.titleMedium)
                    Toggle("widget_show_unread", config.unread) { change(config.copy(unread = it)) }
                    Toggle("widget_show_sender", config.sender) { change(config.copy(sender = it)) }
                    Toggle("widget_show_subject", config.subject, config.privacy != WidgetPrivacy.HIDE_CONTENT) { change(config.copy(subject = it)) }
                    Toggle("widget_show_preview", config.preview, config.privacy == WidgetPrivacy.NORMAL) { change(config.copy(preview = it)) }
                    Toggle("widget_show_time", config.time) { change(config.copy(time = it)) }
                    Toggle("widget_show_compose", config.compose) { change(config.copy(compose = it)) }
                    Toggle("widget_show_refresh", config.refresh) { change(config.copy(refresh = it)) }
                    Text(tr("widget_limits"), style = MaterialTheme.typography.bodySmall)
                    if (config.style == UiStyle.LIQUID_GLASS) Text(tr("widget_glass_note"), style = MaterialTheme.typography.bodySmall)
                    if (error) Text(tr("widget_save_error"), color = MaterialTheme.colorScheme.error)
                    BondPrimaryButton(enabled = !saving && snapshot.valid, modifier = Modifier.fillMaxWidth(), onClick = {
                        saving = true
                        lifecycleScope.launch {
                            try {
                                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                    withContext(Dispatchers.IO) { WidgetUpdates.save(this@WidgetConfigActivity, id, config) }
                                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)); finish()
                                } else {
                                    val manager = AppWidgetManager.getInstance(this@WidgetConfigActivity)
                                    val provider = when (selectedSize) { "small" -> SmallMailWidget::class.java; "large" -> LargeMailWidget::class.java; else -> MediumMailWidget::class.java }
                                    val callback = Intent(this@WidgetConfigActivity, WidgetPinReceiver::class.java).setAction("widget.pin")
                                        .setData(android.net.Uri.parse("bondmail-widget://pin/${java.util.UUID.randomUUID()}"))
                                        .putExtra("config", config.encode())
                                    // The launcher fills in the newly allocated appWidgetId. Explicit receiver limits mutability.
                                    val pending = PendingIntent.getBroadcast(this@WidgetConfigActivity, 0, callback,
                                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
                                    val accepted = manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(ComponentName(this@WidgetConfigActivity, provider), null, pending)
                                    if (accepted) finish() else error = true
                                }
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                              catch (_: Exception) { error = true }
                            saving = false
                        }
                    }) { Text(tr(if (id == AppWidgetManager.INVALID_APPWIDGET_ID) "widget_add" else "save")) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun <T> Choice(label: String, selected: T, choices: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        BondPopupMenu(expanded, { expanded = false }, entries = choices.map { (value, title) ->
            BondMenuEntry(title, { expanded = false; onSelect(value) }, selected = value == selected)
        }, anchor = {
            BondSecondaryButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(choices.firstOrNull { it.first == selected }?.second.orEmpty(), maxLines = 2)
            }
        })
    }
}

@Composable
private fun Toggle(key: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(tr(key), Modifier.weight(1f))
        BondSwitch(checked, onChange, enabled = enabled)
    }
}

@Composable
private fun WidgetPreview(config: WidgetConfig, snapshot: WidgetSnapshot, size: SizeF, language: String) {
    val context = LocalContext.current
    val views = remember(config, snapshot, size, language) { WidgetRenderer(context, loadJsonStrings(context, language)).render(-1, config, snapshot, size, false) }
    AndroidView(factory = { FrameLayout(it) }, modifier = Modifier.width(size.width.dp).height(size.height.dp), update = { frame ->
        frame.removeAllViews()
        frame.addView(views.apply(context, frame), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    })
}

class WidgetPinReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val config = intent.getStringExtra("config")?.let(WidgetConfig::decode) ?: return
        if (!WidgetUpdates.owns(context, id)) return
        WidgetStore(context).save(id, config)
        WidgetUpdates.enqueue(context)
    }
}
