package com.example.localvocabulary.backup.data

import androidx.room.withTransaction
import com.example.localvocabulary.backup.domain.BACKUP_FORMAT_ID
import com.example.localvocabulary.backup.domain.BackupConflictPolicy
import com.example.localvocabulary.backup.domain.BackupEntryV2
import com.example.localvocabulary.backup.domain.BackupImportPreview
import com.example.localvocabulary.backup.domain.BackupImportResult
import com.example.localvocabulary.backup.domain.BackupSenseV2
import com.example.localvocabulary.backup.domain.BackupTagV1
import com.example.localvocabulary.backup.domain.CURRENT_BACKUP_SCHEMA_VERSION
import com.example.localvocabulary.backup.domain.ValidatedBackup
import com.example.localvocabulary.backup.domain.VocabularyBackupRepository
import com.example.localvocabulary.backup.domain.VocabularyBackupV2
import com.example.localvocabulary.core.common.TimeProvider
import com.example.localvocabulary.core.database.VocabularyDatabase
import com.example.localvocabulary.core.database.dao.SenseWrite
import com.example.localvocabulary.core.database.dao.SenseDictionaryProvenanceWrite
import com.example.localvocabulary.core.database.entity.TagEntity
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity
import com.example.localvocabulary.vocabulary.domain.normalizeTagName
import javax.inject.Inject

class RoomVocabularyBackupRepository @Inject constructor(
    private val database: VocabularyDatabase,
    private val timeProvider: TimeProvider,
) : VocabularyBackupRepository {
    override suspend fun createBackup(): VocabularyBackupV2 = database.withTransaction {
        val tags = database.tagDao().getAll()
            .sortedBy { it.backupId }
            .map { BackupTagV1(stableId = it.backupId, name = it.name) }
        val entries = database.vocabularyDao().getAllEntries()
            .sortedBy { it.entry.backupId }
            .map { relation ->
                BackupEntryV2(
                    stableId = relation.entry.backupId,
                    headword = relation.entry.headword,
                    languageTag = relation.entry.languageTag,
                    senses = relation.senses.sortedBy { it.sense.sortOrder }.map { sense ->
                        BackupSenseV2(
                            meaning = sense.sense.meaning,
                            partOfSpeech = sense.sense.partOfSpeech,
                            examples = sense.examples.sortedBy { it.sortOrder }.map { it.text },
                            provenance = sense.provenance?.let { provenance ->
                                com.example.localvocabulary.vocabulary.domain.DictionaryProvenance(
                                    providerId = provenance.providerId,
                                    sourceEntryId = provenance.sourceEntryId,
                                    sourceSenseId = provenance.sourceSenseId,
                                    sourceName = provenance.sourceName,
                                    sourceUrl = provenance.sourceUrl,
                                    licenseName = provenance.licenseName,
                                    licenseUrl = provenance.licenseUrl,
                                    datasetVersion = provenance.datasetVersion,
                                    importedFields = sense.provenanceFields.mapTo(linkedSetOf()) {
                                        com.example.localvocabulary.vocabulary.domain.ImportedDictionaryField
                                            .valueOf(it.field)
                                    },
                                    importedAtEpochMillis = provenance.importedAtEpochMillis,
                                    modifiedAfterImport = provenance.modifiedAfterImport,
                                ).toBackupV2()
                            },
                        )
                    },
                    notes = relation.entry.notes,
                    tagStableIds = relation.tags.map { it.backupId }.sorted(),
                    createdAtEpochMillis = relation.entry.createdAtEpochMillis,
                    modifiedAtEpochMillis = relation.entry.modifiedAtEpochMillis,
                )
            }
        VocabularyBackupV2(
            format = BACKUP_FORMAT_ID,
            schemaVersion = CURRENT_BACKUP_SCHEMA_VERSION,
            exportedAtEpochMillis = timeProvider.currentTimeMillis(),
            tags = tags,
            entries = entries,
        )
    }

    override suspend fun previewImport(
        backup: ValidatedBackup,
        policy: BackupConflictPolicy,
    ): BackupImportPreview = database.withTransaction {
        val document = backup.document
        val existingIds = database.vocabularyDao().getAllEntries()
            .mapTo(mutableSetOf()) { it.entry.backupId }
        val conflictCount = document.entries.count { it.stableId in existingIds }
        val replace = policy == BackupConflictPolicy.REPLACE_ALL
        BackupImportPreview(
            entryCount = document.entries.size,
            senseCount = document.entries.sumOf { it.senses.size },
            exampleCount = document.entries.sumOf { entry -> entry.senses.sumOf { it.examples.size } },
            tagCount = document.tags.size,
            conflictCount = conflictCount,
            newEntryCount = if (replace) document.entries.size else document.entries.size - conflictCount,
            updatedEntryCount = if (replace) 0 else conflictCount,
            skippedEntryCount = 0,
            existingEntryRemovalCount = if (replace) existingIds.size else 0,
        )
    }

    override suspend fun importBackup(
        backup: ValidatedBackup,
        policy: BackupConflictPolicy,
    ): BackupImportResult = database.withTransaction {
        val document = backup.document
        val replace = policy == BackupConflictPolicy.REPLACE_ALL
        val removedCount = if (replace) database.vocabularyDao().getAllEntries().size else 0
        if (replace) {
            database.vocabularyDao().deleteAllEntries()
            database.tagDao().deleteAll()
        }

        val localTagIds = importTags(document.tags)
        var createdCount = 0
        var updatedCount = 0
        document.entries.forEach { entry ->
            val existing = if (replace) null else {
                database.vocabularyDao().findEntryByBackupId(entry.stableId)
            }
            if (existing == null) createdCount++ else updatedCount++
            database.vocabularyDao().saveEntry(
                entry = VocabularyEntryEntity(
                    id = existing?.id ?: 0,
                    backupId = entry.stableId,
                    headword = entry.headword,
                    languageTag = entry.languageTag,
                    notes = entry.notes,
                    createdAtEpochMillis = entry.createdAtEpochMillis,
                    modifiedAtEpochMillis = entry.modifiedAtEpochMillis,
                ),
                senses = entry.senses.map { sense ->
                    SenseWrite(
                        meaning = sense.meaning,
                        partOfSpeech = sense.partOfSpeech,
                        examples = sense.examples,
                        provenance = sense.provenance?.toDomain()?.let { provenance ->
                            SenseDictionaryProvenanceWrite(
                                providerId = provenance.providerId,
                                sourceEntryId = provenance.sourceEntryId,
                                sourceSenseId = provenance.sourceSenseId,
                                sourceName = provenance.sourceName,
                                sourceUrl = provenance.sourceUrl,
                                licenseName = provenance.licenseName,
                                licenseUrl = provenance.licenseUrl,
                                datasetVersion = provenance.datasetVersion,
                                importedFields = provenance.importedFields.mapTo(linkedSetOf()) {
                                    it.name
                                },
                                importedAtEpochMillis = provenance.importedAtEpochMillis,
                                modifiedAfterImport = provenance.modifiedAfterImport,
                            )
                        },
                    )
                },
                tagIds = entry.tagStableIds.mapTo(mutableSetOf()) { stableId ->
                    checkNotNull(localTagIds[stableId]) { "Validated tag reference is missing" }
                },
            )
        }

        BackupImportResult(
            createdEntryCount = createdCount,
            updatedEntryCount = updatedCount,
            removedEntryCount = removedCount,
        )
    }

    private suspend fun importTags(tags: List<BackupTagV1>): Map<String, Long> = buildMap {
        tags.forEach { tag ->
            val name = checkNotNull(normalizeTagName(tag.name))
            val sameName = database.tagDao().findByNormalizedName(name.identity)
            val sameStableId = database.tagDao().findByBackupId(tag.stableId)
            val localId = when {
                sameName != null -> {
                    if (sameName.backupId == tag.stableId && sameName.name != name.displayName) {
                        check(database.tagDao().update(sameName.copy(name = name.displayName)) == 1)
                    }
                    sameName.id
                }
                sameStableId != null -> {
                    check(
                        database.tagDao().update(
                            sameStableId.copy(
                                name = name.displayName,
                                normalizedName = name.identity,
                            ),
                        ) == 1,
                    )
                    sameStableId.id
                }
                else -> database.tagDao().insert(
                    TagEntity(
                        backupId = tag.stableId,
                        name = name.displayName,
                        normalizedName = name.identity,
                    ),
                )
            }
            put(tag.stableId, localId)
        }
    }
}
