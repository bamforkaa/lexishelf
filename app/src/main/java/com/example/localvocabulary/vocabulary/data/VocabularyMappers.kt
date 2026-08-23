package com.example.localvocabulary.vocabulary.data

import com.example.localvocabulary.core.database.relation.VocabularyEntryWithDetails
import com.example.localvocabulary.vocabulary.domain.ExampleSentence
import com.example.localvocabulary.vocabulary.domain.DictionaryProvenance
import com.example.localvocabulary.vocabulary.domain.ImportedDictionaryField
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
)
