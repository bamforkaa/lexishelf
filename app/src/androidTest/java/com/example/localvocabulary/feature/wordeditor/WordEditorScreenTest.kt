package com.example.localvocabulary.feature.wordeditor

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.localvocabulary.core.model.LanguageDisplayNameResolver
import com.example.localvocabulary.core.ui.theme.LocalVocabularyTheme
import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.DictionaryAttribution
import com.example.localvocabulary.dictionary.domain.DictionaryLinguisticFeatures
import com.example.localvocabulary.dictionary.domain.DictionaryInflection
import com.example.localvocabulary.dictionary.domain.DictionaryMeaning
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.DictionaryReading
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry
import com.example.localvocabulary.dictionary.domain.ExternalDictionarySense
import com.example.localvocabulary.dictionary.reference.ExternalDictionaryReference
import com.example.localvocabulary.vocabulary.domain.DictionaryProvenance
import com.example.localvocabulary.vocabulary.domain.ImportedDictionaryField
import com.example.localvocabulary.vocabulary.domain.VocabularyValidationError
import com.example.localvocabulary.vocabulary.domain.PronunciationNotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WordEditorScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missingHeadwordValidationIsVisible() {
        composeRule.setContent {
            LocalVocabularyTheme {
                WordEditorScreen(
                    state = WordEditorUiState(
                        isLoading = false,
                        validationError = VocabularyValidationError.MissingHeadword,
                    ),
                    onAction = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("validation_error"))
        composeRule.onNodeWithTag("validation_error").assertIsDisplayed()
    }

    @Test
    fun dictionarySuggestionsDisplayResultLanguageAndSourceMetadata() {
        val groups = listOf(
            suggestionGroup("alpha", "Alpha Dictionary", "你好"),
            suggestionGroup("beta", "Beta Dictionary", "您好"),
        )
        composeRule.setContent {
            LocalVocabularyTheme {
                DictionarySuggestionSection(
                    state = suggestionState(groups),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithTag("dictionary_suggestions").assertIsDisplayed()
        composeRule.onNodeWithText(LanguageDisplayNameResolver.resolve("en").name).assertIsDisplayed()
        composeRule.onNodeWithText("Alpha Dictionary").assertIsDisplayed()
        composeRule.onNodeWithText("你好").assertIsDisplayed()
        composeRule.onNodeWithText("Beta Dictionary").assertIsDisplayed()
        composeRule.onNodeWithText("您好").assertIsDisplayed()
    }

    @Test
    fun suggestionDisplaysCompactSourceWithoutRepeatingLicense() {
        val group = suggestionGroup(
            providerId = "cc-cedict",
            providerName = "CC-CEDICT",
            headword = "你好",
            licenseShortName = "CC BY-SA 4.0",
        )
        composeRule.setContent {
            LocalVocabularyTheme {
                DictionarySuggestionSection(
                    state = suggestionState(listOf(group)),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("CC-CEDICT").assertIsDisplayed()
        composeRule.onAllNodesWithText("CC BY-SA 4.0", substring = true).assertCountEquals(0)
    }

    @Test
    fun suggestionDisplaysOnlyProvidedTransientRepresentativeForms() {
        val group = suggestionGroup("kaikki", "Kaikki / Wiktionary", "gehen")
        val entry = group.entries.single().copy(
            linguisticFeatures = DictionaryLinguisticFeatures(
                inflections = listOf(
                    DictionaryInflection("geht", "present 3sg"),
                    DictionaryInflection("ging", "past"),
                    DictionaryInflection("gegangen", "past participle"),
                ),
                totalInflectionCount = 111,
            ),
        )

        setSuggestionContent(listOf(group.copy(entries = listOf(entry))))

        composeRule.onNodeWithTag(
            "dictionary_inflection_section",
            useUnmergedTree = true,
        )
            .assert(hasTestTag("dictionary_inflection_section"))
        composeRule.onAllNodesWithTag(
            "dictionary_inflection_row",
            useUnmergedTree = true,
        ).assertCountEquals(3)
        composeRule.onNodeWithText("present 3sg", useUnmergedTree = true)
            .assert(hasText("present 3sg"))
        composeRule.onNodeWithText("gegangen", useUnmergedTree = true)
            .assert(hasText("gegangen"))
        composeRule.onNodeWithText(
            "원본 활용형 111",
            substring = true,
            useUnmergedTree = true,
        )
            .assert(hasText("원본 활용형 111", substring = true))
    }

    @Test
    fun suggestionHidesFormsSectionWhenNoRepresentativeFormWasSelected() {
        setSuggestionContent(listOf(suggestionGroup("kaikki", "Kaikki / Wiktionary", "nước")))

        composeRule.onNodeWithTag("dictionary_inflection_section").assertDoesNotExist()
    }

    @Test
    fun jmdictSuggestionDisplaysReadingPosAndEachSense() {
        composeRule.setContent {
            LocalVocabularyTheme {
                DictionarySuggestionSection(
                    state = suggestionState(listOf(jmDictSuggestionGroup())),
                    onAction = {},
                )
            }
        }

        composeRule.onAllNodesWithText("たべる", substring = true).assertCountEquals(2)
        composeRule.onAllNodesWithText(
            "Ichidan verb; transitive verb",
            substring = true,
        ).assertCountEquals(2)
        composeRule.onNodeWithText("to eat").assertIsDisplayed()
        composeRule.onNodeWithText("to live on").assertIsDisplayed()
        composeRule.onAllNodesWithText("Use").assertCountEquals(0)
        composeRule.onAllNodesWithText("Use this sense").assertCountEquals(0)
        composeRule.onAllNodesWithText("이 뜻 사용").assertCountEquals(0)
        composeRule.onAllNodesWithTag("dictionary_suggestion_row").assertCountEquals(2)
    }

    @Test
    fun suggestionRowsAreClickableAndSelectionIsVisible() {
        var selected: ExternalDictionaryEntry? = null
        val group = suggestionGroup("alpha", "Alpha Dictionary", "你好")
        val synthesizedKey = DictionarySuggestionSynthesizer.synthesize(listOf(group))
            .single().candidates.single().key
        composeRule.setContent {
            LocalVocabularyTheme {
                DictionarySuggestionSection(
                    state = suggestionState(listOf(group), setOf(synthesizedKey)),
                    onAction = { action ->
                        if (action is WordEditorAction.DictionarySuggestionSelected) {
                            selected = action.entry
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithContentDescription("가져옴").assertIsDisplayed()
        composeRule.onNodeWithText("greeting").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assert(selected != null) }
    }

    @Test
    fun koreanBasicPanLexAndCcCedictRowsUseTheSameGenericTapAction() {
        val selectedProviderIds = mutableListOf<String>()
        val groups = listOf(
            suggestionGroup("korean-basic-dictionary", "한국어기초사전", "먹다"),
            suggestionGroup("panlex", "PanLex", "배우다"),
            suggestionGroup("cc-cedict", "CC-CEDICT", "你好"),
        )
        composeRule.setContent {
            LocalVocabularyTheme {
                DictionarySuggestionSection(
                    state = suggestionState(groups),
                    onAction = { action ->
                        if (action is WordEditorAction.DictionarySuggestionSelected) {
                            selectedProviderIds += action.entry.providerId.value
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithText("먹다").performClick()
        composeRule.onNodeWithText("배우다").performClick()
        composeRule.onNodeWithText("你好").performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf("korean-basic-dictionary", "panlex", "cc-cedict"),
                selectedProviderIds,
            )
        }
    }

    @Test
    fun zeroSelectableRowsHaveNoSuggestionListContent() {
        composeRule.setContent {
            LocalVocabularyTheme {
                DictionarySuggestionSection(
                    state = WordEditorUiState(
                        isLoading = false,
                        dictionarySuggestionGroups = emptyList(),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithTag("dictionary_suggestion_content").assertDoesNotExist()
        composeRule.onNodeWithTag("dictionary_suggestion_list").assertDoesNotExist()
    }

    @Test
    fun identicalCandidateShowsOneRowWithBothSources() {
        val groups = listOf(
            suggestionGroup("korean-basic-dictionary", "한국어기초사전", "먹다"),
            suggestionGroup("panlex", "PanLex", "먹다"),
        )

        setSuggestionContent(groups)

        composeRule.onAllNodesWithTag("dictionary_suggestion_row").assertCountEquals(1)
        composeRule.onNodeWithText("한국어기초사전 · PanLex").assertIsDisplayed()
    }

    @Test
    fun synthesizedVisibleCountControlsScrollThreshold() {
        val groups = listOf(
            suggestionGroup("alpha", "Alpha", "duplicate"),
            suggestionGroup("beta", "Beta", "duplicate"),
            suggestionGroupWithRows("gamma", "Gamma", 3),
        )

        setSuggestionContent(groups)

        composeRule.onAllNodesWithTag("dictionary_suggestion_row").assertCountEquals(4)
        composeRule.onNodeWithTag("dictionary_suggestion_content").assertIsDisplayed()
        composeRule.onNodeWithTag("dictionary_suggestion_list").assertDoesNotExist()
    }

    @Test
    fun oneSelectableRowUsesNaturalHeightWithoutInnerScroll() {
        setSuggestionContent(listOf(suggestionGroupWithRows("alpha", "Alpha", 1)))

        composeRule.onNodeWithTag("dictionary_suggestion_content").assertIsDisplayed()
        composeRule.onNodeWithTag("dictionary_suggestion_list").assertDoesNotExist()
    }

    @Test
    fun twoSelectableRowsUseNaturalHeightWithoutInnerScroll() {
        setSuggestionContent(listOf(suggestionGroupWithRows("alpha", "Alpha", 2)))

        composeRule.onNodeWithTag("dictionary_suggestion_content").assertIsDisplayed()
        composeRule.onNodeWithTag("dictionary_suggestion_list").assertDoesNotExist()
    }

    @Test
    fun fourSelectableRowsIgnoreProviderHeadersAndDoNotCreateInnerScroll() {
        val groups = (1..4).map { index ->
            suggestionGroupWithRows("provider$index", "Provider $index", 1)
        }
        setSuggestionContent(groups)

        composeRule.onNodeWithTag("dictionary_suggestion_content").assertIsDisplayed()
        composeRule.onNodeWithTag("dictionary_suggestion_list").assertDoesNotExist()
    }

    @Test
    fun fiveSelectableRowsUseOneBoundedScrollableList() {
        setSuggestionContent(listOf(suggestionGroupWithRows("alpha", "Alpha", 5)))

        composeRule.onNodeWithTag("dictionary_suggestion_list")
            .assertIsDisplayed()
            .assert(hasScrollAction())
        composeRule.onNodeWithTag("dictionary_suggestion_content").assertDoesNotExist()
    }

    @Test
    fun tenSelectableRowsCanScrollToTheLastRow() {
        setSuggestionContent(listOf(suggestionGroupWithRows("alpha", "Alpha", 10)))

        composeRule.onNodeWithTag("dictionary_suggestion_list")
            .performScrollToNode(hasText("word10"))
        composeRule.onNodeWithText("word10").assertIsDisplayed()
    }

    @Test
    fun twentySelectableRowsCanScrollToTheLastRow() {
        setSuggestionContent(listOf(suggestionGroupWithRows("alpha", "Alpha", 20)))

        composeRule.onNodeWithTag("dictionary_suggestion_list")
            .performScrollToNode(hasText("word20"))
        composeRule.onNodeWithText("word20").assertIsDisplayed()
    }

    @Test
    fun selectableRowsAcrossProvidersShareTheSameScrollThresholdAndContainer() {
        setSuggestionContent(
            listOf(
                suggestionGroupWithRows("korean", "Korean", 2),
                suggestionGroupWithRows("english", "English", 3, rowPrefix = "english-word"),
            ),
        )

        composeRule.onNodeWithTag("dictionary_suggestion_list")
            .assertIsDisplayed()
            .assert(hasScrollAction())
        composeRule.onAllNodesWithTag("dictionary_suggestion_list").assertCountEquals(1)
    }

    @Test
    fun verticalSwipeOnSuggestionRowScrollsWithoutImportingIt() {
        var selectionCount = 0
        composeRule.setContent {
            LocalVocabularyTheme {
                DictionarySuggestionSection(
                    state = suggestionState(
                        listOf(suggestionGroupWithRows("alpha", "Alpha", 10)),
                    ),
                    onAction = { action ->
                        if (action is WordEditorAction.DictionarySuggestionSelected) {
                            selectionCount += 1
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithText("word1").performTouchInput { swipeUp() }
        composeRule.runOnIdle { assertEquals(0, selectionCount) }
    }

    @Test
    fun jmdictSenseRowsDispatchOnlyTheTappedSense() {
        val selected = mutableListOf<ExternalDictionaryEntry>()
        composeRule.setContent {
            LocalVocabularyTheme {
                DictionarySuggestionSection(
                    state = suggestionState(listOf(jmDictSuggestionGroup())),
                    onAction = { action ->
                        if (action is WordEditorAction.DictionarySuggestionSelected) {
                            selected += action.entry
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithText("to eat").performClick()
        composeRule.onNodeWithText("to live on").performClick()

        composeRule.runOnIdle {
            assertEquals(2, selected.size)
            assertEquals("to eat", selected[0].senses.single().meanings.single().text)
            assertEquals("to live on", selected[1].senses.single().meanings.single().text)
        }
    }

    @Test
    fun importedSenseDisplaysGenericSourceAndModifiedIndicator() {
        composeRule.setContent {
            LocalVocabularyTheme {
                WordEditorScreen(
                    state = WordEditorUiState(
                        isLoading = false,
                        headword = "你好",
                        languageTag = "zh-Hans",
                        senses = listOf(
                            EditableSense(
                                key = 1,
                                meaning = "안녕하세요",
                                provenance = DictionaryProvenance(
                                    providerId = "cc-cedict",
                                    sourceEntryId = "source-key",
                                    sourceSenseId = "0",
                                    sourceName = "CC-CEDICT",
                                    sourceUrl = null,
                                    licenseName = "CC BY-SA 4.0",
                                    licenseUrl = null,
                                    datasetVersion = null,
                                    importedFields = setOf(ImportedDictionaryField.MEANING),
                                    importedAtEpochMillis = 1,
                                    modifiedAfterImport = true,
                                ),
                            ),
                        ),
                    ),
                    onAction = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("sense_provenance"))
        composeRule.onNodeWithText("CC-CEDICT 기반 · 수정됨").assertIsDisplayed()
    }

    @Test
    fun pronunciationAndGenderEditorsAppearOnlyWhenValuesExist() {
        composeRule.setContent {
            LocalVocabularyTheme {
                WordEditorScreen(
                    state = WordEditorUiState(
                        isLoading = false,
                        headword = "Wasser",
                        languageTag = "de",
                        pronunciations = listOf(
                            EditablePronunciation(
                                key = 10,
                                notation = PronunciationNotation.IPA,
                                value = "/ˈvasɐ/",
                                languageTag = "de",
                            ),
                        ),
                        senses = listOf(
                            EditableSense(
                                key = 20,
                                meaning = "water",
                                grammaticalGender = "neuter",
                                isGrammaticalGenderVisible = true,
                            ),
                        ),
                    ),
                    onAction = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("pronunciation"))
        composeRule.onNodeWithTag("pronunciation").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("grammatical_gender"))
        composeRule.onNodeWithTag("grammatical_gender").assertIsDisplayed()
    }

    @Test
    fun inlineTagCreationStartsInsideEditorWithoutNavigation() {
        val actions = mutableListOf<WordEditorAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                WordEditorScreen(
                    state = WordEditorUiState(isLoading = false),
                    onAction = actions::add,
                    onBack = {},
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("create_tag"))
        composeRule.onNodeWithTag("create_tag").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(WordEditorAction.CreateTagRequested), actions)
        }
    }

    @Test
    fun inlineTagDialogShowsValidationAndConfirmAction() {
        val actions = mutableListOf<WordEditorAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                WordEditorScreen(
                    state = WordEditorUiState(
                        isLoading = false,
                        isTagCreatorVisible = true,
                        tagCreationError = "태그 이름을 입력하세요.",
                    ),
                    onAction = actions::add,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("태그 이름을 입력하세요.").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_create_tag").performClick()
        composeRule.runOnIdle {
            assert(actions.contains(WordEditorAction.CreateTagConfirmed))
        }
    }

    @Test
    fun wordbookAndTagSectionsAreSeparateAndInlineWordbookCreationNeedsNoNavigation() {
        val actions = mutableListOf<WordEditorAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                WordEditorScreen(
                    state = WordEditorUiState(
                        isLoading = false,
                        availableWordbooks = listOf(
                            com.example.localvocabulary.vocabulary.domain.VocabularyWordbook(
                                1,
                                "wordbook-1",
                                "JLPT N2",
                            ),
                        ),
                        availableTags = listOf(
                            com.example.localvocabulary.vocabulary.domain.VocabularyTag(
                                2,
                                "tag-2",
                                "음식",
                            ),
                        ),
                    ),
                    onAction = actions::add,
                    onBack = {},
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("create_wordbook"))
        composeRule.onNodeWithText("단어장").assertIsDisplayed()
        composeRule.onNodeWithText("태그").assertIsDisplayed()
        composeRule.onNodeWithTag("create_wordbook").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(WordEditorAction.CreateWordbookRequested), actions)
        }
    }

    @Test
    fun duplicateWarningRequiresAnExplicitChoice() {
        composeRule.setContent {
            LocalVocabularyTheme {
                WordEditorScreen(
                    state = WordEditorUiState(
                        isLoading = false,
                        duplicateCandidate = com.example.localvocabulary.vocabulary.domain.VocabularyEntry(
                            id = 7,
                            backupId = "entry-7",
                            headword = "long",
                            languageTag = "en",
                            senses = listOf(
                                com.example.localvocabulary.vocabulary.domain.VocabularySense(
                                    id = 70,
                                    meaning = "기다랗다",
                                    partOfSpeech = "",
                                    examples = emptyList(),
                                ),
                            ),
                            notes = "",
                            tags = emptyList(),
                            createdAtEpochMillis = 1,
                            modifiedAtEpochMillis = 1,
                        ),
                    ),
                    onAction = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("이미 저장된 단어입니다").assertIsDisplayed()
        composeRule.onNodeWithText("기존 단어 열기").assertIsDisplayed()
        composeRule.onNodeWithText("별도 단어로 저장").assertIsDisplayed()
    }

    @Test
    fun externalDictionaryLinkAppearsImmediatelyBelowReadingAndKeepsAction() {
        val actions = mutableListOf<WordEditorAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                WordEditorScreen(
                    state = WordEditorUiState(
                        isLoading = false,
                        reading = "word",
                        externalDictionaryReference = ExternalDictionaryReference(
                            providerId = "naver-dictionary-reference",
                            providerName = "NAVER Dictionary",
                            destinationName = "네이버 영어사전",
                            uri = "https://en.dict.naver.com/#/search?query=word",
                        ),
                    ),
                    onAction = actions::add,
                    onBack = {},
                )
            }
        }

        val readingBounds = composeRule.onNodeWithTag("reading").fetchSemanticsNode().boundsInRoot
        val link = composeRule.onNodeWithTag("external_dictionary_reference").assertIsDisplayed()
        val linkBounds = link.fetchSemanticsNode().boundsInRoot

        assertTrue(linkBounds.top >= readingBounds.bottom)
        composeRule.onNodeWithText("외부 참고").assertDoesNotExist()
        composeRule.onNodeWithText("네이버 영어사전에서 발음 확인 ↗").performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(WordEditorAction.OpenExternalDictionaryReference),
                actions,
            )
        }
    }

    @Test
    fun wordEditorUsesSharedLanguagePicker() {
        val actions = mutableListOf<WordEditorAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                WordEditorScreen(
                    state = WordEditorUiState(isLoading = false, languageTag = "en"),
                    onAction = actions::add,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("언어 선택").performClick()
        composeRule.onNodeWithText("日本語").performClick()
        composeRule.runOnIdle {
            assertTrue(actions.contains(WordEditorAction.LanguageTagChanged("ja")))
        }
    }

    private fun suggestionGroup(
        providerId: String,
        providerName: String,
        headword: String,
        licenseShortName: String? = null,
    ) = DictionarySuggestionGroup(
        providerId = DictionaryProviderId(providerId),
        providerName = providerName,
        entries = listOf(
            ExternalDictionaryEntry(
                providerId = DictionaryProviderId(providerId),
                sourceEntryId = "$providerId:$headword",
                headword = headword,
                sourceLanguage = Bcp47LanguageTag.requireValid("zh-Hans"),
                senses = listOf(
                    ExternalDictionarySense(
                        meanings = listOf(
                            DictionaryMeaning(
                                text = "greeting",
                                language = Bcp47LanguageTag.requireValid("en"),
                                kind = DictionaryResultKind.TRANSLATION,
                            ),
                        ),
                    ),
                ),
                attribution = DictionaryAttribution(
                    sourceName = providerName,
                    sourceUrl = "https://example.invalid/$providerId",
                    officialIdentifier = providerId,
                    licenseName = null,
                    licenseUrl = null,
                    attributionNotice = null,
                    licenseShortName = licenseShortName,
                ),
            ),
        ),
    )

    private fun suggestionGroupWithRows(
        providerId: String,
        providerName: String,
        rowCount: Int,
        rowPrefix: String = "word",
    ): DictionarySuggestionGroup {
        val template = suggestionGroup(providerId, providerName, "${rowPrefix}1")
        val templateEntry = template.entries.single()
        val templateSense = templateEntry.senses.single()
        return template.copy(
            entries = (1..rowCount).map { index ->
                templateEntry.copy(
                    sourceEntryId = "$providerId:$index",
                    headword = "$rowPrefix$index",
                    senses = listOf(
                        templateSense.copy(
                            meanings = listOf(
                                templateSense.meanings.single().copy(text = "meaning$index"),
                            ),
                        ),
                    ),
                )
            },
        )
    }

    private fun setSuggestionContent(groups: List<DictionarySuggestionGroup>) {
        composeRule.setContent {
            LocalVocabularyTheme {
                DictionarySuggestionSection(
                    state = suggestionState(groups),
                    onAction = {},
                )
            }
        }
    }

    private fun suggestionState(
        groups: List<DictionarySuggestionGroup>,
        selectedSuggestionKeys: Set<String> = emptySet(),
    ) = WordEditorUiState(
        isLoading = false,
        dictionarySuggestionGroups = groups,
        synthesizedSuggestionGroups = DictionarySuggestionSynthesizer.synthesize(groups),
        selectedSuggestionKeys = selectedSuggestionKeys,
    )

    private fun jmDictSuggestionGroup() = DictionarySuggestionGroup(
        providerId = DictionaryProviderId("jmdict"),
        providerName = "JMdict",
        entries = listOf(
            ExternalDictionaryEntry(
                providerId = DictionaryProviderId("jmdict"),
                sourceEntryId = "1358280",
                headword = "食べる",
                sourceLanguage = Bcp47LanguageTag.requireValid("ja"),
                linguisticFeatures = DictionaryLinguisticFeatures(
                    reading = DictionaryReading("たべる"),
                ),
                senses = listOf(
                    ExternalDictionarySense(
                        meanings = listOf(
                            DictionaryMeaning(
                                text = "to eat",
                                language = Bcp47LanguageTag.requireValid("en"),
                                kind = DictionaryResultKind.TRANSLATION,
                            ),
                        ),
                        partOfSpeech = "Ichidan verb; transitive verb",
                    ),
                    ExternalDictionarySense(
                        meanings = listOf(
                            DictionaryMeaning(
                                text = "to live on",
                                language = Bcp47LanguageTag.requireValid("en"),
                                kind = DictionaryResultKind.TRANSLATION,
                            ),
                        ),
                        partOfSpeech = "Ichidan verb; transitive verb",
                    ),
                ),
                attribution = DictionaryAttribution(
                    sourceName = "JMdict",
                    sourceUrl = "https://www.edrdg.org/jmdict/j_jmdict.html",
                    officialIdentifier = "jmdict",
                    licenseName = "Creative Commons Attribution-ShareAlike 4.0 International",
                    licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
                    attributionNotice = "JMdict by EDRDG",
                ),
            ),
        ),
    )
}
