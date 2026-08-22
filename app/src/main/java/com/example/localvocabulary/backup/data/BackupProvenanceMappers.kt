package com.example.localvocabulary.backup.data

import com.example.localvocabulary.backup.domain.BackupDictionaryProvenanceV2
import com.example.localvocabulary.backup.domain.BackupImportedFieldV2
import com.example.localvocabulary.vocabulary.domain.DictionaryProvenance
import com.example.localvocabulary.vocabulary.domain.ImportedDictionaryField

internal fun DictionaryProvenance.toBackupV2(): BackupDictionaryProvenanceV2 =
    BackupDictionaryProvenanceV2(
        providerId = providerId,
        sourceEntryId = sourceEntryId,
        sourceSenseId = sourceSenseId,
        sourceName = sourceName,
        sourceUrl = sourceUrl,
        licenseName = licenseName,
        licenseUrl = licenseUrl,
        datasetVersion = datasetVersion,
        importedFields = importedFields.sortedBy { it.name }.map {
            BackupImportedFieldV2.valueOf(it.name)
        },
        importedAtEpochMillis = importedAtEpochMillis,
        modifiedAfterImport = modifiedAfterImport,
    )

internal fun BackupDictionaryProvenanceV2.toDomain(): DictionaryProvenance =
    DictionaryProvenance(
        providerId = providerId,
        sourceEntryId = sourceEntryId,
        sourceSenseId = sourceSenseId,
        sourceName = sourceName,
        sourceUrl = sourceUrl,
        licenseName = licenseName,
        licenseUrl = licenseUrl,
        datasetVersion = datasetVersion,
        importedFields = importedFields.mapTo(linkedSetOf()) {
            ImportedDictionaryField.valueOf(it.name)
        },
        importedAtEpochMillis = importedAtEpochMillis,
        modifiedAfterImport = modifiedAfterImport,
    )
