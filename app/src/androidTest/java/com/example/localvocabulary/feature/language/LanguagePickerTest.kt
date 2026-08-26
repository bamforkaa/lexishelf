package com.example.localvocabulary.feature.language

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.localvocabulary.core.ui.theme.LocalVocabularyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LanguagePickerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun catalogSearchAndSelectionUseHumanNameAndCanonicalTag() {
        var selected = "en"
        composeRule.setContent {
            var value by remember { mutableStateOf("en") }
            LocalVocabularyTheme {
                LanguagePickerField(
                    value = value,
                    onValueChange = {
                        value = it
                        selected = it
                    },
                    label = "언어",
                )
            }
        }

        composeRule.onNodeWithContentDescription("언어 선택").performClick()
        composeRule.onNodeWithTag("language_search").performTextReplacement("jap")
        composeRule.onNodeWithText("日本語").assertIsDisplayed().performClick()

        composeRule.onNodeWithText("日本語 · ja").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("ja", selected) }
    }

    @Test
    fun manualUnknownValidTagIsCanonicalizedAndInvalidTagIsRejected() {
        var selected = "en"
        val addedLanguages = mutableListOf<String>()
        composeRule.setContent {
            LocalVocabularyTheme {
                LanguagePickerField(
                    value = selected,
                    onValueChange = { selected = it },
                    onUserLanguageAdded = addedLanguages::add,
                    label = "언어",
                )
            }
        }

        composeRule.onNodeWithContentDescription("언어 선택").performClick()
        composeRule.onNodeWithText("직접 태그 입력").performClick()
        composeRule.onNodeWithTag("manual_language_tag")
            .performTextReplacement("not_a_tag")
        composeRule.onNodeWithTag("confirm_manual_language").performClick()
        composeRule.onNodeWithText("유효한 BCP 47 언어 태그를 입력하세요.").assertIsDisplayed()

        composeRule.onNodeWithTag("manual_language_tag").performTextReplacement("pt-br")
        composeRule.onNodeWithTag("confirm_manual_language").performClick()
        composeRule.runOnIdle {
            assertEquals("pt-BR", selected)
            assertEquals(listOf("pt-BR"), addedLanguages)
        }
    }

    @Test
    fun simplifiedAndTraditionalChineseRemainSeparateChoices() {
        composeRule.setContent {
            LocalVocabularyTheme {
                LanguagePickerField(value = "zh-Hans", onValueChange = {}, label = "언어")
            }
        }

        composeRule.onNodeWithContentDescription("언어 선택").performClick()
        composeRule.onNodeWithText("中文（简体）").assertIsDisplayed()
        composeRule.onNodeWithText("中文（繁體）").assertIsDisplayed()
        composeRule.onNodeWithText("Chinese (Simplified) · zh-Hans").assertIsDisplayed()
        composeRule.onNodeWithText("Chinese (Traditional) · zh-Hant").assertIsDisplayed()
    }

    @Test
    fun persistedUserLanguageCanBeSearchedByNameAndCode() {
        composeRule.setContent {
            LocalVocabularyTheme {
                LanguagePickerField(
                    value = "en",
                    onValueChange = {},
                    userLanguageTags = setOf("nl"),
                    label = "언어",
                )
            }
        }

        composeRule.onNodeWithContentDescription("언어 선택").performClick()
        composeRule.onNodeWithText("추가한 언어").assertIsDisplayed()
        composeRule.onNodeWithTag("language_search").performTextReplacement("Dutch")
        composeRule.onNodeWithTag("language_option_nl").assertIsDisplayed()
        composeRule.onNodeWithTag("language_search").performTextReplacement("nl")
        composeRule.onNodeWithTag("language_option_nl").assertIsDisplayed()
    }

    @Test
    fun unknownCurrentValidTagAndLastBuiltInItemRemainReachable() {
        var selected = "pt-BR"
        composeRule.setContent {
            LocalVocabularyTheme {
                LanguagePickerField(
                    value = selected,
                    onValueChange = { selected = it },
                    label = "언어",
                )
            }
        }

        composeRule.onNodeWithContentDescription("언어 선택").performClick()
        composeRule.onNodeWithTag("language_option_pt-BR").assertIsDisplayed()
            .performTouchInput { swipeUp() }
        composeRule.runOnIdle { assertEquals("pt-BR", selected) }
        composeRule.onNodeWithTag("language_catalog_list")
            .performScrollToNode(hasTestTag("language_option_la"))
        composeRule.onNodeWithTag("language_option_la").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("pt-BR", selected) }
    }
}
