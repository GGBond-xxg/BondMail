package com.bond.mail.ui.components

import org.jsoup.nodes.Document

/** A display-only fallback for DITO templates with light text on a retained white canvas. */
internal fun repairDitoBodyReadability(document: Document, senderAddress: String, darkMode: Boolean) {
    val domain = senderAddress.substringAfterLast('@', "").substringBefore('>').trim().lowercase()
    if (!darkMode || !(domain == "dito.ph" || domain.endsWith(".dito.ph"))) return
    val body = document.body()
    // Avoid styling the body itself: BondMail adds its themed sender header later.
    body.textNodes().filter { it.text().isNotBlank() }.toList().forEach { it.wrap("<span></span>") }
    body.getAllElements().filter { element ->
        element !== body && element.ownText().isNotBlank() &&
            element.normalName() !in setOf("style", "script", "svg", "math", "noscript") &&
            element.parents().none { it.normalName() in setOf("svg", "math") }
    }.forEach { element ->
        val isLink = element.normalName() == "a" || element.parents().any { it.normalName() == "a" }
        val color = if (isLink) "#1267a5" else "#202124"
        // Inline !important also wins against sender inline light text and text-fill styles.
        // Only text-bearing elements receive a light plate; images/QR pixels remain untouched.
        element.attr("style", element.attr("style").trimEnd().trimEnd(';') +
            ";background-color:#ffffff!important;color:$color!important;" +
            "-webkit-text-fill-color:$color!important;text-shadow:none!important")
        element.attr("data-bondmail-readable-text", "true")
    }
}
