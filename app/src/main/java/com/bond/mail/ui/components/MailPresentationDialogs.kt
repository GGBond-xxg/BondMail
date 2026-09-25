package com.bond.mail.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bond.mail.data.settings.*
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.theme.BondAlertDialog
import com.bond.mail.ui.theme.BondTextAction

internal val manualIconCategories = listOf("aidef", "bank", "simcard", "exchange", "sports", "clothes", "shopping", "hotel", "expressdelivery", "airplane")

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SenderIconDialog(sender: String, onDismiss: () -> Unit) {
    val store = MailPresentationStore.get(LocalContext.current)
    val values by store.values.collectAsState()
    var domainScope by remember(sender) { mutableStateOf(false) }
    val target = if (domainScope) SenderPresentationRules.domain(sender) else SenderPresentationRules.address(sender)
    val key = "icon:${if (domainScope) "domain" else "email"}:$target"
    var selected by remember(key) { mutableStateOf(values[key]) }
    BondAlertDialog(onDismissRequest = onDismiss,
        title = { Text(tr("sender_icon_title")) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text(tr("sender_icon_scope"), style = MaterialTheme.typography.labelLarge)
                Choice(tr("sender_scope_email"), !domainScope) { domainScope = false }
                Choice(tr("sender_scope_domain"), domainScope) { domainScope = true }
                Text(target, style = MaterialTheme.typography.bodySmall)
                Text(tr("sender_icon_priority"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                HorizontalDivider()
                Choice(tr("sender_icon_auto"), selected == null) { selected = null }
                manualIconCategories.forEach { category ->
                    Choice(tr("sender_icon_$category"), selected == category) { selected = category }
                }
            }
        },
        confirmButton = { BondTextAction(text = tr("save"), onClick = { store.set("icon", sender, domainScope, selected); onDismiss() }) },
        dismissButton = { BondTextAction(text = tr("cancel"), onClick = onDismiss) })
}

@Composable
fun MailDisplayModeDialog(sender: String, mode: MailDisplayMode, onSelect: (MailDisplayMode) -> Unit, onDismiss: () -> Unit) {
    val store = MailPresentationStore.get(LocalContext.current)
    var selected by remember { mutableStateOf(mode) }
    var rememberSender by remember { mutableStateOf(false) }
    BondAlertDialog(onDismissRequest = onDismiss,
        title = { Text(tr("mail_display_title")) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                MailDisplayMode.entries.forEach { value ->
                    Choice(tr("mail_display_${value.name.lowercase()}"), selected == value) { selected = value }
                }
                Row(Modifier.fillMaxWidth().clickable { rememberSender = !rememberSender }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rememberSender, onCheckedChange = { rememberSender = it })
                    Text(tr("mail_display_remember"), Modifier.weight(1f))
                }
                Text(sender, style = MaterialTheme.typography.bodySmall)
                BondTextAction(text = tr("mail_display_reset"), onClick = {
                    store.set("display", sender, false, null)
                    onSelect(MailDisplayMode.AUTO)
                    onDismiss()
                })
            }
        },
        confirmButton = { BondTextAction(text = tr("save"), onClick = {
            if (rememberSender) store.set("display", sender, false, selected.name.takeUnless { selected == MailDisplayMode.AUTO })
            onSelect(selected)
            onDismiss()
        }) },
        dismissButton = { BondTextAction(text = tr("cancel"), onClick = onDismiss) })
}
