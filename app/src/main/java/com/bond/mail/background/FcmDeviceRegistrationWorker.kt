package com.bond.mail.background

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bond.mail.BuildConfig
import com.bond.mail.data.mail.MailLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

internal class FcmDeviceRegistrationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val registration = FcmRegistrationStore.snapshot(applicationContext)
            ?: return Result.success()
        return runCatching {
            upload(registration)
            MailLog.d(
                MailLog.APP,
                "FCM device registration uploaded interval=${registration.intervalMinutes}m",
            )
            Result.success()
        }.getOrElse { error ->
            MailLog.w(
                MailLog.APP,
                "FCM device registration failed cause=${MailLog.causeSummary(error)}",
                error,
            )
            Result.retry()
        }
    }

    private suspend fun upload(
        registration: FcmRegistrationStore.RegistrationSnapshot,
    ) = withContext(Dispatchers.IO) {
        val connection = (
            URI.create(REGISTRATION_ENDPOINT).toURL().openConnection() as HttpURLConnection
            ).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty(
                "User-Agent",
                "BondMail/${BuildConfig.VERSION_NAME} Android/${Build.VERSION.SDK_INT}",
            )
        }
        try {
            val payload = JSONObject()
                .put("installationId", registration.installationId)
                .put("installationSecret", registration.installationSecret)
                .put("fcmToken", registration.fcmToken)
                .put("intervalMinutes", registration.intervalMinutes)
                .put("enabled", registration.enabled)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .toString()
            connection.outputStream.use { stream ->
                stream.write(payload.toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val responseStream =
                if (status in 200..299) connection.inputStream else connection.errorStream
            val response = responseStream
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299 || !JSONObject(response).optBoolean("ok")) {
                throw IllegalStateException("Push registration HTTP $status")
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val REGISTRATION_ENDPOINT =
            "https://push.usdit.eu.cc/v1/devices/register"
        private const val UNIQUE_WORK_NAME = "fcm_device_registration"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<FcmDeviceRegistrationWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
