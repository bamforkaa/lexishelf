package com.example.localvocabulary.backup.domain

interface BackupSerializer {
    fun encode(backup: VocabularyBackupV1): String
    fun decode(json: String): BackupDecodeResult
}

interface VocabularyBackupRepository {
    suspend fun createBackup(): VocabularyBackupV1

    suspend fun previewImport(
        backup: ValidatedBackup,
        policy: BackupConflictPolicy,
    ): BackupImportPreview

    suspend fun importBackup(
        backup: ValidatedBackup,
        policy: BackupConflictPolicy,
    ): BackupImportResult
}

interface BackupFileStore {
    suspend fun read(uri: String): String
    suspend fun write(uri: String, content: String)
}
