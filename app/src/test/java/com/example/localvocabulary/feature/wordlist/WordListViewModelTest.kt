package com.example.localvocabulary.feature.wordlist

import androidx.lifecycle.SavedStateHandle
import com.example.localvocabulary.vocabulary.domain.SaveTagResult
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.ValidatedVocabularyDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import com.example.localvocabulary.vocabulary.domain.VocabularyWordbook
import com.example.localvocabulary.vocabulary.domain.WordbookRepository
import com.example.localvocabulary.vocabulary.domain.SaveWordbookResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WordListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `query and tag actions update repository-backed state`() = runTest {
        val repository = RecordingVocabularyRepository()
        val viewModel = WordListViewModel(
            SavedStateHandle(),
            repository,
            FakeTagRepository(listOf(VocabularyTag(4, "tag-4", "Selected"))),
        )

        viewModel.onAction(WordListAction.QueryChanged("term"))
        viewModel.onAction(WordListAction.TagSelected(4))
        viewModel.uiState.collectInBackground(backgroundScope)
        advanceUntilIdle()

        assertEquals("term", viewModel.uiState.value.query)
        assertEquals(4L, viewModel.uiState.value.selectedTagId)
        assertEquals("term" to 4L, repository.lastFilter)
    }

    @Test
    fun `removing the selected tag clears the stale filter`() = runTest {
        val repository = RecordingVocabularyRepository()
        val tags = FakeTagRepository(listOf(VocabularyTag(4, "tag-4", "Selected")))
        val viewModel = WordListViewModel(SavedStateHandle(), repository, tags)
        viewModel.uiState.collectInBackground(backgroundScope)
        advanceUntilIdle()

        viewModel.onAction(WordListAction.TagSelected(4))
        advanceUntilIdle()
        tags.replaceWith(emptyList())
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedTagId)
        assertEquals("" to null, repository.lastFilter)
    }

    @Test
    fun `tag collection route reuses query and tag filtering`() = runTest {
        val repository = RecordingVocabularyRepository()
        val viewModel = WordListViewModel(
            SavedStateHandle(mapOf("tagId" to 4L)),
            repository,
            FakeTagRepository(listOf(VocabularyTag(4, "tag-4", "JLPT N2"))),
        )
        viewModel.uiState.collectInBackground(backgroundScope)
        advanceUntilIdle()

        assertEquals(4L, viewModel.uiState.value.selectedTagId)
        assertEquals("JLPT N2", viewModel.uiState.value.selectedTagName)
        assertEquals(true, viewModel.uiState.value.isCollectionView)
        assertEquals("" to 4L, repository.lastFilter)

        viewModel.onAction(WordListAction.QueryChanged("食べる"))
        advanceUntilIdle()

        assertEquals("食べる" to 4L, repository.lastFilter)
    }

    @Test
    fun `language filtering uses entry language independently of tags`() = runTest {
        val repository = RecordingVocabularyRepository()
        val viewModel = WordListViewModel(
            SavedStateHandle(),
            repository,
            FakeTagRepository(listOf(VocabularyTag(4, "tag-en", "en"))),
        )
        viewModel.uiState.collectInBackground(backgroundScope)
        advanceUntilIdle()

        viewModel.onAction(
            WordListAction.FilterCategorySelected(VocabularyFilterCategory.LANGUAGE),
        )
        viewModel.onAction(WordListAction.LanguageSelected("ja"))
        advanceUntilIdle()

        assertEquals("ja", repository.lastLanguageTag)
        assertNull(repository.lastTagId)
    }

    @Test
    fun `wordbook collection route reuses vocabulary filtering`() = runTest {
        val repository = RecordingVocabularyRepository()
        val viewModel = WordListViewModel(
            SavedStateHandle(mapOf("wordbookId" to 8L)),
            repository,
            FakeTagRepository(),
            FakeWordbookRepository(listOf(VocabularyWordbook(8, "wb-8", "JLPT N2"))),
        )
        viewModel.uiState.collectInBackground(backgroundScope)
        advanceUntilIdle()

        assertEquals(8L, viewModel.uiState.value.selectedWordbookId)
        assertEquals("JLPT N2", viewModel.uiState.value.selectedWordbookName)
        assertEquals(8L, repository.lastWordbookId)
    }
}

private fun <T> Flow<T>.collectInBackground(scope: kotlinx.coroutines.CoroutineScope) =
    scope.launch { this@collectInBackground.collect { } }

private class RecordingVocabularyRepository : VocabularyRepository {
    var lastFilter: Pair<String, Long?>? = null
    var lastTagId: Long? = null
    var lastWordbookId: Long? = null
    var lastLanguageTag: String? = null

    override fun observeEntries(query: String, tagId: Long?): Flow<List<VocabularyEntry>> {
        lastFilter = query to tagId
        return MutableStateFlow(emptyList())
    }

    override fun observeEntries(
        query: String,
        tagId: Long?,
        wordbookId: Long?,
        languageTag: String?,
    ): Flow<List<VocabularyEntry>> {
        lastFilter = query to tagId
        lastTagId = tagId
        lastWordbookId = wordbookId
        lastLanguageTag = languageTag
        return MutableStateFlow(emptyList())
    }

    override fun observeLanguages(): Flow<List<String>> = flowOf(listOf("en", "ja"))

    override fun observeEntry(id: Long): Flow<VocabularyEntry?> = MutableStateFlow(null)
    override suspend fun save(draft: ValidatedVocabularyDraft): Long = 1
    override suspend fun delete(id: Long) = Unit
}

private class FakeWordbookRepository(
    private val wordbooks: List<VocabularyWordbook>,
) : WordbookRepository {
    override fun observeWordbooks(): Flow<List<VocabularyWordbook>> = flowOf(wordbooks)
    override suspend fun save(id: Long?, name: String): SaveWordbookResult =
        SaveWordbookResult.Saved(id ?: 1)
    override suspend fun delete(id: Long) = Unit
}

private class FakeTagRepository(initialTags: List<VocabularyTag> = emptyList()) : TagRepository {
    private val tags = MutableStateFlow(initialTags)

    override fun observeTags(): Flow<List<VocabularyTag>> = tags
    override suspend fun save(id: Long?, name: String): SaveTagResult = SaveTagResult.Saved(id ?: 1)
    override suspend fun delete(id: Long) = Unit

    fun replaceWith(value: List<VocabularyTag>) {
        tags.value = value
    }
}
