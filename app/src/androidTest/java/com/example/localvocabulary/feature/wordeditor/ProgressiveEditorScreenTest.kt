package com.example.localvocabulary.feature.wordeditor

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localvocabulary.core.ui.theme.LocalVocabularyTheme
import com.example.localvocabulary.feature.worddetail.WordDetailScreen
import com.example.localvocabulary.feature.worddetail.WordDetailUiState
import com.example.localvocabulary.vocabulary.domain.DictionaryProvenance
import com.example.localvocabulary.vocabulary.domain.ExampleOrigin
import com.example.localvocabulary.vocabulary.domain.ExampleSentence
import com.example.localvocabulary.vocabulary.domain.ImportedDictionaryField
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularySense
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProgressiveEditorScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun quickEditorKeepsOnlyLearningFieldsOpen() {
        val actions = mutableListOf<WordEditorAction>()
        compose.setContent {
            LocalVocabularyTheme(darkTheme = false) {
                WordEditorScreen(editorState, actions::add, {})
            }
        }
        compose.onNodeWithTag("headword").assertIsDisplayed()
        compose.onNodeWithTag("meaning_1").assertIsDisplayed()
        compose.onNodeWithTag("context_text_2").assertIsDisplayed()
        compose.onNodeWithTag("context_MEANING_2").assertDoesNotExist()
        compose.onNodeWithTag("notes").assertDoesNotExist()
        compose.onNodeWithTag("reading").assertDoesNotExist()
        compose.onNodeWithTag("create_tag").assertDoesNotExist()
        capture("editor-light")
        compose.onNodeWithTag("save_word").performClick()
        compose.runOnIdle { assertEquals(listOf(WordEditorAction.Save), actions) }
    }

    @Test fun contextDetailsSurviveSaveableStateRestorationAndClosingDoesNotClearValues() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            LocalVocabularyTheme { WordEditorScreen(editorState, {}, {}) }
        }
        clickSection("context_details_2")
        scrollTo("context_MEANING_2")
        compose.onNodeWithTag("context_MEANING_2").assertTextContains("일정에 맞춰 마무리해 주세요.")
        restoration.emulateSavedInstanceStateRestore()
        scrollTo("context_SOURCE_URL_2")
        compose.onNodeWithTag("context_SOURCE_URL_2").assertTextContains("https://example.com/context")
        clickSection("context_details_2")
        compose.onNodeWithTag("context_SOURCE_URL_2").assertDoesNotExist()
        clickSection("context_details_2")
        scrollTo("context_SOURCE_URL_2")
        compose.onNodeWithTag("context_SOURCE_URL_2").assertTextContains("https://example.com/context")
    }

    @Test fun notesLanguageAndOrganizationRestoreIndependently() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            LocalVocabularyTheme { WordEditorScreen(editorState, {}, {}) }
        }
        clickSection("editor_notes")
        clickSection("editor_organization")
        restoration.emulateSavedInstanceStateRestore()
        scrollTo("notes")
        compose.onNodeWithTag("notes").assertTextContains("Use in a meeting")
        scrollTo("create_wordbook")
        compose.onNodeWithTag("create_wordbook").assertIsDisplayed()
        compose.onNodeWithTag("reading").assertDoesNotExist()
        clickSection("editor_linguistics")
        scrollTo("reading")
        compose.onNodeWithTag("reading").assertTextContains("reading fixture")
        clickSection("editor_linguistics")
        compose.onNodeWithTag("reading").assertDoesNotExist()
        scrollTo("create_tag")
        compose.onNodeWithTag("create_tag").assertIsDisplayed()
    }

    @Test fun longExpressionAndTwoHundredPercentFontRemainEditableInDarkMode() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                LocalVocabularyTheme(darkTheme = true) {
                    WordEditorScreen(editorState.copy(headword = "I don't think that necessarily follows from what we discussed."), {}, {})
                }
            }
        }
        compose.onNodeWithTag("headword").assertTextContains("I don't think that necessarily follows from what we discussed.")
        compose.onNodeWithTag("save_word").assertIsDisplayed()
        capture("editor-dark-large-font")
        scrollTo("context_text_2")
        compose.onNodeWithTag("context_text_2").assertIsDisplayed()
        clickSection("editor_organization")
        scrollTo("create_tag")
        compose.onNodeWithTag("create_tag").assertIsDisplayed()
    }

    @Test fun detailShowsContextAndOneReviewActionWithoutDictionaryProvenance() {
        val actions = mutableListOf<com.example.localvocabulary.feature.worddetail.WordDetailAction>()
        compose.setContent {
            LocalVocabularyTheme(darkTheme = false) {
                WordDetailScreen(WordDetailUiState.Content(detailEntry), actions::add, {}, {}, {})
            }
        }
        compose.onNodeWithText("finish on time").assertIsDisplayed()
        compose.onNodeWithText("Please finish on time.").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Meeting"))
        compose.onNodeWithText("Meeting").assertIsDisplayed()
        for (text in listOf(provenance.sourceName, provenance.licenseName, "Test Dictionary", "수정됨")) {
            compose.onNodeWithText(text).assertDoesNotExist()
        }
        compose.onNodeWithTag("sense_provenance_1").assertDoesNotExist()
        scrollTo("review_enable_sense-1")
        compose.onAllNodesWithText("복습에 추가").assertCountEquals(1)
        compose.onNodeWithTag("review_enable_sense-1").performClick()
        compose.runOnIdle {
            assertEquals(listOf(com.example.localvocabulary.feature.worddetail.WordDetailAction.SetReviewEnabled("sense-1", true)), actions)
        }
        capture("detail-light")
    }

    @Test fun multipleSensesKeepLearningContentVisibleInDarkMode() {
        val second = detailEntry.senses.single().copy(id = 3, stableId = "sense-3", meaning = "다른 뜻",
            examples = emptyList(), provenance = provenance.copy(sourceSenseId = "second-sense"))
        compose.setContent {
            LocalVocabularyTheme(darkTheme = true) {
                WordDetailScreen(WordDetailUiState.Content(detailEntry.copy(senses = detailEntry.senses + second)), {}, {}, {}, {})
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("다른 뜻"))
        compose.onNodeWithText("다른 뜻").assertIsDisplayed()
        scrollTo("review_enable_sense-3")
        compose.onNodeWithTag("review_enable_sense-3").assertIsDisplayed()
        compose.onNodeWithTag("sense_provenance_3").assertDoesNotExist()
        compose.onNodeWithText(provenance.licenseName).assertDoesNotExist()
        capture("detail-dark-multiple-senses")
    }

    @Test fun editorShowsCompactSourceAndGroupsSecondaryActions() {
        var density = 1f
        val actions = mutableListOf<WordEditorAction>()
        compose.setContent {
            density = LocalDensity.current.density
            LocalVocabularyTheme(darkTheme = false) {
                WordEditorScreen(editorState.copy(senses = editorState.senses.map { it.copy(provenance = provenance) }), actions::add, {})
            }
        }
        compose.onNodeWithTag("sense_provenance").assertTextContains("Test Dictionary · 수정됨")
        compose.onNodeWithTag("sense_provenance").assert(hasClickAction().not())
        compose.onNodeWithText(provenance.licenseName).assertDoesNotExist()
        scrollTo("editor_review_1")
        compose.onNodeWithTag("editor_review_1").performClick()
        compose.runOnIdle { assertEquals(WordEditorAction.SetSenseReviewEnabled(1, false), actions.single()) }
        scrollTo("editor_organization")
        fun label(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        fun assertGap(first: String, second: String, min: Float, max: Float) {
            val gap = (label(second).top - label(first).bottom) / density
            assertTrue("$first -> $second: $gap dp", gap in min..max)
        }
        // Each padding rounds to physical pixels independently; allow their combined rounding.
        assertGap("context_details_label_2", "add_context_label_1", 9f, 11f)
        assertGap("add_context_label_1", "add_sense_label", 23f, 25f)
        assertGap("add_sense_label", "editor_notes_label", 11f, 13f)
        assertGap("editor_notes_label", "editor_linguistics_label", 11f, 13f)
        assertGap("editor_linguistics_label", "editor_organization_label", 11f, 13f)
        for (tag in listOf("context_details_2", "add_context_1", "add_sense", "editor_notes",
            "editor_linguistics", "editor_organization")) {
            val action = compose.onNodeWithTag(tag)
            // Text and padding round independently to physical pixels.
            val height = action.fetchSemanticsNode().boundsInRoot.height / density
            assertTrue("$tag height: $height dp", height in 28f..29f)
            action.assertTouchHeightIsEqualTo(48.dp)
        }
        // Exercise real pointer hit testing where adjacent expanded targets are closest.
        compose.onNodeWithTag("add_context_1").performTouchInput { click(center.copy(y = 1f)) }
        compose.onNodeWithTag("add_sense").performTouchInput { click(center.copy(y = height - 1f)) }
        compose.runOnIdle {
            assertEquals(listOf(WordEditorAction.SetSenseReviewEnabled(1, false),
                WordEditorAction.AddExample(1), WordEditorAction.AddSense), actions)
        }
        capture("editor-action-group")
    }

    @Test fun twoExpandedSensesAndLargeFontRemainReadableInLightTheme() {
        val fontScale = mutableStateOf(1f)
        val second = EditableSense(3, meaning = "일정을 지키다", examples = listOf(
            EditableExample(4, text = "We should finish on time.")))
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = fontScale.value)) {
                LocalVocabularyTheme(darkTheme = false) {
                    WordEditorScreen(editorState.copy(senses = editorState.senses + second), {}, {})
                }
            }
        }
        scrollTo("context_text_4")
        compose.onNodeWithTag("meaning_3").assertIsDisplayed()
        capture("editor-light-two-senses")
        compose.runOnIdle { fontScale.value = 2f }
        scrollTo("meaning_1")
        compose.onNodeWithTag("meaning_1").assertIsDisplayed()
        scrollTo("meaning_3")
        compose.onNodeWithTag("meaning_3").assertIsDisplayed()
        capture("editor-light-two-senses-large-font")
        scrollTo("editor_organization")
        compose.onNodeWithTag("editor_organization").assertIsDisplayed()
        for (tag in listOf("add_sense_label", "editor_notes_label", "editor_linguistics_label", "editor_organization_label")) {
            compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
        }
        capture("editor-light-large-font-actions")
    }

    @Test fun keyboardOpenPreservesContextInputFocus() {
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            LocalVocabularyTheme(darkTheme = false) { WordEditorScreen(editorState, {}, {}) }
        }
        compose.runOnIdle {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                view.context.getSystemService(android.content.ClipboardManager::class.java).clearPrimaryClip()
            }
        }
        compose.onNodeWithTag("context_text_2").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        compose.onNodeWithTag("context_text_2").assertIsFocused()
        compose.onNodeWithTag("save_word").assertIsDisplayed()
        capture("editor-light-keyboard-input")
    }

    private fun scrollTo(tag: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun clickSection(tag: String) {
        scrollTo(tag)
        compose.onNodeWithTag(tag).performClick()
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureUxScreenshots") != "true") return
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/lexishelf-detail-polish-$name.png",
        ).use { descriptor -> FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
    }

    private val provenance = DictionaryProvenance(
        providerId = "fixture.dictionary", sourceEntryId = "fixture-entry", sourceSenseId = "fixture-sense",
        sourceName = "Test Dictionary · Fixture attribution", sourceUrl = "https://example.com/dictionary",
        licenseName = "Test fixture license", licenseUrl = "https://example.com/license",
        datasetVersion = "fixture-version", importedFields = setOf(ImportedDictionaryField.MEANING, ImportedDictionaryField.EXAMPLES),
        importedAtEpochMillis = 1_700_000_000_000, modifiedAfterImport = true,
    )
    private val editorState = WordEditorUiState(
        isLoading = false, headword = "finish on time", notes = "Use in a meeting", reading = "reading fixture",
        senses = listOf(EditableSense(1, meaning = "제시간에 마치다", examples = listOf(EditableExample(
            2, text = "Please finish on time.", meaning = "일정에 맞춰 마무리해 주세요.",
            sourceUrl = "https://example.com/context", sourceTitle = "Meeting", origin = ExampleOrigin.CAPTURED,
        )))),
    )
    private val detailEntry = VocabularyEntry(
        id = 1, backupId = "fixture-entry", headword = "finish on time", languageTag = "en",
        senses = listOf(VocabularySense(1, "제시간에 마치다", "", listOf(ExampleSentence(
            id = 2, text = "Please finish on time.", meaning = "일정에 맞춰 마무리해 주세요.",
            origin = ExampleOrigin.CAPTURED, sourceTitle = "Meeting",
        )), provenance = provenance, stableId = "sense-1")),
        notes = "", tags = emptyList(), createdAtEpochMillis = 1, modifiedAtEpochMillis = 1,
    )
}
