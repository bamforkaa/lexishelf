package com.example.localvocabulary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_states",
    foreignKeys = [ForeignKey(
        entity = SenseEntity::class, parentColumns = ["stable_id"], childColumns = ["sense_stable_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["sense_stable_id"], unique = true), Index("next_review_at")],
)
data class ReviewStateEntity(
    @PrimaryKey @ColumnInfo(name = "stable_id") val stableId: String,
    @ColumnInfo(name = "sense_stable_id") val senseStableId: String,
    val enabled: Boolean,
    val stage: Int,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long?,
    @ColumnInfo(name = "next_review_at") val nextReviewAt: Long,
    @ColumnInfo(name = "scheduler_version") val schedulerVersion: String,
    val generation: Int,
)

@Entity(
    tableName = "review_events",
    foreignKeys = [ForeignKey(
        entity = ReviewStateEntity::class, parentColumns = ["stable_id"], childColumns = ["review_state_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("review_state_id"), Index("reviewed_at"), Index(value = ["retry_of_event_id"], unique = true)],
)
data class ReviewEventEntity(
    @PrimaryKey @ColumnInfo(name = "stable_id") val stableId: String,
    @ColumnInfo(name = "review_state_id") val reviewStateId: String,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Long,
    val rating: String?,
    val kind: String,
    @ColumnInfo(name = "scheduler_version") val schedulerVersion: String,
    val generation: Int,
    @ColumnInfo(name = "was_new") val wasNew: Boolean,
    @ColumnInfo(name = "session_id") val sessionId: String?,
    @ColumnInfo(name = "retry_of_event_id") val retryOfEventId: String?,
    @ColumnInfo(name = "prompt_direction") val promptDirection: String?,
    @ColumnInfo(name = "legacy_review_state_id") val legacyReviewStateId: String?,
)
