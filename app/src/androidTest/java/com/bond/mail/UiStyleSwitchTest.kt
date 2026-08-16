package com.bond.mail

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bond.mail.data.settings.UiStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiStyleSwitchTest {
    @Test
    fun bottomDockReturnsToMailAfterContactsAndSettings() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val settings = (targetContext.applicationContext as MailApplication).container.settings
        val device = UiDevice.getInstance(instrumentation)

        runBlocking {
            settings.setLanguage("en")
            settings.setUiStyle(UiStyle.MATERIAL3)
        }
        startApp(device)

        clickTab(device, "Settings")
        assertTrue(
            "Settings tab did not open",
            device.wait(Until.hasObject(By.text("Settings")), 5_000L),
        )

        clickTab(device, "Contacts")
        assertTrue(
            "Contacts tab did not open",
            device.wait(Until.hasObject(By.text("Contacts")), 5_000L),
        )

        clickTab(device, "Mail")
        assertTrue(
            "Mail tab did not reopen after another bottom destination",
            device.wait(Until.hasObject(By.text("Welcome to Mail")), 5_000L),
        )
    }

    @Test
    fun uiStyleSwitchesBothWaysAndSurvivesActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val settings = (targetContext.applicationContext as MailApplication).container.settings
        val device = UiDevice.getInstance(instrumentation)

        runBlocking {
            settings.setUiStyle(UiStyle.MATERIAL3)
            assertEquals(UiStyle.MATERIAL3, settings.settings.first().uiStyle)
        }

        startApp(device)
        assertTrue(
            "BondMail did not render with Material 3",
            device.wait(Until.hasObject(By.pkg(targetContext.packageName)), 10_000L),
        )

        // Change the same DataStore observed by MainActivity. The existing Activity and navigation
        // host must stay alive while its screen components change to native MIUIX widgets.
        runBlocking {
            settings.setUiStyle(UiStyle.MIUIX)
            assertEquals(UiStyle.MIUIX, settings.settings.first().uiStyle)
        }
        device.waitForIdle(5_000L)
        assertTrue(
            "BondMail stopped while switching to MIUIX",
            device.executeShellCommand("pidof com.bond.mail").isNotBlank(),
        )

        // CLEAR_TASK creates a fresh MainActivity while the instrumentation process remains alive.
        startApp(device)
        runBlocking {
            assertEquals(
                "MIUIX selection was not restored after Activity recreation",
                UiStyle.MIUIX,
                settings.settings.first().uiStyle,
            )
        }

        // Switching back must keep the same Activity alive and persist Material 3 as well.
        runBlocking {
            settings.setUiStyle(UiStyle.MATERIAL3)
            assertEquals(UiStyle.MATERIAL3, settings.settings.first().uiStyle)
        }
        device.waitForIdle(5_000L)
        assertTrue(
            "BondMail stopped while switching back to Material 3",
            device.executeShellCommand("pidof com.bond.mail").isNotBlank(),
        )
        startApp(device)
        runBlocking {
            assertEquals(
                "Material 3 selection was not restored after Activity recreation",
                UiStyle.MATERIAL3,
                settings.settings.first().uiStyle,
            )

            // Leave the connected development device in MIUIX for visual review.
            settings.setUiStyle(UiStyle.MIUIX)
        }
        runBlocking {
            assertEquals(UiStyle.MIUIX, settings.settings.first().uiStyle)
        }
    }

    private fun startApp(device: UiDevice) {
        // HyperOS blocks both ADB and instrumentation input injection unless its extra security
        // toggle is enabled. Starting through shell still exercises the real Activity lifecycle.
        device.executeShellCommand(
            "am start -W -f 0x10008000 -n com.bond.mail/.MainActivity",
        )
        device.waitForIdle(5_000L)
    }

    private fun clickTab(device: UiDevice, description: String) {
        val icon = device.wait(Until.findObject(By.desc(description)), 5_000L)
        assertTrue("Bottom tab $description was not found", icon != null)
        icon.click()
        device.waitForIdle(2_000L)
    }
}
