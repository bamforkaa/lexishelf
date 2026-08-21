package com.example.localvocabulary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vocabulary_entries")
data class VocabularyEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val headword: String,
    @ColumnInfo(name = "language_tag")
    val languageTag: String,
    val notes: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "modified_at_epoch_millis")
    val modifiedAtEpochMillis: Long,
)
