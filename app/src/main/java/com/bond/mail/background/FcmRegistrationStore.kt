package com.bond.mail.background

import android.content.Context
import android.util.Base64
import com.bond.mail.data.mail.MailLog
import com.google.firebase.messaging.FirebaseMessaging
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.UUID

/**
 * Keeps the current device registration token in app-private storage.
 *
 * Firebase's Notifications composer currently requires this identifier for direct test messages.
 * The token is deliberately not written to Logcat. A short SHA-256 fingerprint is enough for local
 * diagnostics without exposing a value that can be targeted by a trusted FCM sender.
 */
internal object FcmRegistrationStore {
    private const val PREFERENCES_NAME = "fcm_registration"
    private const val TOKEN_KEY = "registration_token"
    private const val UNUSED_INSTALLATION_ID_KEY = "installation_id"
    private const val INSTALLATION_ID_KEY = "push_installation_id"
    private const val INSTALLATION_SECRET_KEY = "push_installation_secret"
    private const val SYNC_INTERVAL_KEY = "sync_interval"
    private const val SYNC_ENABLED_KEY = "sync_enabled"

    @Suppress("DEPRECATION")
    fun register(context: Context) {
        // Refresh the self-hosted Firebase token and Worker registration on every process start.
        // Do not make that depend on the default Firebase project's token callback: a custom
        // deployment must remain repairable when the bundled project is delayed or unavailable.
        if (PushAccessConfigStore(context).read() != null) {
            FcmDeviceRegistrationWorker.enqueue(context)
        }
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> storeToken(context, token) }
            .addOnFailureListener { error ->
                MailLog.w(
                    MailLog.APP,
                    "FCM registration failed cause=${MailLog.causeSummary(error)}",
                    error,
                )
            }
    }

    fun save(context: Context, token: String) {
        if (storeToken(context, token) && PushAccessConfigStore(context).read() != null) {
            FcmDeviceRegistrationWorker.enqueue(context)
        }
    }

    private fun storeToken(context: Context, token: String): Boolean {
        val normalized = token.trim()
        if (normalized.isEmpty()) return false
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(TOKEN_KEY, normalized)
            .remove(UNUSED_INSTALLATION_ID_KEY)
            .apply()
        MailLog.d(
            MailLog.APP,
            "FCM registration ready fingerprint=${fingerprint(normalized)}",
        )
        return true
    }

    fun read(context: Context): String? =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(TOKEN_KEY, null)
            ?.takeIf(String::isNotBlank)

    fun updateSyncPreference(
        context: Context,
        enabled: Boolean,
        intervalMinutes: Int,
    ) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SYNC_ENABLED_KEY, enabled)
            .putInt(SYNC_INTERVAL_KEY, intervalMinutes.toSupportedInterval())
            .apply()
        if (PushAccessConfigStore(context).read() != null) {
            FcmDeviceRegistrationWorker.enqueue(context)
        }
    }

    fun updatePushAccessConfig(
        context: Context,
        serviceOrigin: String,
        accessKey: String,
    ) {
        PushAccessConfigStore(context).save(serviceOrigin, accessKey)
        FcmDeviceRegistrationWorker.enqueue(context)
    }

    fun readPushAccessConfig(context: Context): PushAccessConfig? =
        PushAccessConfigStore(context).read()

    internal fun snapshot(context: Context): RegistrationSnapshot? {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val pushConfig = PushAccessConfigStore(context).read() ?: return null
        val installationId = preferences.getString(INSTALLATION_ID_KEY, null)
            ?.takeIf(String::isNotBlank)
            ?: UUID.randomUUID().toString().also { generated ->
                preferences.edit().putString(INSTALLATION_ID_KEY, generated).apply()
            }
        val installationSecret = preferences.getString(INSTALLATION_SECRET_KEY, null)
            ?.takeIf(String::isNotBlank)
            ?: generateInstallationSecret().also { generated ->
                preferences.edit().putString(INSTALLATION_SECRET_KEY, generated).apply()
            }
        return RegistrationSnapshot(
            installationId = installationId,
            installationSecret = installationSecret,
            serviceOrigin = pushConfig.serviceOrigin,
            pushAccessKey = pushConfig.accessKey,
            intervalMinutes = preferences.getInt(SYNC_INTERVAL_KEY, 15).toSupportedInterval(),
            enabled = preferences.getBoolean(SYNC_ENABLED_KEY, true),
        )
    }

    private fun fingerprint(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .take(6)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun generateInstallationSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun Int.toSupportedInterval(): Int = when {
        this <= 1 -> 1
        this <= 5 -> 5
        this <= 10 -> 10
        this <= 15 -> 15
        this <= 30 -> 30
        else -> 60
    }

    internal data class RegistrationSnapshot(
        val installationId: String,
        val installationSecret: String,
        val serviceOrigin: String,
        val pushAccessKey: String,
        val intervalMinutes: Int,
        val enabled: Boolean,
    )
}
