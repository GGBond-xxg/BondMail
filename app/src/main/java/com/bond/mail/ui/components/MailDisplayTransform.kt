package com.bond.mail.ui.components

import com.bond.mail.data.settings.MailDisplayMode
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

internal fun displayModeHtml(html: String, mode: MailDisplayMode): String {
    if (mode != MailDisplayMode.TEXT) return html
    val source = Jsoup.parse(html)
    source.select("script,style,noscript,template,iframe,object,svg").remove()
    source.select("[hidden],[aria-hidden=true]").remove()
    source.select("[style]").filter {
        Regex("(?i)(display\\s*:\\s*none|visibility\\s*:\\s*hidden)").containsMatchIn(it.attr("style"))
    }.forEach { it.remove() }
    val plain = Document.createShell("")
    plain.body().appendElement("pre").attr("style", "white-space:pre-wrap;overflow-wrap:anywhere;font-family:inherit").text(source.body().wholeText())
    return plain.outerHtml()
}

internal fun applyLightMailCanvas(document: Document) {
    // Apply only to the message before app chrome is inserted. Images and QR pixels stay intact.
    document.body().getAllElements().filter { element ->
        element.normalName() !in setOf("img", "svg", "path", "style", "script") &&
            element.parents().none { it.normalName() == "svg" }
    }.forEach { element ->
        val color = if (element.normalName() == "a" || element.parents().any { it.normalName() == "a" }) "#1267a5" else "#202124"
        element.attr("style", element.attr("style").trimEnd(';') +
            ";background-color:#fff!important;background-image:none!important;color:$color!important;-webkit-text-fill-color:$color!important;text-shadow:none!important")
    }
}
