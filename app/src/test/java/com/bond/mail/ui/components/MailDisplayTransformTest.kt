package com.bond.mail.ui.components

import com.bond.mail.data.settings.MailDisplayMode
import com.bond.mail.data.settings.SenderPresentationRules
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class MailDisplayTransformTest {
    @Test fun addressRulesOverrideDomainAndDoNotLeakToOtherDomains() {
        val values = mapOf("icon:domain:example.com" to "bank", "icon:email:one@example.com" to "sports")
        assertEquals("sports", SenderPresentationRules.resolve(values, "icon", "Name <ONE@EXAMPLE.COM>"))
        assertEquals("bank", SenderPresentationRules.resolve(values, "icon", "two@example.com"))
        assertNull(SenderPresentationRules.resolve(values, "icon", "@example.com"))
        assertNull(SenderPresentationRules.resolve(values, "icon", "one@notexample.com"))
        assertNull(SenderPresentationRules.resolve(values, "icon", "one@sub.example.com"))
        assertNull(SenderPresentationRules.resolve(values, "display", "one@example.com"))
        assertEquals("bank", SenderPresentationRules.resolve(values - "icon:email:one@example.com", "icon", "one@example.com"))
        assertNull(SenderPresentationRules.resolve(emptyMap(), "icon", "one@example.com"))
    }
    @Test fun plainTextRemovesExecutableAndHiddenMarkupAndKeepsParagraphs() {
        val result = Jsoup.parse(displayModeHtml("""<style>secret-css</style><script>secret-code</script>
            <p>Hello &lt;friend&gt;</p><p>Second<br>Third</p><span style="display: none">secret-preview</span>
            <img src="https://example.com/tracker"><div hidden>secret-hidden</div>""", MailDisplayMode.TEXT))
        assertTrue(result.selectFirst("pre")!!.wholeText().contains("Hello <friend>"))
        assertTrue(result.selectFirst("pre")!!.wholeText().contains("Second\nThird"))
        assertFalse(result.text().contains("secret"))
        assertTrue(result.select("img,script,style").isEmpty())
    }
    @Test fun otherModesPreserveInputBeforeSanitization() {
        val html = "<p style='color:red'>Original</p>"
        listOf(MailDisplayMode.AUTO, MailDisplayMode.ORIGINAL, MailDisplayMode.LIGHT).forEach {
            assertEquals(html, displayModeHtml(html, it))
        }
    }
    @Test fun lightCanvasKeepsImagesLinksAndHiddenContentHidden() {
        val doc = Jsoup.parse("""<div style="background:black;color:white"><p>Readable</p>
            <a href="https://example.com"><span>Link</span></a><img src="cid:qr" width="200">
            <svg><path d="M0 0" fill="white"/></svg><p id="hidden" style="display:none">Hidden</p></div>""")
        val image = doc.selectFirst("img")!!.outerHtml()
        val svg = doc.selectFirst("svg")!!.outerHtml()
        applyLightMailCanvas(doc)
        assertEquals(image, doc.selectFirst("img")!!.outerHtml())
        assertEquals(svg, doc.selectFirst("svg")!!.outerHtml())
        assertTrue(doc.selectFirst("a span")!!.attr("style").contains("color:#1267a5!important"))
        assertEquals("https://example.com", doc.selectFirst("a")!!.attr("href"))
        assertTrue(doc.getElementById("hidden")!!.attr("style").contains("display:none"))
        assertTrue(doc.selectFirst("p")!!.attr("style").contains("color:#202124!important"))
    }
}
