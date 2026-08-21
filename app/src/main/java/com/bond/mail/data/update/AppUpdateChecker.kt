package com.bond.mail.data.update

import android.content.Context
import com.bond.mail.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val version: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val releasePageUrl: String,
)

sealed interface AppUpdateCheckResult {
    data class Available(val update: AppUpdateInfo) : AppUpdateCheckResult
    data object UpToDate : AppUpdateCheckResult
    data object Failed : AppUpdateCheckResult
}

/**
 * Checks BondMail's latest stable GitHub Release without requiring a token or a second server.
 * Network and parsing failures deliberately return null so startup is never interrupted.
 */
class AppUpdateChecker {
    suspend fun check(): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        val connection = runCatching {
            URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
        }.getOrNull() ?: return@withContext AppUpdateCheckResult.Failed

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)
            connection.setRequestProperty("User-Agent", "BondMail/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext AppUpdateCheckResult.Failed
            }

            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val update = parseRelease(payload) ?: return@withContext AppUpdateCheckResult.Failed
            if (isVersionNewer(update.version, BuildConfig.VERSION_NAME)) {
                AppUpdateCheckResult.Available(update)
            } else {
                AppUpdateCheckResult.UpToDate
            }
        } catch (_: Exception) {
            AppUpdateCheckResult.Failed
        } finally {
            connection.disconnect()
        }
    }

    suspend fun checkForUpdate(): AppUpdateInfo? = when (val result = check()) {
        is AppUpdateCheckResult.Available -> result.update
        AppUpdateCheckResult.Failed,
        AppUpdateCheckResult.UpToDate,
        -> null
    }

    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/GGBond-xxg/BondMail/releases/latest"
        private const val GITHUB_API_VERSION = "2026-03-10"
        private const val CONNECT_TIMEOUT_MS = 6_000
        private const val READ_TIMEOUT_MS = 8_000

        internal fun parseLatestRelease(
            payload: String,
            currentVersion: String,
        ): AppUpdateInfo? {
            val update = parseRelease(payload) ?: return null
            return update.takeIf { isVersionNewer(it.version, currentVersion) }
        }

        private fun parseRelease(payload: String): AppUpdateInfo? {
            val release = runCatching { JSONObject(payload) }.getOrNull() ?: return null
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null

            val tag = release.optString("tag_name").trim()
            if (tag.isBlank()) return null

            val version = tag.trimStart('v', 'V').ifBlank { tag }
            val releasePageUrl = release.optString("html_url").takeIf(String::isNotBlank)
                ?: "https://github.com/GGBond-xxg/BondMail/releases/tag/$tag"
            val assets = release.optJSONArray("assets")
            val apkUrl = (0 until (assets?.length() ?: 0))
                .asSequence()
                .mapNotNull { index -> assets?.optJSONObject(index) }
                .filter { asset ->
                    asset.optString("name").endsWith(".apk", ignoreCase = true)
                }
                .sortedByDescending { asset ->
                    asset.optString("name").contains("BondMail", ignoreCase = true)
                }
                .map { asset -> asset.optString("browser_download_url") }
                .firstOrNull(String::isNotBlank)

            return AppUpdateInfo(
                version = version,
                releaseNotes = sanitizeReleaseNotes(release.optString("body")),
                downloadUrl = apkUrl ?: releasePageUrl,
                releasePageUrl = releasePageUrl,
            )
        }

        internal fun isVersionNewer(candidate: String, current: String): Boolean {
            val candidateParts = versionParts(candidate)
            val currentParts = versionParts(current)
            if (candidateParts.isEmpty() || currentParts.isEmpty()) return false
            val partCount = maxOf(candidateParts.size, currentParts.size)
            repeat(partCount) { index ->
                val candidatePart = candidateParts.getOrElse(index) { 0L }
                val currentPart = currentParts.getOrElse(index) { 0L }
                if (candidatePart != currentPart) return candidatePart > currentPart
            }
            return false
        }

        private fun versionParts(version: String): List<Long> =
            Regex("\\d+").findAll(version).mapNotNull { it.value.toLongOrNull() }.toList()

        private fun sanitizeReleaseNotes(raw: String): String = raw
            .lineSequence()
            .map { line ->
                line
                    .replace(Regex("^\\s*#{1,6}\\s*"), "")
                    .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
                    .replace("**", "")
                    .replace("`", "")
                    .trimEnd()
            }
            .dropWhile(String::isBlank)
            .joinToString("\n")
            .trim()
            .take(MAX_RELEASE_NOTES_LENGTH)

        private const val MAX_RELEASE_NOTES_LENGTH = 1_600
    }
}

/** Stores only public release metadata and the user's prompt choice. */
class UpdatePromptStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun pendingUpdate(): AppUpdateInfo? {
        val version = preferences.getString(KEY_PENDING_VERSION, null)?.takeIf(String::isNotBlank)
            ?: return null
        if (preferences.getString(KEY_IGNORED_VERSION, null) == version) return null
        return AppUpdateInfo(
            version = version,
            releaseNotes = preferences.getString(KEY_PENDING_NOTES, "").orEmpty(),
            downloadUrl = preferences.getString(KEY_PENDING_DOWNLOAD_URL, "").orEmpty(),
            releasePageUrl = preferences.getString(KEY_PENDING_RELEASE_URL, "").orEmpty(),
        ).takeIf { it.downloadUrl.isNotBlank() && it.releasePageUrl.isNotBlank() }
    }

    fun hasPendingUpdate(): Boolean = pendingUpdate() != null

    /** Cancel keeps a cached prompt for the next cold start and paints the version red dot. */
    fun cancel(update: AppUpdateInfo) {
        preferences.edit()
            .putString(KEY_PENDING_VERSION, update.version)
            .putString(KEY_PENDING_NOTES, update.releaseNotes)
            .putString(KEY_PENDING_DOWNLOAD_URL, update.downloadUrl)
            .putString(KEY_PENDING_RELEASE_URL, update.releasePageUrl)
            .remove(KEY_IGNORED_VERSION)
            .apply()
    }

    /** Ignore suppresses cold-start prompts for this version; manual checks still return it. */
    fun ignore(update: AppUpdateInfo) {
        preferences.edit()
            .putString(KEY_IGNORED_VERSION, update.version)
            .removePending()
            .apply()
    }

    fun updateStarted() {
        preferences.edit().removePending().apply()
    }

    private fun android.content.SharedPreferences.Editor.removePending() = this
        .remove(KEY_PENDING_VERSION)
        .remove(KEY_PENDING_NOTES)
        .remove(KEY_PENDING_DOWNLOAD_URL)
        .remove(KEY_PENDING_RELEASE_URL)

    private companion object {
        private const val PREFERENCES_NAME = "bond_mail_update_prompt"
        private const val KEY_PENDING_VERSION = "pending_version"
        private const val KEY_PENDING_NOTES = "pending_notes"
        private const val KEY_PENDING_DOWNLOAD_URL = "pending_download_url"
        private const val KEY_PENDING_RELEASE_URL = "pending_release_url"
        private const val KEY_IGNORED_VERSION = "ignored_version"
    }
}
