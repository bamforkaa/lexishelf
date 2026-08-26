package com.example.localvocabulary.vocabulary.data

import com.example.localvocabulary.core.common.StableIdGenerator
import com.example.localvocabulary.core.database.dao.WordbookDao
import com.example.localvocabulary.core.database.entity.WordbookEntity
import com.example.localvocabulary.vocabulary.domain.SaveWordbookResult
import com.example.localvocabulary.vocabulary.domain.VocabularyWordbook
import com.example.localvocabulary.vocabulary.domain.VocabularyWordbookSummary
import com.example.localvocabulary.vocabulary.domain.WordbookRepository
import com.example.localvocabulary.vocabulary.domain.normalizeWordbookName
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomWordbookRepository @Inject constructor(
    private val wordbookDao: WordbookDao,
    private val stableIdGenerator: StableIdGenerator,
) : WordbookRepository {
    override fun observeWordbooks(): Flow<List<VocabularyWordbook>> =
        wordbookDao.observeWordbooks().map { rows ->
            rows.map { VocabularyWordbook(it.id, it.backupId, it.name) }
        }

    override fun observeWordbookSummaries(): Flow<List<VocabularyWordbookSummary>> =
        wordbookDao.observeWordbooksWithEntryCounts().map { rows ->
            rows.map { row ->
                VocabularyWordbookSummary(
                    wordbook = VocabularyWordbook(
                        row.wordbook.id,
                        row.wordbook.backupId,
                        row.wordbook.name,
                    ),
                    entryCount = row.entryCount,
                )
            }
        }

    override suspend fun save(id: Long?, name: String): SaveWordbookResult {
        val value = normalizeWordbookName(name) ?: return SaveWordbookResult.BlankName
        val conflict = wordbookDao.findByNormalizedName(value.identity)
        if (conflict != null && conflict.id != id) {
            return SaveWordbookResult.NameConflict(conflict.id)
        }
        if (id == null) {
            return SaveWordbookResult.Saved(
                wordbookDao.insert(
                    WordbookEntity(
                        backupId = stableIdGenerator.newId(),
                        name = value.displayName,
                        normalizedName = value.identity,
                    ),
                ),
            )
        }
        val existing = wordbookDao.findById(id) ?: return SaveWordbookResult.NotFound
        wordbookDao.update(
            existing.copy(name = value.displayName, normalizedName = value.identity),
        )
        return SaveWordbookResult.Saved(id)
    }

    override suspend fun delete(id: Long) {
        wordbookDao.delete(id)
    }

    override fun observeEntryIds(wordbookId: Long): Flow<Set<Long>> =
        wordbookDao.observeEntryIds(wordbookId).map { it.toSet() }

    override suspend fun addEntries(wordbookId: Long, entryIds: Set<Long>): Int =
        if (entryIds.isEmpty()) 0 else wordbookDao.addEntries(wordbookId, entryIds)

    override suspend fun removeEntries(wordbookId: Long, entryIds: Set<Long>): Int =
        wordbookDao.removeEntries(wordbookId, entryIds)
}
