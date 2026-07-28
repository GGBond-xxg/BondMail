package com.bond.mail.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bond.mail.MailApplication

/** Uploads a locally saved draft to the provider's IMAP Drafts folder. */
class MailDraftWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val repository = (applicationContext as MailApplication).container.repository
        return runCatching {
            repository.syncDraftTask(taskId)
            Result.success()
        }.getOrElse {
            if (runAttemptCount < 4) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
    }
}
