package com.example.localvocabulary.feature.handwriting

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.example.localvocabulary.handwriting.domain.HandwritingCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HandwritingInputDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun candidatesRequireExplicitTapBeforeSelectionCallback() {
        val selected = mutableListOf<String>()
        val actions = mutableListOf<HandwritingInputAction>()
        composeRule.setContent {
            MaterialTheme {
                HandwritingInputDialog(
                    state = readyState(
                        candidates = listOf(
                            HandwritingCandidate("食"),
                            HandwritingCandidate("事"),
                        ),
                    ),
                    onAction = actions::add,
                    onCandidateSelected = selected::add,
                )
            }
        }

        assertTrue(selected.isEmpty())
        composeRule.onNodeWithTag("handwriting_candidate_食").performClick()

        assertEquals(listOf("食"), selected)
        assertTrue(actions.contains(HandwritingInputAction.CandidateAccepted("食")))
    }

    @Test
    fun unsupportedLanguageAndMissingModelAreContainedInsideDialog() {
        composeRule.setContent {
            MaterialTheme {
                HandwritingInputDialog(
                    state = HandwritingInputUiState(
                        isOpen = true,
                        selectedLanguageTag = "qaa",
                        languageOptions = listOf("qaa"),
                        modelState = HandwritingModelUiState.UnsupportedLanguage,
                    ),
                    onAction = {},
                    onCandidateSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("이 언어의 손글씨 인식은 지원되지 않습니다.").assertIsDisplayed()
        composeRule.onNodeWithTag("close_handwriting").assertIsDisplayed()
    }

    @Test
    fun swipeOnCanvasOnlyEmitsStrokeActionsAndNeverSelectsCandidate() {
        val selected = mutableListOf<String>()
        val actions = mutableListOf<HandwritingInputAction>()
        composeRule.setContent {
            MaterialTheme {
                HandwritingInputDialog(
                    state = readyState(),
                    onAction = actions::add,
                    onCandidateSelected = selected::add,
                )
            }
        }

        composeRule.onNodeWithTag("handwriting_canvas").performTouchInput {
            swipe(start = centerLeft, end = centerRight, durationMillis = 400)
        }

        assertTrue(actions.any { it is HandwritingInputAction.StrokeStarted })
        assertTrue(actions.any { it is HandwritingInputAction.StrokeEnded })
        assertFalse(actions.contains(HandwritingInputAction.Close))
        assertTrue(selected.isEmpty())
    }

    @Test
    fun candidateAndModelStateChangesDoNotMoveOrResizeCanvas() {
        val holder = mutableStateOf(readyState())
        composeRule.setContent {
            MaterialTheme {
                HandwritingInputDialog(
                    state = holder.value,
                    onAction = {},
                    onCandidateSelected = {},
                )
            }
        }

        val initial = composeRule.onNodeWithTag("handwriting_canvas")
            .fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle {
            holder.value = holder.value.copy(
                isRecognizing = true,
                candidates = emptyList(),
            )
        }
        val recognizing = composeRule.onNodeWithTag("handwriting_canvas")
            .fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle {
            holder.value = holder.value.copy(
                isRecognizing = false,
                modelState = HandwritingModelUiState.ModelMissing,
                candidates = listOf(HandwritingCandidate("食"), HandwritingCandidate("事")),
            )
        }
        val modelMissingWithCandidates = composeRule.onNodeWithTag("handwriting_canvas")
            .fetchSemanticsNode().boundsInRoot

        assertEquals(initial, recognizing)
        assertEquals(initial, modelMissingWithCandidates)
    }

    @Test
    fun inkCanBeDrawnBeforeLanguageSelectionAndLanguageChipIsExplicit() {
        val actions = mutableListOf<HandwritingInputAction>()
        composeRule.setContent {
            MaterialTheme {
                HandwritingInputDialog(
                    state = HandwritingInputUiState(
                        isOpen = true,
                        languageOptions = listOf("ja", "ko"),
                        modelState = HandwritingModelUiState.NoLanguageSelected,
                    ),
                    onAction = actions::add,
                    onCandidateSelected = {},
                )
            }
        }

        composeRule.onNodeWithTag("handwriting_canvas").performTouchInput {
            swipe(start = centerLeft, end = centerRight, durationMillis = 300)
        }
        composeRule.onNodeWithTag("handwriting_language_ja").performClick()

        assertTrue(actions.any { it is HandwritingInputAction.StrokeStarted })
        assertTrue(actions.contains(HandwritingInputAction.LanguageSelected("ja")))
    }

    @Test
    fun repeatedDrawingUsesTheSameCanvasLocalCoordinates() {
        val actions = mutableListOf<HandwritingInputAction>()
        composeRule.setContent {
            MaterialTheme {
                HandwritingInputDialog(
                    state = readyState(),
                    onAction = actions::add,
                    onCandidateSelected = {},
                )
            }
        }

        repeat(2) {
            composeRule.onNodeWithTag("handwriting_canvas").performTouchInput {
                swipe(start = centerLeft, end = centerRight, durationMillis = 300)
            }
        }

        val starts = actions.filterIsInstance<HandwritingInputAction.StrokeStarted>()
        assertEquals(2, starts.size)
        assertEquals(starts.first().point.x, starts.last().point.x, 0.5f)
        assertEquals(starts.first().point.y, starts.last().point.y, 0.5f)
    }

    private fun readyState(candidates: List<HandwritingCandidate> = emptyList()) =
        HandwritingInputUiState(
            isOpen = true,
            contextLanguageTag = "ja",
            selectedLanguageTag = "ja",
            languageOptions = listOf("ja"),
            modelLanguageTag = "ja",
            modelState = HandwritingModelUiState.Ready,
            candidates = candidates,
        )
}
