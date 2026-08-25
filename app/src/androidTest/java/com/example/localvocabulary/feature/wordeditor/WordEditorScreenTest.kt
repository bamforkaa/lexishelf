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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.localvocabulary.core.ui.theme.LocalVocabularyTheme
import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.DictionaryAttribution
import com.example.localvocabulary.dictionary.domain.DictionaryLinguisticFeatures
import com.example.localvocabulary.dictionary.domain.DictionaryMeaning
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.DictionaryReading
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry
import com.example.localvocabulary.dictionary.domain.ExternalDictionarySense
import com.example.localvocabulary.vocabulary.domain.DictionaryProvenance
import com.example.localvocabulary.vocabulary.domain.ImportedDictionaryField
import com.example.localvocabulary.vocabulary.domain.VocabularyValidationError
import org.junit.Assert.assertEquals
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
    fun dictionarySuggestionsDisplayResultsGroupedByProvider() {
        composeRule.setContent {
            LocalVocabularyTheme {
                DictionarySuggestionSection(
                    state = WordEditorUiState(
                        isLoading = false,
                        dictionarySuggestionGroups = listOf(
                            suggestionGroup("alpha", "Alpha Dictionary", "你好"),
                            suggestionGroup("beta", "Beta Dictionary", "您好"),
                        ),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithTag("dictionary_suggestions").assertIsDisplayed()
        composeRule.onNodeWithText("Alpha Dictionary").assertIsDisplayed()
        composeRule.onNodeWithText("你好").assertIsDisplayed()
        composeRule.onNodeWithText("Beta Dictionary").assertIsDisplayed()
        composeRule.onNodeWithText("您好").assertIsDisplayed()
    }

    @Test
    fun suggestionDisplaysCompactLicenseAttribution() {
        composeRule.setContent {
            LocalVocabularyTheme {
                DictionarySuggestionSection(
                    state = WordEditorUiState(
                        isLoading = false,
                        dictionarySuggestionGroups = listOf(
                            suggestionGroup(
                                providerId = "cc-cedict",
                                providerName = "CC-CEDICT",
                                headword = "你好",
                                licenseShortName = "CC BY-SA 4.0",
                            ),
                        ),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("CC-CEDICT · CC BY-SA 4.0").assertIsDisplayed()
    }

    @Test
    fun jmdictSuggestionDisplaysReadingPosAndEachSense() {
        composeRule.setContent {
            LocalVocabularyTheme {
                DictionarySuggestionSection(
                    state = WordEditorUiState(
                        isLoading = false,
                        dictionarySuggestionGroups = listOf(jmDictSuggestionGroup()),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onAllNodesWithText("たべる").assertCountEquals(2)
        composeRule.onAllNodesWithText("Ichidan verb; transitive verb").assertCountEquals(2)
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
        composeRule.setContent {
            LocalVocabularyTheme {
                DictionarySuggestionSection(
                    state = WordEditorUiState(
                        isLoading = false,
                        dictionarySuggestionGroups = listOf(group),
                        selectedSuggestionKeys = setOf(group.entries.single().suggestionKey()),
                    ),
                    onAction = { action ->
                        if (action is WordEditorAction.DictionarySuggestionSelected) {
                            selected = action.entry
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithText("✓ 你好").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assert(selected != null) }
    }

    @Test
    fun koreanBasicPanLexAndCcCedictRowsUseTheSameGenericTapAction() {
        val selectedProviderIds = mutableListOf<String>()
        composeRule.setContent {
            LocalVocabularyTheme {
                DictionarySuggestionSection(
                    state = WordEditorUiState(
                        isLoading = false,
                        dictionarySuggestionGroups = listOf(
                            suggestionGroup("korean-basic-dictionary", "한국어기초사전", "먹다"),
                            suggestionGroup("panlex", "PanLex", "배우다"),
                            suggestionGroup("cc-cedict", "CC-CEDICT", "你好"),
                        ),
                    ),
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
                suggestionGroupWithRows("english", "English", 3),
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
                    state = WordEditorUiState(
                        isLoading = false,
                        dictionarySuggestionGroups = listOf(
                            suggestionGroupWithRows("alpha", "Alpha", 10),
                        ),
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
                    state = WordEditorUiState(
                        isLoading = false,
                        dictionarySuggestionGroups = listOf(jmDictSuggestionGroup()),
                    ),
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
    ): DictionarySuggestionGroup {
        val template = suggestionGroup(providerId, providerName, "word1")
        val templateEntry = template.entries.single()
        val templateSense = templateEntry.senses.single()
        return template.copy(
            entries = (1..rowCount).map { index ->
                templateEntry.copy(
                    sourceEntryId = "$providerId:$index",
                    headword = "word$index",
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
                    state = WordEditorUiState(
                        isLoading = false,
                        dictionarySuggestionGroups = groups,
                    ),
                    onAction = {},
                )
            }
        }
    }

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
