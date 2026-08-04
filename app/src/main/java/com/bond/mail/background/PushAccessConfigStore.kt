package com.bond.mail.background

import android.content.Context
import com.bond.mail.data.security.CredentialStore
import java.net.URI
import java.util.Locale

/**
 * Stores the user-supplied Cloudflare Worker origin and access key with the same Android
 * Keystore-backed encryption used for mailbox app passwords. Neither value is copied into
 * DataStore or logs.
 */
internal class PushAccessConfigStore(context: Context) {
    private val credentials = CredentialStore(context.applicationContext)

    fun save(serviceOrigin: String, accessKey: String) {
        val normalizedOrigin = normalizeServiceOrigin(serviceOrigin)
            ?: throw IllegalArgumentException("Push service origin is invalid")
        val normalizedKey = accessKey.trim()
        require(normalizedKey.isNotEmpty()) { "Push access key is empty" }
        credentials.save(ORIGIN_STORAGE_KEY, normalizedOrigin)
        credentials.save(ACCESS_KEY_STORAGE_KEY, normalizedKey)
    }

    fun read(): PushAccessConfig? {
        val accessKey = credentials.read(ACCESS_KEY_STORAGE_KEY)?.takeIf(String::isNotBlank)
            ?: return null
        // v1.2.1 stored only the access key. Keep that installation working until the user opens
        // the new configuration page and explicitly saves an origin.
        val serviceOrigin = credentials.read(ORIGIN_STORAGE_KEY)
            ?.let(::normalizeServiceOrigin)
            ?: LEGACY_SERVICE_ORIGIN
        return PushAccessConfig(serviceOrigin = serviceOrigin, accessKey = accessKey)
    }

    fun clear() {
        credentials.delete(ORIGIN_STORAGE_KEY)
        credentials.delete(ACCESS_KEY_STORAGE_KEY)
    }

    companion object {
        const val LEGACY_SERVICE_ORIGIN = "https://push.usdit.eu.cc"

        fun normalizeServiceOrigin(rawValue: String): String? {
            val trimmed = rawValue.trim().trimEnd('/')
            if (trimmed.isEmpty() || trimmed.length > 512) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val parsed = runCatching { URI(withScheme) }.getOrNull() ?: return null
            if (!parsed.scheme.equals("https", ignoreCase = true)) return null
            if (parsed.userInfo != null || parsed.query != null || parsed.fragment != null) return null
            if (!parsed.path.isNullOrEmpty() && parsed.path != "/") return null
            val host = parsed.host?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank)
                ?: return null
            return runCatching {
                URI("https", null, host, parsed.port, null, null, null).toASCIIString()
            }.getOrNull()
        }

        private const val ACCESS_KEY_STORAGE_KEY = "__bondmail_push_access_key_v1"
        private const val ORIGIN_STORAGE_KEY = "__bondmail_push_service_origin_v1"
    }
}

internal data class PushAccessConfig(
    val serviceOrigin: String,
    val accessKey: String,
)
