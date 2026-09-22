package com.bond.mail.data.mail

import com.bond.mail.data.db.MailIndexRow
import com.bond.mail.ui.components.collapseQuotedHistory
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class MailProductivityTest {
    private fun row(id: String, account: String = "a", reply: String? = null, refs: String? = null) =
        MailIndexRow(id, account, "Same title", "sender@example.com", 0, "[]", "<$id>", reply, refs)

    @Test fun groupsRepliesAcrossMissingParentsButNotSubjectsOrAccounts() {
        val groups = conversationGroups(listOf(row("child", reply = "<missing>"), row("sibling", refs = "<missing>"),
            row("other"), row("child", account = "b", reply = "<missing>")))
        assertEquals(listOf(1, 1, 2), groups.map { it.size }.sorted())
        assertTrue(groups.all { it.map { row -> row.accountId }.distinct().size == 1 })
    }

    @Test fun handlesTransitiveAndCyclicReferencesWithoutLooping() {
        assertEquals(1, conversationGroups(listOf(row("a", reply = "<b>"), row("b", reply = "<c>"), row("c", reply = "<a>"))).size)
        assertEquals(setOf("<a@b>"), messageIds("garbage <a@b> <bad id>"))
    }

    @Test fun searchBindsUserInputAndCombinesFilters() {
        val query = mailSearchQuery("account", "from:x@example.com after:2026-01-01 before:2026-02-01 has:attachment 中文%'", 100)
        assertFalse(query.sql.contains("example.com"))
        assertFalse(query.sql.contains("中文"))
        assertTrue(query.sql.contains("hasAttachments = 1"))
        assertTrue(query.sql.contains("receivedAt >= ?"))
        assertEquals(10, query.argCount)
    }

    @Test fun translationMarkupEscapesTextAndRetainsOnlySafeOriginalLinks() {
        val html = com.bond.mail.ui.components.translatedDocument("<script>bad()</script>\n中文", "<a href='https://example.com'>Open</a><a href='javascript:bad()'>Bad</a>", "", false, "Translation", "Original", "Links")
        val doc = Jsoup.parse(html)
        assertTrue(doc.select("script").isEmpty())
        assertEquals(1, doc.select("a").size)
        assertEquals("https://example.com", doc.selectFirst("a")!!.attr("href"))
        assertTrue(doc.text().contains("中文"))
    }

    @Test fun collapsingQuotesRetainsOrdinaryQuotesAndMessageContent() {
        val doc = Jsoup.parse("<p>New</p><blockquote>Actual message</blockquote><div class=gmail_quote><blockquote type=cite>Old</blockquote></div>")
        collapseQuotedHistory(doc)
        assertEquals(1, doc.select("details").size)
        assertEquals("Old", doc.selectFirst("details blockquote")!!.text())
        assertEquals("Actual message", doc.selectFirst("body > blockquote")!!.text())
        assertTrue(doc.text().contains("New"))
    }
}
