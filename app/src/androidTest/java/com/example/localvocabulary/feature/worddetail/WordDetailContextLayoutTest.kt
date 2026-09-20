package com.example.localvocabulary.feature.worddetail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localvocabulary.core.ui.theme.LocalVocabularyTheme
import com.example.localvocabulary.review.domain.ReviewState
import com.example.localvocabulary.vocabulary.domain.ExampleOrigin
import com.example.localvocabulary.vocabulary.domain.ExampleSentence
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularySense
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WordDetailContextLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun noContextsKeepsMeaningPartOfSpeechAndReviewInHeader() {
        compose.setContent { LocalVocabularyTheme(darkTheme = false) { Detail(entry(emptyList())) } }
        compose.onNodeWithText("뜻 1").assertIsDisplayed()
        compose.onNodeWithText("찾다").assertIsDisplayed()
        compose.onNodeWithText("verb").assertIsDisplayed()
        compose.onNodeWithTag("contexts_10").assertDoesNotExist()
        compose.onNodeWithText("복습에 추가").assertIsDisplayed()
        val meaning = compose.onNodeWithText("찾다").fetchSemanticsNode().boundsInRoot
        val pos = compose.onNodeWithText("verb").fetchSemanticsNode().boundsInRoot
        assertTrue(pos.top < meaning.bottom && pos.bottom > meaning.top)
        val heading = compose.onNodeWithText("뜻 1").fetchSemanticsNode().boundsInRoot
        val review = compose.onNodeWithText("복습에 추가").fetchSemanticsNode().boundsInRoot
        assertTrue(review.top < heading.bottom && review.bottom > heading.top)
        capture("detail-no-contexts")
    }

    @Test fun textOnlyContextHasNoEmptyMetadataRowsOrSingleCount() {
        compose.setContent { LocalVocabularyTheme(darkTheme = false) { Detail(entry(listOf(unknown))) } }
        compose.onNodeWithText(unknown.text).assertIsDisplayed()
        compose.onNodeWithText("출처 미상").assertIsDisplayed()
        compose.onNodeWithTag("context_source_4").assertDoesNotExist()
        compose.onNodeWithText("문맥 1개").assertDoesNotExist()
        compose.onNodeWithTag("contexts_toggle_10").assertDoesNotExist()
        capture("detail-text-only")
    }

    @Test fun completeContextGroupsMeaningAndSourceAndOpensOriginalUrl() {
        val opened = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened += uri }
            }) {
                LocalVocabularyTheme(darkTheme = false) { Detail(entry(listOf(captured))) }
            }
        }
        compose.onNodeWithTag("detail_context_3").assertIsDisplayed()
        compose.onNodeWithText("만난 문맥").assertIsDisplayed()
        compose.onNodeWithText(captured.meaning).assertIsDisplayed()
        compose.onNodeWithText("Example Video · 11:11").assertIsDisplayed()
        compose.onNodeWithText(captured.sourceUrl!!).assertDoesNotExist()
        capture("detail-complete-context")
        compose.onNodeWithTag("context_source_3").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(listOf(captured.sourceUrl), opened) }
    }

    @Test fun urlOnlyAndLocatorOnlyKeepSourceAccessibleWithoutRawUrl() {
        val opened = mutableListOf<String>()
        val urlOnly = unknown.copy(sourceUrl = captured.sourceUrl)
        val locatorOnly = dictionary.copy(sourceLocator = "p. 12")
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened += uri }
            }) {
                LocalVocabularyTheme(darkTheme = false) { Detail(entry(listOf(urlOnly, locatorOnly))) }
            }
        }
        compose.onNodeWithText("출처 열기").assertIsDisplayed()
        compose.onNodeWithText("p. 12").assertIsDisplayed()
        compose.onNodeWithText(captured.sourceUrl!!).assertDoesNotExist()
        compose.onNodeWithTag("context_source_4").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(listOf(captured.sourceUrl), opened) }
    }

    @Test fun twoContextsKeepStoredOrderEvenWhenUserExampleComesFirst() {
        compose.setContent {
            LocalVocabularyTheme(darkTheme = false) { Detail(entry(listOf(user, dictionary))) }
        }
        compose.onNodeWithText("문맥 2개").assertIsDisplayed()
        compose.onNodeWithText("내가 만든 문장").assertIsDisplayed()
        compose.onNodeWithText("사전 예문").assertIsDisplayed()
        compose.onNodeWithText(dictionary.meaning).assertIsDisplayed()
        compose.onNodeWithTag("contexts_toggle_10").assertDoesNotExist()
        assertOrder(user, dictionary)
        capture("detail-two-contexts")
    }

    @Test fun expansionRestoresAndReviewTogglePreservesContextsAndSchedule() {
        val savedEntry = entry(listOf(user, dictionary, captured, unknown))
        val originalReview = ReviewState("review-1", "sense-10", stage = 3, nextReviewAt = 42)
        val state = mutableStateOf(WordDetailUiState.Content(savedEntry, reviewStates = listOf(originalReview)))
        val actions = mutableListOf<WordDetailAction>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            LocalVocabularyTheme(darkTheme = false) {
                WordDetailScreen(state.value, { action ->
                    actions += action
                    if (action is WordDetailAction.SetReviewEnabled) {
                        state.value = state.value.copy(reviewStates = listOf(originalReview.copy(enabled = action.enabled)))
                    }
                }, {}, {}, {})
            }
        }
        compose.onNodeWithText("문맥 4개").assertIsDisplayed()
        compose.onNodeWithText("복습 중 · 중지").assertIsDisplayed()
        compose.onNodeWithText(captured.text).assertDoesNotExist()
        compose.onNodeWithText(unknown.text).assertDoesNotExist()
        assertOrder(user, dictionary)
        capture("detail-four-collapsed")
        compose.onNodeWithText("나머지 2개 보기").performScrollTo().performClick()
        compose.onNodeWithText(unknown.text).performScrollTo().assertIsDisplayed()
        capture("detail-four-expanded")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(unknown.text).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("review_enable_sense-10").performScrollTo().performClick()
        compose.onNodeWithText("복습에 추가").assertIsDisplayed()
        compose.onNodeWithText(unknown.text).assertExists()
        compose.onNodeWithText("접기").performScrollTo().performClick()
        compose.onNodeWithText(captured.text).assertDoesNotExist()
        compose.onNodeWithText(unknown.text).assertDoesNotExist()
        compose.onNodeWithText("나머지 2개 보기").assertExists()
        compose.runOnIdle {
            assertEquals(listOf(WordDetailAction.SetReviewEnabled("sense-10", false)), actions)
            assertEquals(savedEntry, state.value.entry)
            assertEquals(originalReview.copy(enabled = false), state.value.reviewStates.single())
        }
    }

    @Test fun contextExpansionIsIndependentForEachSense() {
        val savedEntry = entry(listOf(user, dictionary, captured)).let {
            it.copy(senses = it.senses + VocabularySense(20, "살피다", "verb", listOf(
                unknown.copy(id = 21, text = "First in second sense."),
                unknown.copy(id = 22, text = "Second in second sense."),
                unknown.copy(id = 23, text = "Third in second sense."),
            ), stableId = "sense-20"))
        }
        compose.setContent { LocalVocabularyTheme(darkTheme = true) { Detail(savedEntry) } }
        compose.onNodeWithTag("contexts_toggle_10").performScrollTo().performClick()
        compose.onNodeWithText(captured.text).assertExists()
        compose.onNodeWithTag("contexts_toggle_20").performScrollTo().assertTextContains("나머지 1개 보기")
        compose.onNodeWithText("Third in second sense.").assertDoesNotExist()
        compose.onNodeWithTag("contexts_toggle_20").performClick()
        compose.onNodeWithText("Third in second sense.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("contexts_toggle_10").performScrollTo().performClick()
        compose.onNodeWithText(captured.text).assertDoesNotExist()
        compose.onNodeWithText("Third in second sense.").performScrollTo().assertIsDisplayed()
        capture("detail-two-senses-dark")
    }

    @Test fun longContentAtTwoHundredPercentInLightTheme() = assertLargeFont(dark = false)

    @Test fun longContentAtTwoHundredPercentInDarkTheme() = assertLargeFont(dark = true)

    private fun assertLargeFont(dark: Boolean) {
        val longExpression = "I don't think that necessarily follows from what we discussed."
        val longContext = captured.copy(text = "I'm looking for my keys while the rest of the team prepares for the next meeting in the building across the street.")
        val savedEntry = entry(listOf(longContext, user, dictionary)).let {
            it.copy(headword = longExpression, senses = it.senses + VocabularySense(
                20, "길게 설명한 의미도 읽고 복습할 수 있다", "a long part of speech label", emptyList(), stableId = "sense-20",
            ))
        }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                LocalVocabularyTheme(darkTheme = dark) { Detail(savedEntry) }
            }
        }
        assertTextWrapsWithoutClipping(longExpression)
        capture("detail-large-${if (dark) "dark" else "light"}-heading")
        compose.onNodeWithText(longContext.text).performScrollTo()
        assertTextWrapsWithoutClipping(longContext.text)
        compose.onNodeWithText("Example Video · 11:11").performScrollTo().assertIsDisplayed()
        capture("detail-large-${if (dark) "dark" else "light"}-context")
        compose.onNodeWithTag("contexts_toggle_10").performScrollTo().performClick()
        compose.onNodeWithText(dictionary.text).performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("review_enable_sense-20"))
        compose.onNodeWithTag("review_enable_sense-20").assertIsDisplayed()
        compose.onNodeWithText("a long part of speech label").performScrollTo()
        capture("detail-large-${if (dark) "dark" else "light"}-second-sense")
        assertTextWrapsWithoutClipping("a long part of speech label")
    }

    @androidx.compose.runtime.Composable
    private fun Detail(entry: VocabularyEntry) {
        WordDetailScreen(WordDetailUiState.Content(entry), {}, {}, {}, {})
    }

    private fun assertTextWrapsWithoutClipping(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach {
            // Paragraph constraints can exceed the text's measured width in FlowRow.
            // Check actual line bounds, allowing fractional pixels in the font metrics.
            assertTrue("$text height: ${it.multiParagraph.height} > ${it.size.height}",
                it.multiParagraph.height <= it.size.height + 1f)
            for (line in 0 until it.lineCount) {
                assertFalse("$text line $line is truncated", it.isLineEllipsized(line))
                assertTrue("$text line $line: ${it.getLineLeft(line)}..${it.getLineRight(line)}, width=${it.size.width}",
                    it.getLineLeft(line) >= -1f && it.getLineRight(line) <= it.size.width + 1f)
            }
        }
    }

    private fun assertOrder(first: ExampleSentence, second: ExampleSentence) {
        val a = compose.onNodeWithText(first.text).fetchSemanticsNode().boundsInRoot
        val b = compose.onNodeWithText(second.text).fetchSemanticsNode().boundsInRoot
        assertTrue(a.bottom <= b.top)
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureUxScreenshots") != "true") return
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/lexishelf-detail-polish-$name.png",
        ).use { descriptor -> FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
    }

    private fun entry(examples: List<ExampleSentence>) = VocabularyEntry(
        id = 1, backupId = "fixture-entry", headword = "look for", languageTag = "en",
        senses = listOf(VocabularySense(10, "찾다", "verb", examples, stableId = "sense-10")),
        notes = "", tags = emptyList(), createdAtEpochMillis = 1, modifiedAtEpochMillis = 1,
    )

    private val user = ExampleSentence(1, "I am looking for a quiet place to study.", origin = ExampleOrigin.USER)
    private val dictionary = ExampleSentence(2, "I'm looking for something.", meaning = "무언가를 찾고 있다.", origin = ExampleOrigin.DICTIONARY)
    private val captured = ExampleSentence(3, "I'm looking for my keys.", meaning = "열쇠를 찾고 있어.",
        origin = ExampleOrigin.CAPTURED, sourceTitle = "Example Video", sourceLocator = "11:11",
        sourceUrl = "https://example.com/videos/fixture?timestamp=671&description=long-source-url")
    private val unknown = ExampleSentence(4, "Have you found what you were looking for?")
}
