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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LiquidGlassStyleTest {
    @Test
    fun stylePickerPersistsAndSwitchesBackToBothOriginalStyles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = (context.applicationContext as MailApplication).container.settings
        val original = runBlocking { settings.settings.first() }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        fun launch() {
            device.executeShellCommand("am start -W -f 0x10008000 -n com.bond.mail/.MainActivity")
            assertNotNull(device.wait(Until.findObject(By.desc("Settings")), 30_000))
            device.waitForIdle()
        }
        fun select(label: String) {
            val current = runBlocking { settings.settings.first().uiStyle }
            val currentLabel = when (current) {
                UiStyle.MATERIAL3 -> "Material 3"
                UiStyle.MIUIX -> "MIUIX"
                UiStyle.LIQUID_GLASS -> "Liquid Glass"
            }
            checkNotNull(device.wait(Until.findObject(By.text(currentLabel)), 20_000)).click()
            checkNotNull(device.wait(Until.findObject(By.text(label)), 20_000)).click()
        }
        fun awaitStyle(style: UiStyle) {
            val deadline = android.os.SystemClock.uptimeMillis() + 20_000
            while (runBlocking { settings.settings.first().uiStyle } != style && android.os.SystemClock.uptimeMillis() < deadline) {
                android.os.SystemClock.sleep(100)
            }
            assertEquals(style, runBlocking { settings.settings.first().uiStyle })
        }
        fun shot(name: String) {
            device.waitForIdle()
            android.os.SystemClock.sleep(700)
            device.takeScreenshot(File(context.getExternalFilesDir(null), "$name.png"))
        }
        try {
            runBlocking { settings.setLanguage("en"); settings.setTheme(ThemeMode.LIGHT); settings.setUiStyle(UiStyle.MATERIAL3) }
            launch()
            checkNotNull(device.findObject(By.desc("Settings"))).click()
            select("Liquid Glass")
            awaitStyle(UiStyle.LIQUID_GLASS)
            assertFalse(device.hasObject(By.textStartsWith("Refractive glass")))
            shot("glass-style-light")
            launch()
            assertEquals(UiStyle.LIQUID_GLASS, runBlocking { settings.settings.first().uiStyle })
            checkNotNull(device.findObject(By.desc("Settings"))).click()
            runBlocking { settings.setTheme(ThemeMode.DARK) }
            assertTrue(device.wait(Until.hasObject(By.text("Dark")), 20_000))
            shot("glass-style-dark")
            select("MIUIX")
            awaitStyle(UiStyle.MIUIX)
            select("Liquid Glass")
            awaitStyle(UiStyle.LIQUID_GLASS)
            select("Material 3")
            awaitStyle(UiStyle.MATERIAL3)
        } catch (failure: Throwable) {
            shot("glass-style-failure")
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "glass-style-failure.xml"))
            throw failure
        } finally {
            runBlocking { settings.setTheme(original.themeMode); settings.setLanguage(original.languageCode); settings.setUiStyle(original.uiStyle) }
        }
    }
}
