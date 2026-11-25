package com.bond.mail.ui.components

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class MailSenderNightStyleTest {
    @Test fun viewportOrIncompleteMobileCssCannotOptInFixedStatements() {
        assertFalse(hasCathayPhoneLayout(Jsoup.parse("""<meta name='viewport' content='width=device-width'>
            <style>@media(max-width:620px){.deviceWidth{width:100%}}</style>
            <table class='deviceWidth' width='700'><tr><td>Fixed content</td></tr></table>""")))
    }

    @Test fun onlyVerifiedTransparentAssetsReceiveNightArtworkTreatment() {
        val doc = Jsoup.parse("""<img id='icon' src='https://www.cathaypacific.com/content/dam/cx/applications/email/assets-lrp/member-icon.png'>
            <img id='brand' src='https://www.cathaypacific.com/content/dam/cx/applications/email/header-lrp/cathay80-zh.png'>
            <img id='qr' src='https://www.cathaypacific.com/qr.png' data-bondmail-night-artwork='monochrome'>
            <img id='lookalike' src='https://www.cathaypacific.com.example.org/content/dam/cx/applications/email/assets-lrp/member-icon.png'>""")
        markCathayNightArtwork(doc)
        assertEquals("monochrome", doc.getElementById("icon")!!.attr("data-bondmail-night-artwork"))
        assertEquals("brand", doc.getElementById("brand")!!.attr("data-bondmail-night-artwork"))
        assertFalse(doc.getElementById("qr")!!.hasAttr("data-bondmail-night-artwork"))
        assertFalse(doc.getElementById("lookalike")!!.hasAttr("data-bondmail-night-artwork"))
    }
}
