package com.bond.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.components.ComposeExtras
import com.bond.mail.ui.components.SendStatusContent
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.theme.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class LongTextLayoutTest {
    @Test fun compactActionsKeepLabelsInsideButtonsAtNormalAndLargeFontSizes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var installed = false
            val deadline = android.os.SystemClock.uptimeMillis() + 20_000
            while (!installed && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { installed = MainActivity::class.java.getDeclaredField("contentInstalled")
                    .apply { isAccessible = true }.getBoolean(it) }
                if (!installed) Thread.sleep(100)
            }
            assertTrue(installed)
            for (scale in listOf(1f, 1.6f)) {
                val resetBounds = AtomicReference(Rect.Zero)
                val disabledBounds = AtomicReference(Rect.Zero)
                var density = 1f
                scenario.onActivity { activity -> activity.setContent {
                    density = LocalDensity.current.density
                    CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                        BondMailTheme(AppSettings(uiStyle = UiStyle.LIQUID_GLASS,
                            themeMode = if (scale == 1f) ThemeMode.LIGHT else ThemeMode.DARK)) {
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                              JsonStringsProvider("en") {
                                Column(Modifier.fillMaxSize().background(MaterialTheme.bondSurfaces.page).statusBarsPadding()) {
                                    Column(Modifier.width(300.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("Font scale $scale")
                                        BondTextAction("Reset sender display preference", {},
                                            Modifier.fillMaxWidth().onGloballyPositioned { resetBounds.set(it.boundsInWindow()) })
                                        BondTextAction("Disabled sender display preference", {}, enabled = false,
                                            modifier = Modifier.fillMaxWidth().onGloballyPositioned { disabledBounds.set(it.boundsInWindow()) })
                                        ComposeExtras("layout-test") {}
                                        SendStatusContent("Fwd: 国际机票出行单 — a very long message subject repeated for a compact screen") {
                                            BondFormAction({}) { Text("Undo send (5)") }
                                        }
                                    }
                                }
                              }
                            }
                        }
                    }
                } }
                assertTrue(device.wait(Until.hasObject(By.text("Font scale $scale")), 7000))
                device.waitForIdle()
                listOf("Reset sender display preference" to resetBounds.get(),
                    "Disabled sender display preference" to disabledBounds.get()).forEach { (label, button) ->
                    val text = checkNotNull(device.findObject(By.text(label))).visibleBounds
                    assertTrue("Top padding for $label", text.top >= button.top + 10 * density)
                    assertTrue("Bottom padding for $label", text.bottom <= button.bottom - 10 * density)
                    if (scale > 1f) assertTrue("Wrapped label must increase button height", button.height > 48 * density)
                }
                val signature = checkNotNull(device.findObject(By.text("Insert signature"))).visibleBounds
                val templates = checkNotNull(device.findObject(By.text("Reply templates"))).visibleBounds
                assertTrue("Compact actions must stack instead of squeezing labels", templates.top > signature.bottom)
                val subject = checkNotNull(device.findObject(By.textStartsWith("Fwd:"))).visibleBounds
                val undo = checkNotNull(device.findObject(By.text("Undo send (5)"))).visibleBounds
                assertTrue("Undo action must have its own row", undo.top > subject.bottom)
                device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "long-text-$scale.png"))
            }
        }
    }
}
