package com.bond.mail

import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.bond.mail.data.db.AccountEntity
import com.bond.mail.data.db.MailDatabase
import com.bond.mail.data.db.OutboxEntity
import com.bond.mail.data.mail.ForwardBody
import com.bond.mail.data.mail.SmtpClient
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class HtmlForwardingTest {
    @Test fun savedDraftPreparesHtmlWithInlineImageAndAttachmentWithoutSending() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        val attachment = File.createTempFile("forward-test-", ".txt", context.cacheDir)
        attachment.writeText("Offline attachment")
        try {
            val html = ForwardBody.html("A note", ForwardBody.original(
                "<html><body><table><tr><td>Original<img src='data:image/png;base64,AQID'></td></tr></table></body></html>",
                "original@example.invalid", "Original subject",
            ))!!
            val task = OutboxEntity(id = "offline-test", accountId = "offline-account", recipients = "nobody@example.invalid",
                subject = "Fwd: test", bodyText = ForwardBody.text("A note", html), bodyHtml = html,
                attachmentsJson = JSONArray(listOf(Uri.fromFile(attachment).toString())).toString(),
                state = "DRAFT", createdAt = 1, updatedAt = 1)
            database.outboxDao().upsert(task)
            val restored = database.outboxDao().byId(task.id)!!
            assertEquals(html, restored.bodyHtml)
            assertEquals("A note", ForwardBody.note(restored.bodyHtml, ""))
            val account = AccountEntity(id = task.accountId, providerId = "test", email = "sender@example.invalid",
                displayName = "Offline sender", authType = "PASSWORD", createdAt = 1)
            val prepared = SmtpClient(context).prepare(account, restored)
            val message = MimeMessage(Session.getInstance(Properties()), prepared.raw.inputStream())
            assertTrue(message.isMimeType("multipart/mixed"))
            val mixed = message.content as MimeMultipart
            assertEquals(2, mixed.count)
            assertEquals("Offline attachment", mixed.getBodyPart(1).inputStream.reader().readText())
            val alternative = mixed.getBodyPart(0).content as MimeMultipart
            assertTrue(alternative.getBodyPart(0).isMimeType("text/plain"))
            val related = alternative.getBodyPart(1).content as MimeMultipart
            val sentHtml = related.getBodyPart(0).content as String
            assertTrue(sentHtml.contains("<table>"))
            assertTrue(sentHtml.contains("A note"))
            assertTrue(sentHtml.contains("cid:"))
            assertArrayEquals(byteArrayOf(1, 2, 3), related.getBodyPart(1).inputStream.readBytes())
        } finally {
            database.close()
            attachment.delete()
        }
    }
}
