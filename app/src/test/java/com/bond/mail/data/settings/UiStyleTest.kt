package com.bond.mail.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class UiStyleTest {
    @Test
    fun storageValues_areStableAndRoundTrip() {
        UiStyle.entries.forEach { style ->
            assertEquals(style, UiStyle.fromStorageValue(style.storageValue))
        }
    }

    @Test
    fun missingOrUnknownValue_fallsBackToMaterial3() {
        assertEquals(UiStyle.MATERIAL3, UiStyle.fromStorageValue(null))
        assertEquals(UiStyle.MATERIAL3, UiStyle.fromStorageValue("future-style"))
    }
}
