package com.bond.mail.data.mail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import com.bond.mail.data.db.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VerificationCodeClipboard {
    /** Called only following a tap in a focused activity; the code is never separately persisted. */
    suspend fun copy(context: Context, message: MessageEntity): Boolean {
        val code = withContext(Dispatchers.Default) {
            VerificationCodeDetector.detect(message.subject, message.bodyText, message.bodyHtml, VerificationRuleStore(context).rules())
        } ?: return false
        withContext(Dispatchers.Main.immediate) {
            val clip = ClipData.newPlainText("Verification code", code)
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        }
        return true
    }
}
