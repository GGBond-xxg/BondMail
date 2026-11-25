package com.bond.mail.ui.components

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailWebViewCacheTest {
    @Test fun completeCathayPhoneRulesReflowNightStatementWithoutChangingDayCanvas() = runBlocking {
        val html = """<html><head><style>
            body{min-width:700px}.deviceWidth{width:700px!important}
            @media screen and (max-width:620px){
              body{min-width:0!important}.deviceWidth{width:100%!important}
              .deviceWidthLarger{width:100%!important}
              .fullWidthBlock{display:block!important;float:none;width:auto!important}
            }
            </style></head><body><table width='700' class='deviceWidth'><tr><td>
            <img id='height-only-logo' height='22' width='' src='data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="800" height="200"%3E%3C/svg%3E'>
            <p id='readable-title' style='font-size:20px'>A full size statement with mobile reading rules</p>
            <table width='100%'><tr><td class='fullWidthBlock'>
            <table width='349' class='deviceWidthLarger'><tr><td id='mobile-left'>Account balance and miles</td></tr></table>
            </td><td class='fullWidthBlock'>
            <table width='349' class='deviceWidthLarger'><tr><td id='mobile-right'>Status points and membership</td></tr></table>
            </td></tr></table></td></tr></table></body></html>"""
        val night = prepare("statement@e.cathaypacific.com", html, darkMode = true)
        assertEquals(MailDocumentLayout.FLUID, night.layout)
        assertEquals(MailDocumentLayout.DESKTOP_SCALED,
            prepare("statement@e.cathaypacific.com", html, darkMode = false).layout)
        val result = renderedGeometry(night, """
            const b=document.getElementById('bondmail-message-body');
            const l=document.getElementById('mobile-left').getBoundingClientRect();
            const r=document.getElementById('mobile-right').getBoundingClientRect();
            return [b.scrollWidth-b.clientWidth,r.top-l.top,l.width,
                parseFloat(getComputedStyle(document.getElementById('readable-title')).fontSize),
                parseFloat(getComputedStyle(b).paddingLeft),
                document.getElementById('height-only-logo').getBoundingClientRect().height];
        """)
        assertTrue("Phone layout cannot overflow", result.getDouble(0) <= 1)
        assertTrue("Columns must stack", result.getDouble(1) > 0)
        assertTrue("Text uses the available phone width", result.getDouble(2) > 250)
        assertEquals(20.0, result.getDouble(3), .1)
        assertEquals("Sender already supplies phone gutters", 0.0, result.getDouble(4), .1)
        assertEquals("Height-only logos keep their authored size", 22.0, result.getDouble(5), .1)
    }

    @Test
    fun responsiveTableRetainsSideGutters() = runBlocking {
        val prepared = prepare("notice@example.com", """
            <html><head><style>
              @media only screen and (max-width:600px) { .inner { width:84%!important } }
            </style></head><body>
              <table width="100%" cellpadding="0" cellspacing="0"><tr><td>
                <table id="outer" width="600" style="width:600px" cellpadding="0" cellspacing="0"><tr>
                  <td style="border-top:4px solid #eb1000;background:#fff">
                    <table id="inner" class="inner" width="500" style="width:500px" align="center"><tr>
                      <td style="height:120px">A welcome message with readable side margins.</td>
                    </tr></table>
                  </td></tr></table>
              </td></tr></table>
            </body></html>
        """.trimIndent(), darkMode = true)
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val ready = java.util.concurrent.CountDownLatch(1)
        val result = java.util.concurrent.atomic.AtomicReference<String>()
        var webView: android.webkit.WebView? = null
        instrumentation.runOnMainSync {
            webView = android.webkit.WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                settings.useWideViewPort = true
                layout(0, 0, 720, 1600)
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView, url: String?) {
                        view.evaluateJavascript("""
                            (function() {
                              var o=document.getElementById('outer').getBoundingClientRect();
                              var i=document.getElementById('inner').getBoundingClientRect();
                              var b=document.getElementById('bondmail-message-body').getBoundingClientRect();
                              return [i.width/o.width,i.left-b.left,b.right-i.right];
                            })()
                        """.trimIndent()) { result.set(it); ready.countDown() }
                    }
                }
                loadDataWithBaseURL("https://example.invalid", prepared.html, "text/html", "UTF-8", null)
            }
        }
        try {
            assertTrue(ready.await(15, java.util.concurrent.TimeUnit.SECONDS))
            val bounds = org.json.JSONArray(result.get())
            assertEquals("Sender's responsive width must survive normalization", 0.84, bounds.getDouble(0), 0.02)
            assertTrue("Left gutter must remain", bounds.getDouble(1) >= 16)
            assertTrue("Right gutter must remain", bounds.getDouble(2) >= 16)
        } finally {
            instrumentation.runOnMainSync { webView?.destroy() }
        }
    }

    @Test
    fun smallHeaderLogoDoesNotOverrideFixedItineraryColumns() = runBlocking {
        // Anonymized structure from an 800px itinerary: its only image is in a 314px logo cell.
        val prepared = prepare("notice@example.com", """
            <html><body>
            <table width="800"><tr><td width="20"></td><td width="314">
              <img src="https://example.invalid/logo.png" width="90" height="70">
            </td><td width="436">Electronic ticket itinerary</td></tr></table>
            <table width="800"><tr><td width="20"></td>
              <td width="390">Passenger name / NAME: TEST PERSON</td>
              <td width="390">电子票号 / ETKT NO: 000-1234567890</td></tr>
              <tr><td width="20"></td><td width="390">AIRLINE PNR: ABCDEF</td>
              <td width="390">ISSUED: 2026-01-01</td></tr></table>
            </body></html>
        """.trimIndent())
        assertEquals(MailDocumentLayout.DESKTOP_SCALED, prepared.layout)
        assertEquals(800, prepared.desktopCanvasWidthPx)
        val document = org.jsoup.Jsoup.parse(prepared.html)
        assertEquals(4, document.select("#bondmail-desktop-canvas td[width=390]").size)
        assertFalse(document.select("#bondmail-desktop-canvas .bondmail-wide-layout").isNotEmpty())
    }

    @Test
    fun actualCompactImageMessageStaysFluid() = runBlocking {
        val prepared = prepare("notice@example.com", """
            <html><body><table width="800"><tr><td><table width="320"><tr><td>
              <img src="https://example.invalid/poster.png" width="320" height="480">
            </td></tr></table></td></tr></table><p>Message footer</p></body></html>
        """.trimIndent())
        assertEquals(MailDocumentLayout.FLUID, prepared.layout)
    }

    @Test
    fun renderedHeaderKeepsDateAtRightAndBodyGapOutsideCard() = runBlocking {
        val prepared = prepare("notice@example.com", "<html><body><p>Newsletter body</p></body></html>")
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val ready = java.util.concurrent.CountDownLatch(1)
        val result = java.util.concurrent.atomic.AtomicReference<String>()
        var webView: android.webkit.WebView? = null
        instrumentation.runOnMainSync {
            webView = android.webkit.WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                settings.useWideViewPort = true
                layout(0, 0, 720, 1200)
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView, url: String?) {
                        view.evaluateJavascript("""
                            (function() {
                                var h=document.getElementById('bondmail-message-header').getBoundingClientRect();
                                var b=document.getElementById('bondmail-message-body').getBoundingClientRect();
                                var d=document.querySelector('.bondmail-date').getBoundingClientRect();
                                return [b.top-h.bottom,h.right-d.right];
                            })()
                        """.trimIndent()) { result.set(it); ready.countDown() }
                    }
                }
                loadDataWithBaseURL("https://example.invalid", prepared.html, "text/html", "UTF-8", null)
            }
        }
        try {
            assertTrue("WebView should render the document", ready.await(15, java.util.concurrent.TimeUnit.SECONDS))
            val bounds = org.json.JSONArray(result.get())
            assertEquals("Body must start below the card", 12.0, bounds.getDouble(0), 0.5)
            assertEquals("Date must share the right content inset", 16.0, bounds.getDouble(1), 0.5)
        } finally {
            instrumentation.runOnMainSync { webView?.destroy() }
        }
    }

    @Test
    fun responsiveFallbackCanvasIsKeptFluid() = runBlocking {
        val prepared = prepare(
            senderAddress = "google-gemini-noreply@google.com",
            html = """
                <html>
                  <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <style>
                      .outer, .content { width:600px; }
                      @media only screen and (max-width:600px) {
                        .outer, .content { width:100% !important; }
                      }
                    </style>
                  </head>
                  <body>
                    <table class="outer" width="600"><tr><td>
                      <table class="content" width="600">
                        <tr><td><h1>Make more with Gemini</h1></td></tr>
                        <tr><td>Plan a trip, write a story, and explore more ideas on your phone.</td></tr>
                        <tr><td><img src="https://example.invalid/hero.png" width="560" height="320"></td></tr>
                      </table>
                    </td></tr></table>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertEquals(MailDocumentLayout.FLUID, prepared.layout)
        assertEquals(null, prepared.desktopCanvasWidthPx)
        assertFalse(prepared.html.contains("id=\"bondmail-desktop-canvas\""))
    }

    @Test
    fun fixedDesktopNewsletterStillUsesWholeCanvasScaling() = runBlocking {
        val prepared = prepare(
            senderAddress = "newsletter@example.com",
            html = """
                <html><body>
                  <table width="600">
                    <tr><td><h1>Monthly summary</h1></td></tr>
                    <tr><td>This fixed newsletter contains enough meaningful desktop content.</td></tr>
                    <tr><td><img src="https://example.invalid/one.png"><img src="https://example.invalid/two.png"></td></tr>
                  </table>
                </body></html>
            """.trimIndent(),
        )

        assertEquals(MailDocumentLayout.DESKTOP_SCALED, prepared.layout)
        assertEquals(600, prepared.desktopCanvasWidthPx)
        assertTrue(prepared.html.contains("id=\"bondmail-desktop-canvas\""))
    }

    @Test
    fun knownFixedCanvasExceptionRemainsScaled() = runBlocking {
        val prepared = prepare(
            senderAddress = "updates@cloudflare.com",
            html = """
                <html>
                  <head><meta name="viewport" content="width=device-width"></head>
                  <body><table width="600">
                    <tr><td><h1>Cloudflare update</h1></td></tr>
                    <tr><td>This known template intentionally keeps its complete desktop canvas.</td></tr>
                    <tr><td>More account and product information appears in this section.</td></tr>
                  </table></body>
                </html>
            """.trimIndent(),
        )

        assertEquals(MailDocumentLayout.DESKTOP_SCALED, prepared.layout)
        assertEquals(600, prepared.desktopCanvasWidthPx)
    }

    @Test
    fun formWrapperKeepsVisibleMailAndDropsInactiveControlsAndTrackingPixel() = runBlocking {
        val prepared = prepare(
            senderAddress = "notice@example.com",
            html = """
                <html><body><form action="https://example.invalid/collect">
                  <h2>Your order is ready</h2>
                  <p>The complete visible message must remain readable.</p>
                  <button type="submit">View order</button>
                  <input name="token" value="secret">
                  <img src="https://example.invalid/open.gif" width="1" height="1">
                </form></body></html>
            """.trimIndent(),
        )

        assertTrue(prepared.html.contains("Your order is ready"))
        assertTrue(prepared.html.contains("View order"))
        assertFalse(prepared.html.contains("<form"))
        assertFalse(prepared.html.contains("<button"))
        assertFalse(prepared.html.contains("<input"))
        assertFalse(prepared.html.contains("open.gif"))
    }

    @Test fun ifastFooterUsesSharedNightPaletteInsteadOfAForcedLightPanel() = runBlocking {
        val prepared = prepare("notice@ifastgb.com", "<table><tr><td>Main text</td></tr><tr><td><font color='white'>iFAST Global Bank Limited</font><p style='color:white'>London E14 9SH</p></td></tr></table>", true)
        val document = org.jsoup.Jsoup.parse(prepared.html)
        assertEquals(1, document.select("td.bondmail-ifast-footer").size)
        assertFalse(prepared.html.contains("background-color:#f4f7fa!important;color:#202124!important"))
        assertTrue(document.body().hasClass("bondmail-dark-mode"))
    }

    @Test
    fun senderPageBackgroundCannotFillRoundedCornersAndContentHasSafeInsets() = runBlocking {
        for (dark in listOf(false, true)) {
            for (mode in com.bond.mail.data.settings.MailDisplayMode.entries) {
                val prepared = prepare("notice@example.com", """
                    <html style="background:white!important"><body style="background:white!important;padding:0!important">
                    <div id="edge-content" style="margin:0;background:white">Text starts at the authored edge.</div>
                    </body></html>
                """.trimIndent(), dark, mode)
                val result = renderedGeometry(prepared, """
                    var b=document.getElementById('bondmail-message-body'), r=b.getBoundingClientRect();
                    var c=(document.getElementById('edge-content') || b.firstElementChild).getBoundingClientRect();
                    return [getComputedStyle(document.body).backgroundColor,
                        getComputedStyle(document.documentElement).backgroundColor,
                        c.left-r.left,r.right-c.right,c.top-r.top,r.bottom-c.bottom,
                        getComputedStyle(b).backgroundColor];
                """.trimIndent())
                val page = if (dark) "rgb(0, 0, 0)" else "rgb(247, 247, 247)"
                assertEquals(page, result.getString(0))
                assertEquals(page, result.getString(1))
                for (index in 2..5) assertEquals("All content edges need safe space", 16.0, result.getDouble(index), 0.5)
                assertFalse("Transparent mail needs a visible reading surface", result.getString(6) == "rgba(0, 0, 0, 0)")
            }
        }
    }

    @Test
    fun fixedCanvasFitsInsideNewBodyInsetsWithoutLosingColumns() = runBlocking {
        val prepared = prepare("updates@cloudflare.com", """
            <html><body><table id="fixed-table" width="600" style="width:600px" cellpadding="0" cellspacing="0"><tr>
            <td id="left-column" width="300">Passenger / NAME: EXAMPLE TRAVELLER</td>
            <td id="right-column" width="300">Ticket / NUMBER: 000-1234567890</td>
            </tr></table></body></html>
        """.trimIndent())
        assertEquals(MailDocumentLayout.DESKTOP_SCALED, prepared.layout)
        val result = renderedGeometry(prepared, """
            var b=document.getElementById('bondmail-message-body').getBoundingClientRect();
            var t=document.getElementById('fixed-table').getBoundingClientRect();
            var l=document.getElementById('left-column').getBoundingClientRect();
            var r=document.getElementById('right-column').getBoundingClientRect();
            return [t.left-b.left,b.right-t.right,l.width,r.width,r.left-l.right];
        """.trimIndent())
        assertTrue(result.getDouble(0) >= 15.5)
        assertTrue(result.getDouble(1) >= 15.5)
        assertTrue("Left column remains readable", result.getDouble(2) > 100)
        assertTrue("Right column remains readable", result.getDouble(3) > 100)
        assertEquals(0.0, result.getDouble(4), 0.5)
    }

    @Test
    fun senderResetsCannotMoveBodyBehindTheNativeHeader() = runBlocking {
        for (mode in com.bond.mail.data.settings.MailDisplayMode.entries) {
            val prepared = prepare("offers@example.com", """
                <html style="padding:9px!important;margin:7px!important">
                <head><style>body.sender-page{padding:0!important;margin:20px!important}</style></head>
                <body class="sender-page" style="padding:0!important;margin:4px!important">
                <div><style>body{padding:0!important}</style></div>
                <div id="first-visible">The beginning of this newsletter must remain visible.</div>
                </body></html>
            """.trimIndent(), displayMode = mode)
            val result = renderedGeometry(prepared, """
                var s=document.getElementById('bondmail-message-subject').getBoundingClientRect();
                var h=document.getElementById('bondmail-message-header').getBoundingClientRect();
                var c=document.getElementById('bondmail-body-clip').getBoundingClientRect();
                return [s.top,s.left,h.top,c.top,c.top-h.bottom,parseFloat(getComputedStyle(document.body).paddingTop)];
            """.trimIndent())
            assertEquals("Subject must retain the chrome inset ($mode)", 64.0, result.getDouble(0), 0.5)
            assertEquals("Sender page margin must not shift the app shell", 0.0, result.getDouble(1), 0.5)
            assertEquals(148.0, result.getDouble(2), 0.5)
            assertEquals("Body corners must be below the native header ($mode)", 272.0, result.getDouble(3), 0.5)
            assertEquals(12.0, result.getDouble(4), 0.5)
            assertEquals(64.0, result.getDouble(5), 0.5)
        }
    }

    @Test
    fun fixedMobileNewsletterTablesFitInsideCardGutters() = runBlocking {
        val prepared = prepare("googleplay-noreply@google.com", """
            <html><head><style>table,td{border-collapse:collapse}td{padding:0}@media(max-width:600px){
              .table-main-gmail{width:360px!important}.table-body-gmail{width:300px!important}
            }</style></head><body><table width="100%"><tr><td style="padding:10px">
              <table class="table-main-gmail" width="600"><tr><td>
                <table id="copy" class="table-body-gmail" width="520" align="center"><tr><td>
                  我们将更新服务条款。这是一段需要在手机正文卡片内完整显示的说明文字，不能裁掉右侧内容。
                </td></tr></table>
              </td></tr></table>
            </td></tr></table></body></html>
        """.trimIndent())
        val geometry = renderedGeometry(prepared, """
            var b=document.getElementById('bondmail-message-body').getBoundingClientRect();
            var c=document.getElementById('copy').getBoundingClientRect();
            var t=document.querySelector('.table-main-gmail').getBoundingClientRect();
            return [b.right-t.right,c.width,t.width-c.width,c.left-b.left];
        """.trimIndent())
        assertTrue("Fixed mobile canvas must not overflow", geometry.getDouble(0) >= 16)
        assertTrue("Copy must remain readable", geometry.getDouble(1) > 200)
        assertEquals(40.0, geometry.getDouble(2), 1.0)
        assertTrue(geometry.getDouble(3) >= 16)
    }

    @Test
    fun smallImageLimitsAndHeightOnlyLogosSurviveFluidRules() = runBlocking {
        val icon = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='56' height='56'%3E%3C/svg%3E"
        val logo = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='198' height='44'%3E%3C/svg%3E"
        val prepared = prepare("newsletter@example.com", """
            <html><meta name="viewport" content="width=device-width"><body><table width="100%"><tr><td>
              <div style="max-width:51.6px"><img id="icon" width="51.6" height="auto" src="$icon" style="width:100%"></div>
              <img id="logo" width="auto" height="32" src="$logo" style="width:auto;height:32px">
              <div style="width:80px"><img id="qr" width="96" height="96" src="$icon"></div>
              <img id="hero" width="516" src="$icon" style="width:100%">
              <p>Original image limits must survive the mobile card's generic constraints.</p>
            </td></tr></table></body></html>
        """.trimIndent())
        val geometry = renderedGeometry(prepared, """
            var r=id=>document.getElementById(id).getBoundingClientRect();
            return [r('icon').width,r('icon').height,r('logo').height,r('qr').width,r('qr').height,r('hero').width];
        """.trimIndent())
        assertEquals(51.6, geometry.getDouble(0), 0.5)
        assertEquals(51.6, geometry.getDouble(1), 0.5)
        assertEquals(32.0, geometry.getDouble(2), 0.5)
        assertEquals(80.0, geometry.getDouble(3), 0.5)
        assertEquals(80.0, geometry.getDouble(4), 0.5)
        assertTrue("Hero images should still fill their column", geometry.getDouble(5) > 250)
    }

    @Test
    fun pairedCodeCaptionsStackWithoutSquashingTextOrCodes() = runBlocking {
        val code = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='96' height='96'%3E%3C/svg%3E"
        fun group(id: String, width: Int) = """
            <td id="$id" width="$width"><table width="100%"><tr>
              <td width="96"><img src="$code" width="96" height="96"></td><td width="10"></td>
              <td id="${id}-copy">关注微信公众号<br>重要消息不错过</td>
            </tr></table></td>
        """.trimIndent()
        val prepared = prepare("notice@example.com", """
            <html><body><table width="100%"><tr><td style="padding:20px">
              <table width="525"><tr>${group("first", 204)}<td width="51"></td>${group("second", 270)}</tr></table>
            </td></tr></table></body></html>
        """.trimIndent())
        val geometry = renderedGeometry(prepared, """
            var r=id=>document.getElementById(id).getBoundingClientRect();
            var i=document.querySelector('#first img').getBoundingClientRect();
            return [r('first-copy').width,r('second-copy').width,r('second').top-r('first').bottom,i.width/i.height];
        """.trimIndent())
        assertTrue(geometry.getDouble(0) >= 100)
        assertTrue(geometry.getDouble(1) >= 100)
        assertTrue("Each code-caption group gets its own line", geometry.getDouble(2) >= 15)
        assertEquals("Codes must stay square", 1.0, geometry.getDouble(3), 0.01)
    }

    @Test
    fun componentNewsletterDoesNotAccumulateOuterTextGutters() = runBlocking {
        val prepared = prepare("notice@example.com", """
            <html><body><table width="100%"><tr><td id="wrapper" class="text__row" style="padding:0 45px">
              <table class="header" width="100%"><tr><td>Brand</td></tr></table>
              <table class="hero-component" width="100%"><tr><td id="copy" style="padding:0 22px">
                A complete component newsletter already provides its own text gutters.
              </td></tr></table>
            </td></tr></table></body></html>
        """.trimIndent())
        val geometry = renderedGeometry(prepared, """
            return [parseFloat(getComputedStyle(document.getElementById('wrapper')).paddingLeft),
              parseFloat(getComputedStyle(document.getElementById('copy')).paddingLeft)];
        """.trimIndent())
        assertEquals(0.0, geometry.getDouble(0), 0.1)
        assertEquals(22.0, geometry.getDouble(1), 0.1)
    }

    @Test
    fun percentageBannerOverridesLegacyWidthWithoutGrowingFooterIcons() = runBlocking {
        val banner = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='2160' height='300'%3E%3C/svg%3E"
        val prepared = prepare("notice@example.com", """
            <html><meta name="viewport" content="width=device-width"><body>
            <table width="100%"><tr><td>
              <table id="banner-table" style="min-width:100%;max-width:100%;width:100px" cellspacing="0" cellpadding="0"><tr><td>
                <img id="banner" width="100" height="auto" src="$banner" style="display:block;min-width:100%;width:100%;max-width:100%">
              </td></tr></table>
              <p>A full width transactional banner.</p>
              <img id="footer" width="20" height="20" src="$banner">
              <img id="css-icon" width="100" src="$banner" style="width:24px">
            </td></tr></table></body></html>
        """.trimIndent())
        val geometry = renderedGeometry(prepared, """
            var r=id=>document.getElementById(id).getBoundingClientRect();
            return [r('banner').width,r('banner').width/r('banner').height,r('footer').width,r('css-icon').width];
        """.trimIndent())
        assertTrue("Banner must fill the card, not retain its legacy 100px limit", geometry.getDouble(0) > 280)
        assertEquals(7.2, geometry.getDouble(1), 0.03)
        assertEquals(20.0, geometry.getDouble(2), 0.1)
        assertEquals(24.0, geometry.getDouble(3), 0.1)
    }

    @Test
    fun fullWidthTextTableDoesNotCollapseToLegacyPixelWidth() = runBlocking {
        // Anonymized Gemini structure: the unitless inline width is parsed as pixels in quirks
        // mode, while min-width:100% is the sender's intended fluid layout constraint.
        for (legacyWidth in listOf("100", "100px")) {
            for (darkMode in listOf(false, true)) {
                val prepared = prepare("notice@example.com", """
                    <html><head><meta name="viewport" content="width=device-width">
                    <style>@media(max-width:950px){.wrap-inner{width:100%!important;max-width:100%!important}
                    .mob_outer_style{padding:0 16px 8px!important}}</style></head><body>
                    <center><table id="outer" class="wrap-inner" style="width:600px;border-collapse:collapse" cellpadding="0" cellspacing="0"><tr><td>
                      <table id="body-table" width="100%" style="width:$legacyWidth;min-width:100%" cellpadding="0" cellspacing="0"><tr><td class="mob_outer_style">
                        <table class="wrap-inner" cellpadding="0" cellspacing="0"><tr><td id="copy">
                          我们正在更新应用中的设置，让你能更好地控制自己的数据。后续几周将逐步推出这些变化。
                        </td></tr><tr><td><table width="100%" cellpadding="0" cellspacing="0"><tr>
                          <td width="13">·</td><td id="bullet">有关变化的详细说明，应正常换行并保留完整的阅读宽度。</td>
                        </tr></table></td></tr></table>
                      </td></tr></table>
                      <table id="small" style="width:96px" cellpadding="0" cellspacing="0"><tr><td>Footer</td></tr></table>
                    </td></tr></table></center></body></html>
                """.trimIndent(), darkMode = darkMode)
                assertEquals(MailDocumentLayout.FLUID, prepared.layout)
                val geometry = renderedGeometry(prepared, """
                    var r=id=>document.getElementById(id).getBoundingClientRect();
                    return [r('body-table').width/r('outer').width,r('copy').width,
                      r('copy').left-r('body-table').left,r('body-table').right-r('copy').right,
                      r('bullet').width,r('small').width,document.documentElement.scrollWidth-innerWidth];
                """.trimIndent())
                assertEquals("Text table must fill the newsletter", 1.0, geometry.getDouble(0), 0.01)
                assertTrue("Paragraphs must have readable width", geometry.getDouble(1) > 250)
                assertEquals(16.0, geometry.getDouble(2), 0.5)
                assertEquals("Keep equal text gutters", geometry.getDouble(2), geometry.getDouble(3), 0.5)
                assertTrue("Bullet copy must not become a narrow column", geometry.getDouble(4) > 230)
                assertEquals("Intentional small tables remain small", 96.0, geometry.getDouble(5), 0.5)
                assertTrue("No horizontal overflow", geometry.getDouble(6) <= 1)
            }
        }
    }

    @Test
    fun photoDigestKeepsEqualGuttersAndDoesNotBecomeAnIconStrip() = runBlocking {
        val badge = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='32' height='32'%3E%3C/svg%3E"
        val cells = (1..3).joinToString("") { index -> """
            <td><a><div id="photo-$index" style="width:130px;padding:0 2.5px 5px;background:url('$badge') center / cover;height:162px;margin:1px">
              <img width="32px" src="$badge" style="margin-left:98px;height:32px">
            </div></a></td>
        """ }
        val prepared = prepare("digest@instagram.com", """
            <html><body><table id="digest" cellspacing="0" cellpadding="0"><tr><td>
              <table width="100%" cellspacing="0" cellpadding="0"><tr><td width="16"></td><td width="394">
                <table width="100%"><tr><td style="padding:5px;width:100%">See the latest photo updates from the accounts you follow.</td></tr></table>
                <table id="gallery" width="394" cellspacing="0" cellpadding="0"><tr>$cells</tr></table>
              </td><td width="16"></td></tr></table>
            </td></tr></table></body></html>
        """.trimIndent())
        assertEquals(MailDocumentLayout.DESKTOP_SCALED, prepared.layout)
        assertTrue(org.jsoup.Jsoup.parse(prepared.html).select("#gallery .bondmail-icon-cell").isEmpty())
        val geometry = renderedGeometry(prepared, """
            var r=id=>document.getElementById(id).getBoundingClientRect();
            var b=r('bondmail-message-body'),d=r('digest'),p=r('photo-3');
            return [d.left-b.left,b.right-d.right,r('photo-1').width,p.width,d.right-p.right];
        """.trimIndent())
        assertTrue("Both gutters should remain visible", geometry.getDouble(0) >= 15 && geometry.getDouble(1) >= 15)
        assertEquals("Outer gutters should be symmetric", geometry.getDouble(0), geometry.getDouble(1), 0.5)
        assertEquals("All photo columns should have equal width", geometry.getDouble(2), geometry.getDouble(3), 0.5)
        assertTrue("Last photo must stay inside the canvas", geometry.getDouble(4) > 0)
    }

    @Test
    fun responsiveFeatureColumnsKeepMobileVisibilityAndWidth() = runBlocking {
        val pixel = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"
        for (dark in listOf(false, true)) {
            val prepared = prepare("notice@example.com", """
                <html><head><style>
                .mobile { display:none }
                @media(max-width:950px) {
                  .canvas { width:100%!important } .feature { width:300px!important }
                  .mobile,.stack { display:block!important } .desktop { display:none!important }
                }
                </style></head><body>
                <table id="outer" width="100%" cellspacing="0" cellpadding="0"><tr><td>
                <table class="canvas" width="940" cellspacing="0" cellpadding="0"><tr><td>
                <table width="100%" cellspacing="0" cellpadding="0"><tr id="feature-row">
                  <td id="mobile-art" class="mobile" style="display:none"><table class="feature" width="288"><tr><td><img src="$pixel" width="240" height="180"></td></tr></table></td>
                  <td class="desktop"><img src="$pixel" width="20" height="1"></td>
                  <td id="copy" class="stack"><table class="feature" width="288"><tr><td>Keep your passes together, ready to use wherever you go. A readable paragraph beside a responsive illustration.</td></tr></table></td>
                  <td class="desktop"><img src="$pixel" width="20" height="1"></td>
                  <td id="desktop-art" class="desktop"><table class="feature" width="288"><tr><td><img src="$pixel" width="240" height="180"></td></tr></table></td>
                </tr></table>
                <table id="social" cellspacing="0" cellpadding="0"><tr>
                  <td><img src="$pixel" width="24" height="24"></td>
                  <td><img src="$pixel" width="24" height="24"></td>
                  <td><img src="$pixel" width="24" height="24"></td>
                </tr></table>
                </td></tr></table></td></tr></table></body></html>
            """.trimIndent(), darkMode = dark)
            assertEquals(MailDocumentLayout.FLUID, prepared.layout)
            val dom = org.jsoup.Jsoup.parse(prepared.html)
            assertFalse(dom.getElementById("feature-row")!!.hasClass("bondmail-icon-row"))
            assertEquals(1, dom.select("#social .bondmail-icon-row").size)
            val geometry = renderedGeometry(prepared, """
                var r=id=>document.getElementById(id).getBoundingClientRect();
                var b=r('bondmail-message-body'),o=r('outer'),m=r('mobile-art'),c=r('copy');
                return [o.width,b.width,o.left-b.left,b.right-o.right,
                  r('desktop-art').width,m.width,c.width,c.top-m.bottom,document.documentElement.scrollWidth-innerWidth];
            """.trimIndent())
            assertTrue("Responsive canvas must fit the phone", geometry.getDouble(0) <= geometry.getDouble(1))
            assertEquals(16.0, geometry.getDouble(2), 0.5)
            assertEquals("Equal outer gutters", geometry.getDouble(2), geometry.getDouble(3), 0.5)
            assertEquals("Desktop duplicate stays hidden", 0.0, geometry.getDouble(4), 0.5)
            assertTrue("Mobile artwork is visible", geometry.getDouble(5) >= 290)
            assertTrue("Copy retains readable width", geometry.getDouble(6) >= 290)
            assertTrue("Copy stacks below the artwork", geometry.getDouble(7) >= -1)
            assertTrue("No horizontal overflow", geometry.getDouble(8) <= 1)
        }
    }

    @org.junit.Test fun cathayStatementKeepsBothEdgesOnItsOriginalCanvas() = kotlinx.coroutines.runBlocking {
        assertEquals("Plain mail from the same sender must not shrink", MailDocumentLayout.FLUID,
            prepare("notice@e.cathaypacific.com", "<p>A readable text-only notification.</p>").layout)
        val prepared = prepare("statement@e.cathaypacific.com", """
            <html><head><meta name="viewport" content="width=device-width,user-scalable=no">
            <style>@media(max-width:640px){.logo{width:90px}}</style></head><body>${"<div>".repeat(15)}
            <table width="700" cellspacing="0" cellpadding="0" style="width:700px"><tr><td>
            <table width="700" cellspacing="0" cellpadding="0"><tr>
            <td id="left" width="350">Account balance</td><td id="right" width="350">Full membership statement</td>
            </tr></table><p id="end">All membership terms remain visible.</p>
            </td></tr></table>${"</div>".repeat(15)}</body></html>
        """.trimIndent())
        assertEquals(MailDocumentLayout.DESKTOP_SCALED, prepared.layout)
        val geometry = renderedGeometry(prepared, """
            var b=document.getElementById('bondmail-message-body').getBoundingClientRect();
            var l=document.getElementById('left').getBoundingClientRect();
            var r=document.getElementById('right').getBoundingClientRect();
            return [l.left-b.left,b.right-r.right,r.width,document.documentElement.scrollWidth-innerWidth];
        """.trimIndent())
        assertTrue("Left edge fits", geometry.getDouble(0) >= 0)
        assertTrue("Right edge fits", geometry.getDouble(1) >= -1)
        assertTrue("Right column has not collapsed", geometry.getDouble(2) > 100)
        assertTrue("Page does not overflow", geometry.getDouble(3) <= 1)
        assertTrue(prepared.html.contains("maximum-scale=1.0"))
        assertTrue(prepared.html.contains("user-scalable=no"))
    }

    @org.junit.Test fun unexpectedOverflowRemainsHorizontallyReachable() = kotlinx.coroutines.runBlocking {
        val prepared = prepare("reader@example.com", """
            <html><body><div style="display:flex">
              <div style="flex:0 0 290px">Left content</div>
              <div id="far-edge" style="flex:0 0 290px">Right content must remain reachable</div>
            </div></body></html>
        """.trimIndent())
        val geometry = renderedGeometry(prepared, """
            var b=document.getElementById('bondmail-message-body');
            var before=b.scrollLeft; b.scrollLeft=b.scrollWidth;
            var r=document.getElementById('far-edge').getBoundingClientRect(), v=b.getBoundingClientRect();
            return [b.scrollWidth-b.clientWidth,b.scrollLeft-before,v.right-r.right,
                /pinch-zoom|manipulation|auto/.test(getComputedStyle(b).touchAction)];
        """.trimIndent())
        assertTrue("Fixture must really overflow", geometry.getDouble(0) > 100)
        assertTrue("Overflow must be scrollable", geometry.getDouble(1) > 100)
        assertTrue("Far edge can be revealed", geometry.getDouble(2) >= -1)
        assertTrue("Pinch zoom is not blocked by CSS", geometry.getBoolean(3))
    }

    @Test fun senderScriptsRemainBlockedWhenHostAdjustsBodyZoom() = runBlocking {
        val prepared = prepare("sender@example.invalid", """
            <html><head><script>window.senderRan=true</script></head>
            <body onload="window.senderRan=true"><img src="bad:" onerror="window.senderRan=true">
            <svg onload="window.senderRan=true"></svg><noscript><p>Safe visible text</p></noscript></body></html>
        """.trimIndent())
        val doc = org.jsoup.Jsoup.parse(prepared.html)
        assertEquals("Content-Security-Policy", doc.head().child(0).attr("http-equiv"))
        assertTrue(doc.select("script,noscript,[onload],[onerror]").isEmpty())
        val values = renderedGeometry(prepared, """
            const script=document.createElement('script');script.textContent='window.senderRan=true';document.body.append(script);
            const button=document.createElement('div');button.setAttribute('onclick','window.senderRan=true');document.body.append(button);button.click();
            const b=document.getElementById('bondmail-message-body');b.style.setProperty('zoom','0.5','important');
            return [window.senderRan===undefined, getComputedStyle(b).zoom, document.body.textContent.includes('Safe visible text')];
        """.trimIndent())
        assertTrue("CSP must reject both scripts and inline event handlers", values.getBoolean(0))
        assertEquals("Host DOM zoom is allowed", "0.5", values.getString(1))
        assertTrue(values.getBoolean(2))
    }

    private fun renderedGeometry(prepared: PreparedMailDocument, script: String): org.json.JSONArray {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val ready = java.util.concurrent.CountDownLatch(1)
        val result = java.util.concurrent.atomic.AtomicReference<String>()
        var webView: android.webkit.WebView? = null
        instrumentation.runOnMainSync {
            webView = android.webkit.WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                settings.useWideViewPort = true
                val density = resources.displayMetrics.density
                layout(0, 0, (360 * density).toInt(), (900 * density).toInt())
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView, url: String?) {
                        view.evaluateJavascript("(function(){$script})()") { result.set(it); ready.countDown() }
                    }
                }
                loadDataWithBaseURL("https://example.invalid", prepared.html, "text/html", "UTF-8", null)
            }
        }
        try {
            assertTrue("WebView should render the fixture", ready.await(15, java.util.concurrent.TimeUnit.SECONDS))
            return org.json.JSONArray(result.get())
        } finally {
            instrumentation.runOnMainSync { webView?.destroy() }
        }
    }

    private suspend fun prepare(
        senderAddress: String,
        html: String,
        darkMode: Boolean = false,
        displayMode: com.bond.mail.data.settings.MailDisplayMode = com.bond.mail.data.settings.MailDisplayMode.AUTO,
    ): PreparedMailDocument = MailWebViewCache.preparedDocument(
        key = "instrumentation-${System.nanoTime()}",
        html = html,
        header = MailWebHeader(
            displayMode = displayMode,
            subject = "Test message",
            senderName = "Test sender",
            senderAddress = senderAddress,
            recipient = "reader@example.com",
            dateLabel = "Today",
            avatarText = "T",
        ),
        foregroundCss = "#202124",
        backgroundCss = if (darkMode) "#000000" else "#f7f7f7",
        linkCss = "#0b57d0",
        mutedCss = "#666666",
        headerSurfaceCss = "#ffffff",
        avatarBackgroundCss = "#dde7ff",
        avatarForegroundCss = "#123456",
        darkMode = darkMode,
        topContentInsetCssPx = 64,
        subjectBlockHeightCssPx = 84,
        subjectFontSizeSp = 24f,
        subjectLineHeightSp = 30f,
        senderBlockHeightCssPx = 112,
        viewportWidthCssPx = 360,
        fontScale = 1f,
    )
}
