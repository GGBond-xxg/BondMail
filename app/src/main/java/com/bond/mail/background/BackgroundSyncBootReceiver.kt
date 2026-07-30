package com.bond.mail.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restores explicitly enabled continuous mail polling after a reboot or app update. */
class BackgroundSyncBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val scheduler = WorkScheduler(context.applicationContext)
        val intervalMinutes = scheduler.continuousIntervalMinutes() ?: return
        if (!MailNotificationManager(context.applicationContext).canPostNotifications()) return
        ContinuousMailSyncService.start(context.applicationContext, intervalMinutes)
    }
}
