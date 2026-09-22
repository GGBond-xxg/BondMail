package com.bond.mail.data.mail

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted, bounded, disposable cache; excluded from backups by using noBackupFilesDir. */
internal class TranslationCache(context: Context) {
    private val directory = File(context.noBackupFilesDir, "translations")
    private fun key(): SecretKey = synchronized(TranslationCache::class.java) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("bond_translation_cache", null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder("bond_translation_cache", KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
    }
    private fun file(body: String, provider: TranslationProvider, target: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest("v1\u0000${provider.name}\u0000$target\u0000$body".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(directory, hash)
    }
    suspend fun read(body: String, provider: TranslationProvider, target: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val source = file(body, provider, target)
            val bytes = source.readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }.getOrNull()
    }
    suspend fun write(body: String, provider: TranslationProvider, target: String, translated: String) = withContext(Dispatchers.IO) {
        runCatching {
            directory.mkdirs()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val bytes = cipher.iv + cipher.doFinal(translated.toByteArray())
            val destination = file(body, provider, target)
            val temporary = File.createTempFile("translation", ".tmp", directory)
            try { temporary.writeBytes(bytes); check(temporary.renameTo(destination)) } finally { temporary.delete() }
            var total = directory.listFiles().orEmpty().sumOf { it.length() }
            directory.listFiles().orEmpty().sortedBy { it.lastModified() }.forEach {
                if (total > 20 * 1024 * 1024) { val size = it.length(); if (it.delete()) total -= size }
            }
        }
        Unit
    }
    suspend fun size(): Long = withContext(Dispatchers.IO) { directory.listFiles().orEmpty().sumOf { it.length() } }
    suspend fun clear() = withContext(Dispatchers.IO) { directory.listFiles().orEmpty().forEach { it.delete() } }
}
