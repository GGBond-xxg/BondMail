package com.bond.mail.ui

import com.bond.mail.data.db.MessageEntity
import org.jsoup.Jsoup

/** Prefer the richest readable body when providers expose a short plain-text alternative. */
internal fun MessageEntity.bestForwardText(): String {
    val htmlText = bodyHtml
        ?.takeIf(String::isNotBlank)
        ?.let { html -> runCatching { Jsoup.parse(html).text() }.getOrDefault("") }
        .orEmpty()
        .trim()
    return listOf(bodyText.trim(), htmlText)
        .maxByOrNull(String::length)
        .orEmpty()
}
