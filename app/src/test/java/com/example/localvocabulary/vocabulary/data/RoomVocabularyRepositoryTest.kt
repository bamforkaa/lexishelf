package com.example.localvocabulary.vocabulary.data

import com.example.localvocabulary.core.common.TimeProvider
import com.example.localvocabulary.core.common.StableIdGenerator
import com.example.localvocabulary.core.database.dao.SenseWrite
import com.example.localvocabulary.core.database.dao.VocabularyDao
import com.example.localvocabulary.core.database.entity.EntryTagCrossRef
import com.example.localvocabulary.core.database.entity.ExampleEntity
import com.example.localvocabulary.core.database.entity.SenseEntity
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity
import com.example.localvocabulary.core.database.relation.VocabularyEntryWithDetails
import com.example.localvocabulary.vocabulary.domain.ValidatedVocabularyDraft
import com.example.localvocabulary.vocabulary.domain.VocabularySenseDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomVocabularyRepositoryTest {
    @Test
    fun `creating an entry writes timestamps and the complete aggregate`() = runTest {
        val dao = FakeVocabularyDao()
        val repository = RoomVocabularyRepository(dao, TimeProvider { 500 }, StableIdGenerator { "entry-new" })

        val id = repository.save(
            ValidatedVocabularyDraft(
                id = null,
                headword = "created",
                languageTag = "en",
                senses = listOf(
                    VocabularySenseDraft("first", "noun", listOf("one", "two")),
                    VocabularySenseDraft("second", "verb", emptyList()),
                ),
                notes = "note",
                tagIds = setOf(2, 3),
            ),
        )

        assertEquals(1L, id)
        assertEquals(500L, dao.storedEntry?.createdAtEpochMillis)
        assertEquals(500L, dao.storedEntry?.modifiedAtEpochMillis)
        assertEquals(listOf("first", "second"), dao.savedSenses.map { it.meaning })
        assertEquals(listOf("one", "two"), dao.savedSenses.first().examples)
        assertEquals(setOf(2L, 3L), dao.savedTagIds)
    }

    @Test
    fun `updating an entry preserves creation time and replaces aggregate`() = runTest {
        val dao = FakeVocabularyDao(
            storedEntry = VocabularyEntryEntity(
                id = 8,
                backupId = "entry-8",
                headword = "old",
                languageTag = "en",
                notes = "old note",
                createdAtEpochMillis = 100,
                modifiedAtEpochMillis = 200,
            ),
        )
        val repository = RoomVocabularyRepository(dao, TimeProvider { 500 }, StableIdGenerator { "unused" })

        repository.save(
            ValidatedVocabularyDraft(
                id = 8,
                headword = "edited",
                languageTag = "en",
                senses = listOf(VocabularySenseDraft("new meaning", "noun", listOf("example"))),
                notes = "user text",
                tagIds = setOf(2, 3),
            ),
        )

        assertEquals(100L, dao.storedEntry?.createdAtEpochMillis)
        assertEquals(500L, dao.storedEntry?.modifiedAtEpochMillis)
        assertEquals("edited", dao.storedEntry?.headword)
        assertEquals(listOf("new meaning"), dao.savedSenses.map { it.meaning })
        assertEquals(setOf(2L, 3L), dao.savedTagIds)
    }

    @Test
    fun `search treats SQL wildcard characters as literal user text`() {
        val dao = FakeVocabularyDao()
        val repository = RoomVocabularyRepository(dao, TimeProvider { 500 }, StableIdGenerator { "unused" })

        repository.observeEntries("  100%_\\  ", 7)

        assertEquals("100\\%\\_\\\\", dao.observedQuery)
        assertEquals(7L, dao.observedTagId)
    }
}

private class FakeVocabularyDao(
    var storedEntry: VocabularyEntryEntity? = null,
) : VocabularyDao {
    val savedSenses = mutableListOf<SenseWrite>()
    val savedTagIds = mutableSetOf<Long>()
    var observedQuery: String? = null
    var observedTagId: Long? = null
    private var nextSenseId = 1L

    override fun observeEntries(query: String, tagId: Long?): Flow<List<VocabularyEntryWithDetails>> {
        observedQuery = query
        observedTagId = tagId
        return flowOf(emptyList())
    }

    override fun observeEntry(id: Long): Flow<VocabularyEntryWithDetails?> = flowOf(null)

    override suspend fun getAllEntries(): List<VocabularyEntryWithDetails> = emptyList()

    override suspend fun findEntryByBackupId(backupId: String): VocabularyEntryEntity? =
        storedEntry?.takeIf { it.backupId == backupId }

    override suspend fun findEntryEntity(id: Long): VocabularyEntryEntity? =
        storedEntry?.takeIf { it.id == id }

    override suspend fun insertEntry(entry: VocabularyEntryEntity): Long {
        storedEntry = entry.copy(id = 1)
        return 1
    }

    override suspend fun updateEntry(entry: VocabularyEntryEntity): Int {
        storedEntry = entry
        return 1
    }

    override suspend fun deleteEntry(id: Long) {
        storedEntry = null
    }

    override suspend fun deleteAllEntries() {
        storedEntry = null
    }

    override suspend fun deleteSenses(entryId: Long) {
        savedSenses.clear()
    }

    override suspend fun insertSense(sense: SenseEntity): Long {
        savedSenses += SenseWrite(sense.meaning, sense.partOfSpeech, emptyList())
        return nextSenseId++
    }

    override suspend fun insertExamples(examples: List<ExampleEntity>) {
        if (examples.isNotEmpty()) {
            val last = savedSenses.last()
            savedSenses[savedSenses.lastIndex] = last.copy(examples = examples.map { it.text })
        }
    }

    override suspend fun deleteEntryTags(entryId: Long) {
        savedTagIds.clear()
    }

    override suspend fun insertEntryTags(crossRefs: List<EntryTagCrossRef>) {
        savedTagIds += crossRefs.map { it.tagId }
    }
}
