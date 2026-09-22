package com.bond.mail.ui.components

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Only collapse explicit reply containers; ordinary blockquotes can be the actual message. */
internal fun collapseQuotedHistory(document: Document) {
    document.select(".gmail_quote, .yahoo_quoted, blockquote[type=cite]").toList().forEach { quote ->
        if (quote.parents().any { it.tagName() == "details" }) return@forEach
        val details = Element("details").addClass("bond-quoted-history")
        details.appendElement("summary").text("···")
        quote.before(details)
        details.appendChild(quote)
    }
}
