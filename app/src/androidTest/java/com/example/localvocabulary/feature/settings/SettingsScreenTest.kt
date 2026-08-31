package com.example.localvocabulary.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.localvocabulary.core.ui.theme.LocalVocabularyTheme
import com.example.localvocabulary.dictionary.catalog.DictionaryCatalogSection
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
        composeRule.onNodeWithText("로컬 pack 가져오기").performClick()
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

    @Test
    fun publicCatalogShowsPurposeSizeAndDownloadActionWithoutJmdict() {
        val actions = mutableListOf<SettingsAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        isLoading = false,
                        catalogStatus = DictionaryCatalogStatusUiState.Available(
                            catalogVersion = "v0.1.0",
                            isCached = false,
                            warning = null,
                        ),
                        catalogPacks = listOf(
                            DictionaryCatalogPackUiState(
                                packId = "korean-basic.multilingual",
                                providerId = "korean-basic-dictionary",
                                displayName = "한국어기초사전",
                                section = DictionaryCatalogSection.KOREAN_MEANINGS,
                                description = "영어·일본어·중국어 ↔ 한국어 · 수록 범위는 제한적",
                                datasetVersion = "2026-08-19",
                                downloadSizeBytes = 68L * 1024L * 1024L,
                                installedSizeBytes = 202L * 1024L * 1024L,
                                licenseName = "CC BY-SA 2.0 KR",
                                installStatus = DictionaryCatalogPackInstallStatus.NOT_INSTALLED,
                                downloadState = null,
                                isRecommended = true,
                            ),
                        ),
                    ),
                    onAction = actions::add,
                    onBack = {},
                    onChooseDictionaryPack = {},
                )
            }
        }

        composeRule.onNodeWithText("한국어 뜻").assertIsDisplayed()
        composeRule.onNodeWithText("한국어기초사전").assertIsDisplayed()
        composeRule.onNodeWithText("권장 · 현재 사용 언어에 적합").assertIsDisplayed()
        composeRule.onNodeWithText("다운로드").performClick()
        composeRule.onNodeWithText("JMdict").assertDoesNotExist()

        composeRule.runOnIdle {
            assertTrue(
                actions.contains(SettingsAction.DownloadDictionaryPack("korean-basic.multilingual")),
            )
        }
    }
}
