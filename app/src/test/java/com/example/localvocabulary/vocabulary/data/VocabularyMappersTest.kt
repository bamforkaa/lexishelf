package com.example.localvocabulary.vocabulary.data

import com.example.localvocabulary.core.database.entity.ExampleEntity
import com.example.localvocabulary.core.database.entity.EntryDictionaryProvenanceEntity
import com.example.localvocabulary.core.database.entity.SenseEntity
import com.example.localvocabulary.core.database.entity.SenseDictionaryProvenanceEntity
import com.example.localvocabulary.core.database.entity.SenseDictionaryProvenanceFieldEntity
import com.example.localvocabulary.core.database.entity.TagEntity
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity
import com.example.localvocabulary.core.database.relation.SenseWithExamples
import com.example.localvocabulary.core.database.relation.VocabularyEntryWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyMappersTest {
    @Test
    fun `room rows map to ordered domain senses and examples`() {
        val row = VocabularyEntryWithDetails(
            entry = VocabularyEntryEntity(
                id = 4,
                backupId = "entry-4",
                headword = "run",
                languageTag = "en",
                notes = "note",
                createdAtEpochMillis = 10,
                modifiedAtEpochMillis = 20,
                reading = "rʌn",
            ),
            entryProvenance = listOf(
                EntryDictionaryProvenanceEntity(
                    entryId = 4,
                    field = "READING",
                    providerId = "test.dictionary",
                    sourceEntryId = "run-1",
                    sourceSenseId = "0",
                    sourceName = "Test Dictionary",
                    sourceUrl = "https://example.invalid/source",
                    licenseName = "Test License",
                    licenseUrl = "https://example.invalid/license",
                    datasetVersion = "2026-01",
                    importedAtEpochMillis = 9,
                    modifiedAfterImport = true,
                ),
            ),
            senses = listOf(
                SenseWithExamples(
                    sense = SenseEntity(2, 4, "operate", "verb", 1),
                    examples = emptyList(),
                ),
                SenseWithExamples(
                    sense = SenseEntity(1, 4, "move quickly", "verb", 0),
                    examples = listOf(
                        ExampleEntity(2, 1, "Second", 1),
                        ExampleEntity(1, 1, "First", 0),
                    ),
                    provenance = SenseDictionaryProvenanceEntity(
                        senseId = 1,
                        providerId = "test.dictionary",
                        sourceEntryId = "run-1",
                        sourceSenseId = "0",
                        sourceName = "Test Dictionary",
                        sourceUrl = "https://example.invalid/source",
                        licenseName = "Test License",
                        licenseUrl = "https://example.invalid/license",
                        datasetVersion = "2026-01",
                        importedAtEpochMillis = 9,
                        modifiedAfterImport = true,
                    ),
                    provenanceFields = listOf(
                        SenseDictionaryProvenanceFieldEntity(1, "MEANING"),
                        SenseDictionaryProvenanceFieldEntity(1, "EXAMPLES"),
                    ),
                ),
            ),
            tags = listOf(
                TagEntity(id = 2, backupId = "tag-2", name = "Verbs", normalizedName = "verbs"),
                TagEntity(id = 1, backupId = "tag-1", name = "Actions", normalizedName = "actions"),
            ),
        )

        val entry = row.toDomain()

        assertEquals(4L, entry.id)
        assertEquals("run", entry.headword)
        assertEquals("en", entry.languageTag)
        assertEquals("rʌn", entry.reading)
        assertEquals("test.dictionary", entry.readingProvenance?.providerId)
        assertEquals(setOf("READING"), entry.readingProvenance?.importedFields?.map { it.name }?.toSet())
        assertEquals(listOf("move quickly", "operate"), entry.senses.map { it.meaning })
        assertEquals(listOf("First", "Second"), entry.senses.first().examples.map { it.text })
        assertEquals("test.dictionary", entry.senses.first().provenance?.providerId)
        assertEquals(
            setOf("MEANING", "EXAMPLES"),
            entry.senses.first().provenance?.importedFields?.mapTo(mutableSetOf()) { it.name },
        )
        assertTrue(entry.senses.first().provenance!!.modifiedAfterImport)
        assertEquals(null, entry.senses.last().provenance)
        assertEquals(listOf("Actions", "Verbs"), entry.tags.map { it.name })
        assertEquals("note", entry.notes)
        assertEquals(10L, entry.createdAtEpochMillis)
        assertEquals(20L, entry.modifiedAtEpochMillis)
    }
}
