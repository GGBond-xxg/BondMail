package com.bond.mail

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bond.mail.background.MailNotificationManager
import com.bond.mail.background.WorkScheduler
import com.bond.mail.data.auth.OAuthCredentialBroker
import com.bond.mail.data.db.MailDatabase
import com.bond.mail.data.mail.ImapClient
import com.bond.mail.data.mail.SmtpClient
import com.bond.mail.data.repository.MailRepository
import com.bond.mail.data.security.CredentialStore
import com.bond.mail.data.settings.SettingsStore
import com.bond.mail.data.db.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

enum class NewMailNotificationMode {
    ALERT,
    CONSUME_SILENTLY,
}

class AppContainer(context: Context) {
    internal val appContext: Context = context.applicationContext
    private val migration1To2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE messages ADD COLUMN bodyLoaded INTEGER NOT NULL DEFAULT 1")
        }
    }

    private val migration2To3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Existing bodies are marked as parser v1 so HTML messages are fetched once with
            // the corrected MIME parser instead of remaining stuck as plain text.
            database.execSQL("ALTER TABLE messages ADD COLUMN bodyParserVersion INTEGER NOT NULL DEFAULT 1")
        }
    }

    private val migration3To4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Existing accounts keep their createdAt order through the DAO's secondary sort.
            database.execSQL("ALTER TABLE accounts ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val migration4To5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE outbox ADD COLUMN attachmentsJson TEXT NOT NULL DEFAULT '[]'",
            )
        }
    }

    private val migration5To6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE accounts ADD COLUMN oauthAccountId TEXT")
            // Gmail and Microsoft providers used the generic app-password form in older releases.
            // Basic credentials must never be passed into an XOAUTH2 JavaMail session after the
            // provider definition changes. Keep all cached mail, but mark those accounts as OAuth
            // so the account editor offers an in-place reauthorization on first use.
            database.execSQL(
                "UPDATE accounts SET authType = 'OAUTH2' " +
                    "WHERE providerId IN ('gmail', 'outlook', 'm365')",
            )
        }
    }


    private val migration6To7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE messages ADD COLUMN deliveryState TEXT NOT NULL DEFAULT 'REMOTE'")
            database.execSQL("ALTER TABLE outbox ADD COLUMN internetMessageId TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE outbox ADD COLUMN remoteFolder TEXT")
            database.execSQL("ALTER TABLE outbox ADD COLUMN remoteUid INTEGER")
            database.execSQL("ALTER TABLE outbox ADD COLUMN sourceMessageId TEXT")
        }
    }

    private val migration7To8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE messages ADD COLUMN attachmentsJson TEXT NOT NULL DEFAULT '[]'")
            // MIME parser v8 persists attachment metadata and is intentionally allowed to refresh
            // old multipart bodies once. Existing text-only messages keep their cached bodies.
            database.execSQL(
                "UPDATE messages SET bodyParserVersion = 0 " +
                    "WHERE hasAttachments = 1 OR bodyLoaded = 0",
            )
        }
    }

    private val migration8To9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS saved_contacts (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    email TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_saved_contacts_email ON saved_contacts(email)",
            )
        }
    }

    private val migration9To10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE accounts ADD COLUMN displayEmail TEXT")
            database.execSQL("ALTER TABLE accounts ADD COLUMN avatarText TEXT")
            database.execSQL("ALTER TABLE accounts ADD COLUMN loginName TEXT")
            database.execSQL("ALTER TABLE accounts ADD COLUMN customImapHost TEXT")
            database.execSQL("ALTER TABLE accounts ADD COLUMN customImapPort INTEGER")
            database.execSQL("ALTER TABLE accounts ADD COLUMN customImapSecurity TEXT")
            database.execSQL("ALTER TABLE accounts ADD COLUMN customSmtpHost TEXT")
            database.execSQL("ALTER TABLE accounts ADD COLUMN customSmtpPort INTEGER")
            database.execSQL("ALTER TABLE accounts ADD COLUMN customSmtpSecurity TEXT")
            database.execSQL("ALTER TABLE accounts ADD COLUMN customAuthMechanism TEXT")
        }
    }

    private val migration10To11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE saved_contacts ADD COLUMN avatarText TEXT")
        }
    }

    val database: MailDatabase = Room.databaseBuilder(context, MailDatabase::class.java, "bond_mail.db")
        .addMigrations(
            migration1To2,
            migration2To3,
            migration3To4,
            migration4To5,
            migration5To6,
            migration6To7,
            migration7To8,
            migration8To9,
            migration9To10,
            migration10To11,
        )
        .build()
    val settings = SettingsStore(context)
    val credentials = CredentialStore(context)
    val oauth = OAuthCredentialBroker(context.applicationContext)
    val repository = MailRepository(
        database = database,
        credentials = credentials,
        oauth = oauth,
        imap = ImapClient(context.applicationContext),
        smtp = SmtpClient(context.applicationContext),
    )
    val scheduler = WorkScheduler(context)
    val notifications = MailNotificationManager(context)

    private val appForeground = AtomicBoolean(false)
    private val notificationMutex = Mutex()

    fun setAppForeground(foreground: Boolean) {
        appForeground.set(foreground)
    }

    fun isAppForeground(): Boolean = appForeground.get()

    fun backgroundNotificationMode(): NewMailNotificationMode =
        if (isAppForeground()) {
            NewMailNotificationMode.CONSUME_SILENTLY
        } else {
            NewMailNotificationMode.ALERT
        }

    suspend fun syncAllAndNotify(
        notificationMode: NewMailNotificationMode = NewMailNotificationMode.ALERT,
    ): List<MessageEntity> =
        processNewMessages(repository.syncAll(), notificationMode)

    suspend fun syncAccountAndNotify(
        accountId: String,
        notificationMode: NewMailNotificationMode = NewMailNotificationMode.ALERT,
    ): List<MessageEntity> =
        processNewMessages(repository.syncAccount(accountId), notificationMode)

    suspend fun syncFolder(
        accountId: String,
        folderType: String,
        notificationMode: NewMailNotificationMode = NewMailNotificationMode.ALERT,
    ): List<MessageEntity> =
        if (folderType in setOf("SENT", "DRAFTS", "SPAM", "TRASH")) {
            repository.syncFolder(accountId, folderType)
        } else {
            processNewMessages(repository.syncFolder(accountId, folderType), notificationMode)
        }

    suspend fun syncFolderAll(
        folderType: String,
        notificationMode: NewMailNotificationMode = NewMailNotificationMode.ALERT,
    ): List<MessageEntity> =
        if (folderType in setOf("SENT", "DRAFTS", "SPAM", "TRASH")) {
            repository.syncFolderAll(folderType)
        } else {
            processNewMessages(repository.syncFolderAll(folderType), notificationMode)
        }

    /**
     * A user-visible refresh has already put the new rows on screen, so it consumes their UIDs
     * without posting a second system alert. Background polling alerts only while the app is not
     * visible. The mutex makes `shouldNotify -> show/consume -> markNotified` atomic across a manual
     * refresh and WorkManager finishing at the same time.
     */
    private suspend fun processNewMessages(
        messages: List<MessageEntity>,
        notificationMode: NewMailNotificationMode,
    ): List<MessageEntity> {
        val alertsEnabled = settings.settings.first().notifications
        notificationMutex.withLock {
            messages.forEach { message ->
                if (!repository.shouldNotify(message)) return@forEach
                val shouldAlert = notificationMode == NewMailNotificationMode.ALERT &&
                    alertsEnabled &&
                    !isAppForeground()
                if (shouldAlert) notifications.show(message)
                // Consume the UID even when alerts are disabled or this sync is visible in-app.
                // Re-enabling notifications must not replay old mail as a burst of new alerts.
                repository.markNotified(message)
            }
        }
        return messages
    }
}
