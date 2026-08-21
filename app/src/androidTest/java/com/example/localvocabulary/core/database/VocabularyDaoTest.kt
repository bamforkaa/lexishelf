package com.example.localvocabulary.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localvocabulary.core.database.dao.SenseWrite
import com.example.localvocabulary.core.database.entity.TagEntity
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VocabularyDaoTest {
    private lateinit var database: VocabularyDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VocabularyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun creatingEntryCanBeReadBackWithOrderedSensesExamplesAndMultipleTags() = runTest {
        val firstTagId = insertTag("Important")
        val secondTagId = insertTag("Review")
        val entryId = database.vocabularyDao().saveEntry(
            entry = entry(headword = "serendipity", notes = "manual note"),
            senses = listOf(
                SenseWrite(
                    meaning = "fortunate discovery",
                    partOfSpeech = "noun",
                    examples = listOf("First example.", "Second example."),
                ),
                SenseWrite("unexpected event", "noun", listOf("Another example.")),
            ),
            tagIds = setOf(firstTagId, secondTagId),
        )

        val stored = database.vocabularyDao().observeEntry(entryId).first()
        assertNotNull(stored)
        val orderedSenses = stored!!.senses.sortedBy { it.sense.sortOrder }

        assertEquals(
            listOf("fortunate discovery", "unexpected event"),
            orderedSenses.map { it.sense.meaning },
        )
        assertEquals(
            listOf("First example.", "Second example."),
            orderedSenses.first().examples.sortedBy { it.sortOrder }.map { it.text },
        )
        assertEquals(setOf("Important", "Review"), stored.tags.mapTo(mutableSetOf()) { it.name })
    }

    @Test
    fun updatingEntryReplacesSensesExamplesAndTagsWithoutDuplicates() = runTest {
        val removedTagId = insertTag("Remove")
        val retainedTagId = insertTag("Retain")
        val addedTagId = insertTag("Add")
        val entryId = database.vocabularyDao().saveEntry(
            entry = entry(headword = "old"),
            senses = listOf(
                SenseWrite("old first", "noun", listOf("old example")),
                SenseWrite("old second", "verb", emptyList()),
            ),
            tagIds = setOf(removedTagId, retainedTagId),
        )

        database.vocabularyDao().saveEntry(
            entry = entry(id = entryId, headword = "edited", notes = "edited note", modifiedAt = 2),
            senses = listOf(
                SenseWrite("new first", "adjective", listOf("new one", "new two")),
                SenseWrite("new second", "noun", emptyList()),
            ),
            tagIds = setOf(retainedTagId, addedTagId),
        )

        val stored = database.vocabularyDao().observeEntry(entryId).first()!!
        val senses = stored.senses.sortedBy { it.sense.sortOrder }
        assertEquals("edited", stored.entry.headword)
        assertEquals("edited note", stored.entry.notes)
        assertEquals(listOf("new first", "new second"), senses.map { it.sense.meaning })
        assertEquals(
            listOf("new one", "new two"),
            senses.first().examples.sortedBy { it.sortOrder }.map { it.text },
        )
        assertEquals(setOf("Retain", "Add"), stored.tags.mapTo(mutableSetOf()) { it.name })
        assertEquals(2L, tableCount("senses"))
        assertEquals(2L, tableCount("examples"))
        assertEquals(2L, tableCount("entry_tag_cross_refs"))
    }

    @Test
    fun deletingEntryCascadesAllOwnedRowsAndKeepsTags() = runTest {
        val firstTagId = insertTag("First")
        val secondTagId = insertTag("Second")
        val entryId = database.vocabularyDao().saveEntry(
            entry = entry(headword = "word"),
            senses = listOf(
                SenseWrite("first", "noun", listOf("one")),
                SenseWrite("second", "verb", listOf("two")),
            ),
            tagIds = setOf(firstTagId, secondTagId),
        )

        database.vocabularyDao().deleteEntry(entryId)

        assertNull(database.vocabularyDao().observeEntry(entryId).first())
        assertEquals(0L, tableCount("vocabulary_entries"))
        assertEquals(0L, tableCount("senses"))
        assertEquals(0L, tableCount("examples"))
        assertEquals(0L, tableCount("entry_tag_cross_refs"))
        assertEquals(2L, tableCount("tags"))
    }

    @Test
    fun deletingTagRemovesOnlyCrossReference() = runTest {
        val tagId = insertTag("Keep")
        database.vocabularyDao().saveEntry(
            entry = entry(headword = "word"),
            senses = listOf(SenseWrite("meaning", "", emptyList())),
            tagIds = setOf(tagId),
        )

        database.tagDao().delete(tagId)

        val entries = database.vocabularyDao().observeEntries("", null).first()
        assertEquals(1, entries.size)
        assertTrue(entries.single().tags.isEmpty())
        assertEquals(0L, tableCount("entry_tag_cross_refs"))
    }

    @Test
    fun textSearchAndTagFilterCoverStoredTextAndEmptyQuery() = runTest {
        val firstTagId = insertTag("First")
        val secondTagId = insertTag("Second")
        val firstEntryId = database.vocabularyDao().saveEntry(
            entry = entry(headword = "Alpha", notes = "private memo"),
            senses = listOf(SenseWrite("hidden needle", "noun", listOf("example phrase"))),
            tagIds = setOf(firstTagId, secondTagId),
        )
        database.vocabularyDao().saveEntry(
            entry = entry(headword = "Beta", modifiedAt = 2),
            senses = listOf(SenseWrite("other", "", emptyList())),
            tagIds = setOf(secondTagId),
        )

        listOf("ALPHA", "needle", "phrase", "memo").forEach { query ->
            assertEquals(
                firstEntryId,
                database.vocabularyDao().observeEntries(query, null).first().single().entry.id,
            )
        }
        assertEquals(2, database.vocabularyDao().observeEntries("", null).first().size)
        assertEquals(
            listOf(firstEntryId),
            database.vocabularyDao().observeEntries("", firstTagId).first().map { it.entry.id },
        )
        assertEquals(2, database.vocabularyDao().observeEntries("", secondTagId).first().size)
        assertTrue(database.vocabularyDao().observeEntries("missing", null).first().isEmpty())
    }

    @Test
    fun observingMissingEntryReturnsNull() = runTest {
        assertNull(database.vocabularyDao().observeEntry(999).first())
    }

    @Test
    fun updatingMissingEntryFailsWithoutCreatingPartialRows() = runTest {
        val failure = runCatching {
            database.vocabularyDao().saveEntry(
                entry = entry(id = 999, headword = "missing"),
                senses = listOf(SenseWrite("meaning", "noun", listOf("example"))),
                tagIds = emptySet(),
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(0L, tableCount("vocabulary_entries"))
        assertEquals(0L, tableCount("senses"))
        assertEquals(0L, tableCount("examples"))
    }

    @Test
    fun failedAggregateUpdateRollsBackEveryRelationChange() = runTest {
        val tagId = insertTag("Stable")
        val entryId = database.vocabularyDao().saveEntry(
            entry = entry(headword = "original"),
            senses = listOf(SenseWrite("original meaning", "noun", listOf("original example"))),
            tagIds = setOf(tagId),
        )

        val failure = runCatching {
            database.vocabularyDao().saveEntry(
                entry = entry(id = entryId, headword = "partial update", modifiedAt = 2),
                senses = listOf(SenseWrite("replacement", "verb", emptyList())),
                tagIds = setOf(Long.MAX_VALUE),
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        val stored = database.vocabularyDao().observeEntry(entryId).first()!!
        assertEquals("original", stored.entry.headword)
        assertEquals("original meaning", stored.senses.single().sense.meaning)
        assertEquals("original example", stored.senses.single().examples.single().text)
        assertEquals("Stable", stored.tags.single().name)
        assertEquals(1L, tableCount("senses"))
        assertEquals(1L, tableCount("examples"))
        assertEquals(1L, tableCount("entry_tag_cross_refs"))
    }

    private suspend fun insertTag(name: String): Long = database.tagDao().insert(
        TagEntity(backupId = "tag-$name", name = name, normalizedName = name.lowercase()),
    )

    private fun entry(
        id: Long = 0,
        headword: String,
        notes: String = "",
        modifiedAt: Long = 1,
    ) = VocabularyEntryEntity(
        id = id,
        backupId = "entry-${if (id == 0L) headword else id}",
        headword = headword,
        languageTag = "en",
        notes = notes,
        createdAtEpochMillis = 1,
        modifiedAtEpochMillis = modifiedAt,
    )

    private fun tableCount(tableName: String): Long =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM $tableName")
            .use { cursor ->
                check(cursor.moveToFirst())
                cursor.getLong(0)
            }
}
