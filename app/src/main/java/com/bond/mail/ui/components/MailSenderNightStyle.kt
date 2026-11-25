package com.bond.mail.ui.components

import org.jsoup.nodes.Document
import java.net.URI

/** Require the actual wrapper, stacking and phone-width rules, not just a viewport declaration. */
internal fun hasCathayPhoneLayout(document: Document): Boolean {
    if (document.select("table.deviceWidth").isEmpty()) return false
    val css = document.select("style").joinToString("\n") { it.data() }
    val mobile = css.substringAfter("@media", "")
    return Regex("(?i)max-width\\s*:\\s*(?:620|480)px").containsMatchIn(mobile) &&
        Regex("(?is)\\.deviceWidth\\s*\\{[^}]*width\\s*:\\s*100%").containsMatchIn(mobile) &&
        Regex("(?is)\\.deviceWidthLarger\\s*\\{[^}]*width\\s*:\\s*100%").containsMatchIn(mobile) &&
        Regex("(?is)\\.fullWidthBlock\\s*\\{[^}]*display\\s*:\\s*block").containsMatchIn(mobile)
}

/** Verified transparent assets from the sender's own preview. Never classify by vague "icon"/"QR" hints. */
internal fun markCathayNightArtwork(document: Document) {
    document.select("[data-bondmail-night-artwork]").forEach { it.removeAttr("data-bondmail-night-artwork") }
    document.select("img[src]").forEach { image ->
        val uri = runCatching { URI(image.attr("src")) }.getOrNull() ?: return@forEach
        if (uri.host != "www.cathaypacific.com") return@forEach
        val style = when (uri.path) {
            "/content/dam/cx/applications/email/assets-lrp/member-icon.png",
            "/content/dam/cx/applications/email/assets-lrp/asiamiles.png" -> "monochrome"
            "/content/dam/cx/applications/email/header-lrp/cathay80-zh.png",
            "/content/dam/cx/applications/email/assets-lrp/status_points.png",
            "/content/dam/cx/applications/email/assets-lrp/link-arrow.png" -> "brand"
            else -> null
        }
        if (style != null) image.attr("data-bondmail-night-artwork", style)
    }
}
