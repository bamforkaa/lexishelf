package com.example.localvocabulary.backup.domain

import com.example.localvocabulary.review.domain.ReviewEventKind
import com.example.localvocabulary.review.domain.ReviewMode
import com.example.localvocabulary.review.domain.ReviewRating
import kotlinx.serialization.Serializable

@Serializable
data class VocabularyBackupV7(
    val format: String,
    val schemaVersion: Int,
    val exportedAtEpochMillis: Long,
    val tags: List<BackupTagV1>,
    val entries: List<BackupEntryV6>,
    val wordbooks: List<BackupWordbookV4> = emptyList(),
    val reviewStates: List<BackupReviewStateV7> = emptyList(),
    val reviewEvents: List<BackupReviewEventV7> = emptyList(),
)

@Serializable
data class BackupReviewStateV7(
    val stableId: String,
    val senseStableId: String,
    val mode: ReviewMode,
    val enabled: Boolean,
    val stage: Int,
    val lastReviewedAt: Long?,
    val nextReviewAt: Long,
    val schedulerVersion: String,
    val generation: Int,
)

@Serializable
data class BackupReviewEventV7(
    val stableId: String,
    val reviewStateId: String,
    val reviewedAt: Long,
    val rating: ReviewRating?,
    val kind: ReviewEventKind,
    val schedulerVersion: String,
    val generation: Int,
    val wasNew: Boolean,
    val sessionId: String?,
    val retryOfEventId: String?,
)

@Serializable
data class VocabularyBackupV8(
    val format: String,
    val schemaVersion: Int,
    val exportedAtEpochMillis: Long,
    val tags: List<BackupTagV1>,
    val entries: List<BackupEntryV6>,
    val wordbooks: List<BackupWordbookV4> = emptyList(),
    val reviewStates: List<BackupReviewStateV8> = emptyList(),
    val reviewEvents: List<BackupReviewEventV8> = emptyList(),
)

@Serializable
data class BackupReviewStateV8(
    val stableId: String,
    val senseStableId: String,
    val enabled: Boolean,
    val stage: Int,
    val lastReviewedAt: Long?,
    val nextReviewAt: Long,
    val schedulerVersion: String,
    val generation: Int,
)

@Serializable
data class BackupReviewEventV8(
    val stableId: String,
    val reviewStateId: String,
    val reviewedAt: Long,
    val rating: ReviewRating?,
    val kind: ReviewEventKind,
    val schedulerVersion: String,
    val generation: Int,
    val wasNew: Boolean,
    val sessionId: String?,
    val retryOfEventId: String?,
    val promptDirection: ReviewMode?,
    val legacyReviewStateId: String? = null,
)
