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

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS wordbooks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                backup_id TEXT NOT NULL,
                name TEXT NOT NULL,
                normalized_name TEXT NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_wordbooks_normalized_name " +
                "ON wordbooks(normalized_name)",
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_wordbooks_backup_id ON wordbooks(backup_id)",
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS entry_wordbook_cross_refs (
                entry_id INTEGER NOT NULL,
                wordbook_id INTEGER NOT NULL,
                PRIMARY KEY(entry_id, wordbook_id),
                FOREIGN KEY(entry_id) REFERENCES vocabulary_entries(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(wordbook_id) REFERENCES wordbooks(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_entry_wordbook_cross_refs_wordbook_id " +
                "ON entry_wordbook_cross_refs(wordbook_id)",
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE senses ADD COLUMN grammatical_gender TEXT")
        database.execSQL("ALTER TABLE senses ADD COLUMN grammatical_gender_raw TEXT")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vocabulary_pronunciations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                stable_id TEXT NOT NULL,
                entry_id INTEGER NOT NULL,
                notation TEXT NOT NULL,
                value TEXT NOT NULL,
                language_tag TEXT,
                sort_order INTEGER NOT NULL,
                FOREIGN KEY(entry_id) REFERENCES vocabulary_entries(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_vocabulary_pronunciations_entry_id " +
                "ON vocabulary_pronunciations(entry_id)",
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_vocabulary_pronunciations_stable_id " +
                "ON vocabulary_pronunciations(stable_id)",
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pronunciation_dictionary_provenance (
                pronunciation_id INTEGER NOT NULL,
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
                PRIMARY KEY(pronunciation_id),
                FOREIGN KEY(pronunciation_id) REFERENCES vocabulary_pronunciations(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }
}
