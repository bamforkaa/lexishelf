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

    private companion object {
        const val TEST_DATABASE = "migration-test"
        const val RELATION_MIGRATION_DATABASE = "relation-migration-test"
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
