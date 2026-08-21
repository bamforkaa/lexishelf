package com.example.localvocabulary.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE vocabulary_entries " +
                "ADD COLUMN backup_id TEXT NOT NULL DEFAULT ''",
        )
        database.execSQL(
            "UPDATE vocabulary_entries SET backup_id = lower(hex(randomblob(16))) " +
                "WHERE backup_id = ''",
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_vocabulary_entries_backup_id " +
                "ON vocabulary_entries(backup_id)",
        )

        database.execSQL("ALTER TABLE tags ADD COLUMN backup_id TEXT NOT NULL DEFAULT ''")
        database.execSQL(
            "UPDATE tags SET backup_id = lower(hex(randomblob(16))) WHERE backup_id = ''",
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_tags_backup_id ON tags(backup_id)",
        )
    }
}
