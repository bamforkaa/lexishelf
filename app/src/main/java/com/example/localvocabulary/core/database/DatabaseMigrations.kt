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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sense_dictionary_provenance (
                sense_id INTEGER NOT NULL,
                provider_id TEXT NOT NULL,
                source_entry_id TEXT,
                source_sense_id TEXT,
                source_name TEXT NOT NULL,
                source_url TEXT,
                license_name TEXT NOT NULL,
                license_url TEXT,
                dataset_version TEXT,
                imported_at_epoch_millis INTEGER NOT NULL,
                modified_after_import INTEGER NOT NULL,
                PRIMARY KEY(sense_id),
                FOREIGN KEY(sense_id) REFERENCES senses(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sense_dictionary_provenance_fields (
                sense_id INTEGER NOT NULL,
                field TEXT NOT NULL,
                PRIMARY KEY(sense_id, field),
                FOREIGN KEY(sense_id) REFERENCES sense_dictionary_provenance(sense_id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE vocabulary_entries ADD COLUMN reading TEXT NOT NULL DEFAULT ''",
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS entry_dictionary_provenance (
                entry_id INTEGER NOT NULL,
                field TEXT NOT NULL,
                provider_id TEXT NOT NULL,
                source_entry_id TEXT,
                source_sense_id TEXT,
                source_name TEXT NOT NULL,
                source_url TEXT,
                license_name TEXT NOT NULL,
                license_url TEXT,
                dataset_version TEXT,
                imported_at_epoch_millis INTEGER NOT NULL,
                modified_after_import INTEGER NOT NULL,
                PRIMARY KEY(entry_id, field),
                FOREIGN KEY(entry_id) REFERENCES vocabulary_entries(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_entry_dictionary_provenance_entry_id " +
                "ON entry_dictionary_provenance(entry_id)",
        )
    }
}
