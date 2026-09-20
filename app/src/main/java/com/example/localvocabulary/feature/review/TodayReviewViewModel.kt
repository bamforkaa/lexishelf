package com.example.localvocabulary.feature.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localvocabulary.core.common.StableIdGenerator
import com.example.localvocabulary.review.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class ReviewRetry(val stateId: String, val eventId: String, val afterScheduledCount: Int)

@Serializable
internal data class ReviewSessionSnapshot(
    val sessionId: String,
    val pendingIds: List<String> = emptyList(),
    val attempt: ReviewAttempt? = null,
    val revealed: Boolean = false,
    val rated: Boolean = false,
    val pendingRating: ReviewRating? = null,
    val retries: List<ReviewRetry> = emptyList(),
    val completedIds: List<String> = emptyList(),
    val scheduledCount: Int = 0,
    val previousSenseId: String? = null,
    val exampleText: String = "",
    val exampleId: String? = null,
    val exampleSaved: Boolean = false,
)

data class TodayReviewUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val card: ReviewCard? = null,
    val revealed: Boolean = false,
    val rated: Boolean = false,
    val isRetry: Boolean = false,
    val remaining: Int = 0,
    val completed: Int = 0,
    val exampleText: String = "",
    val exampleSaved: Boolean = false,
    val error: String? = null,
    val unsupportedCount: Int = 0,
    val hasPendingEvaluation: Boolean = false,
)

sealed interface TodayReviewAction {
    data object Reveal : TodayReviewAction
    data class Rate(val rating: ReviewRating) : TodayReviewAction
    data object Next : TodayReviewAction
    data object Refresh : TodayReviewAction
    data class ExampleChanged(val text: String) : TodayReviewAction
    data object SaveExample : TodayReviewAction
}

@HiltViewModel
class TodayReviewViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ReviewRepository,
    private val ids: StableIdGenerator,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TodayReviewUiState())
    val uiState = mutableState.asStateFlow()
    private var session = savedStateHandle.get<String>(SESSION_KEY)?.let {
        runCatching { Json.decodeFromString<ReviewSessionSnapshot>(it) }.getOrNull()
    } ?: ReviewSessionSnapshot(ids.newId())

    init { launchOperation { restoreOrStart() } }

    fun onAction(action: TodayReviewAction) {
        if (mutableState.value.isBusy || mutableState.value.isLoading) return
        when (action) {
            TodayReviewAction.Reveal -> if (session.attempt != null) {
                session = session.copy(revealed = true)
                publish()
            }
            is TodayReviewAction.Rate -> if (session.revealed && !session.rated && session.pendingRating == null) {
                session = session.copy(pendingRating = action.rating)
                publish()
                launchOperation { evaluate(action.rating) }
            }
            TodayReviewAction.Next -> if (session.rated) launchOperation { next() }
            TodayReviewAction.Refresh -> launchOperation {
                // A failed write can be retried with its original idempotency key.
                if (session.pendingRating != null) evaluate(requireNotNull(session.pendingRating))
                else if (session.attempt != null && !session.rated) restoreOrStart()
                else start()
            }
            is TodayReviewAction.ExampleChanged -> if (session.rated && !session.exampleSaved) {
                session = session.copy(exampleText = action.text.take(20_000))
                publish()
            }
            TodayReviewAction.SaveExample -> if (session.rated && !session.exampleSaved && session.exampleText.isNotBlank()) {
                session = session.copy(exampleId = session.exampleId ?: ids.newId())
                persist()
                launchOperation {
                    val senseId = requireNotNull(mutableState.value.card).state.senseStableId
                    repository.addUserExample(senseId, requireNotNull(session.exampleId), session.exampleText)
                    session = session.copy(exampleSaved = true)
                    publish()
                }
            }
        }
    }

    private suspend fun restoreOrStart() {
        val attempt = session.attempt ?: return start()
        val card = repository.card(attempt.stateId)?.copy(promptDirection = attempt.promptDirection)
        if (card == null) {
            session = session.copy(attempt = null)
            return start()
        }
        mutableState.value = mutableState.value.copy(card = card)
        val recorded = repository.event(attempt.eventId)
        if (recorded != null) accept(recorded)
        else if (session.pendingRating != null) evaluate(requireNotNull(session.pendingRating))
        else if (!card.state.enabled || !ReviewScheduler.supports(card.state) ||
            card.state.generation != attempt.generation || card.state.lastReviewedAt != attempt.expectedLastReviewedAt) {
            start()
        } else publish()
    }

    private suspend fun start() {
        val queue = repository.queue()
        session = ReviewSessionSnapshot(ids.newId(), pendingIds = queue.cards.map { it.state.stableId })
        mutableState.value = mutableState.value.copy(unsupportedCount = queue.unsupportedCount)
        next()
    }

    private suspend fun next() {
        val available = repository.queue().cards.filter { it.state.stableId in session.pendingIds }
        val regular = available.getOrNull(ReviewQueueRules.nextIndex(available, session.previousSenseId))
        var retry: ReviewRetry? = null
        val card = regular ?: session.retries.firstNotNullOfOrNull { candidate ->
            if (session.scheduledCount < candidate.afterScheduledCount) return@firstNotNullOfOrNull null
            val parent = repository.event(candidate.eventId) ?: return@firstNotNullOfOrNull null
            val direction = parent.promptDirection ?: return@firstNotNullOfOrNull null
            repository.card(candidate.stateId)?.takeIf {
                it.state.enabled && it.state.senseStableId != session.previousSenseId
            }?.copy(promptDirection = direction)?.also { retry = candidate }
        }
        val attempt = card?.let {
            ReviewAttempt(ids.newId(), it.state.stableId, session.sessionId,
                if (retry == null) ReviewEventKind.SCHEDULED else ReviewEventKind.RETRY,
                it.state.generation, it.state.lastReviewedAt, it.promptDirection, retry?.eventId)
        }
        session = session.copy(
            pendingIds = available.filterNot { it == regular }.map { it.state.stableId },
            attempt = attempt, revealed = false, rated = false, pendingRating = null,
            retries = session.retries.filterNot { it == retry },
            exampleId = null, exampleText = "", exampleSaved = false,
        )
        mutableState.value = mutableState.value.copy(card = card)
        publish()
    }

    private suspend fun evaluate(rating: ReviewRating) {
        val attempt = session.attempt ?: return
        when (val result = repository.record(attempt, rating)) {
            is ReviewRecordResult.Recorded -> accept(result.event)
            is ReviewRecordResult.Unavailable -> {
                session = session.copy(attempt = null, pendingRating = null)
                start()
                mutableState.value = mutableState.value.copy(error = result.message)
            }
        }
    }

    private fun accept(event: ReviewEvent) {
        if (event.stableId !in session.completedIds) {
            val scheduledCount = session.scheduledCount + if (event.kind == ReviewEventKind.SCHEDULED) 1 else 0
            val retry = if (event.kind == ReviewEventKind.SCHEDULED && event.rating == ReviewRating.FORGOT) {
                listOf(ReviewRetry(event.reviewStateId, event.stableId, scheduledCount + 1))
            } else emptyList()
            session = session.copy(
                completedIds = session.completedIds + event.stableId, scheduledCount = scheduledCount,
                retries = session.retries + retry,
            )
        }
        session = session.copy(
            rated = true, revealed = true, pendingRating = null,
            previousSenseId = mutableState.value.card?.state?.senseStableId,
        )
        publish()
    }

    private fun launchOperation(block: suspend () -> Unit) {
        mutableState.value = mutableState.value.copy(isBusy = true, error = null)
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableState.value = mutableState.value.copy(error = error.message ?: "복습을 저장하지 못했습니다. 다시 시도하세요.")
            } finally {
                mutableState.value = mutableState.value.copy(isLoading = false, isBusy = false)
            }
        }
    }

    private fun publish() {
        persist()
        mutableState.value = mutableState.value.copy(
            revealed = session.revealed, rated = session.rated,
            isRetry = session.attempt?.kind == ReviewEventKind.RETRY,
            remaining = session.pendingIds.size + if (session.attempt != null && !session.rated) 1 else 0,
            completed = session.scheduledCount, exampleText = session.exampleText, exampleSaved = session.exampleSaved,
            hasPendingEvaluation = session.pendingRating != null,
        )
    }

    private fun persist() { savedStateHandle[SESSION_KEY] = Json.encodeToString(session) }

    internal companion object { const val SESSION_KEY = "today_review_session" }
}
