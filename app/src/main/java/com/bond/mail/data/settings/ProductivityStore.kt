package com.bond.mail.data.settings

import android.content.Context

internal class ProductivityStore(context: Context) {
    private val prefs = context.getSharedPreferences("mail_productivity", Context.MODE_PRIVATE)
    fun get(account: String, key: String): String = prefs.getString("$account:$key", "").orEmpty()
    fun set(account: String, key: String, value: String) { prefs.edit().putString("$account:$key", value).apply() }
    fun reminder(id: String): Long = prefs.getLong("reminder:$id", 0)
    fun setReminder(id: String, at: Long) { prefs.edit().putLong("reminder:$id", at).commit() }
    fun reminders(): Map<String, Long> = prefs.all.filterKeys { it.startsWith("reminder:") }
        .mapNotNull { (k, v) -> (v as? Long)?.takeIf { it > 0 }?.let { k.removePrefix("reminder:") to it } }.toMap()
    fun notificationMode(account: String, sender: String): String {
        val mode = get(account, "notifications").ifBlank { "all" }
        fun matches(key: String) = get(account, key).split(',', ';', '\n').map { it.trim().lowercase() }
            .filter { it.isNotBlank() }.any { rule ->
                sender.equals(rule, true) || (rule.startsWith("@") && sender.substringAfterLast('@').equals(rule.drop(1), true))
            }
        if (mode == "off") return "off"
        if (matches("quiet")) return "silent"
        if (mode == "important" && !matches("important")) return "off"
        return mode
    }
}
