package com.example.localvocabulary.feature.wordbooks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.localvocabulary.core.ui.theme.LocalVocabularyTheme
import com.example.localvocabulary.vocabulary.domain.VocabularyWordbook
import com.example.localvocabulary.vocabulary.domain.VocabularyWordbookSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WordbookManagementScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun wordbookRowShowsCountAndOpensCollection() {
        var openedId: Long? = null
        composeRule.setContent {
            LocalVocabularyTheme {
                WordbookManagementScreen(
                    state = WordbookManagementUiState(
                        wordbooks = listOf(
                            VocabularyWordbookSummary(
                                VocabularyWordbook(8, "wordbook-8", "JLPT N2"),
                                entryCount = 12,
                            ),
                        ),
                    ),
                    onAction = {},
                    onBack = {},
                    onOpenWordbook = { openedId = it },
                )
            }
        }

        composeRule.onNodeWithText("12개 단어").assertIsDisplayed()
        composeRule.onNodeWithText("JLPT N2").performClick()
        composeRule.runOnIdle { assertEquals(8L, openedId) }
    }

    @Test
    fun deleteConfirmationStatesThatVocabularyIsPreserved() {
        composeRule.setContent {
            LocalVocabularyTheme {
                WordbookManagementScreen(
                    state = WordbookManagementUiState(
                        pendingDeleteWordbook = VocabularyWordbook(
                            8,
                            "wordbook-8",
                            "JLPT N2",
                        ),
                    ),
                    onAction = {},
                    onBack = {},
                    onOpenWordbook = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "‘JLPT N2’ 단어장을 삭제하시겠습니까?\n단어장에 포함된 단어 자체는 삭제되지 않습니다.",
        ).assertIsDisplayed()
    }
}
