package com.bond.mail

import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
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
        val header = MailWebHeader(subject = "Lifecycle fixture", senderName = "Test sender",
            senderAddress = "notice@example.invalid", recipient = "reader@example.invalid",
            dateLabel = "Today", avatarText = "T")
        val html = "<html><body><p id='reader-fixture'>Reader remains available</p>" +
            (1..120).joinToString("") { "<p>Long local message paragraph $it for scrolling.</p>" } + "</body></html>"
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
                        MailHtmlView(cacheKey = "lifecycle-${header.subject}", html = html, header = header,
                            headerLayout = rememberMailHeaderLayout(header.subject), loadImages = false,
                            topContentInset = 64.dp, onTopPullDelta = { 0f }, onTopPullRelease = {},
                            onWebViewChanged = { if (active.getAndSet(it) !== it) committed.set(null) },
                            onRemoteImagesChanged = {},
                            onExternalLink = {}, onAttachmentClick = {}, onChromeVisibilityChanged = {},
                            onContentScrollChanged = {}, onRenderStarted = { committed.set(null) },
                            onDocumentReady = { committed.set(active.get()) },
                            onRenderFailure = { failures.incrementAndGet() },
                            onRendererGone = { failures.incrementAndGet() }, modifier = Modifier.fillMaxSize())
                    } }
                }
            } }
            fun awaitBody() {
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
                            view.evaluateJavascript("document.getElementById('reader-fixture')!==null") {
                                ready = it == "true"; latch.countDown()
                            }
                        }
                    }
                    latch.await(2, TimeUnit.SECONDS)
                    if (!ready) Thread.sleep(50)
                }
                assertTrue("Reader must show its document, not a blank pooled WebView", ready)
                assertEquals("Cancellation must not report a render failure", 0, failures.get())
            }
            awaitBody()
            repeat(3) { index ->
                instrumentation.runOnMainSync { active.get()!!.scrollTo(0, 350) }
                // A configuration change starts preparing another document. Immediately returning
                // to the cached configuration exercises AndroidView removal/recreation in one holder.
                scenario.onActivity { scale.value = 1.2f + index * 0.1f }
                Thread.sleep(32)
                scenario.onActivity { scale.value = 1f }
                awaitBody()
                // Let the retained page's first attachment frame finish before the next gesture.
                Thread.sleep(100)
            }
        }
    }
}
