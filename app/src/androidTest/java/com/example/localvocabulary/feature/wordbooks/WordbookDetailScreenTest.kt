package com.example.localvocabulary.feature.wordbooks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.localvocabulary.core.ui.theme.LocalVocabularyTheme
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularySense
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import com.example.localvocabulary.vocabulary.domain.VocabularyWordbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WordbookDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun existingMembershipIsCheckedDisabledAndNewRowsDispatchSelection() {
        val actions = mutableListOf<WordbookDetailAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                WordbookDetailScreen(
                    state = state(
                        mode = WordbookSelectionMode.ADD,
                        membershipEntryIds = setOf(1),
                        entries = listOf(entry(1, "long"), entry(2, "soul")),
                    ),
                    onAction = actions::add,
                    onBack = {},
                    onOpenWord = {},
                )
            }
        }

        composeRule.onNodeWithTag("wordbook_entry_checkbox_1")
            .assertIsOn()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("이미 포함됨").assertIsDisplayed()
        composeRule.onNodeWithText("soul").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(WordbookDetailAction.EntryToggled(2)), actions)
        }
    }

    @Test
    fun browseActionsAndSelectionFiltersAreClear() {
        val actions = mutableListOf<WordbookDetailAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                WordbookDetailScreen(
                    state = state(entries = listOf(entry(1, "long")), membershipEntryIds = setOf(1)),
                    onAction = actions::add,
                    onBack = {},
                    onOpenWord = {},
                )
            }
        }

        composeRule.onNodeWithText("단어 추가").performClick()
        composeRule.onNodeWithText("선택").performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(WordbookDetailAction.StartAdding, WordbookDetailAction.StartRemoving),
                actions,
            )
        }
    }

    @Test
    fun removeConfirmationSaysVocabularyItselfIsPreserved() {
        composeRule.setContent {
            LocalVocabularyTheme {
                WordbookDetailScreen(
                    state = state(
                        mode = WordbookSelectionMode.REMOVE,
                        entries = listOf(entry(1, "long")),
                        membershipEntryIds = setOf(1),
                        selectedEntryIds = setOf(1),
                        isRemoveConfirmationVisible = true,
                    ),
                    onAction = {},
                    onBack = {},
                    onOpenWord = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "선택한 1개 단어를 ‘TOEIC 950’에서 제거하시겠습니까?\n" +
                "저장된 단어 자체는 삭제되지 않습니다.",
        ).assertIsDisplayed()
    }

    @Test
    fun selectionListCanReachLastItemAndSwipeDoesNotSelectARow() {
        val actions = mutableListOf<WordbookDetailAction>()
        composeRule.setContent {
            LocalVocabularyTheme {
                WordbookDetailScreen(
                    state = state(
                        mode = WordbookSelectionMode.ADD,
                        entries = (1L..30L).map { entry(it, "word-$it") },
                    ),
                    onAction = actions::add,
                    onBack = {},
                    onOpenWord = {},
                )
            }
        }

        composeRule.onNodeWithText("word-1").performTouchInput { swipeUp() }
        composeRule.runOnIdle { assertTrue(actions.isEmpty()) }
        composeRule.onNodeWithTag("wordbook_entry_list")
            .performScrollToNode(hasText("word-30"))
        composeRule.onNodeWithText("word-30").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(WordbookDetailAction.EntryToggled(30)), actions)
        }
    }

    private fun state(
        mode: WordbookSelectionMode = WordbookSelectionMode.BROWSE,
        entries: List<VocabularyEntry> = emptyList(),
        membershipEntryIds: Set<Long> = emptySet(),
        selectedEntryIds: Set<Long> = emptySet(),
        isRemoveConfirmationVisible: Boolean = false,
    ) = WordbookDetailUiState(
        isLoading = false,
        wordbook = VocabularyWordbook(8, "wordbook-8", "TOEIC 950"),
        entries = entries,
        membershipEntryIds = membershipEntryIds,
        mode = mode,
        selectedEntryIds = selectedEntryIds,
        isRemoveConfirmationVisible = isRemoveConfirmationVisible,
        languages = listOf("en", "ja"),
        tags = listOf(VocabularyTag(9, "tag-9", "어려움")),
    )

    private fun entry(id: Long, headword: String) = VocabularyEntry(
        id = id,
        backupId = "entry-$id",
        headword = headword,
        languageTag = "en",
        senses = listOf(VocabularySense(id * 10, "meaning-$id", "", emptyList())),
        notes = "",
        tags = emptyList(),
        createdAtEpochMillis = 1,
        modifiedAtEpochMillis = 1,
    )
}
