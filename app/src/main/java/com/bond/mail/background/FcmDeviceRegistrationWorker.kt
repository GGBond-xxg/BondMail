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
import com.bond.mail.MailApplication
import com.bond.mail.data.mail.MailLog
import com.bond.mail.data.settings.PushAccessState
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class FcmDeviceRegistrationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = (applicationContext as MailApplication).container.settings
        val registration = FcmRegistrationStore.snapshot(applicationContext)
            ?: run {
                settings.setPushAccessState(PushAccessState.MISSING)
                return Result.success()
            }
        return runCatching {
            val clientConfig = downloadClientConfig(registration)
            val fcmToken = resolveFcmToken(clientConfig)
            upload(registration, fcmToken)
            settings.setPushAccessState(PushAccessState.VERIFIED)
            MailLog.d(
                MailLog.APP,
                "FCM device registration uploaded interval=${registration.intervalMinutes}m",
            )
            Result.success()
        }.getOrElse { error ->
            if (error is InvalidPushAccessKeyException) {
                settings.setPushAccessState(PushAccessState.REJECTED)
                MailLog.w(MailLog.APP, "FCM device registration rejected by access-key policy")
                return@getOrElse Result.success()
            }
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
        fcmToken: String,
    ) = withContext(Dispatchers.IO) {
        val connection = (
            URI.create("${registration.serviceOrigin}/v1/devices/register")
                .toURL()
                .openConnection() as HttpURLConnection
            ).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty(PUSH_ACCESS_KEY_HEADER, registration.pushAccessKey)
            setRequestProperty(
                "User-Agent",
                "BondMail/${BuildConfig.VERSION_NAME} Android/${Build.VERSION.SDK_INT}",
            )
        }
        try {
            val payload = JSONObject()
                .put("installationId", registration.installationId)
                .put("installationSecret", registration.installationSecret)
                .put("fcmToken", fcmToken)
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
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED ||
                status == HttpURLConnection.HTTP_FORBIDDEN
            ) {
                throw InvalidPushAccessKeyException()
            }
            if (status !in 200..299 || !JSONObject(response).optBoolean("ok")) {
                throw IllegalStateException("Push registration HTTP $status")
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun downloadClientConfig(
        registration: FcmRegistrationStore.RegistrationSnapshot,
    ): PushFirebaseClientConfig = withContext(Dispatchers.IO) {
        val connection = (
            URI.create("${registration.serviceOrigin}/v1/client-config")
                .toURL()
                .openConnection() as HttpURLConnection
            ).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty(PUSH_ACCESS_KEY_HEADER, registration.pushAccessKey)
            setRequestProperty(
                "User-Agent",
                "BondMail/${BuildConfig.VERSION_NAME} Android/${Build.VERSION.SDK_INT}",
            )
        }
        try {
            val status = connection.responseCode
            val responseStream =
                if (status in 200..299) connection.inputStream else connection.errorStream
            val response = responseStream
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED ||
                status == HttpURLConnection.HTTP_FORBIDDEN
            ) {
                throw InvalidPushAccessKeyException()
            }
            if (status !in 200..299) {
                throw IllegalStateException("Push client config HTTP $status")
            }
            val payload = JSONObject(response)
            if (!payload.optBoolean("ok")) {
                throw IllegalStateException("Push client config rejected")
            }
            PushFirebaseClientConfig(
                projectId = payload.requireString("projectId"),
                applicationId = payload.requireString("applicationId"),
                apiKey = payload.requireString("apiKey"),
                senderId = payload.requireString("senderId"),
            )
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveFcmToken(config: PushFirebaseClientConfig): String {
        val appName = "bondmail-push-${config.applicationId.sha256Prefix()}"
        val firebaseApp = FirebaseApp.getApps(applicationContext)
            .firstOrNull { app -> app.name == appName }
            ?: synchronized(FirebaseApp::class.java) {
                FirebaseApp.getApps(applicationContext)
                    .firstOrNull { app -> app.name == appName }
                    ?: FirebaseApp.initializeApp(
                        applicationContext,
                        FirebaseOptions.Builder()
                            .setProjectId(config.projectId)
                            .setApplicationId(config.applicationId)
                            .setApiKey(config.apiKey)
                            .setGcmSenderId(config.senderId)
                            .build(),
                        appName,
                    )
            }
        return suspendCancellableCoroutine { continuation ->
            firebaseApp.get(FirebaseMessaging::class.java).token
                .addOnSuccessListener { token ->
                    if (continuation.isActive) continuation.resume(token)
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }
    }

    companion object {
        private const val PUSH_ACCESS_KEY_HEADER = "X-BondMail-Push-Key"
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

    private fun JSONObject.requireString(name: String): String =
        optString(name).trim().takeIf(String::isNotEmpty)
            ?: throw IllegalStateException("Push client config is missing $name")

    private fun String.sha256Prefix(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private data class PushFirebaseClientConfig(
        val projectId: String,
        val applicationId: String,
        val apiKey: String,
        val senderId: String,
    )

    private class InvalidPushAccessKeyException : IllegalStateException()
}
