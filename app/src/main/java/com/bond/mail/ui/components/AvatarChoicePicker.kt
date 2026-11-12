package com.bond.mail.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bond.mail.data.settings.AvatarChoice
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.theme.*

@Composable
internal fun AvatarChoiceField(value: String?, onChange: (String?) -> Unit, name: String, email: String, monet: Boolean) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ContactAvatar(name, email, value, size = 52.dp, monet = monet, useStoredOverride = false, onAvatarClick = { open = true })
        BondSecondaryButton(onClick = { open = true }, modifier = Modifier.weight(1f)) {
            Text(tr("choose_avatar"), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (open) AvatarChoiceDialog(value, onChange = { onChange(it); open = false }, onDismiss = { open = false })
}

@Composable
internal fun AvatarChoiceDialog(initial: String?, onChange: (String?) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    BondAlertDialog(onDismissRequest = onDismiss, title = { Text(tr("choose_avatar")) }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            AvatarChoiceOptions(selected) { selected = it }
        }
    }, confirmButton = { BondTextAction(tr("save"), enabled = AvatarChoice.isValid(selected), onClick = { onChange(selected?.trim()?.ifBlank { null }) }) },
        dismissButton = { BondTextAction(tr("cancel"), onClick = onDismiss) })
}

@Composable
internal fun AvatarChoiceOptions(value: String?, onChange: (String?) -> Unit) {
    val category = AvatarChoice.category(value)
    val custom = value != null && category == null
    Row(Modifier.fillMaxWidth().clickable { onChange(null) }, verticalAlignment = Alignment.CenterVertically) {
        RadioButton(value == null, onClick = { onChange(null) })
        Text(tr("sender_icon_auto"))
    }
    Row(Modifier.fillMaxWidth().clickable { onChange("") }, verticalAlignment = Alignment.CenterVertically) {
        RadioButton(custom, onClick = { onChange("") })
        Text(tr("avatar_custom"))
    }
    if (custom) {
        BondTextField(value = value.orEmpty(), onValueChange = { onChange(it.take(32)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), label = tr("contact_avatar"),
            supportingText = if (AvatarChoice.isValid(value)) tr("contact_avatar_hint") else tr("error_contact_avatar_single_glyph"), singleLine = true)
    }
    Text(tr("sender_icon_title"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 12.dp))
    AvatarChoice.categories.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { key ->
                Surface(onClick = { onChange("icon:$key") }, modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    color = if (category == key) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.bondSurfaces.content) {
                    Column(Modifier.padding(10.dp).heightIn(min = 76.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ContactAvatar("", "", "icon:$key", size = 34.dp, useStoredOverride = false)
                        Text(tr("sender_icon_$key"), style = MaterialTheme.typography.labelMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
