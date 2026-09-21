package com.example.localvocabulary.feature.wordeditor

import android.content.ComponentName
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.localvocabulary.app.MainActivity
import com.example.localvocabulary.core.ui.theme.LocalVocabularyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Uses a real IME and the production Activity's manifest policy, rather than a fake keyboard. */
class WordEditorKeyboardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val state = mutableStateOf(
        WordEditorUiState(
            isLoading = false,
            headword = "take a break",
            senses = (1L..3L).map { key ->
                EditableSense(key, meaning = "meaning $key", examples = listOf(EditableExample(key)))
            },
        ),
    )

    @Before
    fun showEditorWithProductionWindowPolicy() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.enableEdgeToEdge()
            val policy = activity.packageManager.getActivityInfo(
                ComponentName(activity, MainActivity::class.java), 0,
            ).softInputMode
            activity.window.setSoftInputMode(policy)
            assertEquals(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                policy and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST,
            )
        }
        composeRule.setContent {
            LocalVocabularyTheme {
                WordEditorScreen(state.value, ::onAction, {})
            }
        }
    }

    @Test
    fun contextTypingKeepsTheFieldAboveTheKeyboardWithoutPanningTheToolbar() {
        assertStableTyping("context_text_3")
    }

    @Test
    fun notesTypingKeepsTheFieldAboveTheKeyboardWithoutPanningTheToolbar() {
        expand("editor_notes")
        assertStableTyping("notes")
    }

    @Test
    fun readingTypingKeepsTheFieldAboveTheKeyboardWithoutPanningTheToolbar() {
        expand("editor_linguistics")
        assertStableTyping("reading")
    }

    @Test
    fun contextMetadataTypingKeepsTheFieldAboveTheKeyboardWithoutPanningTheToolbar() {
        expand("context_details_3")
        assertStableTyping("context_SOURCE_TITLE_3")
    }

    private fun expand(tag: String) {
        composeRule.onNodeWithTag("editor_scroll").performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).performClick()
    }

    private fun assertStableTyping(tag: String) {
        val toolbarTop = composeRule.onNodeWithTag("save_word").fetchSemanticsNode().boundsInWindow.top
        composeRule.onNodeWithTag("editor_scroll").performScrollToNode(hasTestTag(tag))
        val field = composeRule.onNodeWithTag(tag)
        field.performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        // Wait for real-time IME animation as well as Compose's focus relocation to settle.
        composeRule.waitUntil(timeoutMillis = 15_000) {
            val insets = ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
            val keyboardTop = composeRule.activity.window.decorView.height -
                (insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0)
            composeRule.onNodeWithTag("editor_scroll").fetchSemanticsNode().boundsInWindow.bottom <=
                keyboardTop + 1f
        }
        composeRule.waitForIdle()
        // Prime the floating label/caret before checking equal-height, same-line edits.
        field.performTextInput("a")
        composeRule.waitForIdle()
        val fieldTop = field.fetchSemanticsNode().boundsInWindow.top
        for (character in "bcdefghijkl") {
            field.performTextInput(character.toString())
            field.assertIsFocused()
            val bounds = field.fetchSemanticsNode().boundsInWindow
            val insets = requireNotNull(ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView))
            val keyboardTop = composeRule.activity.window.decorView.height -
                insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            assertTrue("Input field must remain above the IME", bounds.bottom <= keyboardTop + 1f)
            assertEquals("Typing must not move the toolbar", toolbarTop,
                composeRule.onNodeWithTag("save_word").fetchSemanticsNode().boundsInWindow.top, 1f)
            assertEquals("Same-line typing must not bounce the field", fieldTop, bounds.top, 1f)
        }
        val value = when (tag) {
            "notes" -> state.value.notes
            "reading" -> state.value.reading
            "context_text_3" -> state.value.senses.last().examples.single().text
            else -> state.value.senses.last().examples.single().sourceTitle
        }
        assertEquals("abcdefghijkl", value)
    }

    private fun onAction(action: WordEditorAction) {
        val current = state.value
        state.value = when (action) {
            is WordEditorAction.NotesChanged -> current.copy(notes = action.value)
            is WordEditorAction.ReadingChanged -> current.copy(reading = action.value)
            is WordEditorAction.ExampleChanged -> current.copy(senses = current.senses.map { sense ->
                if (sense.key != action.senseKey) sense else sense.copy(examples = sense.examples.map {
                    if (it.key == action.exampleKey) it.copy(text = action.value) else it
                })
            })
            is WordEditorAction.ExampleMetadataChanged -> current.copy(senses = current.senses.map { sense ->
                if (sense.key != action.senseKey) sense else sense.copy(examples = sense.examples.map {
                    if (it.key == action.exampleKey) it.copy(sourceTitle = action.value) else it
                })
            })
            else -> current
        }
    }
}
