package com.bond.mail.ui.components

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class MailBodyReadabilityTest {
    @Test fun repairsDitoTextButKeepsQrLinksAndHiddenLayout() {
        val document = Jsoup.parse("""<body style="background:white">
            <table><tr><td id="step" style="color:white!important;background:white">Install your SIM</td></tr></table>
            <a href="https://dito.ph/help"><span id="link" style="-webkit-text-fill-color:white">here</span></a>
            <p id="hidden" style="display:none">Preview</p>
            <img id="qr" src="cid:private-qr" width="200" height="200">
            <svg><text id="vector" fill="white">Keep image</text></svg>
            </body>""")
        val originalImage = document.getElementById("qr")!!.outerHtml()
        val originalVector = document.selectFirst("svg")!!.outerHtml()
        repairDitoBodyReadability(document, "noreply@mail.dito.ph", true)
        val step = document.getElementById("step")!!
        assertTrue(step.attr("style").contains("color:#202124!important"))
        assertTrue(step.attr("style").contains("background-color:#ffffff!important"))
        assertTrue(document.getElementById("link")!!.attr("style").contains("color:#1267a5!important"))
        assertEquals("https://dito.ph/help", document.selectFirst("a")!!.attr("href"))
        assertTrue(document.getElementById("hidden")!!.attr("style").contains("display:none"))
        assertEquals(originalImage, document.getElementById("qr")!!.outerHtml())
        assertEquals(originalVector, document.selectFirst("svg")!!.outerHtml())
        assertEquals("background:white", document.body().attr("style"))
    }
    @Test fun onlyDitoDarkDocumentsAreChanged() {
        listOf("person@notdito.ph" to true, "person@dito.ph.example.org" to true,
            "person@dito.ph" to false).forEach { (sender, dark) ->
            val document = Jsoup.parse("<p style='color:white'>Text</p>")
            val original = document.outerHtml()
            repairDitoBodyReadability(document, sender, dark)
            assertEquals(original, document.outerHtml())
        }
        val document = Jsoup.parse("<body>Direct text</body>")
        repairDitoBodyReadability(document, "person@DITO.PH", true)
        assertEquals("Direct text", document.selectFirst("[data-bondmail-readable-text]")!!.text())
    }
}
