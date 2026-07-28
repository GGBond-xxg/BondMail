package com.bond.mail.data.performance

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps background MIME/IMAP work away from the user's active list scroll window.
 *
 * This does not lower the display refresh rate or disable synchronization. It only defers work that
 * is safe to postpone until the list has been still for a short period. Interactive operations such
 * as opening a message and manual pull-to-refresh never pass through this gate.
 */
object UiPerformanceGate {
    private val mailListScrolling = MutableStateFlow(false)

    @Volatile
    private var foregroundGraceUntilMs = SystemClock.elapsedRealtime() + DEFAULT_FOREGROUND_GRACE_MS

    fun onForeground(graceMs: Long = DEFAULT_FOREGROUND_GRACE_MS) {
        val requestedUntil = SystemClock.elapsedRealtime() + graceMs.coerceAtLeast(0L)
        if (requestedUntil > foregroundGraceUntilMs) foregroundGraceUntilMs = requestedUntil
    }

    fun setMailListScrolling(scrolling: Boolean) {
        mailListScrolling.value = scrolling
    }

    fun isMailListScrolling(): Boolean = mailListScrolling.value


    /**
     * Waits for a short user-idle window without honoring the longer foreground grace period.
     *
     * This is reserved for one-time UI warm-up work such as creating the reusable WebView after
     * the first inbox frame. It never runs while the message list is being dragged or flinging,
     * so the warm-up cost is paid during an idle gap instead of on the first message tap.
     */
    suspend fun awaitUiIdleWindow(
        settleDelayMs: Long = 650L,
        maximumWaitMs: Long = 6_000L,
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + maximumWaitMs.coerceAtLeast(0L)
        while (SystemClock.elapsedRealtime() < deadline) {
            if (mailListScrolling.value) {
                val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                withTimeoutOrNull(remaining) {
                    mailListScrolling.filter { scrolling -> !scrolling }.first()
                }
            }

            val quietDelay = settleDelayMs.coerceAtLeast(0L)
                .coerceAtMost((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            if (quietDelay > 0L) delay(quietDelay)
            if (!mailListScrolling.value) return true
        }
        return false
    }

    suspend fun awaitBackgroundWindow(
        settleDelayMs: Long = 1_200L,
        maximumWaitMs: Long = 30_000L,
    ) {
        val deadline = SystemClock.elapsedRealtime() + maximumWaitMs.coerceAtLeast(0L)

        while (SystemClock.elapsedRealtime() < deadline) {
            if (mailListScrolling.value) {
                val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                withTimeoutOrNull(remaining) {
                    mailListScrolling.filter { scrolling -> !scrolling }.first()
                }
            }

            val now = SystemClock.elapsedRealtime()
            val graceRemaining = (foregroundGraceUntilMs - now).coerceAtLeast(0L)
            val quietDelay = maxOf(graceRemaining, settleDelayMs.coerceAtLeast(0L))
                .coerceAtMost((deadline - now).coerceAtLeast(0L))
            if (quietDelay > 0L) delay(quietDelay)

            // Recheck after the quiet period. This matters when a user starts their first fling while
            // a WorkManager task is waiting: the task must stay paused instead of waking underneath
            // the scroll and causing the observed 90 -> 30 Hz drop.
            if (
                !mailListScrolling.value &&
                SystemClock.elapsedRealtime() >= foregroundGraceUntilMs
            ) return
        }
    }

    private const val DEFAULT_FOREGROUND_GRACE_MS = 5_000L
}
