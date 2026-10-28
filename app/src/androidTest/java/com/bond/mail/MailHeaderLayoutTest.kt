package com.bond.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bond.mail.data.mail.MailAttachmentInfo
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.screens.MailInformationHeader
import com.bond.mail.ui.components.MailWebHeader
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.screens.rememberMailHeaderLayout
import com.bond.mail.ui.theme.BondMailTheme
import com.bond.mail.ui.theme.bondSurfaces
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class MailHeaderLayoutTest {
    @Test fun dateStaysAtTrailingEdgeAndHeaderHasBreathingRoom() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val header = mutableStateOf(MailWebHeader(
            subject = "趋势概览：Top 5% 投资者最常交易的股票 💡",
            senderName = "ZA Bank",
            senderAddress = "bank.update@mail.za.group",
            recipient = "reader@example.com",
            dateLabel = "10:13 AM",
            avatarText = "Z",
        ))
        val largeText = mutableStateOf(false)
        val theme = mutableStateOf(ThemeMode.LIGHT)
        val clicked = AtomicBoolean(false)
        var density = 1f
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var installed = false
            val deadline = android.os.SystemClock.uptimeMillis() + 10_000
            while (!installed && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { installed = MainActivity::class.java.getDeclaredField("contentInstalled")
                    .apply { isAccessible = true }.getBoolean(it) }
                if (!installed) Thread.sleep(100)
            }
            assertTrue(installed)
            scenario.onActivity { activity -> activity.setContent {
                density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, if (largeText.value) 1.6f else 1f)) {
                    BondMailTheme(AppSettings(uiStyle = UiStyle.LIQUID_GLASS, themeMode = theme.value)) {
                        JsonStringsProvider("zh") {
                            Column(Modifier.fillMaxSize().background(MaterialTheme.bondSurfaces.page)
                                .statusBarsPadding().padding(horizontal = 12.dp, vertical = 24.dp)) {
                                MailInformationHeader(header.value, rememberMailHeaderLayout(header.value.subject),
                                    onAttachmentsClick = { clicked.set(true) })
                                Column(Modifier.fillMaxWidth().background(MaterialTheme.bondSurfaces.content).padding(24.dp)) {
                                    Text("众安银行", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(Modifier.height(40.dp))
                                    Text("你好！\n\n这里是邮件正文，与发件人信息保持清晰间距。", fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.text("10:13 AM")), 7000))
            device.waitForIdle()
            val before = device.findObject(By.text("10:13 AM")).visibleBounds
            assertEquals("Date must end at the card's right inset", device.displayWidth - 28f * density,
                before.right.toFloat(), 2f)
            val recipient = device.findObject(By.text("reader@example.com")).visibleBounds
            val body = device.findObject(By.text("众安银行")).visibleBounds
            assertTrue("Metadata must not touch the body", body.top - recipient.bottom >= 32 * density)
            device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "mail-header-light.png"))

            scenario.onActivity { header.value = header.value.copy(attachments = listOf(MailAttachmentInfo("statement.pdf"))) }
            assertTrue(device.wait(Until.hasObject(By.desc("附件")), 5000))
            val after = device.findObject(By.text("10:13 AM")).visibleBounds
            assertEquals("Loading attachment metadata must not shift the date", before, after)
            device.findObject(By.desc("附件")).click()
            instrumentation.waitForIdleSync()
            assertTrue(clicked.get())

            scenario.onActivity { largeText.value = true; theme.value = ThemeMode.DARK }
            device.waitForIdle()
            val name = device.findObject(By.text("ZA Bank")).visibleBounds
            val largeRecipient = device.findObject(By.text("reader@example.com")).visibleBounds
            assertTrue("Large-text sender and recipient must stay separate", name.bottom < largeRecipient.top)
            assertEquals(device.displayWidth - 28f * density,
                device.findObject(By.text("10:13 AM")).visibleBounds.right.toFloat(), 2f)
            device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "mail-header-dark-large.png"))
        }
    }
}
