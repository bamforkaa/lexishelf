package com.example.localvocabulary.feature.tags

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.localvocabulary.core.ui.theme.LocalVocabularyTheme
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import com.example.localvocabulary.vocabulary.domain.VocabularyTagSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TagManagementScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tagRowDisplaysCountAndOpensCollection() {
        var openedId: Long? = null
        val tag = VocabularyTag(7, "tag-7", "JLPT N2")
        composeRule.setContent {
            LocalVocabularyTheme {
                TagManagementScreen(
                    state = TagManagementUiState(
                        tags = listOf(VocabularyTagSummary(tag, 128)),
                    ),
                    onAction = {},
                    onBack = {},
                    onOpenTag = { openedId = it },
                )
            }
        }

        composeRule.onNodeWithText("128개 단어").assertIsDisplayed()
        composeRule.onNodeWithText("JLPT N2").performClick()
        composeRule.runOnIdle { assertEquals(7L, openedId) }
    }

    @Test
    fun deleteConfirmationExplainsThatWordsRemainAndDispatchesConfirm() {
        val actions = mutableListOf<TagManagementAction>()
        val tag = VocabularyTag(7, "tag-7", "여행")
        composeRule.setContent {
            LocalVocabularyTheme {
                TagManagementScreen(
                    state = TagManagementUiState(
                        tags = listOf(VocabularyTagSummary(tag, 34)),
                        pendingDeleteTag = tag,
                    ),
                    onAction = actions::add,
                    onBack = {},
                    onOpenTag = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "‘여행’ 태그를 삭제하시겠습니까? 이 태그에 포함된 단어 자체는 삭제되지 않습니다.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_delete_tag").performClick()
        composeRule.runOnIdle {
            assertTrue(actions.contains(TagManagementAction.DeleteConfirmed))
        }
    }

    @Test
    fun editAndDeleteActionsHaveDistinctAccessibleLabels() {
        val tag = VocabularyTag(7, "tag-7", "여행")
        composeRule.setContent {
            LocalVocabularyTheme {
                TagManagementScreen(
                    state = TagManagementUiState(
                        tags = listOf(VocabularyTagSummary(tag, 1)),
                    ),
                    onAction = {},
                    onBack = {},
                    onOpenTag = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("여행 태그 수정").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("여행 태그 삭제").assertIsDisplayed()
    }
}
