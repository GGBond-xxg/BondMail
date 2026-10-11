package com.bond.mail

import androidx.activity.BackEventCompat
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.screens.OpenSourceLicensesScreen
import com.bond.mail.ui.theme.BondMailTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LicensePredictiveBackTest {
    @Test fun documentsFollowBackProgressCancelAndReturnToSameListPosition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var installed = false
            val deadline = android.os.SystemClock.uptimeMillis() + 20_000
            while (!installed && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    installed = MainActivity::class.java.getDeclaredField("contentInstalled")
                        .apply { isAccessible = true }.getBoolean(activity)
                }
                if (!installed) android.os.SystemClock.sleep(100)
            }
            assertTrue(installed)
            var parentBacks = 0
            scenario.onActivity { activity -> activity.setContent {
                BondMailTheme(AppSettings(uiStyle = UiStyle.LIQUID_GLASS)) {
                    JsonStringsProvider("zh") { OpenSourceLicensesScreen { parentBacks++ } }
                }
            } }
            fun find(text: String) = checkNotNull(device.wait(Until.findObject(By.text(text)), 8_000))
            for ((index, label) in listOf("查看完整许可与免责条款", "查看第三方版权与改动说明").withIndex()) {
                repeat(8) {
                    if (device.findObject(By.text(label)) == null) {
                        device.swipe(360, 1300, 360, 500, 25)
                    }
                }
                val before = find(label).visibleBounds
                find(label).click()
                val title = if (index == 0) "Liquid Glass" else "开源声明"
                val initial = find(title).visibleBounds
                // Exercise the same AndroidX progress flow used by a real edge gesture.
                scenario.onActivity {
                    it.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 700f, 0f, BackEventCompat.EDGE_LEFT))
                }
                android.os.SystemClock.sleep(100)
                scenario.onActivity {
                    it.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(250f, 700f, .45f, BackEventCompat.EDGE_LEFT))
                }
                android.os.SystemClock.sleep(200)
                assertTrue("Document must move with back progress", find(title).visibleBounds.left > initial.left + 60)
                device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "license-back-progress-$index.png"))
                scenario.onActivity { it.onBackPressedDispatcher.dispatchOnBackCancelled() }
                android.os.SystemClock.sleep(350)
                assertEquals("Cancel restores the document", initial.left, find(title).visibleBounds.left)
                assertEquals(0, parentBacks)
                if (index == 0) {
                    checkNotNull(device.wait(Until.findObject(By.desc("返回")), 5_000)).click()
                } else {
                    scenario.onActivity {
                        it.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 700f, 0f, BackEventCompat.EDGE_LEFT))
                    }
                    android.os.SystemClock.sleep(100)
                    scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                }
                assertEquals("Return preserves the declaration scroll position", before, find(label).visibleBounds)
                assertEquals("Child return must not pop the parent", 0, parentBacks)
            }
        }
    }
}
