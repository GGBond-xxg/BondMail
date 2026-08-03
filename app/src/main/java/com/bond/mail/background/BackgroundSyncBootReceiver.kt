package com.bond.mail.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bond.mail.MailApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restores mail polling after a reboot or app update, including migrations from older schedules. */
class BackgroundSyncBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val application = context.applicationContext as MailApplication
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = application.container.settings.settings.first()
                application.container.scheduler.scheduleBackgroundSync(
                    enabled = true,
                    intervalMinutes = settings.syncMinutes,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
