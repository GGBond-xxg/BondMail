package com.bond.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.theme.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedBottomDockTest {
    @Test fun mainAndReaderKeepOneLayoutAndUseCurrentActionsInEveryStyle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        var style by mutableStateOf(UiStyle.MATERIAL3)
        var reader by mutableStateOf(false)
        var selected by mutableIntStateOf(0)
        var coordinates: LayoutCoordinates? = null
        var auxiliaryBounds = Rect.Zero
        var composeCalls = 0
        val actionCalls = mutableListOf<String>()
        var mailId by mutableStateOf("first")
        fun settle() {
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(350)
        }
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
                BondMailTheme(AppSettings(uiStyle = style)) {
                    JsonStringsProvider("en") {
                        val backdrop = rememberLayerBackdrop()
                        val id = mailId
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().background(Color.Gray).layerBackdrop(backdrop))
                            Box(Modifier.align(Alignment.BottomCenter).bottomDockAuxiliaryBounds(88.dp)) {
                                Box(Modifier.align(Alignment.BottomEnd).size(BottomDockDefaults.AuxiliarySize)
                                    .onGloballyPositioned { auxiliaryBounds = it.boundsInWindow() })
                            }
                            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                                AppBottomDock(selected, { selected = it }, { composeCalls++ }, reader,
                                    ReaderDockActions(id, { actionCalls.add("reply:$id") },
                                        { actionCalls.add("forward:$id") }, { actionCalls.add("share:$id") },
                                        { actionCalls.add("delete:$id") }),
                                    Modifier.align(Alignment.BottomCenter).onGloballyPositioned { coordinates = it },
                                )
                            }
                        }
                    }
                }
            } }
            for (variant in UiStyle.entries) {
                instrumentation.runOnMainSync { style = variant; reader = false }
                assertTrue(device.wait(Until.hasObject(By.desc("Compose")), 5_000))
                settle()
                val mainCoordinates = coordinates
                val mainSize = checkNotNull(mainCoordinates).size
                val mainBounds = device.findObject(By.desc("Compose")).visibleBounds
                assertEquals("Scroll-to-top must share the trailing button axis",
                    mainBounds.exactCenterX(), auxiliaryBounds.center.x, 1f)
                repeat(2) { iteration ->
                    val id = "${variant.name}-$iteration"
                    instrumentation.runOnMainSync { mailId = id; reader = true }
                    assertTrue(device.wait(Until.hasObject(By.desc("Reply")), 5_000))
                    settle()
                    assertSame("Dock layout was recreated on entry", mainCoordinates, coordinates)
                    assertEquals(mainSize, coordinates!!.size)
                    val deleteButton = device.wait(Until.findObject(By.desc("Delete")), 2_000)
                    assertNotNull("Missing Delete ($variant/$iteration); visible labels: " +
                        device.findObjects(By.desc(java.util.regex.Pattern.compile(".+"))).map { it.contentDescription }, deleteButton)
                    assertEquals(mainBounds, deleteButton!!.visibleBounds)
                    assertTrue(device.wait(Until.gone(By.desc("Compose")), 2_000))
                    for ((label, action) in listOf("Reply" to "reply", "Forward" to "forward", "Share" to "share", "Delete" to "delete")) {
                        device.findObject(By.desc(label)).click()
                        instrumentation.waitForIdleSync()
                        assertEquals("$action:$id", actionCalls.last())
                    }
                    assertEquals("Reader actions must not trigger Compose", 0, composeCalls)
                    instrumentation.runOnMainSync { reader = false }
                    settle()
                    assertSame("Dock layout was recreated on return", mainCoordinates, coordinates)
                    assertTrue(device.wait(Until.gone(By.desc("Delete")), 2_000))
                }
            }
        }
    }
}
