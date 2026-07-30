package com.bond.mail.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        FolderEntity::class,
        MessageEntity::class,
        OutboxEntity::class,
        NotificationStateEntity::class,
        SavedContactEntity::class,
    ],
    version = 11,
    exportSchema = false,
)
abstract class MailDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun folderDao(): FolderDao
    abstract fun messageDao(): MessageDao
    abstract fun outboxDao(): OutboxDao
    abstract fun notificationStateDao(): NotificationStateDao
    abstract fun savedContactDao(): SavedContactDao
}
