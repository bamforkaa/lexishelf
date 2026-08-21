package com.example.localvocabulary.vocabulary.data

import com.example.localvocabulary.core.database.entity.ExampleEntity
import com.example.localvocabulary.core.database.entity.SenseEntity
import com.example.localvocabulary.core.database.entity.TagEntity
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity
import com.example.localvocabulary.core.database.relation.SenseWithExamples
import com.example.localvocabulary.core.database.relation.VocabularyEntryWithDetails
import org.junit.Assert.assertEquals
import org.junit.Test

class VocabularyMappersTest {
    @Test
    fun `room rows map to ordered domain senses and examples`() {
        val row = VocabularyEntryWithDetails(
            entry = VocabularyEntryEntity(
                id = 4,
                headword = "run",
                languageTag = "en",
                notes = "note",
                createdAtEpochMillis = 10,
                modifiedAtEpochMillis = 20,
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
                ),
            ),
            tags = listOf(
                TagEntity(2, "Verbs", "verbs"),
                TagEntity(1, "Actions", "actions"),
            ),
        )

        val entry = row.toDomain()

        assertEquals(4L, entry.id)
        assertEquals("run", entry.headword)
        assertEquals("en", entry.languageTag)
        assertEquals(listOf("move quickly", "operate"), entry.senses.map { it.meaning })
        assertEquals(listOf("First", "Second"), entry.senses.first().examples.map { it.text })
        assertEquals(listOf("Actions", "Verbs"), entry.tags.map { it.name })
        assertEquals("note", entry.notes)
        assertEquals(10L, entry.createdAtEpochMillis)
        assertEquals(20L, entry.modifiedAtEpochMillis)
    }
}
