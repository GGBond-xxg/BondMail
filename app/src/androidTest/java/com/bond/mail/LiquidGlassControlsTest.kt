package com.bond.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.theme.*
import com.bond.mail.ui.glass.components.LiquidBottomTab
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class LiquidGlassControlsTest {
    @Test
    fun toggleSupportsTapDragAndAccessibilityWhileDisabledActionsStayDisabled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        val context = instrumentation.targetContext
        val checked = mutableStateOf(false)
        val clicks = AtomicInteger()
        val disabledClicks = AtomicInteger()
        val motion = device.executeShellCommand("settings get global animator_duration_scale").trim()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var installed = false
                val deadline = android.os.SystemClock.uptimeMillis() + 20_000
                while (!installed && android.os.SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity { activity ->
                        installed = MainActivity::class.java.getDeclaredField("contentInstalled").apply { isAccessible = true }.getBoolean(activity)
                    }
                    if (!installed) android.os.SystemClock.sleep(100)
                }
                assertTrue(installed)
                scenario.onActivity { activity ->
                    activity.setContent {
                        BondMailTheme(AppSettings(uiStyle = UiStyle.LIQUID_GLASS, themeMode = ThemeMode.LIGHT)) {
                            Column(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background).statusBarsPadding().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                Text(if (checked.value) "Glass on" else "Glass off")
                                BondSwitch(checked.value, { checked.value = it }, Modifier.semantics { contentDescription = "Glass toggle" })
                                BondPrimaryButton({ clicks.incrementAndGet() }) { Text("Glass action") }
                                BondPrimaryButton({ disabledClicks.incrementAndGet() }, enabled = false) { Text("Disabled action") }
                                BondSwitch(true, { error("Disabled switch changed") }, Modifier.semantics { contentDescription = "Disabled toggle" }, enabled = false)
                            }
                        }
                    }
                }
                try {
                    val toggle = checkNotNull(device.wait(Until.findObject(By.desc("Glass toggle")), 20_000))
                    assertTrue(toggle.isCheckable)
                    assertFalse(toggle.isChecked)
                    toggle.click()
                    assertTrue(device.wait(Until.hasObject(By.text("Glass on")), 10_000))
                    val bounds = checkNotNull(device.findObject(By.desc("Glass toggle"))).visibleBounds
                    device.swipe(bounds.right - 8, bounds.centerY(), bounds.left + 8, bounds.centerY(), 24)
                    assertTrue(device.wait(Until.hasObject(By.text("Glass off")), 10_000))
                    checkNotNull(device.findObject(By.text("Glass action"))).click()
                    checkNotNull(device.findObject(By.text("Disabled action"))).click()
                    checkNotNull(device.findObject(By.desc("Disabled toggle"))).click()
                    assertEquals(1, clicks.get())
                    assertEquals(0, disabledClicks.get())
                    device.executeShellCommand("settings put global animator_duration_scale 0")
                    checkNotNull(device.findObject(By.desc("Glass toggle"))).click()
                    assertTrue(device.wait(Until.hasObject(By.text("Glass on")), 10_000))
                    device.takeScreenshot(File(context.getExternalFilesDir(null), "glass-controls-fallback.png"))
                } catch (failure: Throwable) {
                    device.takeScreenshot(File(context.getExternalFilesDir(null), "glass-controls-failure.png"))
                    device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "glass-controls-failure.xml"))
                    throw failure
                }
            }
        } finally {
            if (motion == "null") device.executeShellCommand("settings delete global animator_duration_scale")
            else device.executeShellCommand("settings put global animator_duration_scale $motion")
        }
    }
    @Test
    fun draggingGlassTabsDoesNotOpenTheParentDrawer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        val selected = mutableIntStateOf(0)
        val drawer = androidx.compose.material3.DrawerState(androidx.compose.material3.DrawerValue.Closed)
        val closedOffset = AtomicReference(Float.NaN)
        val drawerMoved = AtomicBoolean(false)
        val trackDrawer = AtomicBoolean(true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var installed = false
            val deadline = android.os.SystemClock.uptimeMillis() + 20_000
            while (!installed && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    installed = MainActivity::class.java.getDeclaredField("contentInstalled").apply { isAccessible = true }.getBoolean(activity)
                }
                if (!installed) android.os.SystemClock.sleep(100)
            }
            assertTrue(installed)
            scenario.onActivity { activity ->
                activity.setContent {
                    BondMailTheme(AppSettings(uiStyle = UiStyle.LIQUID_GLASS, themeMode = ThemeMode.LIGHT)) {
                        LaunchedEffect(drawer) {
                            snapshotFlow { drawer.currentOffset }.collect { offset ->
                                if (trackDrawer.get() && offset > closedOffset.get() + 1f) drawerMoved.set(true)
                            }
                        }
                        androidx.compose.material3.ModalNavigationDrawer(
                            drawerState = drawer,
                            drawerContent = { androidx.compose.material3.ModalDrawerSheet { Text(if (drawer.isOpen) "Drawer opened" else "Drawer moving") } },
                        ) {
                            Box(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
                                Text("Selected ${selected.intValue}", Modifier.align(androidx.compose.ui.Alignment.Center))
                                com.bond.mail.ui.glass.components.LiquidBottomTabs(
                                    { selected.intValue }, { selected.intValue = it }, glassBackdrop(), 3,
                                    Modifier.align(androidx.compose.ui.Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
                                        .semantics { contentDescription = "Test glass dock" },
                                ) {
                                    repeat(3) { index ->
                                        LiquidBottomTab({ selected.intValue = index }) { Text("Tab $index") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val dock = checkNotNull(device.wait(Until.findObject(By.desc("Test glass dock")), 20_000)).visibleBounds
            scenario.onActivity { closedOffset.set(drawer.currentOffset) }
            val x = dock.left + dock.width() / 6
            val y = dock.centerY()
            val endX = dock.centerX()
            val points = Array(8) { i -> android.graphics.Point(if (i < 7) x else endX, y) }
            assertTrue(device.swipe(points, 22)) // Hold first, then drag right across the selected pill.
            assertTrue(device.wait(Until.hasObject(By.text("Selected 1")), 10_000))
            assertTrue("Glass drag also opened the drawer", drawer.isClosed)
            val Point = { px: Int, py: Int -> android.graphics.Point(px, py) }
            val starts = listOf(x, dock.centerX(), dock.left + 8, dock.right - 8)
            for (startX in starts) {
                scenario.onActivity { selected.intValue = 0 }
                android.os.SystemClock.sleep(350)
                // Long hold at selected/unselected tabs and both ends, then leave the dock.
                val hold = List(8) { Point(startX, y) }
                assertTrue(device.swipe((hold + listOf(Point(endX, y), Point(dock.right + 8, y), Point(x, y))).toTypedArray(), 22))
                assertTrue("Drawer moved during a dock gesture starting at $startX", !drawerMoved.get())
                assertTrue(drawer.isClosed)
            }
            scenario.onActivity { selected.intValue = 0 }
            android.os.SystemClock.sleep(350)
            assertTrue(device.swipe(arrayOf(Point(x, y), Point(x, y - 100), Point(dock.right - 12, y - 100)), 30))
            assertFalse("Vertical-then-horizontal dock drag leaked into the drawer", drawerMoved.get())
            // Clicking an unselected tab must still work after the drag boundary consumes movement.
            checkNotNull(device.findObject(By.text("Tab 2"))).click()
            assertTrue(device.wait(Until.hasObject(By.text("Selected 2")), 10_000))
            // The fix must not disable the drawer's normal swipe gesture.
            trackDrawer.set(false)
            device.swipe(device.displayWidth / 4, device.displayHeight / 3, device.displayWidth * 9 / 10, device.displayHeight / 3, 35)
            device.wait(Until.hasObject(By.text("Drawer opened")), 10_000)
            assertTrue("Normal content swipe no longer opens the drawer", drawer.isOpen)
        }
    }

}
