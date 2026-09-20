package com.example.localvocabulary.release

import com.example.localvocabulary.core.database.dao.ExampleWrite

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localvocabulary.core.database.MIGRATION_1_2
import com.example.localvocabulary.core.database.MIGRATION_2_3
import com.example.localvocabulary.core.database.MIGRATION_3_4
import com.example.localvocabulary.core.database.MIGRATION_4_5
import com.example.localvocabulary.core.database.MIGRATION_5_6
import com.example.localvocabulary.core.database.MIGRATION_6_7
import com.example.localvocabulary.core.database.MIGRATION_8_9
import com.example.localvocabulary.core.database.MIGRATION_7_8
import com.example.localvocabulary.core.database.VocabularyDatabase
import com.example.localvocabulary.core.database.dao.PronunciationWrite
import com.example.localvocabulary.core.database.dao.SenseWrite
import com.example.localvocabulary.core.database.entity.TagEntity
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity
import com.example.localvocabulary.core.database.entity.WordbookEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Explicit release-QA hook. A normal suite run is a no-op; host-driven write/verify invocations
 * bracket an update install without adding any production-only database pathway.
 */
class ReleaseUpdatePersistenceProbeTest {
    @Test
    fun updateInstallPreservesUserAggregate() = runBlocking {
        val mode = InstrumentationRegistry.getArguments().getString(ARGUMENT).orEmpty()
        if (mode.isEmpty()) return@runBlocking

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = openProductionDatabase(context)
        try {
            when (mode) {
                WRITE -> writeProbe(database)
                VERIFY -> verifyAndRemoveProbe(database)
                else -> error("Unknown release update probe mode: $mode")
            }
        } finally {
            database.close()
        }
    }

    private fun openProductionDatabase(context: Context): VocabularyDatabase =
        Room.databaseBuilder(context, VocabularyDatabase::class.java, DATABASE_NAME)
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8, MIGRATION_8_9,
            )
            .build()

    private suspend fun writeProbe(database: VocabularyDatabase) {
        database.vocabularyDao().findEntryByBackupId(ENTRY_BACKUP_ID)?.let { existing ->
            database.vocabularyDao().deleteEntry(existing.id)
        }
        database.tagDao().findByNormalizedName(TAG_NAME)?.let { database.tagDao().delete(it.id) }
        database.wordbookDao().findByNormalizedName(WORD_BOOK_NAME)?.let {
            database.wordbookDao().delete(it.id)
        }
        val tagId = database.tagDao().insert(
            TagEntity(backupId = TAG_BACKUP_ID, name = TAG_NAME, normalizedName = TAG_NAME),
        )
        val wordbookId = database.wordbookDao().insert(
            WordbookEntity(
                backupId = WORD_BOOK_BACKUP_ID,
                name = WORD_BOOK_NAME,
                normalizedName = WORD_BOOK_NAME,
            ),
        )
        database.vocabularyDao().saveEntry(
            entry = VocabularyEntryEntity(
                backupId = ENTRY_BACKUP_ID,
                headword = "look forward to",
                languageTag = "en",
                notes = "release update probe",
                createdAtEpochMillis = 1,
                modifiedAtEpochMillis = 1,
                reading = "look forward to",
            ),
            senses = listOf(SenseWrite("기대하다", "phrasal verb", listOf("I look forward to it.").map { ExampleWrite(text = it) })),
            tagIds = setOf(tagId),
            wordbookIds = setOf(wordbookId),
            pronunciations = listOf(
                PronunciationWrite("release-probe-pronunciation", "OTHER", "look forward to", "en"),
            ),
        )
    }

    private suspend fun verifyAndRemoveProbe(database: VocabularyDatabase) {
        assertDatabaseIntegrity(database)
        val entry = database.vocabularyDao().findEntryByBackupId(ENTRY_BACKUP_ID)
        assertNotNull(entry)
        val aggregate = database.vocabularyDao().observeEntry(requireNotNull(entry).id).first()
        assertNotNull(aggregate)
        assertEquals("look forward to", aggregate?.entry?.headword)
        assertEquals("기대하다", aggregate?.senses?.single()?.sense?.meaning)
        assertEquals("I look forward to it.", aggregate?.senses?.single()?.examples?.single()?.text)
        assertEquals(TAG_NAME, aggregate?.tags?.single()?.name)
        assertEquals(WORD_BOOK_NAME, aggregate?.wordbooks?.single()?.name)
        assertEquals("look forward to", aggregate?.pronunciations?.single()?.pronunciation?.value)

        database.vocabularyDao().deleteEntry(entry.id)
        database.tagDao().findByNormalizedName(TAG_NAME)?.let { database.tagDao().delete(it.id) }
        database.wordbookDao().findByNormalizedName(WORD_BOOK_NAME)?.let {
            database.wordbookDao().delete(it.id)
        }
    }

    private fun assertDatabaseIntegrity(database: VocabularyDatabase) {
        database.openHelper.readableDatabase.query("PRAGMA quick_check").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0))
        }
        database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals(0, cursor.count)
        }
    }

    private companion object {
        const val DATABASE_NAME = "vocabulary.db"
        const val ARGUMENT = "releaseUpdateProbe"
        const val WRITE = "write"
        const val VERIFY = "verify"
        const val ENTRY_BACKUP_ID = "release-update-probe-entry"
        const val TAG_BACKUP_ID = "release-update-probe-tag"
        const val WORD_BOOK_BACKUP_ID = "release-update-probe-wordbook"
        const val TAG_NAME = "release-update-probe-tag"
        const val WORD_BOOK_NAME = "release-update-probe-wordbook"
    }
}
