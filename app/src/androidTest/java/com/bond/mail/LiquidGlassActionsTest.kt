package com.bond.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bond.mail.data.db.MessageEntity
import com.bond.mail.data.mail.MimeParser
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.ComposeViewModel
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.screens.ComposeScreen
import com.bond.mail.ui.screens.DetailScreen
import com.bond.mail.ui.theme.BondMailTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Exercise the actual WebView/detail and editor surfaces with a disposable, offline message. */
@RunWith(AndroidJUnit4::class)
class LiquidGlassActionsTest {
    @Test
    fun detailAndComposerRenderGlassWithoutSendingOrDeleting() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val container = (context.applicationContext as MailApplication).container
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        val id = "glass-test-${UUID.randomUUID()}"
        val html = "<html><body>" + (1..30).joinToString("") {
            val color = if (it % 2 == 0) "#5eb8b0" else "#d7a476"
            "<div style='background:$color;padding:24px;height:100px'>Offline glass sample $it</div>"
        } + "</body></html>"
        val fixture = MessageEntity(
            id = id, accountId = id, folderType = "GLASS_TEST", remoteFolder = "GLASS_TEST",
            remoteUid = 0, senderName = "Glass Preview", senderAddress = "preview@example.invalid",
            subject = "Offline glass controls", preview = "Disposable visual fixture",
            bodyText = "Offline visual fixture", bodyHtml = html, bodyParserVersion = MimeParser.CURRENT_VERSION,
            receivedAt = 0, unread = false, starred = false, hasAttachments = false,
        )
        container.database.messageDao().upsert(fixture)
        val settings = mutableStateOf(AppSettings(uiStyle = UiStyle.LIQUID_GLASS, themeMode = ThemeMode.LIGHT, dynamicColor = false))
        val compose = mutableStateOf(false)
        val replies = AtomicInteger()
        val forwards = AtomicInteger()
        val deletes = AtomicInteger()
        fun shot(name: String) {
            device.waitForIdle()
            android.os.SystemClock.sleep(800)
            assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "$name.png")))
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var installed = false
                val deadline = android.os.SystemClock.uptimeMillis() + 20_000
                while (!installed && android.os.SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity { activity ->
                        val field = MainActivity::class.java.getDeclaredField("contentInstalled").apply { isAccessible = true }
                        installed = field.getBoolean(activity)
                    }
                    if (!installed) android.os.SystemClock.sleep(100)
                }
                assertTrue(installed)
                scenario.onActivity { activity ->
                    val vm = ViewModelProvider(activity, object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = ComposeViewModel(container) as T
                    })[ComposeViewModel::class.java]
                    activity.setContent {
                        BondMailTheme(settings.value) {
                            JsonStringsProvider("en") {
                                if (compose.value) {
                                    ComposeScreen(
                                        viewModel = vm, initialAccountId = id, initialTo = "preview@example.invalid",
                                        initialSubject = "Offline preview — do not send", initialBody = "Glass editor preview",
                                        onBack = { compose.value = false }, onQueued = { error("Test must never send") },
                                    )
                                } else {
                                    DetailScreen(
                                        container = container, messageId = id, initialMessage = fixture,
                                        markSeenOnOpen = false, settings = settings.value,
                                        onMessageSnapshot = {}, onBack = {}, onDelete = { deletes.incrementAndGet() },
                                        onMoveSenderToSpam = {}, onRestoreSenderFromSpam = {},
                                        onReply = { _, _, _ -> replies.incrementAndGet() },
                                        onForward = { _, _ -> forwards.incrementAndGet() },
                                    )
                                }
                            }
                        }
                    }
                }
                try {
                    for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                        scenario.onActivity { settings.value = settings.value.copy(themeMode = mode); compose.value = false }
                        assertTrue(device.wait(Until.hasObject(By.desc("Reply")), 20_000))
                        android.os.SystemClock.sleep(1_200) // Chromium's first body frame.
                        scenario.onActivity { activity ->
                            fun findWebView(view: android.view.View): android.webkit.WebView? {
                                if (view is android.webkit.WebView) return view
                                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) {
                                    findWebView(view.getChildAt(i))?.let { return it }
                                }
                                return null
                            }
                            val web = checkNotNull(findWebView(activity.window.decorView))
                            assertEquals(android.view.View.LAYER_TYPE_HARDWARE, web.layerType)
                        }
                        shot("glass-actions-${mode.name.lowercase()}")
                        checkNotNull(device.findObject(By.descStartsWith("Translation language:"))).click()
                        assertTrue(device.wait(Until.hasObject(By.text("English")), 10_000))
                        shot("glass-language-menu-${mode.name.lowercase()}")
                        device.pressBack()
                        checkNotNull(device.findObject(By.descStartsWith("Translation provider key:"))).click()
                        assertTrue(device.wait(Until.hasObject(By.text("Google Cloud")), 10_000))
                        shot("glass-provider-menu-${mode.name.lowercase()}")
                        device.pressBack()
                        checkNotNull(device.findObject(By.desc("Reply"))).click()
                        checkNotNull(device.findObject(By.desc("Forward"))).click()
                        checkNotNull(device.findObject(By.desc("Delete"))).click()
                        assertTrue(device.wait(Until.hasObject(By.text("Delete email?")), 20_000))
                        shot("glass-dialog-${mode.name.lowercase()}")
                        checkNotNull(device.wait(Until.findObject(By.text("Cancel")), 20_000)).click()
                        assertEquals(0, deletes.get())
                        scenario.onActivity { compose.value = true }
                        assertTrue(device.wait(Until.hasObject(By.desc("Send")), 20_000))
                        shot("glass-compose-${mode.name.lowercase()}")
                        // The real Send button is rendered, but no tap is injected into it.
                        assertTrue(device.hasObject(By.desc("Add attachment")))
                    }
                    assertEquals(2, replies.get())
                    assertEquals(2, forwards.get())
                    assertNotNull(container.database.messageDao().byId(id))
                } catch (failure: Throwable) {
                    device.takeScreenshot(File(context.getExternalFilesDir(null), "glass-actions-failure.png"))
                    device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "glass-actions-failure.xml"))
                    throw failure
                }
            }
        } finally {
            container.database.messageDao().deleteById(id)
        }
    }
}
