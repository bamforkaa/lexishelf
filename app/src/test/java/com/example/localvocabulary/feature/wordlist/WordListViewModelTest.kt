package com.example.localvocabulary.feature.wordlist

import com.example.localvocabulary.vocabulary.domain.SaveTagResult
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.ValidatedVocabularyDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        val viewModel = WordListViewModel(repository, tags)
        viewModel.uiState.collectInBackground(backgroundScope)
        advanceUntilIdle()

        viewModel.onAction(WordListAction.TagSelected(4))
        advanceUntilIdle()
        tags.replaceWith(emptyList())
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedTagId)
        assertEquals("" to null, repository.lastFilter)
    }
}

private fun <T> Flow<T>.collectInBackground(scope: kotlinx.coroutines.CoroutineScope) =
    scope.launch { this@collectInBackground.collect { } }

private class RecordingVocabularyRepository : VocabularyRepository {
    var lastFilter: Pair<String, Long?>? = null

    override fun observeEntries(query: String, tagId: Long?): Flow<List<VocabularyEntry>> {
        lastFilter = query to tagId
        return MutableStateFlow(emptyList())
    }

    override fun observeEntry(id: Long): Flow<VocabularyEntry?> = MutableStateFlow(null)
    override suspend fun save(draft: ValidatedVocabularyDraft): Long = 1
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
