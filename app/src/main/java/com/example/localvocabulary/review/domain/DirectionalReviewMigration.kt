package com.example.localvocabulary.review.domain

data class LegacyDirectionalReviewState(val state: ReviewState, val mode: ReviewMode)

data class MigratedReviews(val states: List<ReviewState>, val events: List<ReviewEvent>)

/** Shared by Room v8 and JSON v7 conversion, independent of row or array order. */
object DirectionalReviewMigration {
    fun merge(states: List<LegacyDirectionalReviewState>, events: List<ReviewEvent>): MigratedReviews {
        val byId = states.associateBy { it.state.stableId }
        require(byId.size == states.size)
        require(states.map { it.state.senseStableId to it.mode }.distinct().size == states.size)
        require(events.all { it.reviewStateId in byId })
        val eventsByState = events.groupBy { it.reviewStateId }
        val canonicalIds = mutableMapOf<String, String>()
        val merged = states.groupBy { it.state.senseStableId }.toSortedMap().map { (_, group) ->
            val ordered = group.sortedBy { it.state.stableId }
            val canonical = ordered.first().state
            ordered.forEach { canonicalIds[it.state.stableId] = canonical.stableId }
            val maxGeneration = maxOf(ordered.maxOf { it.state.generation },
                ordered.maxOf { legacy -> eventsByState[legacy.state.stableId]?.maxOfOrNull { it.generation } ?: 0 })
            // Never reinterpret an unknown scheduler as steps-v1. Its history retains exact versions.
            val scheduler = ordered.firstOrNull { it.state.schedulerVersion != ReviewPolicy.VERSION }
                ?.state?.schedulerVersion ?: ReviewPolicy.VERSION
            canonical.copy(
                enabled = ordered.any { it.state.enabled },
                stage = ordered.minOf { it.state.stage },
                lastReviewedAt = if (ordered.any { it.state.lastReviewedAt == null }) null
                    else ordered.minOf { requireNotNull(it.state.lastReviewedAt) },
                nextReviewAt = ordered.minOf { it.state.nextReviewAt },
                schedulerVersion = scheduler,
                // The new progression does not reuse either legacy generation namespace.
                generation = if (maxGeneration == Int.MAX_VALUE) maxGeneration else maxGeneration + 1,
            )
        }
        return MigratedReviews(merged, events.map { event ->
            val source = byId.getValue(event.reviewStateId)
            event.copy(
                reviewStateId = canonicalIds.getValue(event.reviewStateId),
                promptDirection = source.mode,
                legacyReviewStateId = event.reviewStateId,
            )
        })
    }
}
