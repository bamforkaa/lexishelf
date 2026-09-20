package com.example.localvocabulary.vocabulary.data

import com.example.localvocabulary.vocabulary.domain.ExampleOrigin

import com.example.localvocabulary.core.database.relation.VocabularyEntryWithDetails
import com.example.localvocabulary.core.database.relation.VocabularyListEntryWithDetails
import com.example.localvocabulary.vocabulary.domain.ExampleSentence
import com.example.localvocabulary.vocabulary.domain.DictionaryProvenance
import com.example.localvocabulary.vocabulary.domain.ImportedDictionaryField
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularySense
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import com.example.localvocabulary.vocabulary.domain.VocabularyWordbook
import com.example.localvocabulary.vocabulary.domain.GrammaticalGenderCategory
import com.example.localvocabulary.vocabulary.domain.PronunciationNotation
import com.example.localvocabulary.vocabulary.domain.VocabularyGrammaticalGender
import com.example.localvocabulary.vocabulary.domain.VocabularyPronunciation

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
                stableId = relation.sense.stableId,
                meaning = relation.sense.meaning,
                partOfSpeech = relation.sense.partOfSpeech,
                examples = relation.examples
                    .sortedBy { it.sortOrder }
                    .map {
                        ExampleSentence(
                            id = it.id, text = it.text, stableId = it.stableId,
                            meaning = it.meaning,
                            origin = ExampleOrigin.valueOf(it.origin),
                            sourceTitle = it.sourceTitle, sourceUrl = it.sourceUrl,
                            sourceLocator = it.sourceLocator, capturedAt = it.capturedAt,
                        )
                    },
                provenance = relation.provenance?.let { provenance ->
                    DictionaryProvenance(
                        providerId = provenance.providerId,
                        sourceEntryId = provenance.sourceEntryId,
                        sourceSenseId = provenance.sourceSenseId,
                        sourceName = provenance.sourceName,
                        sourceUrl = provenance.sourceUrl,
                        licenseName = provenance.licenseName,
                        licenseUrl = provenance.licenseUrl,
                        datasetVersion = provenance.datasetVersion,
                        importedFields = relation.provenanceFields.mapTo(linkedSetOf()) {
                            ImportedDictionaryField.valueOf(it.field)
                        },
                        importedAtEpochMillis = provenance.importedAtEpochMillis,
                        modifiedAfterImport = provenance.modifiedAfterImport,
                    )
                },
                grammaticalGender = relation.sense.grammaticalGender?.let { category ->
                    VocabularyGrammaticalGender(
                        category = GrammaticalGenderCategory.valueOf(category),
                        rawValue = relation.sense.grammaticalGenderRaw,
                    )
                },
            )
        },
    notes = entry.notes,
    tags = tags.sortedBy { it.name.lowercase() }.map { VocabularyTag(it.id, it.backupId, it.name) },
    createdAtEpochMillis = entry.createdAtEpochMillis,
    modifiedAtEpochMillis = entry.modifiedAtEpochMillis,
    reading = entry.reading,
    readingProvenance = entryProvenance.singleOrNull { it.field == "READING" }?.let { provenance ->
        DictionaryProvenance(
            providerId = provenance.providerId,
            sourceEntryId = provenance.sourceEntryId,
            sourceSenseId = provenance.sourceSenseId,
            sourceName = provenance.sourceName,
            sourceUrl = provenance.sourceUrl,
            licenseName = provenance.licenseName,
            licenseUrl = provenance.licenseUrl,
            datasetVersion = provenance.datasetVersion,
            importedFields = setOf(ImportedDictionaryField.READING),
            importedAtEpochMillis = provenance.importedAtEpochMillis,
            modifiedAfterImport = provenance.modifiedAfterImport,
        )
    },
    pronunciations = pronunciations.sortedBy { it.pronunciation.sortOrder }.map { relation ->
        val pronunciation = relation.pronunciation
        VocabularyPronunciation(
            id = pronunciation.id,
            stableId = pronunciation.stableId,
            notation = PronunciationNotation.valueOf(pronunciation.notation),
            value = pronunciation.value,
            languageTag = pronunciation.languageTag,
            provenance = relation.provenance?.let { provenance ->
                DictionaryProvenance(
                    providerId = provenance.providerId,
                    sourceEntryId = provenance.sourceEntryId,
                    sourceSenseId = provenance.sourceSenseId,
                    sourceName = provenance.sourceName,
                    sourceUrl = provenance.sourceUrl,
                    licenseName = provenance.licenseName,
                    licenseUrl = provenance.licenseUrl,
                    datasetVersion = provenance.datasetVersion,
                    importedFields = setOf(ImportedDictionaryField.PRONUNCIATION),
                    importedAtEpochMillis = provenance.importedAtEpochMillis,
                    modifiedAfterImport = provenance.modifiedAfterImport,
                )
            },
        )
    },
    wordbooks = wordbooks.sortedBy { it.name.lowercase() }.map {
        VocabularyWordbook(it.id, it.backupId, it.name)
    },
)

internal fun VocabularyListEntryWithDetails.toDomain(): VocabularyEntry = VocabularyEntryWithDetails(
    entry = entry,
    entryProvenance = entryProvenance,
    pronunciations = emptyList(),
    senses = senses,
    tags = tags,
    wordbooks = wordbooks,
).toDomain()
