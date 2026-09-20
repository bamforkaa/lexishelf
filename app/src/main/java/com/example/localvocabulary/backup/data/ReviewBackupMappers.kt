package com.example.localvocabulary.backup.data

import com.example.localvocabulary.backup.domain.*
import com.example.localvocabulary.review.domain.*

internal fun VocabularyBackupV6.toCurrent() = VocabularyBackupV8(
    format, CURRENT_BACKUP_SCHEMA_VERSION, exportedAtEpochMillis, tags, entries, wordbooks,
)

internal fun VocabularyBackupV7.toCurrent(): VocabularyBackupV8 {
    val migrated = DirectionalReviewMigration.merge(
        reviewStates.map { LegacyDirectionalReviewState(it.toDomain(), it.mode) },
        reviewEvents.map { ReviewEvent(it.stableId, it.reviewStateId, it.reviewedAt, it.rating,
            it.kind, it.schedulerVersion, it.generation, it.wasNew, it.sessionId, it.retryOfEventId) },
    )
    return VocabularyBackupV8(format, CURRENT_BACKUP_SCHEMA_VERSION, exportedAtEpochMillis,
        tags, entries, wordbooks, migrated.states.map { it.toBackup() }, migrated.events.map { it.toBackup() })
}

internal fun BackupReviewStateV7.toDomain() = ReviewState(
    stableId, senseStableId, enabled, stage, lastReviewedAt, nextReviewAt, schedulerVersion, generation,
)

internal fun ReviewState.toBackup() = BackupReviewStateV8(
    stableId, senseStableId, enabled, stage, lastReviewedAt, nextReviewAt, schedulerVersion, generation,
)

internal fun BackupReviewStateV8.toDomain() = ReviewState(
    stableId, senseStableId, enabled, stage, lastReviewedAt, nextReviewAt, schedulerVersion, generation,
)

internal fun ReviewEvent.toBackup() = BackupReviewEventV8(
    stableId, reviewStateId, reviewedAt, rating, kind, schedulerVersion, generation, wasNew,
    sessionId, retryOfEventId, promptDirection, legacyReviewStateId,
)

internal fun BackupReviewEventV8.toDomain() = ReviewEvent(
    stableId, reviewStateId, reviewedAt, rating, kind, schedulerVersion, generation, wasNew,
    sessionId, retryOfEventId, promptDirection, legacyReviewStateId,
)

/** Unknown scheduler versions are retained verbatim and excluded from scheduling until supported. */
internal fun VocabularyBackupV8.reviewValidationError(): String? {
    fun id(value: String) = value.matches(Regex("[A-Za-z0-9._:-]{1,128}"))
    val senses = entries.flatMap { it.senses }.mapTo(hashSetOf()) { it.stableId }
    if (reviewStates.map { it.stableId }.distinct().size != reviewStates.size ||
        reviewStates.map { it.senseStableId }.distinct().size != reviewStates.size
    ) return "복습 상태 ID 또는 의미가 중복됩니다."
    val states = reviewStates.associateBy { it.stableId }
    if (reviewStates.any {
        !id(it.stableId) || it.senseStableId !in senses || it.stage < -1 || it.generation < 0 ||
            it.nextReviewAt < 0 || (it.lastReviewedAt != null && it.lastReviewedAt < 0) ||
            !id(it.schedulerVersion) ||
            (it.schedulerVersion == ReviewPolicy.VERSION && !ReviewScheduler.supports(it.toDomain()))
    }) return "복습 상태의 참조, 시각 또는 scheduler 값이 잘못되었습니다."
    if (reviewEvents.map { it.stableId }.distinct().size != reviewEvents.size) return "복습 event ID가 중복됩니다."
    val events = reviewEvents.associateBy { it.stableId }
    val legacyOwners = mutableMapOf<String, Pair<String, ReviewMode>>()
    val retryParents = hashSetOf<String>()
    for (event in reviewEvents) {
        if (event.reviewStateId !in states) return "복습 event의 상태 참조가 없습니다."
        if (!id(event.stableId) || event.reviewedAt < 0 || event.generation < 0 ||
            !id(event.schedulerVersion) || (event.sessionId != null && !id(event.sessionId)) ||
            (event.legacyReviewStateId != null && !id(event.legacyReviewStateId))
        ) return "복습 event metadata가 잘못되었습니다."
        if (event.legacyReviewStateId != null) {
            val direction = event.promptDirection ?: return "이전 복습의 방향이 없습니다."
            val owner = event.reviewStateId to direction
            val previous = legacyOwners.put(event.legacyReviewStateId, owner)
            if (previous != null && previous != owner) return "이전 복습 상태의 참조 또는 방향이 충돌합니다."
        }
        when (event.kind) {
            ReviewEventKind.SCHEDULED -> if (event.rating == null || event.sessionId == null || event.retryOfEventId != null || event.promptDirection == null) {
                return "정규 복습 event 형식이 잘못되었습니다."
            }
            ReviewEventKind.RESET -> if (event.rating != null || event.wasNew || event.retryOfEventId != null || event.sessionId != null ||
                (event.legacyReviewStateId == null && event.promptDirection != null)) {
                return "초기화 event 형식이 잘못되었습니다."
            }
            ReviewEventKind.RETRY -> {
                val parent = events[event.retryOfEventId] ?: return "재시도의 원래 event가 없습니다."
                if (event.rating == null || event.wasNew || !retryParents.add(parent.stableId) ||
                    parent.kind != ReviewEventKind.SCHEDULED || parent.rating != ReviewRating.FORGOT ||
                    parent.reviewStateId != event.reviewStateId || parent.sessionId != event.sessionId ||
                    parent.generation != event.generation || parent.reviewedAt > event.reviewedAt ||
                    parent.schedulerVersion != event.schedulerVersion || event.promptDirection == null ||
                    parent.promptDirection != event.promptDirection || parent.legacyReviewStateId != event.legacyReviewStateId
                ) return "재시도 event 관계가 잘못되었습니다."
            }
        }
    }
    return null
}
