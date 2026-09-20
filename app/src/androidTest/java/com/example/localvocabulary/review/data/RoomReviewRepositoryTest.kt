package com.example.localvocabulary.review.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localvocabulary.backup.data.KotlinxBackupSerializer
import com.example.localvocabulary.backup.data.RoomVocabularyBackupRepository
import com.example.localvocabulary.backup.domain.*
import com.example.localvocabulary.core.common.StableIdGenerator
import com.example.localvocabulary.core.common.TimeProvider
import com.example.localvocabulary.core.database.VocabularyDatabase
import com.example.localvocabulary.review.domain.*
import com.example.localvocabulary.settings.AppSettings
import com.example.localvocabulary.settings.SettingsRepository
import com.example.localvocabulary.vocabulary.data.RoomVocabularyRepository
import com.example.localvocabulary.vocabulary.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomReviewRepositoryTest {
    private lateinit var db: VocabularyDatabase
    private lateinit var vocabulary: RoomVocabularyRepository
    private lateinit var review: RoomReviewRepository
    private lateinit var backup: RoomVocabularyBackupRepository
    private val serializer = KotlinxBackupSerializer()
    private var sequence = 0
    private var now = 1_800_000_000_000L
    private val clock = TimeProvider { now }
    private val ids = StableIdGenerator { "test-id-${++sequence}" }
    private val settings = object : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun setDefaultLanguageTag(languageTag: String) = Unit
        override suspend fun addUserLanguageTag(languageTag: String) = Unit
        override suspend fun setReviewLimits(limits: ReviewLimits) { settings.value = settings.value.copy(reviewLimits = limits) }
    }

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VocabularyDatabase::class.java)
            .allowMainThreadQueries().build()
        vocabulary = RoomVocabularyRepository(db.vocabularyDao(), clock, ids)
        review = RoomReviewRepository(db, vocabulary, settings, clock, ids)
        backup = RoomVocabularyBackupRepository(db, clock)
    }
    @After fun close() { db.close() }

    private fun draft(id: Long? = null, meaning: String = "기대하다") = ValidatedVocabularyDraft(
        id, "look forward to", "en", listOf(VocabularySenseDraft(meaning, "", listOf(
            VocabularyExampleDraft("I look forward to the meeting.", "example", "회의가 기대된다.", ExampleOrigin.CAPTURED,
                "Sample article", "https://example.com/article", "p. 1", 100),
        ), stableId = "sense")), "note", emptySet(),
    )
    private suspend fun enroll(): ReviewState {
        vocabulary.save(draft())
        review.setEnabled("sense", true)
        return review.observeStates().first().single()
    }
    private suspend fun attempt(state: ReviewState, eventId: String = ids.newId(), kind: ReviewEventKind = ReviewEventKind.SCHEDULED,
        retryOf: String? = null) = ReviewAttempt(eventId, state.stableId, "session", kind,
        state.generation, state.lastReviewedAt,
        if (retryOf == null) review.card(state.stableId)!!.promptDirection else review.event(retryOf)!!.promptDirection!!, retryOf)
    private fun validated(document: VocabularyBackupV8): ValidatedBackup =
        (serializer.decode(serializer.encode(document)) as BackupDecodeResult.Success).backup

    @Test fun repeatedEnrollmentKeepsOneSenseIdentity() = runTest {
        vocabulary.save(draft())
        assertTrue(review.observeStates().first().isEmpty())
        repeat(3) { review.setEnabled("sense", true) }
        val state = review.observeStates().first().single()
        review.record(attempt(state), ReviewRating.REMEMBERED)
        review.setEnabled("sense", true)
        assertEquals(state.stableId, review.observeStates().first().single().stableId)
        assertEquals(0, review.card(state.stableId)!!.state.stage)
    }

    @Test fun evaluationPersistsStateAndHistoryWithoutTouchingVocabularyTime() = runTest {
        val state = enroll()
        val entry = vocabulary.observeEntries().first().single()
        now += 1_000
        review.record(attempt(state), ReviewRating.REMEMBERED)
        val stored = review.card(state.stableId)!!.state
        assertEquals(0, stored.stage)
        assertEquals(now, stored.lastReviewedAt)
        assertEquals(1, review.events(state.stableId).size)
        assertEquals(entry.modifiedAtEpochMillis, vocabulary.observeEntry(entry.id).first()!!.modifiedAtEpochMillis)
    }

    @Test fun failedStateWriteRollsBackInsertedEvent() = runTest {
        val state = enroll()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_review_update BEFORE UPDATE ON review_states BEGIN SELECT RAISE(ABORT, 'test rollback'); END")
        assertTrue(runCatching { review.record(attempt(state), ReviewRating.REMEMBERED) }.isFailure)
        assertTrue(review.events(state.stableId).isEmpty())
        assertEquals(state, review.card(state.stableId)!!.state)
    }

    @Test fun duplicateTokenAndDifferentTokenForSameScheduledStateCannotDoubleEvaluate() = runTest {
        val state = enroll()
        val request = attempt(state)
        assertTrue(review.record(request, ReviewRating.REMEMBERED) is ReviewRecordResult.Recorded)
        assertTrue(review.record(request, ReviewRating.REMEMBERED) is ReviewRecordResult.Recorded)
        assertTrue(review.record(attempt(state), ReviewRating.REMEMBERED) is ReviewRecordResult.Unavailable)
        assertEquals(1, review.events(state.stableId).size)
    }

    @Test fun retryIsRecordedOnceWithoutChangingProgressOrDailyCount() = runTest {
        val state = enroll()
        val request = attempt(state)
        review.record(request, ReviewRating.FORGOT)
        val beforeRetry = review.card(state.stableId)!!.state
        val retry = attempt(beforeRetry, kind = ReviewEventKind.RETRY, retryOf = request.eventId)
        review.record(retry, ReviewRating.REMEMBERED)
        assertEquals(beforeRetry, review.card(state.stableId)!!.state)
        assertEquals(1, review.queue().completedToday)
        assertEquals(ReviewEventKind.RETRY, review.events(state.stableId).last().kind)
        assertTrue(review.record(retry.copy(eventId = ids.newId()), ReviewRating.FORGOT) is ReviewRecordResult.Unavailable)
    }

    @Test fun suspensionAndReactivationPreserveProgressAndHistory() = runTest {
        val state = enroll()
        review.record(attempt(state), ReviewRating.REMEMBERED)
        val before = review.card(state.stableId)!!.state
        review.setEnabled("sense", false)
        now += 2 * 86_400_000L
        assertTrue(review.queue().cards.isEmpty())
        review.setEnabled("sense", true)
        assertEquals(before, review.card(state.stableId)!!.state)
        assertEquals(1, review.queue().cards.size)
        assertEquals(1, review.events(state.stableId).size)
    }

    @Test fun deletingSenseCascadesToStateAndEvents() = runTest {
        val state = enroll()
        review.record(attempt(state), ReviewRating.REMEMBERED)
        val entry = vocabulary.observeEntries().first().single()
        vocabulary.save(draft(entry.id).copy(senses = listOf(VocabularySenseDraft(
            "remaining meaning", "", emptyList(), stableId = "remaining-sense",
        ))))
        assertNotNull(vocabulary.observeEntry(entry.id).first())
        assertTrue(db.reviewDao().states().isEmpty())
        assertTrue(db.reviewDao().events().isEmpty())
    }

    @Test fun meaningEditsPreserveHistoryUnlessTheSenseIsReset() = runTest {
        val first = enroll()
        review.setEnabled("sense", true)
        val states = review.observeStates().first()
        states.forEach { review.record(attempt(it), ReviewRating.REMEMBERED) }
        val entry = vocabulary.observeEntries().first().single()
        assertTrue(review.meaningChanges(draft(entry.id, "  기대하다!\n ")).isEmpty())
        val edited = draft(entry.id, "다른 의미")
        assertEquals(1, review.meaningChanges(edited).size)
        val before = review.card(first.stableId)!!.state
        review.saveVocabulary(edited, emptySet())
        assertEquals(before, review.card(first.stableId)!!.state)
        review.saveVocabulary(draft(entry.id, "새로운 의미"), setOf(first.stableId))
        val reset = review.card(first.stableId)!!.state
        assertEquals(-1, reset.stage)
        assertEquals(1, reset.generation)
        assertNull(reset.lastReviewedAt)
        assertEquals(2, review.events(first.stableId).size)
        assertEquals(ReviewEventKind.RESET, review.events(first.stableId).last().kind)
        assertEquals("example", vocabulary.observeEntry(entry.id).first()!!.senses.single().examples.single().stableId)
    }

    @Test fun resetFailureRollsBackVocabularyEditAsWell() = runTest {
        val state = enroll()
        review.record(attempt(state), ReviewRating.REMEMBERED)
        val entry = vocabulary.observeEntries().first().single()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_reset BEFORE INSERT ON review_events WHEN NEW.kind = 'RESET' BEGIN SELECT RAISE(ABORT, 'test rollback'); END")
        assertTrue(runCatching { review.saveVocabulary(draft(entry.id, "changed"), setOf(state.stableId)) }.isFailure)
        assertEquals(entry, vocabulary.observeEntry(entry.id).first())
        assertEquals(1, review.events(state.stableId).size)
    }

    @Test fun ownSentenceHasUserOriginAndStableIdWithoutRating() = runTest {
        val state = enroll()
        review.addUserExample("sense", "my-example", "  My own sentence.  ")
        review.addUserExample("sense", "my-example", "My own sentence.")
        val entry = vocabulary.observeEntries().first().single()
        val example = entry.senses.single().examples.last()
        assertEquals(ExampleOrigin.USER, example.origin)
        assertEquals("my-example", example.stableId)
        assertEquals(2, entry.senses.single().examples.size)
        assertTrue(review.events(state.stableId).isEmpty())
    }

    @Test fun v8ReplaceRoundTripAndRepeatedMergePreserveStateAndEventsVerbatim() = runTest {
        val state = enroll()
        review.record(attempt(state), ReviewRating.HARD)
        val document = backup.createBackup()
        now += 10 * 86_400_000L
        val validated = validated(document)
        backup.importBackup(validated, BackupConflictPolicy.REPLACE_ALL)
        repeat(2) { backup.importBackup(validated, BackupConflictPolicy.MERGE_BY_STABLE_ID) }
        val restored = backup.createBackup()
        assertEquals(document.reviewStates, restored.reviewStates)
        assertEquals(document.reviewEvents, restored.reviewEvents)
        assertEquals(document.entries, restored.entries)
    }

    @Test fun mergeUnionsImmutableEventsButRestoresSnapshotScheduleWithoutRecalculation() = runTest {
        val state = enroll()
        review.record(attempt(state), ReviewRating.REMEMBERED)
        val snapshot = backup.createBackup()
        now += 2 * 86_400_000L
        review.record(attempt(review.card(state.stableId)!!.state), ReviewRating.HARD)
        backup.importBackup(validated(snapshot), BackupConflictPolicy.MERGE_BY_STABLE_ID)
        assertEquals(snapshot.reviewStates, backup.createBackup().reviewStates)
        assertEquals(2, review.events(state.stableId).size)
        assertTrue(serializer.decode(serializer.encode(backup.createBackup())) is BackupDecodeResult.Success)
    }

    @Test fun immutableEventConflictAbortsMergeWithoutOverwritingEntry() = runTest {
        val state = enroll()
        review.record(attempt(state), ReviewRating.REMEMBERED)
        val original = backup.createBackup()
        val conflict = original.copy(
            entries = original.entries.map { it.copy(notes = "must roll back") },
            reviewEvents = original.reviewEvents.map { it.copy(rating = ReviewRating.HARD) },
        )
        assertTrue(runCatching { backup.importBackup(validated(conflict), BackupConflictPolicy.MERGE_BY_STABLE_ID) }.isFailure)
        assertEquals(original, backup.createBackup())
    }

    @Test fun replaceFailureRollsBackDeletedVocabularyAndReviewHistory() = runTest {
        val state = enroll()
        review.record(attempt(state), ReviewRating.REMEMBERED)
        val original = backup.createBackup()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_event_restore BEFORE INSERT ON review_events BEGIN SELECT RAISE(ABORT, 'test rollback'); END")
        assertTrue(runCatching { backup.importBackup(validated(original), BackupConflictPolicy.REPLACE_ALL) }.isFailure)
        assertEquals(original, backup.createBackup())
    }

    @Test fun legacyV6MergeRetainsReviewForSurvivingSenseButReplaceWarnsAndRemovesIt() = runTest {
        val state = enroll()
        review.record(attempt(state), ReviewRating.REMEMBERED)
        val current = backup.createBackup()
        val legacy = VocabularyBackupV6(current.format, 6, current.exportedAtEpochMillis, current.tags, current.entries, current.wordbooks)
        val validated = (serializer.decode(Json.encodeToString(legacy)) as BackupDecodeResult.Success).backup
        assertEquals(0, backup.previewImport(validated, BackupConflictPolicy.MERGE_BY_STABLE_ID).reviewStateRemovalCount)
        backup.importBackup(validated, BackupConflictPolicy.MERGE_BY_STABLE_ID)
        assertEquals(1, review.events(state.stableId).size)
        val preview = backup.previewImport(validated, BackupConflictPolicy.REPLACE_ALL)
        assertEquals(1, preview.reviewStateRemovalCount)
        assertEquals(1, preview.reviewEventRemovalCount)
        backup.importBackup(validated, BackupConflictPolicy.REPLACE_ALL)
        assertTrue(db.reviewDao().states().isEmpty())
    }

    @Test fun legacyV5MergeWarnsBeforeReplacingChildrenAndTheirReviews() = runTest {
        val state = enroll()
        review.record(attempt(state), ReviewRating.REMEMBERED)
        val current = backup.createBackup()
        val entry = current.entries.single()
        val old = VocabularyBackupV2(BACKUP_FORMAT_ID, 5, now, emptyList(), listOf(
            BackupEntryV2(entry.stableId, entry.headword, entry.languageTag,
                entry.senses.map { BackupSenseV2(it.meaning, it.partOfSpeech, it.examples.map { example -> example.text }) },
                entry.notes, emptyList(), entry.createdAtEpochMillis, entry.modifiedAtEpochMillis),
        ))
        val validated = (serializer.decode(Json.encodeToString(old)) as BackupDecodeResult.Success).backup
        val preview = backup.previewImport(validated, BackupConflictPolicy.MERGE_BY_STABLE_ID)
        assertEquals(1, preview.legacyChildReplacementCount)
        assertEquals(1, preview.reviewStateRemovalCount)
        assertEquals(1, preview.reviewEventRemovalCount)
        assertEquals(1, db.reviewDao().states().size)
        backup.importBackup(validated, BackupConflictPolicy.MERGE_BY_STABLE_ID)
        assertTrue(db.reviewDao().states().isEmpty())
        assertEquals(entry.headword, vocabulary.observeEntries().first().single().headword)
    }

    @Test fun unknownSchedulerVersionIsPreservedButNotScheduled() = runTest {
        enroll()
        val document = backup.createBackup()
        val future = document.copy(reviewStates = document.reviewStates.map { it.copy(schedulerVersion = "future-v2", stage = 20) })
        backup.importBackup(validated(future), BackupConflictPolicy.REPLACE_ALL)
        assertEquals(future.reviewStates, backup.createBackup().reviewStates)
        assertTrue(review.queue().cards.isEmpty())
        assertEquals(1, review.queue().unsupportedCount)
    }

    @Test fun repositoryRechecksDailyLimitsInsideEvaluationTransaction() = runTest {
        val first = enroll()
        vocabulary.save(draft().copy(senses = listOf(VocabularySenseDraft("other meaning", "", emptyList(), stableId = "other-sense"))))
        review.setEnabled("other-sense", true)
        settings.setReviewLimits(ReviewLimits(1, 1))
        assertEquals(1, review.queue().cards.size)
        review.record(attempt(first), ReviewRating.REMEMBERED)
        val other = review.observeStates().first().first { it.stableId != first.stableId }
        assertTrue(review.record(attempt(other), ReviewRating.REMEMBERED) is ReviewRecordResult.Unavailable)
        assertEquals(1, db.reviewDao().events().size)
    }
    @Test fun newEntryEnrollsOnlyItsFirstSenseAndEditingOldEntriesDoesNotEnroll() = runTest {
        val senses = (1..4).map { VocabularySenseDraft("meaning $it", "", emptyList()) }
        val id = review.saveVocabulary(draft().copy(senses = senses), emptySet())
        val entry = vocabulary.observeEntry(id).first()!!
        assertEquals(entry.senses.first().stableId, review.observeStates().first().single().senseStableId)
        assertEquals(1, review.queue().cards.size)
        val oldId = vocabulary.save(draft())
        review.saveVocabulary(draft(oldId, "edited"), emptySet())
        assertEquals(1, review.observeStates().first().size)
    }

    @Test fun editorOverridesCanOptOutAndEnrollAnotherSenseWithoutLosingProgress() = runTest {
        val senses = (1..3).map { VocabularySenseDraft("meaning $it", "", emptyList()) }
        val id = review.saveVocabulary(draft().copy(senses = senses), emptySet(), mapOf(0 to false, 2 to true))
        val entry = vocabulary.observeEntry(id).first()!!
        val states = review.observeStates().first()
        assertFalse(states.first { it.senseStableId == entry.senses[0].stableId }.enabled)
        assertEquals(entry.senses[2].stableId, review.queue().cards.single().state.senseStableId)
        val state = states.first { it.enabled }
        review.record(attempt(state), ReviewRating.REMEMBERED)
        review.setEnabled(state.senseStableId, false)
        review.setEnabled(state.senseStableId, true)
        assertEquals(0, review.card(state.stableId)!!.state.stage)
        assertEquals(1, review.events(state.stableId).size)
    }

    @Test fun autoEnrollmentFailureRollsBackTheNewVocabularyToo() = runTest {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_enrollment BEFORE INSERT ON review_states BEGIN SELECT RAISE(ABORT, 'test rollback'); END")
        assertTrue(runCatching { review.saveVocabulary(draft(), emptySet()) }.isFailure)
        assertTrue(vocabulary.observeEntries().first().isEmpty())
        assertTrue(review.observeStates().first().isEmpty())
    }

    @Test fun bothDirectionsAdvanceOneProgressionAndRetryRejectsDifferentDirection() = runTest {
        val initial = enroll()
        val request = attempt(initial)
        review.record(request, ReviewRating.FORGOT)
        val retry = attempt(review.card(initial.stableId)!!.state, kind = ReviewEventKind.RETRY, retryOf = request.eventId)
        assertEquals(request.promptDirection, retry.promptDirection)
        val opposite = ReviewMode.entries.first { it != retry.promptDirection }
        assertTrue(review.record(retry.copy(promptDirection = opposite), ReviewRating.REMEMBERED) is ReviewRecordResult.Unavailable)
        assertTrue(review.record(retry, ReviewRating.REMEMBERED) is ReviewRecordResult.Recorded)
        val directions = mutableListOf<ReviewMode>()
        repeat(6) { index ->
            now = review.card(initial.stableId)!!.state.nextReviewAt + 1
            val card = review.queue().cards.single()
            directions += card.promptDirection
            assertTrue(review.record(attempt(card.state), ReviewRating.REMEMBERED) is ReviewRecordResult.Recorded)
            assertEquals(index, review.card(initial.stableId)!!.state.stage)
        }
        assertEquals(2, directions.distinct().size)
        assertEquals(3, directions.count { it == ReviewMode.MEANING_TO_EXPRESSION })
        assertEquals(1, review.observeStates().first().size)
    }

    @Test fun partialDirectionalV7MergeReusesMigratedIdentityAndKeepsResetNamespace() = runTest {
        val entryId = vocabulary.save(draft())
        val current = backup.createBackup()
        val forward = BackupReviewStateV7("a", "sense", ReviewMode.MEANING_TO_EXPRESSION,
            true, 3, 10, 100, ReviewPolicy.VERSION, 2)
        val reverse = forward.copy(stableId = "b", mode = ReviewMode.EXPRESSION_TO_MEANING, stage = 1, generation = 4)
        val event = BackupReviewEventV7("old", "b", 10, ReviewRating.REMEMBERED, ReviewEventKind.SCHEDULED,
            ReviewPolicy.VERSION, 4, false, "old-session", null)
        val old = VocabularyBackupV7(current.format, 7, now, current.tags, current.entries, current.wordbooks,
            listOf(forward, reverse), listOf(event))
        fun validatedOld(document: VocabularyBackupV7) = (serializer.decode(Json.encodeToString(document)) as BackupDecodeResult.Success).backup
        backup.importBackup(validatedOld(old), BackupConflictPolicy.MERGE_BY_STABLE_ID)
        review.saveVocabulary(draft(entryId, "changed meaning"), setOf("a"))
        val resetGeneration = review.card("a")!!.state.generation
        val history = review.events("a")
        val partial = validatedOld(old.copy(reviewStates = listOf(reverse)))
        assertEquals(1, backup.previewImport(partial, BackupConflictPolicy.MERGE_BY_STABLE_ID).reviewStateOverwriteCount)
        repeat(2) { backup.importBackup(partial, BackupConflictPolicy.MERGE_BY_STABLE_ID) }
        assertEquals("a", review.observeStates().first().single().stableId)
        assertEquals(resetGeneration + 1, review.card("a")!!.state.generation)
        assertEquals(history, review.events("a"))
        assertEquals("b", review.events("a").first().legacyReviewStateId)
        val restored = backup.createBackup()
        backup.importBackup(validated(restored), BackupConflictPolicy.REPLACE_ALL)
        assertEquals(restored.reviewEvents, backup.createBackup().reviewEvents)
        val reusedLineage = restored.copy(reviewEvents = listOf(restored.reviewEvents.first { it.legacyReviewStateId == "b" }
            .copy(stableId = "collision", promptDirection = ReviewMode.MEANING_TO_EXPRESSION)))
        assertTrue(runCatching { backup.importBackup(validated(reusedLineage), BackupConflictPolicy.MERGE_BY_STABLE_ID) }.isFailure)
        assertEquals(restored, backup.createBackup())
    }

    @Test fun currentVersionCannotChangeStateIdentityForTheSameSense() = runTest {
        enroll()
        val original = backup.createBackup()
        val changed = original.copy(reviewStates = original.reviewStates.map { it.copy(stableId = "other") })
        assertTrue(runCatching { backup.importBackup(validated(changed), BackupConflictPolicy.MERGE_BY_STABLE_ID) }.isFailure)
        assertEquals(original, backup.createBackup())
    }

}
