package com.example.localvocabulary.feature.review

import androidx.lifecycle.SavedStateHandle
import com.example.localvocabulary.core.common.StableIdGenerator
import com.example.localvocabulary.feature.wordlist.MainDispatcherRule
import com.example.localvocabulary.review.FakeReviewRepository
import com.example.localvocabulary.review.domain.*
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TodayReviewViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private var id = 0
    private val ids = StableIdGenerator { "generated-${++id}" }
    private fun repository(count: Int = 2) = FakeReviewRepository().apply {
        repeat(count) { index -> cards.add(ReviewCard(
            ReviewState("state-$index", "sense-$index", nextReviewAt = now),
            1, "expression $index", "meaning $index", emptyList(),
        )) }
    }

    @Test fun `rating and next are ignored before reveal and only one rating is committed`() = runTest {
        val repository = repository()
        val vm = TodayReviewViewModel(SavedStateHandle(), repository, ids)
        advanceUntilIdle()
        vm.onAction(TodayReviewAction.Rate(ReviewRating.REMEMBERED))
        vm.onAction(TodayReviewAction.Next)
        assertTrue(repository.history.isEmpty())
        assertFalse(vm.uiState.value.revealed)
        vm.onAction(TodayReviewAction.Reveal)
        vm.onAction(TodayReviewAction.Rate(ReviewRating.REMEMBERED))
        vm.onAction(TodayReviewAction.Rate(ReviewRating.REMEMBERED))
        advanceUntilIdle()
        assertEquals(1, repository.history.size)
        assertTrue(vm.uiState.value.rated)
    }

    @Test fun `forgot retries once behind another scheduled question without advancing stage`() = runTest {
        val repository = repository()
        val vm = TodayReviewViewModel(SavedStateHandle(), repository, ids)
        advanceUntilIdle()
        val firstDirection = vm.uiState.value.card!!.promptDirection
        val firstId = vm.uiState.value.card!!.state.stableId
        vm.onAction(TodayReviewAction.Reveal)
        vm.onAction(TodayReviewAction.Rate(ReviewRating.FORGOT))
        advanceUntilIdle()
        val afterFailure = repository.card(firstId)!!.state
        vm.onAction(TodayReviewAction.Next)
        advanceUntilIdle()
        assertNotEquals(firstId, vm.uiState.value.card!!.state.stableId)
        vm.onAction(TodayReviewAction.Reveal)
        vm.onAction(TodayReviewAction.Rate(ReviewRating.REMEMBERED))
        advanceUntilIdle()
        vm.onAction(TodayReviewAction.Next)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isRetry)
        assertEquals(firstDirection, vm.uiState.value.card!!.promptDirection)
        assertEquals(firstId, vm.uiState.value.card!!.state.stableId)
        vm.onAction(TodayReviewAction.Reveal)
        vm.onAction(TodayReviewAction.Rate(ReviewRating.REMEMBERED))
        advanceUntilIdle()
        assertEquals(afterFailure, repository.card(firstId)!!.state)
        assertEquals(ReviewEventKind.RETRY, repository.history.last().kind)
        assertEquals(firstDirection, repository.history.last().promptDirection)
        vm.onAction(TodayReviewAction.Next)
        advanceUntilIdle()
        assertNull(vm.uiState.value.card)
        assertEquals(2, vm.uiState.value.completed)
    }

    @Test fun `single forgotten card ends without immediate retry loop`() = runTest {
        val repository = repository(1)
        val vm = TodayReviewViewModel(SavedStateHandle(), repository, ids)
        advanceUntilIdle()
        vm.onAction(TodayReviewAction.Reveal)
        vm.onAction(TodayReviewAction.Rate(ReviewRating.FORGOT))
        advanceUntilIdle()
        vm.onAction(TodayReviewAction.Next)
        advanceUntilIdle()
        assertNull(vm.uiState.value.card)
        assertEquals(1, repository.history.size)
    }

    @Test fun `process recreation restores answer and detects already committed evaluation`() = runTest {
        val repository = repository()
        val handle = SavedStateHandle()
        val vm = TodayReviewViewModel(handle, repository, ids)
        advanceUntilIdle()
        vm.onAction(TodayReviewAction.Reveal)
        val revealedSnapshot = handle.get<String>(TodayReviewViewModel.SESSION_KEY)
        val restored = TodayReviewViewModel(SavedStateHandle(mapOf(TodayReviewViewModel.SESSION_KEY to revealedSnapshot)), repository, ids)
        advanceUntilIdle()
        assertTrue(restored.uiState.value.revealed)
        repository.failAfterCommit = true
        vm.onAction(TodayReviewAction.Rate(ReviewRating.REMEMBERED))
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.error)
        val restarted = TodayReviewViewModel(SavedStateHandle(mapOf(TodayReviewViewModel.SESSION_KEY to handle.get<String>(TodayReviewViewModel.SESSION_KEY))), repository, ids)
        advanceUntilIdle()
        assertTrue(restarted.uiState.value.rated)
        assertEquals(repository.history.single().promptDirection, restarted.uiState.value.card!!.promptDirection)
        restarted.onAction(TodayReviewAction.Rate(ReviewRating.REMEMBERED))
        assertEquals(1, repository.history.size)
    }

    @Test fun `leaving and reentering excludes completed scheduled work and preserves unsaved work`() = runTest {
        val repository = repository()
        val first = TodayReviewViewModel(SavedStateHandle(), repository, ids)
        advanceUntilIdle()
        val original = first.uiState.value.card!!.state.stableId
        val unreviewed = TodayReviewViewModel(SavedStateHandle(), repository, ids)
        advanceUntilIdle()
        assertEquals(original, unreviewed.uiState.value.card!!.state.stableId)
        first.onAction(TodayReviewAction.Reveal)
        first.onAction(TodayReviewAction.Rate(ReviewRating.HARD))
        advanceUntilIdle()
        val next = TodayReviewViewModel(SavedStateHandle(), repository, ids)
        advanceUntilIdle()
        assertNotEquals(original, next.uiState.value.card!!.state.stableId)
    }

    @Test fun `own sentence saves once without another rating and draft survives recreation`() = runTest {
        val repository = repository(1)
        val handle = SavedStateHandle()
        val vm = TodayReviewViewModel(handle, repository, ids)
        advanceUntilIdle()
        vm.onAction(TodayReviewAction.Reveal)
        vm.onAction(TodayReviewAction.Rate(ReviewRating.HARD))
        advanceUntilIdle()
        vm.onAction(TodayReviewAction.ExampleChanged("My own sentence."))
        val restored = TodayReviewViewModel(SavedStateHandle(mapOf(TodayReviewViewModel.SESSION_KEY to handle.get<String>(TodayReviewViewModel.SESSION_KEY))), repository, ids)
        advanceUntilIdle()
        assertEquals("My own sentence.", restored.uiState.value.exampleText)
        restored.onAction(TodayReviewAction.SaveExample)
        restored.onAction(TodayReviewAction.SaveExample)
        advanceUntilIdle()
        assertEquals(1, repository.examples.size)
        assertEquals(1, repository.history.size)
        assertTrue(restored.uiState.value.exampleSaved)
    }

    @Test fun `empty queue has a finished loading state`() = runTest {
        val vm = TodayReviewViewModel(SavedStateHandle(), repository(0), ids)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.card)
    }
}
