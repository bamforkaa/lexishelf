package com.example.localvocabulary.vocabulary.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf

interface VocabularyRepository {
    fun observeEntries(query: String = "", tagId: Long? = null): Flow<List<VocabularyEntry>>
    fun observeEntries(
        query: String,
        tagId: Long?,
        wordbookId: Long?,
        languageTag: String?,
    ): Flow<List<VocabularyEntry>> = observeEntries(query, tagId)
    fun observeLanguages(): Flow<List<String>> = flowOf(emptyList())
    fun observeEntry(id: Long): Flow<VocabularyEntry?>
    suspend fun findPracticeItems(
        filter: VocabularyPracticeFilter,
    ): List<VocabularyPracticeItem> = emptyList()
    suspend fun findDuplicateCandidates(
        headword: String,
        languageTag: String,
        excludingEntryId: Long? = null,
    ): List<VocabularyEntry> = emptyList()
    suspend fun save(draft: ValidatedVocabularyDraft): Long
    suspend fun delete(id: Long)
}

interface WordbookRepository {
    fun observeWordbooks(): Flow<List<VocabularyWordbook>>
    fun observeWordbookSummaries(): Flow<List<VocabularyWordbookSummary>> =
        observeWordbooks().map { wordbooks ->
            wordbooks.map { VocabularyWordbookSummary(it, entryCount = 0) }
        }
    suspend fun save(id: Long?, name: String): SaveWordbookResult
    suspend fun delete(id: Long)
    fun observeEntryIds(wordbookId: Long): Flow<Set<Long>> = flowOf(emptySet())
    suspend fun addEntries(wordbookId: Long, entryIds: Set<Long>): Int = 0
    suspend fun removeEntries(wordbookId: Long, entryIds: Set<Long>): Int = 0
}

sealed interface SaveWordbookResult {
    data class Saved(val id: Long) : SaveWordbookResult
    data object BlankName : SaveWordbookResult
    data class NameConflict(val existingId: Long) : SaveWordbookResult
    data object NotFound : SaveWordbookResult
}

interface TagRepository {
    fun observeTags(): Flow<List<VocabularyTag>>
    fun observeTagSummaries(): Flow<List<VocabularyTagSummary>> = observeTags().map { tags ->
        tags.map { VocabularyTagSummary(it, entryCount = 0) }
    }
    suspend fun save(id: Long?, name: String): SaveTagResult
    suspend fun delete(id: Long)
}

sealed interface SaveTagResult {
    data class Saved(val id: Long) : SaveTagResult
    data object BlankName : SaveTagResult
    data class NameConflict(val existingId: Long) : SaveTagResult
    data object NotFound : SaveTagResult
}
