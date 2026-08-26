package com.example.localvocabulary.feature.tags

import com.example.localvocabulary.feature.wordlist.MainDispatcherRule
import com.example.localvocabulary.vocabulary.domain.SaveTagResult
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import com.example.localvocabulary.vocabulary.domain.VocabularyTagSummary
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
class TagManagementViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `tag collection summaries expose repository word counts`() = runTest {
        val repository = FakeTagManagementRepository(
            listOf(VocabularyTagSummary(VocabularyTag(1, "tag-1", "JLPT N2"), 128)),
        )
        val viewModel = TagManagementViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals("JLPT N2", viewModel.uiState.value.tags.single().tag.name)
        assertEquals(128, viewModel.uiState.value.tags.single().entryCount)
    }

    @Test
    fun `tag deletion requires confirmation before using existing delete path`() = runTest {
        val tag = VocabularyTag(1, "tag-1", "여행")
        val repository = FakeTagManagementRepository(listOf(VocabularyTagSummary(tag, 34)))
        val viewModel = TagManagementViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onAction(TagManagementAction.DeleteRequested(tag))
        assertEquals(tag, viewModel.uiState.value.pendingDeleteTag)
        assertEquals(emptyList<Long>(), repository.deletedIds)

        viewModel.onAction(TagManagementAction.DeleteConfirmed)
        advanceUntilIdle()

        assertEquals(listOf(1L), repository.deletedIds)
        assertNull(viewModel.uiState.value.pendingDeleteTag)
    }

    @Test
    fun `cancelling tag deletion does not delete anything`() = runTest {
        val tag = VocabularyTag(1, "tag-1", "음식")
        val repository = FakeTagManagementRepository(listOf(VocabularyTagSummary(tag, 1)))
        val viewModel = TagManagementViewModel(repository)

        viewModel.onAction(TagManagementAction.DeleteRequested(tag))
        viewModel.onAction(TagManagementAction.DeleteCancelled)

        assertNull(viewModel.uiState.value.pendingDeleteTag)
        assertEquals(emptyList<Long>(), repository.deletedIds)
    }
}

private class FakeTagManagementRepository(
    initial: List<VocabularyTagSummary>,
) : TagRepository {
    private val summaries = MutableStateFlow(initial)
    val deletedIds = mutableListOf<Long>()

    override fun observeTags(): Flow<List<VocabularyTag>> =
        MutableStateFlow(summaries.value.map { it.tag })

    override fun observeTagSummaries(): Flow<List<VocabularyTagSummary>> = summaries

    override suspend fun save(id: Long?, name: String): SaveTagResult =
        SaveTagResult.Saved(id ?: 1L)

    override suspend fun delete(id: Long) {
        deletedIds += id
        summaries.value = summaries.value.filterNot { it.tag.id == id }
    }
}
