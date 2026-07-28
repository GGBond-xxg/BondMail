package com.bond.mail.data.mail

import android.util.Log
import com.bond.mail.BuildConfig

/** Debug-only mail diagnostics. Secrets and full message bodies are never written to Logcat. */
internal object MailLog {
    const val APP = "BondMail"
    const val IMAP = "BondMail-IMAP"
    const val SMTP = "BondMail-SMTP"
    const val PERF = "BondMail-Perf"
    const val WEB = "BondMail-Web"
    const val OAUTH = "BondMail-OAuth"

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(tag, message, error)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(tag, message, error)
    }

    fun accountHint(email: String): String {
        val trimmed = email.trim()
        val domain = trimmed.substringAfter('@', missingDelimiterValue = "unknown")
        val local = trimmed.substringBefore('@').take(2)
        return "$local***@$domain"
    }

    fun causeSummary(error: Throwable): String {
        var current = error
        val visited = hashSetOf<Throwable>()
        while (visited.add(current)) {
            val next = current.cause ?: break
            current = next
        }
        return "${current::class.java.simpleName}: ${current.message.orEmpty()}"
    }
}
