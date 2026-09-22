package com.bond.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bond.mail.data.mail.TranslatedHtmlMail
import com.bond.mail.data.mail.TranslationProvider
import com.bond.mail.ui.components.InlineMailTranslationState
import com.bond.mail.ui.components.InlineTranslationSelectors
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.motion.animateChromeOffset
import com.bond.mail.ui.motion.floatingActionBottomPadding
import com.bond.mail.ui.screens.MessageActionDock
import com.bond.mail.ui.screens.MessageTranslationActions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class InlineTranslationLayoutTest {
    @Test fun floatingTranslationActionsFollowNavigationAndKeepOriginalToggle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val visible = mutableStateOf(true)
        val state = InlineMailTranslationState(TranslationProvider.GOOGLE, "zh").apply {
            result = TranslatedHtmlMail("离线测试", "<p>模拟译文，不使用任何密钥。</p>")
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            Thread.sleep(2000)
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        JsonStringsProvider("zh") {
                            Surface {
                                Box(Modifier.fillMaxSize()) {
                                    Row(Modifier.statusBarsPadding().align(Alignment.TopEnd)) { InlineTranslationSelectors(state) }
                                    val bottom = floatingActionBottomPadding(visible.value)
                                    val offset = animateChromeOffset(visible.value, 124.dp, "test-dock")
                                    MessageTranslationActions(state, true, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = bottom))
                                    MessageActionDock({}, {}, {}, {}, Modifier.align(Alignment.BottomCenter).graphicsLayer { translationY = offset.toPx() }.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp))
                                }
                            }
                        }
                    }
                }
            }
            assertTrue(device.wait(Until.hasObject(By.desc("翻译邮件")), 5000))
            val original = device.findObject(By.desc("查看原文"))
            val translate = device.findObject(By.desc("翻译邮件"))
            val delete = device.findObject(By.desc("删除"))
            val initial = translate.visibleBounds
            assertEquals(original.visibleBounds.width(), initial.width())
            assertEquals(original.visibleBounds.height(), initial.height())
            assertEquals(initial.centerX(), delete.visibleBounds.centerX())
            assertTrue(original.visibleBounds.right < initial.left)
            assertTrue(initial.bottom < delete.visibleBounds.top)
            assertTrue(device.hasObject(By.descStartsWith("翻译语言")))
            assertTrue(device.hasObject(By.descStartsWith("翻译服务密钥")))
            original.click()
            instrumentation.waitForIdleSync()
            assertTrue(state.showOriginal)
            translate.click() // Reuses the fake result; this state has no network/controller coroutine.
            instrumentation.waitForIdleSync()
            assertFalse(state.showOriginal)
            scenario.onActivity { visible.value = false }
            Thread.sleep(600)
            val shifted = device.findObject(By.desc("翻译邮件")).visibleBounds
            assertTrue(shifted.top > initial.top)
            scenario.onActivity { visible.value = true }
            Thread.sleep(600)
            assertEquals(initial.top, device.findObject(By.desc("翻译邮件")).visibleBounds.top)
            device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "inline-translation-controls.png"))
        }
    }
}
