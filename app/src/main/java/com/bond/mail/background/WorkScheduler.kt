package com.bond.mail.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules mail synchronization without pretending WorkManager can provide an exact alarm.
 *
 * Android only supports periodic WorkManager jobs at 15 minutes or longer. The user-facing
 * Cloudflare sends a data-only FCM tick at the selected interval. WorkManager remains registered
 * as a legal 15-minute-or-longer fallback for devices where Google Play services are unavailable
 * or an OEM temporarily delays FCM delivery.
 */
class WorkScheduler(private val context: Context) {
    private val manager: WorkManager
        get() = WorkManager.getInstance(context)

    private val preferences by lazy {
        context.getSharedPreferences(BACKGROUND_PREFS, Context.MODE_PRIVATE)
    }

    fun scheduleBackgroundSync(enabled: Boolean, intervalMinutes: Int) {
        val normalizedMinutes = intervalMinutes.coerceAtLeast(1)
        FcmRegistrationStore.updateSyncPreference(
            context = context,
            enabled = enabled,
            intervalMinutes = normalizedMinutes,
        )

        if (!enabled) {
            manager.cancelUniqueWork(PERIODIC_SYNC_WORK)
            preferences.edit().clear().apply()
            return
        }

        preferences.edit()
            .putInt(KEY_SCHEDULE_INTERVAL, normalizedMinutes)
            .apply()

        // Always keep a legal WorkManager fallback. UPDATE preserves the original enqueue time, so
        // checking this again after process recreation repairs a missing/cancelled registration
        // without moving the next run every time the user opens BondMail.
        val fallbackMinutes = normalizedMinutes.coerceAtLeast(MIN_PERIODIC_MINUTES)
        val request = PeriodicWorkRequestBuilder<MailSyncWorker>(
            fallbackMinutes.toLong(),
            TimeUnit.MINUTES,
        )
            .setInputData(
                Data.Builder()
                    .putString(MailSyncWorker.KEY_MODE, MailSyncWorker.MODE_PERIODIC)
                    .build(),
            )
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        manager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<MailSyncWorker>()
            .setInputData(
                Data.Builder()
                    .putString(MailSyncWorker.KEY_MODE, MailSyncWorker.MODE_MANUAL)
                    .build(),
            )
            .setConstraints(networkConstraints())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        manager.enqueueUniqueWork(MANUAL_SYNC_WORK, ExistingWorkPolicy.KEEP, request)
    }

    fun syncFromPush() {
        val request = OneTimeWorkRequestBuilder<MailSyncWorker>()
            .setInputData(
                Data.Builder()
                    .putString(MailSyncWorker.KEY_MODE, MailSyncWorker.MODE_PUSH)
                    .build(),
            )
            .setConstraints(networkConstraints())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        manager.enqueueUniqueWork(PUSH_SYNC_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun send(taskId: String) {
        manager.cancelUniqueWork("draft_$taskId")
        val request = OneTimeWorkRequestBuilder<MailSendWorker>()
            .setInputData(Data.Builder().putString("task_id", taskId).build())
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        manager.enqueueUniqueWork("send_$taskId", ExistingWorkPolicy.KEEP, request)
    }

    fun saveDraft(taskId: String) {
        val request = OneTimeWorkRequestBuilder<MailDraftWorker>()
            .setInputData(Data.Builder().putString(MailDraftWorker.KEY_TASK_ID, taskId).build())
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // REPLACE is important when the same draft is edited repeatedly. Only the latest Room
        // snapshot should be uploaded; an older queued worker must not overwrite it afterwards.
        manager.enqueueUniqueWork("draft_$taskId", ExistingWorkPolicy.REPLACE, request)
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    companion object {
        private const val MIN_PERIODIC_MINUTES = 15
        private const val PERIODIC_SYNC_WORK = "mail_periodic_sync"
        private const val MANUAL_SYNC_WORK = "mail_manual_sync"
        private const val PUSH_SYNC_WORK = "mail_push_sync"
        private const val BACKGROUND_PREFS = "bond_mail_background"
        private const val KEY_SCHEDULE_INTERVAL = "schedule_interval"
    }
}
