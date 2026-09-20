package com.example.localvocabulary.review.domain

import com.example.localvocabulary.vocabulary.domain.ValidatedVocabularyDraft
import kotlinx.coroutines.flow.Flow

interface ReviewRepository {
    fun observeStates(): Flow<List<ReviewState>>
    suspend fun queue(): ReviewQueue
    suspend fun card(stateId: String): ReviewCard?
    suspend fun events(stateId: String): List<ReviewEvent>
    suspend fun event(eventId: String): ReviewEvent?
    suspend fun setEnabled(senseStableId: String, enabled: Boolean)
    suspend fun record(attempt: ReviewAttempt, rating: ReviewRating): ReviewRecordResult
    suspend fun addUserExample(senseStableId: String, exampleId: String, text: String)
    suspend fun meaningChanges(draft: ValidatedVocabularyDraft): List<MeaningReviewChange>
    /** Vocabulary edits and selected progression resets commit together. */
    suspend fun saveVocabulary(
        draft: ValidatedVocabularyDraft, resetStateIds: Set<String>,
        reviewEnabledBySenseIndex: Map<Int, Boolean> = emptyMap(),
    ): Long
}
