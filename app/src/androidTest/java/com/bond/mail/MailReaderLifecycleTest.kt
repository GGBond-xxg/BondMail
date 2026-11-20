package com.bond.mail

import android.webkit.WebView
import android.graphics.Color
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.ui.components.MailWebHeader
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.screens.MailHtmlView
import com.bond.mail.ui.screens.rememberMailHeaderLayout
import com.bond.mail.ui.screens.rememberSettledMailTopBarHeight
import com.bond.mail.ui.theme.BondMailTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MailReaderLifecycleTest {
    @Test fun scrolledReaderSurvivesWindowChanges() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val active = AtomicReference<WebView?>()
        val committed = AtomicReference<WebView?>()
        val failures = AtomicInteger()
        val scale = mutableStateOf(1f)
        val measuredTopBarHeight = mutableIntStateOf(180)
        val readyEvents = AtomicInteger()
        val header = MailWebHeader(subject = "Lifecycle fixture", senderName = "Test sender",
            senderAddress = "notice@example.invalid", recipient = "reader@example.invalid",
            dateLabel = "Today", avatarText = "T")
        val html = "<html><body><div id='reader-fixture' style='background:#12a345'>" +
            (1..120).joinToString("") { "<p>Reader remains available: paragraph $it for scrolling.</p>" } +
            "</div></body></html>"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val deadline = System.currentTimeMillis() + 10000
            var installed = false
            while (!installed && System.currentTimeMillis() < deadline) {
                scenario.onActivity { installed = MainActivity::class.java.getDeclaredField("contentInstalled")
                    .apply { isAccessible = true }.getBoolean(it) }
                if (!installed) Thread.sleep(50)
            }
            assertTrue(installed)
            scenario.onActivity { activity -> activity.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, scale.value)) {
                    BondMailTheme(AppSettings()) { JsonStringsProvider("en") {
                        val topBarHeight = rememberSettledMailTopBarHeight(measuredTopBarHeight.intValue)
                        MailHtmlView(cacheKey = "lifecycle-${header.subject}", html = html, header = header,
                            headerLayout = rememberMailHeaderLayout(header.subject), loadImages = false,
                            topContentInset = (topBarHeight / density).dp, onTopPullDelta = { 0f }, onTopPullRelease = {},
                            onWebViewChanged = { if (active.getAndSet(it) !== it) committed.set(null) },
                            onRemoteImagesChanged = {},
                            onExternalLink = {}, onAttachmentClick = {}, onChromeVisibilityChanged = {},
                            onContentScrollChanged = {}, onRenderStarted = { committed.set(null) },
                            onDocumentReady = { committed.set(active.get()); readyEvents.incrementAndGet() },
                            onRenderFailure = { failures.incrementAndGet() },
                            onRendererGone = { failures.incrementAndGet() }, modifier = Modifier.fillMaxSize())
                    } }
                }
            } }
            fun awaitBody(expectedFontScale: Float = 1f) {
                val end = System.currentTimeMillis() + 15000
                var ready = false
                while (!ready && System.currentTimeMillis() < end) {
                    val latch = CountDownLatch(1)
                    instrumentation.runOnMainSync {
                        val view = active.get()
                        if (view == null || !view.isAttachedToWindow || committed.get() !== view) latch.countDown()
                        else {
                            // Only this synthetic, script-free fixture enables JS for DOM assertions.
                            view.settings.javaScriptEnabled = true
                            view.evaluateJavascript("document.getElementById('reader-fixture')!==null && " +
                                "Math.abs(parseFloat(getComputedStyle(document.querySelector('.bondmail-name')).fontSize)-${16f * expectedFontScale})<0.05") {
                                ready = it == "true"; latch.countDown()
                            }
                        }
                    }
                    latch.await(2, TimeUnit.SECONDS)
                    if (!ready) Thread.sleep(50)
                }
                assertTrue("Reader must show its document, not a blank pooled WebView", ready)
                assertEquals("Cancellation must not report a render failure", 0, failures.get())
                // DOM readiness alone missed the vivo regression: a loaded WebView was covered by
                // a stale Compose skeleton. Verify pixels of the whole screen, including overlays.
                val location = IntArray(2)
                var sampleX = 0
                var sampleY = 0
                instrumentation.runOnMainSync {
                    active.get()!!.let { view ->
                        view.getLocationOnScreen(location)
                        sampleX = location[0] + view.width / 2
                        sampleY = location[1] + view.height * 2 / 3
                    }
                }
                val paintDeadline = System.currentTimeMillis() + 5000
                var exposed = false
                var observedPixel = 0
                while (!exposed && System.currentTimeMillis() < paintDeadline) {
                    val bitmap = instrumentation.uiAutomation.takeScreenshot()
                    val pixel = bitmap.getPixel(sampleX, sampleY)
                    observedPixel = pixel
                    exposed = kotlin.math.abs(Color.red(pixel) - 18) < 8 &&
                        kotlin.math.abs(Color.green(pixel) - 163) < 8 &&
                        kotlin.math.abs(Color.blue(pixel) - 69) < 8
                    bitmap.recycle()
                    if (!exposed) Thread.sleep(100)
                }
                if (!exposed) {
                    instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                        java.io.File(instrumentation.targetContext.filesDir, "reader-pixel-failure.png")
                            .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                    }
                }
                assertTrue("Committed mail must be visible above the loading placeholder; pixel=${Integer.toHexString(observedPixel)} at $sampleX,$sampleY", exposed)
            }
            awaitBody()
            repeat(3) {
                instrumentation.runOnMainSync { active.get()!!.scrollTo(0, 350) }
                Thread.sleep(100)
                val previousView = active.get()
                val previousReadyEvents = readyEvents.get()
                // Recorded vivo resume sequence, including a zero-size detached-window frame.
                scenario.onActivity { measuredTopBarHeight.intValue = 0 }
                Thread.sleep(32)
                scenario.onActivity { measuredTopBarHeight.intValue = 164 }
                Thread.sleep(100)
                scenario.onActivity { measuredTopBarHeight.intValue = 180 }
                Thread.sleep(500)
                awaitBody()
                instrumentation.runOnMainSync {
                    assertTrue("Transient insets must retain the current WebView", previousView === active.get())
                    assertEquals("Returning to the app must retain the reading position", 350, active.get()!!.scrollY)
                }
                assertEquals("Transient insets must not reload the document", previousReadyEvents, readyEvents.get())
            }
            repeat(3) {
                instrumentation.runOnMainSync { active.get()!!.scrollTo(0, 350) }
                // First warm both configurations, then repeat cached switches without recreating
                // AndroidView. Its original client must update the current loading/visible state.
                scenario.onActivity { scale.value = 1.2f }
                awaitBody(1.2f)
                scenario.onActivity { scale.value = 1f }
                awaitBody()
                // Let the retained page's first attachment frame finish before the next gesture.
                Thread.sleep(100)
            }
        }
    }
}
