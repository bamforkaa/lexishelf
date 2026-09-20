package com.example.localvocabulary.backup.data

import com.example.localvocabulary.backup.domain.*
import com.example.localvocabulary.review.domain.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ReviewBackupTest {
    private val serializer = KotlinxBackupSerializer()
    private fun document(): VocabularyBackupV8 {
        val state = BackupReviewStateV8("review", "sense",
            true, -1, 10, 100, ReviewPolicy.VERSION, 0)
        val event = BackupReviewEventV8("event", "review", 10, ReviewRating.FORGOT,
            ReviewEventKind.SCHEDULED, ReviewPolicy.VERSION, 0, true, "session", null, ReviewMode.MEANING_TO_EXPRESSION)
        return VocabularyBackupV8(BACKUP_FORMAT_ID, CURRENT_BACKUP_SCHEMA_VERSION, 20, emptyList(), listOf(
            BackupEntryV6("entry", "expression", "en", listOf(BackupSenseV6("sense", "meaning", "", emptyList())),
                "", emptyList(), 0, 0),
        ), reviewStates = listOf(state), reviewEvents = listOf(event))
    }
    private fun decode(document: VocabularyBackupV8) = serializer.decode(serializer.encode(document))

    @Test fun `v8 round trip preserves both directions events retries reset and unknown algorithm`() {
        val base = document()
        val retry = base.reviewEvents.single().copy(stableId = "retry", kind = ReviewEventKind.RETRY,
            rating = ReviewRating.REMEMBERED, wasNew = false, retryOfEventId = "event", reviewedAt = 11)
        val reset = retry.copy(stableId = "reset", kind = ReviewEventKind.RESET, rating = null,
            retryOfEventId = null, sessionId = null, promptDirection = null, generation = 1, reviewedAt = 12)
        val reverse = base.reviewEvents.single().copy(stableId = "reverse", reviewedAt = 15,
            wasNew = false, generation = 1, promptDirection = ReviewMode.EXPRESSION_TO_MEANING)
        val current = base.copy(reviewStates = listOf(
            base.reviewStates.single().copy(generation = 1, schedulerVersion = "future-v1", stage = 20),
        ), reviewEvents = base.reviewEvents + retry + reset + reverse)
        assertEquals(current, (decode(current) as BackupDecodeResult.Success).backup.document)
    }

    @Test fun `v6 without review fields imports context and does not enroll`() {
        val current = document()
        val legacy = VocabularyBackupV6(current.format, 6, 20, current.tags, current.entries)
        val result = serializer.decode(Json.encodeToString(legacy)) as BackupDecodeResult.Success
        assertEquals(6, result.backup.sourceSchemaVersion)
        assertEquals(current.entries, result.backup.document.entries)
        assertTrue(result.backup.document.reviewStates.isEmpty())
        assertTrue(result.backup.document.reviewEvents.isEmpty())
    }

    @Test fun `missing sense state or retry references fail validation`() {
        val base = document()
        val bad = listOf(
            base.copy(reviewStates = base.reviewStates.map { it.copy(senseStableId = "missing") }),
            base.copy(reviewEvents = base.reviewEvents.map { it.copy(reviewStateId = "missing") }),
            base.copy(reviewEvents = base.reviewEvents.map { it.copy(kind = ReviewEventKind.RETRY, wasNew = false, retryOfEventId = "missing") }),
        )
        bad.forEach { assertTrue(decode(it) is BackupDecodeResult.Failure) }
    }

    @Test fun `duplicate identities and same sense are rejected`() {
        val base = document()
        listOf(
            base.copy(reviewStates = base.reviewStates + base.reviewStates),
            base.copy(reviewStates = base.reviewStates + base.reviewStates.single().copy(stableId = "other")),
            base.copy(reviewEvents = base.reviewEvents + base.reviewEvents),
        ).forEach { assertTrue(decode(it) is BackupDecodeResult.Failure) }
    }

    @Test fun `known algorithm stages timestamps and event kind rules are validated`() {
        val base = document()
        listOf(
            base.copy(reviewStates = base.reviewStates.map { it.copy(stage = 9) }),
            base.copy(reviewStates = base.reviewStates.map { it.copy(nextReviewAt = -1) }),
            base.copy(reviewEvents = base.reviewEvents.map { it.copy(rating = null) }),
            base.copy(reviewEvents = base.reviewEvents.map { it.copy(reviewedAt = -1) }),
            base.copy(reviewEvents = base.reviewEvents.map { it.copy(kind = ReviewEventKind.RESET) }),
        ).forEach { assertTrue(decode(it) is BackupDecodeResult.Failure) }
    }

    @Test fun `multiple retries of the same scheduled event are rejected`() {
        val base = document()
        val retry = base.reviewEvents.single().copy(stableId = "retry", kind = ReviewEventKind.RETRY,
            wasNew = false, retryOfEventId = "event")
        assertTrue(decode(base.copy(reviewEvents = base.reviewEvents + retry + retry.copy(stableId = "retry-2"))) is BackupDecodeResult.Failure)
    }

    @Test fun `unknown root version field and enum cannot be silently discarded`() {
        val json = serializer.encode(document())
        listOf(json.replace("\"schemaVersion\": 8", "\"schemaVersion\": 99"),
            json.replace("\"FORGOT\"", "\"UNKNOWN_RATING\""),
            json.replace("\"wasNew\": true", "\"wasNew\": true, \"secretField\": 1"),
        ).forEach { assertTrue(serializer.decode(it) is BackupDecodeResult.Failure) }
    }
}
