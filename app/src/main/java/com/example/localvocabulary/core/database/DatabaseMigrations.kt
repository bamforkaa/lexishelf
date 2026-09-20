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


val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // No existing sense is enrolled automatically.
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS review_states (
                stable_id TEXT NOT NULL PRIMARY KEY, sense_stable_id TEXT NOT NULL,
                mode TEXT NOT NULL, enabled INTEGER NOT NULL, stage INTEGER NOT NULL,
                last_reviewed_at INTEGER, next_review_at INTEGER NOT NULL,
                scheduler_version TEXT NOT NULL, generation INTEGER NOT NULL,
                FOREIGN KEY(sense_stable_id) REFERENCES senses(stable_id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        database.execSQL("CREATE UNIQUE INDEX index_review_states_sense_stable_id_mode ON review_states(sense_stable_id, mode)")
        database.execSQL("CREATE INDEX index_review_states_next_review_at ON review_states(next_review_at)")
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS review_events (
                stable_id TEXT NOT NULL PRIMARY KEY, review_state_id TEXT NOT NULL,
                reviewed_at INTEGER NOT NULL, rating TEXT, kind TEXT NOT NULL,
                scheduler_version TEXT NOT NULL, generation INTEGER NOT NULL,
                was_new INTEGER NOT NULL, session_id TEXT, retry_of_event_id TEXT,
                FOREIGN KEY(review_state_id) REFERENCES review_states(stable_id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX index_review_events_review_state_id ON review_events(review_state_id)")
        database.execSQL("CREATE INDEX index_review_events_reviewed_at ON review_events(reviewed_at)")
        database.execSQL("CREATE UNIQUE INDEX index_review_events_retry_of_event_id ON review_events(retry_of_event_id)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // ALTER preserves Long primary keys and every existing foreign-key reference.
        for (table in listOf("senses", "examples")) {
            database.execSQL("ALTER TABLE $table ADD COLUMN stable_id TEXT NOT NULL DEFAULT ''")
            database.query("SELECT id FROM $table").use { rows ->
                while (rows.moveToNext()) {
                    database.execSQL(
                        "UPDATE $table SET stable_id = ? WHERE id = ?",
                        arrayOf<Any>(java.util.UUID.randomUUID().toString(), rows.getLong(0)),
                    )
                }
            }
            database.execSQL("CREATE UNIQUE INDEX index_${table}_stable_id ON $table(stable_id)")
        }
        database.execSQL("ALTER TABLE examples ADD COLUMN meaning TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE examples ADD COLUMN origin TEXT NOT NULL DEFAULT 'UNKNOWN'")
        database.execSQL("ALTER TABLE examples ADD COLUMN source_title TEXT")
        database.execSQL("ALTER TABLE examples ADD COLUMN source_url TEXT")
        database.execSQL("ALTER TABLE examples ADD COLUMN source_locator TEXT")
        database.execSQL("ALTER TABLE examples ADD COLUMN captured_at INTEGER")
    }
}
