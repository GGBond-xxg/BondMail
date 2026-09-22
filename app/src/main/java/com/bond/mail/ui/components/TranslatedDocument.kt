package com.bond.mail.ui.components

import org.jsoup.Jsoup

/** Translation is always escaped text. Original links are retained without trusting translated markup. */
internal fun translatedDocument(text: String, originalHtml: String?, originalText: String,
    bilingual: Boolean, translationLabel: String, originalLabel: String, linksLabel: String): String {
    val doc = Jsoup.parse("<html><body></body></html>")
    doc.body().appendElement("h3").text(translationLabel)
    doc.body().appendElement("div").attr("style", "white-space:pre-wrap;overflow-wrap:anywhere").text(text)
    val original = Jsoup.parse(originalHtml.orEmpty())
    val links = original.select("a[href]").filter {
        it.attr("href").startsWith("https://", true) || it.attr("href").startsWith("http://", true) || it.attr("href").startsWith("mailto:", true)
    }.distinctBy { it.attr("href") }
    if (links.isNotEmpty()) {
        doc.body().appendElement("h4").text(linksLabel)
        val list = doc.body().appendElement("ul")
        links.forEach { list.appendElement("li").appendElement("a").attr("href", it.attr("href")).text(it.text().ifBlank { it.attr("href") }) }
    }
    if (bilingual) {
        doc.body().appendElement("hr")
        doc.body().appendElement("h3").text(originalLabel)
        // The shared message renderer still sanitizes this document and controls remote images.
        if (originalHtml.isNullOrBlank()) doc.body().appendElement("div").attr("style", "white-space:pre-wrap").text(originalText)
        else original.body().childNodes().toList().forEach { doc.body().appendChild(it.clone()) }
    }
    return doc.outerHtml()
}
