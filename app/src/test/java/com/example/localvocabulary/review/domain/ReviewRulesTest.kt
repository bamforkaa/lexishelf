package com.example.localvocabulary.review.domain

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class ReviewRulesTest {
    private val zone = TimeZone.getTimeZone("UTC")
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L
    private fun state(id: String = "state", stage: Int = -1, last: Long? = null, due: Long = now) =
        ReviewState(id, "sense-$id", stage = stage,
            lastReviewedAt = last, nextReviewAt = due)
    private fun card(state: ReviewState) = ReviewCard(state, 1, "expression", "meaning", emptyList())

    @Test fun `remembered advances through every interval and stays at sixty`() {
        var state = state()
        listOf(1, 3, 7, 14, 30, 60, 60, 60).forEach { days ->
            state = ReviewScheduler.next(state, ReviewRating.REMEMBERED, now, zone)
            assertEquals(now + days * day, state.nextReviewAt)
        }
        assertEquals(5, state.stage)
    }

    @Test fun `hard retains stage with one to three day interval`() {
        listOf(-1 to 1, 0 to 1, 1 to 3, 4 to 3, 5 to 3).forEach { (stage, days) ->
            val next = ReviewScheduler.next(state(stage = stage), ReviewRating.HARD, now, zone)
            assertEquals(stage, next.stage)
            assertEquals(now + days * day, next.nextReviewAt)
        }
    }

    @Test fun `forgot moves down one stage and is due tomorrow`() {
        for (stage in -1..5) {
            val next = ReviewScheduler.next(state(stage = stage), ReviewRating.FORGOT, now, zone)
            assertEquals((stage - 1).coerceAtLeast(-1), next.stage)
            assertEquals(now + day, next.nextReviewAt)
        }
    }

    @Test fun `retry never changes progression or scheduled timestamps for any rating`() {
        val previous = state(stage = 2, last = now - day, due = now + day)
        ReviewRating.entries.forEach { rating ->
            assertEquals(previous, ReviewScheduler.next(previous, rating, now, zone, ReviewEventKind.RETRY))
        }
    }

    @Test fun `overdue advances once from evaluation time without catching up missed intervals`() {
        val next = ReviewScheduler.next(state(stage = 1, last = now - 30 * day, due = now - 27 * day),
            ReviewRating.REMEMBERED, now, zone)
        assertEquals(2, next.stage)
        assertEquals(now + 7 * day, next.nextReviewAt)
    }

    @Test fun `senses remain independent and unsupported versions are not reinterpreted`() {
        val forward = state()
        val reverse = state("other")
        ReviewScheduler.next(forward, ReviewRating.REMEMBERED, now, zone)
        assertEquals(-1, reverse.stage)
        assertFalse(ReviewScheduler.supports(forward.copy(schedulerVersion = "future-v2")))
    }

    @Test fun `local dates exclude next midnight and account for daylight saving`() {
        val newYork = TimeZone.getTimeZone("America/New_York")
        val clock = Calendar.getInstance(newYork).apply {
            clear(); set(2026, Calendar.MARCH, 8, 0, 0, 0)
        }.timeInMillis
        val boundary = ReviewDay.at(clock, newYork)
        assertEquals(23 * 60 * 60 * 1000L, boundary.end - boundary.start)
        assertTrue(clock in boundary)
        assertFalse(boundary.end in boundary)
        assertEquals(boundary.end, ReviewScheduler.next(state(), ReviewRating.REMEMBERED, clock, newYork).nextReviewAt)
        assertNotEquals(boundary.start, ReviewDay.at(clock, zone).start)
    }

    @Test fun `queue prefers overdue then due then new and caps backlog`() {
        val cards = (1..60).map { card(state("due-$it", 0, now - day, now - it * day)) } + card(state("new"))
        val queue = ReviewQueueRules.select(cards, emptyList(), ReviewLimits(), now, zone)
        assertEquals(40, queue.cards.size)
        assertEquals("due-60", queue.cards.first().state.stableId)
        assertEquals(0, queue.newAvailable)
        val small = listOf(card(state("new")), card(state("today", 0, now - day)),
            card(state("old", 0, now - day, ReviewDay.at(now, zone).start - 1)))
        assertEquals(listOf("old", "today", "new"),
            ReviewQueueRules.select(small, emptyList(), ReviewLimits(), now, zone).cards.map { it.state.stableId })
    }

    @Test fun `new limit is fifteen and daily counts survive session restarts without counting retries`() {
        val cards = (1..100).map { card(state("new-$it")) }
        val regular = ReviewEvent("event", "new-1", now, ReviewRating.FORGOT, ReviewEventKind.SCHEDULED,
            ReviewPolicy.VERSION, 0, wasNew = true)
        val retry = regular.copy(stableId = "retry", kind = ReviewEventKind.RETRY, wasNew = false)
        assertEquals(15, ReviewQueueRules.select(cards, emptyList(), ReviewLimits(), now, zone).cards.size)
        val queue = ReviewQueueRules.select(cards, listOf(regular, retry), ReviewLimits(), now, zone)
        assertEquals(14, queue.cards.size)
        assertEquals(1, queue.completedToday)
        assertFalse(queue.cards.any { it.state.stableId == "new-1" })
    }

    @Test fun `daily total includes both modes and yesterday does not consume today quota`() {
        val cards = (1..60).map { card(state("due-$it", 0, now - day)) }
        val events = (1..39).map { ReviewEvent("event-$it", "past-$it", now,
            ReviewRating.REMEMBERED, ReviewEventKind.SCHEDULED, ReviewPolicy.VERSION, 0) }
        assertEquals(1, ReviewQueueRules.select(cards, events, ReviewLimits(), now, zone).cards.size)
        assertEquals(40, ReviewQueueRules.select(cards, events.map { it.copy(reviewedAt = now - day) }, ReviewLimits(), now, zone).cards.size)
    }

    @Test fun `disabled and future states are excluded and reenable retains progression`() {
        val original = state(stage = 3, last = now - day)
        val disabled = original.copy(enabled = false)
        assertTrue(ReviewQueueRules.select(listOf(card(disabled)), emptyList(), ReviewLimits(), now, zone).cards.isEmpty())
        assertEquals(original, disabled.copy(enabled = true))
        assertEquals(1, ReviewQueueRules.select(listOf(card(original)), emptyList(), ReviewLimits(), now, zone).cards.size)
    }

    @Test fun `same sense retry is separated when another sense is available`() {
        val cards = listOf(card(state("one")), card(state("two")))
        assertEquals(1, ReviewQueueRules.nextIndex(cards, "sense-one"))
    }

    @Test fun `only mechanical meaning edits skip confirmation`() {
        assertFalse(MeaningChangeRule.isSubstantive("  a  useful\nmeaning!", "a useful meaning."))
        assertTrue(MeaningChangeRule.isSubstantive("an old meaning", "a new meaning"))
    }

    @Test fun `reset starts a new generation without disabling the state`() {
        val next = ReviewScheduler.reset(state(stage = 4, last = now - day), now)
        assertEquals(-1, next.stage)
        assertNull(next.lastReviewedAt)
        assertEquals(1, next.generation)
        assertTrue(next.enabled)
    }

    @Test fun `limit validation requires a consistent range`() {
        listOf(-1 to 40, 101 to 200, 15 to 0, 15 to 501, 16 to 15).forEach { (fresh, total) ->
            assertThrows(IllegalArgumentException::class.java) { ReviewLimits(fresh, total) }
        }
        assertEquals(0, ReviewLimits(0, 1).newPerDay)
    }
}
