package com.example.localvocabulary.feature.wordeditor

import androidx.lifecycle.SavedStateHandle
import com.example.localvocabulary.feature.wordlist.MainDispatcherRule
import com.example.localvocabulary.settings.AppSettings
import com.example.localvocabulary.settings.SettingsRepository
import com.example.localvocabulary.vocabulary.domain.ExampleSentence
import com.example.localvocabulary.vocabulary.domain.SaveTagResult
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.ValidatedVocabularyDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import com.example.localvocabulary.vocabulary.domain.VocabularySense
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import com.example.localvocabulary.vocabulary.domain.VocabularyValidationError
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WordEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `saving blank headword exposes validation without calling repository`() = runTest {
        val repository = RecordingVocabularyRepository()
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()

        viewModel.onAction(WordEditorAction.Save)

        assertEquals(VocabularyValidationError.MissingHeadword, viewModel.uiState.value.validationError)
        assertTrue(repository.savedDrafts.isEmpty())
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `valid editor actions save trimmed multiple senses examples notes and tags`() = runTest {
        val repository = RecordingVocabularyRepository(savedId = 42)
        val viewModel = createViewModel(
            repository = repository,
            tags = listOf(VocabularyTag(2, "tag-2", "Important")),
        )
        advanceUntilIdle()
        val savedEffect = async(start = CoroutineStart.UNDISPATCHED) { viewModel.effects.first() }

        viewModel.onAction(WordEditorAction.HeadwordChanged("  word  "))
        viewModel.onAction(WordEditorAction.MeaningChanged(-1, "  first meaning  "))
        viewModel.onAction(WordEditorAction.PartOfSpeechChanged(-1, "  noun  "))
        viewModel.onAction(WordEditorAction.ExampleChanged(-1, -2, "  first example  "))
        viewModel.onAction(WordEditorAction.AddSense)
        viewModel.onAction(WordEditorAction.MeaningChanged(-10, "second meaning"))
        viewModel.onAction(WordEditorAction.ExampleChanged(-10, -11, "second example"))
        viewModel.onAction(WordEditorAction.NotesChanged("  user note  "))
        viewModel.onAction(WordEditorAction.TagToggled(2))
        viewModel.onAction(WordEditorAction.Save)
        advanceUntilIdle()

        val draft = repository.savedDrafts.single()
        assertEquals("word", draft.headword)
        assertEquals(listOf("first meaning", "second meaning"), draft.senses.map { it.meaning })
        assertEquals(listOf("first example"), draft.senses.first().examples)
        assertEquals(listOf("second example"), draft.senses.last().examples)
        assertEquals("user note", draft.notes)
        assertEquals(setOf(2L), draft.tagIds)
        assertEquals(WordEditorEffect.Saved(42), savedEffect.await())
        assertEquals(42L, viewModel.uiState.value.entryId)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `database emissions do not overwrite user edits after initial load`() = runTest {
        val repository = RecordingVocabularyRepository(initialEntry = entry(7, "original"))
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf("entryId" to 7L)),
        )
        advanceUntilIdle()

        viewModel.onAction(WordEditorAction.HeadwordChanged("user edit"))
        repository.entry.value = entry(7, "external update")
        advanceUntilIdle()

        assertEquals("user edit", viewModel.uiState.value.headword)
    }

    @Test
    fun `missing entry finishes loading with an error state`() = runTest {
        val viewModel = createViewModel(
            repository = RecordingVocabularyRepository(initialEntry = null),
            savedStateHandle = SavedStateHandle(mapOf("entryId" to 999L)),
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.loadErrorMessage)
        assertEquals(999L, viewModel.uiState.value.entryId)
    }

    private fun createViewModel(
        repository: RecordingVocabularyRepository,
        tags: List<VocabularyTag> = emptyList(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = WordEditorViewModel(
        savedStateHandle = savedStateHandle,
        vocabularyRepository = repository,
        tagRepository = FakeTagRepository(tags),
        settingsRepository = FakeSettingsRepository(),
    )
}

private class RecordingVocabularyRepository(
    initialEntry: VocabularyEntry? = null,
    private val savedId: Long = 1,
) : VocabularyRepository {
    val entry = MutableStateFlow(initialEntry)
    val savedDrafts = mutableListOf<ValidatedVocabularyDraft>()

    override fun observeEntries(query: String, tagId: Long?): Flow<List<VocabularyEntry>> =
        flowOf(entry.value?.let(::listOf) ?: emptyList())

    override fun observeEntry(id: Long): Flow<VocabularyEntry?> = entry

    override suspend fun save(draft: ValidatedVocabularyDraft): Long {
        savedDrafts += draft
        return savedId
    }

    override suspend fun delete(id: Long) = Unit
}

private class FakeTagRepository(
    private val tags: List<VocabularyTag>,
) : TagRepository {
    override fun observeTags(): Flow<List<VocabularyTag>> = flowOf(tags)
    override suspend fun save(id: Long?, name: String): SaveTagResult = SaveTagResult.Saved(id ?: 1)
    override suspend fun delete(id: Long) = Unit
}

private class FakeSettingsRepository : SettingsRepository {
    override val settings: Flow<AppSettings> = flowOf(AppSettings(defaultLanguageTag = "en"))
    override suspend fun setDefaultLanguageTag(languageTag: String) = Unit
}

private fun entry(id: Long, headword: String) = VocabularyEntry(
    id = id,
    backupId = "entry-$id",
    headword = headword,
    languageTag = "en",
    senses = listOf(
        VocabularySense(
            id = 10,
            meaning = "meaning",
            partOfSpeech = "noun",
            examples = listOf(ExampleSentence(20, "example")),
        ),
    ),
    notes = "note",
    tags = listOf(VocabularyTag(2, "tag-2", "Important")),
    createdAtEpochMillis = 100,
    modifiedAtEpochMillis = 200,
)
