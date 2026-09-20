package com.example.localvocabulary.review.data

import androidx.room.withTransaction
import com.example.localvocabulary.core.common.StableIdGenerator
import com.example.localvocabulary.core.common.TimeProvider
import com.example.localvocabulary.core.database.VocabularyDatabase
import com.example.localvocabulary.core.database.entity.ExampleEntity
import com.example.localvocabulary.review.domain.*
import com.example.localvocabulary.settings.SettingsRepository
import com.example.localvocabulary.vocabulary.data.toDomain
import com.example.localvocabulary.vocabulary.domain.ValidatedVocabularyDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RoomReviewRepository @Inject constructor(
    private val database: VocabularyDatabase,
    private val vocabularyRepository: VocabularyRepository,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val ids: StableIdGenerator,
) : ReviewRepository {
    private val dao get() = database.reviewDao()

    override fun observeStates() = dao.observeStates().map { rows -> rows.map { it.toDomain() } }
    override suspend fun events(stateId: String) = dao.eventsForState(stateId).map { it.toDomain() }
    override suspend fun event(eventId: String) = dao.event(eventId)?.toDomain()

    private suspend fun cards(): List<ReviewCard> {
        val entries = database.vocabularyDao().getAllEntries().map { it.toDomain() }
        val senses = entries.flatMap { entry -> entry.senses.map { it.stableId to (entry to it) } }.toMap()
        val history = dao.events().map { it.toDomain() }.groupBy { it.reviewStateId }
        return dao.states().mapNotNull { row ->
            val (entry, sense) = senses[row.senseStableId] ?: return@mapNotNull null
            ReviewCard(row.toDomain(), entry.id, entry.headword, sense.meaning, sense.examples, sense.provenance,
                ReviewDirectionPolicy.next(row.stableId, history[row.stableId].orEmpty()))
        }
    }

    override suspend fun card(stateId: String): ReviewCard? = database.withTransaction {
        cards().firstOrNull { it.state.stableId == stateId }
    }

    override suspend fun queue(): ReviewQueue {
        val limits = settingsRepository.settings.first().reviewLimits
        return database.withTransaction {
            val now = timeProvider.currentTimeMillis()
            val zone = TimeZone.getDefault()
            val day = ReviewDay.at(now, zone)
            ReviewQueueRules.select(cards(), dao.eventsBetween(day.start, day.end).map { it.toDomain() }, limits, now, zone)
        }
    }

    override suspend fun setEnabled(senseStableId: String, enabled: Boolean) {
        database.withTransaction {
            checkNotNull(database.vocabularyDao().findSense(senseStableId)) { "의미가 삭제되었습니다." }
            val existing = dao.stateForSense(senseStableId)
            if (existing != null) dao.updateState(existing.copy(enabled = enabled))
            else if (enabled) dao.insertState(ReviewState(
                stableId = ids.newId(), senseStableId = senseStableId,
                nextReviewAt = timeProvider.currentTimeMillis(),
            ).toEntity())
        }
    }

    override suspend fun record(attempt: ReviewAttempt, rating: ReviewRating): ReviewRecordResult {
        val limits = settingsRepository.settings.first().reviewLimits
        return database.withTransaction {
            val duplicate = dao.event(attempt.eventId)?.toDomain()
            if (duplicate != null) {
                check(duplicate.reviewStateId == attempt.stateId && duplicate.sessionId == attempt.sessionId &&
                    duplicate.rating == rating && duplicate.kind == attempt.kind &&
                    duplicate.retryOfEventId == attempt.retryOfEventId &&
                    duplicate.promptDirection == attempt.promptDirection && duplicate.generation == attempt.generation) { "Evaluation ID conflict" }
                return@withTransaction ReviewRecordResult.Recorded(duplicate)
            }
            val state = dao.state(attempt.stateId)?.toDomain()
            if (state == null || !state.enabled || !ReviewScheduler.supports(state) ||
                state.generation != attempt.generation || state.lastReviewedAt != attempt.expectedLastReviewedAt
            ) return@withTransaction ReviewRecordResult.Unavailable("학습 상태가 바뀌었습니다. 목록을 다시 불러오세요.")
            val now = timeProvider.currentTimeMillis()
            val zone = TimeZone.getDefault()
            val day = ReviewDay.at(now, zone)
            val events = dao.eventsBetween(day.start, day.end).map { it.toDomain() }
            when (attempt.kind) {
                ReviewEventKind.SCHEDULED -> {
                    val eligible = ReviewQueueRules.select(cards(), events, limits, now, zone)
                    if (eligible.cards.none { it.state.stableId == state.stableId && it.promptDirection == attempt.promptDirection }) {
                        return@withTransaction ReviewRecordResult.Unavailable("오늘의 한도 또는 복습 일정이 바뀌었습니다.")
                    }
                }
                ReviewEventKind.RETRY -> {
                    val parent = events.firstOrNull { it.stableId == attempt.retryOfEventId }
                    if (parent == null || parent.kind != ReviewEventKind.SCHEDULED ||
                        parent.rating != ReviewRating.FORGOT || parent.reviewStateId != state.stableId ||
                        parent.generation != state.generation || parent.sessionId != attempt.sessionId ||
                        parent.promptDirection != attempt.promptDirection ||
                        parent.reviewedAt !in ReviewDay.at(now, zone) ||
                        events.any { it.retryOfEventId == parent.stableId }
                    ) return@withTransaction ReviewRecordResult.Unavailable("이 재시도는 이미 끝났거나 만료되었습니다.")
                }
                ReviewEventKind.RESET -> error("Resets are vocabulary edit actions")
            }
            val event = ReviewEvent(
                stableId = attempt.eventId, reviewStateId = state.stableId, reviewedAt = now,
                rating = rating, kind = attempt.kind, schedulerVersion = state.schedulerVersion,
                generation = state.generation, wasNew = attempt.kind == ReviewEventKind.SCHEDULED && state.lastReviewedAt == null,
                sessionId = attempt.sessionId, retryOfEventId = attempt.retryOfEventId,
                promptDirection = attempt.promptDirection,
            )
            dao.insertEvent(event.toEntity())
            dao.updateState(ReviewScheduler.next(state, rating, now, zone, attempt.kind).toEntity())
            ReviewRecordResult.Recorded(event)
        }
    }

    override suspend fun addUserExample(senseStableId: String, exampleId: String, text: String) {
        require(text.isNotBlank())
        database.withTransaction {
            val vocabulary = database.vocabularyDao()
            val sense = checkNotNull(vocabulary.findSense(senseStableId)) { "의미가 삭제되었습니다." }
            val existing = vocabulary.findExample(exampleId)
            if (existing != null) {
                check(existing.senseId == sense.id && existing.text == text.trim() && existing.origin == "USER")
                return@withTransaction
            }
            val entry = checkNotNull(vocabulary.observeEntry(sense.entryId).first())
            val examples = entry.senses.first { it.sense.id == sense.id }.examples
            vocabulary.insertExamples(listOf(ExampleEntity(
                senseId = sense.id, stableId = exampleId, text = text.trim(), origin = "USER",
                sortOrder = (examples.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            )))
            // A new example is a vocabulary edit; evaluating a review never reaches this path.
            vocabulary.updateEntry(entry.entry.copy(modifiedAtEpochMillis = timeProvider.currentTimeMillis()))
        }
    }

    override suspend fun meaningChanges(draft: ValidatedVocabularyDraft): List<MeaningReviewChange> =
        database.withTransaction {
            val states = dao.states()
            draft.senses.flatMap { sense ->
                val id = sense.stableId ?: return@flatMap emptyList()
                val before = database.vocabularyDao().findSense(id) ?: return@flatMap emptyList()
                if (!MeaningChangeRule.isSubstantive(before.meaning, sense.meaning)) return@flatMap emptyList()
                states.filter { it.senseStableId == id && dao.eventsForState(it.stableId).isNotEmpty() }
                    .map { MeaningReviewChange(it.stableId, id, sense.meaning) }
            }
        }

    override suspend fun saveVocabulary(
        draft: ValidatedVocabularyDraft, resetStateIds: Set<String>, reviewEnabledBySenseIndex: Map<Int, Boolean>,
    ): Long =
        database.withTransaction {
            val changes = meaningChanges(draft)
            check(resetStateIds.all { id -> changes.any { it.stateId == id } })
            val entryId = vocabularyRepository.save(draft)
            resetStateIds.forEach { id ->
                val previous = checkNotNull(dao.state(id)).toDomain()
                val now = timeProvider.currentTimeMillis()
                val generation = maxOf(previous.generation, dao.eventsForState(id).maxOfOrNull { it.generation } ?: 0)
                val next = ReviewScheduler.reset(previous.copy(generation = generation), now)
                dao.insertEvent(ReviewEvent(
                    ids.newId(), id, now, null, ReviewEventKind.RESET,
                    next.schedulerVersion, next.generation,
                ).toEntity())
                dao.updateState(next.toEntity())
            }
            val savedSenses = checkNotNull(database.vocabularyDao().observeEntry(entryId).first())
                .senses.sortedBy { it.sense.sortOrder }.map { it.sense }
            if (draft.id == null && savedSenses.isNotEmpty()) {
                setEnabled(savedSenses.first().stableId, true)
            }
            reviewEnabledBySenseIndex.forEach { (index, enabled) ->
                require(index in savedSenses.indices)
                setEnabled(savedSenses[index].stableId, enabled)
            }
            entryId
        }
}
