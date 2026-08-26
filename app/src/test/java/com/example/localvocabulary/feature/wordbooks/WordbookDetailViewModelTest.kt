package com.example.localvocabulary.feature.wordbooks

import androidx.lifecycle.SavedStateHandle
import com.example.localvocabulary.feature.wordlist.MainDispatcherRule
import com.example.localvocabulary.vocabulary.domain.ExampleSentence
import com.example.localvocabulary.vocabulary.domain.SaveTagResult
import com.example.localvocabulary.vocabulary.domain.SaveWordbookResult
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.ValidatedVocabularyDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import com.example.localvocabulary.vocabulary.domain.VocabularySense
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import com.example.localvocabulary.vocabulary.domain.VocabularyWordbook
import com.example.localvocabulary.vocabulary.domain.WordbookRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WordbookDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `batch add keeps selection across filters and ignores existing membership`() = runTest {
        val memberships = MutableStateFlow(setOf(1L))
        val entries = listOf(
            entry(1, "long", "en", tagId = 9),
            entry(2, "soul", "en", tagId = 9),
            entry(3, "食べる", "ja", tagId = 10),
        )
        val wordbooks = FakeMembershipWordbookRepository(memberships)
        val vocabulary = FilteringVocabularyRepository(entries, memberships)
        val viewModel = createViewModel(vocabulary, wordbooks)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onAction(WordbookDetailAction.StartAdding)
        viewModel.onAction(WordbookDetailAction.EntryToggled(1))
        viewModel.onAction(WordbookDetailAction.EntryToggled(2))
        viewModel.onAction(
            WordbookDetailAction.FilterSelected(WordbookSelectionFilter.LANGUAGE),
        )
        viewModel.onAction(WordbookDetailAction.LanguageSelected("en"))
        advanceUntilIdle()

        assertEquals(setOf(2L), viewModel.uiState.value.selectedEntryIds)
        assertEquals("en", vocabulary.lastLanguageTag)
        assertEquals(setOf(1L), viewModel.uiState.value.membershipEntryIds)

        viewModel.onAction(WordbookDetailAction.AddSelected)
        advanceUntilIdle()

        assertEquals(setOf(1L, 2L), memberships.value)
        assertEquals(WordbookSelectionMode.BROWSE, viewModel.uiState.value.mode)
        assertEquals(2, viewModel.uiState.value.membershipEntryIds.size)
        assertEquals(1, wordbooks.addCalls)
    }

    @Test
    fun `batch remove preserves vocabulary and selection while tag filter changes`() = runTest {
        val memberships = MutableStateFlow(setOf(1L, 2L))
        val entries = listOf(
            entry(1, "long", "en", tagId = 9),
            entry(2, "soul", "en", tagId = 10),
        )
        val wordbooks = FakeMembershipWordbookRepository(memberships)
        val vocabulary = FilteringVocabularyRepository(entries, memberships)
        val viewModel = createViewModel(vocabulary, wordbooks)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.onAction(WordbookDetailAction.StartRemoving)
        viewModel.onAction(WordbookDetailAction.EntryToggled(1))
        viewModel.onAction(WordbookDetailAction.FilterSelected(WordbookSelectionFilter.TAG))
        viewModel.onAction(WordbookDetailAction.TagSelected(10))
        advanceUntilIdle()

        assertEquals(setOf(1L), viewModel.uiState.value.selectedEntryIds)
        assertEquals(10L, vocabulary.lastTagId)

        viewModel.onAction(WordbookDetailAction.RemoveRequested)
        assertTrue(viewModel.uiState.value.isRemoveConfirmationVisible)
        viewModel.onAction(WordbookDetailAction.RemoveConfirmed)
        advanceUntilIdle()

        assertEquals(setOf(2L), memberships.value)
        assertEquals(2, vocabulary.allEntries.size)
        assertFalse(vocabulary.allEntries.any { it.id == 1L && it.senses.isEmpty() })
        assertEquals(1, viewModel.uiState.value.membershipEntryIds.size)
    }

    private fun createViewModel(
        vocabularyRepository: VocabularyRepository,
        wordbookRepository: WordbookRepository,
    ) = WordbookDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("wordbookId" to 8L)),
        vocabularyRepository = vocabularyRepository,
        tagRepository = FakeDetailTagRepository,
        wordbookRepository = wordbookRepository,
    )
}

private class FakeMembershipWordbookRepository(
    private val memberships: MutableStateFlow<Set<Long>>,
) : WordbookRepository {
    var addCalls = 0

    override fun observeWordbooks(): Flow<List<VocabularyWordbook>> = flowOf(
        listOf(VocabularyWordbook(8, "wordbook-8", "TOEIC 950")),
    )

    override fun observeEntryIds(wordbookId: Long): Flow<Set<Long>> = memberships

    override suspend fun addEntries(wordbookId: Long, entryIds: Set<Long>): Int {
        addCalls += 1
        val newIds = entryIds - memberships.value
        memberships.value = memberships.value + newIds
        return newIds.size
    }

    override suspend fun removeEntries(wordbookId: Long, entryIds: Set<Long>): Int {
        val removed = memberships.value.intersect(entryIds)
        memberships.value = memberships.value - removed
        return removed.size
    }

    override suspend fun save(id: Long?, name: String): SaveWordbookResult =
        SaveWordbookResult.Saved(id ?: 8)

    override suspend fun delete(id: Long) = Unit
}

private class FilteringVocabularyRepository(
    val allEntries: List<VocabularyEntry>,
    private val memberships: MutableStateFlow<Set<Long>>,
) : VocabularyRepository {
    private val entries = MutableStateFlow(allEntries)
    var lastLanguageTag: String? = null
    var lastTagId: Long? = null

    override fun observeEntries(query: String, tagId: Long?): Flow<List<VocabularyEntry>> =
        observeEntries(query, tagId, null, null)

    override fun observeEntries(
        query: String,
        tagId: Long?,
        wordbookId: Long?,
        languageTag: String?,
    ): Flow<List<VocabularyEntry>> {
        lastLanguageTag = languageTag
        lastTagId = tagId
        return combine(entries, memberships) { values, memberIds ->
            values.filter { entry ->
                (query.isBlank() || entry.headword.contains(query, ignoreCase = true) ||
                    entry.senses.any { it.meaning.contains(query, ignoreCase = true) }) &&
                    (tagId == null || entry.tags.any { it.id == tagId }) &&
                    (languageTag == null || entry.languageTag == languageTag) &&
                    (wordbookId == null || entry.id in memberIds)
            }
        }
    }

    override fun observeLanguages(): Flow<List<String>> = entries.map { values ->
        values.map(VocabularyEntry::languageTag).distinct()
    }

    override fun observeEntry(id: Long): Flow<VocabularyEntry?> =
        entries.map { values -> values.firstOrNull { it.id == id } }

    override suspend fun save(draft: ValidatedVocabularyDraft): Long = draft.id ?: 1
    override suspend fun delete(id: Long) = Unit
}

private object FakeDetailTagRepository : TagRepository {
    override fun observeTags(): Flow<List<VocabularyTag>> = flowOf(
        listOf(
            VocabularyTag(9, "tag-9", "어려움"),
            VocabularyTag(10, "tag-10", "동사"),
        ),
    )

    override suspend fun save(id: Long?, name: String): SaveTagResult = SaveTagResult.Saved(id ?: 1)
    override suspend fun delete(id: Long) = Unit
}

private fun entry(id: Long, headword: String, languageTag: String, tagId: Long) = VocabularyEntry(
    id = id,
    backupId = "entry-$id",
    headword = headword,
    languageTag = languageTag,
    senses = listOf(
        VocabularySense(
            id = id * 10,
            meaning = "meaning-$id",
            partOfSpeech = "",
            examples = listOf(ExampleSentence(id * 100, "example-$id")),
        ),
    ),
    notes = "",
    tags = listOf(VocabularyTag(tagId, "tag-$tagId", "tag-$tagId")),
    createdAtEpochMillis = 1,
    modifiedAtEpochMillis = 1,
)
