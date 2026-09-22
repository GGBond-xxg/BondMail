package com.bond.mail.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.bond.mail.data.settings.ProductivityStore
import com.bond.mail.ui.i18n.tr

@Composable
internal fun ComposeExtras(accountId: String, onInsert: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { ProductivityStore(context) }
    var expanded by remember { mutableStateOf(false) }
    var configure by remember { mutableStateOf(false) }
    if (configure) MailToolsDialog(initialTab = "tools_accounts") { configure = false }
    Row {
        TextButton(onClick = { val signature = store.get(accountId, "signature"); if (signature.isBlank()) configure = true else onInsert(signature) }) {
            Text(tr("insert_signature"))
        }
        Box {
            TextButton(onClick = { expanded = true }) { Text(tr("response_templates")) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                store.get(accountId, "templates").split("\n---\n").filter { it.isNotBlank() }.forEach { text ->
                    DropdownMenuItem(text = { Text(text.take(60)) }, onClick = { onInsert(text); expanded = false })
                }
                DropdownMenuItem(text = { Text(tr("manage")) }, onClick = { configure = true; expanded = false })
            }
        }
    }
}
