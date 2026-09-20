package com.example.localvocabulary.feature.wordeditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.localvocabulary.core.ui.theme.LocalVocabularyTheme
import com.example.localvocabulary.vocabulary.domain.ExampleOrigin
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ContextCaptureScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun contextMeaningOriginAndSourceEmitEditorActions() {
        val actions = mutableListOf<WordEditorAction>()
        compose.setContent {
            LocalVocabularyTheme {
                WordEditorScreen(
                    WordEditorUiState(isLoading = false,
                        headword = "I look forward to working with you on this project.",
                        senses = listOf(EditableSense(1, meaning = "기대하다", examples = listOf(
                            EditableExample(2, text = "I look forward to it."),
                        ))),
                    ), actions::add, {},
                )
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("context_details_2"))
        compose.onNodeWithTag("context_details_2").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("context_MEANING_2"))
        compose.onNodeWithTag("context_MEANING_2").performTextInput("기대됩니다.")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("내가 만든 문장"))
        compose.onNodeWithText("내가 만든 문장").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("context_SOURCE_URL_2"))
        compose.onNodeWithTag("context_SOURCE_URL_2").performTextInput("https://example.com/episode")
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("context_SOURCE_LOCATOR_2"))
        compose.onNodeWithTag("context_SOURCE_LOCATOR_2").assertIsDisplayed().performTextInput("12:35")
        compose.runOnIdle {
            assertTrue(actions.contains(WordEditorAction.ExampleMetadataChanged(1, 2, ExampleMetadataField.MEANING, "기대됩니다.")))
            assertTrue(actions.contains(WordEditorAction.ExampleOriginChanged(1, 2, ExampleOrigin.USER)))
            assertTrue(actions.contains(WordEditorAction.ExampleMetadataChanged(1, 2, ExampleMetadataField.SOURCE_URL, "https://example.com/episode")))
            assertTrue(actions.contains(WordEditorAction.ExampleMetadataChanged(1, 2, ExampleMetadataField.SOURCE_LOCATOR, "12:35")))
        }
    }
}
