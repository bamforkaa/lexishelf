package com.example.localvocabulary.review.domain

import java.util.TimeZone

object ReviewQueueRules {
    fun select(
        cards: List<ReviewCard>, events: List<ReviewEvent>, limits: ReviewLimits,
        now: Long, zone: TimeZone,
    ): ReviewQueue {
        val day = ReviewDay.at(now, zone)
        val completed = events.filter { it.kind == ReviewEventKind.SCHEDULED && it.reviewedAt in day }
        val reviewedIds = completed.mapTo(hashSetOf()) { it.reviewStateId }
        val available = cards.filter {
            it.state.enabled && ReviewScheduler.supports(it.state) &&
                it.state.nextReviewAt <= now && it.state.stableId !in reviewedIds
        }
        val totalRemaining = (limits.totalPerDay - completed.size).coerceAtLeast(0)
        val newRemaining = (limits.newPerDay - completed.count { it.wasNew }).coerceAtLeast(0)
        val due = available.filter { it.state.lastReviewedAt != null }
            .sortedWith(compareBy<ReviewCard> { it.state.nextReviewAt }.thenBy { it.state.stableId })
            .take(totalRemaining)
        val fresh = available.filter { it.state.lastReviewedAt == null }
            .sortedWith(compareBy<ReviewCard> { it.state.nextReviewAt }.thenBy { it.state.stableId })
            .take(minOf(newRemaining, totalRemaining - due.size))
        return ReviewQueue(
            cards = due + fresh, newAvailable = fresh.size, completedToday = completed.size,
            unsupportedCount = cards.count { it.state.enabled && !ReviewScheduler.supports(it.state) },
        )
    }

    /** Keep priority order unless separating the two directions of a sense is possible. */
    fun nextIndex(cards: List<ReviewCard>, previousSenseId: String?): Int =
        cards.indexOfFirst { it.state.senseStableId != previousSenseId }.takeIf { it >= 0 } ?: 0
}

object MeaningChangeRule {
    fun isSubstantive(before: String, after: String): Boolean = normalized(before) != normalized(after)

    private fun normalized(text: String): String = text
        .replace(Regex("[\\p{P}]"), "")
        .replace(Regex("\\s+"), " ").trim()
}
