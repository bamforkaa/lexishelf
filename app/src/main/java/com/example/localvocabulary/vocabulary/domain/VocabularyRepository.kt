package com.example.localvocabulary.vocabulary.domain

import kotlinx.coroutines.flow.Flow

interface VocabularyRepository {
    fun observeEntries(query: String = "", tagId: Long? = null): Flow<List<VocabularyEntry>>
    fun observeEntry(id: Long): Flow<VocabularyEntry?>
    suspend fun save(draft: ValidatedVocabularyDraft): Long
    suspend fun delete(id: Long)
}

interface TagRepository {
    fun observeTags(): Flow<List<VocabularyTag>>
    suspend fun save(id: Long?, name: String): SaveTagResult
    suspend fun delete(id: Long)
}

sealed interface SaveTagResult {
    data class Saved(val id: Long) : SaveTagResult
    data object BlankName : SaveTagResult
    data object NameConflict : SaveTagResult
    data object NotFound : SaveTagResult
}
