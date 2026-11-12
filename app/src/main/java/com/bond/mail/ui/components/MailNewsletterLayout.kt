package com.bond.mail.ui.components

import org.jsoup.nodes.Document

/** Repairs identified newsletter structures without changing their text, links or media. */
internal fun normalizeNewsletterColumns(document: Document) {
    // This template's mobile query still insists on a 360px canvas plus outer gutters.
    // A percentage max-width alone cannot constrain its auto-layout table in Chromium.
    document.select("table.table-main-gmail").filter { it.select("table.table-body-gmail").isNotEmpty() }
        .forEach { table ->
            table.attr("style", table.attr("style") + ";width:100%!important;table-layout:fixed!important;")
            table.select("table.table-body-gmail").forEach { inner ->
                inner.attr("style", inner.attr("style") + ";width:calc(100% - 40px)!important;")
            }
        }

    // HSBC wraps its entire component tree in an extra text row. Individual components already
    // have their own mobile gutters; retaining both leaves narrow headings and quote columns.
    document.select("td.text__row").filter {
        it.select("table.header").isNotEmpty() && it.select("table.hero-component").isNotEmpty()
    }.forEach { wrapper ->
        wrapper.attr("style", wrapper.attr("style") + ";padding-left:0!important;padding-right:0!important;")
    }

    // Paired square codes with captions need a full line per pair on phones. Do not treat ordinary
    // data tables, image-only social strips, or a single illustration as such a group.
    document.select("tr").toList().forEach { row ->
        val cells = row.children().filter { it.normalName() == "td" }
        if (cells.size !in 2..7) return@forEach
        val groups = cells.filter { cell ->
            val images = cell.select("img")
            if (images.size != 1 || cell.text().length !in 10..180) return@filter false
            val image = images.single()
            val width = newsletterPixels(image.attr("width")) ?: return@filter false
            val height = newsletterPixels(image.attr("height")) ?: return@filter false
            width in 72f..128f && height in 72f..128f && kotlin.math.abs(width - height) < 2f &&
                cell.select("table").isNotEmpty()
        }
        if (groups.size !in 2..3 || cells.any { it !in groups && (it.text().isNotBlank() || it.select("img").isNotEmpty()) }) {
            return@forEach
        }
        row.addClass("bondmail-caption-groups")
        row.attr("style", row.attr("style") + ";display:flex!important;flex-direction:column!important;gap:16px!important;")
        cells.forEach { cell ->
            cell.attr("style", cell.attr("style") + if (cell in groups) {
                ";display:block!important;width:100%!important;min-width:0!important;padding:0!important;"
            } else {
                ";display:none!important;"
            })
        }
    }
}

/** Generic fluid constraints must not replace a sender's smaller inline image/container limit. */
internal fun preserveNewsletterSizeLimits(document: Document) {
    document.select("[style]").forEach { element ->
        val maximum = NEWSLETTER_MAX_WIDTH.findAll(element.attr("style")).lastOrNull()
            ?.groupValues?.get(1)?.toFloatOrNull() ?: return@forEach
        if (maximum < 0f || maximum > 440f) return@forEach
        element.attr("style", element.attr("style") + ";max-width:min(100%,${maximum}px)!important;")
    }
    document.select("img").forEach { image ->
        val width = newsletterPixels(image.attr("width"))
        val height = newsletterPixels(image.attr("height"))
        if (width != null && width in 1f..128f) {
            image.attr("style", image.attr("style") + ";max-width:min(100%,${width}px)!important;height:auto!important;")
        } else if (width == null && height != null && height in 1f..96f &&
            image.attr("width").trim().let { it.isEmpty() || it.equals("auto", true) }) {
            // Logos authored by height only must not revert to their larger intrinsic bitmap size.
            image.attr("style", image.attr("style") + ";width:auto!important;height:auto!important;max-height:${height}px!important;")
        }
    }
}

private fun newsletterPixels(raw: String): Float? =
    raw.trim().removeSuffix("px").toFloatOrNull()

private val NEWSLETTER_MAX_WIDTH = Regex("(?i)(?:^|;)\\s*max-width\\s*:\\s*(\\d+(?:\\.\\d+)?)px\\s*(?:!important)?\\s*(?=;|$)")
