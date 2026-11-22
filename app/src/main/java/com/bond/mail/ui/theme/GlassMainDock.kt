package com.bond.mail.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Compatibility entry point; the shared dock owns all geometry and rendering. */
@Composable
internal fun GlassMainDock(selectedTab: Int, onSelectTab: (Int) -> Unit, onCompose: () -> Unit, modifier: Modifier) {
    AppBottomDock(selectedTab, onSelectTab, onCompose, modifier = modifier)
}
