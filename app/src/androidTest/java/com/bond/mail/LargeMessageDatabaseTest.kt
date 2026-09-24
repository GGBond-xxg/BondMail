package com.bond.mail

import android.database.sqlite.SQLiteBlobTooBigException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bond.mail.data.db.MailDatabase
import com.bond.mail.data.db.MessageEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated in-memory database: no accounts, network or user mailbox data. */
@RunWith(AndroidJUnit4::class)
class LargeMessageDatabaseTest {
    private fun message(id: String, uid: Long) = MessageEntity(
        id = id, accountId = "test", folderType = "SENT", remoteFolder = "Sent", remoteUid = uid,
        internetMessageId = "<$id@example.test>", senderName = "Synthetic sender", senderAddress = "sender@example.test",
        subject = "Large mail regression", preview = "Preview", bodyText = "Small body", bodyHtml = null,
        receivedAt = uid, unread = false, starred = false, hasAttachments = false,
    )

    @Test fun legacyOversizedRowFailsOldQueryButAllFullReadersPreserveContent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        try {
            val dao = db.messageDao()
            val huge = message("large", 8).copy(
                bodyText = ("a".repeat(65535) + "🙂中文").repeat(40),
                bodyHtml = "<p>" + "界🙂".repeat(450000) + "</p>",
                attachmentsJson = "[\"" + "x".repeat(2200000) + "\"]",
            )
            dao.upsertAll((1L..7).map { message("small-$it", it) } + huge)
            var reproduced = false
            try {
                db.openHelper.readableDatabase.query("SELECT * FROM messages ORDER BY remoteUid").use { cursor ->
                    while (cursor.moveToNext()) cursor.getString(cursor.getColumnIndexOrThrow("bodyText"))
                }
            } catch (_: SQLiteBlobTooBigException) { reproduced = true }
            assertTrue("Original full-row query must reproduce CursorWindow overflow", reproduced)
            assertEquals(8, dao.folderRowSnapshot("test", "SENT").size)
            assertEquals(huge, dao.byId(huge.id))
            assertEquals(huge, dao.byIds(listOf("small-1", huge.id)).first { it.id == huge.id })
            assertEquals(huge, dao.folderEntitySnapshot("test", "SENT").first())
            assertEquals(huge, dao.senderFolderSnapshot("test", "SENT", huge.senderAddress).first())
            assertEquals(huge, dao.remoteByInternetMessageId("test", "SENT", "large@example.test"))
            assertNull(dao.byId("missing"))
            assertEquals(message("small-1", 1), dao.byId("small-1"))
        } finally { db.close() }
    }

    @Test fun detailFlowEmitsBodyOnlyChangesAndDeletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, MailDatabase::class.java).build()
        try {
            val dao = db.messageDao()
            val original = message("flow", 1)
            dao.upsert(original)
            val first = CompletableDeferred<Unit>()
            val changed = original.copy(bodyText = "changed🙂".repeat(300000), bodyHtml = "")
            val updated = CompletableDeferred<Unit>()
            val emissions = async {
                withTimeout(30000) {
                    dao.observeById(original.id).onEach {
                        if (it == original) first.complete(Unit)
                        if (it == changed) updated.complete(Unit)
                    }.take(3).toList()
                }
            }
            withTimeout(30000) { first.await() }
            dao.upsert(changed)
            withTimeout(30000) { updated.await() }
            dao.deleteById(original.id)
            assertEquals(listOf(original, changed, null), emissions.await())
        } finally { db.close() }
    }
}
