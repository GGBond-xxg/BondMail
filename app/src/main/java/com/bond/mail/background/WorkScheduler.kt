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
 * 1/5/10-minute choices are handled by [ContinuousMailSyncService], because a self-rescheduling
 * one-time chain is still freely delayed or cancelled by Android/OEM background limits.
 */
class WorkScheduler(private val context: Context) {
    private val manager: WorkManager
        get() = WorkManager.getInstance(context)

    private val preferences by lazy {
        context.getSharedPreferences(BACKGROUND_PREFS, Context.MODE_PRIVATE)
    }

    fun scheduleBackgroundSync(enabled: Boolean, intervalMinutes: Int) {
        val normalizedMinutes = intervalMinutes.coerceAtLeast(1)
        val desiredMode =
            if (usesContinuousService(normalizedMinutes)) MODE_CONTINUOUS else MODE_PERIODIC
        val currentMode = preferences.getString(KEY_SCHEDULE_MODE, null)
        val currentInterval = preferences.getInt(KEY_SCHEDULE_INTERVAL, -1)

        // MailApplication calls this on every process start. Do not reset an already-persisted
        // WorkManager schedule, otherwise repeatedly opening the app keeps moving the next run.
        if (enabled && currentMode == desiredMode && currentInterval == normalizedMinutes) return

        val previousToken = preferences.getString(KEY_SHORT_TOKEN, null)
        previousToken?.let { manager.cancelUniqueWork(shortWorkName(it)) }

        if (!enabled) {
            manager.cancelUniqueWork(PERIODIC_SYNC_WORK)
            preferences.edit().clear().apply()
            return
        }

        if (desiredMode == MODE_PERIODIC) {
            preferences.edit()
                .putString(KEY_SCHEDULE_MODE, MODE_PERIODIC)
                .putInt(KEY_SCHEDULE_INTERVAL, normalizedMinutes)
                .remove(KEY_SHORT_TOKEN)
                .apply()
            val request = PeriodicWorkRequestBuilder<MailSyncWorker>(
                normalizedMinutes.toLong(),
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
            return
        }

        // A visible foreground-service notification is the Android-supported contract for a local
        // mail client that the user explicitly asks to poll more often than WorkManager's minimum.
        // Cancel the legacy short-work chain to avoid duplicate network traffic after an upgrade.
        manager.cancelUniqueWork(PERIODIC_SYNC_WORK)
        preferences.edit()
            .putString(KEY_SCHEDULE_MODE, MODE_CONTINUOUS)
            .putInt(KEY_SCHEDULE_INTERVAL, normalizedMinutes)
            .remove(KEY_SHORT_TOKEN)
            .apply()
    }

    fun continuousIntervalMinutes(): Int? {
        if (preferences.getString(KEY_SCHEDULE_MODE, null) != MODE_CONTINUOUS) return null
        return preferences.getInt(KEY_SCHEDULE_INTERVAL, -1)
            .takeIf(::usesContinuousService)
    }

    /** Appends the next short-poll request after a successful short-poll worker run. */
    fun scheduleNextShortSync(intervalMinutes: Int, token: String) {
        val activeToken = preferences.getString(KEY_SHORT_TOKEN, null)
        val activeMode = preferences.getString(KEY_SCHEDULE_MODE, null)
        val activeInterval = preferences.getInt(KEY_SCHEDULE_INTERVAL, -1)
        if (
            activeMode != MODE_SHORT ||
            activeToken != token ||
            activeInterval != intervalMinutes ||
            intervalMinutes >= MIN_PERIODIC_MINUTES
        ) return
        enqueueShortSync(
            intervalMinutes = intervalMinutes.coerceAtLeast(1),
            token = token,
            policy = ExistingWorkPolicy.APPEND_OR_REPLACE,
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

    private fun enqueueShortSync(
        intervalMinutes: Int,
        token: String,
        policy: ExistingWorkPolicy,
    ) {
        val request = OneTimeWorkRequestBuilder<MailSyncWorker>()
            .setInitialDelay(intervalMinutes.toLong(), TimeUnit.MINUTES)
            .setInputData(
                Data.Builder()
                    .putString(MailSyncWorker.KEY_MODE, MailSyncWorker.MODE_SHORT_POLL)
                    .putString(MailSyncWorker.KEY_SHORT_TOKEN, token)
                    .putInt(MailSyncWorker.KEY_SHORT_INTERVAL, intervalMinutes)
                    .build(),
            )
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        manager.enqueueUniqueWork(shortWorkName(token), policy, request)
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    private fun shortWorkName(token: String): String = "$SHORT_SYNC_WORK_PREFIX$token"

    companion object {
        private const val MIN_PERIODIC_MINUTES = 15
        private const val PERIODIC_SYNC_WORK = "mail_periodic_sync"
        private const val MANUAL_SYNC_WORK = "mail_manual_sync"
        private const val SHORT_SYNC_WORK_PREFIX = "mail_short_sync_"
        private const val BACKGROUND_PREFS = "bond_mail_background"
        private const val KEY_SHORT_TOKEN = "short_sync_token"
        private const val KEY_SCHEDULE_MODE = "schedule_mode"
        private const val KEY_SCHEDULE_INTERVAL = "schedule_interval"
        private const val MODE_PERIODIC = "periodic"
        private const val MODE_SHORT = "short"
        private const val MODE_CONTINUOUS = "continuous"

        fun usesContinuousService(intervalMinutes: Int): Boolean =
            intervalMinutes in 1 until MIN_PERIODIC_MINUTES
    }
}
