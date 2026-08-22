package com.example.localvocabulary.vocabulary.domain

data class VocabularyEntry(
    val id: Long,
    val backupId: String,
    val headword: String,
    val languageTag: String,
    val senses: List<VocabularySense>,
    val notes: String,
    val tags: List<VocabularyTag>,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
)

data class VocabularySense(
    val id: Long,
    val meaning: String,
    val partOfSpeech: String,
    val examples: List<ExampleSentence>,
    val provenance: DictionaryProvenance? = null,
)

data class ExampleSentence(
    val id: Long,
    val text: String,
)

data class VocabularyTag(
    val id: Long,
    val backupId: String,
    val name: String,
)

data class VocabularyEntryDraft(
    val id: Long? = null,
    val headword: String,
    val languageTag: String,
    val senses: List<VocabularySenseDraft>,
    val notes: String,
    val tagIds: Set<Long>,
)

data class VocabularySenseDraft(
    val meaning: String,
    val partOfSpeech: String,
    val examples: List<String>,
    val provenance: DictionaryProvenance? = null,
)

enum class ImportedDictionaryField {
    MEANING,
    PART_OF_SPEECH,
    EXAMPLES,
}

data class DictionaryProvenance(
    val providerId: String,
    val sourceEntryId: String?,
    val sourceSenseId: String?,
    val sourceName: String,
    val sourceUrl: String?,
    val licenseName: String,
    val licenseUrl: String?,
    val datasetVersion: String?,
    val importedFields: Set<ImportedDictionaryField>,
    val importedAtEpochMillis: Long,
    val modifiedAfterImport: Boolean,
) {
    init {
        require(PROVIDER_ID.matches(providerId)) { "Invalid dictionary provider ID" }
        require(sourceEntryId == null || sourceEntryId.isNotBlank()) {
            "Source entry ID must not be blank"
        }
        require(sourceSenseId == null || sourceSenseId.isNotBlank()) {
            "Source sense ID must not be blank"
        }
        require(sourceName.isNotBlank()) { "Source name must not be blank" }
        require(sourceUrl == null || sourceUrl.isNotBlank()) { "Source URL must not be blank" }
        require(licenseName.isNotBlank()) { "License name must not be blank" }
        require(licenseUrl == null || licenseUrl.isNotBlank()) { "License URL must not be blank" }
        require(datasetVersion == null || datasetVersion.isNotBlank()) {
            "Dataset version must not be blank"
        }
        require(importedFields.isNotEmpty()) { "Imported fields must not be empty" }
        require(importedAtEpochMillis >= 0) { "Import timestamp must not be negative" }
    }

    fun markModified(): DictionaryProvenance = if (modifiedAfterImport) {
        this
    } else {
        copy(modifiedAfterImport = true)
    }

    fun hasSameSourceAs(other: DictionaryProvenance): Boolean =
        providerId == other.providerId &&
            sourceEntryId != null &&
            sourceEntryId == other.sourceEntryId &&
            sourceSenseId == other.sourceSenseId

    private companion object {
        val PROVIDER_ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    }
}
