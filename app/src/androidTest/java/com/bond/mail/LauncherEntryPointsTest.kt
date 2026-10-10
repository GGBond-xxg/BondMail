package com.bond.mail

import android.appwidget.AppWidgetManager
import android.content.pm.ShortcutManager
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherEntryPointsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun installedLauncherShortcutsHaveExplicitSupportedTargets() {
        val entries = context.getSystemService(ShortcutManager::class.java).manifestShortcuts
        assertEquals(setOf("compose", "add_account", "refresh"), entries.map { it.id }.toSet())
        assertEquals(LauncherShortcut.entries.toSet(), entries.map { LauncherShortcut.fromAction(it.intent?.action) }.toSet())
        entries.forEach {
            assertTrue(it.isEnabled)
            assertFalse(it.shortLabel.isNullOrBlank())
            assertEquals(context.packageName, it.intent?.component?.packageName)
            assertEquals(MainActivity::class.java.name, it.intent?.component?.className)
        }
        assertNull(LauncherShortcut.fromAction(null))
        assertNull(LauncherShortcut.fromAction("com.bond.mail.shortcut.DELETE"))
    }

    @Test fun everyWidgetHasLoadableDistinctPickerArtworkAndLayout() {
        val providers = AppWidgetManager.getInstance(context).installedProviders.filter { it.provider.packageName == context.packageName }
        assertEquals(3, providers.size)
        assertEquals(3, providers.map { it.previewImage }.toSet().size)
        assertEquals(3, providers.map { it.previewLayout }.toSet().size)
        for (provider in providers) {
            assertNotEquals(0, provider.previewImage)
            val artwork = provider.loadPreviewImage(context, 0)
            assertNotNull(artwork)
            assertTrue(artwork!!.intrinsicWidth >= 400 && artwork.intrinsicHeight >= 400)
            assertNotEquals(provider.initialLayout, provider.previewLayout)
            instrumentation.runOnMainSync {
                val preview = RemoteViews(context.packageName, provider.previewLayout).apply(context, FrameLayout(context))
                assertTrue(preview is android.widget.ImageView)
                assertNotNull((preview as android.widget.ImageView).drawable)
            }
        }
    }
}
