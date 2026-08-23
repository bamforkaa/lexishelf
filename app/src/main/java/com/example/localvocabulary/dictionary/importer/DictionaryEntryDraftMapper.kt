package com.example.localvocabulary.dictionary.importer

import com.example.localvocabulary.dictionary.domain.DictionaryAttribution
import com.example.localvocabulary.dictionary.domain.DictionaryContentField
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry
import com.example.localvocabulary.vocabulary.domain.DictionaryProvenance
import com.example.localvocabulary.vocabulary.domain.ImportedDictionaryField
import com.example.localvocabulary.vocabulary.domain.VocabularyEntryDraft
import com.example.localvocabulary.vocabulary.domain.VocabularySenseDraft

data class DictionarySourceReference(
    val providerId: DictionaryProviderId,
    val sourceEntryId: String?,
    val datasetVersion: String?,
    val attribution: DictionaryAttribution,
)

data class DictionaryEditorSeed(
    val draft: VocabularyEntryDraft,
    val source: DictionarySourceReference,
    val transientEntry: ExternalDictionaryEntry,
    val copiedProviderFields: Set<DictionaryContentField>,
)

sealed interface DictionaryEntryDraftMappingResult {
    data class Ready(val seed: DictionaryEditorSeed) : DictionaryEntryDraftMappingResult

    data class ReferenceOnly(
        val manualDraft: VocabularyEntryDraft,
        val source: DictionarySourceReference,
        val transientEntry: ExternalDictionaryEntry,
    ) : DictionaryEntryDraftMappingResult
}

object DictionaryEntryDraftMapper {
    fun map(
        entry: ExternalDictionaryEntry,
        importedAtEpochMillis: Long,
    ): DictionaryEntryDraftMappingResult {
        val source = DictionarySourceReference(
            providerId = entry.providerId,
            sourceEntryId = entry.sourceEntryId,
            datasetVersion = entry.datasetVersion,
            attribution = entry.attribution,
        )
        val policy = entry.attribution.usagePolicy
        val copiedFields = linkedSetOf<DictionaryContentField>()
        val headword = entry.headword.takeIf {
            it.isNotBlank() && policy.permitsExportableVocabularyCopy(DictionaryContentField.HEADWORD)
        }?.also {
            copiedFields += DictionaryContentField.HEADWORD
        }.orEmpty()
        val licenseName = entry.attribution.licenseName?.takeIf(String::isNotBlank)
        val reading = entry.linguisticFeatures.reading?.text?.takeIf {
            it.isNotBlank() && policy.permitsExportableVocabularyCopy(DictionaryContentField.READING)
        }?.also {
            copiedFields += DictionaryContentField.READING
        }.orEmpty()
        val senses = if (licenseName == null) {
            emptyList()
        } else {
            entry.senses.mapIndexedNotNull { senseIndex, sense ->
                val senseFields = linkedSetOf<DictionaryContentField>()
                val meanings = sense.meanings.mapNotNull { meaning ->
                    val meaningField = when (meaning.kind) {
                        DictionaryResultKind.MONOLINGUAL_DEFINITION ->
                            DictionaryContentField.MONOLINGUAL_DEFINITION
                        DictionaryResultKind.TRANSLATION -> DictionaryContentField.TRANSLATION
                    }
                    if (
                        meaning.text.isBlank() ||
                        !policy.permitsExportableVocabularyCopy(meaningField)
                    ) {
                        return@mapNotNull null
                    }
                    copiedFields += meaningField
                    senseFields += meaningField
                    meaning.text
                }
                if (meanings.isEmpty()) return@mapIndexedNotNull null

                val partOfSpeech = sense.partOfSpeech?.takeIf {
                    it.isNotBlank() &&
                        policy.permitsExportableVocabularyCopy(DictionaryContentField.PART_OF_SPEECH)
                }?.also {
                    copiedFields += DictionaryContentField.PART_OF_SPEECH
                    senseFields += DictionaryContentField.PART_OF_SPEECH
                }.orEmpty()
                val examples = if (
                    policy.permitsExportableVocabularyCopy(DictionaryContentField.EXAMPLE)
                ) {
                    sense.examples.map { it.text }.filter(String::isNotBlank).also {
                        if (it.isNotEmpty()) {
                            copiedFields += DictionaryContentField.EXAMPLE
                            senseFields += DictionaryContentField.EXAMPLE
                        }
                    }
                } else {
                    emptyList()
                }

                VocabularySenseDraft(
                    meaning = meanings.joinToString("; "),
                    partOfSpeech = partOfSpeech,
                    examples = examples,
                    provenance = DictionaryProvenance(
                        providerId = entry.providerId.value,
                        sourceEntryId = entry.sourceEntryId,
                        sourceSenseId = sense.sourceSenseId ?: senseIndex.toString(),
                        sourceName = entry.attribution.sourceName,
                        sourceUrl = entry.attribution.sourceUrl,
                        licenseName = licenseName,
                        licenseUrl = entry.attribution.licenseUrl,
                        datasetVersion = entry.datasetVersion,
                        importedFields = senseFields.mapTo(linkedSetOf()) { it.toImportedField() },
                        importedAtEpochMillis = importedAtEpochMillis,
                        modifiedAfterImport = false,
                    ),
                )
            }
        }
        val draft = VocabularyEntryDraft(
            headword = headword,
            languageTag = entry.sourceLanguage.value,
            senses = senses.ifEmpty { listOf(emptySense()) },
            notes = "",
            tagIds = emptySet(),
            reading = reading,
            readingProvenance = if (reading.isNotEmpty() && licenseName != null) {
                DictionaryProvenance(
                    providerId = entry.providerId.value,
                    sourceEntryId = entry.sourceEntryId,
                    sourceSenseId = entry.senses.firstOrNull()?.sourceSenseId,
                    sourceName = entry.attribution.sourceName,
                    sourceUrl = entry.attribution.sourceUrl,
                    licenseName = licenseName,
                    licenseUrl = entry.attribution.licenseUrl,
                    datasetVersion = entry.datasetVersion,
                    importedFields = setOf(ImportedDictionaryField.READING),
                    importedAtEpochMillis = importedAtEpochMillis,
                    modifiedAfterImport = false,
                )
            } else {
                null
            },
        )
        if (senses.isEmpty()) {
            return DictionaryEntryDraftMappingResult.ReferenceOnly(
                manualDraft = draft,
                source = source,
                transientEntry = entry,
            )
        }

        return DictionaryEntryDraftMappingResult.Ready(
            DictionaryEditorSeed(
                draft = draft,
                source = source,
                transientEntry = entry,
                copiedProviderFields = copiedFields.toSet(),
            ),
        )
    }

    private fun emptySense() = VocabularySenseDraft(
        meaning = "",
        partOfSpeech = "",
        examples = emptyList(),
    )

    private fun DictionaryContentField.toImportedField(): ImportedDictionaryField = when (this) {
        DictionaryContentField.MONOLINGUAL_DEFINITION,
        DictionaryContentField.TRANSLATION,
        -> ImportedDictionaryField.MEANING
        DictionaryContentField.PART_OF_SPEECH -> ImportedDictionaryField.PART_OF_SPEECH
        DictionaryContentField.EXAMPLE -> ImportedDictionaryField.EXAMPLES
        DictionaryContentField.READING -> ImportedDictionaryField.READING
        else -> error("Field $this is not stored at vocabulary sense level")
    }
}
