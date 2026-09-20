package com.example.localvocabulary.review

import com.example.localvocabulary.review.domain.*
import com.example.localvocabulary.vocabulary.domain.ValidatedVocabularyDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.TimeZone

class FakeReviewRepository(private val vocabulary: VocabularyRepository? = null) : ReviewRepository {
    var now = 1_800_000_000_000L
    val cards = mutableListOf<ReviewCard>()
    val history = mutableListOf<ReviewEvent>()
    val examples = mutableMapOf<String, Pair<String, String>>()
    var changes = emptyList<MeaningReviewChange>()
    var resetIds = emptySet<String>()
    var failAfterCommit = false
    var reviewOverrides = emptyMap<Int, Boolean>()
    override fun observeStates() = MutableStateFlow(cards.map { it.state })
    override suspend fun queue() = ReviewQueueRules.select(cards.map { it.copy(promptDirection = ReviewDirectionPolicy.next(it.state.stableId, history)) }, history, ReviewLimits(), now, TimeZone.getDefault())
    override suspend fun card(stateId: String) = cards.firstOrNull { it.state.stableId == stateId }
        ?.copy(promptDirection = ReviewDirectionPolicy.next(stateId, history))
    override suspend fun events(stateId: String) = history.filter { it.reviewStateId == stateId }
    override suspend fun event(eventId: String) = history.firstOrNull { it.stableId == eventId }
    override suspend fun setEnabled(senseStableId: String, enabled: Boolean) = Unit
    override suspend fun record(attempt: ReviewAttempt, rating: ReviewRating): ReviewRecordResult {
        event(attempt.eventId)?.let { return ReviewRecordResult.Recorded(it) }
        val current = requireNotNull(card(attempt.stateId))
        val event = ReviewEvent(attempt.eventId, attempt.stateId, now, rating, attempt.kind,
            current.state.schedulerVersion, current.state.generation,
            attempt.kind == ReviewEventKind.SCHEDULED && current.state.lastReviewedAt == null,
            attempt.sessionId, attempt.retryOfEventId, attempt.promptDirection)
        history.add(event)
        cards[cards.indexOfFirst { it.state.stableId == current.state.stableId }] = current.copy(state = ReviewScheduler.next(current.state, rating, now,
            TimeZone.getDefault(), attempt.kind))
        if (failAfterCommit) { failAfterCommit = false; error("connection lost after commit") }
        return ReviewRecordResult.Recorded(event)
    }
    override suspend fun addUserExample(senseStableId: String, exampleId: String, text: String) {
        examples[exampleId] = senseStableId to text
    }
    override suspend fun meaningChanges(draft: ValidatedVocabularyDraft) = changes
    override suspend fun saveVocabulary(draft: ValidatedVocabularyDraft, resetStateIds: Set<String>, reviewEnabledBySenseIndex: Map<Int, Boolean>): Long {
        resetIds = resetStateIds
        reviewOverrides = reviewEnabledBySenseIndex
        return requireNotNull(vocabulary).save(draft)
    }
}
