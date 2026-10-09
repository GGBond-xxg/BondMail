package com.bond.mail

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.data.settings.UiStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LiquidGlassStyleTest {
    @Test
    fun selectorPersistsAndRendersAcrossThemesAndFallback() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = (context.applicationContext as MailApplication).container.settings
        val original = settings.settings.first()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val originalMotion = device.executeShellCommand("settings get global animator_duration_scale").trim()
        fun restart() {
            device.executeShellCommand("am start -W -f 0x10008000 -n com.bond.mail/.MainActivity")
            assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 10_000))
            device.waitForIdle()
        }
        fun settingsTab() {
            val tab = device.wait(Until.findObject(By.desc("Settings")), 5_000)
            checkNotNull(tab).click()
            assertTrue(device.wait(Until.hasObject(By.text("Interface style")), 5_000))
        }
        fun screenshot(name: String) {
            device.waitForIdle()
            // UIAutomator idle does not include Compose draw/transition frames. Let the final
            // frame reach SurfaceFlinger instead of capturing the previous theme or closing menu.
            android.os.SystemClock.sleep(500)
            assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "$name.png")))
        }
        try {
            settings.setLanguage("en")
            settings.setUiStyle(UiStyle.MATERIAL3)
            restart()
            settingsTab()
            checkNotNull(device.wait(Until.findObject(By.text("Material 3")), 5_000)).click()
            checkNotNull(device.wait(Until.findObject(By.text("Liquid Glass")), 5_000)).click()
            assertTrue(device.wait(Until.hasObject(By.textStartsWith("Refractive glass")), 5_000))
            assertEquals(UiStyle.LIQUID_GLASS, settings.settings.first().uiStyle)
            // Every palette swap changes the recorded source; catch stale render-layer bindings.
            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                settings.setTheme(mode)
                settings.setDynamic(false)
                assertTrue(device.wait(Until.hasObject(By.text(if (mode == ThemeMode.LIGHT) "Light" else "Dark")), 5_000))
                assertTrue(device.wait(Until.hasObject(By.text("Theme color")), 5_000))
                screenshot("liquid-glass-${mode.name.lowercase()}")
            }
            settings.setDynamic(true)
            assertTrue(device.wait(Until.gone(By.text("Theme color")), 5_000))
            screenshot("liquid-glass-dynamic")
            restart()
            assertEquals(UiStyle.LIQUID_GLASS, settings.settings.first().uiStyle)
            settingsTab()
            assertTrue(device.wait(Until.hasObject(By.text("Liquid Glass")), 5_000))
            device.executeShellCommand("settings put global animator_duration_scale 0")
            screenshot("liquid-glass-reduced-motion")
            // Exercise every adapter while retaining the active settings destination.
            for (style in listOf(UiStyle.MIUIX, UiStyle.MATERIAL3, UiStyle.LIQUID_GLASS)) {
                settings.setUiStyle(style)
                device.waitForIdle()
                assertTrue(device.wait(Until.hasObject(By.text("Settings")), 5_000))
            }
            assertTrue(device.executeShellCommand("pidof com.bond.mail").isNotBlank())
        } finally {
            if (originalMotion == "null") {
                device.executeShellCommand("settings delete global animator_duration_scale")
            } else {
                device.executeShellCommand("settings put global animator_duration_scale $originalMotion")
            }
            settings.setTheme(original.themeMode)
            settings.setDynamic(original.dynamicColor)
            settings.setLanguage(original.languageCode)
            settings.setUiStyle(original.uiStyle)
        }
    }
}
