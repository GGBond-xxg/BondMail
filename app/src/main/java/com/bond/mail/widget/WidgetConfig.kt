package com.bond.mail.widget

import android.content.Context
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.data.settings.UiStyle
import org.json.JSONObject

enum class WidgetPrivacy { NORMAL, HIDE_PREVIEW, HIDE_CONTENT }

data class WidgetConfig(
    val accountId: String,
    val style: UiStyle = UiStyle.MATERIAL3,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val privacy: WidgetPrivacy = WidgetPrivacy.NORMAL,
    val unread: Boolean = true,
    val sender: Boolean = true,
    val subject: Boolean = true,
    val preview: Boolean = false,
    val time: Boolean = true,
    val compose: Boolean = true,
    val refresh: Boolean = true,
) {
    fun encode(): String = JSONObject().apply {
        put("account", accountId); put("style", style.name); put("mode", mode.name)
        put("privacy", privacy.name)
        put("unread", unread); put("sender", sender); put("subject", subject)
        put("preview", preview); put("time", time); put("compose", compose); put("refresh", refresh)
    }.toString()

    companion object {
        fun decode(value: String): WidgetConfig? = runCatching {
            val j = JSONObject(value)
            WidgetConfig(
                j.getString("account"), UiStyle.valueOf(j.getString("style")),
                ThemeMode.valueOf(j.getString("mode")), WidgetPrivacy.valueOf(j.getString("privacy")),
                j.optBoolean("unread", true), j.optBoolean("sender", true),
                j.optBoolean("subject", true), j.optBoolean("preview", true),
                j.optBoolean("time", true), j.optBoolean("compose", true), j.optBoolean("refresh", true),
            )
        }.getOrNull()
    }
}

/** Only preferences live here. Mail content stays in Room and is never duplicated on disk. */
class WidgetStore(context: Context) {
    private val preferences = context.getSharedPreferences("mail_widgets", Context.MODE_PRIVATE)
    fun get(id: Int): WidgetConfig? = preferences.getString("widget_$id", null)?.let(WidgetConfig::decode)
    fun save(id: Int, config: WidgetConfig) { check(preferences.edit().putString("widget_$id", config.encode()).commit()) }
    fun delete(id: Int) { preferences.edit().remove("widget_$id").apply() }
    fun rememberAccount(id: String?) { preferences.edit().putString("last_account", id).apply() }
    fun refreshing(account: String): Boolean = System.currentTimeMillis() - preferences.getLong("refresh_$account", 0) in 0..120_000
    fun setRefreshing(account: String, active: Boolean) {
        preferences.edit().putLong("refresh_$account", if (active) System.currentTimeMillis() else 0).apply()
    }
    fun lastAccount(): String? = preferences.getString("last_account", null)
}

data class WidgetLayout(val compact: Boolean, val rows: Int, val summary: Boolean, val rowHeight: Int)

/** Budget actual dp and scaled text, not the launcher's advertised cell count. */
fun widgetLayout(width: Float, height: Float, fontScale: Float, preview: Boolean, status: Boolean = false): WidgetLayout {
    val scale = fontScale.coerceAtLeast(1f)
    val compact = width < 235f || height < 155f
    val summary = !compact && height >= 285f && preview
    val minimumRow = maxOf(37, ((if (summary) 54 else 37) * scale).toInt())
    val reserved = 28 + maxOf(48, (34 * scale).toInt()) + if (status) (16 * scale).toInt() else 0
    val rows = if (compact) 0 else ((height - reserved) / (minimumRow + 1)).toInt().coerceIn(0, 6)
    val rowHeight = if (rows > 0) ((height - reserved) / rows - 1).toInt().coerceIn(minimumRow, maxOf(minimumRow, (64 * scale).toInt())) else minimumRow
    return WidgetLayout(compact, rows, summary, rowHeight)
}
