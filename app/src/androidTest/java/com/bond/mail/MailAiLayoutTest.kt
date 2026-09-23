package com.bond.mail

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bond.mail.data.ai.*
import com.bond.mail.ui.components.MailAiDialog
import com.bond.mail.ui.i18n.JsonStringsProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class MailAiLayoutTest {
    @Test fun draftRequiresReviewActionAndUsesOriginalMailContext() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val reply = AtomicReference<String?>()
        val request = AtomicReference<List<AiTurn>?>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var installed = false
            val deadline = android.os.SystemClock.uptimeMillis() + 10_000
            while (!installed && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    installed = MainActivity::class.java.getDeclaredField("contentInstalled").apply { isAccessible = true }.getBoolean(activity)
                }
                if (!installed) Thread.sleep(100)
            }
            assertTrue(installed)
            scenario.onActivity { activity -> activity.setContent {
                MaterialTheme { JsonStringsProvider("zh") {
                    MailAiDialog("Offline sample", "<p>Meeting on Friday</p>", "", true,
                        onUseReply = { reply.set(it) }, onDismiss = {},
                        loadConfig = { AiConfig(AiProvider.COMPATIBLE, "https://example.com/v1", "offline-model", "fake-unused-key") },
                        generate = { _, messages -> request.set(messages); "谢谢你的邀请，请确认会议时间。" })
                } }
            } }
            val visible = device.wait(Until.hasObject(By.text("生成回复")), 7000)
            if (!visible) device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "ai-test-blocked.png"))
            assertTrue("AI controls visible; foreground=${device.currentPackageName}", visible)
            assertNull(request.get())
            assertNull(reply.get())
            device.findObject(By.text("生成回复")).click()
            assertTrue(device.wait(Until.hasObject(By.text("带入回复编辑页")), 7000))
            assertTrue(request.get()!![1].text.contains("Meeting on Friday"))
            assertNull(reply.get())
            device.findObject(By.text("带入回复编辑页")).click()
            instrumentation.waitForIdleSync()
            assertEquals("谢谢你的邀请，请确认会议时间。", reply.get())
            device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "ai-mail-assistant.png"))
        }
    }
}
