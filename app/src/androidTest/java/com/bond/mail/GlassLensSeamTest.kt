package com.bond.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.bond.mail.data.settings.*
import com.bond.mail.ui.theme.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class GlassLensSeamTest {
    @Test fun capsuleCenterStaysContinuousAtStrongRefractionWithAndWithoutDispersion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var dark by mutableStateOf(false)
        var height by mutableFloatStateOf(32f)
        var dispersion by mutableFloatStateOf(0f)
        var bounds = Rect.Zero
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
                    themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
                    val backdrop = rememberLayerBackdrop()
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF386A91)).layerBackdrop(backdrop))
                        CompositionLocalProvider(LocalGlassEffects provides true,
                            LocalGlassSettings provides GlassSettings(refractionHeight = height,
                                chromaticAberration = dispersion)) {
                            Box(Modifier.align(Alignment.Center).size(238.dp, 52.dp)
                                .onGloballyPositioned { bounds = it.boundsInWindow() }
                                .glassDockSurface(backdrop))
                        }
                    }
                }
            } }
            for (night in listOf(false, true)) for (strength in listOf(12f, 26f, 32f)) {
                for (chromatic in listOf(0f, 1f)) {
                    instrumentation.runOnMainSync { dark = night; height = strength; dispersion = chromatic }
                    instrumentation.waitForIdleSync()
                    android.os.SystemClock.sleep(250)
                    val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                    val center = bounds.center.y.roundToInt()
                    var maximumDifference = 0
                    // A flat backdrop must stay flat across the capsule's axis, including the
                    // exact center pixel and both plain/dispersion shader paths.
                    for (x in (bounds.left + bounds.width * .25f).roundToInt()..
                        (bounds.right - bounds.width * .25f).roundToInt()) {
                        val reference = bitmap.getPixel(x, center - 6)
                        for (y in center - 2..center + 2) {
                            val pixel = bitmap.getPixel(x, y)
                            for (shift in listOf(0, 8, 16)) maximumDifference = maxOf(maximumDifference,
                                abs(((pixel shr shift) and 255) - ((reference shr shift) and 255)))
                        }
                    }
                    bitmap.recycle()
                    assertTrue("Center seam: dark=$night height=$strength dispersion=$chromatic delta=$maximumDifference",
                        maximumDifference <= 4)
                }
            }
        }
    }
}
