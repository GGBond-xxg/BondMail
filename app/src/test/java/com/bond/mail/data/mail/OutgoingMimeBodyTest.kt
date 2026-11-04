package com.bond.mail.data.mail

import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import org.junit.Assert.*
import org.junit.Test

class OutgoingMimeBodyTest {
    private fun roundTrip(text: String, html: String?): MimeMessage {
        val session = Session.getInstance(Properties())
        val message = MimeMessage(session)
        OutgoingMimeBody.applyTo(message, text, html)
        message.saveChanges()
        val bytes = ByteArrayOutputStream().also { message.writeTo(it) }.toByteArray()
        return MimeMessage(session, bytes.inputStream())
    }

    @Test fun htmlAndPlainAlternativesSurviveMimeSerialization() {
        val message = roundTrip("你好", "<table><tr><td>你好</td></tr></table>")
        assertTrue(message.isMimeType("multipart/alternative"))
        val alternatives = message.content as MimeMultipart
        assertEquals("你好", alternatives.getBodyPart(0).content)
        assertTrue(alternatives.getBodyPart(1).isMimeType("text/html"))
        assertTrue((alternatives.getBodyPart(1).content as String).contains("<table>"))
    }

    @Test fun embeddedImagesBecomeDeduplicatedRelatedMimeParts() {
        val message = roundTrip("logo", "<img src='data:image/png;base64,AQID'><img src='data:image/png;base64,AQID'>")
        val alternative = message.content as MimeMultipart
        val related = alternative.getBodyPart(1).content as MimeMultipart
        assertEquals(2, related.count)
        val html = related.getBodyPart(0).content as String
        val image = related.getBodyPart(1)
        assertTrue(html.contains("cid:"))
        assertFalse(html.contains("data:image"))
        assertTrue(html.contains(image.getHeader("Content-ID")[0].trim('<', '>')))
        assertArrayEquals(byteArrayOf(1, 2, 3), image.inputStream.readBytes())
        assertEquals("inline", image.disposition)
    }

    @Test fun plainMessagesRemainPlain() {
        val message = roundTrip("Plain text", null)
        assertTrue(message.isMimeType("text/plain"))
        assertEquals("Plain text", message.content)
    }
}
