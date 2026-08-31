package com.bond.mail.ui.components

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailWebViewCacheTest {
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

    private suspend fun prepare(
        senderAddress: String,
        html: String,
    ): PreparedMailDocument = MailWebViewCache.preparedDocument(
        key = "instrumentation-${System.nanoTime()}",
        html = html,
        header = MailWebHeader(
            subject = "Test message",
            senderName = "Test sender",
            senderAddress = senderAddress,
            recipient = "reader@example.com",
            dateLabel = "Today",
            avatarText = "T",
        ),
        foregroundCss = "#202124",
        backgroundCss = "#f7f7f7",
        linkCss = "#0b57d0",
        mutedCss = "#666666",
        headerSurfaceCss = "#ffffff",
        avatarBackgroundCss = "#dde7ff",
        avatarForegroundCss = "#123456",
        darkMode = false,
        topContentInsetCssPx = 64,
        subjectBlockHeightCssPx = 84,
        subjectFontSizeSp = 24f,
        subjectLineHeightSp = 30f,
        senderBlockHeightCssPx = 112,
        viewportWidthCssPx = 360,
        fontScale = 1f,
    )
}
