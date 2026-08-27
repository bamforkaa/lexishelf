package com.example.localvocabulary.vocabulary.domain

import java.util.Locale

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
    val reading: String = "",
    val readingProvenance: DictionaryProvenance? = null,
    val pronunciations: List<VocabularyPronunciation> = emptyList(),
    val wordbooks: List<VocabularyWordbook> = emptyList(),
)

data class VocabularySense(
    val id: Long,
    val meaning: String,
    val partOfSpeech: String,
    val examples: List<ExampleSentence>,
    val provenance: DictionaryProvenance? = null,
    val grammaticalGender: VocabularyGrammaticalGender? = null,
)

enum class PronunciationNotation {
    IPA,
    PHONETIC,
    ROMANIZATION,
    OTHER,
}

data class VocabularyPronunciation(
    val id: Long,
    val stableId: String,
    val notation: PronunciationNotation,
    val value: String,
    val languageTag: String? = null,
    val provenance: DictionaryProvenance? = null,
)

data class VocabularyPronunciationDraft(
    val stableId: String? = null,
    val notation: PronunciationNotation,
    val value: String,
    val languageTag: String? = null,
    val provenance: DictionaryProvenance? = null,
)

enum class GrammaticalGenderCategory {
    MASCULINE,
    FEMININE,
    NEUTER,
    COMMON,
    OTHER,
}

data class VocabularyGrammaticalGender(
    val category: GrammaticalGenderCategory,
    val rawValue: String? = null,
) {
    init {
        require(category == GrammaticalGenderCategory.OTHER || rawValue == null) {
            "Only OTHER grammatical gender retains a raw value"
        }
        require(category != GrammaticalGenderCategory.OTHER || !rawValue.isNullOrBlank()) {
            "OTHER grammatical gender requires a raw value"
        }
    }

    fun displayValue(): String = when (category) {
        GrammaticalGenderCategory.MASCULINE -> "masculine"
        GrammaticalGenderCategory.FEMININE -> "feminine"
        GrammaticalGenderCategory.NEUTER -> "neuter"
        GrammaticalGenderCategory.COMMON -> "common"
        GrammaticalGenderCategory.OTHER -> requireNotNull(rawValue)
    }

    companion object {
        fun parse(value: String): VocabularyGrammaticalGender? {
            val normalized = value.trim()
            if (normalized.isEmpty()) return null
            return when (normalized.lowercase(Locale.ROOT)) {
                "masculine" -> VocabularyGrammaticalGender(GrammaticalGenderCategory.MASCULINE)
                "feminine" -> VocabularyGrammaticalGender(GrammaticalGenderCategory.FEMININE)
                "neuter" -> VocabularyGrammaticalGender(GrammaticalGenderCategory.NEUTER)
                "common", "common-gender" ->
                    VocabularyGrammaticalGender(GrammaticalGenderCategory.COMMON)
                else -> VocabularyGrammaticalGender(
                    category = GrammaticalGenderCategory.OTHER,
                    rawValue = normalized,
                )
            }
        }
    }
}

data class ExampleSentence(
    val id: Long,
    val text: String,
)

data class VocabularyTag(
    val id: Long,
    val backupId: String,
    val name: String,
)

data class VocabularyTagSummary(
    val tag: VocabularyTag,
    val entryCount: Int,
)

data class VocabularyWordbook(
    val id: Long,
    val backupId: String,
    val name: String,
)

data class VocabularyWordbookSummary(
    val wordbook: VocabularyWordbook,
    val entryCount: Int,
)

data class VocabularyEntryDraft(
    val id: Long? = null,
    val headword: String,
    val languageTag: String,
    val senses: List<VocabularySenseDraft>,
    val notes: String,
    val tagIds: Set<Long>,
    val reading: String = "",
    val readingProvenance: DictionaryProvenance? = null,
    val pronunciations: List<VocabularyPronunciationDraft> = emptyList(),
    val wordbookIds: Set<Long> = emptySet(),
)

data class VocabularySenseDraft(
    val meaning: String,
    val partOfSpeech: String,
    val examples: List<String>,
    val provenance: DictionaryProvenance? = null,
    val grammaticalGender: VocabularyGrammaticalGender? = null,
)

enum class ImportedDictionaryField {
    MEANING,
    PART_OF_SPEECH,
    EXAMPLES,
    READING,
    PRONUNCIATION,
    GRAMMATICAL_GENDER,
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
