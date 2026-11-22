package com.bond.mail

import android.graphics.Color as PixelColor
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.data.settings.GlassSettings
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.theme.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Pixel regression: off-screen redraw must not leave a transparent dock on return. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class GlassDockRecoveryTest {
    @Test fun offscreenRedrawThenReturnRestoresBlur() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val hidden = mutableStateOf(false)
        val blur = mutableStateOf(6f)
        val dark = mutableStateOf(false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var installed = false
            val deadline = android.os.SystemClock.uptimeMillis() + 20_000
            while (!installed && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { installed = MainActivity::class.java.getDeclaredField("contentInstalled")
                    .apply { isAccessible = true }.getBoolean(it) }
                if (!installed) android.os.SystemClock.sleep(100)
            }
            assertTrue(installed)
            scenario.onActivity { activity -> activity.setContent {
                BondMailTheme(AppSettings(uiStyle = UiStyle.LIQUID_GLASS,
                    themeMode = if (dark.value) ThemeMode.DARK else ThemeMode.LIGHT)) {
                    JsonStringsProvider("en") {
                        val backdrop = rememberLayerBackdrop()
                        Box(Modifier.fillMaxSize()) {
                            Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                                val stripe = 4.dp.toPx()
                                repeat((size.width / stripe).toInt() + 1) { index ->
                                    drawRect(if (index % 2 == 0) Color.Red else Color.Blue,
                                        Offset(index * stripe, 0f), Size(stripe, size.height))
                                }
                            }
                            CompositionLocalProvider(LocalGlassEffects provides true,
                                LocalGlassBackdrop provides backdrop,
                                LocalGlassSettings provides GlassSettings(blurRadius = blur.value)) {
                                GlassMainDock(2, {}, {}, Modifier.align(Alignment.BottomCenter)
                                    .bottomDockOffset(if (hidden.value) 200.dp else 0.dp))
                            }
                        }
                    }
                }
            } }
            fun assertBlurred(label: String) {
                instrumentation.waitForIdleSync()
                android.os.SystemClock.sleep(500)
                val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                val density = instrumentation.targetContext.resources.displayMetrics.density
                // Device uses edge-to-edge; inset is read from the actual Activity, not assumed.
                var inset = 0
                scenario.onActivity { inset = it.window.decorView.rootWindowInsets
                    .getInsets(android.view.WindowInsets.Type.navigationBars()).bottom }
                val left = (bitmap.width - minOf(bitmap.width / density - 24f, 300f) * density) / 2f
                val centerY = bitmap.height - inset - (20f + 26f) * density
                var contrast = 0.0
                var count = 0
                // Left portion of the first tab, away from its icon and the rounded edge.
                for (x in (left + 12 * density).toInt()..(left + 24 * density).toInt()) {
                    for (y in (centerY - 5 * density).toInt()..(centerY + 5 * density).toInt()) {
                        val pixel = bitmap.getPixel(x, y)
                        contrast += abs(PixelColor.red(pixel) - PixelColor.blue(pixel))
                        count++
                    }
                }
                bitmap.recycle()
                assertTrue("$label: red/blue stripes remain sharp ($contrast / $count)", contrast / count < 45)
            }
            for (isDark in listOf(false, true)) {
                instrumentation.runOnMainSync { dark.value = isDark; hidden.value = false; blur.value = 6f }
                assertBlurred("initial dark=$isDark")
                repeat(3) {
                    instrumentation.runOnMainSync { hidden.value = true }
                    instrumentation.waitForIdleSync()
                    android.os.SystemClock.sleep(150)
                    // Like navigation recomposition: update a draw node while it is off-screen.
                    instrumentation.runOnMainSync { blur.value = if (it % 2 == 0) 8f else 6f }
                    instrumentation.waitForIdleSync()
                    android.os.SystemClock.sleep(150)
                    instrumentation.runOnMainSync { hidden.value = false }
                    assertBlurred("restored dark=$isDark cycle=$it")
                }
            }
        }
    }
}
