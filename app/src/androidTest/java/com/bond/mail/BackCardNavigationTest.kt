package com.bond.mail

import android.graphics.Color as AndroidColor
import androidx.activity.BackEventCompat
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.ui.motion.BondBackScreen
import com.bond.mail.ui.motion.TelegramMailTransition
import com.bond.mail.ui.theme.BondMailTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class BackCardNavigationTest {
    @Test fun heldGestureCancelCommitAndButtonWorkForComposeAndFrozenReader() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var installed = false
            val deadline = android.os.SystemClock.uptimeMillis() + 20_000
            while (!installed && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { installed = MainActivity::class.java.getDeclaredField("contentInstalled")
                    .apply { isAccessible = true }.getBoolean(it) }
                android.os.SystemClock.sleep(100)
            }
            assertTrue(installed)
            for (reader in listOf(false, true)) for (edge in listOf(BackEventCompat.EDGE_LEFT, BackEventCompat.EDGE_RIGHT)) {
                val backs = AtomicInteger()
                val taps = AtomicInteger()
                val active = mutableStateOf(true)
                scenario.onActivity { activity -> activity.setContent {
                    BondMailTheme(AppSettings()) {
                        Box(Modifier.fillMaxSize().background(Color.Red)) {
                            if (active.value) {
                                val committed = { backs.incrementAndGet(); active.value = false }
                                val page: @Composable (() -> Unit) -> Unit = { back ->
                                    Box(Modifier.fillMaxSize().background(Color.Blue).clickable { taps.incrementAndGet() }) {
                                        Text("Back card", Modifier.statusBarsPadding().clickable(onClick = back))
                                    }
                                }
                                if (reader) TelegramMailTransition(null, true, committed) { back, ready ->
                                    LaunchedEffect(Unit) { ready() }
                                    page(back)
                                } else BondBackScreen(null, true, committed) { page(it) }
                            } else Text("Returned", Modifier.statusBarsPadding())
                        }
                    }
                } }
                assertTrue(device.wait(Until.hasObject(By.text("Back card")), 5_000))
                android.os.SystemClock.sleep(500)
                fun start() {
                    scenario.onActivity { it.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 700f, 0f, edge)) }
                    android.os.SystemClock.sleep(100)
                    scenario.onActivity { it.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(250f, 700f, .7f, edge)) }
                    android.os.SystemClock.sleep(250)
                }
                start()
                assertEquals("Preview must not pop", 0, backs.get())
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                val border = screenshot.getPixel(2, screenshot.height / 2)
                assertTrue("Preview exposes previous page", AndroidColor.red(border) > AndroidColor.blue(border))
                val center = screenshot.getPixel(screenshot.width / 2, screenshot.height / 2)
                assertTrue("Current page remains visible", AndroidColor.blue(center) > AndroidColor.red(center))
                screenshot.recycle()
                scenario.onActivity { it.onBackPressedDispatcher.dispatchOnBackCancelled() }
                android.os.SystemClock.sleep(400)
                device.click(device.displayWidth / 2, device.displayHeight / 2)
                android.os.SystemClock.sleep(100)
                assertEquals("Cancelled snapshot must release touches", 1, taps.get())
                assertEquals(0, backs.get())
                if (edge == BackEventCompat.EDGE_LEFT) start()
                // Also exercise the non-progress/three-button fallback after cancellation.
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                val returned = device.wait(Until.hasObject(By.text("Returned")), 5_000)
                assertTrue("Return visible: reader=$reader edge=$edge commits=${backs.get()} active=${active.value}", returned)
                assertEquals("Return commits exactly once", 1, backs.get())
            }
        }
    }
}
