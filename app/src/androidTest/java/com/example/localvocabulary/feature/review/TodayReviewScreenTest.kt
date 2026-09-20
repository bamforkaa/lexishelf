package com.example.localvocabulary.feature.review

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import com.example.localvocabulary.core.ui.theme.LocalVocabularyTheme
import com.example.localvocabulary.review.domain.*
import com.example.localvocabulary.vocabulary.domain.ExampleSentence
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TodayReviewScreenTest {
    @get:Rule val compose = createComposeRule()
    private val expression = "look forward to"
    private fun card(mode: ReviewMode = ReviewMode.MEANING_TO_EXPRESSION, text: String = expression) = ReviewCard(
        ReviewState("state", "sense", nextReviewAt = 0), 1, text, "기대하다",
        listOf(ExampleSentence(1, "I look forward to it.", meaning = "그 일이 기대된다.", sourceTitle = "Sample source")), promptDirection = mode,
    )

    @Test fun answerAndOriginalContextAreAbsentUntilReveal() {
        compose.setContent {
            var state by remember { mutableStateOf(TodayReviewUiState(isLoading = false, card = card())) }
            LocalVocabularyTheme {
                TodayReviewScreen(state, { if (it == TodayReviewAction.Reveal) state = state.copy(revealed = true) }, {})
            }
        }
        compose.onNodeWithText(expression).assertDoesNotExist()
        compose.onNodeWithText("I look forward to it.").assertDoesNotExist()
        compose.onNodeWithTag("review_rate_REMEMBERED").assertDoesNotExist()
        compose.onNodeWithTag("review_next").assertDoesNotExist()
        capture("today-review-prompt")
        compose.onNodeWithTag("review_reveal").performClick()
        compose.onNodeWithTag("review_answer").assertTextEquals(expression)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Sample source"))
        compose.onNodeWithText("Sample source").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("review_rate_REMEMBERED"))
        compose.onNodeWithTag("review_rate_REMEMBERED").assertIsDisplayed()
        capture("today-review-revealed")
    }

    @Test fun targetInsideMeaningIsHiddenAndReverseModeHidesMeaning() {
        compose.setContent {
            LocalVocabularyTheme {
                TodayReviewScreen(TodayReviewUiState(isLoading = false, card = card().copy(meaning = "Use look forward to here")), {}, {})
            }
        }
        compose.onNodeWithText("Use look forward to here").assertDoesNotExist()
        compose.onNodeWithText(expression).assertDoesNotExist()
    }

    @Test fun reverseModeShowsExpressionButKeepsMeaningForAnswer() {
        compose.setContent {
            LocalVocabularyTheme {
                TodayReviewScreen(TodayReviewUiState(isLoading = false, card = card(ReviewMode.EXPRESSION_TO_MEANING)), {}, {})
            }
        }
        compose.onNodeWithTag("review_prompt").assertTextEquals(expression)
        compose.onNodeWithText("기대하다").assertDoesNotExist()
    }

    @Test fun longExpressionWithLargeFontAndDarkThemeKeepsControlsReachable() {
        val long = "This is a deliberately long expression with enough context to wrap onto several lines without clipping the answer."
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                LocalVocabularyTheme(darkTheme = true) {
                    TodayReviewScreen(TodayReviewUiState(isLoading = false, card = card(text = long), revealed = true), {}, {})
                }
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("review_answer"))
        compose.onNodeWithTag("review_answer").assertTextEquals(long)
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("review_rate_REMEMBERED"))
        compose.onNodeWithTag("review_rate_REMEMBERED").assertIsDisplayed()
    }

    @Test fun ownSentenceIsOptionalAndNextRequiresAnEvaluation() {
        var action: TodayReviewAction? = null
        compose.setContent {
            var state by remember { mutableStateOf(TodayReviewUiState(isLoading = false, card = card(), revealed = true, rated = true)) }
            LocalVocabularyTheme {
                TodayReviewScreen(state, {
                    action = it
                    if (it is TodayReviewAction.ExampleChanged) state = state.copy(exampleText = it.text)
                }, {})
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("이 표현으로 내 문장 만들기"))
        compose.onNodeWithText("이 표현으로 내 문장 만들기").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("review_user_example"))
        compose.onNodeWithTag("review_user_example").performTextInput("My sentence.")
        compose.runOnIdle { assertEquals(TodayReviewAction.ExampleChanged("My sentence."), action) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("review_next"))
        compose.onNodeWithTag("review_next").performClick()
        compose.runOnIdle { assertEquals(TodayReviewAction.Next, action) }
    }

    @Test fun emptyStateExplainsEnrollmentWithoutGamification() {
        compose.setContent { LocalVocabularyTheme { TodayReviewScreen(TodayReviewUiState(isLoading = false), {}, {}) } }
        compose.onNodeWithText("지금 할 복습이 없습니다").assertIsDisplayed()
    }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureUxScreenshots") != "true") return
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/lexishelf-ux-$name.png",
        ).use { descriptor -> FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
    }

}
