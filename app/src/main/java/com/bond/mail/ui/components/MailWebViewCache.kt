package com.bond.mail.ui.components

import android.util.LruCache
import com.bond.mail.data.mail.MailAttachmentCodec
import com.bond.mail.data.mail.MailAttachmentInfo
import com.bond.mail.data.mail.MailLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.net.URI

internal data class MailWebHeader(
    val subject: String,
    val senderName: String,
    val senderAddress: String,
    val recipient: String,
    val dateLabel: String,
    val avatarText: String,
    val customAvatarText: String? = null,
    val avatarSvg: String? = null,
    val monetBrandIcons: Boolean = true,
    val attachments: List<MailAttachmentInfo> = emptyList(),
)

internal enum class MailDocumentLayout {
    FLUID,
    DESKTOP_SCALED,
}

internal enum class MailContentHeightHint {
    SHORT,
    LONG,
}

internal data class PreparedMailDocument(
    val html: String,
    val hasRemoteImages: Boolean,
    val baseUrl: String?,
    val layout: MailDocumentLayout,
    val desktopCanvasWidthPx: Int? = null,
    val textZoomPercent: Int = 100,
    val contentHeightHint: MailContentHeightHint = MailContentHeightHint.LONG,
)

private data class DesktopCanvasCandidate(
    val width: Int,
    val score: Int,
    val hardWidthScore: Int,
)

/** Caches the sanitized local HTML document. Message bodies themselves are persisted in Room. */
internal object MailWebViewCache {
    private val documents = LruCache<String, PreparedMailDocument>(64)

    /**
     * Returns a prepared document already present in the in-memory LRU without dispatcher hops.
     *
     * Re-entering the last opened message previously showed the loading document for one or two
     * frames even though both the sanitized HTML and the rasterized WebView page were available.
     * A synchronous memory hit lets Compose attach the retained page during its first frame.
     */
    fun peekPreparedDocument(
        key: String,
        header: MailWebHeader,
        foregroundCss: String,
        backgroundCss: String,
        linkCss: String,
        mutedCss: String,
        headerSurfaceCss: String,
        avatarBackgroundCss: String,
        avatarForegroundCss: String,
        darkMode: Boolean,
        topContentInsetCssPx: Int,
        subjectBlockHeightCssPx: Int,
        subjectFontSizeSp: Float,
        subjectLineHeightSp: Float,
        senderBlockHeightCssPx: Int,
        viewportWidthCssPx: Int,
        fontScale: Float,
    ): PreparedMailDocument? = synchronized(this) {
        documents.get(
            preparedDocumentCacheKey(
                key = key,
                header = header,
                foregroundCss = foregroundCss,
                backgroundCss = backgroundCss,
                linkCss = linkCss,
                mutedCss = mutedCss,
                headerSurfaceCss = headerSurfaceCss,
                avatarBackgroundCss = avatarBackgroundCss,
                avatarForegroundCss = avatarForegroundCss,
                darkMode = darkMode,
                topContentInsetCssPx = topContentInsetCssPx,
                subjectBlockHeightCssPx = subjectBlockHeightCssPx,
                subjectFontSizeSp = subjectFontSizeSp,
                subjectLineHeightSp = subjectLineHeightSp,
                senderBlockHeightCssPx = senderBlockHeightCssPx,
                viewportWidthCssPx = viewportWidthCssPx,
                fontScale = fontScale,
            ),
        )
    }

    suspend fun preparedDocument(
        key: String,
        html: String,
        header: MailWebHeader,
        foregroundCss: String,
        backgroundCss: String,
        linkCss: String,
        mutedCss: String,
        headerSurfaceCss: String,
        avatarBackgroundCss: String,
        avatarForegroundCss: String,
        darkMode: Boolean,
        topContentInsetCssPx: Int,
        subjectBlockHeightCssPx: Int,
        subjectFontSizeSp: Float,
        subjectLineHeightSp: Float,
        senderBlockHeightCssPx: Int,
        viewportWidthCssPx: Int,
        fontScale: Float,
    ): PreparedMailDocument = withContext(Dispatchers.Default) {
        preparedDocumentBlocking(
            key = key,
            html = html,
            header = header,
            foregroundCss = foregroundCss,
            backgroundCss = backgroundCss,
            linkCss = linkCss,
            mutedCss = mutedCss,
            headerSurfaceCss = headerSurfaceCss,
            avatarBackgroundCss = avatarBackgroundCss,
            avatarForegroundCss = avatarForegroundCss,
            darkMode = darkMode,
            topContentInsetCssPx = topContentInsetCssPx,
            subjectBlockHeightCssPx = subjectBlockHeightCssPx,
            subjectFontSizeSp = subjectFontSizeSp,
            subjectLineHeightSp = subjectLineHeightSp,
            senderBlockHeightCssPx = senderBlockHeightCssPx,
            viewportWidthCssPx = viewportWidthCssPx,
            fontScale = fontScale,
        )
    }

    @Synchronized
    private fun preparedDocumentBlocking(
        key: String,
        html: String,
        header: MailWebHeader,
        foregroundCss: String,
        backgroundCss: String,
        linkCss: String,
        mutedCss: String,
        headerSurfaceCss: String,
        avatarBackgroundCss: String,
        avatarForegroundCss: String,
        darkMode: Boolean,
        topContentInsetCssPx: Int,
        subjectBlockHeightCssPx: Int,
        subjectFontSizeSp: Float,
        subjectLineHeightSp: Float,
        senderBlockHeightCssPx: Int,
        viewportWidthCssPx: Int,
        fontScale: Float,
    ): PreparedMailDocument {
        val cacheKey = preparedDocumentCacheKey(
            key = key,
            header = header,
            foregroundCss = foregroundCss,
            backgroundCss = backgroundCss,
            linkCss = linkCss,
            mutedCss = mutedCss,
            headerSurfaceCss = headerSurfaceCss,
            avatarBackgroundCss = avatarBackgroundCss,
            avatarForegroundCss = avatarForegroundCss,
            darkMode = darkMode,
            topContentInsetCssPx = topContentInsetCssPx,
            subjectBlockHeightCssPx = subjectBlockHeightCssPx,
            subjectFontSizeSp = subjectFontSizeSp,
            subjectLineHeightSp = subjectLineHeightSp,
            senderBlockHeightCssPx = senderBlockHeightCssPx,
            viewportWidthCssPx = viewportWidthCssPx,
            fontScale = fontScale,
        )
        documents.get(cacheKey)?.let { return it }

        val document = Jsoup.parse(html)
        // Record responsive intent before replacing the sender's viewport declaration. Many
        // transactional messages keep a 600 px fallback table for Outlook but include real phone
        // media rules for modern clients. Those rules should win: scaling the fallback canvas again
        // is what made Binance text much smaller than Apple Mail.
        val responsiveMediaRules = hasResponsiveMediaRules(document)
        val responsiveMarkup = hasResponsiveMarkup(document)
        document.outputSettings().prettyPrint(false)
        document.select("script, iframe, object, embed, form").remove()
        document.select("meta[http-equiv~=(?i)content-security-policy]").remove()
        document.select("meta[http-equiv~=(?i)refresh]").remove()
        // Let Android WebView's algorithmic darkening decide how to transform light-only mail.
        // A number of newsletter templates incorrectly declare dark support while still using
        // black inline text, which prevents WebView from making the message readable.
        document.select("meta[name=color-scheme], meta[name=supported-color-schemes]").remove()
        // WebView's prefers-color-scheme follows the device configuration, not BondMail's
        // user-selected Compose theme. Disable sender dark media rules in both app themes so a
        // LIGHT BondMail window cannot receive a partial black canvas merely because ColorOS
        // entered night mode. BondMail applies its own complete dark transformation below.
        disableSenderDarkMode(document)
        trimTrailingNonVisualSections(document)

        val senderDomain = header.senderAddress.substringAfterLast('@', "").lowercase()
        val senderIdentity = "${header.senderName} ${header.senderAddress}".lowercase()
        val zaBankSender = isZaBankSender(senderDomain)
        val forceTransactionalFluid =
            isKnownMobileTransactionalSender(senderDomain) || zaBankSender
        val binanceSender = isBinanceSender(senderDomain)
        val bybitSender = senderDomain.contains("bybit.com") ||
            senderIdentity.contains("bybit")
        val bochkSender = senderDomain.contains("bochk.com") ||
            senderIdentity.contains("bochk") ||
            senderIdentity.contains("bank of china hong kong")
        val appleSender = senderDomain == "apple.com" ||
            senderDomain.endsWith(".apple.com")
        val samsungSender = senderDomain.contains("samsung") ||
            senderIdentity.contains("samsung")
        val oslSender = senderDomain == "osl.com" ||
            senderDomain.endsWith(".osl.com") ||
            senderIdentity.contains("osl global")
        val instagramSender = senderDomain.contains("instagram.com") ||
            senderIdentity.contains("instagram")
        val immigrationSender = senderDomain == "imigrasi.go.id" ||
            senderDomain.endsWith(".imigrasi.go.id")
        val cloudflareSender = senderDomain == "cloudflare.com" ||
            senderDomain.endsWith(".cloudflare.com")
        val grabSender = isGrabTransactionalSender(senderDomain, senderIdentity)
        val facebookSender = isFacebookSender(senderDomain, senderIdentity)
        val forceKnownSenderResponsive = binanceSender && responsiveMediaRules
        // Grab and Cloudflare's current templates advertise responsive rules but still paint their
        // hero/text on a roughly 600px canvas. Before remote media styles settle, FLUID briefly
        // enlarges that canvas past the phone and clips its right edge. Preserve the original
        // geometry and scale the complete canvas from the very first committed frame.
        val preferDesktopCanvas =
            (binanceSender && !forceKnownSenderResponsive) || grabSender || cloudflareSender

        // Production mailers frequently keep the real icon/image URL in data-src/data-original
        // while leaving a transparent tracking pixel in src. WebView does not execute the sender's
        // lazy-loading JavaScript, so promote those safe URL attributes before rendering.
        normalizeImageSources(document)
        if (appleSender) {
            replaceAppleBrandLogo(document)
        }
        markRemoteImagesAsync(document)
        normalizeKnownBrandLogoImages(document)
        if (samsungSender) {
            markSamsungLogoImages(document)
        }
        if (darkMode) {
            markDarkModeLogoImages(document)
        }
        enhanceKnownSenderBranding(
            document = document,
            isFacebookSender = facebookSender,
        )
        markHorizontalIconRows(document)
        markHorizontalIconStrips(
            document = document,
            aggressive = binanceSender,
        )
        val baseUrl = resolveBaseUrl(document)
        val hasRemoteImages = containsRemoteImages(document.outerHtml(), document)

        document.head().select("meta[name=viewport]").remove()
        document.head().prepend(
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, minimum-scale=0.75, maximum-scale=5.0, user-scalable=yes\" />",
        )

        val body = document.body()
        if (darkMode) {
            body.addClass("bondmail-dark-mode")
            val nativeDarkCanvas = hasNativeDarkCanvas(document)
            if (nativeDarkCanvas) {
                body.addClass("bondmail-native-dark-mail")
            }
            if (bybitSender || bochkSender || appleSender || oslSender) {
                // This template becomes only partially dark in Android WebView: its canvas turns
                // black while some panels, brand marks, or inline text keep the light palette.
                // Preserve the complete authored light message instead of showing a split canvas.
                body.addClass("bondmail-force-light-mail")
            }
        }
        if (samsungSender) {
            body.addClass("bondmail-samsung-mail")
        }
        if (appleSender) {
            body.addClass("bondmail-apple-mail")
        }
        if (darkMode && immigrationSender) {
            body.addClass("bondmail-repair-inherited-light-text")
        }
        normalizeEmojiPresentation(document)
        // Preserve direct text nodes from malformed/plain HTML messages while still giving them
        // normal paragraph layout.
        body.childNodes().toList().forEach { node ->
            if (node is TextNode && node.text().isNotBlank()) {
                val paragraph = Element("p").addClass("bondmail-direct-text").text(node.text())
                node.replaceWith(paragraph)
            }
        }

        val compactContentWidthPx = detectCompactContentWidth(
            document = document,
            preferTransactionalCard = forceTransactionalFluid,
        ) ?: if (forceTransactionalFluid) viewportWidthCssPx else null
        val desktopCandidate = detectDesktopCanvas(document)
        val knownSenderCanvasWidthPx = if (
            instagramSender &&
            compactContentWidthPx != null &&
            compactContentWidthPx in 360..440
        ) {
            // Meta digest templates use a fixed 394px content table plus 16px side cells. Reflowing
            // every table independently clips the third column on a 360px viewport, so preserve the
            // complete 426px outer canvas and scale it as one unit.
            compactContentWidthPx + INSTAGRAM_SIDE_GUTTERS_PX
        } else if (preferDesktopCanvas) {
            // Known fixed-canvas transactional templates are consistently authored around 600px,
            // but some variants expose that width only through Outlook conditionals that Jsoup no
            // longer sees as a normal element. Falling back to 600 keeps the complete table intact.
            desktopCandidate
                ?.width
                ?.takeIf { width -> width in 520..720 }
                ?: KNOWN_DESKTOP_CANVAS_FALLBACK_PX
        } else {
            null
        }
        val detectedDesktopCanvasWidthPx = desktopCandidate
            ?.takeIf { candidate ->
                (!forceTransactionalFluid || grabSender) &&
                    !forceKnownSenderResponsive &&
                    compactContentWidthPx == null && (
                    // An explicit 560/600/640px canvas is a stronger signal than a generic
                    // viewport declaration. Cloudflare-style newsletters often include a
                    // device-width meta tag but still require whole-canvas scaling.
                    candidate.hardWidthScore >= STRONG_DESKTOP_HARD_SCORE ||
                        (!responsiveMarkup && candidate.score >= MIN_DESKTOP_CANVAS_SCORE) ||
                        (!hasStrongFluidRoot(document) && candidate.score >= STRONG_DESKTOP_SCORE)
                    )
            }
            ?.width
        val desktopCanvasWidthPx = knownSenderCanvasWidthPx ?: detectedDesktopCanvasWidthPx
        val documentLayout = if (desktopCanvasWidthPx != null) {
            MailDocumentLayout.DESKTOP_SCALED
        } else {
            MailDocumentLayout.FLUID
        }
        val textZoomPercent = when {
            facebookSender -> FACEBOOK_TEXT_ZOOM_PERCENT
            instagramSender -> 100
            documentLayout == MailDocumentLayout.DESKTOP_SCALED && grabSender -> GRAB_TEXT_ZOOM_PERCENT
            documentLayout == MailDocumentLayout.DESKTOP_SCALED -> DESKTOP_TEXT_ZOOM_PERCENT
            else -> 100
        }
        MailLog.d(
            MailLog.WEB,
            "layout selected=$documentLayout responsive=$responsiveMarkup " +
                "compactWidth=${compactContentWidthPx ?: 0} " +
                "desktopWidth=${desktopCanvasWidthPx ?: 0} " +
                "desktopScore=${desktopCandidate?.score ?: 0} " +
                "hardScore=${desktopCandidate?.hardWidthScore ?: 0} " +
                "transactionalFluid=$forceTransactionalFluid desktopPreferred=$preferDesktopCanvas " +
                "binance=$binanceSender grab=$grabSender cloudflare=$cloudflareSender " +
                "zaBank=$zaBankSender " +
                "facebook=$facebookSender " +
                "responsiveKnownSender=$forceKnownSenderResponsive mediaRules=$responsiveMediaRules " +
                "textZoom=$textZoomPercent " +
                "domain=${senderDomain.ifBlank { "unknown" }} " +
                "viewport=$viewportWidthCssPx",
        )

        body.children().forEach { child -> child.addClass("bondmail-root-content") }
        body.children().firstOrNull()?.addClass("bondmail-original-first")
        normalizeFirstChildSelectors(document)
        if (documentLayout == MailDocumentLayout.DESKTOP_SCALED) {
            // Table-based newsletters are normally authored on a 560-700 px canvas. Thunderbird
            // keeps that canvas intact and scales it as one unit. Rewriting every nested width to
            // 100% destroys columns and is the reason some Cloudflare/marketing messages were cut
            // off on the right. Keep the original geometry and only remove viewport locks.
            normalizeDesktopSafety(document)
            retargetBodyChildSelectors(document, "#bondmail-desktop-canvas")
            wrapDesktopCanvas(body, desktopCanvasWidthPx!!)
        } else {
            // The Gmail-style message card wraps the original body nodes. Retarget sender CSS that
            // relied on direct `body > ...` selectors before moving those nodes into the card.
            retargetBodyChildSelectors(document, "#bondmail-message-body")
            normalizeWideLayouts(document, viewportWidthCssPx)
            normalizeCompactPrimaryContent(
                document = document,
                viewportWidthCssPx = viewportWidthCssPx,
                preferTransactionalCard = forceTransactionalFluid,
            )
            normalizeLockedLayouts(document)
            normalizeOversizedTypography(document)
            normalizeLongNoWrapText(document)
            normalizeHorizontalSpacing(document)
        }

        document.select("img").forEach { image ->
            val width = parsePixelDimension(image.attr("width"))
                ?: STYLE_WIDTH_DECLARATION.find(image.attr("style"))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let(::parsePixelDimension)
            val height = parsePixelDimension(image.attr("height"))
                ?: STYLE_HEIGHT_DECLARATION.find(image.attr("style"))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let(::parsePixelDimension)
            val smallIcon = width != null && height != null && width <= 96 && height <= 96
            val responsiveHeight = if (smallIcon) "" else ";height:auto!important"
            image.attr(
                "style",
                image.attr("style") + ";max-width:100%!important$responsiveHeight",
            )
        }
        document.select("video").forEach { media ->
            media.attr(
                "style",
                media.attr("style") + ";max-width:100%!important;height:auto!important",
            )
        }
        // Do not force `height:auto` on inline SVG icons. Many transactional mails use tiny SVGs
        // without a viewBox; overriding their explicit height collapses them to zero in WebView.
        document.select("svg").forEach { svg ->
            svg.attr("style", svg.attr("style") + ";max-width:100%!important")
        }

        // Build one Gmail-style content card: subject on the page background, then the sender
        // row and original message body on a raised content surface. Keeping both loading and final
        // layouts structurally identical also removes the visible font-weight jump on first open.
        wrapMessageCard(body, header)
        val contentHeightHint = estimateContentHeightHint(
            document = document,
            layout = documentLayout,
            attachmentCount = header.attachments.size,
        )
        // Keep the inert scroll tail outside the rounded message card. Chromium still gets enough
        // range for header dragging and chrome reveal, while a one- or two-line message keeps its
        // card bottom immediately after the real content instead of looking artificially tall.
        body.appendChild(Element("div").attr("id", "bondmail-scroll-tail").attr("aria-hidden", "true"))
        // Compose uses sp for the visible native title/sender layer while the hidden HTML spacer
        // uses CSS px. At initial-scale=1 one CSS px maps to one dp, so multiply only typography by
        // the current Android font scale. This keeps line wrapping and card geometry identical while
        // Chromium replaces the preview body underneath the stable native header.
        val safeTopInsetCssPx = topContentInsetCssPx.coerceIn(0, 200)
        val safeSubjectBlockHeightCssPx = subjectBlockHeightCssPx.coerceIn(48, 520)
        val safeSenderBlockHeightCssPx = senderBlockHeightCssPx.coerceIn(64, 180)
        // The adaptive title size is selected by Compose before the document is prepared. WebView
        // must use that exact size as well: keeping the old hard-coded 22/28 px typography while
        // reserving a block measured at 18/24 sp made the final line appear underneath the sender.
        val subjectFontSizeCssPx = formatCssNumber(subjectFontSizeSp * fontScale)
        val subjectLineHeightCssPx = formatCssNumber(subjectLineHeightSp * fontScale)
        val avatarFontSizeCssPx = formatCssNumber(15f * fontScale)
        val senderNameFontSizeCssPx = formatCssNumber(16f * fontScale)
        val senderNameLineHeightCssPx = formatCssNumber(20f * fontScale)
        val dateFontSizeCssPx = formatCssNumber(12f * fontScale)
        val dateLineHeightCssPx = formatCssNumber(16f * fontScale)
        val paperclipFontSizeCssPx = formatCssNumber(14f * fontScale)
        val paperclipLineHeightCssPx = formatCssNumber(16f * fontScale)
        val addressFontSizeCssPx = formatCssNumber(12f * fontScale)
        val addressLineHeightCssPx = formatCssNumber(16f * fontScale)
        val recipientFontSizeCssPx = formatCssNumber(12f * fontScale)
        val recipientLineHeightCssPx = formatCssNumber(16f * fontScale)
        val layoutCss = when (documentLayout) {
            MailDocumentLayout.FLUID -> """
              #bondmail-message-body > .bondmail-root-content{
                position:relative!important;left:auto!important;right:auto!important;
                float:none!important;transform:none!important;width:auto!important;
                min-width:0!important;max-width:100%!important;margin-left:auto!important;
                margin-right:auto!important;box-sizing:border-box!important
              }
              #bondmail-message-body > table.bondmail-root-content,
              #bondmail-message-body > center.bondmail-root-content > table,
              #bondmail-message-body > div.bondmail-root-content > table:first-child{
                width:100%!important;min-width:0!important;max-width:100%!important
              }
              .bondmail-wide-layout{
                width:100%!important;min-width:0!important;max-width:100%!important;
                box-sizing:border-box!important;overflow-x:visible!important
              }
              .bondmail-compact-primary{
                width:calc(100% - 16px)!important;min-width:0!important;max-width:440px!important;
                margin-left:auto!important;margin-right:auto!important;box-sizing:border-box!important
              }
              table.bondmail-compact-primary,
              .bondmail-compact-primary > table:first-child,
              .bondmail-compact-primary > tbody,
              .bondmail-compact-primary > tbody > tr,
              .bondmail-compact-primary > tbody > tr > td{
                width:100%!important;min-width:0!important;max-width:100%!important;
                box-sizing:border-box!important
              }
              .bondmail-auto-height{
                position:relative!important;height:auto!important;min-height:0!important;
                max-height:none!important;overflow:visible!important
              }
              .bondmail-wrap-text{white-space:normal!important;overflow-wrap:break-word!important}
              #bondmail-message-body > .bondmail-root-content,
              #bondmail-message-body > .bondmail-root-content *{
                min-width:0!important;max-width:100%!important;box-sizing:border-box!important
              }
              #bondmail-message-body > .bondmail-root-content div,
              #bondmail-message-body > .bondmail-root-content section,
              #bondmail-message-body > .bondmail-root-content article,
              #bondmail-message-body > .bondmail-root-content main,
              #bondmail-message-body > .bondmail-root-content center,
              #bondmail-message-body > .bondmail-root-content figure{
                max-width:100%!important;box-sizing:border-box!important
              }
              #bondmail-message-body > .bondmail-root-content table,
              #bondmail-message-body > .bondmail-root-content tbody,
              #bondmail-message-body > .bondmail-root-content tr,
              #bondmail-message-body > .bondmail-root-content td,
              #bondmail-message-body > .bondmail-root-content th{
                min-width:0!important;max-width:100%!important;box-sizing:border-box!important
              }
              #bondmail-message-body > .bondmail-root-content table.bondmail-wide-layout,
              #bondmail-message-body > .bondmail-root-content .bondmail-wide-layout table{
                width:100%!important;table-layout:auto!important
              }
              #bondmail-message-body > .bondmail-root-content p,
              #bondmail-message-body > .bondmail-root-content span,
              #bondmail-message-body > .bondmail-root-content a,
              #bondmail-message-body > .bondmail-root-content li,
              #bondmail-message-body > .bondmail-root-content td,
              #bondmail-message-body > .bondmail-root-content th,
              #bondmail-message-body > .bondmail-root-content blockquote{
                white-space:normal!important;overflow-wrap:anywhere!important;word-break:break-word
              }
              /* Receipts and travel confirmations commonly place prices in a narrow right-aligned
                 table cell. Never split currency/amounts character by character; let that column
                 keep its intrinsic width and let the descriptive column wrap instead. */
              #bondmail-message-body td[align="right"],
              #bondmail-message-body th[align="right"],
              #bondmail-message-body td[style*="text-align: right" i],
              #bondmail-message-body th[style*="text-align: right" i],
              #bondmail-message-body [class*="price" i],
              #bondmail-message-body [class*="amount" i],
              #bondmail-message-body [class*="total" i]{
                min-width:max-content!important;max-width:none!important;
                white-space:nowrap!important;overflow-wrap:normal!important;word-break:normal!important
              }
              #bondmail-message-body > .bondmail-root-content h1,
              #bondmail-message-body > .bondmail-root-content h2,
              #bondmail-message-body > .bondmail-root-content h3,
              #bondmail-message-body > .bondmail-root-content h4,
              #bondmail-message-body > .bondmail-root-content h5,
              #bondmail-message-body > .bondmail-root-content h6,
              #bondmail-message-body > .bondmail-root-content p,
              #bondmail-message-body > .bondmail-root-content li{
                max-width:100%!important;white-space:normal!important;
                overflow-wrap:anywhere!important;box-sizing:border-box!important
              }
              blockquote,blockquote[type=cite],.gmail_quote,.yahoo_quoted{
                width:auto!important;min-width:0!important;max-width:100%!important;
                margin:12px 0!important;padding-left:8px!important;
                box-sizing:border-box!important;overflow-x:hidden!important
              }
              blockquote table,.gmail_quote table,.yahoo_quoted table{
                width:100%!important;min-width:0!important;max-width:100%!important
              }
            """.trimIndent()

            MailDocumentLayout.DESKTOP_SCALED -> {
                val width = desktopCanvasWidthPx!!
                val availableWidth = (viewportWidthCssPx - 24).coerceAtLeast(240)
                val desktopScale = (availableWidth.toFloat() / width.toFloat()).coerceIn(0.20f, 1f)
                val scaledWidth = kotlin.math.ceil(width * desktopScale).toInt().coerceAtMost(availableWidth)
                val zoomPercent = formatCssNumber(desktopScale * 100f)
                """
                  #bondmail-message-content{
                    display:block!important;position:relative!important;width:${scaledWidth}px!important;
                    min-width:0!important;max-width:100%!important;margin:0 auto!important;
                    padding:0!important;overflow:visible!important;box-sizing:border-box!important
                  }
                  #bondmail-desktop-canvas{
                    display:inline-block!important;position:relative!important;
                    width:${width}px!important;min-width:${width}px!important;max-width:${width}px!important;
                    margin:0!important;padding:0!important;overflow:visible!important;
                    transform-origin:top left!important;
                    zoom:${zoomPercent}%!important;-webkit-text-size-adjust:100%!important
                  }
                  #bondmail-desktop-canvas > .bondmail-root-content{
                    max-width:${width}px!important;box-sizing:border-box!important
                  }
                  #bondmail-desktop-canvas img,
                  #bondmail-desktop-canvas video{max-width:100%!important;height:auto!important}
                  #bondmail-desktop-canvas svg{max-width:100%!important}
                """.trimIndent()
            }
        }
        val themeFallback = """
            html,body{background:$backgroundCss!important;color:$foregroundCss}
            a{color:$linkCss}
        """.trimIndent()

        document.head().append(
            """
            <style>
              html{
                margin:0!important;padding:0!important;width:100%!important;min-width:0!important;
                max-width:100%!important;height:auto!important;min-height:100%!important;
                overflow-x:hidden!important;overflow-y:visible!important;box-sizing:border-box!important;
                background:$backgroundCss;touch-action:pan-y
              }
              body{
                display:block!important;position:static!important;margin:0!important;
                width:auto!important;min-width:0!important;max-width:100%!important;
                height:auto!important;min-height:100vh!important;max-height:none!important;
                padding:${safeTopInsetCssPx}px 0 0!important;box-sizing:border-box!important;
                overflow-x:hidden!important;overflow-y:visible!important;
                font-family:sans-serif;
                line-height:1.45;overflow-wrap:break-word;
                -webkit-text-size-adjust:100%;-webkit-overflow-scrolling:touch;
                touch-action:pan-y;overscroll-behavior-y:auto
              }
              $layoutCss
              tr.bondmail-icon-row{
                display:table-row!important;float:none!important;white-space:nowrap!important
              }
              tr.bondmail-icon-row > td.bondmail-icon-cell,
              tr.bondmail-icon-row > th.bondmail-icon-cell{
                display:table-cell!important;float:none!important;width:auto!important;
                min-width:0!important;max-width:none!important;padding-left:4px!important;
                padding-right:4px!important;vertical-align:middle!important;
                white-space:nowrap!important;overflow-wrap:normal!important;word-break:normal!important
              }
              tr.bondmail-icon-row img,tr.bondmail-icon-row svg{
                display:inline-block!important;float:none!important;max-width:64px!important;
                vertical-align:middle!important
              }
              .bondmail-icon-strip{
                max-width:100%!important;white-space:nowrap!important;overflow:visible!important;
                text-align:center!important;word-break:normal!important;overflow-wrap:normal!important
              }
              .bondmail-icon-strip > .bondmail-icon-unit{
                display:inline-table!important;float:none!important;width:auto!important;
                min-width:0!important;max-width:none!important;margin-left:2px!important;
                margin-right:2px!important;vertical-align:middle!important;white-space:nowrap!important;
                overflow:visible!important;word-break:normal!important;overflow-wrap:normal!important
              }
              .bondmail-icon-unit table,.bondmail-icon-unit-table{
                display:inline-table!important;float:none!important;width:auto!important;
                min-width:0!important;max-width:none!important;vertical-align:middle!important
              }
              .bondmail-icon-unit tr{display:table-row!important;float:none!important}
              .bondmail-icon-unit td,.bondmail-icon-unit th{
                display:table-cell!important;float:none!important;width:auto!important;
                min-width:0!important;max-width:none!important;vertical-align:middle!important;
                white-space:nowrap!important
              }
              .bondmail-icon-unit img{
                display:inline-block!important;float:none!important;max-width:64px!important;
                height:auto!important;vertical-align:middle!important
              }
              .bondmail-icon-unit svg{
                display:inline-block!important;float:none!important;max-width:64px!important;
                vertical-align:middle!important
              }
              #bondmail-message-subject{
                position:relative!important;z-index:2!important;display:block!important;
                width:100%!important;min-width:0!important;max-width:100%!important;
                height:${safeSubjectBlockHeightCssPx}px!important;
                min-height:${safeSubjectBlockHeightCssPx}px!important;
                max-height:${safeSubjectBlockHeightCssPx}px!important;
                margin:0!important;padding:0!important;overflow:hidden!important;
                box-sizing:border-box!important;background:$headerSurfaceCss!important;
                color:$foregroundCss!important;
                font-family:sans-serif!important
              }
              #bondmail-message-subject,#bondmail-message-subject *{
                animation:none!important;transition:none!important;
                font-family:sans-serif!important
              }
              #bondmail-message-subject .bondmail-subject-text{
                display:-webkit-box!important;-webkit-box-orient:vertical!important;
                -webkit-line-clamp:6!important;overflow:hidden!important;
                margin:0!important;padding:16px 14px 12px!important;
                box-sizing:border-box!important;
                max-width:100%!important;font-size:${subjectFontSizeCssPx}px!important;line-height:${subjectLineHeightCssPx}px!important;
                font-weight:600!important;letter-spacing:0!important;color:$foregroundCss!important;
                -webkit-text-fill-color:$foregroundCss!important;white-space:normal!important;
                overflow-wrap:anywhere!important;word-break:break-word!important;
                visibility:visible!important;opacity:1!important
              }
              #bondmail-message-card{
                position:relative!important;z-index:1!important;display:block!important;
                width:100%!important;min-width:0!important;max-width:100%!important;
                margin:0!important;padding:0!important;box-sizing:border-box!important;
                border:0!important;border-radius:0!important;background:transparent!important;
                color:$foregroundCss!important;overflow:visible!important;
                box-shadow:none!important;
                font-family:sans-serif!important
              }
              #bondmail-message-header{
                position:relative!important;z-index:2147483647!important;display:flex!important;
                align-items:flex-start!important;gap:11px!important;width:100%!important;
                min-width:0!important;max-width:100%!important;
                min-height:${safeSenderBlockHeightCssPx}px!important;
                height:${safeSenderBlockHeightCssPx}px!important;
                max-height:${safeSenderBlockHeightCssPx}px!important;
                padding:13px 14px 11px!important;margin:0!important;
                box-sizing:border-box!important;border:0!important;
                background:$headerSurfaceCss!important;color:$foregroundCss!important;
                overflow:hidden!important;
                font-family:sans-serif!important;isolation:isolate!important
              }
              #bondmail-message-body{
                position:relative!important;z-index:1!important;display:block!important;width:100%!important;
                min-width:0!important;max-width:100%!important;margin:0!important;
                padding:0!important;box-sizing:border-box!important;
                background:transparent!important;color:$foregroundCss!important;
                overflow:visible!important;border-radius:0!important;
                isolation:auto!important;contain:none!important;
                clip-path:none!important;color-scheme:light!important
              }
              #bondmail-message-body > .bondmail-original-first{
                top:auto!important;margin-top:0!important
              }
              #bondmail-message-header,#bondmail-message-header *{
                animation:none!important;transition:none!important;
                font-family:sans-serif!important;
                font-style:normal!important;font-variant:normal!important;text-transform:none!important;
                letter-spacing:0!important;font-feature-settings:normal!important;
                font-variation-settings:normal!important
              }
              #bondmail-message-header .bondmail-avatar{
                width:46px!important;height:46px!important;min-width:46px!important;max-width:46px!important;
                border-radius:50%!important;display:flex!important;align-items:center!important;
                justify-content:center!important;font-size:${avatarFontSizeCssPx}px!important;font-weight:700!important;
                letter-spacing:0!important;background:$avatarBackgroundCss!important;
                color:$avatarForegroundCss!important;-webkit-text-fill-color:$avatarForegroundCss!important;
                box-sizing:border-box!important
              }
              #bondmail-message-header .bondmail-avatar,
              #bondmail-message-header .bondmail-meta{
                visibility:visible!important;opacity:1!important
              }
              #bondmail-message-header .bondmail-avatar svg{
                width:58%!important;height:58%!important;display:block!important;
                fill:$avatarForegroundCss!important;color:$avatarForegroundCss!important
              }
              #bondmail-message-header .bondmail-avatar svg path:not([data-bondmail-stroke]),
              #bondmail-message-header .bondmail-avatar svg circle:not([data-bondmail-stroke]),
              #bondmail-message-header .bondmail-avatar svg ellipse:not([data-bondmail-stroke]),
              #bondmail-message-header .bondmail-avatar svg rect:not([data-bondmail-stroke]),
              #bondmail-message-header .bondmail-avatar svg polygon:not([data-bondmail-stroke]){
                fill:currentColor!important
              }
              #bondmail-message-header .bondmail-avatar svg [data-bondmail-stroke]{
                fill:none!important;stroke:currentColor!important
              }
              #bondmail-message-header .bondmail-meta{
                min-width:0!important;max-width:calc(100% - 57px)!important;flex:1!important;
                display:flex!important;flex-direction:column!important;gap:2px!important;padding-top:0!important;

              }
              #bondmail-message-header .bondmail-name-row{
                display:flex!important;align-items:center!important;gap:8px!important;
                width:100%!important;min-width:0!important;max-width:100%!important
              }
              #bondmail-message-header .bondmail-name{
                display:block!important;min-width:0!important;max-width:100%!important;flex:1!important;
                font-size:${senderNameFontSizeCssPx}px!important;line-height:${senderNameLineHeightCssPx}px!important;font-weight:600!important;
                color:$foregroundCss!important;-webkit-text-fill-color:$foregroundCss!important;
                white-space:nowrap!important;overflow:hidden!important;text-overflow:ellipsis!important;
                overflow-wrap:normal!important;word-break:normal!important
              }
              #bondmail-message-header .bondmail-date{
                display:block!important;flex:0 0 auto!important;white-space:nowrap!important;
                font-size:${dateFontSizeCssPx}px!important;line-height:${dateLineHeightCssPx}px!important;font-weight:400!important;
                color:$mutedCss!important;-webkit-text-fill-color:$mutedCss!important
              }
              #bondmail-message-header .bondmail-header-paperclip{
                display:block!important;flex:0 0 16px!important;width:16px!important;
                font-size:${paperclipFontSizeCssPx}px!important;
                line-height:${paperclipLineHeightCssPx}px!important;color:$mutedCss!important;white-space:nowrap!important
              }
              #bondmail-message-header .bondmail-address{
                display:block!important;max-width:100%!important;
                font-size:${addressFontSizeCssPx}px!important;line-height:${addressLineHeightCssPx}px!important;font-weight:400!important;
                color:$foregroundCss!important;-webkit-text-fill-color:$foregroundCss!important;
                white-space:nowrap!important;overflow:hidden!important;text-overflow:ellipsis!important;
                overflow-wrap:normal!important;word-break:normal!important
              }
              #bondmail-message-header .bondmail-muted{
                display:-webkit-box!important;max-width:100%!important;
                font-size:${recipientFontSizeCssPx}px!important;line-height:${recipientLineHeightCssPx}px!important;font-weight:400!important;
                color:$mutedCss!important;-webkit-text-fill-color:$mutedCss!important;
                white-space:normal!important;overflow:hidden!important;text-overflow:ellipsis!important;
                -webkit-box-orient:vertical!important;-webkit-line-clamp:2!important;
                overflow-wrap:anywhere!important;word-break:break-word!important
              }
              #bondmail-attachments{
                display:flex!important;flex-direction:column!important;gap:8px!important;
                width:auto!important;margin:4px 16px 14px!important;padding:0!important;
                box-sizing:border-box!important;background:transparent!important
              }
              #bondmail-attachments .bondmail-attachment{
                display:flex!important;align-items:center!important;gap:10px!important;
                min-width:0!important;width:100%!important;padding:12px 14px!important;
                box-sizing:border-box!important;border-radius:14px!important;
                border:1px solid color-mix(in srgb,$mutedCss 18%,transparent)!important;
                background:color-mix(in srgb,$mutedCss 7%,$headerSurfaceCss)!important;
                color:$foregroundCss!important;font-family:sans-serif!important;
                text-decoration:none!important;cursor:pointer!important
              }
              #bondmail-attachments .bondmail-paperclip{
                flex:0 0 auto!important;font-size:20px!important;line-height:1!important
              }
              #bondmail-attachments .bondmail-attachment-name{
                flex:1!important;min-width:0!important;font-size:14px!important;line-height:1.35!important;
                font-weight:500!important;white-space:nowrap!important;overflow:hidden!important;
                text-overflow:ellipsis!important;color:$foregroundCss!important
              }
              #bondmail-attachments .bondmail-attachment-size{
                flex:0 0 auto!important;font-size:12px!important;line-height:1.35!important;
                color:$mutedCss!important;white-space:nowrap!important
              }
              img.bondmail-dark-logo{
                box-sizing:border-box!important
              }
              #bondmail-message-body img{
                color:transparent!important;-webkit-text-fill-color:transparent!important;
                font-size:0!important
              }
              img.bondmail-google-logo{
                display:block!important;width:auto!important;height:auto!important;
                max-width:96px!important;max-height:48px!important;object-fit:contain!important
              }
              img.bondmail-wise-logo{
                display:block!important;width:93px!important;height:21px!important;
                min-width:93px!important;max-width:93px!important;
                min-height:21px!important;max-height:21px!important;
                margin:0 auto!important;padding:0!important;
                object-fit:contain!important;background:transparent!important
              }
              .bondmail-wise-logo-cell{
                width:93px!important;min-width:93px!important;max-width:93px!important;
                height:21px!important;min-height:21px!important;max-height:21px!important;
                padding:0!important
              }
              body.bondmail-dark-mode:not(.bondmail-native-dark-mail) #bondmail-message-body{
                background:#ffffff!important;color:#202124!important;color-scheme:light!important;
                filter:none!important
              }
              body.bondmail-dark-mode:not(.bondmail-native-dark-mail) #bondmail-message-body img,
              body.bondmail-dark-mode:not(.bondmail-native-dark-mail) #bondmail-message-body video,
              body.bondmail-dark-mode:not(.bondmail-native-dark-mail) #bondmail-message-body svg{
                filter:none!important
              }
              body.bondmail-dark-mode.bondmail-force-light-mail #bondmail-message-body{
                background:#ffffff!important;color:#111111!important;
                -webkit-text-fill-color:#111111!important;color-scheme:light!important;
                filter:none!important
              }
              body.bondmail-dark-mode.bondmail-force-light-mail #bondmail-message-body *{
                color:#111111!important;-webkit-text-fill-color:#111111!important
              }
              body.bondmail-dark-mode.bondmail-force-light-mail #bondmail-message-body a,
              body.bondmail-dark-mode.bondmail-force-light-mail #bondmail-message-body a *{
                color:#1689d8!important;-webkit-text-fill-color:#1689d8!important
              }
              body.bondmail-dark-mode.bondmail-force-light-mail #bondmail-message-body img,
              body.bondmail-dark-mode.bondmail-force-light-mail #bondmail-message-body video,
              body.bondmail-dark-mode.bondmail-force-light-mail #bondmail-message-body svg{
                filter:none!important
              }
              body.bondmail-repair-inherited-light-text #bondmail-message-body p,
              body.bondmail-repair-inherited-light-text #bondmail-message-body p *{
                color:#202124!important;-webkit-text-fill-color:#202124!important
              }
              body.bondmail-repair-inherited-light-text #bondmail-message-body table.blue,
              body.bondmail-repair-inherited-light-text #bondmail-message-body table.blue *{
                color:#ffffff!important;-webkit-text-fill-color:#ffffff!important
              }
              body.bondmail-apple-mail svg.bondmail-apple-brand-logo{
                display:block!important;width:24px!important;height:29px!important;
                min-width:24px!important;max-width:24px!important;
                min-height:29px!important;max-height:29px!important;
                fill:#111111!important;color:#111111!important;
                filter:none!important;background:transparent!important
              }
              body.bondmail-samsung-mail img.bondmail-samsung-logo{
                display:block!important;visibility:visible!important;opacity:1!important;
                width:auto!important;height:auto!important;min-width:0!important;
                max-width:280px!important;max-height:90px!important;
                margin:24px auto 28px!important;object-fit:contain!important
              }
              body.bondmail-facebook-mail .bondmail-brand-logo-host{
                width:auto!important;min-width:0!important;max-width:100%!important;
                overflow:visible!important;text-align:center!important
              }
              body.bondmail-facebook-mail img.bondmail-brand-logo-wide{
                display:block!important;width:128px!important;min-width:96px!important;
                max-width:42vw!important;height:auto!important;max-height:56px!important;
                margin:12px auto 16px!important;object-fit:contain!important
              }
              body.bondmail-facebook-mail img.bondmail-brand-logo-square{
                display:block!important;width:52px!important;height:52px!important;
                min-width:52px!important;max-width:52px!important;max-height:52px!important;
                margin:12px auto 16px!important;object-fit:contain!important
              }
              pre,.bondmail-plain{
                white-space:pre-wrap!important;word-break:break-word!important;
                font-family:sans-serif!important;
                font-size:16px!important;line-height:1.55!important;margin:0!important;
                padding:4px 18px 22px!important;box-sizing:border-box!important
              }
              #bondmail-scroll-tail{
                display:block!important;width:1px!important;min-width:1px!important;
                height:24px!important;min-height:24px!important;
                margin:0!important;padding:0!important;pointer-events:none!important;
                visibility:hidden!important;clear:both!important
              }
              a{word-break:break-word}
              $themeFallback
            </style>
            """.trimIndent(),
        )
        return PreparedMailDocument(
            html = document.outerHtml(),
            hasRemoteImages = hasRemoteImages,
            baseUrl = baseUrl,
            layout = documentLayout,
            desktopCanvasWidthPx = desktopCanvasWidthPx,
            textZoomPercent = textZoomPercent,
            contentHeightHint = contentHeightHint,
        ).also { documents.put(cacheKey, it) }
    }


    /**
     * The loading card starts at the full body height. Only messages that are clearly compact are
     * allowed to spring back before Chromium is revealed; uncertain or fixed-canvas newsletters stay
     * expanded so the transition never collapses and then immediately grows again.
     */
    private fun estimateContentHeightHint(
        document: Document,
        layout: MailDocumentLayout,
        attachmentCount: Int,
    ): MailContentHeightHint {
        if (layout == MailDocumentLayout.DESKTOP_SCALED) return MailContentHeightHint.LONG

        val content = document.getElementById("bondmail-message-body") ?: document.body()
        val normalizedText = content.text()
            .replace(Regex("\\s+"), " ")
            .trim()
        val textLength = normalizedText.length
        val semanticBlocks = content.select(
            "p, li, h1, h2, h3, h4, h5, h6, blockquote, pre, section, article, main",
        ).size
        val textBearingDivs = content.select("div")
            .count { element -> element.ownText().trim().length >= 24 }
            .coerceAtMost(18)
        val structuralBlocks = semanticBlocks + textBearingDivs
        val tableCount = content.select("table").size
        val meaningfulMediaCount = content.select("img, video").count { media ->
            if (media.tagName().equals("video", ignoreCase = true)) return@count true

            val source = sequenceOf("src", "data-src", "data-original", "data-lazy-src")
                .map(media::attr)
                .firstOrNull(String::isNotBlank)
                .orEmpty()
            val signature = buildString {
                append(source).append(' ')
                append(media.attr("alt")).append(' ')
                append(media.className()).append(' ')
                append(media.id())
            }.lowercase()
            val width = parsePixelDimension(media.attr("width"))
                ?: STYLE_WIDTH_DECLARATION.find(media.attr("style"))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let(::parsePixelDimension)
            val height = parsePixelDimension(media.attr("height"))
                ?: STYLE_HEIGHT_DECLARATION.find(media.attr("style"))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let(::parsePixelDimension)
            val trackingResource = HEIGHT_HINT_TRACKING_MARKERS.any(signature::contains)
            val tinyPixel = width != null && height != null && width <= 2 && height <= 2
            !trackingResource &&
                !tinyPixel &&
                !isPlaceholderImageSource(source) &&
                ((width == null && height == null) || (width ?: 0) >= 180 || (height ?: 0) >= 100)
        }
        val estimatedLines = (textLength + HEIGHT_HINT_CHARS_PER_LINE - 1) /
            HEIGHT_HINT_CHARS_PER_LINE
        val weightedHeight = estimatedLines * 22 +
            structuralBlocks * 5 +
            tableCount * 34 +
            meaningfulMediaCount * 150 +
            attachmentCount * 64
        val hint = if (
            textLength <= 1_200 &&
            structuralBlocks <= 34 &&
            tableCount <= 5 &&
            meaningfulMediaCount <= 1 &&
            weightedHeight <= 760
        ) {
            MailContentHeightHint.SHORT
        } else {
            MailContentHeightHint.LONG
        }
        MailLog.d(
            MailLog.WEB,
            "content heightHint=$hint text=$textLength blocks=$structuralBlocks " +
                "tables=$tableCount media=$meaningfulMediaCount attachments=$attachmentCount " +
                "weighted=$weightedHeight",
        )
        return hint
    }


    /**
     * Some senders omit the emoji variation selector for symbols such as the framed-picture icon.
     * Apple renders those as color emoji by default, while several Android fonts choose a missing
     * monochrome glyph. Add VS16 only to the small set observed in transactional mail; text and
     * branding typography remain untouched.
     */
    private fun normalizeEmojiPresentation(document: Document) {
        document.getAllElements().forEach { element ->
            element.textNodes().forEach { node ->
                var value = node.text()
                EMOJI_PRESENTATION_SYMBOLS.forEach { symbol ->
                    value = value.replace(Regex("${Regex.escape(symbol)}(?!\uFE0F)"), "$symbol\uFE0F")
                }
                node.text(value)
            }
        }
    }

    private fun normalizeImageSources(document: Document) {
        document.select("img").forEach { image ->
            val current = image.attr("src").trim()
            val lazySource = LAZY_IMAGE_ATTRIBUTES
                .asSequence()
                .map(image::attr)
                .map(String::trim)
                .firstOrNull { it.isNotBlank() && !isPlaceholderImageSource(it) }

            if (lazySource != null && (current.isBlank() || isPlaceholderImageSource(current))) {
                image.attr("src", normalizeResourceUrl(lazySource))
            } else if (current.isNotBlank()) {
                image.attr("src", normalizeResourceUrl(current))
            }

            val srcSet = image.attr("srcset").ifBlank { image.attr("data-srcset") }.trim()
            if (srcSet.isNotBlank()) {
                val normalized = srcSet.split(',').joinToString(", ") { entry ->
                    val trimmed = entry.trim()
                    val url = trimmed.substringBefore(' ')
                    val descriptor = trimmed.substringAfter(' ', missingDelimiterValue = "")
                    val fixed = normalizeResourceUrl(url)
                    if (descriptor.isBlank()) fixed else "$fixed $descriptor"
                }
                image.attr("srcset", normalized)
                if (image.attr("src").isBlank()) {
                    image.attr("src", normalized.substringBefore(',').trim().substringBefore(' '))
                }
            }

            LAZY_IMAGE_ATTRIBUTES.forEach(image::removeAttr)
            image.removeAttr("data-srcset")
            image.removeAttr("loading")
        }

        document.select("[background],[poster]").forEach { element ->
            listOf("background", "poster").forEach { attribute ->
                if (element.hasAttr(attribute)) {
                    element.attr(attribute, normalizeResourceUrl(element.attr(attribute)))
                }
            }
        }

        document.select("style,[style]").forEach { element ->
            val attributeStyle = !element.tagName().equals("style", ignoreCase = true)
            val css = if (attributeStyle) element.attr("style") else element.data().ifBlank { element.html() }
            val normalized = PROTOCOL_RELATIVE_CSS_URL.replace(css) { match ->
                "url(\"https://${match.groupValues[1]}\")"
            }
            if (attributeStyle) element.attr("style", normalized) else element.text(normalized)
        }
    }

    /**
     * A small number of Facebook transactional templates ship a 16–24px logo that is intended for
     * a desktop-width header. Apple Mail preserves that size too, which makes the brand mark nearly
     * disappear on a phone. Enlarge only a confidently identified Facebook/Meta brand image; all
     * other sender artwork keeps its authored dimensions.
     */
    private fun enhanceKnownSenderBranding(
        document: Document,
        isFacebookSender: Boolean,
    ) {
        if (!isFacebookSender) return
        document.body().addClass("bondmail-facebook-mail")

        fun dimensions(image: Element): Pair<Int?, Int?> {
            val width = parsePixelDimension(image.attr("width"))
                ?: STYLE_WIDTH_DECLARATION.find(image.attr("style"))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let(::parsePixelDimension)
            val height = parsePixelDimension(image.attr("height"))
                ?: STYLE_HEIGHT_DECLARATION.find(image.attr("style"))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let(::parsePixelDimension)
            return width to height
        }

        fun signature(image: Element): String = buildString {
            append(image.attr("alt")).append(' ')
            append(image.attr("title")).append(' ')
            append(image.attr("src")).append(' ')
            append(image.attr("data-src")).append(' ')
            append(image.className()).append(' ')
            append(image.id()).append(' ')
            append(image.closest("a")?.attr("href").orEmpty())
        }.lowercase()

        fun plausibleBrandDimensions(image: Element): Boolean {
            val (width, height) = dimensions(image)
            val plausibleWordmark = width != null && height != null &&
                width in 32..180 && height in 10..72 && width >= height * 1.35f
            val plausibleSquareMark = width != null && height != null &&
                width in 16..64 && height in 16..64
            return plausibleWordmark || plausibleSquareMark
        }

        val candidates = document.select("img").asSequence().take(14).filter { image ->
            val (width, height) = dimensions(image)
            val value = signature(image)
            val tinyPixel = (width != null && width <= 2) || (height != null && height <= 2)
            val trackingResource = listOf(
                "tracking",
                "tracker",
                "beacon",
                "open.gif",
                "open.png",
                "1x1",
                "spacer.gif",
                "transparent.gif",
            ).any(value::contains)
            !tinyPixel && !trackingResource
        }.toList()

        val logo = candidates.firstOrNull { image ->
            val value = signature(image)
            "facebook" in value || "meta-logo" in value || "meta_logo" in value
        } ?: candidates.firstOrNull { image ->
            "fbcdn" in signature(image) && plausibleBrandDimensions(image)
        } ?: candidates.take(8).firstOrNull(::plausibleBrandDimensions) ?: return

        val (width, height) = dimensions(logo)
        val wide = width != null && height != null && width >= height * 1.55f
        logo.addClass(if (wide) "bondmail-brand-logo-wide" else "bondmail-brand-logo-square")

        var parent = logo.parent()
        var depth = 0
        while (parent != null && parent !== document.body() && depth < 4) {
            if (parent.tagName() in setOf("td", "th", "div", "a", "center")) {
                parent.addClass("bondmail-brand-logo-host")
                return
            }
            parent = parent.parent()
            depth += 1
        }
    }

    /**
     * Preserve compact icon strips (social links, app-store badges, payment marks) as a table row.
     * Some sender media queries turn every cell into a full-width block on Android WebView. That
     * is useful for text columns but makes a seven-icon footer become a tall vertical list. The
     * marker is intentionally structural rather than sender-specific and only accepts rows where
     * most cells contain a small image/SVG and almost no prose.
     */
    private fun markHorizontalIconRows(document: Document) {
        document.select("tr").forEach rowLoop@ { row ->
            val cells = row.children().filter { child ->
                child.tagName().equals("td", ignoreCase = true) ||
                    child.tagName().equals("th", ignoreCase = true)
            }
            if (cells.size !in 3..12) return@rowLoop

            val iconCells = cells.count { cell ->
                val textLength = cell.text().trim().length
                val imageLikeCount = cell.select("img, svg").size
                imageLikeCount in 1..3 && textLength <= 24
            }
            if (iconCells < 3 || iconCells * 2 < cells.size) return@rowLoop

            row.addClass("bondmail-icon-row")
            row.attr(
                "style",
                row.attr("style") +
                    ";display:table-row!important;float:none!important;white-space:nowrap!important",
            )
            cells.forEach { cell ->
                cell.addClass("bondmail-icon-cell")
                cell.attr(
                    "style",
                    cell.attr("style") +
                        ";display:table-cell!important;float:none!important;width:auto!important;" +
                        "min-width:0!important;max-width:none!important;vertical-align:middle!important;" +
                        "white-space:nowrap!important;overflow-wrap:normal!important;" +
                        "word-break:normal!important",
                )
                cell.select("img, svg").forEach { icon ->
                    icon.attr(
                        "style",
                        icon.attr("style") +
                            ";display:inline-block!important;float:none!important;max-width:64px!important",
                    )
                }
            }
        }
    }

    /**
     * Preserve social-icon groups authored as several sibling inline tables/links. MJML-based
     * transactional mail (including Binance) commonly emits one tiny table per icon instead of a
     * single row with many cells. Sender mobile CSS then changes each table to width:100%, which is
     * why the footer appears as a vertical list in WebView even though Apple Mail keeps it inline.
     */
    private fun markHorizontalIconStrips(
        document: Document,
        aggressive: Boolean,
    ) {
        val minimumUnits = if (aggressive) 3 else 4
        val maximumParentText = if (aggressive) 160 else 96
        document.select("td, th, div, center, p, section").forEach parentLoop@ { parent ->
            if (
                parent.hasClass("bondmail-icon-row") ||
                parent.hasClass("bondmail-icon-cell") ||
                parent.hasClass("bondmail-icon-strip")
            ) {
                return@parentLoop
            }

            val children = parent.children().toList()
            if (children.size !in minimumUnits..16) return@parentLoop
            if (parent.ownText().trim().length > 32) return@parentLoop
            if (parent.text().trim().length > maximumParentText) return@parentLoop

            val iconUnits = children.filter { child ->
                isCompactIconUnit(child, aggressive = aggressive)
            }
            if (iconUnits.size < minimumUnits || iconUnits.size * 2 < children.size) {
                return@parentLoop
            }

            val meaningfulNonIconChildren = children.count { child ->
                child !in iconUnits && (
                    child.text().trim().length > 24 ||
                        child.select("img, svg").isEmpty()
                    )
            }
            if (meaningfulNonIconChildren > 1) return@parentLoop

            parent.addClass("bondmail-icon-strip")
            parent.attr(
                "style",
                parent.attr("style") +
                    ";white-space:nowrap!important;overflow:visible!important;" +
                    "text-align:center!important;word-break:normal!important",
            )
            iconUnits.forEach { unit ->
                unit.addClass("bondmail-icon-unit")
                unit.attr(
                    "style",
                    unit.attr("style") +
                        ";display:inline-table!important;float:none!important;width:auto!important;" +
                        "min-width:0!important;max-width:none!important;margin-left:2px!important;" +
                        "margin-right:2px!important;vertical-align:middle!important;" +
                        "white-space:nowrap!important;overflow:visible!important",
                )

                val nestedTables = buildList {
                    if (unit.tagName().equals("table", ignoreCase = true)) add(unit)
                    addAll(unit.select("table"))
                }.distinct()
                nestedTables.forEach { table ->
                    table.addClass("bondmail-icon-unit-table")
                    table.attr(
                        "style",
                        table.attr("style") +
                            ";display:inline-table!important;float:none!important;width:auto!important;" +
                            "min-width:0!important;max-width:none!important;vertical-align:middle!important",
                    )
                }
                unit.select("tr").forEach { row ->
                    row.attr("style", row.attr("style") + ";display:table-row!important;float:none!important")
                }
                unit.select("td, th").forEach { cell ->
                    cell.attr(
                        "style",
                        cell.attr("style") +
                            ";display:table-cell!important;float:none!important;width:auto!important;" +
                            "min-width:0!important;max-width:none!important;vertical-align:middle!important;" +
                            "white-space:nowrap!important",
                    )
                }
                unit.select("img").forEach { icon ->
                    icon.attr(
                        "style",
                        icon.attr("style") +
                            ";display:inline-block!important;float:none!important;max-width:64px!important;" +
                            "height:auto!important;vertical-align:middle!important",
                    )
                }
                // Some inline SVG social icons have no viewBox. Preserve their declared height;
                // forcing height:auto can collapse them to zero in Android WebView.
                unit.select("svg").forEach { icon ->
                    icon.attr(
                        "style",
                        icon.attr("style") +
                            ";display:inline-block!important;float:none!important;max-width:64px!important;" +
                            "vertical-align:middle!important",
                    )
                }
            }
        }
    }

    private fun isCompactIconUnit(
        element: Element,
        aggressive: Boolean,
    ): Boolean {
        val icons = element.select("img, svg")
        if (icons.size !in 1..3) return false
        if (element.text().trim().length > if (aggressive) 40 else 24) return false
        if (element.select("h1, h2, h3, h4, h5, h6, p, ul, ol").isNotEmpty()) return false

        val maximumDeclaredSize = if (aggressive) 128 else 96
        return icons.none { icon ->
            val width = parsePixelDimension(icon.attr("width"))
                ?: STYLE_WIDTH_DECLARATION.find(icon.attr("style"))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let(::parsePixelDimension)
            val height = parsePixelDimension(icon.attr("height"))
                ?: STYLE_HEIGHT_DECLARATION.find(icon.attr("style"))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let(::parsePixelDimension)
            (width != null && width > maximumDeclaredSize) ||
                (height != null && height > maximumDeclaredSize)
        }
    }

    private fun isIconStripContainer(element: Element): Boolean {
        if (
            element.hasClass("bondmail-icon-row") ||
            element.hasClass("bondmail-icon-cell") ||
            element.hasClass("bondmail-icon-strip") ||
            element.hasClass("bondmail-icon-unit") ||
            element.hasClass("bondmail-icon-unit-table")
        ) {
            return true
        }
        if (element.select(".bondmail-icon-strip, .bondmail-icon-unit").isNotEmpty()) {
            return element.text().trim().length <= 160
        }
        val iconRows = element.select("tr.bondmail-icon-row")
        if (iconRows.isEmpty()) return false

        // Allow one tiny caption row such as "Keep in touch" above the icons, but never classify
        // a real content table containing prose as an icon-only footer.
        val nonIconContentRows = element.select("tr").count { row ->
            !row.hasClass("bondmail-icon-row") &&
                (row.text().trim().length > 24 || row.select("img, svg").isEmpty())
        }
        return element.text().trim().length <= 64 && nonIconContentRows <= 1
    }

    private fun normalizeResourceUrl(raw: String): String {
        val value = raw.trim()
        return if (value.startsWith("//")) "https:$value" else value
    }

    private fun isPlaceholderImageSource(raw: String): Boolean {
        val value = raw.trim().lowercase()
        return value.isBlank() ||
            value == "about:blank" ||
            value == "#" ||
            value.startsWith("data:image/gif;base64,r0lgodlhaqab") ||
            value.contains("transparent.gif") ||
            value.contains("spacer.gif") ||
            value.contains("blank.gif")
    }

    private fun resolveBaseUrl(document: Document): String? {
        val explicit = document.selectFirst("base[href]")
            ?.attr("href")
            ?.trim()
            ?.takeIf(::isHttpUrl)
        if (explicit != null) return explicit

        val absoluteResource = document.select("[src],[href],[background],[poster]")
            .asSequence()
            .flatMap { element ->
                sequenceOf("src", "href", "background", "poster")
                    .filter(element::hasAttr)
                    .map { element.attr(it).trim() }
            }
            .firstOrNull(::isHttpUrl)
            ?: return null

        return runCatching {
            val uri = URI(absoluteResource)
            val path = uri.path.orEmpty()
            val directory = if (path.endsWith('/')) path else path.substringBeforeLast('/', "/") + "/"
            URI(uri.scheme, uri.authority, directory, null, null).toString()
        }.getOrNull()
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)

    private fun containsRemoteImages(rawHtml: String, document: Document): Boolean {
        if (REMOTE_IMAGE_PATTERN.containsMatchIn(rawHtml)) return true
        val baseHref = document.selectFirst("base[href]")?.attr("href").orEmpty().trim()
        val remoteBase = baseHref.startsWith("https://", ignoreCase = true) ||
            baseHref.startsWith("http://", ignoreCase = true)

        val remoteAttribute = document.select("[src],[srcset],[background],[poster]").any { element ->
            listOf("src", "srcset", "background", "poster").any { attribute ->
                element.hasAttr(attribute) && element.attr(attribute)
                    .split(',')
                    .map { entry -> entry.trim().substringBefore(' ').trim() }
                    .any { value -> isRemoteOrRelativeResource(value, remoteBase) }
            }
        }
        if (remoteAttribute) return true

        return document.select("style,[style]").any { element ->
            val css = if (element.tagName().equals("style", ignoreCase = true)) {
                element.data().ifBlank { element.html() }
            } else {
                element.attr("style")
            }
            CSS_URL_PATTERN.findAll(css).any { match ->
                isRemoteOrRelativeResource(match.groupValues[1].trim(), remoteBase)
            }
        }
    }

    private fun isRemoteOrRelativeResource(raw: String, remoteBase: Boolean): Boolean {
        val value = raw.trim().trim('"', '\'', '(', ')')
        if (value.isBlank()) return false
        if (
            value.startsWith("data:", ignoreCase = true) ||
            value.startsWith("cid:", ignoreCase = true) ||
            value.startsWith("javascript:", ignoreCase = true) ||
            value.startsWith("mailto:", ignoreCase = true) ||
            value.startsWith("#")
        ) return false
        if (
            value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("//")
        ) return true
        return remoteBase
    }

    private fun normalizeFirstChildSelectors(document: Document) {
        document.select("style").forEach { style ->
            var source = style.data().ifBlank { style.html() }
            source = BODY_DIRECT_FIRST_TAG.replace(source) { match ->
                "body > ${match.groupValues[1]}.bondmail-original-first"
            }
            source = BODY_DIRECT_FIRST_ANY.replace(source, "body > .bondmail-original-first")
            style.html(source)
        }
    }

    private fun hasResponsiveMediaRules(document: Document): Boolean {
        val css = document.select("style")
            .joinToString("\n") { style -> style.data().ifBlank { style.html() } }
            .lowercase()
        return RESPONSIVE_MEDIA_QUERY.containsMatchIn(css)
    }

    /**
     * Detect mobile/responsive intent before replacing the sender's viewport declaration.
     *
     * Many transactional messages keep a fixed 600 px fallback table for legacy Outlook engines,
     * then turn that table into a phone layout inside a media query. The fallback width alone must
     * not make BondMail scale an already responsive document a second time.
     */
    private fun hasResponsiveMarkup(document: Document): Boolean {
        val viewport = document.selectFirst("meta[name=viewport]")
            ?.attr("content")
            .orEmpty()
            .lowercase()
        if ("device-width" in viewport || "width=device" in viewport) return true
        if (hasResponsiveMediaRules(document)) return true

        // A shallow root table that is explicitly fluid is a useful fallback signal when a sender
        // omitted both viewport and media-query declarations. Nested button/icon tables are ignored.
        return hasStrongFluidRoot(document)
    }

    private fun isKnownMobileTransactionalSender(domain: String): Boolean =
        domain == "163.com" ||
            domain == "126.com" ||
            domain == "yeah.net" ||
            domain == "netease.com" ||
            domain.endsWith(".netease.com")

    /**
     * ZA Bank's messages include a fixed 600 px fallback table for desktop Outlook, but the same
     * markup reflows correctly on mobile. Treating that fallback as the canonical canvas shrinks
     * the entire message to roughly half size on a phone.
     */
    private fun isZaBankSender(domain: String): Boolean =
        domain == "za.group" || domain.endsWith(".za.group")

    private fun isBinanceSender(domain: String): Boolean =
        domain == "binance.com" ||
            domain.endsWith(".binance.com") ||
            domain == "binance.us" ||
            domain.endsWith(".binance.us")

    private fun isGrabTransactionalSender(domain: String, identity: String): Boolean =
        domain == "grab.com" ||
            domain.endsWith(".grab.com") ||
            ("grab" in identity && (
                "grab.com" in identity ||
                    "grab_com" in identity ||
                    identity.trim().startsWith("grab ")
                ))

    private fun isFacebookSender(domain: String, identity: String): Boolean =
        domain == "facebookmail.com" ||
            domain.endsWith(".facebookmail.com") ||
            domain == "facebook.com" ||
            domain.endsWith(".facebook.com") ||
            domain == "meta.com" ||
            domain.endsWith(".meta.com") ||
            "facebookmail" in identity ||
            identity.trim().startsWith("facebook ") ||
            identity.trim().startsWith("meta ")

    private fun hasStrongFluidRoot(document: Document): Boolean {
        val body = document.body()
        return body.children().take(4).any { root ->
            val tables = if (root.tagName().equals("table", ignoreCase = true)) {
                listOf(root)
            } else {
                root.select("table").take(2)
            }
            tables.any { table ->
                table.attr("width").trim() == "100%" ||
                    FLUID_WIDTH_DECLARATION.containsMatchIn(table.attr("style"))
            }
        }
    }

    /**
     * Find a phone-sized content card nested inside a wider compatibility/background wrapper.
     *
     * NetEase and similar transactional messages frequently use a 600px outer background table
     * for desktop Outlook, but place nearly all meaningful content in a 360–420px inner card. If
     * the outer width is scaled as the document canvas, that already-mobile card becomes tiny. A
     * compact element that contains most of the message is therefore treated as a fluid document.
     */
    private fun detectCompactContentWidth(
        document: Document,
        preferTransactionalCard: Boolean = false,
    ): Int? {
        val body = document.body()
        val totalTextLength = body.text().trim().length.coerceAtLeast(1)
        val totalImageCount = body.select("img").size.coerceAtLeast(1)

        data class CompactCandidate(
            val width: Int,
            val textCoverage: Float,
            val imageCoverage: Float,
            val depth: Int,
        )

        fun depthOf(element: Element): Int {
            var depth = 0
            var parent = element.parent()
            while (parent != null && parent !== body && depth <= MAX_COMPACT_CONTENT_DEPTH) {
                depth += 1
                parent = parent.parent()
            }
            return depth
        }

        val candidates = mutableListOf<CompactCandidate>()
        val minimumCompactWidth = if (preferTransactionalCard) 160 else MIN_COMPACT_CONTENT_PX
        val minimumTextCoverage = if (preferTransactionalCard) 0.28f else 0.52f
        val minimumImageCoverage = if (preferTransactionalCard) 0.42f else 0.62f

        fun addCandidate(element: Element, width: Int) {
            if (width !in minimumCompactWidth..MAX_COMPACT_CONTENT_PX) return
            if (isIconStripContainer(element)) return
            val depth = depthOf(element)
            if (depth > MAX_COMPACT_CONTENT_DEPTH) return
            val textLength = element.text().trim().length
            val imageCount = element.select("img").size
            if (textLength < (if (preferTransactionalCard) 16 else 32) && imageCount == 0) return

            val textCoverage = textLength.toFloat() / totalTextLength.toFloat()
            val imageCoverage = imageCount.toFloat() / totalImageCount.toFloat()
            if (textCoverage < minimumTextCoverage && imageCoverage < minimumImageCoverage) return
            candidates += CompactCandidate(width, textCoverage, imageCoverage, depth)
        }

        document.select("table, td, div, center, section, article, main").forEach { element ->
            parsePixelDimension(element.attr("width"))?.let { addCandidate(element, it) }
            DESKTOP_WIDTH_DECLARATION.findAll(element.attr("style")).forEach { match ->
                val property = match.groupValues[1].lowercase()
                if (property == "width" || property == "max-width") {
                    parsePixelDimension(match.groupValues[2])?.let { addCandidate(element, it) }
                }
            }
        }

        // A number of NetEase templates declare their 280/300/360px phone card only in a class
        // inside <style>. Include matched CSS nodes so the outer 600px Outlook fallback cannot
        // incorrectly force DESKTOP_SCALED mode.
        document.select("style").forEach { styleElement ->
            val css = styleElement.data().ifBlank { styleElement.html() }
            CSS_RULE_BLOCK.findAll(css).forEach ruleLoop@ { rule ->
                val selector = rule.groupValues[1].trim()
                val declarations = rule.groupValues[2]
                val matchingNodes = linkedSetOf<Element>()
                CSS_CLASS_SELECTOR.findAll(selector).forEach { match ->
                    document.getElementsByClass(match.groupValues[1]).forEach(matchingNodes::add)
                }
                CSS_ID_SELECTOR.findAll(selector).forEach { match ->
                    document.getElementById(match.groupValues[1])?.let(matchingNodes::add)
                }
                // Generic tag rules are useful only when they match a small set; otherwise a
                // `table{width:300px}` compatibility reset would mark every nested layout table.
                CSS_CANVAS_TAG.findAll(selector).forEach { match ->
                    val nodes = document.getElementsByTag(match.groupValues[1])
                    if (nodes.size <= 4) nodes.forEach(matchingNodes::add)
                }
                if (matchingNodes.isEmpty()) return@ruleLoop

                DESKTOP_WIDTH_DECLARATION.findAll(declarations).forEach widthLoop@ { match ->
                    val property = match.groupValues[1].lowercase()
                    if (property != "width" && property != "max-width") return@widthLoop
                    val width = parsePixelDimension(match.groupValues[2]) ?: return@widthLoop
                    matchingNodes.forEach { element -> addCandidate(element, width) }
                }
            }
        }

        return candidates.maxWithOrNull(
            compareBy<CompactCandidate> {
                (it.textCoverage * 100f + it.imageCoverage * 28f + it.depth.coerceAtMost(10)).toInt()
            }.thenBy { -kotlin.math.abs(it.width - 320) },
        )?.width
    }

    /**
     * Detect a conventional desktop e-mail canvas. Most newsletters are authored around a
     * 560–700 px outer table and then scaled by mail clients. Treating every nested table as fluid
     * destroys their column geometry, so only shallow, explicitly sized tables are considered.
     */
    private fun detectDesktopCanvas(document: Document): DesktopCanvasCandidate? {
        val body = document.body()
        data class Candidate(val width: Int, val score: Int, val hardWidth: Boolean)

        val candidates = mutableListOf<Candidate>()
        document.select("table, center, div").forEach { table ->
            if (isIconStripContainer(table)) return@forEach
            var depth = 0
            var parent = table.parent()
            while (parent != null && parent !== body && depth <= MAX_DESKTOP_TABLE_DEPTH) {
                depth += 1
                parent = parent.parent()
            }
            if (depth > MAX_DESKTOP_TABLE_DEPTH) return@forEach

            fun add(rawWidth: String, baseScore: Int, hardWidth: Boolean) {
                val width = parsePixelDimension(rawWidth) ?: return
                if (width !in MIN_DESKTOP_CANVAS_PX..MAX_DESKTOP_CANVAS_PX) return

                // A tiny icon/layout table that happens to declare a large width should not turn
                // an otherwise fluid transactional message into a desktop document.
                val meaningfulContent = table.text().length >= 48 ||
                    table.select("img").size >= 2 ||
                    table.select("tr").size >= 3
                if (!meaningfulContent) return

                candidates += Candidate(
                    width = width,
                    score = (baseScore - depth).coerceAtLeast(1),
                    hardWidth = hardWidth,
                )
            }

            val tagBonus = if (table.tagName().equals("table", ignoreCase = true)) 2 else 0
            add(table.attr("width"), baseScore = 8 + tagBonus, hardWidth = true)
            val inlineStyle = table.attr("style")
            DESKTOP_WIDTH_DECLARATION.findAll(inlineStyle).forEach { match ->
                val property = match.groupValues[1].lowercase()
                val score = when (property) {
                    "width" -> 7 + tagBonus
                    "max-width" -> 6 + tagBonus
                    else -> 4 + tagBonus
                }
                add(
                    rawWidth = match.groupValues[2],
                    baseScore = score,
                    hardWidth = property == "width",
                )
            }
        }

        // A large number of production newsletters keep their 560/600/640 px outer canvas only
        // in a <style> block (for example `.email-wrapper { width:600px }`). The previous detector
        // only inspected HTML attributes and inline styles, so those templates stayed in FLUID mode
        // and WebView clipped the right-hand side. Only count a CSS rule when its selector resolves
        // to meaningful nodes that are actually present in this document; this avoids treating an
        // unused compatibility rule or a tiny icon class as the whole message canvas.
        document.select("style").forEach { styleElement ->
            val css = styleElement.data().ifBlank { styleElement.html() }
            CSS_RULE_BLOCK.findAll(css).forEach ruleLoop@{ rule ->
                val selector = rule.groupValues[1].trim()
                val declarations = rule.groupValues[2]
                if (!CSS_CANVAS_SELECTOR_HINT.containsMatchIn(selector)) return@ruleLoop

                val matchingNodes = linkedSetOf<Element>()
                CSS_CLASS_SELECTOR.findAll(selector).forEach { match ->
                    document.getElementsByClass(match.groupValues[1]).forEach(matchingNodes::add)
                }
                CSS_ID_SELECTOR.findAll(selector).forEach { match ->
                    document.getElementById(match.groupValues[1])?.let(matchingNodes::add)
                }
                CSS_CANVAS_TAG.findAll(selector).forEach { match ->
                    document.getElementsByTag(match.groupValues[1]).forEach(matchingNodes::add)
                }

                val meaningfulNodes = matchingNodes.filter { element ->
                    !isIconStripContainer(element) && (
                        element.text().length >= 48 ||
                            element.select("img").size >= 2 ||
                            element.select("tr").size >= 3
                        )
                }
                if (meaningfulNodes.isEmpty()) return@ruleLoop

                DESKTOP_WIDTH_DECLARATION.findAll(declarations).forEach declarationLoop@{ match ->
                    val property = match.groupValues[1].lowercase()
                    val width = parsePixelDimension(match.groupValues[2]) ?: return@declarationLoop
                    if (width !in MIN_DESKTOP_CANVAS_PX..MAX_DESKTOP_CANVAS_PX) {
                        return@declarationLoop
                    }

                    // CSS-only evidence is intentionally scored a little lower than a width on an
                    // actual table. One isolated fallback rule is therefore not enough to override
                    // a genuinely responsive message, while repeated wrapper/container rules still
                    // form a strong desktop-canvas signal for Cloudflare-style newsletters.
                    val baseScore = when (property) {
                        "width" -> 5
                        "max-width" -> 4
                        else -> 2
                    }
                    candidates += Candidate(
                        width = width,
                        score = baseScore + meaningfulNodes.size.coerceAtMost(2) - 1,
                        hardWidth = property == "width",
                    )
                }
            }
        }

        if (candidates.isEmpty()) return null

        // Templates often repeat the same outer width on two or three wrapper tables. Aggregate
        // nearby values so a 600/602 px pair is still recognized as one strong canvas signal.
        val grouped = candidates.groupBy { candidate -> ((candidate.width + 5) / 10) * 10 }
        val best = grouped.entries
            .map { (roundedWidth, group) ->
                DesktopCanvasCandidate(
                    width = roundedWidth,
                    score = group.sumOf(Candidate::score),
                    hardWidthScore = group.filter(Candidate::hardWidth).sumOf(Candidate::score),
                )
            }
            .sortedWith(
                compareByDescending<DesktopCanvasCandidate> { it.score }
                    .thenByDescending { it.hardWidthScore }
                    .thenBy { kotlin.math.abs(it.width - 600) },
            )
            .firstOrNull()
            ?: return null

        return best.takeIf { it.score >= MIN_DESKTOP_CANVAS_SCORE }
    }

    /** Keep a desktop newsletter's internal geometry, while removing only viewport-level locks. */
    private fun normalizeDesktopSafety(document: Document) {
        document.select("html, body").forEach { element ->
            val style = element.attr("style")
            if (style.isNotBlank()) {
                var normalized = style
                normalized = POSITION_FIXED.replace(normalized, "${'$'}1position:relative;")
                normalized = OVERFLOW_LOCK.replace(normalized, "${'$'}1overflow:visible;")
                normalized = ROOT_SIZE_DECLARATION.replace(normalized, "${'$'}1")
                element.attr("style", normalized)
            }
            element.removeAttr("width")
            element.removeAttr("height")
        }

        document.body().children().forEach { child ->
            val style = child.attr("style")
            if (style.isBlank()) return@forEach
            var normalized = POSITION_FIXED.replace(style, "${'$'}1position:relative;")
            normalized = OVERFLOW_LOCK.replace(normalized, "${'$'}1overflow:visible;")
            child.attr("style", normalized)
        }
    }

    /**
     * Wrapping the original message changes `body > …` selectors. Retarget those selectors to the
     * new canvas so the sender header never inherits newsletter rules and the original first-child
     * styling remains intact.
     */
    private fun retargetBodyChildSelectors(document: Document, target: String) {
        document.select("style").forEach { style ->
            val source = style.data().ifBlank { style.html() }
            style.html(BODY_DIRECT_CHILD.replace(source) { "$target > " })
        }
    }

    private fun wrapDesktopCanvas(body: Element, width: Int) {
        val originalNodes = body.childNodes().toList()
        val canvas = Element("div")
            .attr("id", "bondmail-desktop-canvas")
            .attr("data-bondmail-width", width.toString())

        originalNodes.forEach { node ->
            node.remove()
            canvas.appendChild(node)
        }

        val content = Element("div")
            .attr("id", "bondmail-message-content")
            .addClass("bondmail-desktop-content")
            .appendChild(canvas)
        body.appendChild(content)
    }

    private fun parsePixelDimension(raw: String): Int? {
        val value = raw.trim().lowercase().removeSuffix("!important").trim()
        if (value.isBlank() || value.endsWith('%')) return null
        val numeric = value.removeSuffix("px").trim().toFloatOrNull() ?: return null
        return numeric.toInt()
    }

    private fun normalizeWideLayouts(document: Document, viewportWidthCssPx: Int) {
        val wideThreshold = (viewportWidthCssPx - 24).coerceAtLeast(320).toFloat()
        document.select("body, table, td, th, div, center, section, article, main, figure").forEach { element ->
            val widthAttr = element.attr("width").trim()
            if (isWideDimension(widthAttr, wideThreshold)) {
                element.removeAttr("width")
                element.addClass("bondmail-wide-layout")
            }

            val style = element.attr("style")
            if (style.isNotBlank()) {
                var wide = false
                var normalized = WIDE_WIDTH.replace(style) { match ->
                    val width = match.groupValues[2].toFloatOrNull() ?: return@replace match.value
                    if (width >= wideThreshold) {
                        wide = true
                        "${match.groupValues[1]}width:100%${match.groupValues[3]};"
                    } else {
                        match.value
                    }
                }
                normalized = WIDE_MAX_WIDTH.replace(normalized) { match ->
                    val width = match.groupValues[2].toFloatOrNull() ?: return@replace match.value
                    if (width >= wideThreshold) {
                        wide = true
                        "${match.groupValues[1]}max-width:100%${match.groupValues[3]};"
                    } else {
                        match.value
                    }
                }
                element.attr("style", normalized)
                if (wide) element.addClass("bondmail-wide-layout")
            }
        }

        document.select("style").forEach { style ->
            val source = style.data().ifBlank { style.html() }
            var normalized = WIDE_WIDTH.replace(source) { match ->
                val width = match.groupValues[2].toFloatOrNull() ?: return@replace match.value
                if (width >= wideThreshold) {
                    "${match.groupValues[1]}width:100%${match.groupValues[3]};"
                } else {
                    match.value
                }
            }
            normalized = WIDE_MAX_WIDTH.replace(normalized) { match ->
                val width = match.groupValues[2].toFloatOrNull() ?: return@replace match.value
                if (width >= wideThreshold) {
                    "${match.groupValues[1]}max-width:100%${match.groupValues[3]};"
                } else {
                    match.value
                }
            }
            normalized = WIDE_MIN_WIDTH.replace(normalized) { match ->
                val width = match.groupValues[2].toFloatOrNull() ?: return@replace match.value
                if (width >= wideThreshold) match.groupValues[1] else match.value
            }
            style.html(normalized)
        }

        removeWideMinimumWidths(document, wideThreshold)
    }

    private fun normalizeCompactPrimaryContent(
        document: Document,
        viewportWidthCssPx: Int,
        preferTransactionalCard: Boolean = false,
    ) {
        val body = document.body()
        val totalText = body.text().trim().length.coerceAtLeast(1)
        val totalImages = body.select("img").size.coerceAtLeast(1)
        data class Candidate(val element: Element, val coverage: Float, val depth: Int, val width: Int)

        fun depthOf(element: Element): Int {
            var depth = 0
            var parent = element.parent()
            while (parent != null && parent !== body && depth <= MAX_COMPACT_CONTENT_DEPTH) {
                depth += 1
                parent = parent.parent()
            }
            return depth
        }

        val maximumCompactWidth = (viewportWidthCssPx - 8).coerceIn(240, 440)
        val minimumCompactWidth = if (preferTransactionalCard) 160 else MIN_COMPACT_CONTENT_PX
        val minimumCoverage = if (preferTransactionalCard) 0.35f else 0.66f
        val candidates = mutableListOf<Candidate>()
        fun consider(element: Element, declaredWidth: Int) {
            if (isIconStripContainer(element)) return
            val depth = depthOf(element)
            if (depth > MAX_COMPACT_CONTENT_DEPTH) return
            if (declaredWidth !in minimumCompactWidth..maximumCompactWidth) return

            val textCoverage = element.text().trim().length.toFloat() / totalText.toFloat()
            val imageCoverage = element.select("img").size.toFloat() / totalImages.toFloat()
            val coverage = textCoverage + imageCoverage * 0.20f
            if (coverage < minimumCoverage) return
            candidates += Candidate(element, coverage, depth, declaredWidth)
        }

        document.select("table, td, div, center, section, article, main").forEach { element ->
            parsePixelDimension(element.attr("width"))?.let { consider(element, it) }
            DESKTOP_WIDTH_DECLARATION.findAll(element.attr("style")).forEach { match ->
                val property = match.groupValues[1].lowercase()
                if (property == "width" || property == "max-width") {
                    parsePixelDimension(match.groupValues[2])?.let { consider(element, it) }
                }
            }
        }

        document.select("style").forEach { styleElement ->
            val css = styleElement.data().ifBlank { styleElement.html() }
            CSS_RULE_BLOCK.findAll(css).forEach ruleLoop@ { rule ->
                val selector = rule.groupValues[1].trim()
                val declarations = rule.groupValues[2]
                val matchingNodes = linkedSetOf<Element>()
                CSS_CLASS_SELECTOR.findAll(selector).forEach { match ->
                    document.getElementsByClass(match.groupValues[1]).forEach(matchingNodes::add)
                }
                CSS_ID_SELECTOR.findAll(selector).forEach { match ->
                    document.getElementById(match.groupValues[1])?.let(matchingNodes::add)
                }
                if (matchingNodes.isEmpty()) return@ruleLoop
                DESKTOP_WIDTH_DECLARATION.findAll(declarations).forEach widthLoop@ { match ->
                    val property = match.groupValues[1].lowercase()
                    if (property != "width" && property != "max-width") return@widthLoop
                    val width = parsePixelDimension(match.groupValues[2]) ?: return@widthLoop
                    matchingNodes.forEach { element -> consider(element, width) }
                }
            }
        }

        val selectedCandidate = candidates.maxWithOrNull(
            compareBy<Candidate> { (it.coverage * 100f).toInt() + it.depth }
                .thenBy { -kotlin.math.abs(it.width - 320) },
        ) ?: if (preferTransactionalCard) {
            // Some NetEase mobile cards have no width attribute at all; the only 300/360px rule
            // lives in a nested media block that a flat CSS parser cannot associate reliably. Pick
            // the deepest table that contains most of the actual message, while the wider Outlook
            // wrapper has already been normalized to the viewport by normalizeWideLayouts().
            document.select("table, center, div, section, article, main")
                .mapNotNull { element ->
                    val depth = depthOf(element)
                    if (depth > MAX_COMPACT_CONTENT_DEPTH) return@mapNotNull null
                    val textCoverage = element.text().trim().length.toFloat() / totalText.toFloat()
                    val imageCoverage = element.select("img").size.toFloat() / totalImages.toFloat()
                    val coverage = textCoverage + imageCoverage * 0.20f
                    if (coverage < 0.70f) null else Candidate(element, coverage, depth, 0)
                }
                .maxWithOrNull(
                    compareBy<Candidate> { (it.coverage * 100f).toInt() + it.depth * 2 }
                        .thenBy { if (it.element.tagName().equals("table", true)) 1 else 0 },
                )
        } else {
            null
        } ?: return
        val selected = selectedCandidate.element

        selected.removeAttr("width")
        selected.addClass("bondmail-compact-primary")
        selected.attr(
            "style",
            selected.attr("style") +
                ";width:calc(100% - 16px)!important;min-width:0!important;" +
                "max-width:440px!important;margin-left:auto!important;" +
                "margin-right:auto!important;box-sizing:border-box!important",
        )
        if (!selected.tagName().equals("table", ignoreCase = true)) {
            selected.select("table").firstOrNull()?.addClass("bondmail-wide-layout")
        }
        MailLog.d(
            MailLog.WEB,
            "fluid compact expanded tag=${selected.tagName()} declaredWidth=${selectedCandidate.width} " +
                "coverage=${"%.2f".format(java.util.Locale.ROOT, selectedCandidate.coverage)} " +
                "depth=${selectedCandidate.depth} text=${selected.text().length} " +
                "images=${selected.select("img").size}",
        )
    }

    private fun normalizeLockedLayouts(document: Document) {
        document.select("html, body, table, tbody, tr, td, th, div, center, section, article, main").forEach { element ->
            var unlock = false
            val heightAttr = element.attr("height").trim()
            if (isLockedHeight(heightAttr)) {
                element.removeAttr("height")
                unlock = true
            }
            if (element.hasAttr("nowrap")) {
                element.removeAttr("nowrap")
                if (element.text().length > 20) element.addClass("bondmail-wrap-text")
            }

            val style = element.attr("style")
            if (style.isNotBlank()) {
                var normalized = style
                if (POSITION_FIXED.containsMatchIn(normalized)) {
                    normalized = POSITION_FIXED.replace(normalized, "${'$'}1position:relative;")
                    unlock = true
                }
                if (OVERFLOW_LOCK.containsMatchIn(normalized)) {
                    normalized = OVERFLOW_LOCK.replace(normalized, "${'$'}1overflow:visible;")
                    unlock = true
                }
                normalized = HEIGHT_DECLARATION.replace(normalized) { match ->
                    val property = match.groupValues[2].lowercase()
                    val value = match.groupValues[3].trim()
                    if (isLockedHeight(value)) {
                        unlock = true
                        val replacement = if (property.startsWith("min-")) "0" else "auto"
                        "${match.groupValues[1]}${match.groupValues[2]}:$replacement${match.groupValues[4]};"
                    } else {
                        match.value
                    }
                }
                element.attr("style", normalized)
            }
            if (unlock) element.addClass("bondmail-auto-height")
        }

        // Responsive e-mail rules often put `height:100%` or `min-height:100vh` in a <style>
        // block rather than inline. On a WebView those declarations can turn one receipt section
        // into a full-screen spacer. Normalize only locked heights; leave visual overflow and
        // positioning rules untouched so sender branding is preserved.
        document.select("style").forEach { style ->
            val source = style.data().ifBlank { style.html() }
            style.html(
                HEIGHT_DECLARATION.replace(source) { match ->
                    val property = match.groupValues[2].lowercase()
                    val value = match.groupValues[3].trim()
                    if (isLockedHeight(value)) {
                        val replacement = if (property.startsWith("min-")) "0" else "auto"
                        "${match.groupValues[1]}${match.groupValues[2]}:$replacement${match.groupValues[4]};"
                    } else {
                        match.value
                    }
                },
            )
        }
    }

    private fun normalizeOversizedTypography(document: Document) {
        document.select("[style]").forEach { element ->
            element.attr("style", capTypography(element.attr("style")))
        }
        document.select("style").forEach { style ->
            val source = style.data().ifBlank { style.html() }
            style.html(capTypography(source))
        }
    }

    private fun capTypography(css: String): String {
        var result = FONT_SIZE.replace(css) { match ->
            val size = match.groupValues[2].toFloatOrNull() ?: return@replace match.value
            val unit = match.groupValues[3].lowercase()
            val limit = if (unit == "pt") 25.5f else 34f
            if (size > limit) {
                "${match.groupValues[1]}${formatCssNumber(limit)}$unit${match.groupValues[4]}"
            } else {
                match.value
            }
        }
        result = PIXEL_LINE_HEIGHT.replace(result) { match ->
            val size = match.groupValues[2].toFloatOrNull() ?: return@replace match.value
            if (size > 48f) {
                "${match.groupValues[1]}1.28${match.groupValues[3]}"
            } else {
                match.value
            }
        }
        return result
    }

    private fun normalizeLongNoWrapText(document: Document) {
        document.select("[style]").forEach { element ->
            if (element.text().length < 24) return@forEach
            val style = element.attr("style")
            if (WHITE_SPACE_NOWRAP.containsMatchIn(style)) {
                element.attr("style", WHITE_SPACE_NOWRAP.replace(style, "${'$'}1white-space:normal;"))
                element.addClass("bondmail-wrap-text")
            }
        }
    }

    /**
     * Desktop newsletter templates frequently keep 48–96 px side gutters even after their outer
     * 600 px table has been made responsive. On a phone those nested gutters can leave less than
     * half of the viewport for text, or push a quoted message visibly to the right. Keep normal
     * spacing intact and cap only clearly desktop-sized horizontal offsets.
     */
    private fun normalizeHorizontalSpacing(document: Document) {
        document.select("td, th, div, section, article, main, blockquote").forEach { element ->
            val style = element.attr("style")
            if (style.isNotBlank()) element.attr("style", capHorizontalSpacing(style))
        }
        document.select("style").forEach { style ->
            val source = style.data().ifBlank { style.html() }
            style.html(capHorizontalSpacing(source))
        }
    }

    private fun capHorizontalSpacing(css: String): String {
        var result = HORIZONTAL_SPACING.replace(css) { match ->
            val value = match.groupValues[3].toFloatOrNull() ?: return@replace match.value
            if (value >= 48f) {
                "${match.groupValues[1]}${match.groupValues[2]}:24px${match.groupValues[4]};"
            } else {
                match.value
            }
        }
        result = BOX_SPACING_TWO.replace(result) { match ->
            val horizontal = match.groupValues[4].toFloatOrNull() ?: return@replace match.value
            val capped = if (horizontal >= 48f) "24" else match.groupValues[4]
            "${match.groupValues[1]}${match.groupValues[2]}:${match.groupValues[3]}px ${capped}px${match.groupValues[5]};"
        }
        result = BOX_SPACING_THREE.replace(result) { match ->
            val horizontal = match.groupValues[4].toFloatOrNull() ?: return@replace match.value
            val capped = if (horizontal >= 48f) "24" else match.groupValues[4]
            "${match.groupValues[1]}${match.groupValues[2]}:${match.groupValues[3]}px ${capped}px ${match.groupValues[5]}px${match.groupValues[6]};"
        }
        result = BOX_SPACING_FOUR.replace(result) { match ->
            val right = match.groupValues[4].toFloatOrNull() ?: return@replace match.value
            val left = match.groupValues[6].toFloatOrNull() ?: return@replace match.value
            val cappedRight = if (right >= 48f) "24" else match.groupValues[4]
            val cappedLeft = if (left >= 48f) "24" else match.groupValues[6]
            "${match.groupValues[1]}${match.groupValues[2]}:${match.groupValues[3]}px ${cappedRight}px ${match.groupValues[5]}px ${cappedLeft}px${match.groupValues[7]};"
        }
        return result
    }

    private fun removeWideMinimumWidths(document: Document, threshold: Float) {
        document.select("body, table, tbody, tr, td, th, div, center, section, article, main").forEach { element ->
            val style = element.attr("style")
            if (style.isBlank()) return@forEach
            element.attr(
                "style",
                WIDE_MIN_WIDTH.replace(style) { match ->
                    val width = match.groupValues[2].toFloatOrNull() ?: return@replace match.value
                    if (width >= threshold) match.groupValues[1] else match.value
                },
            )
        }
    }

    private fun preparedDocumentCacheKey(
        key: String,
        header: MailWebHeader,
        foregroundCss: String,
        backgroundCss: String,
        linkCss: String,
        mutedCss: String,
        headerSurfaceCss: String,
        avatarBackgroundCss: String,
        avatarForegroundCss: String,
        darkMode: Boolean,
        topContentInsetCssPx: Int,
        subjectBlockHeightCssPx: Int,
        subjectFontSizeSp: Float,
        subjectLineHeightSp: Float,
        senderBlockHeightCssPx: Int,
        viewportWidthCssPx: Int,
        fontScale: Float,
    ): String = buildString {
        append("layout-v51|")
        append(key)
        append("|domain=").append(header.senderAddress.substringAfterLast('@', "").lowercase())
        append("|sender=").append(header.senderName.hashCode())
        append("|avatarText=").append(header.avatarText.hashCode())
        append("|avatar=").append(header.avatarSvg.hashCode())
        append("|attachments=").append(header.attachments.hashCode())
        append('|').append(foregroundCss)
        append('|').append(backgroundCss)
        append('|').append(linkCss)
        append('|').append(mutedCss)
        append('|').append(headerSurfaceCss)
        append('|').append(avatarBackgroundCss)
        append('|').append(avatarForegroundCss)
        append("|dark=").append(darkMode)
        append("|top=").append(topContentInsetCssPx)
        append("|subjectHeight=").append(subjectBlockHeightCssPx)
        append("|subjectFont=").append(subjectFontSizeSp)
        append("|subjectLineHeight=").append(subjectLineHeightSp)
        append("|senderHeight=").append(senderBlockHeightCssPx)
        append("|viewport=").append(viewportWidthCssPx)
        append("|fontScale=").append(fontScale)
    }

    private fun wrapMessageCard(body: Element, header: MailWebHeader) {
        val originalNodes = body.childNodes().toList()
        val messageBody = Element("div").attr("id", "bondmail-message-body")
        originalNodes.forEach { node ->
            node.remove()
            messageBody.appendChild(node)
        }

        val card = Element("section")
            .attr("id", "bondmail-message-card")
            .appendChild(createHeader(header))
        card.appendChild(messageBody)

        body.appendChild(createSubject(header))
        body.appendChild(card)
    }

    private fun createHeader(header: MailWebHeader): Element {
        val nameRow = Element("div")
            .addClass("bondmail-name-row")
            .appendChild(Element("div").addClass("bondmail-name").text(header.senderName))
            .appendChild(Element("div").addClass("bondmail-date").text(header.dateLabel))
        nameRow.appendChild(
            Element("span")
                .addClass("bondmail-header-paperclip")
                .attr("aria-label", if (header.attachments.isNotEmpty()) "attachment" else "")
                .text(if (header.attachments.isNotEmpty()) "📎" else ""),
        )
        return Element("section")
            .attr("id", "bondmail-message-header")
            .appendChild(
                Element("div")
                    .addClass("bondmail-avatar")
                    .apply {
                        if (header.avatarSvg != null) html(header.avatarSvg)
                        else text(header.avatarText)
                    },
            )
            .appendChild(
                Element("div")
                    .addClass("bondmail-meta")
                    .appendChild(nameRow)
                    .appendChild(Element("div").addClass("bondmail-address").text(header.senderAddress))
                    .appendChild(Element("div").addClass("bondmail-muted").text(header.recipient)),
            )
    }

    private fun createAttachments(header: MailWebHeader): Element? {
        if (header.attachments.isEmpty()) return null
        val section = Element("section").attr("id", "bondmail-attachments")
        header.attachments.forEachIndexed { index, attachment ->
            val meta = MailAttachmentCodec.formatSize(attachment.sizeBytes)
            section.appendChild(
                Element("a")
                    .addClass("bondmail-attachment")
                    .attr("href", "bondmail-attachment://open/$index")
                    .appendChild(Element("span").addClass("bondmail-paperclip").attr("aria-hidden", "true").text("📎"))
                    .appendChild(
                        Element("span")
                            .addClass("bondmail-attachment-name")
                            .text(attachment.name),
                    )
                    .apply {
                        if (meta.isNotBlank()) {
                            appendChild(Element("span").addClass("bondmail-attachment-size").text(meta))
                        }
                    },
            )
        }
        return section
    }

    private fun createSubject(header: MailWebHeader): Element =
        Element("section")
            .attr("id", "bondmail-message-subject")
            .appendChild(
                Element("h1")
                    .addClass("bondmail-subject-text")
                    .text(header.subject),
            )

    /**
     * Several newsletter templates advertise dark-mode support and then switch only their text,
     * leaving inline white panels untouched. WebView consequently renders white text on white
     * backgrounds. Disable those sender media queries and let WebView darken the complete light
     * document as one unit instead.
     */
    private fun disableSenderDarkMode(document: Document) {
        document.select("style").forEach { style ->
            val css = style.data().ifBlank { style.html() }
            val normalized = COLOR_SCHEME_DECLARATION.replace(
                DARK_COLOR_SCHEME_QUERY.replace(css, "(min-width:99999px)"),
                "$1",
            )
            if (normalized != css) {
                style.html(normalized)
            }
        }
        document.select("[style*=color-scheme]").forEach { element ->
            element.attr(
                "style",
                COLOR_SCHEME_DECLARATION.replace(element.attr("style"), "$1"),
            )
        }
    }

    /**
     * Preserve only templates that explicitly author both a dark canvas and a light foreground.
     * A dark decorative header without a matching text color is not a native dark message: treating
     * it as one is what produced black inherited text on the Immigration notification.
     */
    private fun hasNativeDarkCanvas(document: Document): Boolean {
        val body = document.body()
        val totalText = body.text().trim().length.coerceAtLeast(1)
        return document.select(
            "body, body > table, body > center, body > div, body > section, body > table > tbody > tr > td",
        ).any { element ->
            val colorSource = buildString {
                append(element.attr("bgcolor")).append(';')
                append(element.attr("background")).append(';')
                append(element.attr("style"))
            }
            val foregroundSource = buildString {
                append(element.attr("color")).append(';')
                append(element.attr("style"))
            }
            val textCoverage = element.text().trim().length.toFloat() / totalText.toFloat()
            textCoverage >= 0.45f &&
                DARK_BACKGROUND_VALUE.containsMatchIn(colorSource) &&
                LIGHT_FOREGROUND_VALUE.containsMatchIn(foregroundSource)
        }
    }

    /**
     * Algorithmic darkening cannot recolor pixels inside transparent PNG/SVG logos. A common
     * black wordmark therefore disappears on a dark mail background even though body text is
     * readable. Give only assets identified as logos a neutral light plate.
     */
    private fun markDarkModeLogoImages(document: Document) {
        document.select("img").forEach { image ->
            val identity = buildString {
                append(image.attr("src")).append(' ')
                append(image.attr("alt")).append(' ')
                append(image.attr("title")).append(' ')
                append(image.className()).append(' ')
                append(image.id())
            }
            if (BRAND_LOGO_HINT.containsMatchIn(identity)) {
                image.addClass("bondmail-dark-logo")
            }
        }
    }

    private fun normalizeKnownBrandLogoImages(document: Document) {
        document.select("img").forEach { image ->
            val identity = buildString {
                append(image.attr("src")).append(' ')
                append(image.attr("alt")).append(' ')
                append(image.attr("title")).append(' ')
                append(image.className()).append(' ')
                append(image.id())
            }
            if (GOOGLE_LOGO_HINT.containsMatchIn(identity)) {
                image.addClass("bondmail-google-logo")
            }
            if (WISE_LOGO_HINT.containsMatchIn(identity)) {
                image.addClass("bondmail-wise-logo")
                image.parent()?.addClass("bondmail-wise-logo-cell")
            }
        }
    }

    /**
     * Marketing senders append transparent analytics sections after the visible footer. Keeping
     * those 1px beacons and zero-width spacer tables in the fluid normalization path can expand an
     * otherwise empty tail to a full white viewport. Remove only consecutive non-visual top-level
     * nodes at the very end of the original message.
     */
    private fun trimTrailingNonVisualSections(document: Document) {
        var container = document.body()
        repeat(3) {
            trimTrailingChildren(container)
            val onlyChild = container.children().singleOrNull() ?: return
            if (onlyChild.ownText().isNotBlank()) return
            container = onlyChild
        }
    }

    private fun trimTrailingChildren(container: Element) {
        container.children().toList().asReversed().forEach { child ->
            if (!isTrailingNonVisualSection(child)) return
            child.remove()
        }
    }

    private fun isTrailingNonVisualSection(element: Element): Boolean {
        val identity = buildString {
            append(element.className()).append(' ')
            append(element.id()).append(' ')
            append(element.ownText())
        }.lowercase()
        val allText = element.text().trim()
        if (
            "zendesk-tag" in identity ||
            "zdtag-" in identity ||
            (allText.startsWith("zdtag-", ignoreCase = true) && allText.length <= 80)
        ) {
            return true
        }

        val media = element.select("img, video, svg")
        val hasMeaningfulMedia = media.any { item ->
            if (!item.tagName().equals("img", ignoreCase = true)) return@any true
            val source = sequenceOf("src", "data-src", "data-original", "data-lazy-src")
                .map(item::attr)
                .firstOrNull(String::isNotBlank)
                .orEmpty()
                .lowercase()
            val width = parsePixelDimension(item.attr("width"))
                ?: STYLE_WIDTH_DECLARATION.find(item.attr("style"))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let(::parsePixelDimension)
            val height = parsePixelDimension(item.attr("height"))
                ?: STYLE_HEIGHT_DECLARATION.find(item.attr("style"))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let(::parsePixelDimension)
            val trackingResource = HEIGHT_HINT_TRACKING_MARKERS.any(source::contains)
            val tinyPixel = width != null && height != null && width <= 2 && height <= 2
            !trackingResource && !tinyPixel
        }
        if (hasMeaningfulMedia) return false

        val visibleText = element.text()
            .replace(INVISIBLE_MAIL_TEXT, "")
            .trim()
        return visibleText.isEmpty() || (
            visibleText.length <= 4 &&
                element.select("[style*=font-size:0], [style*=font-size: 0]").isNotEmpty()
            )
    }

    /**
     * Apple account notices sometimes reference a light-only remote mark. When remote resources are
     * delayed or blocked, WebView renders its rounded image box without the Apple glyph. Replace
     * only the explicitly identified brand image with the bundled vector so the header stays stable.
     */
    private fun replaceAppleBrandLogo(document: Document) {
        val image = document.select("img").firstOrNull { candidate ->
            val identity = buildString {
                append(candidate.attr("src")).append(' ')
                append(candidate.attr("alt")).append(' ')
                append(candidate.attr("title")).append(' ')
                append(candidate.className()).append(' ')
                append(candidate.id())
            }
            APPLE_LOGO_HINT.containsMatchIn(identity)
        } ?: return

        val logo = Element("svg")
            .addClass("bondmail-apple-brand-logo")
            .attr("viewBox", "0 0 24 24")
            .attr("aria-hidden", "true")
        logo.appendChild(Element("path").attr("d", APPLE_LOGO_PATH))
        image.replaceWith(logo)
    }

    /**
     * Keep image decode work off the document commit path without changing when an image is fetched.
     * Native lazy loading is intentionally avoided here: this WebView initially blocks the network
     * and enables it after commit, a sequence where some Chromium versions do not re-arm lazy images.
     */
    private fun markRemoteImagesAsync(document: Document) {
        document.select("img").forEach { image ->
            val source = image.attr("src").trim()
            if (!source.startsWith("https://", ignoreCase = true) &&
                !source.startsWith("http://", ignoreCase = true)
            ) {
                return@forEach
            }
            if (!image.hasAttr("decoding")) image.attr("decoding", "async")
        }
    }

    private fun markSamsungLogoImages(document: Document) {
        document.select("img").forEach { image ->
            val identity = buildString {
                append(image.attr("src")).append(' ')
                append(image.attr("alt")).append(' ')
                append(image.attr("title")).append(' ')
                append(image.className()).append(' ')
                append(image.id())
            }
            if (SAMSUNG_LOGO_HINT.containsMatchIn(identity)) {
                image.addClass("bondmail-samsung-logo")
            }
        }
    }

    private fun isWideDimension(raw: String, threshold: Float): Boolean {
        val value = raw.trim().lowercase()
        if (value.isBlank()) return false
        if (value.endsWith('%')) {
            return value.removeSuffix("%").toFloatOrNull()?.let { it > 100f } == true
        }
        val number = value.removeSuffix("px").toFloatOrNull() ?: return false
        return number >= threshold
    }

    private fun isLockedHeight(raw: String): Boolean {
        val value = raw.trim().lowercase().removeSuffix("!important").trim()
        if (value.isBlank() || value == "auto" || value == "inherit" || value == "initial") return false
        if (value.contains("vh") || value.contains("vw") || value.contains("calc(")) return true
        if (value.endsWith('%')) {
            return value.removeSuffix("%").toFloatOrNull()?.let { it >= 80f } == true
        }
        val number = value.removeSuffix("px").toFloatOrNull() ?: return false
        return number >= 260f
    }

    private fun formatCssNumber(value: Float): String {
        val rounded = kotlin.math.round(value * 100f) / 100f
        return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    }

    private val BODY_DIRECT_FIRST_TAG = Regex(
        """(?i)body\s*>\s*([a-z][a-z0-9_-]*(?:[.#][a-z0-9_-]+)*)\s*:\s*first-child""",
    )

    private val BODY_DIRECT_FIRST_ANY = Regex(
        """(?i)body\s*>\s*\*\s*:\s*first-child""",
    )

    private val BODY_DIRECT_CHILD = Regex("""(?i)\bbody\s*>\s*""")

    private val DESKTOP_WIDTH_DECLARATION = Regex(
        "(?i)(?:^|;)\\s*(width|max-width|min-width)\\s*:\\s*(\\d+(?:\\.\\d+)?(?:px)?)\\s*(?:!important)?\\s*;?",
    )

    /** Flat CSS rule parser; inner rules inside @media blocks are still matched independently. */
    private val CSS_RULE_BLOCK = Regex("""(?is)([^{}]+)\{([^{}]*)\}""")

    private val CSS_CLASS_SELECTOR = Regex("\\.([A-Za-z_][A-Za-z0-9_-]*)")

    private val CSS_ID_SELECTOR = Regex("#([A-Za-z_][A-Za-z0-9_-]*)")

    private val CSS_CANVAS_TAG = Regex(
        "(?i)(?:^|[\\s,>+~])(html|body|table|center|div|main|article|section)(?=$|[\\s,.#:\\[>+~])",
    )

    private val CSS_CANVAS_SELECTOR_HINT = Regex(
        "(?i)(?:html|body|table|center|wrapper|container|content|email|mail|outer|layout|frame|main|article|section)",
    )

    private val ROOT_SIZE_DECLARATION = Regex(
        "(?i)(^|;)\\s*(?:min-|max-)?(?:width|height)\\s*:\\s*[^;]+\\s*(?:!important)?\\s*;?",
    )

    private val WIDE_MIN_WIDTH = Regex(
        "(?i)(^|[;{])\\s*min-width\\s*:\\s*(\\d+(?:\\.\\d+)?)px\\s*(?:!important)?\\s*;?",
    )

    private val WIDE_WIDTH = Regex(
        "(?i)(^|[;{])\\s*width\\s*:\\s*(\\d+(?:\\.\\d+)?)px(\\s*!important)?\\s*;?",
    )

    private val WIDE_MAX_WIDTH = Regex(
        "(?i)(^|[;{])\\s*max-width\\s*:\\s*(\\d+(?:\\.\\d+)?)px(\\s*!important)?\\s*;?",
    )

    private val HEIGHT_DECLARATION = Regex(
        "(?i)(^|[;{])\\s*((?:min-)?height)\\s*:\\s*([^;]+)(\\s*!important)?\\s*;?",
    )

    private val POSITION_FIXED = Regex(
        "(?i)(^|;)\\s*position\\s*:\\s*fixed\\s*(?:!important)?\\s*;?",
    )

    private val OVERFLOW_LOCK = Regex(
        "(?i)(^|;)\\s*overflow(?:-[xy])?\\s*:\\s*hidden\\s*(?:!important)?\\s*;?",
    )

    private val FONT_SIZE = Regex(
        "(?i)(font-size\\s*:\\s*)(\\d+(?:\\.\\d+)?)(px|pt)(\\s*!important)?",
    )

    private val PIXEL_LINE_HEIGHT = Regex(
        "(?i)(line-height\\s*:\\s*)(\\d+(?:\\.\\d+)?)px(\\s*!important)?",
    )

    private val WHITE_SPACE_NOWRAP = Regex(
        "(?i)(^|;)\\s*white-space\\s*:\\s*nowrap\\s*(?:!important)?\\s*;?",
    )

    private val HORIZONTAL_SPACING = Regex(
        "(?i)(^|[;{])\\s*((?:padding|margin)-(?:left|right))\\s*:\\s*" +
            "(\\d+(?:\\.\\d+)?)px(\\s*!important)?\\s*;?",
    )

    private val BOX_SPACING_TWO = Regex(
        "(?i)(^|[;{])\\s*(padding|margin)\\s*:\\s*" +
            "(\\d+(?:\\.\\d+)?)px\\s+(\\d+(?:\\.\\d+)?)px(\\s*!important)?\\s*;?",
    )

    private val BOX_SPACING_THREE = Regex(
        "(?i)(^|[;{])\\s*(padding|margin)\\s*:\\s*" +
            "(\\d+(?:\\.\\d+)?)px\\s+(\\d+(?:\\.\\d+)?)px\\s+" +
            "(\\d+(?:\\.\\d+)?)px(\\s*!important)?\\s*;?",
    )

    private val BOX_SPACING_FOUR = Regex(
        "(?i)(^|[;{])\\s*(padding|margin)\\s*:\\s*" +
            "(\\d+(?:\\.\\d+)?)px\\s+(\\d+(?:\\.\\d+)?)px\\s+" +
            "(\\d+(?:\\.\\d+)?)px\\s+(\\d+(?:\\.\\d+)?)px" +
            "(\\s*!important)?\\s*;?",
    )

    private val RESPONSIVE_MEDIA_QUERY = Regex(
        "(?is)@media[^\\{]{0,180}\\((?:max-device-width|max-width)\\s*:\\s*\\d+(?:\\.\\d+)?(?:px|em|rem)\\)",
    )

    private val DARK_COLOR_SCHEME_QUERY = Regex(
        """(?i)\(\s*prefers-color-scheme\s*:\s*dark\s*\)""",
    )

    private val COLOR_SCHEME_DECLARATION = Regex(
        """(?i)((?:^|[;{])\s*)(?:supported-)?color-scheme\s*:\s*[^;]+;?""",
    )

    private val DARK_BACKGROUND_VALUE = Regex(
        """(?i)(?:#(?:000(?:000)?|080808|0[abcde]0[abcde]0[abcde]|111(?:111)?|121212|181818)|""" +
            """rgba?\(\s*(?:0|8|10|11|12|17|18|24)\s*,\s*(?:0|8|10|11|12|17|18|24)\s*,)""",
    )

    private val LIGHT_FOREGROUND_VALUE = Regex(
        """(?i)(?:^|;)\s*(?:color|-webkit-text-fill-color)\s*:\s*""" +
            """(?:#(?:fff(?:fff)?|f[0-9a-f]f[0-9a-f]f[0-9a-f])|white|""" +
            """rgba?\(\s*(?:2[0-5]\d)\s*,\s*(?:2[0-5]\d)\s*,\s*(?:2[0-5]\d)\s*(?:,|\)))""",
    )

    private val BRAND_LOGO_HINT = Regex(
        """(?i)(?:^|[/_.\-\s])(logo|wordmark|brand)(?:[/_.\-\s]|$)""",
    )

    private val APPLE_LOGO_HINT = Regex(
        """(?i)(?:\bapple\b|apple[_-]?logo|logo[_-]?apple)""",
    )

    private const val APPLE_LOGO_PATH =
        "M12.152 6.896c-.948 0-2.415-1.078-3.96-1.04-2.04.027-3.91 1.183-" +
            "4.961 3.014-2.117 3.675-.546 9.103 1.519 12.09 1.013 1.454 2.208 3.09 " +
            "3.792 3.039 1.52-.065 2.09-.987 3.935-.987 1.831 0 2.35.987 3.96.948 " +
            "1.637-.026 2.676-1.48 3.676-2.948 1.156-1.688 1.636-3.325 1.662-" +
            "3.415-.039-.013-3.182-1.221-3.22-4.857-.026-3.04 2.48-4.494 2.597-" +
            "4.559-1.429-2.09-3.623-2.324-4.39-2.376-2-.156-3.675 1.09-4.61 " +
            "1.09zM15.53 3.83c.843-1.012 1.4-2.427 1.245-3.83-1.207.052-2.662.805-" +
            "3.532 1.818-.78.896-1.454 2.338-1.273 3.714 1.338.104 2.715-.688 " +
            "3.559-1.701"

    private val GOOGLE_LOGO_HINT = Regex(
        """(?i)(?:google|gmail)[/_.\-\s]*(?:logo|wordmark)|(?:logo|wordmark)[/_.\-\s]*(?:google|gmail)""",
    )
    private val WISE_LOGO_HINT = Regex(
        """(?i)(?:\bwise\b|transferwise).{0,80}(?:logo|wordmark)|""" +
            """(?:logo|wordmark).{0,80}(?:\bwise\b|transferwise)""",
    )
    private val SAMSUNG_LOGO_HINT = Regex(
        """(?i)samsung.{0,64}(?:logo|wordmark)|(?:logo|wordmark).{0,64}samsung|[/_-]samsung[/_.-]""",
    )

    private val FLUID_WIDTH_DECLARATION = Regex(
        "(?i)(?:^|;)\\s*(?:width|max-width)\\s*:\\s*100%\\s*(?:!important)?\\s*;?",
    )
    private val REMOTE_IMAGE_PATTERN = Regex(
        """(?is)(?:src|srcset|background|poster)\s*=\s*[\"']?\s*(?:https?:)?//|url\(\s*[\"']?\s*(?:https?:)?//""",
    )

    private val EMOJI_PRESENTATION_SYMBOLS = listOf(
        "🖼",
        "🛡",
    )

    private val LAZY_IMAGE_ATTRIBUTES = listOf(
        "data-src",
        "data-original",
        "data-lazy-src",
        "data-url",
        "data-image",
        "data-cfsrc",
    )
    private const val HEIGHT_HINT_CHARS_PER_LINE = 40
    private val HEIGHT_HINT_TRACKING_MARKERS = listOf(
        "tracking",
        "tracker",
        "beacon",
        "open.gif",
        "open.png",
        "pixel.gif",
        "1x1",
        "spacer",
        "transparent",
    )
    private val INVISIBLE_MAIL_TEXT = Regex(
        """[\u0000-\u0020\u00a0\u200b-\u200f\u2060\ufeff]+""",
    )

    private val STYLE_WIDTH_DECLARATION = Regex(
        """(?i)(?:^|;)\s*width\s*:\s*([^;]+)""",
    )
    private val STYLE_HEIGHT_DECLARATION = Regex(
        """(?i)(?:^|;)\s*height\s*:\s*([^;]+)""",
    )
    private val PROTOCOL_RELATIVE_CSS_URL = Regex(
        """(?i)url\(\s*["']?//([^\)"']+)["']?\s*\)""",
    )
    private val CSS_URL_PATTERN = Regex(
        """(?is)url\(\s*["']?([^"')]+)["']?\s*\)""",
    )

    private const val KNOWN_DESKTOP_CANVAS_FALLBACK_PX = 600
    private const val INSTAGRAM_SIDE_GUTTERS_PX = 32
    private const val DESKTOP_TEXT_ZOOM_PERCENT = 118
    private const val GRAB_TEXT_ZOOM_PERCENT = 106
    private const val FACEBOOK_TEXT_ZOOM_PERCENT = 132
    private const val MIN_DESKTOP_CANVAS_PX = 480
    private const val MAX_DESKTOP_CANVAS_PX = 1200
    private const val MAX_DESKTOP_TABLE_DEPTH = 6
    private const val MIN_DESKTOP_CANVAS_SCORE = 5
    private const val STRONG_DESKTOP_SCORE = 8
    private const val STRONG_DESKTOP_HARD_SCORE = 8
    private const val MIN_COMPACT_CONTENT_PX = 220
    private const val MAX_COMPACT_CONTENT_PX = 479
    private const val MAX_COMPACT_CONTENT_DEPTH = 10

}
