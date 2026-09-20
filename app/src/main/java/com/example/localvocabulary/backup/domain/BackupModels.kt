package com.example.localvocabulary.backup.domain

import com.example.localvocabulary.vocabulary.domain.ExampleOrigin

import kotlinx.serialization.Serializable

const val BACKUP_FORMAT_ID = "local-vocabulary-backup"
const val CURRENT_BACKUP_SCHEMA_VERSION = 8

@Serializable
data class VocabularyBackupV1(
    val format: String,
    val schemaVersion: Int,
    val exportedAtEpochMillis: Long,
    val tags: List<BackupTagV1>,
    val entries: List<BackupEntryV1>,
)

@Serializable
data class BackupTagV1(
    val stableId: String,
    val name: String,
)

@Serializable
data class BackupEntryV1(
    val stableId: String,
    val headword: String,
    val languageTag: String,
    val senses: List<BackupSenseV1>,
    val notes: String,
    val tagStableIds: List<String>,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
)

@Serializable
data class BackupSenseV1(
    val meaning: String,
    val partOfSpeech: String,
    val examples: List<String>,
)

@Serializable
data class VocabularyBackupV2(
    val format: String,
    val schemaVersion: Int,
    val exportedAtEpochMillis: Long,
    val tags: List<BackupTagV1>,
    val entries: List<BackupEntryV2>,
    val wordbooks: List<BackupWordbookV4> = emptyList(),
)

@Serializable
data class BackupWordbookV4(
    val stableId: String,
    val name: String,
)

@Serializable
data class BackupEntryV2(
    val stableId: String,
    val headword: String,
    val languageTag: String,
    val senses: List<BackupSenseV2>,
    val notes: String,
    val tagStableIds: List<String>,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
    val reading: String = "",
    val readingProvenance: BackupDictionaryProvenanceV2? = null,
    val wordbookStableIds: List<String> = emptyList(),
    val pronunciations: List<BackupPronunciationV5> = emptyList(),
)

@Serializable
data class BackupSenseV2(
    val meaning: String,
    val partOfSpeech: String,
    val examples: List<String>,
    val provenance: BackupDictionaryProvenanceV2? = null,
    val grammaticalGender: BackupGrammaticalGenderV5? = null,
)

@Serializable
data class BackupPronunciationV5(
    val stableId: String,
    val notation: BackupPronunciationNotationV5,
    val value: String,
    val languageTag: String? = null,
    val provenance: BackupDictionaryProvenanceV2? = null,
)

@Serializable
enum class BackupPronunciationNotationV5 {
    IPA,
    PHONETIC,
    ROMANIZATION,
    OTHER,
}

@Serializable
data class BackupGrammaticalGenderV5(
    val category: BackupGrammaticalGenderCategoryV5,
    val rawValue: String? = null,
)

@Serializable
enum class BackupGrammaticalGenderCategoryV5 {
    MASCULINE,
    FEMININE,
    NEUTER,
    COMMON,
    OTHER,
}

@Serializable
data class BackupDictionaryProvenanceV2(
    val providerId: String,
    val sourceEntryId: String? = null,
    val sourceSenseId: String? = null,
    val sourceName: String,
    val sourceUrl: String? = null,
    val licenseName: String,
    val licenseUrl: String? = null,
    val datasetVersion: String? = null,
    val importedFields: List<BackupImportedFieldV2>,
    val importedAtEpochMillis: Long,
    val modifiedAfterImport: Boolean,
)

@Serializable
enum class BackupImportedFieldV2 {
    MEANING,
    PART_OF_SPEECH,
    EXAMPLES,
    READING,
    PRONUNCIATION,
    GRAMMATICAL_GENDER,
}

class ValidatedBackup internal constructor(
    internal val document: VocabularyBackupV8,
    internal val sourceSchemaVersion: Int = CURRENT_BACKUP_SCHEMA_VERSION,
)

enum class BackupConflictPolicy {
    MERGE_BY_STABLE_ID,
    REPLACE_ALL,
}

data class BackupImportPreview(
    val entryCount: Int,
    val senseCount: Int,
    val exampleCount: Int,
    val tagCount: Int,
    val conflictCount: Int,
    val newEntryCount: Int,
    val updatedEntryCount: Int,
    val skippedEntryCount: Int,
    val existingEntryRemovalCount: Int,
    val wordbookCount: Int = 0,
    val legacyChildReplacementCount: Int = 0,
    val reviewStateRemovalCount: Int = 0,
    val reviewEventRemovalCount: Int = 0,
    val reviewStateOverwriteCount: Int = 0,
)

data class BackupImportResult(
    val createdEntryCount: Int,
    val updatedEntryCount: Int,
    val removedEntryCount: Int,
)

sealed interface BackupDecodeResult {
    data class Success(val backup: ValidatedBackup) : BackupDecodeResult
    data class Failure(val error: BackupReadError) : BackupDecodeResult
}

sealed interface BackupReadError {
    data object MalformedJson : BackupReadError
    data object MissingRequiredField : BackupReadError
    data class UnsupportedSchemaVersion(val version: Int) : BackupReadError
    data class InvalidData(val path: String, val reason: String) : BackupReadError
}

@Serializable
data class VocabularyBackupV6(
    val format: String,
    val schemaVersion: Int,
    val exportedAtEpochMillis: Long,
    val tags: List<BackupTagV1>,
    val entries: List<BackupEntryV6>,
    val wordbooks: List<BackupWordbookV4> = emptyList(),
)

@Serializable
data class BackupEntryV6(
    val stableId: String,
    val headword: String,
    val languageTag: String,
    val senses: List<BackupSenseV6>,
    val notes: String,
    val tagStableIds: List<String>,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
    val reading: String = "",
    val readingProvenance: BackupDictionaryProvenanceV2? = null,
    val wordbookStableIds: List<String> = emptyList(),
    val pronunciations: List<BackupPronunciationV5> = emptyList(),
)

@Serializable
data class BackupSenseV6(
    val stableId: String,
    val meaning: String,
    val partOfSpeech: String,
    val examples: List<BackupExampleV6>,
    val provenance: BackupDictionaryProvenanceV2? = null,
    val grammaticalGender: BackupGrammaticalGenderV5? = null,
)

@Serializable
data class BackupExampleV6(
    val stableId: String,
    val text: String,
    val meaning: String = "",
    val origin: ExampleOrigin =
        ExampleOrigin.UNKNOWN,
    val sourceTitle: String? = null,
    val sourceUrl: String? = null,
    val sourceLocator: String? = null,
    val capturedAt: Long? = null,
)
