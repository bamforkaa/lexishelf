package com.example.localvocabulary.backup.domain

import kotlinx.serialization.Serializable

const val BACKUP_FORMAT_ID = "local-vocabulary-backup"
const val CURRENT_BACKUP_SCHEMA_VERSION = 3

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
)

@Serializable
data class BackupSenseV2(
    val meaning: String,
    val partOfSpeech: String,
    val examples: List<String>,
    val provenance: BackupDictionaryProvenanceV2? = null,
)

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
}

class ValidatedBackup internal constructor(
    internal val document: VocabularyBackupV2,
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
