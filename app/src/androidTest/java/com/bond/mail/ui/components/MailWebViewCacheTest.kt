package com.bond.mail.ui.components

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailWebViewCacheTest {
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

    @Test fun ifastFooterGetsReadablePaletteInDarkMode() = runBlocking {
        val prepared = prepare("notice@ifastgb.com", "<table><tr><td>Main text</td></tr><tr><td><font color='white'>iFAST Global Bank Limited</font><p style='color:white'>London E14 9SH</p></td></tr></table>", true)
        val document = org.jsoup.Jsoup.parse(prepared.html)
        assertEquals(1, document.select("td.bondmail-ifast-footer").size)
        assertTrue(prepared.html.contains("background-color:#f4f7fa!important;color:#202124!important"))
        assertTrue(prepared.html.contains("body.bondmail-dark-mode #bondmail-message-body .bondmail-ifast-footer"))
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
