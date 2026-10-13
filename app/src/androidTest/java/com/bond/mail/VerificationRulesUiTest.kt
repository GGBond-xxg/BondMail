package com.bond.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.bond.mail.data.mail.VerificationRuleStore
import com.bond.mail.data.mail.VerificationCodeDetector
import com.bond.mail.data.settings.*
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.screens.VerificationRulesScreen
import com.bond.mail.ui.theme.BondMailTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VerificationRulesUiTest {
    @Test fun exampleCanBeTestedSavedEditedAndDeletedAcrossThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        Configurator.getInstance().waitForIdleTimeout = 500
        val store = VerificationRuleStore(context)
        val original = store.rules()
        val settings = mutableStateOf(AppSettings(uiStyle = UiStyle.LIQUID_GLASS, themeMode = ThemeMode.LIGHT))
        fun find(text: String): UiObject2 = checkNotNull(device.wait(Until.findObject(By.text(text)), 8_000)) { text }
        fun scrollTo(text: String): UiObject2 {
            repeat(5) {
                if (device.hasObject(By.text(text))) return find(text)
                device.swipe(350, 1100, 350, 650, 20)
            }
            return find(text)
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var installed = false
                repeat(100) {
                    if (!installed) {
                        scenario.onActivity { activity -> installed = MainActivity::class.java.getDeclaredField("contentInstalled")
                            .apply { isAccessible = true }.getBoolean(activity) }
                        if (!installed) android.os.SystemClock.sleep(100)
                    }
                }
                assertTrue(installed)
                scenario.onActivity { it.setContent { BondMailTheme(settings.value) {
                    JsonStringsProvider("zh") { VerificationRulesScreen {} }
                } } }
                scrollTo("添加规则").click()
                find("填入示例").click()
                scrollTo("测试此规则").click()
                find("识别结果：482631")
                device.takeScreenshot(File(context.getExternalFilesDir(null), "verification-rule-tested.png"))
                find("保存").click()
                val deadline = android.os.SystemClock.uptimeMillis() + 5_000
                while (store.rules().size == original.size && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(100)
                val saved = VerificationRuleStore(context).rules().single { rule -> original.none { it.id == rule.id } }
                assertEquals("482631", VerificationCodeDetector.detect("Notice", "您的登录口令为：482631", rules = store.rules()))
                for (style in UiStyle.entries) for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                    scenario.onActivity { settings.value = AppSettings(uiStyle = style, themeMode = mode) }
                    scrollTo("编辑")
                    android.os.SystemClock.sleep(300)
                    device.takeScreenshot(File(context.getExternalFilesDir(null), "verification-rules-${style.name}-${mode.name}.png"))
                }
                find("编辑").click()
                find("填入示例")
                find("取消").click()
                find("删除").click()
                val deleteDeadline = android.os.SystemClock.uptimeMillis() + 5_000
                while (store.rules().any { it.id == saved.id } && android.os.SystemClock.uptimeMillis() < deleteDeadline) android.os.SystemClock.sleep(100)
                assertEquals(original, store.rules())
                runBlocking { store.save(original + saved.copy(id = "verification-qa-persist")) }
            }
        } catch (failure: Throwable) {
            device.takeScreenshot(File(context.getExternalFilesDir(null), "verification-rules-failure.png"))
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "verification-rules-failure.xml"))
            runBlocking { store.save(original) }
            throw failure
        }
    }
}

@RunWith(AndroidJUnit4::class)
class VerificationRulePersistenceTest {
    @Test fun rulesSurviveProcessRestart() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("verify_rule_restart") == "true")
        val store = VerificationRuleStore(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            assertNotNull(store.rules().firstOrNull { it.id == "verification-qa-persist" })
            assertEquals("482631", VerificationCodeDetector.detect("", "您的登录口令为：482631", rules = store.rules()))
        } finally { runBlocking { store.save(store.rules().filterNot { it.id == "verification-qa-persist" }) } }
    }
}
