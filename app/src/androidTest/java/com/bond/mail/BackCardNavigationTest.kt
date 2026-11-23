package com.bond.mail

import android.graphics.Color as AndroidColor
import androidx.activity.BackEventCompat
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
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
                var dockBounds = Rect.Zero
                val webReady = java.util.concurrent.CountDownLatch(if (reader) 1 else 0)
                val dockProgress = java.util.concurrent.CopyOnWriteArrayList<Float>()
                scenario.onActivity { activity -> activity.setContent {
                    BondMailTheme(AppSettings()) {
                        Box(Modifier.fillMaxSize().background(Color.Red)) {
                            if (active.value) {
                                val committed = { backs.incrementAndGet(); active.value = false }
                                val page: @Composable (() -> Unit) -> Unit = { back ->
                                    Box(Modifier.fillMaxSize().background(Color.Blue)) {
                                        if (reader) AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                                            WebView(context).apply {
                                                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                                setBackgroundColor(AndroidColor.GREEN)
                                                webViewClient = object : WebViewClient() {
                                                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                                                        webReady.countDown()
                                                    }
                                                }
                                                loadDataWithBaseURL(null, "<html><body style='margin:0;background:#0044ff;height:3000px'>Reader fixture</body></html>", "text/html", "UTF-8", null)
                                            }
                                        }, onRelease = { it.destroy() })
                                        Box(Modifier.fillMaxSize().clickable { taps.incrementAndGet() })
                                        Text("Back card", Modifier.statusBarsPadding().clickable(onClick = back))
                                    }
                                }
                                if (reader) TelegramMailTransition(null, true, committed,
                                    onVisualProgress = { dockProgress.add(it) }) { back, ready ->
                                    LaunchedEffect(Unit) {
                                        // The dock must stay in main mode while the reader is prepared.
                                        kotlinx.coroutines.delay(180)
                                        assertTrue(dockProgress.isNotEmpty())
                                        assertTrue(dockProgress.all { it == 0f })
                                        ready()
                                    }
                                    page(back)
                                } else BondBackScreen(null, true, committed) { page(it) }
                            } else Text("Returned", Modifier.statusBarsPadding())
                            // Represents the persistent sibling dock. It must never enter the frozen
                            // reader bitmap, even though window screenshots include these pixels.
                            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
                                .fillMaxWidth().height(24.dp).onGloballyPositioned { dockBounds = it.boundsInWindow() }
                                .background(Color.Magenta))
                        }
                    }
                } }
                assertTrue(device.wait(Until.hasObject(By.text("Back card")), 5_000))
                assertTrue("WebView fixture committed", webReady.await(10, java.util.concurrent.TimeUnit.SECONDS))
                android.os.SystemClock.sleep(500)
                if (reader) {
                    assertTrue("Entry needs visible intermediate dock frames", dockProgress.distinct().count { it > .01f && it < .99f } >= 3)
                    assertEquals(1f, dockProgress.last(), .001f)
                    assertTrue(dockProgress.zipWithNext().all { (a, b) -> b >= a })
                    dockProgress.clear()
                }
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
                if (reader) assertTrue("Frozen reader must contain Chromium pixels, not just its Compose background",
                    AndroidColor.green(center) in 50..90)
                for (y in 0 until screenshot.height step 4) for (x in 0 until screenshot.width step 4) {
                    if (x >= dockBounds.left - 2 && x <= dockBounds.right + 2 &&
                        y >= dockBounds.top - 2 && y <= dockBounds.bottom + 2) continue
                    val pixel = screenshot.getPixel(x, y)
                    assertFalse("Snapshot duplicated the persistent dock at $x,$y (reader=$reader edge=$edge)",
                        AndroidColor.red(pixel) > 220 && AndroidColor.blue(pixel) > 220 && AndroidColor.green(pixel) < 40)
                }
                screenshot.recycle()
                scenario.onActivity { it.onBackPressedDispatcher.dispatchOnBackCancelled() }
                android.os.SystemClock.sleep(400)
                device.click(device.displayWidth / 2, device.displayHeight / 2)
                android.os.SystemClock.sleep(100)
                assertEquals("Cancelled snapshot must release touches", 1, taps.get())
                assertEquals(0, backs.get())
                if (reader) assertTrue("Cancelled preview keeps reader actions", dockProgress.all { it == 1f })
                if (edge == BackEventCompat.EDGE_LEFT) start()
                // Also exercise the non-progress/three-button fallback after cancellation.
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                val returned = device.wait(Until.hasObject(By.text("Returned")), 5_000)
                assertTrue("Return visible: reader=$reader edge=$edge commits=${backs.get()} active=${active.value}", returned)
                assertEquals("Return commits exactly once", 1, backs.get())
                if (reader) {
                    assertTrue("Return needs intermediate dock frames (edge=$edge): ${dockProgress.distinct()}",
                        dockProgress.distinct().count { it > .01f && it < .99f } >= 3)
                    assertTrue(dockProgress.zipWithNext().all { (a, b) -> b <= a })
                    assertTrue("Dock finishes with the page", dockProgress.last() < .02f)
                }
            }
        }
    }
}
