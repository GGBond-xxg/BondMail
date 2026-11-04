package com.bond.mail.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val HTML_OUTBOX_MIGRATION = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE outbox ADD COLUMN bodyHtml TEXT")
    }
}
