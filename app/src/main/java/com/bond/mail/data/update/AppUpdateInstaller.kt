package com.bond.mail.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import kotlinx.coroutines.delay

/** Downloads a release APK through Android's visible download service and opens the installer. */
class AppUpdateInstaller(private val context: Context) {
    private val manager = context.getSystemService(DownloadManager::class.java)
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun start(update: AppUpdateInfo): Boolean {
        if (!update.downloadUrl.substringBefore('?').endsWith(".apk", ignoreCase = true)) return false
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("BondMail v${update.version}")
            .setDescription("Downloading update")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "BondMail-v${update.version}-${System.currentTimeMillis()}.apk",
            )
        val id = runCatching { manager.enqueue(request) }.getOrNull() ?: return false
        preferences.edit()
            .putLong(KEY_DOWNLOAD_ID, id)
            .remove(KEY_PERMISSION_REQUESTED)
            .apply()
        return true
    }

    suspend fun awaitAndInstall() {
        repeat(MAX_POLLS) {
            when (installIfReady()) {
                InstallState.PENDING -> delay(POLL_INTERVAL_MS)
                InstallState.FAILED,
                InstallState.INSTALL_LAUNCHED,
                InstallState.PERMISSION_REQUIRED,
                InstallState.NONE,
                -> return
            }
        }
    }

    fun installIfReady(): InstallState {
        val id = preferences.getLong(KEY_DOWNLOAD_ID, -1L).takeIf { it >= 0L }
            ?: return InstallState.NONE
        manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return InstallState.PENDING
            return when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> installDownloaded(id)
                DownloadManager.STATUS_FAILED -> {
                    clear()
                    InstallState.FAILED
                }
                else -> InstallState.PENDING
            }
        }
        return InstallState.PENDING
    }

    private fun installDownloaded(id: Long): InstallState {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            if (!preferences.getBoolean(KEY_PERMISSION_REQUESTED, false)) {
                preferences.edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply()
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return InstallState.PERMISSION_REQUIRED
        }
        val uri = manager.getUriForDownloadedFile(id) ?: return InstallState.PENDING
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(install)
            clear()
            InstallState.INSTALL_LAUNCHED
        }.getOrElse {
            clear()
            InstallState.FAILED
        }
    }

    private fun clear() {
        preferences.edit()
            .remove(KEY_DOWNLOAD_ID)
            .remove(KEY_PERMISSION_REQUESTED)
            .apply()
    }

    enum class InstallState { NONE, PENDING, PERMISSION_REQUIRED, INSTALL_LAUNCHED, FAILED }

    private companion object {
        private const val PREFERENCES_NAME = "bond_mail_update_download"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_PERMISSION_REQUESTED = "permission_requested"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val POLL_INTERVAL_MS = 1_000L
        private const val MAX_POLLS = 15 * 60
    }
}
