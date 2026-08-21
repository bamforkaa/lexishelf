package com.example.localvocabulary.feature.wordeditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import com.example.localvocabulary.core.ui.theme.LocalVocabularyTheme
import com.example.localvocabulary.vocabulary.domain.VocabularyValidationError
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
}
