package com.example.localvocabulary.vocabulary.data

import com.example.localvocabulary.core.common.TimeProvider
import com.example.localvocabulary.core.database.dao.SenseWrite
import com.example.localvocabulary.core.database.dao.VocabularyDao
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity
import com.example.localvocabulary.vocabulary.domain.ValidatedVocabularyDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomVocabularyRepository @Inject constructor(
    private val vocabularyDao: VocabularyDao,
    private val timeProvider: TimeProvider,
) : VocabularyRepository {
    override fun observeEntries(query: String, tagId: Long?): Flow<List<VocabularyEntry>> =
        vocabularyDao.observeEntries(query.escapeForLike(), tagId).map { rows ->
            rows.map { it.toDomain() }
        }

    override fun observeEntry(id: Long): Flow<VocabularyEntry?> =
        vocabularyDao.observeEntry(id).map { it?.toDomain() }

    override suspend fun save(draft: ValidatedVocabularyDraft): Long {
        val now = timeProvider.currentTimeMillis()
        val existing = draft.id?.let { vocabularyDao.findEntryEntity(it) }
        val entity = VocabularyEntryEntity(
            id = draft.id ?: 0,
            headword = draft.headword,
            languageTag = draft.languageTag,
            notes = draft.notes,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            modifiedAtEpochMillis = now,
        )
        return vocabularyDao.saveEntry(
            entry = entity,
            senses = draft.senses.map { sense ->
                SenseWrite(
                    meaning = sense.meaning,
                    partOfSpeech = sense.partOfSpeech,
                    examples = sense.examples,
                )
            },
            tagIds = draft.tagIds,
        )
    }

    override suspend fun delete(id: Long) {
        vocabularyDao.deleteEntry(id)
    }
}

private fun String.escapeForLike(): String = trim()
    .replace("\\", "\\\\")
    .replace("%", "\\%")
    .replace("_", "\\_")
