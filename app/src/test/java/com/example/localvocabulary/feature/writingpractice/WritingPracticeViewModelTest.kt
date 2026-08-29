package com.example.localvocabulary.feature.writingpractice

import androidx.lifecycle.SavedStateHandle
import com.example.localvocabulary.feature.wordlist.MainDispatcherRule
import com.example.localvocabulary.vocabulary.domain.SaveTagResult
import com.example.localvocabulary.vocabulary.domain.SaveWordbookResult
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.ValidatedVocabularyDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularyPracticeFilter
import com.example.localvocabulary.vocabulary.domain.VocabularyPracticeItem
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import com.example.localvocabulary.vocabulary.domain.VocabularyWordbook
import com.example.localvocabulary.vocabulary.domain.WordbookRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WritingPracticeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `all language wordbook and tag scopes query the existing vocabulary repository`() = runTest {
        val repository = FakePracticeVocabularyRepository(defaultItems())
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        assertEquals(VocabularyPracticeFilter(), repository.lastFilter)
        assertEquals(3, viewModel.uiState.value.eligibleCount)

        viewModel.onAction(WritingPracticeAction.ScopeTypeSelected(PracticeScopeType.LANGUAGE))
        viewModel.onAction(WritingPracticeAction.LanguageSelected("ja"))
        advanceUntilIdle()
        assertEquals(VocabularyPracticeFilter(languageTag = "ja"), repository.lastFilter)
        assertEquals(1, viewModel.uiState.value.eligibleCount)

        viewModel.onAction(WritingPracticeAction.ScopeTypeSelected(PracticeScopeType.WORDBOOK))
        viewModel.onAction(WritingPracticeAction.WordbookSelected(8))
        advanceUntilIdle()
        assertEquals(VocabularyPracticeFilter(wordbookId = 8), repository.lastFilter)
        assertEquals(2, viewModel.uiState.value.eligibleCount)

        viewModel.onAction(WritingPracticeAction.ScopeTypeSelected(PracticeScopeType.TAG))
        viewModel.onAction(WritingPracticeAction.TagSelected(9))
        advanceUntilIdle()
        assertEquals(VocabularyPracticeFilter(tagId = 9), repository.lastFilter)
        assertEquals(2, viewModel.uiState.value.eligibleCount)
    }

    @Test
    fun `preselected wordbook and empty or one-item scopes are handled without special cases`() = runTest {
        val oneItem = listOf(item(1, "long", "en", "긴"))
        val repository = FakePracticeVocabularyRepository(oneItem, wordbookMembers = mapOf(8L to setOf(1L)))
        val viewModel = viewModel(repository, savedState = mapOf("practiceWordbookId" to 8L))
        advanceUntilIdle()

        assertEquals(PracticeScopeType.WORDBOOK, viewModel.uiState.value.scopeType)
        assertEquals(1, viewModel.uiState.value.eligibleCount)
        viewModel.onAction(WritingPracticeAction.Start)
        assertEquals(PracticePhase.QUESTION, viewModel.uiState.value.phase)
        assertEquals(1, viewModel.uiState.value.sessionTotal)

        val empty = viewModel(FakePracticeVocabularyRepository(emptyList()))
        advanceUntilIdle()
        assertEquals(0, empty.uiState.value.eligibleCount)
        assertFalse(empty.uiState.value.canStart)
        empty.onAction(WritingPracticeAction.Start)
        assertEquals(PracticePhase.SETUP, empty.uiState.value.phase)
    }

    @Test
    fun `candidate selection populates answer but explicit submit is required`() = runTest {
        val viewModel = viewModel(FakePracticeVocabularyRepository(listOf(item(1, "食べる", "ja", "먹다"))))
        advanceUntilIdle()
        viewModel.onAction(WritingPracticeAction.Start)
        viewModel.onAction(WritingPracticeAction.AnswerChanged("食べる"))

        assertEquals("食べる", viewModel.uiState.value.answer)
        assertNull(viewModel.uiState.value.feedback)

        viewModel.onAction(WritingPracticeAction.Submit)
        assertEquals(true, viewModel.uiState.value.feedback?.isCorrect)
        assertEquals(1, viewModel.uiState.value.firstAttemptCorrect)
        viewModel.onAction(WritingPracticeAction.Next)
        assertEquals(PracticePhase.COMPLETE, viewModel.uiState.value.phase)
    }

    @Test
    fun `wrong first attempt remains one error after retry succeeds and restart resets statistics`() = runTest {
        val viewModel = viewModel(FakePracticeVocabularyRepository(listOf(item(1, "Wasser", "de", "물"))))
        advanceUntilIdle()
        viewModel.onAction(WritingPracticeAction.Start)
        val firstInputKey = viewModel.uiState.value.handwritingSessionKey
        viewModel.onAction(WritingPracticeAction.AnswerChanged("wasser"))
        viewModel.onAction(WritingPracticeAction.Submit)
        assertEquals(1, viewModel.uiState.value.firstAttemptIncorrect)

        viewModel.onAction(WritingPracticeAction.Retry)
        assertTrue(viewModel.uiState.value.handwritingSessionKey > firstInputKey)
        assertEquals("", viewModel.uiState.value.answer)
        viewModel.onAction(WritingPracticeAction.AnswerChanged("Wasser"))
        viewModel.onAction(WritingPracticeAction.Submit)

        assertEquals(0, viewModel.uiState.value.firstAttemptCorrect)
        assertEquals(1, viewModel.uiState.value.firstAttemptIncorrect)
        assertEquals(true, viewModel.uiState.value.feedback?.isCorrect)
        viewModel.onAction(WritingPracticeAction.Next)
        viewModel.onAction(WritingPracticeAction.Restart)
        assertEquals(PracticePhase.QUESTION, viewModel.uiState.value.phase)
        assertEquals(0, viewModel.uiState.value.firstAttemptCorrect)
        assertEquals(0, viewModel.uiState.value.firstAttemptIncorrect)
    }

    @Test
    fun `next question keeps the shuffled snapshot and clears answer state for the new language`() = runTest {
        val viewModel = viewModel(
            FakePracticeVocabularyRepository(
                listOf(item(1, "食べる", "ja", "먹다"), item(2, "long", "en", "긴")),
            ),
        )
        advanceUntilIdle()
        viewModel.onAction(WritingPracticeAction.SessionLengthSelected(PracticeSessionLength.ALL))
        viewModel.onAction(WritingPracticeAction.Start)
        val snapshot = viewModel.uiState.value.sessionEntryIds
        val firstId = viewModel.uiState.value.currentQuestion?.entryId
        val firstLanguage = viewModel.uiState.value.currentQuestion?.languageTag
        val inputKey = viewModel.uiState.value.handwritingSessionKey
        val expected = requireNotNull(viewModel.uiState.value.currentQuestion).headword
        viewModel.onAction(WritingPracticeAction.AnswerChanged(expected))
        viewModel.onAction(WritingPracticeAction.Submit)
        viewModel.onAction(WritingPracticeAction.Next)

        assertEquals(snapshot, viewModel.uiState.value.sessionEntryIds)
        assertNotEquals(firstId, viewModel.uiState.value.currentQuestion?.entryId)
        assertNotEquals(firstLanguage, viewModel.uiState.value.currentQuestion?.languageTag)
        assertEquals("", viewModel.uiState.value.answer)
        assertNull(viewModel.uiState.value.feedback)
        assertTrue(viewModel.uiState.value.handwritingSessionKey > inputKey)

        viewModel.onAction(WritingPracticeAction.InputModeSelected(PracticeInputMode.KEYBOARD))
        assertEquals(PracticeInputMode.KEYBOARD, viewModel.uiState.value.inputMode)
    }

    @Test
    fun `unsafe hints and invalid BCP 47 entries are counted as excluded`() = runTest {
        val repository = FakePracticeVocabularyRepository(
            listOf(
                item(1, "hello", "en", "hello"),
                item(2, "word", "not_a_tag", "뜻"),
                item(3, "你好", "zh-Hans", "안녕"),
            ),
        )
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.eligibleCount)
        assertEquals(2, viewModel.uiState.value.excludedCount)
    }

    private fun viewModel(
        repository: VocabularyRepository,
        savedState: Map<String, Any?> = emptyMap(),
    ) = WritingPracticeViewModel(
        savedStateHandle = SavedStateHandle(savedState),
        vocabularyRepository = repository,
        tagRepository = FakePracticeTagRepository,
        wordbookRepository = FakePracticeWordbookRepository,
        sessionFactory = PracticeSessionFactory(),
    )
}

private class FakePracticeVocabularyRepository(
    private val items: List<VocabularyPracticeItem>,
    private val wordbookMembers: Map<Long, Set<Long>> = mapOf(8L to setOf(1L, 2L)),
    private val tagMembers: Map<Long, Set<Long>> = mapOf(9L to setOf(1L, 3L)),
) : VocabularyRepository {
    var lastFilter: VocabularyPracticeFilter? = null

    override fun observeEntries(query: String, tagId: Long?): Flow<List<VocabularyEntry>> =
        flowOf(emptyList())

    override fun observeLanguages(): Flow<List<String>> = flowOf(
        items.map(VocabularyPracticeItem::languageTag).distinct(),
    )

    override fun observeEntry(id: Long): Flow<VocabularyEntry?> = flowOf(null)

    override suspend fun findPracticeItems(
        filter: VocabularyPracticeFilter,
    ): List<VocabularyPracticeItem> {
        lastFilter = filter
        return items.filter { item ->
            (filter.languageTag == null || item.languageTag == filter.languageTag) &&
                (filter.wordbookId == null || item.entryId in wordbookMembers[filter.wordbookId].orEmpty()) &&
                (filter.tagId == null || item.entryId in tagMembers[filter.tagId].orEmpty())
        }
    }

    override suspend fun save(draft: ValidatedVocabularyDraft): Long = draft.id ?: 1
    override suspend fun delete(id: Long) = Unit
}

private object FakePracticeTagRepository : TagRepository {
    override fun observeTags(): Flow<List<VocabularyTag>> = flowOf(
        listOf(VocabularyTag(9, "tag-9", "동사")),
    )

    override suspend fun save(id: Long?, name: String): SaveTagResult = SaveTagResult.Saved(id ?: 9)
    override suspend fun delete(id: Long) = Unit
}

private object FakePracticeWordbookRepository : WordbookRepository {
    override fun observeWordbooks(): Flow<List<VocabularyWordbook>> = flowOf(
        listOf(VocabularyWordbook(8, "wordbook-8", "JLPT")),
    )

    override suspend fun save(id: Long?, name: String): SaveWordbookResult =
        SaveWordbookResult.Saved(id ?: 8)

    override suspend fun delete(id: Long) = Unit
}

private fun defaultItems() = listOf(
    item(1, "食べる", "ja", "먹다"),
    item(2, "long", "en", "긴"),
    item(3, "Wasser", "de", "물"),
)

private fun item(
    id: Long,
    headword: String,
    languageTag: String,
    meaning: String,
) = VocabularyPracticeItem(
    entryId = id,
    headword = headword,
    languageTag = languageTag,
    representativeMeaning = meaning,
    reading = "",
    pronunciation = "",
    partOfSpeech = "",
    grammaticalGender = "",
    example = "",
)
