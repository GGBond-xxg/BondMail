package com.bond.mail.ui.screens

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bond.mail.AppContainer
import com.bond.mail.R
import com.bond.mail.data.db.MessageEntity
import com.bond.mail.ui.i18n.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun rememberVerificationCode(message: MessageEntity?): String? {
    if (message == null) return null
    val context = LocalContext.current
    val store = remember(context) { com.bond.mail.data.mail.VerificationRuleStore(context) }
    val rules by store.changes.collectAsState(initial = store.rules())
    val detected by produceState<String?>(null, message.subject, message.bodyText, message.bodyHtml, rules) {
        value = withContext(Dispatchers.Default) {
            com.bond.mail.data.mail.VerificationCodeDetector.detect(message.subject, message.bodyText, message.bodyHtml, rules)
        }
    }
    return detected
}

@Composable
internal fun VerificationCodeAction(message: MessageEntity, container: AppContainer, modifier: Modifier = Modifier,
    onResult: ((Boolean) -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copying by remember(message.id) { mutableStateOf(false) }
    val reportResult: (Boolean) -> Unit = { copied ->
        if (onResult != null) onResult(copied)
        else Toast.makeText(context, if (copied) R.string.verification_code_copied
            else R.string.verification_code_copy_failed, Toast.LENGTH_SHORT).show()
    }
    MessageFloatingIconAction(
        icon = Icons.Outlined.ContentCopy,
        label = tr("copy_verification_code"),
        modifier = modifier,
        enabled = !copying,
        onClick = {
            copying = true
            scope.launch {
                try {
                    val copied = container.copyVerificationCode(message, fromNotification = false)
                    reportResult(copied)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    reportResult(false)
                } finally {
                    copying = false
                }
            }
        },
    )
}
