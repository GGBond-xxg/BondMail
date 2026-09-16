package com.bond.mail.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardNavigationGateTest {
    @Test
    fun repeatedRequestsAreRejectedUntilRelease() {
        val gate = ForwardNavigationGate()

        assertTrue(gate.tryAcquire(activeRoute = "main", expectedSourceRoute = "main"))
        repeat(8) {
            assertFalse(gate.tryAcquire(activeRoute = "main", expectedSourceRoute = "main"))
        }

        gate.release()
        assertTrue(gate.tryAcquire(activeRoute = "main", expectedSourceRoute = "main"))
    }

    @Test
    fun coveredSourceCannotAcquireGate() {
        val gate = ForwardNavigationGate()

        assertFalse(gate.tryAcquire(activeRoute = "detail/{messageId}", expectedSourceRoute = "main"))
        assertTrue(gate.tryAcquire(activeRoute = "main", expectedSourceRoute = "main"))
    }
}
