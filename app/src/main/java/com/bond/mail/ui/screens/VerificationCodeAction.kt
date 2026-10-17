package com.bond.mail.ui.screens

import android.os.Build
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
internal fun VerificationCodeAction(message: MessageEntity, container: AppContainer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copying by remember(message.id) { mutableStateOf(false) }
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
                    if (copied && Build.VERSION.SDK_INT < 33) {
                        Toast.makeText(context, R.string.verification_code_copied, Toast.LENGTH_SHORT).show()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.verification_code_copy_failed, Toast.LENGTH_SHORT).show()
                } finally {
                    copying = false
                }
            }
        },
    )
}
