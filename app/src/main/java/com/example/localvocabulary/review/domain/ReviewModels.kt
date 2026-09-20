package com.example.localvocabulary.review.domain

import com.example.localvocabulary.vocabulary.domain.ExampleSentence
import kotlinx.serialization.Serializable

@Serializable
enum class ReviewMode { MEANING_TO_EXPRESSION, EXPRESSION_TO_MEANING }

@Serializable
enum class ReviewRating { FORGOT, HARD, REMEMBERED }

@Serializable
enum class ReviewEventKind { SCHEDULED, RETRY, RESET }

@Serializable
data class ReviewState(
    val stableId: String,
    val senseStableId: String,
    val enabled: Boolean = true,
    val stage: Int = -1,
    val lastReviewedAt: Long? = null,
    val nextReviewAt: Long,
    val schedulerVersion: String = ReviewPolicy.VERSION,
    val generation: Int = 0,
)

@Serializable
data class ReviewEvent(
    val stableId: String,
    val reviewStateId: String,
    val reviewedAt: Long,
    val rating: ReviewRating?,
    val kind: ReviewEventKind,
    val schedulerVersion: String,
    val generation: Int,
    val wasNew: Boolean = false,
    val sessionId: String? = null,
    val retryOfEventId: String? = null,
    val promptDirection: ReviewMode? = null,
    // Directional states had independent generation counters. Keep their historical identity.
    val legacyReviewStateId: String? = null,
)

data class ReviewCard(
    val state: ReviewState,
    val entryId: Long,
    val expression: String,
    val meaning: String,
    val examples: List<ExampleSentence>,
    val provenance: com.example.localvocabulary.vocabulary.domain.DictionaryProvenance? = null,
    val promptDirection: ReviewMode = ReviewMode.MEANING_TO_EXPRESSION,
)

@Serializable
data class ReviewAttempt(
    val eventId: String,
    val stateId: String,
    val sessionId: String,
    val kind: ReviewEventKind,
    val generation: Int,
    val expectedLastReviewedAt: Long?,
    val promptDirection: ReviewMode,
    val retryOfEventId: String? = null,
)

data class ReviewLimits(val newPerDay: Int = 15, val totalPerDay: Int = 40) {
    init {
        require(newPerDay in 0..100 && totalPerDay in 1..500 && newPerDay <= totalPerDay)
    }
}

data class ReviewQueue(
    val cards: List<ReviewCard> = emptyList(),
    val newAvailable: Int = 0,
    val completedToday: Int = 0,
    val unsupportedCount: Int = 0,
)

data class MeaningReviewChange(val stateId: String, val senseStableId: String, val meaning: String)

sealed interface ReviewRecordResult {
    data class Recorded(val event: ReviewEvent) : ReviewRecordResult
    data class Unavailable(val message: String) : ReviewRecordResult
}
