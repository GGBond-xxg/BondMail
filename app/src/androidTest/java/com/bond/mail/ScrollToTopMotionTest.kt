package com.bond.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bond.mail.ui.motion.animateToTopWithMomentum
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** A tall header and spaced, uneven rows expose the old estimated-distance + final snap. */
@RunWith(AndroidJUnit4::class)
class ScrollToTopMotionTest {
    @Test fun tallHeaderSettlesWithoutAnAbruptFinalCorrection() = verifyMotion(228.dp)

    @Test fun shortFolderStripSettlesWithoutAnAbruptFinalCorrection() = verifyMotion(60.dp)

    private fun verifyMotion(headerHeight: Dp) {
        val finished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var installed = false
            val deadline = android.os.SystemClock.uptimeMillis() + 20_000
            while (!installed && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { installed = MainActivity::class.java.getDeclaredField("contentInstalled")
                    .apply { isAccessible = true }.getBoolean(it) }
                if (!installed) android.os.SystemClock.sleep(100)
            }
            assertTrue(installed)
            scenario.onActivity { activity -> activity.setContent {
                val state = rememberLazyListState()
                val density = LocalDensity.current
                val heights = List(80) { if (it == 0) headerHeight else listOf(80.dp, 96.dp, 124.dp)[it % 3] }
                val starts = heights.runningFold(0) { sum, height ->
                    sum + with(density) { height.roundToPx() + 13.dp.roundToPx() }
                }
                LazyColumn(state = state, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 100.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    items(heights.size) { Box(Modifier.fillMaxWidth().height(heights[it])) }
                }
                LaunchedEffect(Unit) {
                    try {
                        snapshotFlow { state.layoutInfo.totalItemsCount }.first { it == heights.size }
                        for (start in listOf(3, 12, 60)) {
                            state.scrollToItem(start, 31)
                            withFrameNanos { }
                            val positions = mutableListOf<Int>()
                            val sampler = launch(start = CoroutineStart.UNDISPATCHED) {
                                snapshotFlow { starts[state.firstVisibleItemIndex] + state.firstVisibleItemScrollOffset }
                                    .collect { positions.add(it) }
                            }
                            state.animateToTopWithMomentum(true)
                            withFrameNanos { }
                            sampler.cancelAndJoin()
                            assertEquals(0, state.firstVisibleItemIndex)
                            assertEquals(0, state.firstVisibleItemScrollOffset)
                            assertTrue("Scroll from $start should animate", positions.size > 8)
                            val tail = positions.lastOrNull { it > 0 } ?: error("No moving samples")
                            android.util.Log.i("ScrollToTopMotionTest", "start=$start tail=${positions.takeLast(8)}")
                            assertTrue("Final correction from $start was $tail px: ${positions.takeLast(8)}",
                                tail <= with(density) { 4.dp.roundToPx() })
                        }
                        state.scrollToItem(12, 31)
                        state.animateToTopWithMomentum(false)
                        assertEquals(0, state.firstVisibleItemIndex)
                        assertEquals(0, state.firstVisibleItemScrollOffset)

                        state.scrollToItem(60, 31)
                        val scrolling = launch { state.animateToTopWithMomentum(true) }
                        delay(60)
                        scrolling.cancelAndJoin()
                        val stopped = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
                        delay(100)
                        assertEquals(stopped, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset)
                        assertTrue("Cancellation must not force a final jump", stopped.first > 0)
                    } catch (error: Throwable) {
                        failure.set(error)
                    } finally {
                        finished.countDown()
                    }
                }
            } }
            assertTrue("Animation test timed out", finished.await(45, TimeUnit.SECONDS))
            failure.get()?.let { throw it }
        }
    }
}
