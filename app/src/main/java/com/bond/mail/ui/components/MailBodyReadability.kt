package com.bond.mail.ui.components

import org.jsoup.nodes.Document
import kotlin.math.pow

/** Only repair explicit, almost indistinguishable light-on-light text on a known solid canvas. */
internal fun repairNearInvisibleNewsletterText(document: Document) {
    document.body().getAllElements().forEach { element ->
        if (element.ownText().isBlank() || element.normalName() in setOf("style", "script", "noscript")) return@forEach
        val ancestry = listOf(element) + element.parents()
        if (ancestry.any { node ->
                node.normalName() in setOf("svg", "math") || node.hasAttr("hidden") ||
                    node.attr("aria-hidden").equals("true", true) ||
                    INVISIBLE_TEXT_STYLE.containsMatchIn(node.attr("style")) ||
                    IMAGE_BACKGROUND.containsMatchIn(node.attr("style")) || node.hasAttr("background")
            }) return@forEach
        val foreground = inlineNewsletterColor(element.attr("style"), background = false)
            ?: parseNewsletterColor(element.attr("color")) ?: return@forEach
        val background = ancestry.firstNotNullOfOrNull { node ->
            inlineNewsletterColor(node.attr("style"), background = true)
                ?: parseNewsletterColor(node.attr("bgcolor"))
        } ?: return@forEach
        val foregroundLuminance = newsletterLuminance(foreground)
        val backgroundLuminance = newsletterLuminance(background)
        val ratio = (maxOf(foregroundLuminance, backgroundLuminance) + 0.05) /
            (minOf(foregroundLuminance, backgroundLuminance) + 0.05)
        // Leave normal muted text, brand colours, dark panels and button labels alone.
        if (foregroundLuminance > 0.75 && backgroundLuminance > 0.75 && ratio < 1.4) {
            element.attr("style", element.attr("style") + ";color:#555555!important;-webkit-text-fill-color:#555555!important;")
            element.attr("data-bondmail-readable-text", "true")
        }
    }
}

private fun inlineNewsletterColor(style: String, background: Boolean): Int? {
    val properties = if (background) setOf("background", "background-color") else setOf("color", "-webkit-text-fill-color")
    return style.split(';').mapNotNull { declaration ->
        if (declaration.substringBefore(':').trim().lowercase() !in properties) null
        else parseNewsletterColor(declaration.substringAfter(':').substringBefore('!').trim())
    }.lastOrNull()
}

private fun parseNewsletterColor(value: String): Int? {
    val color = value.trim().lowercase()
    if (color == "white") return 0xffffff
    if (color == "black") return 0
    if (Regex("#[0-9a-f]{6}").matches(color)) return color.drop(1).toInt(16)
    if (Regex("#[0-9a-f]{3}").matches(color)) return color.drop(1).flatMap { listOf(it, it) }.joinToString("").toInt(16)
    val rgb = Regex("rgb\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)").matchEntire(color) ?: return null
    val channels = rgb.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
    if (channels.any { it !in 0..255 }) return null
    return (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
}

private fun newsletterLuminance(color: Int): Double {
    fun channel(shift: Int): Double {
        val value = ((color shr shift) and 255) / 255.0
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    return channel(16) * 0.2126 + channel(8) * 0.7152 + channel(0) * 0.0722
}

private val INVISIBLE_TEXT_STYLE = Regex("(?i)(?:display\\s*:\\s*none|visibility\\s*:\\s*hidden|(?:^|;)\\s*(?:opacity|font-size|max-height)\\s*:\\s*0(?:px)?\\s*(?:!important)?\\s*(?:;|$))")
private val IMAGE_BACKGROUND = Regex("(?i)(?:url\\s*\\(|gradient\\s*\\()")

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
