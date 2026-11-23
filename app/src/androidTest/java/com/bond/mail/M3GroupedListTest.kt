package com.bond.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.components.GroupedListSurface
import com.bond.mail.ui.components.MailContentDefaults
import com.bond.mail.ui.components.groupedItemModifier
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.screens.ElasticRefreshContainer
import com.bond.mail.ui.theme.BondMailTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Compose frames, stable lazy keys, long-press input and pull-refresh lifecycle. */
@RunWith(AndroidJUnit4::class)
class M3GroupedListTest {
    @Test fun selectionBatchInsertionAndRefresh() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        var rows by mutableStateOf(listOf("A", "B", "C"))
        var selected by mutableStateOf<String?>(null)
        var refreshing by mutableStateOf(false)
        var refreshCount = 0
        var primary = 0
        var density = 1f
        val corners = mutableMapOf<String, Pair<Float, Float>>()
        val topHistory = mutableListOf<Float>()
        var recordReturn = false
        var rowY = 0f
        val releaseFrames = mutableListOf<Pair<Long, Float>>()
        fun settle() {
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(400)
        }
        fun checkCorners(id: String, top: Float, bottom: Float) {
            instrumentation.runOnMainSync {
                assertEquals("$id top", top, corners.getValue(id).first, 0.01f)
                assertEquals("$id bottom", bottom, corners.getValue(id).second, 0.01f)
            }
        }
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
                BondMailTheme(AppSettings(uiStyle = UiStyle.MATERIAL3)) {
                    primary = MaterialTheme.colorScheme.primary.toArgb()
                    density = LocalDensity.current.density
                    JsonStringsProvider("en") {
                        ElasticRefreshContainer(
                            refreshing = refreshing,
                            onRefresh = { refreshCount++; refreshing = true },
                            modifier = Modifier.fillMaxSize(),
                            indicatorTop = 40.dp,
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 110.dp),
                                verticalArrangement = Arrangement.spacedBy(MailContentDefaults.ItemSpacing),
                            ) {
                                itemsIndexed(rows, key = { _, id -> id }) { index, id ->
                                    val shape = MailContentDefaults.animatedItemShape(index, rows.size, selected == id)
                                    SideEffect {
                                        val outline = shape.createOutline(Size(300f, 64f), LayoutDirection.Ltr, Density(1f))
                                        val radii = if (outline is Outline.Rounded) {
                                            outline.roundRect.topLeftCornerRadius.x to outline.roundRect.bottomLeftCornerRadius.x
                                        } else 0f to 0f
                                        corners[id] = radii
                                        if (id == "A") topHistory.add(radii.first)
                                    }
                                    GroupedListSurface(
                                        shape = shape,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        selected = selected == id,
                                        onLongClick = { selected = id },
                                        onClick = { selected = null },
                                        modifier = groupedItemModifier(),
                                    ) {
                                        Text(id, Modifier.fillMaxWidth().height(64.dp)
                                            .onGloballyPositioned {
                                                if (id == "A") {
                                                    rowY = it.positionInRoot().y
                                                    if (recordReturn) releaseFrames += android.os.SystemClock.uptimeMillis() to rowY
                                                }
                                            }.padding(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.text("B")), 5_000))
            settle()
            checkCorners("A", 24f, 0f)
            checkCorners("B", 0f, 0f)
            checkCorners("C", 0f, 24f)
            device.findObject(By.text("B")).longClick()
            settle()
            checkCorners("B", 24f, 24f)
            checkCorners("A", 24f, 0f)
            instrumentation.runOnMainSync {
                topHistory.clear()
                rows = listOf("N1", "N2", "N3") + rows
            }
            settle()
            checkCorners("N1", 24f, 0f)
            checkCorners("N2", 0f, 0f)
            checkCorners("A", 0f, 0f)
            checkCorners("B", 24f, 24f)
            instrumentation.runOnMainSync {
                assertTrue("Former first row must morph, not snap", topHistory.any { it > 0f && it < 24f })
                selected = null
                rows = listOf("A", "B", "C")
            }
            settle()
            checkCorners("A", 24f, 0f)
            checkCorners("B", 0f, 0f)
            instrumentation.runOnMainSync { rows = listOf("A") }
            settle()
            checkCorners("A", 24f, 24f)
            val restingY = device.findObject(By.text("A")).visibleBounds.top
            // Keep the real pointer down: no refresh or wave may begin until it is released.
            fun touch(downTime: Long, action: Int, y: Float) {
                val event = android.view.MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(),
                    action, device.displayWidth / 2f, y, 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) }
                finally { event.recycle() }
            }
            val shortPull = android.os.SystemClock.uptimeMillis()
            touch(shortPull, android.view.MotionEvent.ACTION_DOWN, 500f)
            touch(shortPull, android.view.MotionEvent.ACTION_MOVE, 550f)
            touch(shortPull, android.view.MotionEvent.ACTION_UP, 550f)
            settle()
            instrumentation.runOnMainSync { assertEquals("Below threshold must not refresh", 0, refreshCount) }
            // Both a held short pull and a quick release must return in one continuous motion.
            // Recording actual layout frames catches the old spacing reopening after spring overshoot.
            val baselineY = rowY
            for (holdBeforeRelease in listOf(true, false, false)) {
                val pullTime = android.os.SystemClock.uptimeMillis()
                touch(pullTime, android.view.MotionEvent.ACTION_DOWN, 500f)
                repeat(12) { step ->
                    touch(pullTime, android.view.MotionEvent.ACTION_MOVE, 500f + (step + 1) * 20f)
                    android.os.SystemClock.sleep(16)
                }
                if (holdBeforeRelease) android.os.SystemClock.sleep(250)
                val releaseTime = android.os.SystemClock.uptimeMillis()
                instrumentation.runOnMainSync {
                    releaseFrames.clear()
                    releaseFrames += releaseTime to rowY
                    recordReturn = true
                }
                touch(pullTime, android.view.MotionEvent.ACTION_UP, 740f)
                android.os.SystemClock.sleep(550)
                instrumentation.runOnMainSync {
                    recordReturn = false
                    assertEquals("Short pulls never refresh", 0, refreshCount)
                    assertTrue("Must capture return motion", releaseFrames.size > 3)
                    assertTrue("Return must have intermediate positions", releaseFrames.any {
                        it.second > baselineY + 2 && it.second < releaseFrames.first().second - 2
                    })
                    assertTrue("No second bounce: $releaseFrames", releaseFrames.zipWithNext().all { (a, b) ->
                        b.second <= a.second + 1f && b.second >= baselineY - 1f
                    })
                    assertEquals("Return reaches the exact starting position", baselineY, rowY, 1f)
                    assertTrue("Pull and spacing must finish together, not run sequentially", releaseFrames.none {
                        it.first - releaseTime > 300 && it.second > baselineY + 2
                    })
                }
            }
            val downTime = android.os.SystemClock.uptimeMillis()
            touch(downTime, android.view.MotionEvent.ACTION_DOWN, 500f)
            repeat(24) { step ->
                touch(downTime, android.view.MotionEvent.ACTION_MOVE, 500f + (step + 1) * 55f)
                android.os.SystemClock.sleep(15)
            }
            settle()
            fun paintedProgressHeight(): Int {
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                var minY = bitmap.height
                var maxY = -1
                try {
                    for (y in (52 * density).toInt() until (67 * density).toInt()) {
                        for (x in 60 until bitmap.width - 60 step 3) {
                            val pixel = bitmap.getPixel(x, y)
                            if (listOf(0, 8, 16).all { shift ->
                                kotlin.math.abs(((pixel shr shift) and 255) - ((primary shr shift) and 255)) < 6
                            }) { minY = minOf(minY, y); maxY = maxOf(maxY, y) }
                        }
                    }
                } finally { bitmap.recycle() }
                return if (maxY >= minY) maxY - minY + 1 else 0
            }
            instrumentation.runOnMainSync { assertEquals("Holding the pull cannot start loading", 0, refreshCount) }
            val straightHeight = paintedProgressHeight()
            assertTrue("Held progress must actually paint in its top-edge bounds", straightHeight > 0)
            assertTrue("Held pull must be a straight stroke", straightHeight <= 4 * density + 2)
            touch(downTime, android.view.MotionEvent.ACTION_UP, 1820f)
            assertTrue(device.wait(Until.hasObject(By.desc("Refreshing…")), 3_000))
            settle()
            // The indeterminate segment periodically contracts to almost nothing. Inspect a cycle
            // instead of treating a single nearly-empty segment as a missing wave.
            val waveDeadline = android.os.SystemClock.uptimeMillis() + 2500
            var waveHeight = paintedProgressHeight()
            while (waveHeight <= straightHeight + density && android.os.SystemClock.uptimeMillis() < waveDeadline) {
                android.os.SystemClock.sleep(80)
                waveHeight = maxOf(waveHeight, paintedProgressHeight())
            }
            assertTrue("Released refresh must grow a wave: straight=$straightHeight wave=$waveHeight density=$density",
                waveHeight > straightHeight + density)
            instrumentation.runOnMainSync { assertEquals(1, refreshCount) }
            val progressBounds = device.findObject(By.desc("Refreshing…")).visibleBounds
            assertTrue("Mailbox refresh belongs to the top edge, not a floating circle",
                progressBounds.width() > progressBounds.height() * 10)
            assertEquals("Loading reserves space below the fixed bar", restingY + 34 * density,
                device.findObject(By.text("A")).visibleBounds.top.toFloat(), 1f)
            val returnPositions = mutableListOf<Int>()
            instrumentation.runOnMainSync { refreshing = false }
            val returnDeadline = android.os.SystemClock.uptimeMillis() + 350
            while (android.os.SystemClock.uptimeMillis() < returnDeadline) {
                device.findObject(By.text("A"))?.let { returnPositions += it.visibleBounds.top }
                android.os.SystemClock.sleep(16)
            }
            settle()
            assertEquals("Spacing must fully collapse after refreshing", restingY,
                device.findObject(By.text("A")).visibleBounds.top)
            assertTrue("Spacing must animate rather than jump back", returnPositions.any {
                it > restingY + 1 && it < restingY + (34 * density).toInt() - 1
            })
            assertTrue(device.wait(Until.gone(By.desc("Refreshing…")), 2_000))
            instrumentation.runOnMainSync { rows = emptyList() }
            settle()
            assertTrue(device.wait(Until.gone(By.text("A")), 2_000))
        }
    }
}
