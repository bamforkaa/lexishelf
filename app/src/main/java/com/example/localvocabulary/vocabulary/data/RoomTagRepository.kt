package com.example.localvocabulary.vocabulary.data

import com.example.localvocabulary.core.database.dao.TagDao
import com.example.localvocabulary.core.database.entity.TagEntity
import com.example.localvocabulary.vocabulary.domain.SaveTagResult
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTagRepository @Inject constructor(
    private val tagDao: TagDao,
) : TagRepository {
    override fun observeTags(): Flow<List<VocabularyTag>> = tagDao.observeTags().map { tags ->
        tags.map { VocabularyTag(it.id, it.name) }
    }

    override suspend fun save(id: Long?, name: String): SaveTagResult {
        val displayName = name.trim().replace(WHITESPACE, " ")
        if (displayName.isEmpty()) return SaveTagResult.BlankName

        val normalizedName = displayName.lowercase(Locale.ROOT)
        val conflict = tagDao.findByNormalizedName(normalizedName)
        if (conflict != null && conflict.id != id) return SaveTagResult.NameConflict

        if (id == null) {
            return SaveTagResult.Saved(
                tagDao.insert(TagEntity(name = displayName, normalizedName = normalizedName)),
            )
        }

        if (tagDao.findById(id) == null) return SaveTagResult.NotFound
        tagDao.update(TagEntity(id = id, name = displayName, normalizedName = normalizedName))
        return SaveTagResult.Saved(id)
    }

    override suspend fun delete(id: Long) {
        tagDao.delete(id)
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
