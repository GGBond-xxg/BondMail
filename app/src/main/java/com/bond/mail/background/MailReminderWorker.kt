package com.bond.mail.background

import android.content.Context
import androidx.work.*
import com.bond.mail.MailApplication
import com.bond.mail.data.settings.ProductivityStore
import java.util.concurrent.TimeUnit

class MailReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("message") ?: return Result.failure()
        val expected = inputData.getLong("at", 0)
        val store = ProductivityStore(applicationContext)
        if (store.reminder(id) != expected || expected <= 0) return Result.success()
        val container = (applicationContext as MailApplication).container
        val message = container.repository.messageNow(id)
        if (message == null) { store.setReminder(id, 0); return Result.success() }
        if (!container.notifications.canPostNotifications()) return Result.retry()
        container.notifications.show(message, reminder = true)
        if (store.reminder(id) == expected) store.setReminder(id, 0)
        return Result.success()
    }
}

internal fun scheduleMailReminder(context: Context, id: String, at: Long) {
    ProductivityStore(context).setReminder(id, at)
    val manager = WorkManager.getInstance(context)
    if (at <= 0) { manager.cancelUniqueWork("mail-reminder:$id"); return }
    val request = OneTimeWorkRequestBuilder<MailReminderWorker>()
        .setInputData(workDataOf("message" to id, "at" to at))
        .setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
    manager.enqueueUniqueWork("mail-reminder:$id", ExistingWorkPolicy.REPLACE, request)
}
