package com.example.localvocabulary.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VocabularyDatabaseMigrationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VocabularyDatabase::class.java,
    )

    @Test
    fun migrate1To2PreservesRowsAndCreatesUniqueStableBackupIds() {
        migrationHelper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO vocabulary_entries
                    (id, headword, language_tag, notes, created_at_epoch_millis, modified_at_epoch_millis)
                VALUES
                    (1, 'alpha', 'en', 'first', 10, 20),
                    (2, 'beta', 'en', 'second', 30, 40)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO tags (id, name, normalized_name)
                VALUES
                    (1, 'Shared', 'shared'),
                    (2, 'Study', 'study')
                """.trimIndent(),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            MIGRATION_1_2,
        )

        migrated.query(
            "SELECT headword, backup_id FROM vocabulary_entries ORDER BY id",
        ).use { cursor ->
            assertEquals(2, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("alpha", cursor.getString(0))
            val firstBackupId = cursor.getString(1)
            assertTrue(firstBackupId.isNotBlank())
            assertTrue(cursor.moveToNext())
            assertEquals("beta", cursor.getString(0))
            val secondBackupId = cursor.getString(1)
            assertTrue(secondBackupId.isNotBlank())
            assertNotEquals(firstBackupId, secondBackupId)
        }
        migrated.query("SELECT name, backup_id FROM tags ORDER BY id").use { cursor ->
            assertEquals(2, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("Shared", cursor.getString(0))
            val firstBackupId = cursor.getString(1)
            assertTrue(firstBackupId.isNotBlank())
            assertTrue(cursor.moveToNext())
            assertEquals("Study", cursor.getString(0))
            assertTrue(cursor.getString(1).isNotBlank())
            assertNotEquals(firstBackupId, cursor.getString(1))
        }
        migrated.close()
    }

    @Test
    fun migrate1To2PreservesCompleteVocabularyAggregatesAndTagRelations() {
        migrationHelper.createDatabase(RELATION_MIGRATION_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO vocabulary_entries
                    (id, headword, language_tag, notes, created_at_epoch_millis, modified_at_epoch_millis)
                VALUES
                    (1, '辞書', 'ja', '日本語メモ', 100, 200),
                    (2, 'dictionary', 'en', 'English note', 300, 400)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO senses (id, entry_id, meaning, part_of_speech, sort_order)
                VALUES
                    (10, 1, '사전', '명사', 0),
                    (11, 1, '어휘집', '명사', 1),
                    (12, 2, 'a reference book', 'noun', 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO examples (id, sense_id, text, sort_order)
                VALUES
                    (100, 10, '辞書を引く。', 0),
                    (101, 10, '辞書で調べる。', 1),
                    (102, 11, '専門用語の語彙集。', 0),
                    (103, 12, 'Open the dictionary.', 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO tags (id, name, normalized_name)
                VALUES
                    (20, 'Shared', 'shared'),
                    (21, 'Japanese', 'japanese')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO entry_tag_cross_refs (entry_id, tag_id)
                VALUES
                    (1, 20),
                    (1, 21),
                    (2, 20)
                """.trimIndent(),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            RELATION_MIGRATION_DATABASE,
            2,
            true,
            MIGRATION_1_2,
        )

        migrated.query(
            """
            SELECT id, headword, language_tag, notes,
                   created_at_epoch_millis, modified_at_epoch_millis
            FROM vocabulary_entries
            ORDER BY id
            """.trimIndent(),
        ).use { cursor ->
            assertEquals(2, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals("辞書", cursor.getString(1))
            assertEquals("ja", cursor.getString(2))
            assertEquals("日本語メモ", cursor.getString(3))
            assertEquals(100L, cursor.getLong(4))
            assertEquals(200L, cursor.getLong(5))
            assertTrue(cursor.moveToNext())
            assertEquals(2L, cursor.getLong(0))
            assertEquals("dictionary", cursor.getString(1))
            assertEquals("en", cursor.getString(2))
            assertEquals("English note", cursor.getString(3))
            assertEquals(300L, cursor.getLong(4))
            assertEquals(400L, cursor.getLong(5))
        }
        migrated.query(
            """
            SELECT id, entry_id, meaning, part_of_speech, sort_order
            FROM senses
            ORDER BY id
            """.trimIndent(),
        ).use { cursor ->
            assertEquals(3, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(listOf(10L, 1L, "사전", "명사", 0), cursor.currentSenseRow())
            assertTrue(cursor.moveToNext())
            assertEquals(listOf(11L, 1L, "어휘집", "명사", 1), cursor.currentSenseRow())
            assertTrue(cursor.moveToNext())
            assertEquals(
                listOf(12L, 2L, "a reference book", "noun", 0),
                cursor.currentSenseRow(),
            )
        }
        migrated.query(
            "SELECT id, sense_id, text, sort_order FROM examples ORDER BY id",
        ).use { cursor ->
            assertEquals(4, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(listOf(100L, 10L, "辞書を引く。", 0), cursor.currentExampleRow())
            assertTrue(cursor.moveToNext())
            assertEquals(listOf(101L, 10L, "辞書で調べる。", 1), cursor.currentExampleRow())
            assertTrue(cursor.moveToNext())
            assertEquals(listOf(102L, 11L, "専門用語の語彙集。", 0), cursor.currentExampleRow())
            assertTrue(cursor.moveToNext())
            assertEquals(listOf(103L, 12L, "Open the dictionary.", 0), cursor.currentExampleRow())
        }
        migrated.query(
            "SELECT id, name, normalized_name FROM tags ORDER BY id",
        ).use { cursor ->
            assertEquals(2, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(listOf(20L, "Shared", "shared"), cursor.currentTagRow())
            assertTrue(cursor.moveToNext())
            assertEquals(listOf(21L, "Japanese", "japanese"), cursor.currentTagRow())
        }
        migrated.query(
            "SELECT entry_id, tag_id FROM entry_tag_cross_refs ORDER BY entry_id, tag_id",
        ).use { cursor ->
            assertEquals(3, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(listOf(1L, 20L), cursor.currentRelationRow())
            assertTrue(cursor.moveToNext())
            assertEquals(listOf(1L, 21L), cursor.currentRelationRow())
            assertTrue(cursor.moveToNext())
            assertEquals(listOf(2L, 20L), cursor.currentRelationRow())
        }
        migrated.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals(0, cursor.count)
        }
        migrated.close()
    }

    @Test
    fun migrate2To3PreservesExistingVocabularyAndAddsEmptyProvenanceTables() {
        migrationHelper.createDatabase(PROVENANCE_MIGRATION_DATABASE, 2).apply {
            execSQL(
                """
                INSERT INTO vocabulary_entries
                    (id, backup_id, headword, language_tag, notes,
                     created_at_epoch_millis, modified_at_epoch_millis)
                VALUES
                    (1, 'entry-one', 'dictionary', 'en', 'user note', 100, 200),
                    (2, 'entry-two', 'manual', 'en', 'second note', 300, 400)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO senses (id, entry_id, meaning, part_of_speech, sort_order)
                VALUES
                    (10, 1, 'reference book', 'noun', 0),
                    (11, 1, 'word list', 'noun', 1),
                    (12, 2, 'made by hand', 'adjective', 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO examples (id, sense_id, text, sort_order)
                VALUES
                    (100, 10, 'Open the dictionary.', 0),
                    (101, 11, 'The word list is ordered.', 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO tags (id, backup_id, name, normalized_name)
                VALUES
                    (20, 'tag-shared', 'Shared', 'shared'),
                    (21, 'tag-study', 'Study', 'study')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO entry_tag_cross_refs (entry_id, tag_id)
                VALUES (1, 20), (1, 21), (2, 20)
                """.trimIndent(),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            PROVENANCE_MIGRATION_DATABASE,
            3,
            true,
            MIGRATION_2_3,
        )

        migrated.query(
            """
            SELECT id, backup_id, headword, language_tag, notes,
                   created_at_epoch_millis, modified_at_epoch_millis
            FROM vocabulary_entries ORDER BY id
            """.trimIndent(),
        ).use { cursor ->
            assertEquals(2, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals("entry-one", cursor.getString(1))
            assertEquals("dictionary", cursor.getString(2))
            assertEquals("en", cursor.getString(3))
            assertEquals("user note", cursor.getString(4))
            assertEquals(100L, cursor.getLong(5))
            assertEquals(200L, cursor.getLong(6))
            assertTrue(cursor.moveToNext())
            assertEquals(2L, cursor.getLong(0))
            assertEquals("entry-two", cursor.getString(1))
            assertEquals("manual", cursor.getString(2))
            assertEquals("en", cursor.getString(3))
            assertEquals("second note", cursor.getString(4))
            assertEquals(300L, cursor.getLong(5))
            assertEquals(400L, cursor.getLong(6))
        }
        migrated.query(
            "SELECT id, entry_id, meaning, part_of_speech, sort_order FROM senses ORDER BY id",
        ).use { cursor ->
            assertEquals(3, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(listOf(10L, 1L, "reference book", "noun", 0), cursor.currentSenseRow())
            assertTrue(cursor.moveToNext())
            assertEquals(listOf(11L, 1L, "word list", "noun", 1), cursor.currentSenseRow())
            assertTrue(cursor.moveToNext())
            assertEquals(
                listOf(12L, 2L, "made by hand", "adjective", 0),
                cursor.currentSenseRow(),
            )
        }
        migrated.query("SELECT id, sense_id, text, sort_order FROM examples ORDER BY id")
            .use { cursor ->
                assertEquals(2, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals(
                    listOf(100L, 10L, "Open the dictionary.", 0),
                    cursor.currentExampleRow(),
                )
                assertTrue(cursor.moveToNext())
                assertEquals(
                    listOf(101L, 11L, "The word list is ordered.", 0),
                    cursor.currentExampleRow(),
                )
            }
        migrated.query("SELECT id, backup_id, name, normalized_name FROM tags ORDER BY id")
            .use { cursor ->
                assertEquals(2, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals(20L, cursor.getLong(0))
                assertEquals("tag-shared", cursor.getString(1))
                assertEquals("Shared", cursor.getString(2))
                assertEquals("shared", cursor.getString(3))
                assertTrue(cursor.moveToNext())
                assertEquals(21L, cursor.getLong(0))
                assertEquals("tag-study", cursor.getString(1))
                assertEquals("Study", cursor.getString(2))
                assertEquals("study", cursor.getString(3))
            }
        migrated.query("SELECT entry_id, tag_id FROM entry_tag_cross_refs ORDER BY entry_id, tag_id")
            .use { cursor ->
                assertEquals(3, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals(listOf(1L, 20L), cursor.currentRelationRow())
                assertTrue(cursor.moveToNext())
                assertEquals(listOf(1L, 21L), cursor.currentRelationRow())
                assertTrue(cursor.moveToNext())
                assertEquals(listOf(2L, 20L), cursor.currentRelationRow())
            }
        migrated.query("SELECT COUNT(*) FROM sense_dictionary_provenance").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM sense_dictionary_provenance_fields").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals(0, cursor.count)
        }
        migrated.close()
    }

    @Test
    fun migrate3To4AddsEmptyReadingAndPreservesCompleteAggregateAndProvenance() {
        migrationHelper.createDatabase(READING_MIGRATION_DATABASE, 3).apply {
            execSQL(
                """
                INSERT INTO vocabulary_entries
                    (id, backup_id, headword, language_tag, notes,
                     created_at_epoch_millis, modified_at_epoch_millis)
                VALUES (1, 'entry-ja', '食べる', 'ja', 'user note', 100, 200)
                """.trimIndent(),
            )
            execSQL(
                "INSERT INTO senses VALUES(10, 1, 'to eat', 'Ichidan verb', 0)",
            )
            execSQL("INSERT INTO examples VALUES(100, 10, '寿司を食べる。', 0)")
            execSQL("INSERT INTO tags VALUES(20, 'tag-ja', 'Japanese', 'japanese')")
            execSQL("INSERT INTO entry_tag_cross_refs VALUES(1, 20)")
            execSQL(
                """
                INSERT INTO sense_dictionary_provenance VALUES(
                    10, 'jmdict', '1358280', '1358280:1', 'JMdict',
                    'https://www.edrdg.org/jmdict/j_jmdict.html',
                    'Creative Commons Attribution-ShareAlike 4.0 International',
                    'https://creativecommons.org/licenses/by-sa/4.0/',
                    '2026-08-23', 150, 0
                )
                """.trimIndent(),
            )
            execSQL("INSERT INTO sense_dictionary_provenance_fields VALUES(10, 'MEANING')")
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            READING_MIGRATION_DATABASE,
            4,
            true,
            MIGRATION_3_4,
        )

        migrated.query(
            "SELECT headword, language_tag, notes, reading FROM vocabulary_entries",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("食べる", cursor.getString(0))
            assertEquals("ja", cursor.getString(1))
            assertEquals("user note", cursor.getString(2))
            assertEquals("", cursor.getString(3))
        }
        migrated.query("SELECT meaning, part_of_speech FROM senses WHERE id = 10").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("to eat", cursor.getString(0))
            assertEquals("Ichidan verb", cursor.getString(1))
        }
        migrated.query("SELECT text FROM examples WHERE id = 100").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("寿司を食べる。", cursor.getString(0))
        }
        migrated.query("SELECT tag_id FROM entry_tag_cross_refs WHERE entry_id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(20L, cursor.getLong(0))
        }
        migrated.query(
            "SELECT provider_id, source_entry_id FROM sense_dictionary_provenance WHERE sense_id = 10",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("jmdict", cursor.getString(0))
            assertEquals("1358280", cursor.getString(1))
        }
        migrated.query("SELECT COUNT(*) FROM entry_dictionary_provenance").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        migrated.close()
    }

    @Test
    fun migrate4To5AddsWordbooksWithoutChangingExistingUserData() {
        migrationHelper.createDatabase(WORD_BOOK_MIGRATION_DATABASE, 4).apply {
            execSQL(
                """
                INSERT INTO vocabulary_entries
                    (id, backup_id, headword, language_tag, notes,
                     created_at_epoch_millis, modified_at_epoch_millis, reading)
                VALUES (1, 'entry-ja', '食べる', 'ja', 'user note', 100, 200, 'たべる')
                """.trimIndent(),
            )
            execSQL("INSERT INTO senses VALUES(10, 1, '먹다', '동사', 0)")
            execSQL("INSERT INTO examples VALUES(100, 10, '寿司を食べる。', 0)")
            execSQL("INSERT INTO tags VALUES(20, 'tag-food', '음식', '음식')")
            execSQL("INSERT INTO entry_tag_cross_refs VALUES(1, 20)")
            execSQL(
                """
                INSERT INTO sense_dictionary_provenance VALUES(
                    10, 'jmdict', '1358280', '1358280:1', 'JMdict', NULL,
                    'CC BY-SA 4.0', NULL, '2026-08-23', 150, 0
                )
                """.trimIndent(),
            )
            execSQL("INSERT INTO sense_dictionary_provenance_fields VALUES(10, 'MEANING')")
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            WORD_BOOK_MIGRATION_DATABASE,
            5,
            true,
            MIGRATION_4_5,
        )

        migrated.query(
            "SELECT headword, language_tag, notes, reading FROM vocabulary_entries",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(listOf("食べる", "ja", "user note", "たべる"), (0..3).map(cursor::getString))
        }
        migrated.query("SELECT meaning, part_of_speech FROM senses").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("먹다", cursor.getString(0))
            assertEquals("동사", cursor.getString(1))
        }
        migrated.query("SELECT text FROM examples").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("寿司を食べる。", cursor.getString(0))
        }
        migrated.query("SELECT name FROM tags").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("음식", cursor.getString(0))
        }
        migrated.query("SELECT tag_id FROM entry_tag_cross_refs WHERE entry_id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals(20L, it.getLong(0))
        }
        migrated.query("SELECT provider_id FROM sense_dictionary_provenance").use {
            assertTrue(it.moveToFirst())
            assertEquals("jmdict", it.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM wordbooks").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM entry_wordbook_cross_refs").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        migrated.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-test"
        const val RELATION_MIGRATION_DATABASE = "relation-migration-test"
        const val PROVENANCE_MIGRATION_DATABASE = "provenance-migration-test"
        const val READING_MIGRATION_DATABASE = "reading-migration-test"
        const val WORD_BOOK_MIGRATION_DATABASE = "wordbook-migration-test"
    }
}

private fun android.database.Cursor.currentSenseRow(): List<Any> = listOf(
    getLong(0),
    getLong(1),
    getString(2),
    getString(3),
    getInt(4),
)

private fun android.database.Cursor.currentExampleRow(): List<Any> = listOf(
    getLong(0),
    getLong(1),
    getString(2),
    getInt(3),
)

private fun android.database.Cursor.currentTagRow(): List<Any> = listOf(
    getLong(0),
    getString(1),
    getString(2),
)

private fun android.database.Cursor.currentRelationRow(): List<Long> = listOf(
    getLong(0),
    getLong(1),
)
