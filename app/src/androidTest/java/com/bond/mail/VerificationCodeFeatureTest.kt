package com.bond.mail

import android.app.NotificationManager
import android.content.ClipboardManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bond.mail.data.db.MessageEntity
import com.bond.mail.data.mail.MimeParser
import com.bond.mail.data.settings.*
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.screens.DetailScreen
import com.bond.mail.ui.theme.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

internal fun verificationFixture(id: String = "verification-qa-restart") = MessageEntity(
    id = id, accountId = "verification-qa-local", folderType = "INBOX", remoteFolder = "INBOX", remoteUid = if (id == "verification-qa-restart") 991_235 else 991_234,
    internetMessageId = "<$id@example.invalid>",
    senderName = "Verification QA", senderAddress = "security@example.invalid", recipients = "reader@example.invalid",
    subject = "登入確認", preview = "驗證碼為：003821", bodyText = "驗證碼為：003821\n有效期5分鐘。",
    bodyHtml = "<p>登入驗證</p><p>驗證碼為：</p><h1>003821</h1><p>有效期5分鐘。</p>",
    bodyParserVersion = MimeParser.CURRENT_VERSION, receivedAt = 1_790_000_000_000,
    unread = true, starred = false, hasAttachments = false,
)

@RunWith(AndroidJUnit4::class)
class VerificationCodeFeatureTest {
    @Test fun notificationActionCopiesWhileAppIsInBackground() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as MailApplication).container
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val fixture = verificationFixture("notification-qa-${System.currentTimeMillis()}").copy(receivedAt = System.currentTimeMillis())
        val other = fixture.copy(id = fixture.id + "-other", senderName = "Other QA", subject = "Other notification", bodyText = "No verification code here", bodyHtml = null)
        runBlocking { container.database.messageDao().upsert(fixture) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitInstalled(scenario)
                device.pressHome()
                android.os.SystemClock.sleep(800)
                container.notifications.show(other, reminder = true)
                container.notifications.show(fixture.copy(bodyText = "", bodyHtml = null, bodyLoaded = false), reminder = true)
                assertTrue(container.notifications.hasMessageNotification(fixture))
                val notifications = context.getSystemService(NotificationManager::class.java)
                assertFalse(notifications.activeNotifications.first { it.id == fixture.id.hashCode() }
                    .notification.actions.orEmpty().any { it.extras.getBoolean("bond_copy_code") })
                container.notifications.refreshVerificationAction(fixture)
                android.os.SystemClock.sleep(600)
                assertTrue(notifications.activeNotifications.first { it.id == fixture.id.hashCode() }
                    .notification.actions.orEmpty().any { it.extras.getBoolean("bond_copy_code") })
                device.openNotification()
                val label = context.getString(R.string.copy_verification_code)
                if (!device.wait(Until.hasObject(By.text(label)), 1_000)) {
                    var parent = device.wait(Until.findObject(By.text("Verification QA")), 5_000)?.parent
                    repeat(12) {
                        val expand = parent?.findObject(By.res("android", "expand_button"))
                            ?: parent?.findObject(By.res("com.android.systemui", "expand_button"))
                        if (expand != null) { expand.click(); parent = null }
                        else parent = parent?.parent
                    }
                }
                if (!device.wait(Until.hasObject(By.text(label)), 2_000)) {
                    // Some launchers hide the expansion chevron but allow dragging the card open.
                    val title = device.findObject(By.text("Verification QA"))
                    title?.visibleBounds?.let { bounds ->
                        device.swipe(bounds.centerX(), bounds.centerY(), bounds.centerX(), bounds.centerY() + 220, 30)
                    }
                }
                val actionVisible = device.wait(Until.hasObject(By.text(label)), 8_000)
                if (!actionVisible) {
                    device.takeScreenshot(File(context.getExternalFilesDir(null), "verification-notification-failure.png"))
                    device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "verification-notification-failure.xml"))
                }
                assertTrue(actionVisible)
                device.findObject(By.text(label)).click()
                val deadline = android.os.SystemClock.uptimeMillis() + 10_000
                while (runBlocking { container.repository.messageNow(fixture.id)!!.unread } && android.os.SystemClock.uptimeMillis() < deadline) {
                    android.os.SystemClock.sleep(100)
                }
                assertFalse(runBlocking { container.repository.messageNow(fixture.id)!!.unread })
                android.os.SystemClock.sleep(600) // NotificationManager publishes the update asynchronously.
                val active = context.getSystemService(NotificationManager::class.java).activeNotifications
                    .firstOrNull { it.id == fixture.id.hashCode() }
                assertNull(active)
                assertTrue(container.notifications.hasMessageNotification(other))
            }
        } finally {
            device.pressBack()
            context.getSystemService(NotificationManager::class.java).cancel(fixture.id.hashCode())
            context.getSystemService(NotificationManager::class.java).cancel(other.id.hashCode())
            runBlocking { container.database.messageDao().deleteById(fixture.id) }
        }
    }

    @Test fun themedDetailAllowsRepeatedCopy() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val container = (context.applicationContext as MailApplication).container
        val device = UiDevice.getInstance(instrumentation)
        Configurator.getInstance().waitForIdleTimeout = 1_000
        val fixture = verificationFixture("verification-qa-${System.currentTimeMillis()}").copy(receivedAt = System.currentTimeMillis())
        val settings = mutableStateOf(AppSettings(uiStyle = UiStyle.LIQUID_GLASS, themeMode = ThemeMode.LIGHT))
        val showDock = mutableStateOf(false)
        runBlocking { container.database.messageDao().upsert(fixture) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitInstalled(scenario)
                scenario.onActivity { activity -> activity.setContent {
                    BondMailTheme(settings.value) { JsonStringsProvider("zh") {
                        if (showDock.value) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            GlassMainDock(0, {}, {}, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
                        } else DetailScreen(container, fixture.id, fixture, false, settings.value,
                            onMessageSnapshot = {}, onBack = {}, onDelete = {}, onMoveSenderToSpam = {},
                            onRestoreSenderFromSpam = {}, onReply = { _, _, _ -> }, onForward = { _, _ -> })
                    } }
                } }
                for (style in UiStyle.entries) for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                    scenario.onActivity { settings.value = AppSettings(uiStyle = style, themeMode = mode) }
                    val visible = device.wait(Until.hasObject(By.desc("复制验证码")), 10_000)
                    if (!visible) {
                        device.takeScreenshot(File(context.getExternalFilesDir(null), "verification-failure.png"))
                        device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "verification-failure.xml"))
                    }
                    assertTrue("copy action missing for $style/$mode", visible)
                    android.os.SystemClock.sleep(700)
                    val button = device.findObject(By.desc("复制验证码"))
                    assertFalse("copy action should be icon-only", device.hasObject(By.text("复制验证码")))
                    val translate = device.findObject(By.desc("翻译邮件"))
                    assertNotNull(translate)
                    assertTrue(button.visibleBounds.bottom < translate.visibleBounds.top)
                    assertEquals(translate.visibleBounds.centerX(), button.visibleBounds.centerX())
                    device.takeScreenshot(File(context.getExternalFilesDir(null), "verification-${style}-${mode}.png"))
                }
                container.notifications.show(fixture, reminder = true)
                val manager = context.getSystemService(NotificationManager::class.java)
                assertTrue(manager.activeNotifications.first { it.id == fixture.id.hashCode() }.notification.actions.any {
                    it.extras.getBoolean("bond_copy_code")
                })
                device.findObject(By.desc("复制验证码")).click()
                android.os.SystemClock.sleep(600)
                assertTrue(device.hasObject(By.desc("复制验证码")))
                scenario.onActivity { activity ->
                    val clipboard = activity.getSystemService(ClipboardManager::class.java)
                    assertEquals("003821", clipboard.primaryClip!!.getItemAt(0).text.toString())
                    assertTrue(clipboard.primaryClip!!.description.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
                }
                assertFalse(runBlocking { container.repository.messageNow(fixture.id)!!.unread })
                assertTrue(manager.activeNotifications.first { it.id == fixture.id.hashCode() }.notification.actions.orEmpty().any {
                    it.extras.getBoolean("bond_copy_code")
                })
                manager.cancel(fixture.id.hashCode())
                container.notifications.refreshVerificationAction(fixture)
                assertFalse(container.notifications.hasMessageNotification(fixture))
                // Seed a known identity for the separately launched process-restart test.
                scenario.onActivity { it.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("QA", "unchanged")) }
                device.findObject(By.desc("复制验证码")).click()
                android.os.SystemClock.sleep(600)
                scenario.onActivity { assertEquals("003821", it.getSystemService(ClipboardManager::class.java)
                    .primaryClip!!.getItemAt(0).text.toString()) }
                assertTrue(device.hasObject(By.desc("复制验证码")))
                runBlocking {
                    container.database.messageDao().upsert(verificationFixture())
                    assertTrue(container.copyVerificationCode(verificationFixture(), fromNotification = true))
                }
                scenario.onActivity { settings.value = AppSettings(uiStyle = UiStyle.LIQUID_GLASS); showDock.value = true }
                assertTrue(device.wait(Until.hasObject(By.desc("邮件")), 5_000))
                assertFalse(device.hasObject(By.text("邮件")))
                assertFalse(device.hasObject(By.text("联系人")))
                assertFalse(device.hasObject(By.text("设置")))
                device.takeScreenshot(File(context.getExternalFilesDir(null), "verification-glass-dock.png"))
            }
        } finally {
            context.getSystemService(NotificationManager::class.java).cancel(fixture.id.hashCode())
            runBlocking { container.database.messageDao().deleteById(fixture.id) }
        }
    }

    private fun waitInstalled(scenario: ActivityScenario<MainActivity>) {
        var installed = false
        val deadline = android.os.SystemClock.uptimeMillis() + 20_000
        while (!installed && android.os.SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { installed = MainActivity::class.java.getDeclaredField("contentInstalled")
                .apply { isAccessible = true }.getBoolean(it) }
            if (!installed) android.os.SystemClock.sleep(100)
        }
        assertTrue(installed)
    }
}

/** Run with -e verify_otp_restart true in a fresh process after VerificationCodeFeatureTest. */
@RunWith(AndroidJUnit4::class)
class VerificationCodePersistenceRestartTest {
    @Test fun readAcknowledgementSurvivesProcessRestart() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("verify_otp_restart") == "true")
        val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MailApplication).container
        runBlocking {
            try {
                val message = container.repository.messageNow(verificationFixture().id)
                assertNotNull(message)
                assertFalse(message!!.unread)
                assertFalse(container.repository.shouldNotify(message.copy(unread = true)))
            } finally {
                container.database.messageDao().deleteForAccount("verification-qa-local")
                container.database.notificationStateDao().deleteForAccount("verification-qa-local")
            }
        }
    }
}
