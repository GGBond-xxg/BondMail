package com.bond.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.components.MailWebHeader
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.screens.MailDocumentPlaceholder
import com.bond.mail.ui.screens.rememberMailHeaderLayout
import com.bond.mail.ui.theme.BondMailTheme
import com.bond.mail.ui.theme.bondSurfaces
import org.junit.Assert.*
import org.junit.Test

class MailLoadingPresentationTest {
    @Test fun loadingBodyHasNoSeparateCardInEveryTheme() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var style by mutableStateOf(UiStyle.MATERIAL3)
        var theme by mutableStateOf(ThemeMode.LIGHT)
        var pageColor = 0
        val device = UiDevice.getInstance(instrumentation)
        var progressCenterY = 0f
        var bodyY = 0
        var insetX = 0
        val header = MailWebHeader(subject = "Loading fixture", senderName = "Test sender",
            senderAddress = "notice@example.invalid", recipient = "reader@example.invalid",
            dateLabel = "Today", avatarText = "T")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var installed = false
            val deadline = android.os.SystemClock.uptimeMillis() + 20_000
            while (!installed && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { installed = MainActivity::class.java.getDeclaredField("contentInstalled")
                    .apply { isAccessible = true }.getBoolean(it) }
                if (!installed) Thread.sleep(100)
            }
            assertTrue(installed)
            scenario.onActivity { activity -> activity.setContent {
                BondMailTheme(AppSettings(uiStyle = style, themeMode = theme)) {
                    JsonStringsProvider("en") {
                        val layout = rememberMailHeaderLayout(header.subject)
                        val density = LocalDensity.current
                        pageColor = MaterialTheme.bondSurfaces.page.toArgb()
                        // Inside the former body's left padding, away from skeleton/progress pixels.
                        insetX = with(density) { 24.dp.roundToPx() }
                        bodyY = with(density) {
                            (80.dp + layout.subjectBlockHeight + layout.senderBlockHeight + 60.dp).roundToPx()
                        }
                        progressCenterY = with(density) {
                            val bodyTop = (80.dp + layout.subjectBlockHeight + layout.senderBlockHeight + 12.dp).toPx()
                            val bodyBottom = device.displayHeight - WindowInsets.navigationBars.getBottom(density) - 72.dp.toPx()
                            (bodyTop + bodyBottom) / 2f
                        }
                        MailDocumentPlaceholder(true, header, layout, "", 80.dp,
                            modifier = Modifier.fillMaxSize())
                    }
                }
            } }
            for (variant in UiStyle.entries) for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                instrumentation.runOnMainSync { style = variant; theme = mode }
                instrumentation.waitForIdleSync()
                Thread.sleep(300)
                if (variant == UiStyle.MATERIAL3) {
                    val progress = device.findObject(By.desc("Loading…"))
                    assertNotNull("Reader must expose its loading indicator", progress)
                    assertEquals("Indicator must center between header and action dock", progressCenterY,
                        progress.visibleBounds.exactCenterY(), 2f)
                }
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                try {
                    // SurfaceFlinger can quantize a dark channel by one level. Compare the body
                    // with the real page in the same frame, not an unconverted sRGB integer.
                    val pagePixel = bitmap.getPixel(1, bodyY)
                    for (shift in listOf(0, 8, 16)) assertTrue("Page color must remain theme-correct",
                        kotlin.math.abs(((pagePixel shr shift) and 255) - ((pageColor shr shift) and 255)) <= 1)
                    assertEquals("Loading card must match page: $variant/$mode", pagePixel,
                        bitmap.getPixel(insetX, bodyY))
                    assertEquals("Loading must not invent a document bottom: $variant/$mode", pagePixel,
                        bitmap.getPixel(insetX, bitmap.height * 3 / 4))
                } finally { bitmap.recycle() }
            }
        }
    }
}
