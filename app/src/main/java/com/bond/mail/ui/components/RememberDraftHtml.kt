package com.bond.mail.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.UUID

/** Keep large quoted HTML out of the Activity saved-state Binder payload. App-private storage only. */
@Composable
internal fun rememberDraftHtml(): MutableState<String?> {
    val context = LocalContext.current.applicationContext
    val saver = remember(context) {
        val directory = File(context.cacheDir, "compose-html-state").apply { mkdirs() }
        val file = File(directory, UUID.randomUUID().toString())
        Saver<MutableState<String?>, String>(
            save = { state ->
                state.value?.let { html -> file.writeText(html); file.name } ?: ""
            },
            restore = { name ->
                mutableStateOf(if (name.isBlank()) null else runCatching {
                    require(name.matches(Regex("[a-f0-9-]+")))
                    File(directory, name).readText()
                }.getOrNull())
            },
        )
    }
    return rememberSaveable(saver = saver) { mutableStateOf(null) }
}
