package com.example.localvocabulary.vocabulary.data

import com.example.localvocabulary.core.common.TimeProvider
import com.example.localvocabulary.core.common.StableIdGenerator
import com.example.localvocabulary.core.database.dao.SenseWrite
import com.example.localvocabulary.core.database.dao.SenseDictionaryProvenanceWrite
import com.example.localvocabulary.core.database.dao.VocabularyDao
import com.example.localvocabulary.core.database.entity.EntryTagCrossRef
import com.example.localvocabulary.core.database.entity.EntryDictionaryProvenanceEntity
import com.example.localvocabulary.core.database.entity.ExampleEntity
import com.example.localvocabulary.core.database.entity.SenseEntity
import com.example.localvocabulary.core.database.entity.SenseDictionaryProvenanceEntity
import com.example.localvocabulary.core.database.entity.SenseDictionaryProvenanceFieldEntity
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity
import com.example.localvocabulary.core.database.relation.VocabularyEntryWithDetails
import com.example.localvocabulary.vocabulary.domain.ValidatedVocabularyDraft
import com.example.localvocabulary.vocabulary.domain.DictionaryProvenance
import com.example.localvocabulary.vocabulary.domain.ImportedDictionaryField
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
    fun `provider provenance is written only for imported senses`() = runTest {
        val dao = FakeVocabularyDao()
        val repository = RoomVocabularyRepository(
            dao,
            TimeProvider { 500 },
            StableIdGenerator { "entry-imported" },
        )
        val provenance = DictionaryProvenance(
            providerId = "cc-cedict",
            sourceEntryId = "source-key",
            sourceSenseId = "0",
            sourceName = "CC-CEDICT",
            sourceUrl = "https://cc-cedict.org/editor/editor.php?handler=Download",
            licenseName = "Creative Commons Attribution-ShareAlike 4.0 International",
            licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
            datasetVersion = "release-id",
            importedFields = setOf(ImportedDictionaryField.MEANING),
            importedAtEpochMillis = 400,
            modifiedAfterImport = false,
        )

        repository.save(
            ValidatedVocabularyDraft(
                id = null,
                headword = "word",
                languageTag = "zh-Hans",
                senses = listOf(
                    VocabularySenseDraft("provider meaning", "", emptyList(), provenance),
                    VocabularySenseDraft("user meaning", "", emptyList()),
                ),
                notes = "",
                tagIds = emptySet(),
            ),
        )

        assertEquals("cc-cedict", dao.savedSenses.first().provenance?.providerId)
        assertEquals(setOf("MEANING"), dao.savedSenses.first().provenance?.importedFields)
        assertEquals(null, dao.savedSenses.last().provenance)
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

    override suspend fun insertSenseProvenance(provenance: SenseDictionaryProvenanceEntity) {
        val last = savedSenses.last()
        savedSenses[savedSenses.lastIndex] = last.copy(
            provenance = SenseDictionaryProvenanceWrite(
                providerId = provenance.providerId,
                sourceEntryId = provenance.sourceEntryId,
                sourceSenseId = provenance.sourceSenseId,
                sourceName = provenance.sourceName,
                sourceUrl = provenance.sourceUrl,
                licenseName = provenance.licenseName,
                licenseUrl = provenance.licenseUrl,
                datasetVersion = provenance.datasetVersion,
                importedFields = emptySet(),
                importedAtEpochMillis = provenance.importedAtEpochMillis,
                modifiedAfterImport = provenance.modifiedAfterImport,
            ),
        )
    }

    override suspend fun insertSenseProvenanceFields(
        fields: List<SenseDictionaryProvenanceFieldEntity>,
    ) {
        val last = savedSenses.last()
        savedSenses[savedSenses.lastIndex] = last.copy(
            provenance = last.provenance?.copy(
                importedFields = fields.mapTo(linkedSetOf()) { it.field },
            ),
        )
    }

    override suspend fun deleteEntryProvenance(entryId: Long) = Unit

    override suspend fun insertEntryProvenance(provenance: EntryDictionaryProvenanceEntity) = Unit

    override suspend fun deleteEntryTags(entryId: Long) {
        savedTagIds.clear()
    }

    override suspend fun insertEntryTags(crossRefs: List<EntryTagCrossRef>) {
        savedTagIds += crossRefs.map { it.tagId }
    }
}
