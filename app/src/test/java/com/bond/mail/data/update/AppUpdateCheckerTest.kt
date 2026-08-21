package com.bond.mail.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {
    @Test
    fun semanticVersionComparisonHandlesMultiDigitParts() {
        assertTrue(AppUpdateChecker.isVersionNewer("v1.2.10", "1.2.9"))
        assertTrue(AppUpdateChecker.isVersionNewer("2.0.0", "1.99.99"))
        assertFalse(AppUpdateChecker.isVersionNewer("v1.2.9", "1.2.9"))
        assertFalse(AppUpdateChecker.isVersionNewer("v1.2.8", "1.2.9"))
    }

    @Test
    fun missingPatchPartIsComparedAsZero() {
        assertFalse(AppUpdateChecker.isVersionNewer("1.2", "1.2.0"))
        assertTrue(AppUpdateChecker.isVersionNewer("1.2.1", "1.2"))
    }

    @Test
    fun malformedTagsDoNotTriggerAnUpdate() {
        assertFalse(AppUpdateChecker.isVersionNewer("latest", "1.2.9"))
        assertFalse(AppUpdateChecker.isVersionNewer("v1.2.10", "unknown"))
    }
}
