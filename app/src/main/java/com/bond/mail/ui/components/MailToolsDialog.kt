package com.bond.mail.ui.components

import com.bond.mail.ui.theme.BondSecondaryButton

import android.content.Intent
import android.webkit.WebView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bond.mail.MainActivity
import com.bond.mail.MailApplication
import com.bond.mail.background.scheduleMailReminder
import com.bond.mail.data.mail.*
import com.bond.mail.data.model.ProviderRegistry
import com.bond.mail.data.settings.ProductivityStore
import com.bond.mail.ui.i18n.tr
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

@Composable
fun MailToolsDialog(messageId: String? = null, initialTab: String = "tools_sync", onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as MailApplication).container
    val scope = rememberCoroutineScope()
    val store = remember { ProductivityStore(context) }
    val cache = remember { TranslationCache(context) }
    val accounts by container.repository.accounts.collectAsState(emptyList())
    var account by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(initialTab) }
    var tabsOpen by remember { mutableStateOf(false) }
    var limit by remember { mutableIntStateOf(200) }
    val indexFlow = remember(account, limit, tab) { if (tab in listOf("tools_attachments", "tools_threads")) container.database.messageDao().observeIndex(account, limit) else kotlinx.coroutines.flow.flowOf(emptyList()) }
    val rows by indexFlow.collectAsState(emptyList())
    var filter by remember { mutableStateOf("") }
    var recentDays by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var bodyBytes by remember { mutableLongStateOf(0) }
    var cacheBytes by remember { mutableLongStateOf(0) }
    var webBytes by remember { mutableLongStateOf(0) }
    var downloads by remember { mutableStateOf(0 to 0L) }
    var reminders by remember { mutableStateOf(store.reminders()) }
    LaunchedEffect(tab, refresh) {
        if (tab == "tools_storage") {
            bodyBytes = container.database.messageDao().bodyBytes()
            cacheBytes = cache.size()
            downloads = AttachmentDownloadStore(context).downloadedStats()
            webBytes = withContext(Dispatchers.IO) {
                java.io.File(context.applicationInfo.dataDir, "app_webview/Default/Cache")
                    .walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
        }
        reminders = store.reminders()
    }
    fun openMessage(id: String) {
        onDismiss()
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("message_id", id)
        })
    }
    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            working = true; status = null
            try { action(); status = "tools_done"; refresh++ }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { status = "error_connection_failed" }
            finally { working = false }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(12.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr("mail_tools"), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text(tr("close")) }
                }
                Box {
                    BondSecondaryButton(onClick = { tabsOpen = true }) { Text(tr(tab) + " ▾") }
                    DropdownMenu(expanded = tabsOpen, onDismissRequest = { tabsOpen = false }) {
                        listOf("tools_sync", "tools_accounts", "tools_attachments", "tools_threads", "tools_reminders", "tools_storage").forEach { key ->
                            DropdownMenuItem(text = { Text(tr(key)) }, onClick = { tab = key; status = null; tabsOpen = false })
                        }
                    }
                }
                if (tab !in listOf("tools_storage", "tools_reminders")) Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { account = null }) { Text(tr("all_accounts")) }
                    accounts.forEach { item -> FilterChip(account == item.id, { account = item.id }, label = { Text(item.displayName.ifBlank { item.email }) }) }
                }
                if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                status?.let { Text(tr(it)) }
                when (tab) {
                    "tools_sync" -> LazyColumn {
                        item { Text(tr("sync_diagnostics_note"), style = MaterialTheme.typography.bodySmall) }
                        items(accounts.filter { account == null || it.id == account }, key = { it.id }) { item ->
                            val provider = ProviderRegistry.forAccount(item)
                            Column(Modifier.padding(vertical = 10.dp)) {
                                Text(item.email, style = MaterialTheme.typography.titleMedium)
                                Text(tr("sync_last") + ": " + (item.lastSyncAt?.let(::formatMailToolDate) ?: "—"))
                                Text("IMAP: ${provider.imapHost}:${provider.imapPort} · ${provider.imapSecurity}")
                                Text("SMTP: ${provider.smtpHost}:${provider.smtpPort} · ${provider.smtpSecurity}")
                                Text(item.authType)
                                item.lastError?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
                                TextButton(enabled = !working, onClick = { runAction { container.repository.syncFolder(item.id, "INBOX") } }) { Text(tr("retry")) }
                                HorizontalDivider()
                            }
                        }
                    }
                    "tools_accounts" -> {
                        val selected = accounts.firstOrNull { it.id == account } ?: accounts.firstOrNull()
                        if (selected == null) Text(tr("tools_account_required"))
                        else key(selected.id) { AccountProductivityEditor(selected.id, selected.email, store) }
                    }
                    "tools_storage" -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text(tr("storage_preserve_note")) }
                        item { Text(tr("storage_bodies") + ": " + MailAttachmentCodec.formatSize(bodyBytes))
                            TextButton(enabled = !working, onClick = { runAction { container.database.messageDao().clearBodyCache(); MailWebViewCache.clear(); MailWebViewPool.clearCachedPages() } }) { Text(tr("clear_cache")) } }
                        item { Text(tr("translation_cache") + ": " + MailAttachmentCodec.formatSize(cacheBytes))
                            TextButton(enabled = !working, onClick = { runAction { cache.clear() } }) { Text(tr("clear_cache")) } }
                        item { Text(tr("storage_web") + ": " + MailAttachmentCodec.formatSize(webBytes))
                            TextButton(enabled = !working, onClick = { runAction { WebView(context).let { it.clearCache(true); it.destroy() }; MailWebViewCache.clear(); MailWebViewPool.clearCachedPages() } }) { Text(tr("clear_cache")) } }
                        item { Text(tr("tools_attachments") + ": ${downloads.first} · " + MailAttachmentCodec.formatSize(downloads.second)); Text(tr("storage_attachments_note")) }
                    }
                    "tools_reminders" -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { Text(tr("reminder_note")) }
                        if (messageId != null) item {
                            Row {
                                listOf(1 to "reminder_hour", 24 to "reminder_day", 168 to "reminder_week").forEach { (hours, label) ->
                                    TextButton(onClick = { scheduleMailReminder(context, messageId, System.currentTimeMillis() + hours * 3_600_000L); refresh++ }) { Text(tr(label)) }
                                }
                            }
                        }
                        items(reminders.entries.sortedBy { it.value }, key = { it.key }) { (id, at) ->
                            var subject by remember(id) { mutableStateOf("") }
                            LaunchedEffect(id) { subject = container.repository.messageNow(id)?.subject.orEmpty() }
                            TextButton(onClick = { openMessage(id) }) { Text(subject.ifBlank { tr("no_subject") }) }
                            Text(formatMailToolDate(at))
                            TextButton(onClick = { scheduleMailReminder(context, id, 0); refresh++ }) { Text(tr("cancel")) }
                        }
                        if (reminders.isEmpty()) item { Text(tr("no_messages")) }
                    }
                    else -> {
                        if (tab == "tools_attachments") {
                            OutlinedTextField(filter, { filter = it }, modifier = Modifier.fillMaxWidth(), label = { Text(tr("attachment_filter")) }, singleLine = true)
                            Row {
                                listOf(0, 7, 30).forEach { days -> TextButton(onClick = { recentDays = days }) { Text(if (days == 0) tr("all") else "$days " + tr("days")) } }
                            }
                            Text(tr("attachment_center_note"), style = MaterialTheme.typography.bodySmall)
                        }
                        val visibleRows = rows.filter { tab != "tools_attachments" || recentDays == 0 || it.receivedAt >= System.currentTimeMillis() - recentDays * 86_400_000L }
                        val groups = remember(rows) { conversationGroups(rows) }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (tab == "tools_threads") {
                                item { Text(tr("threads_note"), style = MaterialTheme.typography.bodySmall) }
                                items(groups, key = { it.first().id }) { group ->
                                    var expanded by remember { mutableStateOf(false) }
                                    TextButton(onClick = { if (group.size == 1) openMessage(group.first().id) else expanded = !expanded }) {
                                        Text("${group.size} · ${group.last().subject}")
                                    }
                                    if (expanded) group.forEach { row -> TextButton(onClick = { openMessage(row.id) }) { Text("${formatMailToolDate(row.receivedAt)} · ${row.senderAddress}\n${row.subject}") } }
                                }
                            } else {
                                items(visibleRows, key = { it.id }) { row ->
                                    MailAttachmentCodec.decode(row.attachmentsJson).withIndex().filter { it.value.name.contains(filter, true) || it.value.contentType.contains(filter, true) }.forEach { (index, info) ->
                                        TextButton(onClick = {
                                            scope.launch {
                                                val uri = withContext(Dispatchers.IO) { AttachmentDownloadStore(context).downloadedUri(row.id, index, info) }
                                                if (uri == null) openMessage(row.id)
                                                else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, info.contentType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                                                    .onFailure { openMessage(row.id) }
                                            }
                                        }) {
                                            Text("${info.name} · ${MailAttachmentCodec.formatSize(info.sizeBytes)}\n${formatMailToolDate(row.receivedAt)} · ${row.senderAddress}")
                                        }
                                    }
                                }
                            }
                            if (rows.size >= limit) item { TextButton(onClick = { limit += 200 }) { Text(tr("load_more")) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountProductivityEditor(account: String, email: String, store: ProductivityStore) {
    var signature by remember { mutableStateOf(store.get(account, "signature")) }
    var templates by remember { mutableStateOf(store.get(account, "templates")) }
    var mode by remember { mutableStateOf(store.get(account, "notifications").ifBlank { "all" }) }
    var important by remember { mutableStateOf(store.get(account, "important")) }
    var quiet by remember { mutableStateOf(store.get(account, "quiet")) }
    var saved by remember { mutableStateOf(false) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(email) }
        item { OutlinedTextField(signature, { signature = it; saved = false }, Modifier.fillMaxWidth(), label = { Text(tr("signature")) }) }
        item { OutlinedTextField(templates, { templates = it; saved = false }, Modifier.fillMaxWidth(), label = { Text(tr("response_templates")) }); Text(tr("templates_note")) }
        item { Text(tr("notifications")); Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("all", "important", "silent", "off").forEach { key -> FilterChip(mode == key, { mode = key; saved = false }, label = { Text(tr("notify_$key")) }) }
        } }
        item { OutlinedTextField(important, { important = it; saved = false }, Modifier.fillMaxWidth(), label = { Text(tr("important_senders")) }) }
        item { OutlinedTextField(quiet, { quiet = it; saved = false }, Modifier.fillMaxWidth(), label = { Text(tr("quiet_senders")) }); Text(tr("sender_rules_note")) }
        item { Button(onClick = {
            mapOf("signature" to signature, "templates" to templates, "notifications" to mode, "important" to important, "quiet" to quiet)
                .forEach { (key, value) -> store.set(account, key, value) }; saved = true
        }) { Text(tr(if (saved) "tools_done" else "save")) } }
    }
}

private fun formatMailToolDate(value: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))
