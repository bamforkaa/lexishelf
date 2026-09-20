package com.example.localvocabulary.review.domain

import java.util.Calendar
import java.util.TimeZone

object ReviewPolicy {
    const val VERSION = "steps-v1"
    val intervalsDays: List<Int> = listOf(1, 3, 7, 14, 30, 60)
    const val HARD_MAX_DAYS = 3
    const val FORGOT_DAYS = 1
}

/** Intervals preserve local wall time across DST; daily quotas use local midnight boundaries. */
data class ReviewDay(val start: Long, val end: Long) {
    operator fun contains(time: Long): Boolean = time >= start && time < end

    companion object {
        fun at(now: Long, zone: TimeZone): ReviewDay {
            val calendar = Calendar.getInstance(zone).apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            return ReviewDay(start, calendar.timeInMillis)
        }
    }
}

object ReviewScheduler {
    fun supports(state: ReviewState): Boolean = state.schedulerVersion == ReviewPolicy.VERSION &&
        state.stage in -1..ReviewPolicy.intervalsDays.lastIndex

    fun next(
        state: ReviewState,
        rating: ReviewRating,
        now: Long,
        zone: TimeZone,
        kind: ReviewEventKind = ReviewEventKind.SCHEDULED,
    ): ReviewState {
        require(supports(state)) { "Unsupported scheduler state" }
        require(kind != ReviewEventKind.RESET)
        if (kind == ReviewEventKind.RETRY) return state
        val stage = when (rating) {
            ReviewRating.REMEMBERED -> (state.stage + 1).coerceAtMost(ReviewPolicy.intervalsDays.lastIndex)
            ReviewRating.HARD -> state.stage
            ReviewRating.FORGOT -> (state.stage - 1).coerceAtLeast(-1)
        }
        val days = when (rating) {
            ReviewRating.REMEMBERED -> ReviewPolicy.intervalsDays[stage]
            ReviewRating.HARD -> ReviewPolicy.intervalsDays[stage.coerceAtLeast(0)]
                .coerceAtMost(ReviewPolicy.HARD_MAX_DAYS)
            ReviewRating.FORGOT -> ReviewPolicy.FORGOT_DAYS
        }
        val next = Calendar.getInstance(zone).apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, days)
        }.timeInMillis
        return state.copy(stage = stage, lastReviewedAt = now, nextReviewAt = next)
    }

    fun reset(state: ReviewState, now: Long): ReviewState {
        require(state.generation < Int.MAX_VALUE) { "Review generation limit reached" }
        return state.copy(
            stage = -1, lastReviewedAt = null, nextReviewAt = now,
            schedulerVersion = ReviewPolicy.VERSION, generation = state.generation + 1,
        )
    }
}
