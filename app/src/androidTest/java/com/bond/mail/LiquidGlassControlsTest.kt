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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

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
}
