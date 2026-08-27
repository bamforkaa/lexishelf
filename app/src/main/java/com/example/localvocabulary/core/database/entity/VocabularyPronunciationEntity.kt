package com.example.localvocabulary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vocabulary_pronunciations",
    foreignKeys = [
        ForeignKey(
            entity = VocabularyEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("entry_id"),
        Index(value = ["stable_id"], unique = true),
    ],
)
data class VocabularyPronunciationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "stable_id")
    val stableId: String,
    @ColumnInfo(name = "entry_id")
    val entryId: Long,
    val notation: String,
    val value: String,
    @ColumnInfo(name = "language_tag")
    val languageTag: String?,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
)
