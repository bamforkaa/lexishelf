package com.example.localvocabulary.backup.data

import com.example.localvocabulary.backup.domain.BackupExampleV6

import com.example.localvocabulary.core.database.dao.ExampleWrite

import com.example.localvocabulary.vocabulary.domain.ExampleOrigin

import androidx.room.withTransaction
import com.example.localvocabulary.review.data.toDomain
import com.example.localvocabulary.review.data.toEntity
import com.example.localvocabulary.backup.domain.BACKUP_FORMAT_ID
import com.example.localvocabulary.backup.domain.BackupConflictPolicy
import com.example.localvocabulary.backup.domain.BackupEntryV6
import com.example.localvocabulary.backup.domain.BackupGrammaticalGenderCategoryV5
import com.example.localvocabulary.backup.domain.BackupGrammaticalGenderV5
import com.example.localvocabulary.backup.domain.BackupPronunciationNotationV5
import com.example.localvocabulary.backup.domain.BackupPronunciationV5
import com.example.localvocabulary.backup.domain.BackupImportPreview
import com.example.localvocabulary.backup.domain.BackupImportResult
import com.example.localvocabulary.backup.domain.BackupSenseV6
import com.example.localvocabulary.backup.domain.BackupTagV1
import com.example.localvocabulary.backup.domain.BackupWordbookV4
import com.example.localvocabulary.backup.domain.CURRENT_BACKUP_SCHEMA_VERSION
import com.example.localvocabulary.backup.domain.ValidatedBackup
import com.example.localvocabulary.backup.domain.VocabularyBackupRepository
import com.example.localvocabulary.backup.domain.VocabularyBackupV8
import com.example.localvocabulary.core.common.TimeProvider
import com.example.localvocabulary.core.database.VocabularyDatabase
import com.example.localvocabulary.core.database.dao.SenseWrite
import com.example.localvocabulary.core.database.dao.SenseDictionaryProvenanceWrite
import com.example.localvocabulary.core.database.dao.PronunciationWrite
import com.example.localvocabulary.core.database.entity.TagEntity
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity
import com.example.localvocabulary.core.database.entity.WordbookEntity
import com.example.localvocabulary.vocabulary.domain.normalizeTagName
import com.example.localvocabulary.vocabulary.domain.normalizeWordbookName
import javax.inject.Inject

class RoomVocabularyBackupRepository @Inject constructor(
    private val database: VocabularyDatabase,
    private val timeProvider: TimeProvider,
) : VocabularyBackupRepository {
    override suspend fun createBackup(): VocabularyBackupV8 = database.withTransaction {
        val tags = database.tagDao().getAll()
            .sortedBy { it.backupId }
            .map { BackupTagV1(stableId = it.backupId, name = it.name) }
        val wordbooks = database.wordbookDao().getAll()
            .sortedBy { it.backupId }
            .map { BackupWordbookV4(stableId = it.backupId, name = it.name) }
        val entries = database.vocabularyDao().getAllEntries()
            .sortedBy { it.entry.backupId }
            .map { relation ->
                BackupEntryV6(
                    stableId = relation.entry.backupId,
                    headword = relation.entry.headword,
                    languageTag = relation.entry.languageTag,
                    senses = relation.senses.sortedBy { it.sense.sortOrder }.map { sense ->
                        BackupSenseV6(
                            stableId = sense.sense.stableId,
                            meaning = sense.sense.meaning,
                            partOfSpeech = sense.sense.partOfSpeech,
                            examples = sense.examples.sortedBy { it.sortOrder }.map {
                                BackupExampleV6(
                                    stableId = it.stableId, text = it.text, meaning = it.meaning,
                                    origin = ExampleOrigin.valueOf(it.origin),
                                    sourceTitle = it.sourceTitle, sourceUrl = it.sourceUrl,
                                    sourceLocator = it.sourceLocator, capturedAt = it.capturedAt,
                                )
                            },
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
                            grammaticalGender = sense.sense.grammaticalGender?.let { category ->
                                BackupGrammaticalGenderV5(
                                    category = BackupGrammaticalGenderCategoryV5.valueOf(category),
                                    rawValue = sense.sense.grammaticalGenderRaw,
                                )
                            },
                        )
                    },
                    notes = relation.entry.notes,
                    tagStableIds = relation.tags.map { it.backupId }.sorted(),
                    createdAtEpochMillis = relation.entry.createdAtEpochMillis,
                    modifiedAtEpochMillis = relation.entry.modifiedAtEpochMillis,
                    reading = relation.entry.reading,
                    readingProvenance = relation.entryProvenance
                        .singleOrNull { it.field == "READING" }
                        ?.let { provenance ->
                            com.example.localvocabulary.vocabulary.domain.DictionaryProvenance(
                                providerId = provenance.providerId,
                                sourceEntryId = provenance.sourceEntryId,
                                sourceSenseId = provenance.sourceSenseId,
                                sourceName = provenance.sourceName,
                                sourceUrl = provenance.sourceUrl,
                                licenseName = provenance.licenseName,
                                licenseUrl = provenance.licenseUrl,
                                datasetVersion = provenance.datasetVersion,
                                importedFields = setOf(
                                    com.example.localvocabulary.vocabulary.domain.ImportedDictionaryField.READING,
                                ),
                                importedAtEpochMillis = provenance.importedAtEpochMillis,
                                modifiedAfterImport = provenance.modifiedAfterImport,
                            ).toBackupV2()
                        },
                    wordbookStableIds = relation.wordbooks.map { it.backupId }.sorted(),
                    pronunciations = relation.pronunciations
                        .sortedBy { it.pronunciation.sortOrder }
                        .map { pronunciationRelation ->
                            val pronunciation = pronunciationRelation.pronunciation
                            BackupPronunciationV5(
                                stableId = pronunciation.stableId,
                                notation = BackupPronunciationNotationV5.valueOf(
                                    pronunciation.notation,
                                ),
                                value = pronunciation.value,
                                languageTag = pronunciation.languageTag,
                                provenance = pronunciationRelation.provenance?.let { provenance ->
                                    com.example.localvocabulary.vocabulary.domain.DictionaryProvenance(
                                        providerId = provenance.providerId,
                                        sourceEntryId = provenance.sourceEntryId,
                                        sourceSenseId = provenance.sourceSenseId,
                                        sourceName = provenance.sourceName,
                                        sourceUrl = provenance.sourceUrl,
                                        licenseName = provenance.licenseName,
                                        licenseUrl = provenance.licenseUrl,
                                        datasetVersion = provenance.datasetVersion,
                                        importedFields = setOf(
                                            com.example.localvocabulary.vocabulary.domain
                                                .ImportedDictionaryField.PRONUNCIATION,
                                        ),
                                        importedAtEpochMillis = provenance.importedAtEpochMillis,
                                        modifiedAfterImport = provenance.modifiedAfterImport,
                                    ).toBackupV2()
                                },
                            )
                        },
                )
            }
        VocabularyBackupV8(
            format = BACKUP_FORMAT_ID,
            schemaVersion = CURRENT_BACKUP_SCHEMA_VERSION,
            exportedAtEpochMillis = timeProvider.currentTimeMillis(),
            tags = tags,
            entries = entries,
            wordbooks = wordbooks,
            reviewStates = database.reviewDao().states().map { it.toDomain().toBackup() },
            reviewEvents = database.reviewDao().events().map { it.toDomain().toBackup() },
        )
    }

    override suspend fun previewImport(
        backup: ValidatedBackup,
        policy: BackupConflictPolicy,
    ): BackupImportPreview = database.withTransaction {
        val document = if (policy == BackupConflictPolicy.MERGE_BY_STABLE_ID && backup.sourceSchemaVersion == 7) {
            alignLegacyReviewIds(backup.document)
        } else backup.document
        val existingIds = database.vocabularyDao().getAllEntries()
            .mapTo(mutableSetOf()) { it.entry.backupId }
        val conflictCount = document.entries.count { it.stableId in existingIds }
        val replace = policy == BackupConflictPolicy.REPLACE_ALL
        val incomingEntries = document.entries.associateBy { it.stableId }
        val removedSenseIds = database.vocabularyDao().getAllEntries().flatMap { entry ->
            val incoming = incomingEntries[entry.entry.backupId]
            val retained = incoming?.senses?.mapTo(hashSetOf()) { it.stableId }.orEmpty()
            entry.senses.filter { replace || (incoming != null && it.sense.stableId !in retained) }
                .map { it.sense.stableId }
        }.toSet()
        val removedStates = database.reviewDao().states().filter { it.senseStableId in removedSenseIds }
        val removedStateIds = removedStates.mapTo(hashSetOf()) { it.stableId }
        BackupImportPreview(
            reviewStateRemovalCount = removedStates.size,
            reviewEventRemovalCount = database.reviewDao().events().count { it.reviewStateId in removedStateIds },
            reviewStateOverwriteCount = if (replace) 0 else document.reviewStates.count {
                val existing = database.reviewDao().state(it.stableId)
                existing != null && existing != it.toDomain().toEntity()
            },
            entryCount = document.entries.size,
            senseCount = document.entries.sumOf { it.senses.size },
            exampleCount = document.entries.sumOf { entry -> entry.senses.sumOf { it.examples.size } },
            tagCount = document.tags.size,
            conflictCount = conflictCount,
            newEntryCount = if (replace) document.entries.size else document.entries.size - conflictCount,
            updatedEntryCount = if (replace) 0 else conflictCount,
            skippedEntryCount = 0,
            existingEntryRemovalCount = if (replace) existingIds.size else 0,
            wordbookCount = document.wordbooks.size,
            legacyChildReplacementCount = if (backup.sourceSchemaVersion < 6) {
                database.vocabularyDao().getAllEntries().filter {
                    replace || it.entry.backupId in document.entries.map { entry -> entry.stableId }
                }.sumOf { it.senses.size }
            } else 0,
        )
    }

    override suspend fun importBackup(
        backup: ValidatedBackup,
        policy: BackupConflictPolicy,
    ): BackupImportResult = database.withTransaction {
        val document = if (policy == BackupConflictPolicy.MERGE_BY_STABLE_ID && backup.sourceSchemaVersion == 7) {
            alignLegacyReviewIds(backup.document)
        } else backup.document
        val replace = policy == BackupConflictPolicy.REPLACE_ALL
        val removedCount = if (replace) database.vocabularyDao().getAllEntries().size else 0
        if (!replace) validateReviewMerge(document)
        if (replace) {
            database.vocabularyDao().deleteAllEntries()
            database.tagDao().deleteAll()
            database.wordbookDao().deleteAll()
        }

        val localTagIds = importTags(document.tags)
        val localWordbookIds = importWordbooks(document.wordbooks)
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
                    reading = entry.reading,
                ),
                senses = entry.senses.map { sense ->
                    SenseWrite(
                        meaning = sense.meaning,
                        partOfSpeech = sense.partOfSpeech,
                        stableId = sense.stableId,
                        examples = sense.examples.map {
                            ExampleWrite(
                                text = it.text, stableId = it.stableId, meaning = it.meaning,
                                origin = it.origin.name, sourceTitle = it.sourceTitle,
                                sourceUrl = it.sourceUrl, sourceLocator = it.sourceLocator,
                                capturedAt = it.capturedAt,
                            )
                        },
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
                        grammaticalGender = sense.grammaticalGender?.category?.name,
                        grammaticalGenderRaw = sense.grammaticalGender?.rawValue,
                    )
                },
                tagIds = entry.tagStableIds.mapTo(mutableSetOf()) { stableId ->
                    checkNotNull(localTagIds[stableId]) { "Validated tag reference is missing" }
                },
                readingProvenance = entry.readingProvenance?.toDomain()?.let { provenance ->
                    SenseDictionaryProvenanceWrite(
                        providerId = provenance.providerId,
                        sourceEntryId = provenance.sourceEntryId,
                        sourceSenseId = provenance.sourceSenseId,
                        sourceName = provenance.sourceName,
                        sourceUrl = provenance.sourceUrl,
                        licenseName = provenance.licenseName,
                        licenseUrl = provenance.licenseUrl,
                        datasetVersion = provenance.datasetVersion,
                        importedFields = setOf("READING"),
                        importedAtEpochMillis = provenance.importedAtEpochMillis,
                        modifiedAfterImport = provenance.modifiedAfterImport,
                    )
                },
                wordbookIds = entry.wordbookStableIds.mapTo(mutableSetOf()) { stableId ->
                    checkNotNull(localWordbookIds[stableId]) {
                        "Validated wordbook reference is missing"
                    }
                },
                pronunciations = entry.pronunciations.map { pronunciation ->
                    PronunciationWrite(
                        stableId = pronunciation.stableId,
                        notation = pronunciation.notation.name,
                        value = pronunciation.value,
                        languageTag = pronunciation.languageTag,
                        provenance = pronunciation.provenance?.toDomain()?.let { provenance ->
                            SenseDictionaryProvenanceWrite(
                                providerId = provenance.providerId,
                                sourceEntryId = provenance.sourceEntryId,
                                sourceSenseId = provenance.sourceSenseId,
                                sourceName = provenance.sourceName,
                                sourceUrl = provenance.sourceUrl,
                                licenseName = provenance.licenseName,
                                licenseUrl = provenance.licenseUrl,
                                datasetVersion = provenance.datasetVersion,
                                importedFields = setOf("PRONUNCIATION"),
                                importedAtEpochMillis = provenance.importedAtEpochMillis,
                                modifiedAfterImport = provenance.modifiedAfterImport,
                            )
                        },
                    )
                },
            )
        }

        importReviews(document)
        BackupImportResult(
            createdEntryCount = createdCount,
            updatedEntryCount = updatedCount,
            removedEntryCount = removedCount,
        )
    }

    private suspend fun alignLegacyReviewIds(document: VocabularyBackupV8): VocabularyBackupV8 {
        val dao = database.reviewDao()
        val idMap = document.reviewStates.associate { state ->
            val existingId = dao.state(state.stableId)
            check(existingId == null || existingId.senseStableId == state.senseStableId) {
                "복습 상태 ID가 다른 의미에서 사용 중입니다."
            }
            state.stableId to (dao.stateForSense(state.senseStableId)?.stableId ?: state.stableId)
        }
        val existingEvents = dao.events().groupBy { it.reviewStateId }
        return document.copy(
            reviewStates = document.reviewStates.map { state ->
                val id = idMap.getValue(state.stableId)
                val lastEventGeneration = existingEvents[id]?.maxOfOrNull { it.generation }
                val afterHistory = lastEventGeneration?.let {
                    if (it == Int.MAX_VALUE) it else it + 1
                } ?: 0
                state.copy(stableId = id, generation = maxOf(
                    state.generation, dao.state(id)?.generation ?: 0, afterHistory,
                ))
            },
            reviewEvents = document.reviewEvents.map { it.copy(reviewStateId = idMap.getValue(it.reviewStateId)) },
        )
    }

    private suspend fun validateReviewMerge(document: VocabularyBackupV8) {
        val dao = database.reviewDao()
        document.reviewStates.forEach { state ->
            val existing = dao.state(state.stableId)
            check(existing == null || existing.senseStableId == state.senseStableId) {
                "복습 상태 ID가 다른 의미에서 사용 중입니다."
            }
            val sameSense = dao.stateForSense(state.senseStableId)
            check(sameSense == null || sameSense.stableId == state.stableId) {
                "같은 의미에 서로 다른 복습 ID가 있습니다. 현재 백업을 보관한 후 전체 교체를 검토하세요."
            }
        }
        document.reviewEvents.forEach { event ->
            val existing = dao.event(event.stableId)
            check(existing == null || existing == event.toDomain().toEntity()) {
                "같은 복습 event ID의 내용이 다릅니다. 이력을 덮어쓸 수 없습니다."
            }
        }
        val existingEvents = dao.events()
        val legacyOwners = existingEvents.filter { it.legacyReviewStateId != null }.associate {
            it.legacyReviewStateId to (it.reviewStateId to it.promptDirection)
        }.toMutableMap()
        document.reviewEvents.forEach { event ->
            event.legacyReviewStateId?.let { legacyId ->
                val owner = event.reviewStateId to event.promptDirection?.name
                val previous = legacyOwners.put(legacyId, owner)
                check(previous == null || previous == owner) { "이전 복습 상태의 참조 또는 방향이 충돌합니다." }
            }
        }
        val existingRetries = existingEvents.filter { it.retryOfEventId != null }.associateBy { it.retryOfEventId }
        document.reviewEvents.forEach { event ->
            if (event.retryOfEventId != null) {
                check(existingRetries[event.retryOfEventId]?.stableId.let { it == null || it == event.stableId }) {
                    "동일한 정규 복습에 서로 다른 재시도 기록이 있습니다."
                }
            }
        }
    }

    private suspend fun importReviews(document: VocabularyBackupV8) {
        val dao = database.reviewDao()
        document.reviewStates.forEach { state ->
            val row = state.toDomain().toEntity()
            if (dao.state(state.stableId) == null) dao.insertState(row) else dao.updateState(row)
        }
        document.reviewEvents.forEach { event ->
            if (dao.event(event.stableId) == null) dao.insertEvent(event.toDomain().toEntity())
        }
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

    private suspend fun importWordbooks(
        wordbooks: List<BackupWordbookV4>,
    ): Map<String, Long> = buildMap {
        wordbooks.forEach { wordbook ->
            val name = checkNotNull(normalizeWordbookName(wordbook.name))
            val sameName = database.wordbookDao().findByNormalizedName(name.identity)
            val sameStableId = database.wordbookDao().findByBackupId(wordbook.stableId)
            val localId = when {
                sameName != null -> {
                    if (
                        sameName.backupId == wordbook.stableId &&
                        sameName.name != name.displayName
                    ) {
                        check(
                            database.wordbookDao().update(
                                sameName.copy(name = name.displayName),
                            ) == 1,
                        )
                    }
                    sameName.id
                }
                sameStableId != null -> {
                    check(
                        database.wordbookDao().update(
                            sameStableId.copy(
                                name = name.displayName,
                                normalizedName = name.identity,
                            ),
                        ) == 1,
                    )
                    sameStableId.id
                }
                else -> database.wordbookDao().insert(
                    WordbookEntity(
                        backupId = wordbook.stableId,
                        name = name.displayName,
                        normalizedName = name.identity,
                    ),
                )
            }
            put(wordbook.stableId, localId)
        }
    }
}
