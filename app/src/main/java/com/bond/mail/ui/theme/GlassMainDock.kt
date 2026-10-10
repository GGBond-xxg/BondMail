package com.bond.mail.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bond.mail.ui.glass.components.LiquidBottomTab
import com.bond.mail.ui.glass.components.LiquidBottomTabs
import com.bond.mail.ui.glass.utils.glassDockGestureBoundary
import com.bond.mail.ui.i18n.tr

@Composable
internal fun GlassMainDock(selectedTab: Int, onSelectTab: (Int) -> Unit, onCompose: () -> Unit, modifier: Modifier) {
    val selection by rememberUpdatedState(selectedTab)
    val onSelection by rememberUpdatedState(onSelectTab)
    val labels = listOf(tr("mail"), tr("contacts"), tr("settings"))
    val icons = listOf(GlassIcons.Mail, GlassIcons.People, GlassIcons.Settings)
    Row(
        modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth()
            .glassDockGestureBoundary(),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (LocalGlassEffects.current) {
            LiquidBottomTabs({ selection }, { onSelection(it) }, glassBackdrop(), 3, Modifier.weight(1f)) {
                repeat(3) { index ->
                    LiquidBottomTab({ onSelectTab(index) }, Modifier.semantics { selected = selectedTab == index }) {
                        Icon(icons[index], labels[index], Modifier.size(24.dp))
                        Text(labels[index], fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
                    }
                }
            }
        } else {
            Row(Modifier.weight(1f).glassSurface().height(64.dp)) {
                repeat(3) { index ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight().selectable(selectedTab == index, role = Role.Tab, onClick = { onSelectTab(index) }),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(icons[index], labels[index], tint = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(labels[index], fontSize = 10.sp)
                    }
                }
            }
        }
        GlassButton(onCompose, Modifier.size(60.dp), primary = true, iconOnly = true) {
            Icon(GlassIcons.Compose, tr("compose_mail"), Modifier.size(26.dp))
        }
    }
}
