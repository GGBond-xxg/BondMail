package com.bond.mail.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bond.mail.MailApplication
import com.bond.mail.NewMailNotificationMode
import com.bond.mail.data.mail.MailLog
import com.bond.mail.data.performance.UiPerformanceGate

class MailSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MailApplication).container
        val mode = inputData.getString(KEY_MODE) ?: MODE_PERIODIC
        val shortToken = inputData.getString(KEY_SHORT_TOKEN).orEmpty()
        val requestedShortInterval = inputData.getInt(KEY_SHORT_INTERVAL, 1).coerceAtLeast(1)

        return runCatching {
            UiPerformanceGate.awaitBackgroundWindow(
                settleDelayMs = 1_500L,
                maximumWaitMs = 20_000L,
            )
            MailLog.d(MailLog.APP, "worker sync start mode=$mode attempt=$runAttemptCount")
            val notificationMode = if (mode == MODE_MANUAL) {
                NewMailNotificationMode.CONSUME_SILENTLY
            } else {
                container.backgroundNotificationMode()
            }
            container.syncAllAndNotify(notificationMode)

            if (mode == MODE_SHORT_POLL && shortToken.isNotBlank()) {
                container.scheduler.scheduleNextShortSync(
                    intervalMinutes = requestedShortInterval,
                    token = shortToken,
                )
            }
            MailLog.d(MailLog.APP, "worker sync success mode=$mode")
            Result.success()
        }.getOrElse { error ->
            MailLog.e(MailLog.APP, "worker sync failed mode=$mode cause=${MailLog.causeSummary(error)}", error)
            Result.retry()
        }
    }

    companion object {
        const val KEY_MODE = "sync_mode"
        const val KEY_SHORT_TOKEN = "short_sync_token"
        const val KEY_SHORT_INTERVAL = "short_sync_interval"

        const val MODE_PERIODIC = "periodic"
        const val MODE_SHORT_POLL = "short_poll"
        const val MODE_MANUAL = "manual"
    }
}
