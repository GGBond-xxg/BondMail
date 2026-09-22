package com.bond.mail.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val PRODUCTIVITY_MIGRATION = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE messages ADD COLUMN inReplyTo TEXT")
        database.execSQL("ALTER TABLE messages ADD COLUMN referencesHeader TEXT")
        database.execSQL("ALTER TABLE outbox ADD COLUMN sendAfter INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE outbox ADD COLUMN inReplyTo TEXT")
        database.execSQL("ALTER TABLE outbox ADD COLUMN referencesHeader TEXT")
    }
}
