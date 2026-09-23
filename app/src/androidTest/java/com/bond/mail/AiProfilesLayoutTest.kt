package com.bond.mail

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bond.mail.data.ai.*
import com.bond.mail.data.security.CredentialStore
import com.bond.mail.ui.components.AiSettingsDialog
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.screens.SponsorshipScreen
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AiProfilesLayoutTest {
    @Test fun savedProfilesSwitchAndModelPickerEditsIndependently() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "ai-ui-test-" + java.util.UUID.randomUUID()
        val isolated = object : android.content.ContextWrapper(context) {
            override fun getSharedPreferences(unused: String?, mode: Int) = context.getSharedPreferences(name, mode)
        }
        val store = CredentialStore(isolated)
        val one = AiProfile("one", "DeepSeek work", "deepseek", "", AiConfig(AiProvider.COMPATIBLE,
            "https://api.deepseek.com", "deepseek-flash", "fake-key-one"), listOf("deepseek-flash", "deepseek-v4-pro"))
        val two = AiProfile("two", "MiMo personal", "mimo", "", AiConfig(AiProvider.COMPATIBLE,
            "https://api.xiaomimimo.com/v1", "mimo-v2.6-flash", "fake-key-two", AiAuth.API_KEY))
        store.saveAiProfiles(AiProfiles(listOf(one, two), "two"))
        val device = UiDevice.getInstance(instrumentation)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitForContent(scenario)
                scenario.onActivity { activity -> activity.setContent {
                    MaterialTheme(colorScheme = darkColorScheme()) { JsonStringsProvider("zh") {
                        AiSettingsDialog(credentialStore = store, onDismiss = {})
                    } }
                } }
                assertTrue(device.wait(Until.hasObject(By.text("DeepSeek work")), 7000))
                device.findObjects(By.text("使用")).first { it.isEnabled }.click()
                instrumentation.waitForIdleSync()
                assertEquals("one", store.aiProfiles().activeId)
                device.findObjects(By.text("编辑")).first().click()
                assertTrue(device.wait(Until.hasObject(By.text("服务商预设")), 5000))
                repeat(3) { if (!device.hasObject(By.text("选择模型"))) device.swipe(530, 1300, 530, 600, 25) }
                val choose = device.findObject(By.text("选择模型"))
                assertNotNull(choose)
                choose.click()
                assertTrue(device.wait(Until.hasObject(By.text("deepseek-v4-pro")), 5000))
                device.findObject(By.text("deepseek-v4-pro")).click()
                device.findObject(By.text("保存并使用")).click()
                instrumentation.waitForIdleSync()
                assertEquals("deepseek-v4-pro", store.aiProfiles().active!!.config.model)
                assertEquals("fake-key-two", store.aiProfiles().entries.first { it.id == "two" }.config.key)
                device.takeScreenshot(File(context.getExternalFilesDir(null), "ai-profiles.png"))
            }
        } finally { context.getSharedPreferences(name, 0).edit().clear().commit() }
    }

    @Test fun sponsorshipActionsShareARowAndDarkQrCanToggle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForContent(scenario)
            scenario.onActivity { activity -> activity.setContent {
                MaterialTheme(colorScheme = darkColorScheme()) { JsonStringsProvider("zh") { SponsorshipScreen({}) } }
            } }
            assertTrue(device.wait(Until.hasObject(By.text("二维码")), 7000))
            val copy = device.findObjects(By.text("复制地址")).first().visibleBounds
            val qr = device.findObjects(By.text("二维码")).first()
            assertEquals(copy.centerY(), qr.visibleBounds.centerY())
            assertTrue(copy.right < qr.visibleBounds.left)
            qr.click()
            assertTrue(device.wait(Until.hasObject(By.text("无法识别？切换黑白配色")), 5000))
            device.waitForIdle()
            device.takeScreenshot(File(context.getExternalFilesDir(null), "sponsor-qr-dark.png"))
            device.findObject(By.text("无法识别？切换黑白配色")).click()
            assertTrue(device.wait(Until.hasObject(By.text("跟随应用主题")), 5000))
            device.takeScreenshot(File(context.getExternalFilesDir(null), "sponsor-qr-standard.png"))
        }
    }

    private fun waitForContent(scenario: ActivityScenario<MainActivity>) {
        var ready = false
        val deadline = android.os.SystemClock.uptimeMillis() + 10_000
        while (!ready && android.os.SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { ready = MainActivity::class.java.getDeclaredField("contentInstalled").apply { isAccessible = true }.getBoolean(it) }
            if (!ready) Thread.sleep(100)
        }
        assertTrue(ready)
    }
}
