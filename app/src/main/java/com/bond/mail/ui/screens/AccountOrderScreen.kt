package com.bond.mail.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bond.mail.data.db.AccountEntity
import com.bond.mail.data.model.visibleEmail
import com.bond.mail.ui.SettingsViewModel
import com.bond.mail.ui.components.AccountAvatar
import com.bond.mail.ui.i18n.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountOrderScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val accounts by viewModel.accounts.collectAsState()
    val ordered = remember { mutableStateListOf<AccountEntity>() }

    LaunchedEffect(accounts) {
        ordered.clear()
        ordered.addAll(accounts)
    }

    fun persist() {
        viewModel.reorderAccounts(ordered.map { it.id })
    }

    fun move(from: Int, to: Int) {
        if (from !in ordered.indices || to !in ordered.indices) return
        val item = ordered.removeAt(from)
        ordered.add(to, item)
        persist()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("mailbox_order_title")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("back"))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    tr("mailbox_order_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            itemsIndexed(ordered, key = { _, account -> account.id }) { index, account ->
                Card(
                    modifier = Modifier.fillMaxWidth().animateItem(),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.DragHandle, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        AccountAvatar(account, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(account.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                account.visibleEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            enabled = index > 0,
                            onClick = { move(index, index - 1) },
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = tr("move_up"))
                        }
                        IconButton(
                            enabled = index < ordered.lastIndex,
                            onClick = { move(index, index + 1) },
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = tr("move_down"))
                        }
                    }
                }
            }
        }
    }
}
