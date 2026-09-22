package com.bond.mail.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bond.mail.MailApplication
import com.bond.mail.ui.i18n.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Visible above every page, including reply-from-detail and a scrolled inbox. */
@Composable
fun SendUndoNotice() {
    val container = (LocalContext.current.applicationContext as MailApplication).container
    val flow = remember { container.database.outboxDao().observeSending() }
    val tasks by flow.collectAsState(emptyList())
    val task = tasks.firstOrNull { it.state == "QUEUED" }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var undone by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(task?.id) {
        now = System.currentTimeMillis()
        while (task != null && now < task.sendAfter) { delay(100); now = System.currentTimeMillis() }
    }
    LaunchedEffect(undone) { if (undone) { delay(3_000); undone = false } }
    if (undone || (task != null && task.sendAfter > now)) {
        androidx.compose.ui.window.Popup(alignment = androidx.compose.ui.Alignment.BottomCenter) {
            Surface(Modifier.padding(horizontal = 16.dp, vertical = 100.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp, shadowElevation = 4.dp) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(if (undone) tr("send_undone") else task!!.subject.ifBlank { tr("no_subject") }, Modifier.weight(1f), maxLines = 2)
                    if (!undone && task != null) TextButton(onClick = { scope.launch { undone = container.repository.undoSend(task.id) } }) {
                        Text(tr("send_undo") + " (${((task.sendAfter - now + 999) / 1000).coerceAtLeast(0)})")
                    }
                    if (undone) TextButton(onClick = { undone = false }) { Text(tr("close")) }
                }
            }
        }
    }
}

@Composable
fun SendQueueBanner() {
    val container = (LocalContext.current.applicationContext as MailApplication).container
    val flow = remember { container.database.outboxDao().observeSending() }
    val tasks by flow.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var notice by remember { mutableStateOf(false) }
    LaunchedEffect(tasks) { while (tasks.any { it.state == "QUEUED" && it.sendAfter > now }) { now = System.currentTimeMillis(); delay(200) } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        tasks.take(3).forEach { task ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.subject.ifBlank { tr("no_subject") }, Modifier.weight(1f), maxLines = 1)
                if (task.state == "QUEUED" && task.sendAfter > now) {
                    TextButton(onClick = { scope.launch { notice = container.repository.undoSend(task.id) } }) {
                        Text(tr("send_undo") + " (${((task.sendAfter - now + 999) / 1000).coerceAtLeast(0)})")
                    }
                } else Text(tr(when (task.state) { "FAILED" -> "send_failed"; "UNKNOWN" -> "send_unknown"; else -> "send_pending" }))
            }
        }
        if (notice) TextButton(onClick = { notice = false }) { Text(tr("send_undone")) }
    }
}
