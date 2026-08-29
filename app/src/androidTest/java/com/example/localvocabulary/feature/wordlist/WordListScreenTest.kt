package com.example.localvocabulary.feature.wordlist

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.example.localvocabulary.core.ui.theme.LocalVocabularyTheme
import com.example.localvocabulary.feature.handwriting.HandwritingInputAction
import com.example.localvocabulary.feature.handwriting.HandwritingInputUiState
import com.example.localvocabulary.feature.handwriting.HandwritingModelUiState
import com.example.localvocabulary.handwriting.domain.HandwritingCandidate
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularySense
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import com.example.localvocabulary.vocabulary.domain.VocabularyWordbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WordListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeOverflowProvidesWritingPracticeEntryPoint() {
        var opened = false
        composeRule.setContent {
            LocalVocabularyTheme {
                WordListScreen(
                    state = WordListUiState(isLoading = false),
                    onAction = {},
                    onAddWord = {},
                    onOpenWord = {},
                    onManageTags = {},
                    onOpenBackup = {},
                    onOpenSettings = {},
                    onOpenWritingPractice = { opened = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("더보기").performClick()
        composeRule.onNodeWithText("쓰기 연습").performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun vocabularyRowShowsHierarchyAndWholeRowOpensEntryAtLargeText() {
        var openedId: Long? = null
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                LocalVocabularyTheme {
                    WordListScreen(
                        state = WordListUiState(
                            isLoading = false,
                            entries = listOf(entry()),
                            tags = listOf(VocabularyTag(2, "tag-food", "음식")),
                        ),
                        onAction = {},
                        onAddWord = {},
                        onOpenWord = { openedId = it },
                        onManageTags = {},
                        onOpenBackup = {},
                        onOpenSettings = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("食べる").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("たべる").assertIsDisplayed()
        composeRule.onNodeWithText("먹다").assertIsDisplayed()
        composeRule.onNodeWithText("동사 · 음식").assertIsDisplayed()
        composeRule.onNodeWithText("日本語").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1L, openedId) }
    }

    @Test
    fun searchClearActionKeepsExistingSearchSemantics() {
        val actions = mutableListOf<WordListAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                WordListScreen(
                    state = WordListUiState(isLoading = false, query = "eat"),
                    onAction = actions::add,
                    onAddWord = {},
                    onOpenWord = {},
                    onManageTags = {},
                    onOpenBackup = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("검색어 지우기").performClick()
        composeRule.runOnIdle { assertEquals(WordListAction.QueryChanged(""), actions.single()) }
    }

    @Test
    fun emptyTagCollectionShowsCollectionEmptyState() {
        var backCount = 0
        composeRule.setContent {
            LocalVocabularyTheme {
                WordListScreen(
                    state = WordListUiState(
                        isLoading = false,
                        selectedTagId = 4,
                        selectedTagName = "JLPT N2",
                        isCollectionView = true,
                    ),
                    onAction = {},
                    onAddWord = {},
                    onOpenWord = {},
                    onManageTags = {},
                    onOpenBackup = {},
                    onOpenSettings = {},
                    onBack = { backCount += 1 },
                )
            }
        }

        composeRule.onNodeWithText("JLPT N2").assertIsDisplayed()
        composeRule.onNodeWithText("조건에 맞는 단어가 없습니다").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("뒤로").performClick()
        composeRule.runOnIdle { assertEquals(1, backCount) }
    }

    @Test
    fun filterCategoriesKeepLanguageWordbookAndTagSeparate() {
        val actions = mutableListOf<WordListAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                WordListScreen(
                    state = WordListUiState(
                        isLoading = false,
                        wordbooks = listOf(VocabularyWordbook(3, "wordbook-3", "JLPT N2")),
                        tags = listOf(VocabularyTag(2, "tag-food", "음식")),
                        languages = listOf("ja"),
                    ),
                    onAction = actions::add,
                    onAddWord = {},
                    onOpenWord = {},
                    onManageTags = {},
                    onOpenBackup = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("언어").performClick()
        composeRule.runOnIdle {
            assertEquals(
                WordListAction.FilterCategorySelected(VocabularyFilterCategory.LANGUAGE),
                actions.single(),
            )
        }
    }

    @Test
    fun searchFieldHandwritingActionOpensSharedInputWithoutSelectingCandidate() {
        val handwritingActions = mutableListOf<HandwritingInputAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                WordListScreen(
                    state = WordListUiState(
                        isLoading = false,
                        languages = listOf("ja", "zh-Hans"),
                    ),
                    onAction = {},
                    onAddWord = {},
                    onOpenWord = {},
                    onManageTags = {},
                    onOpenBackup = {},
                    onOpenSettings = {},
                    onHandwritingAction = handwritingActions::add,
                )
            }
        }

        composeRule.onNodeWithContentDescription("손글씨로 검색").performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    HandwritingInputAction.Open(
                        vocabularyLanguageTags = listOf("ja", "zh-Hans"),
                    ),
                ),
                handwritingActions,
            )
        }
    }

    @Test
    fun explicitHandwritingCandidateReplacesExistingSearchQuery() {
        val actions = mutableListOf<WordListAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                WordListScreen(
                    state = WordListUiState(isLoading = false, query = "old"),
                    onAction = actions::add,
                    onAddWord = {},
                    onOpenWord = {},
                    onManageTags = {},
                    onOpenBackup = {},
                    onOpenSettings = {},
                    handwritingState = HandwritingInputUiState(
                        isOpen = true,
                        selectedLanguageTag = "ja",
                        languageOptions = listOf("ja"),
                        modelLanguageTag = "ja",
                        modelState = HandwritingModelUiState.Ready,
                        candidates = listOf(HandwritingCandidate("食")),
                    ),
                    onHandwritingCandidateSelected = {
                        actions += WordListAction.QueryChanged(it)
                    },
                )
            }
        }

        composeRule.runOnIdle { assertTrue(actions.isEmpty()) }
        composeRule.onNodeWithTag("handwriting_candidate_食").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(WordListAction.QueryChanged("食")), actions)
        }
    }

    private fun entry() = VocabularyEntry(
        id = 1,
        backupId = "entry-1",
        headword = "食べる",
        languageTag = "ja",
        reading = "たべる",
        senses = listOf(
            VocabularySense(
                id = 10,
                meaning = "먹다",
                partOfSpeech = "동사",
                examples = emptyList(),
            ),
        ),
        notes = "",
        tags = listOf(VocabularyTag(2, "tag-food", "음식")),
        createdAtEpochMillis = 1,
        modifiedAtEpochMillis = 1,
    )
}
