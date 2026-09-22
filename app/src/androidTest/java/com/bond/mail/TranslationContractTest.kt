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
