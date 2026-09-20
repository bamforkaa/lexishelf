package com.example.localvocabulary.backup.data

import com.example.localvocabulary.backup.domain.*
import com.example.localvocabulary.review.domain.*

/** Unknown scheduler versions are retained verbatim and excluded from scheduling until supported. */
internal fun VocabularyBackupV7.reviewValidationError(): String? {
    fun id(value: String) = value.matches(Regex("[A-Za-z0-9._:-]{1,128}"))
    val senses = entries.flatMap { it.senses }.mapTo(hashSetOf()) { it.stableId }
    if (reviewStates.map { it.stableId }.distinct().size != reviewStates.size ||
        reviewStates.map { it.senseStableId to it.mode }.distinct().size != reviewStates.size
    ) return "복습 상태 ID 또는 의미·방향이 중복됩니다."
    val states = reviewStates.associateBy { it.stableId }
    if (reviewStates.any {
        !id(it.stableId) || it.senseStableId !in senses || it.stage < -1 || it.generation < 0 ||
            it.nextReviewAt < 0 || (it.lastReviewedAt != null && it.lastReviewedAt < 0) ||
            !id(it.schedulerVersion) ||
            (it.schedulerVersion == ReviewPolicy.VERSION && !ReviewScheduler.supports(it.toDomain()))
    }) return "복습 상태의 참조, 시각 또는 scheduler 값이 잘못되었습니다."
    if (reviewEvents.map { it.stableId }.distinct().size != reviewEvents.size) return "복습 event ID가 중복됩니다."
    val events = reviewEvents.associateBy { it.stableId }
    val retryParents = hashSetOf<String>()
    for (event in reviewEvents) {
        if (event.reviewStateId !in states) return "복습 event의 상태 참조가 없습니다."
        if (!id(event.stableId) || event.reviewedAt < 0 || event.generation < 0 ||
            !id(event.schedulerVersion) || (event.sessionId != null && !id(event.sessionId))
        ) return "복습 event metadata가 잘못되었습니다."
        when (event.kind) {
            ReviewEventKind.SCHEDULED -> if (event.rating == null || event.sessionId == null || event.retryOfEventId != null) {
                return "정규 복습 event 형식이 잘못되었습니다."
            }
            ReviewEventKind.RESET -> if (event.rating != null || event.wasNew || event.retryOfEventId != null || event.sessionId != null) {
                return "초기화 event 형식이 잘못되었습니다."
            }
            ReviewEventKind.RETRY -> {
                val parent = events[event.retryOfEventId] ?: return "재시도의 원래 event가 없습니다."
                if (event.rating == null || event.wasNew || !retryParents.add(parent.stableId) ||
                    parent.kind != ReviewEventKind.SCHEDULED || parent.rating != ReviewRating.FORGOT ||
                    parent.reviewStateId != event.reviewStateId || parent.sessionId != event.sessionId ||
                    parent.generation != event.generation || parent.reviewedAt > event.reviewedAt ||
                    parent.schedulerVersion != event.schedulerVersion
                ) return "재시도 event 관계가 잘못되었습니다."
            }
        }
    }
    return null
}
