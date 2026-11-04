package com.bond.mail.data.mail

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class ForwardBodyTest {
    private val original = """<html><head><style>.card{color:red}</style></head><body style="background:#123456"><table class="card"><tr><td>Hello <b>world</b><img src="https://example.com/logo.png"></td></tr></table></body></html>"""

    @Test fun roundTripDraftKeepsLayoutAndOnlyEditsNote() {
        val quoted = ForwardBody.original(original, "sender@example.com", "A < B")!!
        val sent = ForwardBody.html("My <note>\nSecond line", quoted)!!
        assertEquals("My <note>\nSecond line", ForwardBody.note(sent, "fallback"))
        val reopened = ForwardBody.html("Updated", sent)!!
        val doc = Jsoup.parse(reopened)
        assertEquals(1, doc.select("#bondmail-forward-note").size)
        assertEquals(1, doc.select("table.card").size)
        assertTrue(reopened.contains("background:#123456"))
        assertTrue(reopened.contains(".card{color:red}"))
        assertTrue(reopened.contains("https://example.com/logo.png"))
        assertFalse(reopened.contains("My &lt;note&gt;"))
        assertTrue(ForwardBody.text("Updated", sent).contains("Hello world"))
        assertFalse(ForwardBody.text("Updated", sent).contains(".card"))
        assertTrue(ForwardBody.text("Updated", sent).contains("From: sender@example.com\nSubject: A < B"))
    }

    @Test fun plainMailAndRemovingOriginalDoNotAcquireHtml() {
        assertNull(ForwardBody.original(null, "a", "b"))
        assertNull(ForwardBody.html("Edited", null))
        assertEquals("Edited", ForwardBody.text("Edited", null))
        assertEquals("plain", ForwardBody.note(null, "plain"))
    }

    @Test fun forwardingAnAlreadyForwardedMessageKeepsItsPreviousNote() {
        val first = ForwardBody.html("Earlier note", ForwardBody.original(original, "a", "b"))!!
        val second = ForwardBody.html("New note", ForwardBody.original(first, "c", "d"))!!
        assertEquals("New note", ForwardBody.note(second, ""))
        assertTrue(ForwardBody.quoteText(second).contains("Earlier note"))
    }
}
