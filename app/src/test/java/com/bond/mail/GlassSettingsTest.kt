package com.bond.mail

import com.bond.mail.data.settings.GlassSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassSettingsTest {
    @Test fun corruptOrOutOfRangePreferencesCannotReachTheShader() {
        assertEquals(GlassSettings(6f, 12f, 24f, 0f),
            GlassSettings(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN).normalized())
        assertEquals(GlassSettings(0f, 32f, 64f, 1f), GlassSettings(-1f, 100f, 1000f, 5f).normalized())
        assertEquals(GlassSettings(0f, 0f, 0f, 0f), GlassSettings(0f, 0f, 0f, 0f).normalized())
        assertEquals(0.37f, GlassSettings(chromaticAberration = 0.37f).normalized().chromaticAberration, 0f)
    }
}
