package com.bond.mail.data.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BundledSenderBrandsTest {
    @Test fun allBundledBrandsResolveByDomainAndExactDisplayName() {
        BundledSenderBrands.entries.forEach { entry ->
            entry.domains.forEach { domain ->
                assertEquals(domain, entry.key, BrandMatcher.match("Notice", "notice@$domain").key)
                assertEquals(domain, entry.key, BrandMatcher.match("Notice", "notice@mail.$domain").key)
            }
            entry.names.forEach { name ->
                assertEquals(name, entry.key, BrandMatcher.match(name.uppercase(), "notice@example.org").key)
            }
        }
    }

    @Test fun brandRegistryRejectsLookalikeDomainsAndPartialNames() {
        BundledSenderBrands.entries.forEach { entry ->
            entry.domains.forEach { domain ->
                // A misspelled product subdomain still belongs to the real parent company.
                val parent = if (domain == "quickbooks.intuit.com") "intuit" else null
                assertEquals(domain, parent, BundledSenderBrands.byDomain("not$domain")?.key)
                assertNull(domain, BundledSenderBrands.byDomain("$domain.example.org"))
            }
            entry.names.forEach { name ->
                assertNull(name, BundledSenderBrands.byName("example $name sender"))
            }
        }
    }

    @Test fun officialDomainsWinOverUnrelatedDisplayNamesAndProductDomainsWinOverParents() {
        assertEquals("toobit", BrandMatcher.match("Google", "notice@toobit.com").key)
        assertEquals("blofin", BrandMatcher.match("Exchange", "notice@mail.blofin.com").key)
        assertEquals("tapbit", BrandMatcher.match("Exchange", "notice@tapbit.com").key)
        assertEquals("xt", BrandMatcher.match("Exchange", "notice@xt.com").key)
        assertEquals("ebay", BrandMatcher.match("Shopping", "notice@ebay.co.uk").key)
        assertEquals("quickbooks", BrandMatcher.match("Intuit", "notice@e.quickbooks.intuit.com").key)
        assertEquals("intuit", BrandMatcher.match("QuickBooks", "notice@intuit.com").key)
        assertEquals("wechat", BrandMatcher.match("QQ", "notice@weixin.qq.com").key)
    }

    @Test fun exchangeNamesDoNotMatchArbitraryWordsAddressesOrSubjectMentions() {
        listOf("Next", "Text", "Context", "BloFinch", "Toobitcoin", "Tapbitcoin").forEach { name ->
            assertEquals(name, "unknown", BrandMatcher.match(name, "notice@example.org").key)
        }
        assertEquals("unknown", BrandMatcher.match("Notice", "xt@example.org").key)
        assertEquals("unknown", BrandMatcher.match("Notice", "notice@example.org", "Toobit BloFin Tapbit XT.COM eBay").key)
    }
}
