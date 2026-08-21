package com.example.localvocabulary.vocabulary.data

import com.example.localvocabulary.core.database.relation.VocabularyEntryWithDetails
import com.example.localvocabulary.vocabulary.domain.ExampleSentence
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularySense
import com.example.localvocabulary.vocabulary.domain.VocabularyTag

internal fun VocabularyEntryWithDetails.toDomain(): VocabularyEntry = VocabularyEntry(
    id = entry.id,
    backupId = entry.backupId,
    headword = entry.headword,
    languageTag = entry.languageTag,
    senses = senses
        .sortedBy { it.sense.sortOrder }
        .map { relation ->
            VocabularySense(
                id = relation.sense.id,
                meaning = relation.sense.meaning,
                partOfSpeech = relation.sense.partOfSpeech,
                examples = relation.examples
                    .sortedBy { it.sortOrder }
                    .map { ExampleSentence(id = it.id, text = it.text) },
            )
        },
    notes = entry.notes,
    tags = tags.sortedBy { it.name.lowercase() }.map { VocabularyTag(it.id, it.backupId, it.name) },
    createdAtEpochMillis = entry.createdAtEpochMillis,
    modifiedAtEpochMillis = entry.modifiedAtEpochMillis,
)
