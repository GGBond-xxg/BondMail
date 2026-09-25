package com.bond.mail.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class MailDisplayMode { AUTO, ORIGINAL, LIGHT, TEXT }

/** Exact addresses take priority; domain rules deliberately do not affect subdomains. */
object SenderPresentationRules {
    fun address(value: String): String = value.substringAfterLast('<').substringBefore('>').trim().lowercase(Locale.ROOT)
    fun domain(value: String): String = address(value).substringAfterLast('@', "")
    fun resolve(values: Map<String, String>, kind: String, sender: String): String? {
        val mailbox = address(sender)
        // Provider picker placeholders such as @outlook.com are not sender addresses.
        if (mailbox.substringBefore('@').isBlank()) return null
        return values["$kind:email:$mailbox"] ?: values["$kind:domain:${domain(sender)}"]
    }
}

class MailPresentationStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mail_presentation", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val values = mutable.asStateFlow()
    private fun read() = prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()
    fun icon(sender: String): String? = SenderPresentationRules.resolve(values.value, "icon", sender)
    @Synchronized fun set(kind: String, sender: String, domain: Boolean, value: String?) {
        val scope = if (domain) "domain" else "email"
        val target = if (domain) SenderPresentationRules.domain(sender) else SenderPresentationRules.address(sender)
        if (target.isBlank()) return
        val key = "$kind:$scope:$target"
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
        mutable.value = read()
    }
    companion object {
        @Volatile private var instance: MailPresentationStore? = null
        fun get(context: Context): MailPresentationStore = instance ?: synchronized(this) {
            instance ?: MailPresentationStore(context).also { instance = it }
        }
    }
}
