package com.bond.mail.background

import android.content.Context
import com.bond.mail.data.security.CredentialStore

/**
 * Stores the user-supplied Cloudflare push access key with the same Android Keystore-backed
 * encryption used for mailbox app passwords. The value is never copied into DataStore or logs.
 */
internal class PushAccessKeyStore(context: Context) {
    private val credentials = CredentialStore(context.applicationContext)

    fun save(value: String) {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "Push access key is empty" }
        credentials.save(STORAGE_KEY, normalized)
    }

    fun read(): String? = credentials.read(STORAGE_KEY)?.takeIf(String::isNotBlank)

    fun clear() {
        credentials.delete(STORAGE_KEY)
    }

    private companion object {
        const val STORAGE_KEY = "__bondmail_push_access_key_v1"
    }
}
