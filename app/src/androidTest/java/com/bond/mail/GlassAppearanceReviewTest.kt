package com.bond.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.screens.AboutScreen
import com.bond.mail.ui.theme.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Captures production components in both themes for visual inspection, including opaque fallback. */
@RunWith(AndroidJUnit4::class)
class GlassAppearanceReviewTest {
    @Test fun reviewThemeSurfacesAndControlStates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        Configurator.getInstance().waitForIdleTimeout = 1_000
        val settings = mutableStateOf(AppSettings(uiStyle = UiStyle.LIQUID_GLASS, themeMode = ThemeMode.LIGHT))
        val about = mutableStateOf(false)
        val effects = mutableStateOf(true)
        var clicks = 0
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
                BondMailTheme(settings.value) { JsonStringsProvider("zh") {
                    CompositionLocalProvider(
                        LocalGlassEffects provides effects.value,
                        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                    ) {
                        if (about.value) AboutScreen({}, false, false, {}, {}, {}, {}, {}, {}, {})
                        else Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                            .statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Glass 控件检查", style = MaterialTheme.typography.titleLarge)
                            Column(Modifier.background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large).padding(12.dp)) {
                                GlassSettingDropdown("界面样式", null,
                                    listOf(0 to "Liquid Glass", 1 to "Material 3"), 0, {}, null)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassButton({ clicks++ }) { Text("普通按钮") }
                                GlassButton({ error("Disabled action clicked") }, enabled = false) { Text("禁用按钮") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassButton({}, primary = true) { Text("主要操作") }
                                GlassIconButton({}, Modifier, true) { BondIcon(GlassIcons.Compose, "写邮件") }
                                GlassIconButton({}, Modifier, false) { BondIcon(GlassIcons.Compose, "不可用") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                BondFilterChip(true, {}) { Text("已选择") }
                                BondFilterChip(false, {}) { Text("未选择") }
                            }
                            BondFormField("", {}, Modifier.fillMaxWidth(), label = { Text("服务地址") }, singleLine = true)
                            BondFormField("", {}, Modifier.fillMaxWidth(), label = { Text("输入错误") }, singleLine = true, isError = true)
                            BondFormField("", {}, Modifier.fillMaxWidth(), label = { Text("不可编辑") }, singleLine = true, enabled = false)
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                GlassSwitch(false, {}, Modifier, true)
                                GlassSwitch(true, {}, Modifier, true)
                            }
                            GlassMainDock(2, {}, {}, Modifier.fillMaxWidth())
                        }
                    }
                } }
            } }
            fun shot(name: String) {
                device.waitForIdle(1_000)
                assertTrue(device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")))
            }
            if (InstrumentationRegistry.getArguments().getString("aboutOnly") != "true")
            for (dark in listOf(false, true)) for (optics in listOf(true, false)) {
                instrumentation.runOnMainSync {
                    settings.value = settings.value.copy(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
                    effects.value = optics
                }
                checkNotNull(device.wait(Until.findObject(By.text("普通按钮")), 8_000)).click()
                device.waitForIdle(1_000)
                var count = 0
                instrumentation.runOnMainSync { count = clicks }
                checkNotNull(device.findObject(By.text("禁用按钮"))).click()
                device.waitForIdle(1_000)
                instrumentation.runOnMainSync { assertEquals(count, clicks) }
                shot("glass-controls-$dark-$optics")
                checkNotNull(device.findObject(By.text("Liquid Glass"))).click()
                assertTrue(device.wait(Until.hasObject(By.text("Material 3")), 5_000))
                shot("glass-menu-$dark-$optics")
                device.pressBack()
                device.swipe(680, 1400, 680, 450, 25)
                shot("glass-fields-$dark-$optics")
                device.swipe(680, 400, 680, 1450, 25)
            }
            for (style in UiStyle.entries) for (dark in listOf(false, true)) {
                instrumentation.runOnMainSync {
                    about.value = true
                    effects.value = true
                    settings.value = settings.value.copy(uiStyle = style, themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
                }
                // The title exists in both themes; wait for the newly composed frame, not just
                // the old title's accessibility node, before labelling the screenshot.
                android.os.SystemClock.sleep(500)
                assertTrue(device.wait(Until.hasObject(By.text("BondMail")), 8_000))
                shot("about-${style.name}-$dark")
            }
        }
    }
}
