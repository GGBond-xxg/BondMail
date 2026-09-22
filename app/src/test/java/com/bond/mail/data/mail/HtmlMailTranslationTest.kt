package com.bond.mail.data.mail

import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import com.bond.mail.ui.components.markIfastFooter

class HtmlMailTranslationTest {
    @Test fun preservesLayoutAttributesAndEscapesTranslatedText(): Unit = runBlocking {
        val source = "<html><head><style>td{color:red}</style></head><body><table width='600'><tr><td style='padding:20px'>Hello <b>world</b><a href='https://example.com/account'>Open</a><img src='cid:logo' width='80'></td></tr></table></body></html>"
        val result = translateHtmlMail("Subject", source, "") { "译文 <$it>" }
        val doc = Jsoup.parse(result.html)
        assertEquals("译文 <Subject>", result.subject)
        assertEquals("600", doc.selectFirst("table")!!.attr("width"))
        assertEquals("padding:20px", doc.selectFirst("td")!!.attr("style"))
        assertEquals("https://example.com/account", doc.selectFirst("a")!!.attr("href"))
        assertEquals("cid:logo", doc.selectFirst("img")!!.attr("src"))
        assertEquals("译文 <world>", doc.selectFirst("b")!!.text())
        assertTrue(doc.select("world").isEmpty())
        assertEquals("td{color:red}", doc.selectFirst("style")!!.data())
        assertTrue(source.contains("Hello <b>world</b>"))
    }

    @Test fun excludesHiddenScriptsAddressesAndDeduplicatesText(): Unit = runBlocking {
        val sent = mutableListOf<String>()
        val source = "<p>Hello</p><p>Hello</p><div hidden><b>Secret</b></div><span style='display:none'>Hidden</span><script>bad()</script><p translate='no'>Brand</p><p>help@example.com</p><p>https://example.com</p>"
        val result = translateHtmlMail("Hello", source, "") { sent += it; "你好" }
        assertEquals(listOf("Hello"), sent)
        val doc = Jsoup.parse(result.html)
        assertEquals(2, doc.select("p").count { it.text() == "你好" })
        assertEquals("Brand", doc.selectFirst("[translate=no]")!!.text())
    }

    @Test fun failureDoesNotProducePartialDocument(): Unit = runBlocking {
        var result: TranslatedHtmlMail? = null
        val failure = runCatching {
            result = translateHtmlMail("Subject", "<p>Fail</p>", "") {
                if (it == "Fail") throw TranslationFailure("translation_network") else "标题"
            }
        }.exceptionOrNull()
        assertNull(result)
        assertEquals("translation_network", failure?.message)
    }

    @Test fun plainMailRemainsEscapedAndKeepsNewlines(): Unit = runBlocking {
        val result = translateHtmlMail("", null, "Hello\n<script>plain text</script>") { "第一行\n第二行 <b>文字</b>" }
        val doc = Jsoup.parse(result.html)
        assertTrue(doc.select("script,b").isEmpty())
        assertEquals("white-space:pre-wrap", doc.selectFirst("div")!!.attr("style"))
        assertTrue(doc.selectFirst("div")!!.wholeText().contains("\n"))
    }

    @Test fun ifastFooterRepairTargetsFooterAndSurvivesTranslation(): Unit = runBlocking {
        val source = "<table><tr><td><p>Main body</p></td></tr><tr><td bgcolor='#f4f7fa'><font color='white'>iFAST Global Bank Limited</font><p>London E14 9SH</p><a href='https://ifastgb.com'>Website</a></td></tr></table>"
        val result = translateHtmlMail("Title", source, "", prepareDocument = ::markIfastFooter) { "译文" }
        val doc = Jsoup.parse(result.html)
        assertEquals(1, doc.select(".bondmail-ifast-footer").size)
        assertFalse(doc.select("td").first()!!.hasClass("bondmail-ifast-footer"))
        assertTrue(doc.select("td").last()!!.hasClass("bondmail-ifast-footer"))
        assertEquals("https://ifastgb.com", doc.selectFirst("a")!!.attr("href"))
    }
}
