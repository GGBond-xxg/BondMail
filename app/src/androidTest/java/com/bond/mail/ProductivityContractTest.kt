package com.bond.mail

import androidx.room.Room
import androidx.activity.compose.setContent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bond.mail.data.db.*
import com.bond.mail.data.mail.*
import com.bond.mail.data.settings.ProductivityStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductivityContractTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun message(id: String, state: String = "REMOTE", folder: String = "INBOX") = MessageEntity(
        id = id, accountId = "test", folderType = folder, remoteFolder = folder, remoteUid = id.hashCode().toLong(),
        senderName = "测试", senderAddress = "sender@example.com", subject = "中文主题", preview = "test",
        bodyText = "中文内容", bodyHtml = "<p>test</p>", receivedAt = System.currentTimeMillis(),
        unread = true, starred = false, hasAttachments = true, deliveryState = state)

    @Test fun migratesVersionElevenAndPreservesDraftData() = runBlocking {
        val name = "migration-test-${java.util.UUID.randomUUID()}.db"
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(11) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        InstrumentationRegistry.getInstrumentation().context.assets.open("schema-v11.sql").bufferedReader().use { it.readText() }
                            .split(';').filter { it.isNotBlank() }.forEach { db.execSQL(it) }
                        db.execSQL("INSERT INTO outbox(id,accountId,recipients,cc,bcc,subject,bodyText,state,retryCount,createdAt,updatedAt) VALUES ('draft','a','b@example.com','','','subject','keep me','DRAFT',0,1,1)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build())
        var migrated: MailDatabase? = null
        try {
            helper.writableDatabase
            helper.close()
            migrated = Room.databaseBuilder(context, MailDatabase::class.java, name).addMigrations(PRODUCTIVITY_MIGRATION).build()
            val draft = migrated.outboxDao().byId("draft")!!
            assertEquals("keep me", draft.bodyText)
            assertEquals("DRAFT", draft.state)
            assertEquals(0L, draft.sendAfter)
            assertNull(draft.inReplyTo)
        } finally { helper.close(); migrated?.close(); context.deleteDatabase(name) }
    }

    @Test fun undoAndSendClaimAreMutuallyExclusiveAndRespectDeadline() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        try {
            val dao = db.outboxDao()
            val task = OutboxEntity("test", "account", "to@example.com", subject = "test", bodyText = "body", sendAfter = 200, createdAt = 0, updatedAt = 0)
            dao.upsert(task)
            assertEquals(0, dao.claimSend("test", 199))
            assertEquals(1, dao.undoQueued("test", 199))
            assertEquals(0, dao.claimSend("test", 200))
            dao.upsert(task)
            assertEquals(0, dao.undoQueued("test", 200))
            assertEquals(1, dao.claimSend("test", 200))
            assertEquals(0, dao.claimSend("test", 201))
            assertEquals("SENDING", dao.byId("test")!!.state)
            repeat(20) {
                dao.upsert(task)
                val results = listOf(async(Dispatchers.IO) { dao.undoQueued("test", 199) }, async(Dispatchers.IO) { dao.claimSend("test", 200) }).awaitAll()
                assertEquals(1, results.sum())
            }
        } finally { db.close() }
    }

    @Test fun storageCleanupPreservesDraftsOutboxAndMetadataAndChineseSearchWorks() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MailDatabase::class.java).build()
        try {
            val dao = db.messageDao()
            dao.upsertAll(listOf(message("remote"), message("draft", folder = "DRAFTS"), message("queued", state = "QUEUED")))
            assertEquals(2, dao.searchAdvanced(mailSearchQuery(null, "from:sender@example.com has:attachment 中文", 2)).first().size)
            dao.clearBodyCache()
            assertFalse(dao.byId("remote")!!.bodyLoaded)
            assertEquals("", dao.byId("remote")!!.bodyText)
            assertTrue(dao.byId("remote")!!.unread)
            assertEquals("中文内容", dao.byId("draft")!!.bodyText)
            assertEquals("中文内容", dao.byId("queued")!!.bodyText)
        } finally { db.close() }
    }

    @Test fun encryptedCacheSeparatesProviderTargetAndBody() = runBlocking {
        val cache = TranslationCache(context)
        val body = "Test ${java.util.UUID.randomUUID()}"
        cache.write(body, TranslationProvider.ALIYUN, "zh", "你好")
        assertEquals("你好", cache.read(body, TranslationProvider.ALIYUN, "zh"))
        assertNull(cache.read(body, TranslationProvider.GOOGLE, "zh"))
        assertNull(cache.read(body, TranslationProvider.ALIYUN, "en"))
        assertNull(cache.read(body + "x", TranslationProvider.ALIYUN, "zh"))
        // Do not clear an existing user's translation cache during a test.
    }

    @Test fun undoNoticeWorksOutsideInboxWithoutSchedulingSmtp(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
        val container = (context.applicationContext as MailApplication).container
        val id = "ui-test-${java.util.UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        try {
            container.database.outboxDao().upsert(OutboxEntity(id, "no-real-account", "nobody@example.invalid", subject = "Local undo preview", bodyText = "Test only", sendAfter = now + 60_000, createdAt = now, updatedAt = now))
            // Inserting a row does not enqueue a worker. No SMTP or external API is used.
            androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        androidx.compose.material3.MaterialTheme {
                            com.bond.mail.ui.i18n.JsonStringsProvider("zh") { com.bond.mail.ui.components.SendUndoNotice() }
                        }
                    }
                }
                val undo = device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.textStartsWith("撤销发送")), 5000)
                assertNotNull(undo)
                undo.click()
                assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.textContains("已回到草稿箱")), 5000))
                assertEquals("DRAFT", container.database.outboxDao().byId(id)!!.state)
                device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "mail-send-undone.png"))
            }
        } finally { container.database.outboxDao().deleteById(id) }
    }

    @Test fun toolsScreenOpensAndStorageDoesNotRequireAnAccount() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    androidx.compose.material3.MaterialTheme {
                        com.bond.mail.ui.i18n.JsonStringsProvider("zh") {
                            com.bond.mail.ui.components.MailToolsDialog(initialTab = "tools_storage") {}
                        }
                    }
                }
            }
            assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("邮箱工具")), 5000))
            assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.textContains("远程邮件正文")), 5000))
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "mail-tools-storage.png"))
        }
    }

    @Test fun notificationRulesRespectDomainBoundariesAndAccountScope() {
        val store = ProductivityStore(context)
        val account = "test-${java.util.UUID.randomUUID()}"
        try {
            store.set(account, "notifications", "important")
            store.set(account, "important", "@example.com")
            assertEquals("important", store.notificationMode(account, "a@example.com"))
            assertEquals("off", store.notificationMode(account, "a@notexample.com"))
            store.set(account, "quiet", "a@example.com")
            assertEquals("silent", store.notificationMode(account, "a@example.com"))
            assertEquals("all", store.notificationMode("other", "a@example.com"))
        } finally { listOf("notifications", "important", "quiet").forEach { store.set(account, it, "") } }
    }
}
