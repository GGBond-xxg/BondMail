package com.bond.mail.ui.components

import com.bond.mail.data.mail.BrandMatcher
import com.bond.mail.data.model.ProviderRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderAvatarTest {
    @Test
    fun pickerProvidersResolveTheirBrandInsteadOfInitials() {
        mapOf("qq" to "qq.com", "gmail" to "gmail.com", "yahoo" to "yahoo").forEach { (id, key) ->
            val provider = ProviderRegistry.byId(id)
            assertEquals(key, BrandMatcher.match(provider.label, provider.avatarAddress()).key)
        }
    }
}
