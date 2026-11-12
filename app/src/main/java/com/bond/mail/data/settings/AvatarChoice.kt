package com.bond.mail.data.settings

import android.icu.text.BreakIterator
import java.util.Locale

/** Existing bare category keys and contact emoji remain compatible with the shared picker. */
object AvatarChoice {
    val categories = listOf("aidef", "bank", "simcard", "exchange", "sports", "clothes", "shopping", "hotel", "expressdelivery", "airplane", "wallet", "social", "gaming", "entertainment", "education", "food", "transport", "health", "jobs", "developer", "government")
    fun category(value: String?): String? = value?.removePrefix("icon:")?.takeIf { it in categories }
    fun glyph(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() && category(it) == null }
    fun isValid(value: String?): Boolean {
        if (value.isNullOrBlank() || category(value) != null) return true
        if (value.length > 32 || value.any { it == '\n' || it == '\r' || it == '\t' }) return false
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(value)
        iterator.first()
        return iterator.next() == value.length && iterator.next() == BreakIterator.DONE
    }
}
