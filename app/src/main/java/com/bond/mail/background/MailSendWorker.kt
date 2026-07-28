package com.bond.mail.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bond.mail.MailApplication

class MailSendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getString("task_id") ?: return Result.failure()
        val repository = (applicationContext as MailApplication).container.repository
        return runCatching {
            repository.sendOutboxTask(taskId)
            Result.success()
        }.getOrElse { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }
}
