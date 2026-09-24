package com.bond.mail.ui.components

import com.bond.mail.data.mail.BrandMatcher
import com.bond.mail.data.model.ProviderRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderAvatarTest {
    @Test fun curatedMarksWinOverRawImportsEvenWithMoreSpecificFilenames() {
        val paths = contactLogoAssetPaths(listOf("notification.mexc.link", "mexc"))
        assertEquals(listOf("contact_logos/notification.mexc.link.svg", "contact_logos/mexc.svg"), paths.take(2))
        org.junit.Assert.assertTrue(paths.indexOf("contact_logos/mexc.svg") < paths.indexOf("contact_logos/local/notification.mexc.link.svg"))
        org.junit.Assert.assertTrue(paths.indexOf("contact_logos/local/mexc.svg") < paths.indexOf("contact_logos/thesvg/mexc.svg"))
    }

    @Test
    fun pickerProvidersResolveTheirBrandInsteadOfInitials() {
        mapOf("qq" to "qq.com", "gmail" to "gmail.com", "yahoo" to "yahoo", "163" to "163.com", "126" to "126.com", "outlook" to "outlook.com", "m365" to "outlook.com", "icloud" to "icloud").forEach { (id, key) ->
            val provider = ProviderRegistry.byId(id)
            assertEquals(key, BrandMatcher.match("", provider.avatarAddress()).key)
        }
    }
}
