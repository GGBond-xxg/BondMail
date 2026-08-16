package com.bond.mail.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bond.mail.data.settings.UiStyle
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch

/** A stable switch facade; feature screens do not import either renderer directly. */
@Composable
fun BondSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    when (LocalUiStyle.current) {
        UiStyle.MATERIAL3 -> androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
        )
        UiStyle.MIUIX -> MiuixSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
        )
    }
}
