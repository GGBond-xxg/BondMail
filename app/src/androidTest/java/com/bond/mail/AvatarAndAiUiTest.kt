package com.bond.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bond.mail.data.ai.*
import com.bond.mail.data.security.CredentialStore
import com.bond.mail.data.settings.*
import com.bond.mail.ui.DrawerAllMailboxesRow
import com.bond.mail.ui.components.*
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.screens.ContactItem
import com.bond.mail.ui.theme.BondMailTheme
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.lens
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AvatarAndAiUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device by lazy { UiDevice.getInstance(instrumentation) }

    @Test fun contactsDrawerReturnsToMailAndMailSearchStillCloses() {
        launchScenario().use { scenario ->
            waitForContent(scenario)
            assertTrue(device.wait(Until.hasObject(By.desc("Contacts")), 7000))
            click("Contacts", description = true)
            assertTrue(device.wait(Until.hasObject(By.desc("Search Contacts")), 5000))
            screenshot("contacts-live-header")
            click("Open navigation menu", description = true)
            assertTrue(device.wait(Until.hasObject(By.text("All Mailboxes")), 5000))
            click("Inbox")
            assertTrue(device.wait(Until.hasObject(By.desc("Search mail")), 5000))
            click("Search mail", description = true)
            assertTrue(device.wait(Until.hasObject(By.desc("Cancel")), 5000))
            screenshot("mail-search-inset")
            click("Cancel", description = true)
            assertTrue(device.wait(Until.hasObject(By.desc("Search mail")), 5000))
        }
    }

    @Test fun contactsHeaderSearchAndCloseInsetsAcrossStyles() {
        val container = (context.applicationContext as MailApplication).container
        var drawerOpens = 0
        var composeOpens = 0
        launchScenario().use { scenario ->
            waitForContent(scenario)
            UiStyle.entries.forEach { style ->
                stage("contacts-${style.name}")
                scenario.onActivity { activity -> activity.setContent {
                    key(style) {
                        val settings = AppSettings(uiStyle = style, themeMode = ThemeMode.LIGHT)
                        var chromeVisible by remember { mutableStateOf(true) }
                        BondMailTheme(settings) { JsonStringsProvider("en") {
                            com.bond.mail.ui.screens.ContactsScreen(
                                container, settings, onCompose = { composeOpens++ },
                                onOpenDrawer = { drawerOpens++ }, chromeVisible = chromeVisible,
                                onChromeVisibilityChanged = { chromeVisible = it },
                            )
                        } }
                    }
                } }
                settle()
                assertTrue(device.wait(Until.hasObject(By.desc("Open navigation menu")), 7000))
                click("Open navigation menu", description = true)
                assertEquals(style.ordinal + 1, drawerOpens)
                screenshot("contacts-header-${style.name}")
                click("Add Contact", description = true)
                assertTrue(device.wait(Until.hasObject(By.text("Contact Name")), 5000))
                click("Cancel")
                click("Search Contacts", description = true)
                assertTrue(device.wait(Until.hasObject(By.desc("Cancel")), 5000))
                val close = nodes().first { it.contentDescription?.toString() == "Cancel" }
                val closeBounds = android.graphics.Rect().also(close::getBoundsInScreen)
                val density = context.resources.displayMetrics.density
                // The X glyph must stay at least 20 dp inside the search surface's right edge.
                val surfaceRight = device.displayWidth - 12f * density
                assertTrue("Close icon too close to rounded edge: $style", surfaceRight - closeBounds.right >= 20f * density - 2)
                setText("no-contact-matches-this-7f92@example.invalid")
                assertTrue(device.wait(Until.hasObject(By.text("No matching results")), 5000))
                screenshot("contacts-search-${style.name}")
                click("Cancel", description = true) // Clear query, keep search open.
                assertTrue(device.hasObject(By.desc("Cancel")))
                assertFalse(device.hasObject(By.text("No matching results")))
                click("Cancel", description = true) // Empty query closes search.
                assertTrue(device.wait(Until.hasObject(By.desc("Search Contacts")), 5000))
                assertFalse(device.hasObject(By.desc("Cancel")))
            }
        }
        assertEquals(0, composeOpens)
    }

    @Test fun upstreamBackdropUpdateReleasesCoordinatesAndCompilesCornerShaders() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            listOf(com.kyant.backdrop.RoundedRectRefractionShaderString,
                com.kyant.backdrop.RoundedRectRefractionWithDispersionShaderString,
                com.kyant.backdrop.DefaultHighlightShaderString,
                com.kyant.backdrop.AmbientHighlightShaderString).forEach { android.graphics.RuntimeShader(it) }
        }
        val swapped = mutableStateOf(false)
        var oldSource: LayerBackdrop? = null
        var oldExport: LayerBackdrop? = null
        var newSource: LayerBackdrop? = null
        var newExport: LayerBackdrop? = null
        launchScenario().use { scenario ->
            waitForContent(scenario)
            scenario.onActivity { activity -> activity.setContent {
                val sourceA = rememberLayerBackdrop()
                val sourceB = rememberLayerBackdrop()
                val exportA = rememberLayerBackdrop()
                val exportB = rememberLayerBackdrop()
                SideEffect { oldSource = sourceA; oldExport = exportA; newSource = sourceB; newExport = exportB }
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.size(240.dp).layerBackdrop(if (swapped.value) sourceB else sourceA))
                    Box(Modifier.size(160.dp).drawPlainBackdrop(
                        backdrop = if (swapped.value) sourceB else sourceA,
                        shape = { androidx.compose.foundation.shape.RoundedCornerShape(8.dp, 24.dp, 40.dp, 16.dp) },
                        effects = { lens(12f, 20f) },
                        exportedBackdrop = if (swapped.value) exportB else exportA,
                    ))
                }
            } }
            settle()
            scenario.onActivity {
                assertNotNull(oldSource?.layerCoordinates)
                assertNotNull(oldExport?.layerCoordinates)
                swapped.value = true
            }
            settle()
            scenario.onActivity {
                assertNull("Replaced recording must not retain stale coordinates", oldSource?.layerCoordinates)
                assertNull("Replaced export must not retain stale coordinates", oldExport?.layerCoordinates)
                // Trigger a layout after changing the background providers.
                it.window.decorView.requestLayout()
            }
            settle()
            scenario.onActivity {
                assertNotNull(newSource)
                assertNotNull(newExport)
            }
        }
    }

    @Test fun avatarChoicesKeepLegacyCategoriesAndWholeEmoji() {
        listOf(null, "", "bank", "icon:government", "A", "😀", "🇲🇾", "👩🏽‍💻", "👨‍👩‍👧‍👦").forEach {
            assertTrue("Rejected $it", AvatarChoice.isValid(it))
        }
        listOf("AB", "😀😀", "a\nb", "icon:unknown").forEach { assertFalse(AvatarChoice.isValid(it)) }
        assertEquals("bank", AvatarChoice.category("bank"))
        assertEquals("bank", AvatarChoice.category("icon:bank"))
        assertNull(AvatarChoice.glyph("icon:bank"))
        assertEquals("👩🏽‍💻", AvatarChoice.glyph("👩🏽‍💻"))
        val rules = mapOf("icon:domain:example.test" to "icon:government", "icon:email:a@example.test" to "🇲🇾")
        assertEquals("🇲🇾", SenderPresentationRules.resolve(rules, "icon", "a@example.test"))
        assertEquals("icon:government", SenderPresentationRules.resolve(rules, "icon", "b@example.test"))
    }

    @Test fun savedContactsRoundTripCategoryEmojiAndRenamedContact() = kotlinx.coroutines.runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.bond.mail.data.db.MailDatabase::class.java).build()
        try {
            val dao = db.savedContactDao()
            val original = com.bond.mail.data.db.SavedContactEntity("avatar-test", "Original", "test@example.test", "icon:government", 1L, 1L)
            dao.upsert(original)
            assertEquals("icon:government", dao.byEmail("test@example.test")!!.avatarText)
            dao.upsert(original.copy(name = "Renamed", avatarText = "👩🏽‍💻", updatedAt = 2L))
            assertEquals("Renamed", dao.byId("avatar-test")!!.name)
            assertEquals("👩🏽‍💻", dao.byId("avatar-test")!!.avatarText)
            dao.upsert(original.copy(avatarText = null))
            assertNull(dao.byId("avatar-test")!!.avatarText)
        } finally { db.close() }
    }

    @Test fun aiActionsFitAcrossStylesAndEditorUsesShortLabels() {
        val prefsName = "avatar-ai-test-" + java.util.UUID.randomUUID()
        val isolated = object : android.content.ContextWrapper(context) {
            override fun getSharedPreferences(name: String?, mode: Int) = context.getSharedPreferences(prefsName, mode)
        }
        val store = CredentialStore(isolated)
        store.saveAiProfiles(AiProfiles(listOf(AiProfile("test", "Example Service", "deepseek", "",
            AiConfig(AiProvider.COMPATIBLE, "https://example.test/v1", "example-model", ""))), "test"))
        try {
            stage("launch")
        launchScenario().use { scenario ->
            stage("activity-started")
                waitForContent(scenario)
                UiStyle.entries.forEach { style ->
                    scenario.onActivity { activity -> activity.setContent {
                        BondMailTheme(AppSettings(uiStyle = style, themeMode = ThemeMode.LIGHT)) {
                            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.25f)) {
                                JsonStringsProvider("en") { AiSettingsScreen(store, onBack = {}, onOpenReplySkills = {}) }
                            }
                        }
                    } }
                    settle()
                    assertTrue(device.wait(Until.hasObject(By.text("Add Services")), 7000))
                    settle()
                    val add = device.findObject(By.text("Add Services")).visibleBounds
                    val skills = device.findObject(By.text("Reply Skills")).visibleBounds
                    assertEquals(skills.centerY(), add.centerY())
                    assertTrue(skills.right < add.left)
                    screenshot("ai-actions-${style.name}")
                }
                click("Add Services")
                assertTrue(device.wait(Until.hasObject(By.text("Provider Preset")), 5000))
                repeat(3) { if (!device.hasObject(By.text("Model"))) scrollForward() }
                assertTrue(device.hasObject(By.text("Model")))
                assertTrue(device.hasObject(By.text("Fetch Models")))
                assertEquals(device.findObject(By.text("Model")).visibleBounds.centerY(), device.findObject(By.text("Fetch Models")).visibleBounds.centerY())
                screenshot("ai-editor-new")
            }
        } finally { context.getSharedPreferences(prefsName, 0).edit().clear().commit() }
    }

    @Test fun contactAvatarLongPressOpensPickerAndSavesCategoryAndEmoji() {
        var composed = 0
        val selected = mutableStateOf<String?>("😀")
        stage("launch")
        launchScenario().use { scenario ->
            stage("activity-started")
            waitForContent(scenario)
            scenario.onActivity { activity -> activity.setContent {
                BondMailTheme(AppSettings(uiStyle = UiStyle.MATERIAL3, themeMode = ThemeMode.LIGHT)) {
                    JsonStringsProvider("en") {
                        var open by remember { mutableStateOf(false) }
                        Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                            ContactItem(name = "Example Contact", email = "test@example.test", customAvatarText = selected.value,
                                settings = AppSettings(), shape = MaterialTheme.shapes.large,
                                onCompose = { composed++ }, onEdit = { open = true })
                        }
                        if (open) AvatarChoiceDialog(selected.value, { selected.value = it; open = false }, { open = false })
                    }
                }
            } }
            assertTrue(device.wait(Until.hasObject(By.text("😀")), 7000))
            click("😀", long = true)
            assertTrue(device.wait(Until.hasObject(By.text("Choose Avatar")), 5000))
            assertEquals(0, composed)
            screenshot("avatar-picker-custom")
            click("Bank")
            click("Save")
            instrumentation.waitForIdleSync()
            assertEquals("icon:bank", selected.value)
            click("Edit Contact", description = true)
            assertTrue(device.wait(Until.hasObject(By.text("Choose Avatar")), 5000))
            screenshot("avatar-picker-grid")
            click("Custom Emoji or Character")
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "avatar-picker-input.xml"))
            screenshot("avatar-picker-input")
            setText("👩🏽‍💻")
            click("Save")
            instrumentation.waitForIdleSync()
            assertEquals("👩🏽‍💻", selected.value)
            assertEquals(0, composed)
        }
    }

    @Test fun allMailboxesLabelAndChevronShareOneToggleAction() {
        val selected = mutableStateOf(true)
        val expanded = mutableStateOf(false)
        val expandable = mutableStateOf(true)
        var toggles = 0
        launchScenario().use { scenario ->
            waitForContent(scenario)
            scenario.onActivity { activity -> activity.setContent {
                BondMailTheme(AppSettings()) { JsonStringsProvider("en") {
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        DrawerAllMailboxesRow(selected.value, expanded.value, expandable.value,
                            onToggleExpanded = { toggles++; expanded.value = !expanded.value })
                    }
                } }
            } }
            assertTrue(device.wait(Until.hasObject(By.text("All Mailboxes")), 7000))
            click("All Mailboxes")
            assertTrue(expanded.value)
            assertEquals(1, toggles)
            // The chevron belongs to the same clickable row, without a second button/ripple.
            assertEquals(1, nodes().count { it.isClickable && it.isEnabled })
            click("Collapse mailbox list", description = true)
            assertFalse(expanded.value)
            assertEquals(2, toggles)
            scenario.onActivity { selected.value = false }
            settle()
            click("All Mailboxes")
            assertTrue(expanded.value)
            assertEquals(3, toggles)
            assertFalse(selected.value)
            screenshot("drawer-header-expanded")
            scenario.onActivity { expandable.value = false }
            settle()
            assertFalse(nodes().any { it.isClickable && it.isEnabled })
            assertEquals(3, toggles)
        }
    }

    private fun launchScenario(): ActivityScenario<MainActivity> {
        // Xiaomi restricts synthetic input injection; these tests use the app's accessibility actions.
        // Bring the host forward explicitly before ActivityScenario, including after a previous test closes it.
        instrumentation.uiAutomation.executeShellCommand("am start -W -n com.bond.mail/.MainActivity").use {
            java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() }
        }
        return ActivityScenario.launch(MainActivity::class.java)
    }
    private fun nodes(): List<android.view.accessibility.AccessibilityNodeInfo> {
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        val result = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        fun collect(node: android.view.accessibility.AccessibilityNodeInfo?) {
            if (node == null) return
            result += node
            repeat(node.childCount) { collect(node.getChild(it)) }
        }
        instrumentation.uiAutomation.windows.forEach { collect(it.root) }
        if (result.isEmpty()) collect(instrumentation.uiAutomation.rootInActiveWindow)
        return result.filter { it.packageName?.toString() == context.packageName }
    }
    private fun click(label: String, description: Boolean = false, long: Boolean = false) {
        val action = if (long) android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK else android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
        var node = nodes().firstOrNull { (if (description) it.contentDescription else it.text)?.toString() == label }
        checkNotNull(node) { "Missing action: $label" }
        var done = false
        while (node != null && !done) { done = node.performAction(action); if (!done) node = node.parent }
        assertTrue("Action failed: $label", done)
        settle()
    }
    private fun setText(value: String) {
        val input = nodes().firstOrNull { node -> node.actionList.any { it.id == android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT } }
            ?: error("Avatar input has no set-text accessibility action")
        assertTrue(input.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
            android.os.Bundle().apply { putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }))
        settle()
    }
    private fun scrollForward() {
        nodes().firstOrNull { it.isScrollable }?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        settle()
    }

    private fun settle() {
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(450)
        device.waitForIdle()
    }
    private fun screenshot(name: String) {
        settle()
        assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "$name.png")))
    }
    private fun stage(name: String) { instrumentation.sendStatus(2, android.os.Bundle().apply { putString("stage", name) }) }
    private fun waitForContent(scenario: ActivityScenario<MainActivity>) {
        var ready = false
        val deadline = android.os.SystemClock.uptimeMillis() + 10_000
        while (!ready && android.os.SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { ready = MainActivity::class.java.getDeclaredField("contentInstalled").apply { isAccessible = true }.getBoolean(it) }
            if (!ready) Thread.sleep(100)
        }
        assertTrue(ready)
        stage("content-ready")
    }
}

