package com.example.localvocabulary.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
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

        scrollTo("JMdict")
        composeRule.onNodeWithText("JMdict").assertIsDisplayed()
        scrollTo("설치됨 · 108 MiB")
        composeRule.onNodeWithText("설치됨 · 108 MiB").assertIsDisplayed()
        scrollTo("로컬 pack 가져오기")
        composeRule.onNodeWithText("로컬 pack 가져오기").performClick()
        scrollTo("삭제")
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

        scrollTo("한국어 뜻")
        composeRule.onNodeWithText("한국어 뜻").assertIsDisplayed()
        scrollTo("한국어기초사전")
        composeRule.onNodeWithText("한국어기초사전").assertIsDisplayed()
        scrollTo("권장 · 현재 사용 언어에 적합")
        composeRule.onNodeWithText("권장 · 현재 사용 언어에 적합").assertIsDisplayed()
        scrollTo("다운로드")
        composeRule.onNodeWithText("다운로드").performClick()
        composeRule.onNodeWithText("JMdict").assertDoesNotExist()

        composeRule.runOnIdle {
            assertTrue(
                actions.contains(SettingsAction.DownloadDictionaryPack("korean-basic.multilingual")),
            )
        }
    }

    @Test fun savedSourceLegalInformationRemainsAccessibleWithoutInstalledProvider() {
        val source = DictionarySourceUiState("retired-provider", "Fixture Dictionary",
            "https://example.com/source", "Fixture license", "https://example.com/license",
            "Full attribution from the saved entry", null, null, null, null, null,
            savedDatasetVersion = "fixture-version")
        val opened = mutableListOf<String>()
        composeRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened += uri }
            }) {
                LocalVocabularyTheme {
                    SettingsScreen(SettingsUiState(isLoading = false, savedDictionarySources = listOf(source)), {}, {}, {})
                }
            }
        }
        scrollTo("저장된 항목의 출처 확인")
        composeRule.onNodeWithTag("saved_dictionary_sources").performClick()
        for (text in listOf(source.providerName, source.attributionNotice!!, "라이선스 · ${source.licenseName}")) {
            scrollTo(text)
            composeRule.onNodeWithText(text).assertIsDisplayed()
        }
        for (label in listOf("원본 사이트", "라이선스")) {
            scrollTo(label)
            composeRule.onNodeWithText(label).performClick()
        }
        composeRule.runOnIdle { assertEquals(listOf(source.sourceUrl, source.licenseUrl), opened) }
    }

    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }
}
