package com.bond.mail

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bond.mail.data.db.*
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.i18n.loadJsonStrings
import com.bond.mail.widget.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MailWidgetTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun accountIsolationUnreadCountsPrivacyAndDeletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        try {
            for (id in listOf("a", "b")) db.accountDao().upsert(AccountEntity(id, "custom", "$id@example.test", id, authType = "PASSWORD", createdAt = 0))
            val rows = (1..9).map { i -> MessageEntity("a$i", "a", "INBOX", "INBOX", i.toLong(), senderName = "Sender $i", senderAddress = "sender@example.test",
                subject = "private subject $i", preview = "private preview $i", bodyText = "body must not be loaded", bodyHtml = "<p>body</p>", receivedAt = i.toLong(), unread = true, starred = false, hasAttachments = false) }
            db.messageDao().upsertAll(rows + rows[0].copy(id = "b1", accountId = "b", subject = "other account"))
            val config = WidgetConfig("a")
            val first = widgetSnapshot(db, config)
            assertEquals(9, first.unread)
            assertEquals(6, first.rows.size)
            assertEquals("a9", first.rows.first().id)
            assertTrue(first.rows.all { it.id.startsWith("a") })
            assertEquals(1, widgetSnapshot(db, WidgetConfig("b")).unread)
            db.messageDao().setUnread("a9", false)
            assertEquals(8, widgetSnapshot(db, config).unread)
            db.messageDao().deleteById("a8")
            assertEquals(7, widgetSnapshot(db, config).unread)
            val hidden = widgetSnapshot(db, config.copy(privacy = WidgetPrivacy.HIDE_CONTENT))
            assertTrue(hidden.rows.all { it.subject.isEmpty() && it.preview.isEmpty() })
            val noPreview = widgetSnapshot(db, config.copy(privacy = WidgetPrivacy.HIDE_PREVIEW))
            assertTrue(noPreview.rows.all { it.preview.isEmpty() && it.subject.isNotEmpty() })
            val private = widgetSnapshot(db, config.copy(sender = false, time = false, privacy = WidgetPrivacy.HIDE_CONTENT))
            assertTrue(private.rows.all { it.sender.isEmpty() && it.time == null && it.subject.isEmpty() && it.preview.isEmpty() })
            db.accountDao().deleteById("a")
            val deleted = widgetSnapshot(db, config)
            assertFalse(deleted.valid); assertTrue(deleted.rows.isEmpty()); assertEquals(0, deleted.unread)
            assertTrue(widgetSnapshot(db, WidgetConfig("b")).valid)
        } finally { db.close() }
    }

    @Test fun rendersAllThemesSizesAndScaledFontsWithoutPrivatePayloads() {
        val sizes = listOf(SizeF(110f,110f), SizeF(160f,170f), SizeF(320f,190f), SizeF(320f,350f), SizeF(270f,395f))
        val mail = (1..6).map { WidgetMail("fixture-$it", "Alex Rivera $it", "Design review tomorrow", "Please review the updated design", 1720000000000, it % 2 == 1) }
        val snapshot = WidgetSnapshot("Personal", "alex@example.test", true, 12, mail)
        for (preview in listOf(false, true)) for (scale in listOf(1f, 1.5f, 2f)) for (style in UiStyle.entries) for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) for ((index,size) in sizes.withIndex()) {
            val scaledContext = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = scale })
            val renderer = WidgetRenderer(scaledContext, loadJsonStrings(scaledContext, "en"))
            val config = WidgetConfig("fixture", style, mode, preview = preview)
            val views = renderer.render(-1, config, snapshot, size, false)
            instrumentation.runOnMainSync {
                val host = FrameLayout(scaledContext)
                val view = views.apply(scaledContext, host)
                val density = scaledContext.resources.displayMetrics.density
                val width = (size.width * density).toInt(); val height = (size.height * density).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                view.layout(0,0,width,height)
                assertTrue("Status remains visible: $style $mode $size $scale", view.findViewById<View>(R.id.widget_status).let { it.visibility != View.VISIBLE || (it.top >= 0 && it.height > 0) })
                val refresh = view.findViewById<View>(R.id.widget_refresh)
                val compose = view.findViewById<View>(R.id.widget_compose)
                if (refresh.visibility == View.VISIBLE && compose.visibility == View.VISIBLE) assertTrue(refresh.right < compose.left)
                val rows = view.findViewById<ViewGroup>(R.id.widget_rows)
                if (rows.visibility == View.VISIBLE && rows.childCount > 0) {
                    assertTrue("Last row clipped: $style $mode $size $scale", rows.getChildAt(rows.childCount - 1).bottom <= rows.height)
                }
                if (style == UiStyle.LIQUID_GLASS) {
                    val background = (view.findViewById<android.widget.ImageView>(R.id.widget_background).drawable as android.graphics.drawable.BitmapDrawable).bitmap
                    val center = background.getPixel(background.width / 2, background.height / 2)
                    assertTrue("Glass must reveal wallpaper", android.graphics.Color.alpha(center) in 1..150)
                    assertTrue("Glass must not contain a colored backdrop", kotlin.math.abs(android.graphics.Color.red(center) - android.graphics.Color.blue(center)) <= 2)
                }
                val content = view.findViewById<View>(R.id.widget_content)
                val status = view.findViewById<View>(R.id.widget_status)
                assertTrue("Status outside content", status.visibility != View.VISIBLE || status.parent.let { it as View }.bottom <= content.height)
                val count = view.findViewById<View>(R.id.widget_count)
                if (count.isShown || widgetLayout(size.width,size.height,scale,true).compact) {
                    assertTrue("Count clipped: $style $mode $size $scale", count.height > 0)
                }
                if (!preview && (scale == 1f || (scale == 2f && index == 0))) {
                    val bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    File(context.getExternalFilesDir(null), "widget-${style.name}-${mode.name}-$index-$scale.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                    bitmap.recycle()
                }
                val safe = snapshot // Renderer also guards against an accidentally unsanitized caller.
                val hidden = renderer.render(-1, config.copy(privacy = WidgetPrivacy.HIDE_CONTENT), safe, size, false).apply(scaledContext,host)
                fun allText(v: View): String = (if(v is TextView) v.text.toString() else "") + v.contentDescription?.toString().orEmpty() +
                    (if(v is ViewGroup) (0 until v.childCount).joinToString { allText(v.getChildAt(it)) } else "")
                assertFalse(allText(hidden).contains("Design review"))
                assertFalse(allText(hidden).contains("Please review"))
            }
        }
    }

    @Test fun independentSettingsRoundTripAndCleanup() {
        val store = WidgetStore(context)
        try {
            val a = WidgetConfig("a", UiStyle.LIQUID_GLASS, ThemeMode.DARK, WidgetPrivacy.HIDE_CONTENT, sender = false)
            val b = WidgetConfig("b", UiStyle.MIUIX, ThemeMode.LIGHT, preview = false)
            store.save(-9001,a); store.save(-9002,b)
            assertEquals(a, WidgetStore(context).get(-9001)); assertEquals(b, store.get(-9002))
            store.delete(-9001); assertNull(store.get(-9001)); assertEquals(b,store.get(-9002))
            assertTrue(widgetLayout(320f,350f,2f,true).rows < widgetLayout(320f,350f,1f,true).rows)
        } finally { store.delete(-9001); store.delete(-9002) }
    }
}
