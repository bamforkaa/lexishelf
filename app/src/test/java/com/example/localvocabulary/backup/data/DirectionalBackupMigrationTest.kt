package com.example.localvocabulary.backup.data

import com.example.localvocabulary.backup.domain.*
import com.example.localvocabulary.review.domain.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DirectionalBackupMigrationTest {
    private val serializer = KotlinxBackupSerializer()
    private val forward = BackupReviewStateV7("a", "sense", ReviewMode.MEANING_TO_EXPRESSION,
        false, 4, 20, 500, ReviewPolicy.VERSION, 2)
    private val reverse = forward.copy(stableId = "b", mode = ReviewMode.EXPRESSION_TO_MEANING,
        enabled = true, stage = 1, lastReviewedAt = 10, nextReviewAt = 100, generation = 4)
    private val event = BackupReviewEventV7("event", "b", 10, ReviewRating.FORGOT,
        ReviewEventKind.SCHEDULED, ReviewPolicy.VERSION, 3, false, "session", null)

    private fun legacy(states: List<BackupReviewStateV7> = listOf(forward, reverse),
        events: List<BackupReviewEventV7> = emptyList()) = VocabularyBackupV7(
        BACKUP_FORMAT_ID, 7, 30, emptyList(), listOf(BackupEntryV6("entry", "expression", "en",
            listOf(BackupSenseV6("sense", "meaning", "", emptyList())), "note", emptyList(), 0, 20)),
        reviewStates = states, reviewEvents = events,
    )
    private fun decode(document: VocabularyBackupV7) = serializer.decode(Json.encodeToString(document))

    @Test fun `each single direction imports without enrolling other senses`() {
        listOf(forward, reverse).forEach { state ->
            val old = legacy(listOf(state), listOf(event.copy(reviewStateId = state.stableId)))
            val result = (decode(old) as BackupDecodeResult.Success).backup
            assertEquals(7, result.sourceSchemaVersion)
            assertEquals(8, result.document.schemaVersion)
            assertEquals(state.stableId, result.document.reviewStates.single().stableId)
            assertEquals(state.enabled, result.document.reviewStates.single().enabled)
            assertEquals(state.mode, result.document.reviewEvents.single().promptDirection)
        }
    }

    @Test fun `both states merge conservatively and every legacy event survives round trip`() {
        val reset = event.copy(stableId = "reset", reviewedAt = 12, rating = null,
            kind = ReviewEventKind.RESET, generation = 4, sessionId = null)
        val retry = event.copy(stableId = "retry", reviewedAt = 11, rating = ReviewRating.REMEMBERED,
            kind = ReviewEventKind.RETRY, retryOfEventId = "event")
        val other = event.copy(stableId = "other", reviewStateId = "a", generation = 2)
        val old = legacy(events = listOf(event, retry, reset, other))
        val current = (decode(old) as BackupDecodeResult.Success).backup.document
        val state = current.reviewStates.single()
        assertEquals("a", state.stableId)
        assertEquals(1, state.stage)
        assertEquals(100L, state.nextReviewAt)
        assertEquals(10L, state.lastReviewedAt)
        assertEquals(5, state.generation)
        assertTrue(state.enabled)
        old.reviewEvents.zip(current.reviewEvents).forEach { (before, after) ->
            assertEquals(before.stableId, after.stableId)
            assertEquals(before.reviewStateId, after.legacyReviewStateId)
            assertEquals(before.generation, after.generation)
            assertEquals(before.retryOfEventId, after.retryOfEventId)
            assertEquals(before.kind, after.kind)
            assertEquals(if (before.reviewStateId == "a") forward.mode else reverse.mode, after.promptDirection)
        }
        assertEquals(current, (serializer.decode(serializer.encode(current)) as BackupDecodeResult.Success).backup.document)
        assertEquals(current.reviewStates, (decode(old.copy(reviewStates = old.reviewStates.reversed())) as BackupDecodeResult.Success).backup.document.reviewStates)
    }

    @Test fun `legacy duplicate direction and cross direction retry fail before merge can hide them`() {
        val badRetry = event.copy(stableId = "retry", reviewStateId = "a", kind = ReviewEventKind.RETRY,
            wasNew = false, retryOfEventId = event.stableId)
        listOf(legacy(listOf(forward, forward.copy(stableId = "c"))),
            legacy(events = listOf(event, badRetry)),
            legacy(events = listOf(event.copy(reviewStateId = "missing"))),
        ).forEach { assertTrue(decode(it) is BackupDecodeResult.Failure) }
    }

    @Test fun `current events require direction and retry must retain parent direction`() {
        val current = (decode(legacy(events = listOf(event))) as BackupDecodeResult.Success).backup.document
        val scheduled = current.reviewEvents.single()
        val retry = scheduled.copy(stableId = "retry", kind = ReviewEventKind.RETRY,
            retryOfEventId = scheduled.stableId, promptDirection = forward.mode)
        listOf(current.copy(reviewEvents = listOf(scheduled.copy(promptDirection = null))),
            current.copy(reviewEvents = listOf(scheduled, retry)),
        ).forEach { assertTrue(serializer.decode(serializer.encode(it)) is BackupDecodeResult.Failure) }
    }
}
