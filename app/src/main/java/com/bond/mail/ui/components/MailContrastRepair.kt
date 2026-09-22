package com.bond.mail.ui.components

import org.jsoup.nodes.Document

/** iFAST's pale footer keeps white authored text after sender dark rules are disabled. */
internal fun markIfastFooter(document: Document) {
    val company = Regex("(?i)ifast\\s+global\\s+bank\\s+limited")
    document.body().getAllElements().filter { element ->
        element.ownText().length < 180 && company.containsMatchIn(element.ownText())
    }.forEach { companyElement ->
        val footer = (listOf(companyElement) + companyElement.parents()).firstOrNull {
            it.normalName() in setOf("td", "footer") || it.id().contains("footer", true) || it.className().contains("footer", true)
        } ?: companyElement.parent()
        footer?.addClass("bondmail-ifast-footer")
    }
}
