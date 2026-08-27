package com.example.localvocabulary.feature.wordeditor

import androidx.lifecycle.SavedStateHandle
import com.example.localvocabulary.core.common.TimeProvider
import com.example.localvocabulary.dictionary.registry.DefaultDictionaryProviderRegistry
import com.example.localvocabulary.dictionary.registry.DictionaryProviderRegistry
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
import com.example.localvocabulary.vocabulary.domain.PronunciationNotation
import com.example.localvocabulary.vocabulary.domain.normalizeTagName
import com.example.localvocabulary.vocabulary.domain.SaveWordbookResult
import com.example.localvocabulary.vocabulary.domain.VocabularyWordbook
import com.example.localvocabulary.vocabulary.domain.WordbookRepository
import com.example.localvocabulary.vocabulary.domain.normalizeWordbookName
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
    fun `manual pronunciation and grammatical gender are saved as separate fields`() = runTest {
        val repository = RecordingVocabularyRepository(savedId = 42)
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()
        val savedEffect = async(start = CoroutineStart.UNDISPATCHED) { viewModel.effects.first() }
        viewModel.onAction(WordEditorAction.HeadwordChanged("Wasser"))
        viewModel.onAction(WordEditorAction.LanguageTagChanged("de"))
        viewModel.onAction(WordEditorAction.MeaningChanged(-1, "water"))
        viewModel.onAction(WordEditorAction.AddPronunciation)
        val pronunciationKey = viewModel.uiState.value.pronunciations.single().key
        viewModel.onAction(
            WordEditorAction.PronunciationValueChanged(pronunciationKey, "  /ˈvasɐ/  "),
        )
        viewModel.onAction(
            WordEditorAction.PronunciationNotationChanged(
                pronunciationKey,
                PronunciationNotation.IPA,
            ),
        )
        viewModel.onAction(
            WordEditorAction.GrammaticalGenderVisibilityChanged(-1, true),
        )
        viewModel.onAction(WordEditorAction.GrammaticalGenderChanged(-1, "neuter"))

        viewModel.onAction(WordEditorAction.Save)
        advanceUntilIdle()

        val draft = repository.savedDrafts.single()
        assertEquals("/ˈvasɐ/", draft.pronunciations.single().value)
        assertEquals(PronunciationNotation.IPA, draft.pronunciations.single().notation)
        assertEquals("de", draft.pronunciations.single().languageTag)
        assertEquals("neuter", draft.senses.single().grammaticalGender?.displayValue())
        assertEquals(WordEditorEffect.Saved(42), savedEffect.await())
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
    fun `manual language selection persists in user catalog and updates editor state`() = runTest {
        val settingsRepository = FakeSettingsRepository()
        val viewModel = createViewModel(
            repository = RecordingVocabularyRepository(),
            settingsRepository = settingsRepository,
        )
        advanceUntilIdle()

        viewModel.onAction(WordEditorAction.UserLanguageAdded("nl"))
        advanceUntilIdle()

        assertEquals(listOf("nl"), settingsRepository.addedLanguageTags)
        assertEquals(setOf("nl"), viewModel.uiState.value.userLanguageTags)
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

    @Test
    fun `creating a tag in editor selects it and saved entry receives the relation`() = runTest {
        val repository = RecordingVocabularyRepository(savedId = 9)
        val tags = InlineTagRepository()
        val viewModel = createViewModel(repository = repository, tagRepository = tags)
        advanceUntilIdle()

        viewModel.onAction(WordEditorAction.CreateTagRequested)
        viewModel.onAction(WordEditorAction.NewTagNameChanged("  JLPT   N2  "))
        viewModel.onAction(WordEditorAction.CreateTagConfirmed)
        advanceUntilIdle()

        assertEquals(setOf(1L), viewModel.uiState.value.selectedTagIds)
        assertEquals("JLPT N2", viewModel.uiState.value.availableTags.single().name)
        assertFalse(viewModel.uiState.value.isTagCreatorVisible)

        viewModel.onAction(WordEditorAction.HeadwordChanged("食べる"))
        viewModel.onAction(WordEditorAction.MeaningChanged(-1, "먹다"))
        viewModel.onAction(WordEditorAction.Save)
        advanceUntilIdle()

        assertEquals(setOf(1L), repository.savedDrafts.single().tagIds)
    }

    @Test
    fun `blank inline tag remains in dialog with validation error`() = runTest {
        val viewModel = createViewModel(
            repository = RecordingVocabularyRepository(),
            tagRepository = InlineTagRepository(),
        )
        advanceUntilIdle()

        viewModel.onAction(WordEditorAction.CreateTagRequested)
        viewModel.onAction(WordEditorAction.NewTagNameChanged("   "))
        viewModel.onAction(WordEditorAction.CreateTagConfirmed)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isTagCreatorVisible)
        assertEquals("태그 이름을 입력하세요.", viewModel.uiState.value.tagCreationError)
        assertTrue(viewModel.uiState.value.selectedTagIds.isEmpty())
    }

    @Test
    fun `duplicate inline tag selects existing tag without creating another row`() = runTest {
        val tags = InlineTagRepository(listOf(VocabularyTag(7, "tag-7", "여행")))
        val viewModel = createViewModel(
            repository = RecordingVocabularyRepository(),
            tagRepository = tags,
        )
        advanceUntilIdle()

        viewModel.onAction(WordEditorAction.CreateTagRequested)
        viewModel.onAction(WordEditorAction.NewTagNameChanged(" 여행 "))
        viewModel.onAction(WordEditorAction.CreateTagConfirmed)
        advanceUntilIdle()

        assertEquals(setOf(7L), viewModel.uiState.value.selectedTagIds)
        assertEquals(1, tags.currentTags.size)
        assertEquals("이미 있는 태그를 선택했습니다.", viewModel.uiState.value.tagCreationMessage)
    }

    @Test
    fun `cancelling inline tag creation leaves draft selection unchanged`() = runTest {
        val tags = InlineTagRepository()
        val viewModel = createViewModel(
            repository = RecordingVocabularyRepository(),
            tagRepository = tags,
        )
        advanceUntilIdle()

        viewModel.onAction(WordEditorAction.CreateTagRequested)
        viewModel.onAction(WordEditorAction.NewTagNameChanged("취소할 태그"))
        viewModel.onAction(WordEditorAction.CreateTagDismissed)

        assertFalse(viewModel.uiState.value.isTagCreatorVisible)
        assertTrue(viewModel.uiState.value.selectedTagIds.isEmpty())
        assertTrue(tags.currentTags.isEmpty())
    }

    @Test
    fun `inline wordbook creation selects relation without creating a tag`() = runTest {
        val repository = RecordingVocabularyRepository()
        val wordbooks = InlineWordbookRepository()
        val viewModel = createViewModel(repository, wordbookRepository = wordbooks)
        advanceUntilIdle()

        viewModel.onAction(WordEditorAction.CreateWordbookRequested)
        viewModel.onAction(WordEditorAction.NewWordbookNameChanged("  JLPT   N2 "))
        viewModel.onAction(WordEditorAction.CreateWordbookConfirmed)
        advanceUntilIdle()
        viewModel.onAction(WordEditorAction.HeadwordChanged("食べる"))
        viewModel.onAction(WordEditorAction.MeaningChanged(-1, "먹다"))
        viewModel.onAction(WordEditorAction.Save)
        advanceUntilIdle()

        assertEquals("JLPT N2", viewModel.uiState.value.availableWordbooks.single().name)
        assertEquals(setOf(1L), repository.savedDrafts.single().wordbookIds)
        assertTrue(viewModel.uiState.value.availableTags.isEmpty())
    }

    @Test
    fun `duplicate candidate blocks silent insert until explicit override`() = runTest {
        val repository = RecordingVocabularyRepository(
            duplicateCandidates = listOf(entry(7, "Word")),
        )
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(WordEditorAction.HeadwordChanged(" word "))
        viewModel.onAction(WordEditorAction.MeaningChanged(-1, "another meaning"))

        viewModel.onAction(WordEditorAction.Save)
        advanceUntilIdle()

        assertEquals(7L, viewModel.uiState.value.duplicateCandidate?.id)
        assertTrue(repository.savedDrafts.isEmpty())

        viewModel.onAction(WordEditorAction.SaveDuplicateAnyway)
        advanceUntilIdle()

        assertEquals(1, repository.savedDrafts.size)
    }

    @Test
    fun `editing existing entry excludes itself from duplicate lookup`() = runTest {
        val repository = RecordingVocabularyRepository(initialEntry = entry(7, "Word"))
        val viewModel = createViewModel(
            repository,
            savedStateHandle = SavedStateHandle(mapOf("entryId" to 7L)),
        )
        advanceUntilIdle()

        viewModel.onAction(WordEditorAction.Save)
        advanceUntilIdle()

        assertEquals(7L, repository.lastExcludedEntryId)
        assertEquals(1, repository.savedDrafts.size)
    }

    private fun createViewModel(
        repository: RecordingVocabularyRepository,
        tags: List<VocabularyTag> = emptyList(),
        tagRepository: TagRepository = FakeTagRepository(tags),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        providerRegistry: DictionaryProviderRegistry = DefaultDictionaryProviderRegistry(emptyList()),
        wordbookRepository: WordbookRepository = InlineWordbookRepository(),
        settingsRepository: SettingsRepository = FakeSettingsRepository(),
    ) = WordEditorViewModel(
        savedStateHandle = savedStateHandle,
        vocabularyRepository = repository,
        tagRepository = tagRepository,
        settingsRepository = settingsRepository,
        dictionaryProviderRegistry = providerRegistry,
        timeProvider = TimeProvider { 1_000L },
        wordbookRepository = wordbookRepository,
    )
}

private class RecordingVocabularyRepository(
    initialEntry: VocabularyEntry? = null,
    private val savedId: Long = 1,
    private val duplicateCandidates: List<VocabularyEntry> = emptyList(),
) : VocabularyRepository {
    val entry = MutableStateFlow(initialEntry)
    val savedDrafts = mutableListOf<ValidatedVocabularyDraft>()
    var lastExcludedEntryId: Long? = null

    override suspend fun findDuplicateCandidates(
        headword: String,
        languageTag: String,
        excludingEntryId: Long?,
    ): List<VocabularyEntry> {
        lastExcludedEntryId = excludingEntryId
        return duplicateCandidates.filterNot { it.id == excludingEntryId }
    }

    override fun observeEntries(query: String, tagId: Long?): Flow<List<VocabularyEntry>> =
        flowOf(entry.value?.let(::listOf) ?: emptyList())

    override fun observeEntry(id: Long): Flow<VocabularyEntry?> = entry

    override suspend fun save(draft: ValidatedVocabularyDraft): Long {
        savedDrafts += draft
        return savedId
    }

    override suspend fun delete(id: Long) = Unit
}

private class InlineWordbookRepository(
    initialWordbooks: List<VocabularyWordbook> = emptyList(),
) : WordbookRepository {
    private val wordbooks = MutableStateFlow(initialWordbooks)
    override fun observeWordbooks(): Flow<List<VocabularyWordbook>> = wordbooks

    override suspend fun save(id: Long?, name: String): SaveWordbookResult {
        val normalized = normalizeWordbookName(name) ?: return SaveWordbookResult.BlankName
        val existing = wordbooks.value.firstOrNull {
            normalizeWordbookName(it.name)?.identity == normalized.identity && it.id != id
        }
        if (existing != null) return SaveWordbookResult.NameConflict(existing.id)
        val newId = id ?: ((wordbooks.value.maxOfOrNull { it.id } ?: 0L) + 1L)
        val saved = VocabularyWordbook(newId, "wordbook-$newId", normalized.displayName)
        wordbooks.value = wordbooks.value.filterNot { it.id == newId } + saved
        return SaveWordbookResult.Saved(newId)
    }

    override suspend fun delete(id: Long) {
        wordbooks.value = wordbooks.value.filterNot { it.id == id }
    }
}

private class FakeTagRepository(
    private val tags: List<VocabularyTag>,
) : TagRepository {
    override fun observeTags(): Flow<List<VocabularyTag>> = flowOf(tags)
    override suspend fun save(id: Long?, name: String): SaveTagResult = SaveTagResult.Saved(id ?: 1)
    override suspend fun delete(id: Long) = Unit
}

private class InlineTagRepository(initialTags: List<VocabularyTag> = emptyList()) : TagRepository {
    private val tags = MutableStateFlow(initialTags)
    val currentTags: List<VocabularyTag> get() = tags.value

    override fun observeTags(): Flow<List<VocabularyTag>> = tags

    override suspend fun save(id: Long?, name: String): SaveTagResult {
        val normalized = normalizeTagName(name) ?: return SaveTagResult.BlankName
        val existing = tags.value.firstOrNull {
            normalizeTagName(it.name)?.identity == normalized.identity && it.id != id
        }
        if (existing != null) return SaveTagResult.NameConflict(existing.id)
        val newId = id ?: ((tags.value.maxOfOrNull { it.id } ?: 0L) + 1L)
        val saved = VocabularyTag(newId, "tag-$newId", normalized.displayName)
        tags.value = tags.value.filterNot { it.id == newId } + saved
        return SaveTagResult.Saved(newId)
    }

    override suspend fun delete(id: Long) {
        tags.value = tags.value.filterNot { it.id == id }
    }
}

private class FakeSettingsRepository : SettingsRepository {
    private val mutableSettings = MutableStateFlow(AppSettings(defaultLanguageTag = "en"))
    val addedLanguageTags = mutableListOf<String>()
    override val settings: Flow<AppSettings> = mutableSettings
    override suspend fun setDefaultLanguageTag(languageTag: String) = Unit
    override suspend fun addUserLanguageTag(languageTag: String) {
        addedLanguageTags += languageTag
        mutableSettings.value = mutableSettings.value.copy(
            userLanguageTags = mutableSettings.value.userLanguageTags + languageTag,
        )
    }
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
