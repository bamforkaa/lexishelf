package com.example.localvocabulary.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContextMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), VocabularyDatabase::class.java)

    @Test
    fun migration6To7PreservesRowsAndForeignKeysWithoutInventingContext() {
        helper.createDatabase("context-v6-v7", 6).apply {
            execSQL("INSERT INTO vocabulary_entries VALUES(1, 'entry', 'look forward to', 'en', 'note', 10, 20, '')")
            execSQL("INSERT INTO senses VALUES(10, 1, '기대하다', '', 0, NULL, NULL)")
            execSQL("INSERT INTO senses VALUES(11, 1, '두 번째 뜻', '', 1, NULL, NULL)")
            execSQL("INSERT INTO examples VALUES(100, 10, 'I look forward to it.', 0)")
            execSQL("INSERT INTO examples VALUES(101, 10, 'Another example.', 1)")
            execSQL("INSERT INTO sense_dictionary_provenance VALUES(10, 'test', 'source', 'sense', 'Dictionary', NULL, 'License', NULL, NULL, 15, 1)")
            execSQL("INSERT INTO sense_dictionary_provenance_fields VALUES(10, 'MEANING')")
            close()
        }
        helper.runMigrationsAndValidate("context-v6-v7", 7, true, MIGRATION_6_7).use { db ->
            db.query("SELECT id, entry_id, stable_id, meaning FROM senses ORDER BY id").use {
                assertTrue(it.moveToFirst())
                assertEquals(10L, it.getLong(0))
                assertEquals(1L, it.getLong(1))
                val firstId = it.getString(2)
                assertEquals(36, firstId.length)
                assertEquals("기대하다", it.getString(3))
                assertTrue(it.moveToNext())
                assertNotEquals(firstId, it.getString(2))
            }
            db.query("SELECT id, sense_id, stable_id, meaning, origin, source_title, source_url, source_locator, captured_at, sort_order FROM examples ORDER BY id").use {
                assertTrue(it.moveToFirst())
                assertEquals(100L, it.getLong(0))
                assertEquals(10L, it.getLong(1))
                val firstId = it.getString(2)
                assertEquals(36, firstId.length)
                assertEquals("", it.getString(3))
                assertEquals("UNKNOWN", it.getString(4))
                (5..8).forEach { column -> assertTrue(it.isNull(column)) }
                assertEquals(0, it.getInt(9))
                assertTrue(it.moveToNext())
                assertNotEquals(firstId, it.getString(2))
            }
            db.query("SELECT source_name, modified_after_import FROM sense_dictionary_provenance WHERE sense_id = 10").use {
                assertTrue(it.moveToFirst())
                assertEquals("Dictionary", it.getString(0))
                assertEquals(1, it.getInt(1))
            }
            db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        }
    }

    @Test
    fun completeMigrationChainStillPreservesOriginalChildIds() {
        helper.createDatabase("context-v1-v7", 1).apply {
            execSQL("INSERT INTO vocabulary_entries VALUES(1, 'phrase', 'en', '', 10, 20)")
            execSQL("INSERT INTO senses VALUES(10, 1, 'meaning', '', 0)")
            execSQL("INSERT INTO examples VALUES(100, 10, 'context', 0)")
            close()
        }
        helper.runMigrationsAndValidate("context-v1-v7", 7, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
        ).use { db ->
            db.query("SELECT id, sense_id, text, origin FROM examples").use {
                assertTrue(it.moveToFirst())
                assertEquals(100L, it.getLong(0))
                assertEquals(10L, it.getLong(1))
                assertEquals("context", it.getString(2))
                assertEquals("UNKNOWN", it.getString(3))
            }
            db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        }
    }
}
