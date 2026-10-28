package com.bond.mail

import androidx.activity.BackEventCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

/** Runs against a configured test device; never adds, removes or changes a mailbox. */
@RunWith(AndroidJUnit4::class)
class DrawerNavigationTest {
    @Test fun addMailboxReturnsToClosedInboxAfterCancelledBackPreview() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val menu = By.desc(Pattern.compile("打开导航菜单|開啟導覽選單|Open navigation menu"))
        val allMailboxes = By.text(Pattern.compile("所有邮箱|所有郵箱|All mailboxes"))
        val add = By.text(Pattern.compile("添加邮箱|新增郵箱|Add mailbox"))
        val provider = By.text(Pattern.compile("选择邮箱服务商|選擇郵箱服務商|Choose a mail provider"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue("Requires a configured mailbox and unobstructed inbox", device.wait(Until.hasObject(menu), 20_000))
            device.waitForIdle()
            device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "drawer-inbox-baseline.png"))
            device.findObject(menu).click()
            assertTrue(device.wait(Until.hasObject(allMailboxes), 5000))
            device.waitForIdle()
            device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "drawer-open-baseline.png"))
                val x = device.displayWidth / 3
                repeat(3) { device.swipe(x, device.displayHeight * 4 / 5, x, device.displayHeight / 3, 25) }
                assertFalse("Settings is redundant in the account drawer",
                    device.hasObject(By.text(Pattern.compile("设置|設定|Settings"))))
                repeat(3) { device.swipe(x, device.displayHeight / 3, x, device.displayHeight * 4 / 5, 25) }
                assertTrue(device.wait(Until.hasObject(add), 5000))
                device.findObject(add).click()
                assertTrue(device.wait(Until.hasObject(provider), 7000))
                android.os.SystemClock.sleep(700)
                scenario.onActivity {
                    it.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 700f, 0f, BackEventCompat.EDGE_LEFT))
                    it.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(300f, 700f, .75f, BackEventCompat.EDGE_LEFT))
                }
                android.os.SystemClock.sleep(350)
                // Save the held preview for visual review; the card's shadow and scrim prevent
                // a direct pixel comparison with the untransformed inbox screenshot.
                device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "drawer-add-back-preview.png"))
                scenario.onActivity { it.onBackPressedDispatcher.dispatchOnBackCancelled() }
                android.os.SystemClock.sleep(400)
                assertTrue(device.hasObject(provider))
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                assertTrue(device.wait(Until.hasObject(menu), 5000))
                assertFalse(device.hasObject(allMailboxes))
                android.os.SystemClock.sleep(600)
                assertFalse("Drawer must remain closed after return", device.hasObject(allMailboxes))
                device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "drawer-add-returned.png"))
        }
    }
}
