package com.bond.mail.data.mail

import org.junit.Assert.assertEquals
import org.junit.Test

class GenericSenderCategoriesTest {
    @Test fun everyRegisteredDomainResolvesWithBoundaries() {
        GenericSenderCategories.entries.forEach { entry ->
            entry.domains.forEach { domain ->
                assertEquals(domain, entry.category, BrandMatcher.match("Notice", "notice@$domain").key)
                assertEquals(domain, entry.category, BrandMatcher.match("Notice", "notice@EMAIL.$domain".uppercase()).key)
                assertEquals(domain, "unknown", BrandMatcher.match("Notice", "notice@not$domain").key)
                assertEquals(domain, "unknown", BrandMatcher.match("Notice", "notice@$domain.example.org").key)
            }
        }
    }
    @Test fun knownNamesWorkWithoutOfficialDomainAndShortWordsStayBounded() {
        GenericSenderCategories.entries.forEach { entry ->
            (entry.names + entry.exactNames).forEach { name ->
                assertEquals(name, entry.category, BrandMatcher.match(name.uppercase(), "notice@example.org").key)
            }
        }
        listOf("Mind the gap", "Mango recipes", "Delivery vans", "M1 chip", "Giga bytes",
            "Converse with friends", "Brooks Smith", "Filament", "Bitunixx", "DBSCAN").forEach {
            assertEquals(it, "unknown", BrandMatcher.match(it, "notice@example.org").key)
        }
    }
    @Test fun explicitDomainsDisambiguateMayaAndPreserveDedicatedIcons() {
        assertEquals("bank", BrandMatcher.match("Maya", "no-reply@maya.ph").key)
        assertEquals("bank", BrandMatcher.match("Maya eSIM", "no-reply@mayabank.ph").key)
        assertEquals("simcard", BrandMatcher.match("Maya Bank", "no-reply@maya.net").key)
        assertEquals("simcard", BrandMatcher.match("Maya", "no-reply@mail.maya.net").key)
        assertEquals("bank", BrandMatcher.match("Maya", "notice@example.org").key)
        assertEquals("nike", BrandMatcher.match("Sports", "notice@nike.com").key)
        assertEquals("muji", BrandMatcher.match("Clothing", "notice@muji.com").key)
        assertEquals("za bank", BrandMatcher.match("Maya", "notice@za.group").key)
        assertEquals("exchange", BrandMatcher.match("Gemini", "notice@gemini.com").key)
        assertEquals("gemini", BrandMatcher.match("Gemini", "notice@google.com").key)
    }
}
