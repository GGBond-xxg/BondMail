package com.bond.mail.ui

import java.util.concurrent.atomic.AtomicBoolean

/** Serializes forward navigation while cover transitions keep their source screen composed. */
internal class ForwardNavigationGate {
    private val inFlight = AtomicBoolean(false)

    fun tryAcquire(activeRoute: String?, expectedSourceRoute: String): Boolean =
        activeRoute == expectedSourceRoute && inFlight.compareAndSet(false, true)

    fun release() {
        inFlight.set(false)
    }
}
