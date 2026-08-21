package com.example.localvocabulary.backup.domain

import kotlinx.serialization.Serializable

const val BACKUP_FORMAT_ID = "local-vocabulary-backup"
const val CURRENT_BACKUP_SCHEMA_VERSION = 1

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

class ValidatedBackup internal constructor(
    internal val document: VocabularyBackupV1,
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
