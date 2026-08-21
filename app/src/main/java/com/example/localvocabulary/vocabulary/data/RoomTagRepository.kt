package com.example.localvocabulary.vocabulary.data

import com.example.localvocabulary.core.common.StableIdGenerator
import com.example.localvocabulary.core.database.dao.TagDao
import com.example.localvocabulary.core.database.entity.TagEntity
import com.example.localvocabulary.vocabulary.domain.SaveTagResult
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import com.example.localvocabulary.vocabulary.domain.normalizeTagName
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTagRepository @Inject constructor(
    private val tagDao: TagDao,
    private val stableIdGenerator: StableIdGenerator,
) : TagRepository {
    override fun observeTags(): Flow<List<VocabularyTag>> = tagDao.observeTags().map { tags ->
        tags.map { VocabularyTag(it.id, it.backupId, it.name) }
    }

    override suspend fun save(id: Long?, name: String): SaveTagResult {
        val nameValue = normalizeTagName(name) ?: return SaveTagResult.BlankName
        val conflict = tagDao.findByNormalizedName(nameValue.identity)
        if (conflict != null && conflict.id != id) return SaveTagResult.NameConflict

        if (id == null) {
            return SaveTagResult.Saved(
                tagDao.insert(
                    TagEntity(
                        backupId = stableIdGenerator.newId(),
                        name = nameValue.displayName,
                        normalizedName = nameValue.identity,
                    ),
                ),
            )
        }

        val existing = tagDao.findById(id) ?: return SaveTagResult.NotFound
        tagDao.update(
            existing.copy(name = nameValue.displayName, normalizedName = nameValue.identity),
        )
        return SaveTagResult.Saved(id)
    }

    override suspend fun delete(id: Long) {
        tagDao.delete(id)
    }
}
