package com.example.localvocabulary.feature.writingpractice

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.localvocabulary.core.ui.theme.LocalVocabularyTheme
import com.example.localvocabulary.feature.handwriting.HandwritingInputAction
import com.example.localvocabulary.feature.handwriting.HandwritingInputUiState
import com.example.localvocabulary.feature.handwriting.HandwritingModelUiState
import com.example.localvocabulary.handwriting.domain.HandwritingCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WritingPracticeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun setupShowsScopeLengthCountAndEmptyScopeState() {
        val holder = mutableStateOf(
            WritingPracticeUiState(
                eligibleCount = 3,
                isLoadingEligibility = false,
            ),
        )
        composeRule.setContent {
            LocalVocabularyTheme {
                WritingPracticeScreen(state = holder.value, onAction = {}, onBack = {})
            }
        }

        composeRule.onNodeWithText("연습 범위").assertIsDisplayed()
        composeRule.onNodeWithText("세션 길이").assertIsDisplayed()
        composeRule.onNodeWithText("대상 단어 3개").assertIsDisplayed()
        composeRule.runOnIdle { holder.value = holder.value.copy(eligibleCount = 0) }
        composeRule.onNodeWithTag("practice_empty_scope").assertIsDisplayed()
    }

    @Test
    fun handwritingCandidateOnlyPopulatesAnswerActionAndNeverAutoSubmits() {
        val practiceActions = mutableListOf<WritingPracticeAction>()
        val handwritingActions = mutableListOf<HandwritingInputAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                WritingPracticeScreen(
                    state = questionState(),
                    onAction = practiceActions::add,
                    onBack = {},
                    handwritingState = readyHandwritingState(
                        candidates = listOf(HandwritingCandidate("食"), HandwritingCandidate("事")),
                    ),
                    onHandwritingAction = handwritingActions::add,
                    onHandwritingCandidateSelected = {
                        practiceActions += WritingPracticeAction.AnswerChanged(it)
                    },
                )
            }
        }

        composeRule.onNodeWithText("食べる").assertDoesNotExist()
        composeRule.onNodeWithTag("handwriting_candidate_食").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertTrue(practiceActions.contains(WritingPracticeAction.AnswerChanged("食")))
            assertFalse(practiceActions.contains(WritingPracticeAction.Submit))
            assertTrue(
                handwritingActions.contains(HandwritingInputAction.CandidateAccepted("食")),
            )
        }
    }

    @Test
    fun unsupportedHandwritingIsContainedAndKeyboardFallbackRemainsAvailable() {
        val actions = mutableListOf<WritingPracticeAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                WritingPracticeScreen(
                    state = questionState(),
                    onAction = actions::add,
                    onBack = {},
                    handwritingState = readyHandwritingState().copy(
                        modelState = HandwritingModelUiState.UnsupportedLanguage,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("이 언어의 손글씨 인식은 지원되지 않습니다.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("키보드").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertTrue(
                actions.contains(
                    WritingPracticeAction.InputModeSelected(PracticeInputMode.KEYBOARD),
                ),
            )
        }
    }

    @Test
    fun candidateAndModelChangesDoNotMoveOrResizeInlineCanvas() {
        val handwriting = mutableStateOf(readyHandwritingState())
        composeRule.setContent {
            LocalVocabularyTheme {
                WritingPracticeScreen(
                    state = questionState(),
                    onAction = {},
                    onBack = {},
                    handwritingState = handwriting.value,
                )
            }
        }

        composeRule.onNodeWithTag("handwriting_canvas").performScrollTo()
        val initial = composeRule.onNodeWithTag("handwriting_canvas")
            .fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle {
            handwriting.value = handwriting.value.copy(
                isRecognizing = true,
                candidates = emptyList(),
            )
        }
        val recognizing = composeRule.onNodeWithTag("handwriting_canvas")
            .fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle {
            handwriting.value = handwriting.value.copy(
                isRecognizing = false,
                candidates = listOf(HandwritingCandidate("食"), HandwritingCandidate("事")),
            )
        }
        val candidates = composeRule.onNodeWithTag("handwriting_canvas")
            .fetchSemanticsNode().boundsInRoot

        assertEquals(initial, recognizing)
        assertEquals(initial, candidates)
    }

    @Test
    fun incorrectFeedbackExposesRetryAndNextOnlyAfterAnswerReveal() {
        val actions = mutableListOf<WritingPracticeAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                WritingPracticeScreen(
                    state = questionState().copy(
                        answer = "食べ",
                        feedback = PracticeAnswerFeedback(
                            isCorrect = false,
                            expectedHeadword = "食べる",
                            submittedAnswer = "食べ",
                        ),
                    ),
                    onAction = actions::add,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("practice_expected_answer").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("retry_practice_question").performClick()
        composeRule.onNodeWithTag("next_practice_question").performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(WritingPracticeAction.Retry, WritingPracticeAction.Next),
                actions,
            )
        }
    }

    private fun questionState() = WritingPracticeUiState(
        phase = PracticePhase.QUESTION,
        currentQuestion = PracticeQuestion(
            entryId = 1,
            headword = "食べる",
            languageTag = "ja",
            primaryHint = PracticeHint(PracticeHintKind.MEANING, "먹다"),
            secondaryHints = listOf(PracticeHint(PracticeHintKind.READING, "たべる")),
            expandedHints = listOf(PracticeHint(PracticeHintKind.EXAMPLE, "寿司を食べます。")),
        ),
        sessionTotal = 1,
        handwritingSessionKey = 1,
    )

    private fun readyHandwritingState(
        candidates: List<HandwritingCandidate> = emptyList(),
    ) = HandwritingInputUiState(
        isOpen = true,
        contextLanguageTag = "ja",
        selectedLanguageTag = "ja",
        languageOptions = listOf("ja", "en"),
        modelLanguageTag = "ja",
        modelState = HandwritingModelUiState.Ready,
        candidates = candidates,
    )
}
