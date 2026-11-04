package com.bond.mail.data.mail

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** The editable note is separate from the original HTML, including when reopening a draft. */
internal object ForwardBody {
    private const val NOTE = "bondmail-forward-note"
    private const val ORIGINAL = "bondmail-forward-original"

    fun original(html: String?, sender: String, subject: String): String? {
        if (html.isNullOrBlank()) return null
        val document = Jsoup.parse(html)
        document.outputSettings().prettyPrint(false)
        val body = document.body()
        val original = Element("div").attr("id", ORIGINAL)
        val heading = Element("div").attr("style", "padding:16px 0;color:#202124;background:#fff;font:14px sans-serif")
        heading.appendElement("div").text("--- Forwarded message ---")
        heading.appendElement("div").text("From: $sender")
        heading.appendElement("div").text("Subject: $subject")
        original.appendChild(heading)
        val content = Element("div")
        body.attributes().forEach { content.attr(it.key, it.value) }
        body.childNodes().toList().forEach { it.remove(); content.appendChild(it) }
        original.appendChild(content)
        body.clearAttributes()
        body.appendChild(original)
        return document.outerHtml()
    }

    fun note(html: String?, fallback: String): String = if (html.isNullOrBlank()) fallback else {
        Jsoup.parse(html).body().children().firstOrNull { it.id() == NOTE }?.wholeText().orEmpty()
    }

    fun quoteText(html: String): String {
        val document = Jsoup.parse(html)
        document.body().children().filter { it.id() == NOTE }.forEach(Element::remove)
        document.select("style,script,head").remove()
        document.select("br").forEach { it.replaceWith(org.jsoup.nodes.TextNode("\n")) }
        document.select("td,th").forEach { it.appendText(" ") }
        document.select("div,p,tr,li,h1,h2,h3,h4,blockquote,section").forEach { it.appendText("\n") }
        return document.body().wholeText().replace(Regex("[ \t]+\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n").trim()
    }

    fun html(note: String, original: String?): String? {
        if (original.isNullOrBlank()) return null
        val document = Jsoup.parse(original)
        document.outputSettings().prettyPrint(false)
        document.body().children().filter { it.id() == NOTE }.forEach(Element::remove)
        document.body().prependChild(Element("div").attr("id", NOTE)
            .attr("style", "white-space:pre-wrap;padding:16px;color:#202124;background:#fff;font:16px sans-serif")
            .text(note))
        return document.outerHtml()
    }

    fun text(note: String, original: String?): String =
        if (original.isNullOrBlank()) note else listOf(note, quoteText(original)).filter(String::isNotBlank).joinToString("\n\n")
}
