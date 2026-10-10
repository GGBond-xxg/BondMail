package com.bond.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.components.MailToolsDialog
import com.bond.mail.ui.components.BodyTranslationDialog
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.screens.OpenSourceLicensesScreen
import com.bond.mail.ui.screens.AppLicenseScreen
import com.bond.mail.ui.theme.BondMailTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GlassSecondaryScreensTest {
    @Test fun secondaryControlsKeepGapsAndLicensesOpenOffline() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val page = mutableStateOf(0)
        val dark = mutableStateOf(false)
        val settings = { AppSettings(uiStyle = UiStyle.LIQUID_GLASS,
            themeMode = if (dark.value) ThemeMode.DARK else ThemeMode.LIGHT) }
        // The distributed source attribution and disclaimer must actually be present in the APK.
        fun asset(name: String) = context.assets.open("licenses/$name").bufferedReader().use { it.readText() }
        val apache = asset("AndroidLiquidGlass-Apache-2.0.txt")
        assertTrue(apache.contains("Copyright 2025 Kyant"))
        assertTrue(apache.contains("7. Disclaimer of Warranty"))
        assertTrue(apache.contains("8. Limitation of Liability"))
        assertTrue(asset("BondMail-MIT.txt").contains("THE SOFTWARE IS PROVIDED"))
        assertTrue(asset("THIRD_PARTY_NOTICES.md").contains("896a94a3ade1cc1a940b92365f942a34971fecda"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var installed = false
            val deadline = android.os.SystemClock.uptimeMillis() + 20_000
            while (!installed && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity -> installed = MainActivity::class.java
                    .getDeclaredField("contentInstalled").apply { isAccessible = true }.getBoolean(activity) }
                if (!installed) android.os.SystemClock.sleep(100)
            }
            assertTrue(installed)
            scenario.onActivity { activity -> activity.setContent {
                BondMailTheme(settings()) { JsonStringsProvider("zh") {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (page.value) {
                        0 -> OpenSourceLicensesScreen {}
                        1 -> MailToolsDialog(initialTab = "tools_attachments", onDismiss = {})
                        2 -> BodyTranslationDialog(html = null, plain = "Offline sample", subject = "Preview",
                            translateText = { text, _, _ -> text }, onDismiss = {})
                        else -> AppLicenseScreen {}
                    }
                    }
                } }
            } }
            fun waitText(text: String) = checkNotNull(device.wait(Until.findObject(By.text(text)), 15_000))
            fun actionBounds(text: String): android.graphics.Rect {
                var node = waitText(text)
                while (!node.isClickable && node.parent != null) node = node.parent
                return node.visibleBounds
            }
            fun shot(name: String) {
                device.waitForIdle()
                assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "$name.png")))
            }
            waitText("查看完整许可与免责条款").click()
            assertTrue(device.wait(Until.hasObject(By.textContains("TERMS AND CONDITIONS")), 10_000))
            shot("glass-license-full")
            device.pressBack()
            waitText("查看完整许可与免责条款")
            for (night in listOf(false, true)) {
                instrumentation.runOnMainSync { dark.value = night; page.value = 1 }
                waitText("附件中心 ▾")
                val all = actionBounds("全部")
                val week = actionBounds("7 天")
                val month = actionBounds("30 天")
                assertTrue("Attachment filters must have separate touch bounds", all.right < week.left && week.right < month.left)
                assertTrue("Category and account filters need vertical space", actionBounds("附件中心 ▾").bottom < actionBounds("全部账户").top)
                waitText("7 天").click()
                assertTrue(device.wait(Until.hasObject(By.text("7 天")), 5_000))
                shot("glass-tools-${if (night) "dark" else "light"}")
                instrumentation.runOnMainSync { page.value = 2 }
                waitText("Offline sample")
                assertTrue("Translation footer buttons must not touch", actionBounds("翻译服务").right < actionBounds("关闭").left)
                shot("glass-translation-${if (night) "dark" else "light"}")
            }
            instrumentation.runOnMainSync { page.value = 3 }
            assertTrue(device.wait(Until.hasObject(By.textContains("Copyright (c) 2026 GGBond-xxg")), 10_000))
        }
    }
}

