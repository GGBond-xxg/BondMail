package com.bond.mail

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bond.mail.data.mail.*
import com.bond.mail.ui.components.contactLogoSvgMarkup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bond.mail.ui.components.BodyTranslationDialog
import com.bond.mail.ui.i18n.JsonStringsProvider
import java.io.File

@RunWith(AndroidJUnit4::class)
class TranslationContractTest {
    @Test fun longTranslationKeepsActionsVisibleAndTranslatesSubject() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // MainActivity asynchronously installs its normal content during startup.
            instrumentation.waitForIdleSync()
            Thread.sleep(2000)
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        JsonStringsProvider("zh") {
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            androidx.compose.runtime.CompositionLocalProvider(
                                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.5f)
                            ) {
                                BodyTranslationDialog(null, "Long original body.\n".repeat(100), subject = "Interest update",
                                    onResult = { _, _, _ -> },
                                    translateText = { text, _, _ ->
                                        if (text == "Interest update") "利率更新" else "这是长邮件译文。\n".repeat(100)
                                    }) {}
                            }
                        }
                    }
                }
            }
            assertTrue(device.wait(Until.hasObject(By.text("简体中文 ▾")), 5000))
            val language = device.findObject(By.text("简体中文 ▾")).visibleBounds
            val provider = device.findObjects(By.textEndsWith(" ▾")).first { it.text != "简体中文 ▾" }.visibleBounds
            assertTrue("Selectors must share a row", language.top < provider.bottom && provider.top < language.bottom)
            device.findObjects(By.text("翻译邮件")).last().click()
            assertTrue(device.wait(Until.hasObject(By.text("利率更新")), 5000))
            listOf("译文", "对照", "原文", "在邮件中查看译文", "关闭").forEach {
                val bounds = device.findObject(By.text(it)).visibleBounds
                assertTrue("Visible action: $it", bounds.height() > 0 && bounds.top >= 0 && bounds.bottom <= device.displayHeight)
            }
            assertFalse(device.hasObject(By.clazz("android.widget.CheckBox")))
            device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "translation-fixed-preview.png"))
            device.findObject(By.text("原文")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Interest update")), 3000))
        }
    }

    @Test fun translationDialogShowsProviderAndLanguageWithoutSendingText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        JsonStringsProvider("zh") {
                            BodyTranslationDialog(null, "Hello. This is a local UI preview; no translation request is sent.") {}
                        }
                    }
                }
            }
            assertTrue(device.wait(Until.hasObject(By.text("简体中文 ▾")), 5000))
            assertTrue(device.hasObject(By.textContains("图片和附件不参与翻译")))
            device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "translation-body-preview.png"))
            // Never click Translate: this test must not use a configured user's paid API key.
        }
    }
    @Test fun parsesAllFourOfficialResponseShapes() {
        assertEquals("你好", parseTranslationResponse(TranslationProvider.ALIYUN,
            """{"Code":200,"Data":{"Translated":"你好"}}"""))
        assertEquals("你好", parseTranslationResponse(TranslationProvider.YOUDAO,
            """{"errorCode":"0","translation":["你好"]}"""))
        assertEquals("Hello & welcome", parseTranslationResponse(TranslationProvider.GOOGLE,
            """{"data":{"translations":[{"translatedText":"Hello &amp; welcome"}]}}"""))
        assertEquals("你好", parseTranslationResponse(TranslationProvider.MICROSOFT,
            """[{"translations":[{"text":"你好","to":"zh-Hans"}]}]"""))
    }

    @Test fun rejectsProviderErrorsWithoutEchoingResponse() {
        val error = runCatching { parseTranslationResponse(TranslationProvider.YOUDAO,
            """{"errorCode":"202","message":"sensitive body text"}""") }.exceptionOrNull()
        assertTrue(error is TranslationFailure)
        assertEquals("translation_auth_failed", error?.message)
    }

    @Test fun generatedDomainAliasesLoadIconsAndRespectBoundaries() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf("foxmail.com", "googlemail.com", "hey.com", "pm.me", "mail.hey.com").forEach {
            assertNotNull(it, contactLogoSvgMarkup(context, "Person", "person@$it"))
        }
        assertNull(contactLogoSvgMarkup(context, "Person", "person@nothey.com"))
    }
}
