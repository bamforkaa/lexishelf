package com.example.localvocabulary.feature.worddetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import com.example.localvocabulary.core.ui.theme.LocalVocabularyTheme
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularySense
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import com.example.localvocabulary.vocabulary.domain.GrammaticalGenderCategory
import com.example.localvocabulary.vocabulary.domain.PronunciationNotation
import com.example.localvocabulary.vocabulary.domain.VocabularyGrammaticalGender
import com.example.localvocabulary.vocabulary.domain.VocabularyPronunciation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WordDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun realTagChipOpensItsCollection() {
        var openedTagId: Long? = null
        composeRule.setContent {
            LocalVocabularyTheme {
                WordDetailScreen(
                    state = WordDetailUiState.Content(entry()),
                    onAction = {},
                    onBack = {},
                    onEdit = {},
                    onOpenTag = { openedTagId = it },
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("JLPT N2"))
        composeRule.onNodeWithText("JLPT N2").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(7L, openedTagId) }
    }

    @Test
    fun loadingStateRemainsInsideScaffoldWithVisibleNavigation() {
        composeRule.setContent {
            LocalVocabularyTheme {
                WordDetailScreen(
                    state = WordDetailUiState.Loading,
                    onAction = {},
                    onBack = {},
                    onEdit = {},
                    onOpenTag = {},
                )
            }
        }

        composeRule.onNodeWithText("단어 상세").assertIsDisplayed()
        composeRule.onNodeWithText("단어를 불러오는 중입니다").assertIsDisplayed()
    }

    @Test
    fun pronunciationAndGenderHaveDistinctDetailMetadata() {
        val richEntry = entry().copy(
            pronunciations = listOf(
                VocabularyPronunciation(
                    id = 11,
                    stableId = "pronunciation-11",
                    notation = PronunciationNotation.IPA,
                    value = "/taberu/",
                    languageTag = "ja",
                ),
            ),
            senses = entry().senses.map {
                it.copy(
                    grammaticalGender = VocabularyGrammaticalGender(
                        GrammaticalGenderCategory.COMMON,
                    ),
                )
            },
        )
        composeRule.setContent {
            LocalVocabularyTheme {
                WordDetailScreen(
                    state = WordDetailUiState.Content(richEntry),
                    onAction = {},
                    onBack = {},
                    onEdit = {},
                    onOpenTag = {},
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("IPA · /taberu/"))
        composeRule.onNodeWithText("IPA · /taberu/").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("동사 · common"))
        composeRule.onNodeWithText("동사 · common").assertIsDisplayed()
    }

    private fun entry() = VocabularyEntry(
        id = 1,
        backupId = "entry-1",
        headword = "食べる",
        languageTag = "ja",
        reading = "たべる",
        senses = listOf(VocabularySense(10, "먹다", "동사", emptyList())),
        notes = "",
        tags = listOf(VocabularyTag(7, "tag-7", "JLPT N2")),
        createdAtEpochMillis = 1,
        modifiedAtEpochMillis = 1,
    )
}
