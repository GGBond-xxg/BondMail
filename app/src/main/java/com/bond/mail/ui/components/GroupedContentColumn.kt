package com.bond.mail.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bond.mail.ui.theme.bondSurfaces

/** Logical rows, rather than text nodes: a title, description and controls stay in one row. */
internal class GroupedContentScope {
    val rows = mutableListOf<@Composable ColumnScope.() -> Unit>()

    fun item(content: @Composable ColumnScope.() -> Unit) {
        rows += content
    }
}

/** M3 geometry shared with the mail/contact lists, for short settings and information groups. */
@Composable
internal fun GroupedContentColumn(
    rows: List<@Composable ColumnScope.() -> Unit>,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MailContentDefaults.ItemSpacing)) {
        rows.forEachIndexed { index, row ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MailContentDefaults.animatedItemShape(index, rows.size, selected = false),
                color = MaterialTheme.bondSurfaces.content,
            ) {
                Column(Modifier.fillMaxWidth().padding(contentPadding), content = row)
            }
        }
    }
}
