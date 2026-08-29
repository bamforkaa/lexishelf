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
import com.example.localvocabulary.dictionary.domain.DictionaryLinguisticFeatures
import com.example.localvocabulary.dictionary.domain.DictionaryMeaning
import com.example.localvocabulary.dictionary.domain.DictionaryLemmaCandidate
import com.example.localvocabulary.dictionary.domain.DictionaryMorphologyQuery
import com.example.localvocabulary.dictionary.domain.DictionaryMorphologyResolver
import com.example.localvocabulary.dictionary.domain.DictionaryMorphologyResult
import com.example.localvocabulary.dictionary.domain.DictionaryPermission
import com.example.localvocabulary.dictionary.domain.DictionaryProvider
import com.example.localvocabulary.dictionary.domain.DictionaryProviderDescriptor
import com.example.localvocabulary.dictionary.domain.DictionaryProviderError
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.DictionaryQuery
import com.example.localvocabulary.dictionary.domain.DictionaryReading
import com.example.localvocabulary.dictionary.domain.DictionaryPronunciation
import com.example.localvocabulary.dictionary.domain.DictionaryPronunciationNotation
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
import com.example.localvocabulary.vocabulary.domain.DictionaryProvenance
import com.example.localvocabulary.vocabulary.domain.ImportedDictionaryField
import com.example.localvocabulary.vocabulary.domain.GrammaticalGenderCategory
import com.example.localvocabulary.vocabulary.domain.PronunciationNotation
import com.example.localvocabulary.vocabulary.domain.SaveTagResult
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.ValidatedVocabularyDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularyGrammaticalGender
import com.example.localvocabulary.vocabulary.domain.VocabularyPronunciation
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import com.example.localvocabulary.vocabulary.domain.VocabularySense
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    fun `source-attested morphology re-queries exact providers once and keeps surface headword`() =
        runTest {
            val provider = suggestionProvider(
                importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
            ) { query ->
                if (query.text == "surface") {
                    DictionarySearchResult.Failure(DictionaryProviderError.NoResult)
                } else {
                    success(query, meaning = "lemma meaning")
                }
            }
            val resolver = RecordingMorphologyResolver("surface", "lemma", "zh-Hans")
            val viewModel = createViewModel(
                providers = listOf(provider),
                morphologyResolver = resolver,
            )
            runCurrent()

            viewModel.onAction(WordEditorAction.HeadwordChanged("surface"))
            advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
            advanceUntilIdle()

            val candidate = viewModel.uiState.value.synthesizedSuggestionGroups
                .flatMap { it.candidates }
                .single()
            assertEquals("surface", candidate.morphologyContext?.surface)
            assertEquals("lemma", candidate.morphologyContext?.lemma)
            assertEquals(listOf("surface", "lemma"), provider.queries.map { it.text })
            assertEquals(1, resolver.queries.size)

            viewModel.onAction(
                WordEditorAction.DictionarySuggestionSelected(candidate.primaryEntry),
            )

            assertEquals("surface", viewModel.uiState.value.headword)
            assertEquals("lemma meaning", viewModel.uiState.value.senses.single().meaning)
        }

    @Test
    fun `direct exact candidate remains before lemma-derived candidate`() = runTest {
        val provider = suggestionProvider { query ->
            success(query, meaning = if (query.text == "surface") "direct" else "derived")
        }
        val viewModel = createViewModel(
            providers = listOf(provider),
            morphologyResolver = RecordingMorphologyResolver("surface", "lemma", "zh-Hans"),
        )
        runCurrent()

        viewModel.onAction(WordEditorAction.HeadwordChanged("surface"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        advanceUntilIdle()

        val candidates = viewModel.uiState.value.synthesizedSuggestionGroups
            .flatMap { it.candidates }
        assertEquals(listOf("direct", "derived"), candidates.map { it.displayMeaning })
        assertNull(candidates.first().morphologyContext)
        assertEquals("lemma", candidates.last().morphologyContext?.lemma)
    }

    @Test
    fun `valid morphology ambiguity re-queries every provider once and keeps primary first`() =
        runTest {
            val kaikki = suggestionProvider(id = "kaikki", displayName = "Kaikki") { query ->
                if (query.text == "surface") {
                    DictionarySearchResult.Failure(DictionaryProviderError.NoResult)
                } else {
                    success(query, meaning = "Kaikki ${query.text}")
                }
            }
            val panLex = suggestionProvider(id = "panlex", displayName = "PanLex") { query ->
                if (query.text == "surface") {
                    DictionarySearchResult.Failure(DictionaryProviderError.NoResult)
                } else {
                    success(query, meaning = "PanLex ${query.text}")
                }
            }
            val resolver = RecordingMorphologyResolver(
                surface = "surface",
                lemmas = listOf("primary", "alternate"),
                languageTag = "zh-Hans",
            )
            val viewModel = createViewModel(
                providers = listOf(panLex, kaikki),
                morphologyResolver = resolver,
            )
            runCurrent()

            viewModel.onAction(WordEditorAction.HeadwordChanged("surface"))
            advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
            advanceUntilIdle()

            val contexts = viewModel.uiState.value.synthesizedSuggestionGroups
                .flatMap { it.candidates }
                .mapNotNull { it.morphologyContext }
            assertEquals(4, contexts.size)
            assertTrue(contexts.take(2).all { it.role == MorphologyAnalysisRole.PRIMARY })
            assertTrue(contexts.drop(2).all { it.role == MorphologyAnalysisRole.ALTERNATE })
            assertEquals(
                listOf("surface", "primary", "alternate"),
                kaikki.queries.map { it.text },
            )
            assertEquals(
                listOf("surface", "primary", "alternate"),
                panLex.queries.map { it.text },
            )
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
    fun `identical multi provider candidate imports one deterministic primary contribution`() = runTest {
        val panLex = suggestionProvider(
            id = "panlex",
            displayName = "PanLex",
            importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
        )
        val koreanBasic = suggestionProvider(
            id = "korean-basic-dictionary",
            displayName = "한국어기초사전",
            importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
        )
        val viewModel = createViewModel(providers = listOf(panLex, koreanBasic))
        runCurrent()

        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()

        val candidate = viewModel.uiState.value.synthesizedSuggestionGroups
            .single().candidates.single()
        assertEquals(
            listOf("한국어기초사전", "PanLex"),
            candidate.sources.map { it.providerName },
        )
        assertEquals("korean-basic-dictionary", candidate.primarySource.providerId.value)

        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(candidate.primaryEntry))

        val imported = viewModel.uiState.value
        assertEquals(listOf("provider meaning"), imported.senses.map { it.meaning }.filter(String::isNotBlank))
        assertEquals("korean-basic-dictionary", imported.senses.single().provenance?.providerId)
        assertEquals(setOf(candidate.key), imported.selectedSuggestionKeys)
    }

    @Test
    fun `Kaikki example richness selects Kaikki provenance through existing import pipeline`() = runTest {
        val fallback = suggestionProvider(
            id = "fallback",
            displayName = "Fallback",
            importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
        ) { success(it, includeExample = false) }
        val kaikki = suggestionProvider(
            id = "kaikki",
            displayName = "Kaikki / Wiktionary",
            importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
        ) { success(it, includeLinguisticMetadata = true) }
        val viewModel = createViewModel(providers = listOf(fallback, kaikki))
        runCurrent()

        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()

        val candidate = viewModel.uiState.value.synthesizedSuggestionGroups
            .single().candidates.single()
        assertEquals("kaikki", candidate.primarySource.providerId.value)
        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(candidate.primaryEntry))

        val importedSense = viewModel.uiState.value.senses.single()
        assertEquals(listOf("provider example"), importedSense.examples.map { it.text })
        assertEquals("kaikki", importedSense.provenance?.providerId)
        assertTrue(ImportedDictionaryField.EXAMPLES in importedSense.provenance!!.importedFields)
        assertEquals("neuter", importedSense.grammaticalGender)
        assertEquals("/test/", viewModel.uiState.value.pronunciations.single().value)
        assertEquals(
            PronunciationNotation.IPA,
            viewModel.uiState.value.pronunciations.single().notation,
        )
    }

    @Test
    fun `unchanged pronunciation and gender are removed on second suggestion tap`() = runTest {
        val provider = suggestionProvider(
            id = "kaikki",
            importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
        ) { success(it, includeLinguisticMetadata = true) }
        val viewModel = createViewModel(providers = listOf(provider))
        runCurrent()
        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        val result = viewModel.uiState.value.dictionarySuggestionGroups.single().entries.single()

        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))
        assertEquals(1, viewModel.uiState.value.pronunciations.size)
        assertEquals("neuter", viewModel.uiState.value.senses.single().grammaticalGender)
        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))

        assertTrue(viewModel.uiState.value.pronunciations.isEmpty())
        assertEquals("", viewModel.uiState.value.senses.single().grammaticalGender)
        assertFalse(viewModel.uiState.value.senses.single().isGrammaticalGenderVisible)
    }

    @Test
    fun `headword cleanup preserves user edited provider pronunciation and gender`() = runTest {
        val provider = suggestionProvider(
            id = "kaikki",
            importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
        ) { success(it, includeLinguisticMetadata = true) }
        val viewModel = createViewModel(providers = listOf(provider))
        runCurrent()
        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        val result = viewModel.uiState.value.dictionarySuggestionGroups.single().entries.single()
        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))
        val pronunciationKey = viewModel.uiState.value.pronunciations.single().key
        val senseKey = viewModel.uiState.value.senses.single().key
        viewModel.onAction(
            WordEditorAction.PronunciationValueChanged(pronunciationKey, "/user/"),
        )
        viewModel.onAction(WordEditorAction.GrammaticalGenderChanged(senseKey, "common"))

        viewModel.onAction(WordEditorAction.HeadwordChanged("再见"))

        val state = viewModel.uiState.value
        assertEquals("/user/", state.pronunciations.single().value)
        assertNull(state.pronunciations.single().provenance)
        assertEquals("common", state.senses.single().grammaticalGender)
        assertNull(state.senses.single().provenance)
    }

    @Test
    fun `Japanese Korean and English results are searched together and Korean stays first`() = runTest {
        val korean = RecordingSuggestionProvider(
            id = "korean-basic-dictionary",
            displayName = "Korean Basic Dictionary",
            languagePair = JAPANESE_TO_KOREAN,
            importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
            permission = DictionaryPermission.PERMITTED,
        ) { success(it, meaning = "먹다") }
        val jmdict = RecordingSuggestionProvider(
            id = "jmdict",
            displayName = "JMdict",
            languagePair = JAPANESE_TO_ENGLISH,
            importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
            permission = DictionaryPermission.PERMITTED,
        ) { success(it, meaning = "to eat") }
        val viewModel = createViewModel(
            providers = listOf(korean, jmdict),
            settingsRepository = FixedSettingsRepository("ja"),
        )
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(
            setOf(JAPANESE_TO_KOREAN, JAPANESE_TO_ENGLISH),
            state.dictionaryLanguagePairs.toSet(),
        )
        assertEquals(
            listOf(JAPANESE_TO_KOREAN, JAPANESE_TO_ENGLISH),
            state.dictionaryLanguagePairs,
        )
        viewModel.onAction(WordEditorAction.HeadwordChanged("食べる"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(listOf(JAPANESE_TO_KOREAN), korean.queries.map { it.languagePair })
        assertEquals(listOf(JAPANESE_TO_ENGLISH), jmdict.queries.map { it.languagePair })
        assertEquals(
            listOf("ko", "en"),
            viewModel.uiState.value.synthesizedSuggestionGroups.map { it.resultLanguage?.value },
        )
    }

    @Test
    fun `English remains available when Korean result provider is absent`() = runTest {
        val jmdict = RecordingSuggestionProvider(
            id = "jmdict",
            displayName = "JMdict",
            languagePair = JAPANESE_TO_ENGLISH,
            importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
            permission = DictionaryPermission.PERMITTED,
        ) { success(it, meaning = "to eat") }
        val viewModel = createViewModel(
            providers = listOf(jmdict),
            settingsRepository = FixedSettingsRepository("ja"),
        )
        runCurrent()

        assertEquals(
            listOf(JAPANESE_TO_ENGLISH),
            viewModel.uiState.value.dictionaryLanguagePairs,
        )
    }

    @Test
    fun `provider query uses bounded common result limit`() = runTest {
        val provider = suggestionProvider()
        val viewModel = createViewModel(providers = listOf(provider))
        runCurrent()
        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()

        assertEquals(20, provider.queries.single().resultLimit)
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
        val synthesized = viewModel.uiState.value.synthesizedSuggestionGroups.single()
        assertEquals(1, synthesized.candidates.size)
        assertEquals("available", synthesized.candidates.single().primarySource.providerId.value)
        assertTrue(synthesized.messages.single().text.contains("Unavailable Dictionary"))
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
        assertEquals("provider reading", state.reading)
        assertEquals("test.dictionary", state.readingProvenance?.providerId)
        assertEquals(
            setOf(com.example.localvocabulary.vocabulary.domain.ImportedDictionaryField.READING),
            state.readingProvenance?.importedFields,
        )
        assertEquals("test.dictionary", state.senses.single().provenance?.providerId)
        assertFalse(state.senses.single().provenance!!.modifiedAfterImport)
        assertEquals(result, state.dictionaryReference)
    }

    @Test
    fun `second tap deselects suggestion and removes only its unchanged contribution`() = runTest {
        val provider = suggestionProvider(importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS)
        val viewModel = createViewModel(providers = listOf(provider))
        runCurrent()
        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        val result = viewModel.uiState.value.dictionarySuggestionGroups.single().entries.single()

        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))
        assertTrue(viewModel.uiState.value.selectedSuggestionKeys.isNotEmpty())
        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))

        assertEquals(1, viewModel.uiState.value.senses.size)
        assertEquals("", viewModel.uiState.value.senses.single().meaning)
        assertEquals("", viewModel.uiState.value.reading)
        assertTrue(viewModel.uiState.value.selectedSuggestionKeys.isEmpty())
    }

    @Test
    fun `clearing headword removes session imported contribution`() = runTest {
        val provider = suggestionProvider(
            importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
        )
        val viewModel = createViewModel(providers = listOf(provider))
        runCurrent()
        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        val result = viewModel.uiState.value.dictionarySuggestionGroups.single().entries.single()
        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))

        viewModel.onAction(WordEditorAction.HeadwordChanged(""))
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("", state.senses.single().meaning)
        assertEquals("", state.reading)
        assertTrue(state.selectedSuggestionKeys.isEmpty())
        assertTrue(state.dictionarySuggestionGroups.isEmpty())
    }

    @Test
    fun `headword replacement removes old provider fields but preserves manual draft`() = runTest {
        val provider = suggestionProvider(
            importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
        )
        val viewModel = createViewModel(providers = listOf(provider))
        runCurrent()
        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        viewModel.onAction(WordEditorAction.MeaningChanged(-1, "내 뜻"))
        viewModel.onAction(WordEditorAction.ExampleChanged(-1, -2, "내 예문"))
        viewModel.onAction(WordEditorAction.NotesChanged("내 메모"))
        viewModel.onAction(WordEditorAction.TagToggled(9L))
        viewModel.onAction(WordEditorAction.WordbookToggled(10L))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        val result = viewModel.uiState.value.dictionarySuggestionGroups.single().entries.single()
        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))

        viewModel.onAction(WordEditorAction.HeadwordChanged("您好"))

        val state = viewModel.uiState.value
        assertEquals(listOf("내 뜻"), state.senses.map { it.meaning })
        assertEquals("내 예문", state.senses.single().examples.single().text)
        assertEquals("내 메모", state.notes)
        assertEquals(setOf(9L), state.selectedTagIds)
        assertEquals(setOf(10L), state.selectedWordbookIds)
        assertEquals("", state.reading)
        assertTrue(state.selectedSuggestionKeys.isEmpty())
    }

    @Test
    fun `edited imported example survives cleanup without stale provider meaning`() = runTest {
        val provider = suggestionProvider(
            importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
        )
        val viewModel = createViewModel(providers = listOf(provider))
        runCurrent()
        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        val result = viewModel.uiState.value.dictionarySuggestionGroups.single().entries.single()
        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))
        val importedSense = viewModel.uiState.value.senses.single()
        viewModel.onAction(
            WordEditorAction.ExampleChanged(
                importedSense.key,
                importedSense.examples.single().key,
                "사용자가 고친 예문",
            ),
        )

        viewModel.onAction(WordEditorAction.HeadwordChanged("您好"))

        val retained = viewModel.uiState.value.senses.single()
        assertEquals("", retained.meaning)
        assertEquals("", retained.partOfSpeech)
        assertEquals("사용자가 고친 예문", retained.examples.single().text)
        assertNull(retained.provenance)
    }

    @Test
    fun `deselecting edited imported sense preserves user content and clears provenance`() = runTest {
        val provider = suggestionProvider(importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS)
        val viewModel = createViewModel(providers = listOf(provider))
        runCurrent()
        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        val result = viewModel.uiState.value.dictionarySuggestionGroups.single().entries.single()
        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))
        val senseKey = viewModel.uiState.value.senses.single().key
        viewModel.onAction(WordEditorAction.MeaningChanged(senseKey, "사용자가 수정한 뜻"))

        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))

        val sense = viewModel.uiState.value.senses.single()
        assertEquals("사용자가 수정한 뜻", sense.meaning)
        assertNull(sense.provenance)
        assertTrue(viewModel.uiState.value.selectedSuggestionKeys.isEmpty())
    }

    @Test
    fun `shared imported reading remains until its last selected suggestion is removed`() = runTest {
        val first = suggestionProvider(
            id = "first.dictionary",
            importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
        )
        val second = suggestionProvider(
            id = "second.dictionary",
            importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
        ) { success(it, meaning = "second provider meaning") }
        val viewModel = createViewModel(providers = listOf(first, second))
        runCurrent()
        viewModel.onAction(WordEditorAction.HeadwordChanged("你好"))
        advanceTimeBy(WordEditorViewModel.DICTIONARY_SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        val results = viewModel.uiState.value.dictionarySuggestionGroups.map { it.entries.single() }
        results.forEach {
            viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(it))
        }

        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(results.first()))
        assertEquals("provider reading", viewModel.uiState.value.reading)

        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(results.last()))
        assertEquals("", viewModel.uiState.value.reading)
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
        assertEquals(result, viewModel.uiState.value.dictionaryReference)

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
        viewModel.onAction(WordEditorAction.ReadingChanged("my reading"))

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
        assertEquals(2, viewModel.uiState.value.senses.size)
        val preservedUserSense = viewModel.uiState.value.senses.first { it.key == -1L }
        assertEquals("my meaning", preservedUserSense.meaning)
        assertEquals("my example", preservedUserSense.examples.single().text)
        assertEquals("my reading", viewModel.uiState.value.reading)
        assertNull(viewModel.uiState.value.readingProvenance)
    }

    @Test
    fun `headword edit of existing entry changes draft only and preserves stored aggregate`() = runTest {
        val stored = savedEntry().let { entry ->
            entry.copy(
                senses = entry.senses.map { sense ->
                    sense.copy(
                        provenance = testProvenance(
                            sourceEntryId = "test.dictionary:${entry.headword}",
                            sourceSenseId = "0",
                            importedFields = setOf(
                                ImportedDictionaryField.MEANING,
                                ImportedDictionaryField.EXAMPLES,
                            ),
                        ),
                    )
                },
            )
        }
        val repository = SuggestionVocabularyRepository(initialEntry = stored)
        val viewModel = createViewModel(
            repository = repository,
            providers = listOf(
                suggestionProvider(
                    importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
                ),
            ),
            savedStateHandle = SavedStateHandle(mapOf("entryId" to stored.id)),
        )
        advanceUntilIdle()

        viewModel.onAction(WordEditorAction.HeadwordChanged("您好"))

        assertEquals("saved meaning", viewModel.uiState.value.senses.single().meaning)
        assertEquals("saved example", viewModel.uiState.value.senses.single().examples.single().text)
        assertEquals("test.dictionary", viewModel.uiState.value.senses.single().provenance?.providerId)
        assertEquals("你好", repository.observeEntry(stored.id).first()!!.headword)
        assertTrue(repository.savedDrafts.isEmpty())
    }

    @Test
    fun `automatic search and explicit use do not replace an existing saved entry`() = runTest {
        val provider = suggestionProvider(
            importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
        ) { success(it, includeLinguisticMetadata = true) }
        val saved = savedEntry().copy(
            pronunciations = listOf(
                VocabularyPronunciation(
                    id = 80,
                    stableId = "manual-pronunciation",
                    notation = PronunciationNotation.IPA,
                    value = "/manual/",
                    languageTag = "zh-Hans",
                ),
            ),
            senses = savedEntry().senses.map {
                it.copy(
                    grammaticalGender = VocabularyGrammaticalGender(
                        GrammaticalGenderCategory.COMMON,
                    ),
                )
            },
        )
        val repository = SuggestionVocabularyRepository(initialEntry = saved)
        val viewModel = createViewModel(
            repository = repository,
            providers = listOf(provider),
            savedStateHandle = SavedStateHandle(mapOf("entryId" to 7L)),
        )

        advanceUntilIdle()
        val result = viewModel.uiState.value.dictionarySuggestionGroups.single().entries.single()
        assertEquals("saved meaning", viewModel.uiState.value.senses.single().meaning)
        assertEquals("user reading", viewModel.uiState.value.reading)
        assertEquals(listOf("/manual/"), viewModel.uiState.value.pronunciations.map { it.value })
        assertEquals("common", viewModel.uiState.value.senses.single().grammaticalGender)

        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))

        val state = viewModel.uiState.value
        assertEquals(2, state.senses.size)
        assertEquals("saved meaning", state.senses.first().meaning)
        assertEquals("saved example", state.senses.first().examples.single().text)
        assertEquals("provider meaning", state.senses.last().meaning)
        assertEquals("test.dictionary", state.senses.last().provenance?.providerId)
        assertEquals("saved note", state.notes)
        assertEquals("user reading", state.reading)
        assertEquals(listOf("/manual/", "/test/"), state.pronunciations.map { it.value })
        assertEquals("common", state.senses.first().grammaticalGender)
        assertEquals(setOf(9L), state.selectedTagIds)
    }

    @Test
    fun `existing provider provenance restores toggle ownership without overwriting content`() = runTest {
        val provider = suggestionProvider(importMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS)
        val original = savedEntry()
        val sourceEntryId = "test.dictionary:${original.headword}"
        val repository = SuggestionVocabularyRepository(
            initialEntry = original.copy(
                reading = "provider reading",
                readingProvenance = testProvenance(
                    sourceEntryId = sourceEntryId,
                    sourceSenseId = null,
                    importedFields = setOf(ImportedDictionaryField.READING),
                ),
                senses = listOf(
                    original.senses.single().copy(
                        meaning = "provider meaning",
                        partOfSpeech = "noun",
                        examples = listOf(ExampleSentence(700, "provider example")),
                        provenance = testProvenance(
                            sourceEntryId = sourceEntryId,
                            sourceSenseId = "0",
                            importedFields = setOf(
                                ImportedDictionaryField.MEANING,
                                ImportedDictionaryField.PART_OF_SPEECH,
                                ImportedDictionaryField.EXAMPLES,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val viewModel = createViewModel(
            repository = repository,
            providers = listOf(provider),
            savedStateHandle = SavedStateHandle(mapOf("entryId" to 7L)),
        )

        advanceUntilIdle()
        val result = viewModel.uiState.value.dictionarySuggestionGroups.single().entries.single()
        val selectedState = viewModel.uiState.value
        val synthesizedKey = selectedState.synthesizedSuggestionGroups.single().candidates.single().key
        assertEquals(setOf(synthesizedKey), selectedState.selectedSuggestionKeys)
        assertEquals(synthesizedKey, selectedState.senses.single().importSuggestionKey)
        assertEquals("provider meaning", selectedState.senses.single().meaning)
        assertEquals("saved note", selectedState.notes)

        viewModel.onAction(WordEditorAction.DictionarySuggestionSelected(result))

        val deselectedState = viewModel.uiState.value
        assertTrue(deselectedState.selectedSuggestionKeys.isEmpty())
        assertEquals("", deselectedState.senses.single().meaning)
        assertEquals("", deselectedState.reading)
        assertEquals("saved note", deselectedState.notes)
    }

    private fun createViewModel(
        repository: SuggestionVocabularyRepository = SuggestionVocabularyRepository(),
        providers: List<DictionaryProvider>,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        settingsRepository: SettingsRepository = SuggestionSettingsRepository,
        morphologyResolver: DictionaryMorphologyResolver? = null,
    ) = WordEditorViewModel(
        savedStateHandle = savedStateHandle,
        vocabularyRepository = repository,
        tagRepository = SuggestionTagRepository,
        settingsRepository = settingsRepository,
        dictionaryProviderRegistry = DefaultDictionaryProviderRegistry(providers),
        dictionaryMorphologyResolver = morphologyResolver
            ?: com.example.localvocabulary.dictionary.domain.NoOpDictionaryMorphologyResolver,
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
        val JAPANESE_TO_KOREAN = DictionaryLanguagePair(
            sourceLanguage = Bcp47LanguageTag.requireValid("ja"),
            resultLanguage = Bcp47LanguageTag.requireValid("ko"),
            resultKind = DictionaryResultKind.TRANSLATION,
        )
        val JAPANESE_TO_ENGLISH = DictionaryLanguagePair(
            sourceLanguage = Bcp47LanguageTag.requireValid("ja"),
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
            DictionaryCapability.READING,
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

    override suspend fun exactLookup(query: DictionaryQuery): DictionarySearchResult {
        queries += query
        return response(query)
    }

    override suspend fun checkAvailability(): ProviderAvailability = ProviderAvailability.Available

    fun success(
        query: DictionaryQuery,
        meaning: String = "provider meaning",
        includeExample: Boolean = true,
        includeLinguisticMetadata: Boolean = false,
    ): DictionarySearchResult = DictionarySearchResult.Success(
        DictionarySearchPage(
            entries = listOf(
                ExternalDictionaryEntry(
                    providerId = descriptor.id,
                    sourceEntryId = "${descriptor.id.value}:${query.text}",
                    headword = query.text,
                    sourceLanguage = query.languagePair.sourceLanguage,
                    linguisticFeatures = DictionaryLinguisticFeatures(
                        reading = DictionaryReading("provider reading"),
                        pronunciations = if (includeLinguisticMetadata) {
                            listOf(
                                DictionaryPronunciation(
                                    text = "/test/",
                                    notation = DictionaryPronunciationNotation.IPA,
                                    language = query.languagePair.sourceLanguage,
                                ),
                            )
                        } else {
                            emptyList()
                        },
                    ),
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
                            grammaticalGender = "neuter".takeIf {
                                includeLinguisticMetadata
                            },
                            examples = if (includeExample) {
                                listOf(
                                    DictionaryExample(
                                        text = "provider example",
                                        language = query.languagePair.sourceLanguage,
                                    ),
                                )
                            } else {
                                emptyList()
                            },
                        ),
                    ),
                    attribution = descriptor.attribution,
                ),
            ),
        ),
    )
}

private class RecordingMorphologyResolver(
    private val surface: String,
    private val lemmas: List<String>,
    languageTag: String,
) : DictionaryMorphologyResolver {
    constructor(surface: String, lemma: String, languageTag: String) : this(
        surface,
        listOf(lemma),
        languageTag,
    )

    override val providerId = DictionaryProviderId("kaikki")
    override val displayName = "Kaikki / Wiktionary"
    override val supportedSourceLanguages = setOf(Bcp47LanguageTag.requireValid(languageTag))
    val queries = mutableListOf<DictionaryMorphologyQuery>()

    override suspend fun resolve(query: DictionaryMorphologyQuery): DictionaryMorphologyResult {
        queries += query
        return if (query.surface == surface) {
            DictionaryMorphologyResult.Resolved(
                lemmas.mapIndexed { index, lemma ->
                    DictionaryLemmaCandidate(
                        surface = surface,
                        lemma = lemma,
                        sourceLanguage = query.sourceLanguage,
                        resolverProviderId = providerId,
                        sourceEntryId = "morph-$index",
                    )
                },
            )
        } else {
            DictionaryMorphologyResult.NoResult
        }
    }
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
    override suspend fun addUserLanguageTag(languageTag: String) = Unit
}

private class FixedSettingsRepository(defaultLanguageTag: String) : SettingsRepository {
    override val settings: Flow<AppSettings> = flowOf(
        AppSettings(defaultLanguageTag = defaultLanguageTag),
    )

    override suspend fun setDefaultLanguageTag(languageTag: String) = Unit
    override suspend fun addUserLanguageTag(languageTag: String) = Unit
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
    reading = "user reading",
)

private fun testProvenance(
    sourceEntryId: String,
    sourceSenseId: String?,
    importedFields: Set<ImportedDictionaryField>,
) = DictionaryProvenance(
    providerId = "test.dictionary",
    sourceEntryId = sourceEntryId,
    sourceSenseId = sourceSenseId,
    sourceName = "Test Dictionary",
    sourceUrl = "https://example.invalid/test.dictionary",
    licenseName = "Test fixture",
    licenseUrl = null,
    datasetVersion = null,
    importedFields = importedFields,
    importedAtEpochMillis = 1_000L,
    modifiedAfterImport = false,
)
