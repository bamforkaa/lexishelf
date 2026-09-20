package com.example.localvocabulary.review.domain

import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class SenseReviewRulesTest {
    private fun legacy(id: String, mode: ReviewMode, stage: Int = 2, due: Long = 100,
        generation: Int = 0, enabled: Boolean = true, last: Long? = 10) =
        LegacyDirectionalReviewState(ReviewState(id, "sense", enabled, stage, last, due,
            generation = generation), mode)

    private fun event(id: String, stateId: String, generation: Int = 0,
        kind: ReviewEventKind = ReviewEventKind.SCHEDULED) = ReviewEvent(
        id, stateId, 20, if (kind == ReviewEventKind.RESET) null else ReviewRating.FORGOT,
        kind, ReviewPolicy.VERSION, generation, sessionId = if (kind == ReviewEventKind.RESET) null else "session",
    )

    @Test fun `either legacy direction alone keeps identity schedule and event direction`() {
        ReviewMode.entries.forEach { mode ->
            val old = legacy("review", mode, generation = 3)
            val history = listOf(event("event", "review", 2), event("reset", "review", 3, ReviewEventKind.RESET))
            val result = DirectionalReviewMigration.merge(listOf(old), history)
            assertEquals(old.state.copy(generation = 4), result.states.single())
            assertEquals(listOf(2, 3), result.events.map { it.generation })
            assertTrue(result.events.all { it.promptDirection == mode && it.legacyReviewStateId == "review" })
            assertEquals(ReviewEventKind.RESET, result.events.last().kind)
        }
    }

    @Test fun `two states merge lower stage earlier due and any enabled deterministically`() {
        val states = listOf(legacy("z", ReviewMode.MEANING_TO_EXPRESSION, 4, 100, 2, false, 30),
            legacy("a", ReviewMode.EXPRESSION_TO_MEANING, 1, 500, 5, true, 50))
        val events = listOf(event("one", "z", 2), event("two", "a", 5))
        val result = DirectionalReviewMigration.merge(states, events)
        val state = result.states.single()
        assertEquals("a", state.stableId)
        assertEquals(1, state.stage)
        assertEquals(100L, state.nextReviewAt)
        assertEquals(30L, state.lastReviewedAt)
        assertEquals(6, state.generation)
        assertTrue(state.enabled)
        assertEquals(result.states, DirectionalReviewMigration.merge(states.reversed(), events.reversed()).states)
        assertEquals(setOf("one", "two"), result.events.map { it.stableId }.toSet())
        assertEquals(listOf("z", "a"), result.events.map { it.legacyReviewStateId })
        assertTrue(result.events.all { it.reviewStateId == "a" })
    }

    @Test fun `new or reset legacy direction keeps merged sense new and suspended pair stays suspended`() {
        val states = listOf(legacy("a", ReviewMode.MEANING_TO_EXPRESSION, 4, enabled = false),
            legacy("b", ReviewMode.EXPRESSION_TO_MEANING, -1, last = null, enabled = false))
        val state = DirectionalReviewMigration.merge(states, emptyList()).states.single()
        assertEquals(-1, state.stage)
        assertNull(state.lastReviewedAt)
        assertFalse(state.enabled)
    }

    @Test fun `all generations reset history and retry ancestry retain original meaning`() {
        val states = listOf(legacy("a", ReviewMode.MEANING_TO_EXPRESSION),
            legacy("b", ReviewMode.EXPRESSION_TO_MEANING))
        val old = listOf(event("scheduled", "a", 7),
            event("retry", "a", 7, ReviewEventKind.RETRY).copy(retryOfEventId = "scheduled"),
            event("reset", "b", 4, ReviewEventKind.RESET))
        val result = DirectionalReviewMigration.merge(states, old)
        assertEquals(8, result.states.single().generation)
        result.events.zip(old).forEach { (converted, original) ->
            assertEquals(original, converted.copy(reviewStateId = converted.legacyReviewStateId!!,
                promptDirection = null, legacyReviewStateId = null))
        }
    }

    @Test fun `unknown scheduler is not interpreted as steps and generation never overflows`() {
        val unknown = legacy("z", ReviewMode.EXPRESSION_TO_MEANING, generation = Int.MAX_VALUE)
            .let { it.copy(state = it.state.copy(schedulerVersion = "future-v2", stage = 50)) }
        val state = DirectionalReviewMigration.merge(listOf(
            legacy("a", ReviewMode.MEANING_TO_EXPRESSION), unknown), emptyList()).states.single()
        assertEquals("future-v2", state.schedulerVersion)
        assertFalse(ReviewScheduler.supports(state))
        assertEquals(Int.MAX_VALUE, state.generation)
    }

    @Test fun `scheduled direction alternates with balanced history over hundreds of questions`() {
        val history = mutableListOf<ReviewEvent>()
        repeat(300) { index ->
            val direction = ReviewDirectionPolicy.next("review", history)
            history.lastOrNull()?.let { assertNotEquals(it.promptDirection, direction) }
            history += event("event-$index", "review").copy(reviewedAt = index.toLong(), promptDirection = direction)
            val counts = ReviewMode.entries.map { mode -> history.count { it.promptDirection == mode } }
            assertTrue(kotlin.math.abs(counts[0] - counts[1]) <= 1)
        }
        assertEquals(150, history.count { it.promptDirection == ReviewMode.MEANING_TO_EXPRESSION })
    }

    @Test fun `legacy imbalance recovers and retries resets and other senses never skew direction`() {
        val history = (1..12).map { event("old-$it", "review").copy(
            reviewedAt = it.toLong(), promptDirection = ReviewMode.MEANING_TO_EXPRESSION) }.toMutableList()
        repeat(60) { index ->
            val direction = ReviewDirectionPolicy.next("review", history)
            if (history.takeLast(2).all { it.promptDirection == history.last().promptDirection }) {
                assertNotEquals(history.last().promptDirection, direction)
            }
            history += event("new-$index", "review").copy(reviewedAt = 20L + index,
                promptDirection = direction)
        }
        assertEquals(36, history.count { it.promptDirection == ReviewMode.MEANING_TO_EXPRESSION })
        assertEquals(36, history.count { it.promptDirection == ReviewMode.EXPRESSION_TO_MEANING })
        val next = ReviewDirectionPolicy.next("review", history)
        val noise = (1..20).flatMap { index -> listOf(
            event("retry-$index", "review", kind = ReviewEventKind.RETRY),
            event("reset-$index", "review", kind = ReviewEventKind.RESET),
            event("other-$index", "other-state"),
        ) }
        assertEquals(next, ReviewDirectionPolicy.next("review", history + noise))
        assertEquals(next, ReviewDirectionPolicy.next("review", history.reversed()))
    }

    @Test fun `scheduler progression is shared regardless of question direction`() {
        var state = ReviewState("review", "sense", nextReviewAt = 0)
        val history = mutableListOf<ReviewEvent>()
        repeat(6) { index ->
            val now = state.nextReviewAt
            val direction = ReviewDirectionPolicy.next(state.stableId, history)
            state = ReviewScheduler.next(state, ReviewRating.REMEMBERED, now, TimeZone.getTimeZone("UTC"))
            assertEquals(index, state.stage)
            assertEquals(ReviewPolicy.intervalsDays[index] * 86_400_000L, state.nextReviewAt - now)
            history += event("event-$index", state.stableId).copy(reviewedAt = now, promptDirection = direction)
        }
    }
}
