package com.bond.mail

import android.graphics.Point
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.ui.components.MailWebHeader
import com.bond.mail.ui.components.resetMailZoom
import com.bond.mail.ui.components.zoomMailBodyBy
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.screens.MailHtmlView
import com.bond.mail.ui.screens.rememberMailHeaderLayout
import com.bond.mail.ui.theme.BondMailTheme
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MailZoomGestureTest {
    @Suppress("DEPRECATION")
    @Test fun onlyBodyZoomsBelowDefaultWhileHeaderAndChromeRemainStable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val active = AtomicReference<WebView?>()
        val committed = AtomicInteger()
        val pulls = AtomicInteger()
        val hides = AtomicInteger()
        val desktop = mutableStateOf(false)
        fun settle() { instrumentation.waitForIdleSync(); Thread.sleep(450) }
        fun scale(): Float { var value = 0f; instrumentation.runOnMainSync { value = active.get()!!.scale }; return value }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var installed = false
            val deadline = System.currentTimeMillis() + 20_000
            while (!installed && System.currentTimeMillis() < deadline) {
                scenario.onActivity { installed = MainActivity::class.java.getDeclaredField("contentInstalled")
                    .apply { isAccessible = true }.getBoolean(it) }
                if (!installed) Thread.sleep(50)
            }
            assertTrue(installed)
            scenario.onActivity { activity -> activity.setContent {
                val fixed = desktop.value
                val header = MailWebHeader(subject = "Zoom fixture", senderName = "Test",
                    senderAddress = if (fixed) "statement@e.cathaypacific.com" else "test@example.invalid",
                    recipient = "reader@example.invalid", dateLabel = "Today", avatarText = "T")
                val html = if (fixed) "<table width='640'><tr><td><div style='height:2200px'>Wide statement</div></td></tr></table>"
                    else "<div style='height:2200px'><div style='display:flex'><div style='flex:0 0 290px'>Left</div><div id='far-edge' style='flex:0 0 290px'>Right edge</div></div></div>"
                BondMailTheme(AppSettings()) { JsonStringsProvider("en") {
                    MailHtmlView(cacheKey = "zoom-test-$fixed", html = html, header = header,
                        headerLayout = rememberMailHeaderLayout(header.subject), loadImages = false,
                        topContentInset = 64.dp, onTopPullDelta = { pulls.incrementAndGet(); it }, onTopPullRelease = {},
                        onWebViewChanged = { active.set(it) }, onRemoteImagesChanged = {}, onExternalLink = {},
                        onAttachmentClick = {}, onChromeVisibilityChanged = { if (!it) hides.incrementAndGet() }, onContentScrollChanged = {},
                        onRenderStarted = {}, onDocumentReady = { committed.incrementAndGet() },
                        onRenderFailure = { throw AssertionError("Reader failed") },
                        onRendererGone = { throw AssertionError("Renderer lost") }, modifier = Modifier.fillMaxSize())
                } }
            } }
            for (fixed in listOf(false, true)) {
                val before = committed.get()
                instrumentation.runOnMainSync { desktop.value = fixed }
                val readyBy = System.currentTimeMillis() + 15_000
                while ((active.get() == null || (fixed && committed.get() <= before) || committed.get() == 0) &&
                    System.currentTimeMillis() < readyBy) Thread.sleep(50)
                assertNotNull(active.get())
                settle()
                instrumentation.runOnMainSync {
                    active.get()!!.resetMailZoom()
                    active.get()!!.scrollTo(0, 0)
                    assertFalse("Native whole-page zoom must be disabled", active.get()!!.settings.builtInZoomControls)
                }
                settle()
                val base = scale()
                fun geometry(): org.json.JSONArray {
                    val done = java.util.concurrent.CountDownLatch(1)
                    val result = AtomicReference<String>()
                    instrumentation.runOnMainSync { active.get()!!.evaluateJavascript("""
                        (function(){const b=document.getElementById('bondmail-message-body').getBoundingClientRect();
                        const h=document.getElementById('bondmail-message-header').getBoundingClientRect();
                        const s=document.getElementById('bondmail-message-subject').getBoundingClientRect();
                        const far=document.getElementById('far-edge');
                        const clip=document.getElementById('bondmail-body-clip').getBoundingClientRect();
                        return [b.width,b.height,h.width,h.height,s.height,b.top,
                            far?far.getBoundingClientRect().right:0,clip.right];})();
                    """.trimIndent()) { result.set(it); done.countDown() } }
                    assertTrue(done.await(3, java.util.concurrent.TimeUnit.SECONDS))
                    return org.json.JSONArray(result.get())
                }
                val original = geometry()
                val view = device.findObject(UiSelector().className(WebView::class.java.name))
                val bounds = view.bounds
                val y = bounds.top + bounds.height() * 2 / 3
                pulls.set(0)
                hides.set(0)
                assertTrue(view.performTwoPointerGesture(
                    Point(bounds.left + bounds.width() * 2 / 5, y), Point(bounds.left + bounds.width() * 3 / 5, y),
                    Point(bounds.left + bounds.width() / 5, y), Point(bounds.left + bounds.width() * 4 / 5, y), 35))
                settle()
                val enlarged = geometry().getDouble(0)
                assertTrue("Pinch must enlarge only body ($fixed)", enlarged > original.getDouble(0) * 1.15)
                assertEquals("Whole page stays at 1x", base, scale(), .01f)
                for (i in 2..4) assertEquals("Header dimensions stay fixed", original.getDouble(i), geometry().getDouble(i), 1.0)
                assertEquals("Pinch must not trigger top pull", 0, pulls.get())
                assertEquals("Zoom must not hide the toolbar", 0, hides.get())
                instrumentation.runOnMainSync { active.get()!!.zoomMailBodyBy(.8f) }
                settle()
                assertTrue("Zoom-out action must shrink", geometry().getDouble(0) < enlarged * .95)
                assertEquals("Zoom action must keep reset available", 0, hides.get())
                instrumentation.runOnMainSync { active.get()!!.resetMailZoom() }
                settle()
                assertEquals("Reset restores fitted body", original.getDouble(0), geometry().getDouble(0), 1.0)
                instrumentation.runOnMainSync { active.get()!!.scrollTo(0, 0); active.get()!!.zoomMailBodyBy(.5f) }
                settle()
                val reduced = geometry()
                if (!fixed) assertTrue("Shrinking reveals the previously clipped right edge", reduced.getDouble(6) <= reduced.getDouble(7) + 1)
                assertEquals("Must shrink below initial fit", original.getDouble(0) * .5, reduced.getDouble(0), 1.0)
                for (i in 2..4) assertEquals("Shrinking must not resize header", original.getDouble(i), reduced.getDouble(i), 1.0)
                assertEquals("Header/body gap stays fixed", original.getDouble(5), reduced.getDouble(5), 1.0)
                assertEquals("Menu zoom cannot hide chrome", 0, hides.get())
                // A pinch on the subject/sender region must not scale the body either.
                assertTrue(view.performTwoPointerGesture(
                    Point(bounds.left + bounds.width() * 2 / 5, bounds.top + 100), Point(bounds.left + bounds.width() * 3 / 5, bounds.top + 100),
                    Point(bounds.left + bounds.width() / 5, bounds.top + 100), Point(bounds.left + bounds.width() * 4 / 5, bounds.top + 100), 20))
                settle()
                assertEquals(reduced.getDouble(0), geometry().getDouble(0), 1.0)
                instrumentation.runOnMainSync { active.get()!!.resetMailZoom() }
                settle()
            }
        }
    }
}
