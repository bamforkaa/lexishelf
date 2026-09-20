package com.example.localvocabulary.review.data

import com.example.localvocabulary.core.database.entity.ReviewStateEntity
import com.example.localvocabulary.core.database.entity.ReviewEventEntity
import com.example.localvocabulary.review.domain.*

internal fun ReviewStateEntity.toDomain() = ReviewState(
    stableId, senseStableId, enabled, stage, lastReviewedAt,
    nextReviewAt, schedulerVersion, generation,
)

internal fun ReviewState.toEntity() = ReviewStateEntity(
    stableId, senseStableId, enabled, stage, lastReviewedAt, nextReviewAt, schedulerVersion, generation,
)

internal fun ReviewEventEntity.toDomain() = ReviewEvent(
    stableId, reviewStateId, reviewedAt, rating?.let(ReviewRating::valueOf), ReviewEventKind.valueOf(kind),
    schedulerVersion, generation, wasNew, sessionId, retryOfEventId,
    promptDirection?.let(ReviewMode::valueOf), legacyReviewStateId,
)

internal fun ReviewEvent.toEntity() = ReviewEventEntity(
    stableId, reviewStateId, reviewedAt, rating?.name, kind.name, schedulerVersion, generation,
    wasNew, sessionId, retryOfEventId, promptDirection?.name, legacyReviewStateId,
)
