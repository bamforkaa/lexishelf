package com.example.localvocabulary.feature.wordeditor

import androidx.lifecycle.SavedStateHandle
import com.example.localvocabulary.core.common.TimeProvider
import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.DictionaryAccess
import com.example.localvocabulary.dictionary.domain.DictionaryAttribution
import com.example.localvocabulary.dictionary.domain.DictionaryCachePolicy
import com.example.localvocabulary.dictionary.domain.DictionaryCapability
import com.example.localvocabulary.dictionary.domain.DictionaryExample
import com.example.localvocabulary.dictionary.domain.DictionaryLanguagePair
import com.example.localvocabulary.dictionary.domain.DictionaryMeaning
import com.example.localvocabulary.dictionary.domain.DictionaryPermission
import com.example.localvocabulary.dictionary.domain.DictionaryProvider
import com.example.localvocabulary.dictionary.domain.DictionaryProviderDescriptor
import com.example.localvocabulary.dictionary.domain.DictionaryProviderError
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.DictionaryQuery
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import com.example.localvocabulary.dictionary.domain.DictionarySearchPage
import com.example.localvocabulary.dictionary.domain.DictionarySearchResult
import com.example.localvocabulary.dictionary.domain.DictionaryUsagePolicy
import com.example.localvocabulary.dictionary.domain.DictionaryVocabularyImportMode
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry
import com.example.localvocabulary.dictionary.domain.ExternalDictionarySense
import com.example.localvocabulary.dictionary.domain.ProviderAvailability
import com.example.localvocabulary.dictionary.registry.DefaultDictionaryProviderRegistry
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WordEditorDictionarySuggestionsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `empty headword never searches providers`() = runTest {
        val provider = suggestionProvider()
        createViewModel(providers = listOf(provider))
        runCurrent()

        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(provider.queries.isEmpty())
    }

    @Test
    fun `headword search starts only after four hundred millisecond debounce`() = runTest {
        val provider = suggestionProvider()
        val viewModel = createViewModel(providers = listOf(provider))
        runCurrent()

        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS - 1)
        runCurrent()
        assertTrue(provider.queries.isEmpty())

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("你好"), provider.queries.map { it.text })
        assertEquals("你好", viewModel.uiState.value.dictionarySuggestionGroups.single().entries.single().headword)
    }

    @Test
    fun `same normalized query and language pair are not searched twice`() = runTest {
        val provider = suggestionProvider()
        val viewModel = createViewModel(providers = listOf(provider))
        runCurrent()

        viewModel.onAction(WordEditorAction.HeadwordChanged(" 你好 "))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf("你好"), provider.queries.map { it.text })
    }

    @Test
    fun `only newest query result reaches editor state`() = runTest {
        val provider = suggestionProvider { query ->
            if (query.text == "first") delay(1_000) else delay(10)
            success(query, meaning = "${query.text} meaning")
        }
        val viewModel = createViewModel(providers = listOf(provider))
        runCurrent()

        viewModel.onAction(WordEditorAction.HeadwordChanged("first"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        viewModel.onAction(WordEditorAction.HeadwordChanged("second"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        advanceTimeBy(10)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf("first", "second"), provider.queries.map { it.text })
        assertEquals("second", state.dictionarySuggestionGroups.single().entries.single().headword)
        assertEquals("second meaning", state.dictionarySuggestionGroups.single().entries.single().senses.single().meanings.single().text)
    }

    @Test
    fun `results from multiple providers remain grouped by provider`() = runTest {
        val alpha = suggestionProvider(id = "alpha", displayName = "Alpha Dictionary")
        val beta = suggestionProvider(id = "beta", displayName = "Beta Dictionary")
        val viewModel = createViewModel(providers = listOf(beta, alpha))
        runCurrent()

        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(
            listOf("Alpha Dictionary", "Beta Dictionary"),
            viewModel.uiState.value.dictionarySuggestionGroups.map { it.providerName },
        )
        assertTrue(viewModel.uiState.value.dictionarySuggestionGroups.all { it.entries.size == 1 })
    }

    @Test
    fun `one provider failure does not hide another provider result`() = runTest {
        val available = suggestionProvider(id = "available", displayName = "Available Dictionary")
        val unavailable = suggestionProvider(id = "unavailable", displayName = "Unavailable Dictionary") {
            DictionarySearchResult.Failure(DictionaryProviderError.LocalDatasetUnavailable)
        }
        val viewModel = createViewModel(providers = listOf(unavailable, available))
        runCurrent()

        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()

        val groups = viewModel.uiState.value.dictionarySuggestionGroups
        assertEquals(2, groups.size)
        assertEquals(1, groups.single { it.providerId.value == "available" }.entries.size)
        assertTrue(
            groups.single { it.providerId.value == "unavailable" }
                .message.orEmpty().contains("Unavailable Dictionary"),
        )
    }

    @Test
    fun `local dataset error stays in suggestions and manual save still succeeds`() = runTest {
        val provider = suggestionProvider {
            DictionarySearchResult.Failure(DictionaryProviderError.LocalDatasetUnavailable)
        }
        val repository = SuggestionVocabularyRepository()
        val viewModel = createViewModel(repository = repository, providers = listOf(provider))
        runCurrent()

        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()

        val errorState = viewModel.uiState.value
        assertTrue(errorState.dictionarySuggestionGroups.single().message.orEmpty().contains("Test Dictionary"))
        assertFalse(errorState.isLoading)
        assertNull(errorState.loadErrorMessage)

        viewModel.onAction(WordEditorAction.MeaningChanged(-1, "내가 작성한 뜻"))
        viewModel.onAction(WordEditorAction.Save)
        advanceUntilIdle()

        assertEquals("내가 작성한 뜻", repository.savedDrafts.single().senses.single().meaning)
        assertNull(viewModel.uiState.value.saveErrorMessage)
    }

    @Test
    fun `permitted suggestion seeds only allowed fields after explicit use`() = runTest {
        val provider = suggestionProvider(importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS)
        val viewModel = createViewModel(providers = listOf(provider))
        runCurrent()

        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        val result = viewModel.uiState.value.dictionarySuggestionGroups.single().entries.single()

        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))

        val state = viewModel.uiState.value
        assertEquals("provider meaning", state.senses.single().meaning)
        assertEquals("noun", state.senses.single().partOfSpeech)
        assertEquals("provider example", state.senses.single().examples.single().text)
        assertEquals("test.dictionary", state.senses.single().provenance?.providerId)
        assertFalse(state.senses.single().provenance!!.modifiedAfterImport)
        assertSame(result, state.dictionaryReference)
    }

    @Test
    fun `same provider result cannot be imported twice`() = runTest {
        val provider = suggestionProvider(importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS)
        val viewModel = createViewModel(providers = listOf(provider))
        runCurrent()
        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        val result = viewModel.uiState.value.dictionarySuggestionGroups.single().entries.single()

        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))
        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))

        assertEquals(1, viewModel.uiState.value.senses.size)
        assertEquals("provider meaning", viewModel.uiState.value.senses.single().meaning)
    }

    @Test
    fun `editing imported sense retains provenance and marks it modified`() = runTest {
        val provider = suggestionProvider(importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS)
        val repository = SuggestionVocabularyRepository()
        val viewModel = createViewModel(repository = repository, providers = listOf(provider))
        runCurrent()
        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        val result = viewModel.uiState.value.dictionarySuggestionGroups.single().entries.single()
        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))
        val importedKey = viewModel.uiState.value.senses.single().key

        viewModel.onAction(WordEditorAction.MeaningChanged(importedKey, "edited meaning"))
        viewModel.onAction(WordEditorAction.Save)
        advanceUntilIdle()

        val provenance = repository.savedDrafts.single().senses.single().provenance
        assertEquals("edited meaning", repository.savedDrafts.single().senses.single().meaning)
        assertEquals("test.dictionary", provenance?.providerId)
        assertTrue(provenance!!.modifiedAfterImport)
    }

    @Test
    fun `reference only suggestion remains transient while user draft can be saved`() = runTest {
        val provider = suggestionProvider(
            importMode = DictionaryVocabularyImportMode.REFERENCE_ONLY,
            permission = DictionaryPermission.PROHIBITED,
        )
        val repository = SuggestionVocabularyRepository()
        val viewModel = createViewModel(repository = repository, providers = listOf(provider))
        runCurrent()

        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        val result = viewModel.uiState.value.dictionarySuggestionGroups.single().entries.single()
        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))

        assertEquals("", viewModel.uiState.value.senses.single().meaning)
        assertSame(result, viewModel.uiState.value.dictionaryReference)

        viewModel.onAction(WordEditorAction.MeaningChanged(-1, "사용자가 쓴 뜻"))
        viewModel.onAction(WordEditorAction.Save)
        advanceUntilIdle()

        val saved = repository.savedDrafts.single()
        assertEquals("사용자가 쓴 뜻", saved.senses.single().meaning)
        assertFalse(saved.senses.single().meaning.contains("provider meaning"))
    }

    @Test
    fun `provider result refresh and explicit use never replace user edited senses`() = runTest {
        val provider = suggestionProvider(importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS)
        val viewModel = createViewModel(providers = listOf(provider))
        runCurrent()

        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        viewModel.onAction(WordEditorAction.MeaningChanged(-1, "my meaning"))
        viewModel.onAction(WordEditorAction.ExampleChanged(-1, -2, "my example"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        val result = viewModel.uiState.value.dictionarySuggestionGroups.single().entries.single()

        assertEquals("my meaning", viewModel.uiState.value.senses.single().meaning)
        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))

        assertEquals(2, viewModel.uiState.value.senses.size)
        val userSense = viewModel.uiState.value.senses.first { it.key == -1L }
        assertEquals("my meaning", userSense.meaning)
        assertEquals("my example", userSense.examples.single().text)
        assertEquals(null, userSense.provenance)

        viewModel.onAction(WordEditorAction.HeadwordChanged("您好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        val refreshedResult = viewModel.uiState.value.dictionarySuggestionGroups
            .single().entries.single()
        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(refreshedResult))

        assertEquals(listOf("你好", "您好"), provider.queries.map { it.text })
        assertEquals(3, viewModel.uiState.value.senses.size)
        val preservedUserSense = viewModel.uiState.value.senses.first { it.key == -1L }
        assertEquals("my meaning", preservedUserSense.meaning)
        assertEquals("my example", preservedUserSense.examples.single().text)
    }

    @Test
    fun `automatic search and explicit use do not replace an existing saved entry`() = runTest {
        val provider = suggestionProvider(importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS)
        val repository = SuggestionVocabularyRepository(initialEntry = savedEntry())
        val viewModel = createViewModel(
            repository = repository,
            providers = listOf(provider),
            savedStateHandle = SavedStateHandle(mapOf("entryId" to 7L)),
        )

        advanceUntilIdle()
        val result = viewModel.uiState.value.dictionarySuggestionGroups.single().entries.single()
        assertEquals("saved meaning", viewModel.uiState.value.senses.single().meaning)

        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))

        val state = viewModel.uiState.value
        assertEquals(2, state.senses.size)
        assertEquals("saved meaning", state.senses.first().meaning)
        assertEquals("saved example", state.senses.first().examples.single().text)
        assertEquals("provider meaning", state.senses.last().meaning)
        assertEquals("test.dictionary", state.senses.last().provenance?.providerId)
        assertEquals("saved note", state.notes)
        assertEquals(setOf(9L), state.selectedTagIds)
    }

    private fun createViewModel(
        repository: SuggestionVocabularyRepository = SuggestionVocabularyRepository(),
        providers: List<DictionaryProvider>,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = WordEditorViewModel(
        savedStateHandle = savedStateHandle,
        vocabularyRepository = repository,
        tagRepository = SuggestionTagRepository,
        settingsRepository = SuggestionSettingsRepository,
        dictionaryProviderRegistry = DefaultDictionaryProviderRegistry(providers),
        timeProvider = TimeProvider { 1_000L },
    )

    private fun suggestionProvider(
        id: String = "test.dictionary",
        displayName: String = "Test Dictionary",
        importMode: DictionaryVocabularyImportMode = DictionaryVocabularyImportMode.REFERENCE_ONLY,
        permission: DictionaryPermission = DictionaryPermission.PERMITTED,
        response: suspend RecordingSuggestionProvider.(DictionaryQuery) -> DictionarySearchResult = {
            success(it)
        },
    ) = RecordingSuggestionProvider(
        id = id,
        displayName = displayName,
        languagePair = CHINESE_TO_ENGLISH,
        importMode = importMode,
        permission = permission,
        response = response,
    )

    private companion object {
        val CHINESE_TO_ENGLISH = DictionaryLanguagePair(
            sourceLanguage = Bcp47LanguageTag.requireValid("zh-Hans"),
            resultLanguage = Bcp47LanguageTag.requireValid("en"),
            resultKind = DictionaryResultKind.TRANSLATION,
        )
    }
}

private class RecordingSuggestionProvider(
    id: String,
    displayName: String,
    languagePair: DictionaryLanguagePair,
    importMode: DictionaryVocabularyImportMode,
    permission: DictionaryPermission,
    private val response: suspend RecordingSuggestionProvider.(DictionaryQuery) -> DictionarySearchResult,
) : DictionaryProvider {
    val queries = mutableListOf<DictionaryQuery>()

    override val descriptor = DictionaryProviderDescriptor(
        id = DictionaryProviderId(id),
        displayName = displayName,
        supportedLanguagePairs = setOf(languagePair),
        access = DictionaryAccess.LOCAL_DATASET,
        capabilities = setOf(
            DictionaryCapability.EXACT_LOOKUP,
            DictionaryCapability.TRANSLATIONS,
            DictionaryCapability.EXAMPLE_SENTENCES,
            DictionaryCapability.PART_OF_SPEECH,
        ),
        attribution = DictionaryAttribution(
            sourceName = displayName,
            sourceUrl = "https://example.invalid/$id",
            officialIdentifier = id,
            licenseName = "Test fixture",
            licenseUrl = null,
            attributionNotice = "Test only",
            usagePolicy = DictionaryUsagePolicy(
                localPersistence = permission,
                redistribution = permission,
                cachePolicy = DictionaryCachePolicy.SESSION_ONLY,
                vocabularyImportMode = importMode,
                note = "Test only",
            ),
        ),
    )

    override suspend fun search(query: DictionaryQuery): DictionarySearchResult {
        queries += query
        return response(query)
    }

    override suspend fun checkAvailability(): ProviderAvailability = ProviderAvailability.Available

    fun success(
        query: DictionaryQuery,
        meaning: String = "provider meaning",
    ): DictionarySearchResult = DictionarySearchResult.Success(
        DictionarySearchPage(
            entries = listOf(
                ExternalDictionaryEntry(
                    providerId = descriptor.id,
                    sourceEntryId = "${descriptor.id.value}:${query.text}",
                    headword = query.text,
                    sourceLanguage = query.languagePair.sourceLanguage,
                    senses = listOf(
                        ExternalDictionarySense(
                            meanings = listOf(
                                DictionaryMeaning(
                                    text = meaning,
                                    language = query.languagePair.resultLanguage,
                                    kind = query.languagePair.resultKind,
                                ),
                            ),
                            partOfSpeech = "noun",
                            examples = listOf(
                                DictionaryExample(
                                    text = "provider example",
                                    language = query.languagePair.sourceLanguage,
                                ),
                            ),
                        ),
                    ),
                    attribution = descriptor.attribution,
                ),
            ),
        ),
    )
}

private class SuggestionVocabularyRepository(
    initialEntry: VocabularyEntry? = null,
) : VocabularyRepository {
    private val entry = MutableStateFlow(initialEntry)
    val savedDrafts = mutableListOf<ValidatedVocabularyDraft>()

    override fun observeEntries(query: String, tagId: Long?): Flow<List<VocabularyEntry>> =
        flowOf(entry.value?.let(::listOf).orEmpty())

    override fun observeEntry(id: Long): Flow<VocabularyEntry?> = entry

    override suspend fun save(draft: ValidatedVocabularyDraft): Long {
        savedDrafts += draft
        return draft.id ?: 1L
    }

    override suspend fun delete(id: Long) = Unit
}

private object SuggestionTagRepository : TagRepository {
    override fun observeTags(): Flow<List<VocabularyTag>> = flowOf(emptyList())
    override suspend fun save(id: Long?, name: String): SaveTagResult = SaveTagResult.Saved(id ?: 1L)
    override suspend fun delete(id: Long) = Unit
}

private object SuggestionSettingsRepository : SettingsRepository {
    override val settings: Flow<AppSettings> = flowOf(AppSettings(defaultLanguageTag = "zh-Hans"))
    override suspend fun setDefaultLanguageTag(languageTag: String) = Unit
}

private fun savedEntry() = VocabularyEntry(
    id = 7,
    backupId = "saved-entry",
    headword = "你好",
    languageTag = "zh-Hans",
    senses = listOf(
        VocabularySense(
            id = 70,
            meaning = "saved meaning",
            partOfSpeech = "expression",
            examples = listOf(ExampleSentence(700, "saved example")),
        ),
    ),
    notes = "saved note",
    tags = listOf(VocabularyTag(9, "saved-tag", "Saved")),
    createdAtEpochMillis = 100,
    modifiedAtEpochMillis = 200,
)
