package com.bond.mail

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bond.mail.data.mail.BundledSenderBrands
import com.bond.mail.ui.components.contactLogoBitmap
import com.bond.mail.ui.components.contactLogoSvgMarkup
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BundledBrandAssetTest {
    @Test fun allNewBrandsRenderRealVectorsAndCorrectAliases() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val aliases = context.assets.open("contact_logos/domains.json").bufferedReader().use { JSONObject(it.readText()) }
        val failures = mutableListOf<String>()
        BundledSenderBrands.entries.chunked(52).forEachIndexed { sheetIndex, entries ->
            val sheet = Bitmap.createBitmap(1040, ((entries.size + 3) / 4) * 132, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            entries.forEachIndexed { index, entry ->
                entry.domains.forEach { assertEquals(it, entry.key, aliases.getString(it)) }
                val address = "icon-check@mail.${entry.domains.first()}"
                val markup = contactLogoSvgMarkup(context, "Notice", address)
                assertNotNull("${entry.key} vector is missing", markup)
                // An exact display name must select the same SVG when the domain is unknown.
                assertEquals(entry.key, markup, contactLogoSvgMarkup(context, entry.title, "icon-check@example.org"))
                val x = (index % 4) * 260f
                val y = (index / 4) * 132f
                for (dark in listOf(false, true)) {
                    val dx = x + if (dark) 130f else 0f
                    paint.color = if (dark) Color.rgb(26, 30, 40) else Color.rgb(238, 243, 250)
                    canvas.drawRect(dx + 4, y + 4, dx + 126, y + 96, paint)
                    val tint = if (dark) Color.rgb(177, 202, 255) else Color.rgb(40, 88, 173)
                    val icon = requireNotNull(contactLogoBitmap(context, "Notice", address, tint, 72)) { entry.key }
                    canvas.drawBitmap(icon, dx + 29, y + 14, null)
                    if (!dark) {
                        val pixels = IntArray(72 * 72)
                        icon.getPixels(pixels, 0, 72, 0, 0, 72, 72)
                        val coverage = pixels.count { Color.alpha(it) > 128 }.toFloat() / pixels.size
                        val maxCoverage = if (entry.key in setOf("canva", "eventbrite", "rippling", "runway")) .75f else .94f
                        if (coverage < .015f || coverage > maxCoverage) failures += "${entry.key}: coverage=$coverage"
                    }
                    icon.recycle()
                }
                paint.color = Color.BLACK
                paint.textSize = 16f
                canvas.drawText(entry.title, x + 8, y + 118, paint)
            }
            File(context.getExternalFilesDir(null), "brand-catalog-${sheetIndex + 1}.png").outputStream().use {
                sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            sheet.recycle()
        }
        assertTrue("Blank or opaque icons: $failures", failures.isEmpty())
    }
}
