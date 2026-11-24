package com.bond.mail

import android.Manifest
import android.graphics.Bitmap
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.MotionEvent
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
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bond.mail.data.settings.*
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.theme.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class M3SplitDockTest {
    @Test fun halvesStayIndependentAndRestoreAfterQuickTapsAndCancellation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val resolver = instrumentation.targetContext.contentResolver
        val previousScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
        val density = instrumentation.targetContext.resources.displayMetrics.density
        var selected by mutableIntStateOf(0)
        var mode by mutableStateOf(ThemeMode.LIGHT)
        var bounds = Rect.Zero
        var navCalls = 0
        var composeCalls = 0
        var downTime = 0L
        fun settle(ms: Long = 450) { instrumentation.waitForIdleSync(); SystemClock.sleep(ms) }
        fun touch(action: Int, x: Float, y: Float) {
            if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(automation.injectInputEvent(event, true))
            event.recycle()
        }
        fun shot() = checkNotNull(automation.takeScreenshot())
        try {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var installed = false
                val deadline = SystemClock.uptimeMillis() + 20_000
                while (!installed && SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity { installed = MainActivity::class.java.getDeclaredField("contentInstalled")
                        .apply { isAccessible = true }.getBoolean(it) }
                    if (!installed) SystemClock.sleep(100)
                }
                assertTrue(installed)
                scenario.onActivity { activity -> activity.setContent {
                    BondMailTheme(AppSettings(uiStyle = UiStyle.MATERIAL3, themeMode = mode)) {
                        JsonStringsProvider("en") {
                            Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                                AppBottomDock(selected, { selected = it; navCalls++ }, { composeCalls++ },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                        .onGloballyPositioned { bounds = it.boundsInWindow() })
                            }
                        }
                    }
                } }
                for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                    instrumentation.runOnMainSync { mode = theme; selected = 0 }
                    settle()
                    val left = bounds.left + 12 * density
                    val right = bounds.right - 12 * density
                    val top = bounds.top + 10 * density
                    val joinRight = right - 52 * density
                    val joinLeft = joinRight - 2 * density
                    val centerY = top + 26 * density
                    val navX = (left + joinLeft) / 2
                    val actionX = right - 26 * density
                    fun corner(bitmap: Bitmap, isLeft: Boolean, inset: Float = 2f): Int = bitmap.getPixel(
                        (if (isLeft) joinLeft - inset * density else joinRight + inset * density).roundToInt(),
                        (top + inset * density).roundToInt())
                    val rest = shot()
                    // The gap is the actual page background for the entire visible height.
                    for (y in 1..51) assertEquals(android.graphics.Color.MAGENTA,
                        rest.getPixel(((joinLeft + joinRight) / 2).roundToInt(), (top + y * density).roundToInt()))
                    for (isLeft in listOf(true, false)) {
                        val x = if (isLeft) navX else actionX
                        val callsBefore = navCalls + composeCalls
                        touch(MotionEvent.ACTION_DOWN, x, centerY)
                        settle(350)
                        val pressed = shot()
                        assertNotEquals("Pressed half must round its joining corner ($theme/$isLeft)", corner(rest, isLeft), corner(pressed, isLeft))
                        assertEquals("Other half must stay still", corner(rest, !isLeft), corner(pressed, !isLeft))
                        assertEquals("Pressed half must not become circular", corner(rest, isLeft, 6f), corner(pressed, isLeft, 6f))
                        // Neither the selected background nor the untouched interior gains a press tint.
                        assertEquals(rest.getPixel(x.roundToInt(), (top + 8 * density).roundToInt()),
                            pressed.getPixel(x.roundToInt(), (top + 8 * density).roundToInt()))
                        touch(MotionEvent.ACTION_CANCEL, x, centerY)
                        settle()
                        val cancelled = shot()
                        assertEquals("Cancel restores corner", corner(rest, isLeft), corner(cancelled, isLeft))
                        assertEquals("Cancel must not click", callsBefore, navCalls + composeCalls)
                        pressed.recycle(); cancelled.recycle()
                    }
                    val itemWidth = (joinLeft - left - 12 * density) / 3
                    val initialNavCalls = navCalls
                    for (tab in listOf(2, 0, 1)) {
                        val x = left + 6 * density + itemWidth * (tab + .5f)
                        touch(MotionEvent.ACTION_DOWN, x, centerY)
                        SystemClock.sleep(10)
                        touch(MotionEvent.ACTION_UP, x, centerY)
                        SystemClock.sleep(65)
                    }
                    settle()
                    assertEquals(1, selected)
                    assertEquals(initialNavCalls + 3, navCalls)
                    val initialCompose = composeCalls
                    touch(MotionEvent.ACTION_DOWN, actionX, centerY)
                    touch(MotionEvent.ACTION_UP, actionX, centerY)
                    settle()
                    assertEquals(initialCompose + 1, composeCalls)
                    val tapped = shot()
                    assertEquals(corner(rest, true), corner(tapped, true))
                    assertEquals(corner(rest, false), corner(tapped, false))
                    // The setting is observed live: disabled system motion keeps both halves static.
                    Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
                    settle()
                    for (x in listOf(navX, actionX)) {
                        touch(MotionEvent.ACTION_DOWN, x, centerY)
                        settle(200)
                        val static = shot()
                        assertEquals(corner(rest, true), corner(static, true))
                        assertEquals(corner(rest, false), corner(static, false))
                        touch(MotionEvent.ACTION_CANCEL, x, centerY)
                        static.recycle()
                    }
                    Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
                    rest.recycle(); tapped.recycle()
                }
            }
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, previousScale)
            automation.dropShellPermissionIdentity()
        }
    }
}
