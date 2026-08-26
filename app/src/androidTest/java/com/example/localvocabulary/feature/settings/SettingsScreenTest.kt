package com.example.localvocabulary.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.localvocabulary.core.ui.theme.LocalVocabularyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun installedPackUsesProviderFirstHierarchyAndActionsRemainAvailable() {
        var chooseCount = 0
        val actions = mutableListOf<SettingsAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        isLoading = false,
                        installedPacks = listOf(
                            DictionaryPackUiState(
                                packId = "jmdict.ja-en",
                                providerId = "jmdict",
                                providerName = "JMdict",
                                datasetVersion = "2026-08-23",
                                sizeBytes = 108L * 1024L * 1024L,
                                canRollback = false,
                            ),
                        ),
                    ),
                    onAction = actions::add,
                    onBack = {},
                    onChooseDictionaryPack = { chooseCount += 1 },
                )
            }
        }

        composeRule.onNodeWithText("JMdict").assertIsDisplayed()
        composeRule.onNodeWithText("설치됨 · 108 MiB").assertIsDisplayed()
        composeRule.onNodeWithText("로컬 pack 설치").performClick()
        composeRule.onNodeWithText("삭제").performClick()

        composeRule.runOnIdle {
            assertEquals(1, chooseCount)
            assertTrue(actions.contains(SettingsAction.DeleteDictionaryPack("jmdict.ja-en")))
        }
    }

    @Test
    fun defaultLanguageUsesSharedPickerAndReturnsCanonicalTag() {
        val actions = mutableListOf<SettingsAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                SettingsScreen(
                    state = SettingsUiState(isLoading = false, defaultLanguageTag = "en"),
                    onAction = actions::add,
                    onBack = {},
                    onChooseDictionaryPack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("언어 선택").performClick()
        composeRule.onNodeWithText("日本語").performClick()
        composeRule.runOnIdle {
            assertTrue(actions.contains(SettingsAction.DefaultLanguageChanged("ja")))
        }
    }

    @Test
    fun manualLanguageSelectionRequestsPersistentCatalogAddition() {
        val actions = mutableListOf<SettingsAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                SettingsScreen(
                    state = SettingsUiState(isLoading = false, defaultLanguageTag = "en"),
                    onAction = actions::add,
                    onBack = {},
                    onChooseDictionaryPack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("언어 선택").performClick()
        composeRule.onNodeWithText("직접 태그 입력").performClick()
        composeRule.onNodeWithTag("manual_language_tag").performTextReplacement("NL")
        composeRule.onNodeWithTag("confirm_manual_language").performClick()

        composeRule.runOnIdle {
            assertTrue(actions.contains(SettingsAction.UserLanguageAdded("nl")))
            assertTrue(actions.contains(SettingsAction.DefaultLanguageChanged("nl")))
        }
    }
}
