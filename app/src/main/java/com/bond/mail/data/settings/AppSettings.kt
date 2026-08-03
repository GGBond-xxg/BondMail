package com.bond.mail.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("bond_mail_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class MailDensity { COMFORTABLE, STANDARD, COMPACT }
enum class RemoteImagePolicy { ALWAYS, WIFI_ONLY, NEVER }
enum class PushAccessState { MISSING, VERIFYING, VERIFIED, REJECTED }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val density: MailDensity = MailDensity.STANDARD,
    val monetBrandIcons: Boolean = true,
    val syncMinutes: Int = 15,
    val pushAccessState: PushAccessState = PushAccessState.MISSING,
    val notifications: Boolean = true,
    val notificationPermissionPromptDismissed: Boolean = false,
    val biometricLock: Boolean = false,
    val remoteImagePolicy: RemoteImagePolicy = RemoteImagePolicy.WIFI_ONLY,
    val languageCode: String = "system",
)

class SettingsStore(private val context: Context) {
    private val startupHints =
        context.getSharedPreferences("bond_mail_startup_hints", Context.MODE_PRIVATE)

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic")
        val density = stringPreferencesKey("density")
        val monetBrandIcons = booleanPreferencesKey("monet_brand_icons")
        val sync = intPreferencesKey("sync_minutes")
        val pushAccessState = stringPreferencesKey("push_access_state")
        val notifications = booleanPreferencesKey("notifications")
        val notificationPermissionPromptDismissed = booleanPreferencesKey("notification_permission_prompt_dismissed")
        val biometric = booleanPreferencesKey("biometric")
        val remoteImages = stringPreferencesKey("remote_images")
        val language = stringPreferencesKey("language")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.theme] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = p[Keys.dynamic] ?: true,
            density = runCatching { MailDensity.valueOf(p[Keys.density] ?: MailDensity.STANDARD.name) }.getOrDefault(MailDensity.STANDARD),
            monetBrandIcons = p[Keys.monetBrandIcons] ?: true,
            syncMinutes = p[Keys.sync] ?: 15,
            pushAccessState = runCatching {
                PushAccessState.valueOf(
                    p[Keys.pushAccessState] ?: PushAccessState.MISSING.name,
                )
            }.getOrDefault(PushAccessState.MISSING),
            notifications = p[Keys.notifications] ?: true,
            notificationPermissionPromptDismissed = p[Keys.notificationPermissionPromptDismissed] ?: false,
            biometricLock = p[Keys.biometric] ?: false,
            remoteImagePolicy = runCatching { RemoteImagePolicy.valueOf(p[Keys.remoteImages] ?: RemoteImagePolicy.WIFI_ONLY.name) }.getOrDefault(RemoteImagePolicy.WIFI_ONLY),
            languageCode = p[Keys.language] ?: "system",
        )
    }

    fun startupThemeHint(): ThemeMode? = startupHints.getString("theme", null)
        ?.let { value -> runCatching { ThemeMode.valueOf(value) }.getOrNull() }

    fun rememberStartupTheme(value: ThemeMode) {
        startupHints.edit().putString("theme", value.name).apply()
    }

    suspend fun setTheme(value: ThemeMode) {
        // Keep a tiny synchronous mirror for the window/splash theme. DataStore remains the source
        // of truth; this hint only lets MainActivity set status-bar icon contrast before its first
        // asynchronous DataStore emission.
        rememberStartupTheme(value)
        context.dataStore.edit { it[Keys.theme] = value.name }
    }
    suspend fun setDynamic(value: Boolean) = context.dataStore.edit { it[Keys.dynamic] = value }
    suspend fun setDensity(value: MailDensity) = context.dataStore.edit { it[Keys.density] = value.name }
    suspend fun setMonetBrandIcons(value: Boolean) = context.dataStore.edit { it[Keys.monetBrandIcons] = value }
    suspend fun setSyncMinutes(value: Int) = context.dataStore.edit { it[Keys.sync] = value }
    suspend fun setPushAccessState(value: PushAccessState) =
        context.dataStore.edit { it[Keys.pushAccessState] = value.name }
    suspend fun setNotifications(value: Boolean) = context.dataStore.edit { it[Keys.notifications] = value }
    suspend fun setNotificationPermissionPromptDismissed(value: Boolean) = context.dataStore.edit {
        it[Keys.notificationPermissionPromptDismissed] = value
    }
    suspend fun setBiometric(value: Boolean) = context.dataStore.edit { it[Keys.biometric] = value }
    suspend fun setRemoteImages(value: RemoteImagePolicy) = context.dataStore.edit { it[Keys.remoteImages] = value.name }
    suspend fun setLanguage(value: String) = context.dataStore.edit { it[Keys.language] = value }
}
