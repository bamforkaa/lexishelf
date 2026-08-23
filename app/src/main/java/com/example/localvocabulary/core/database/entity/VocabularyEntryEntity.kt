package com.example.localvocabulary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vocabulary_entries",
    indices = [Index(value = ["backup_id"], unique = true)],
)
data class VocabularyEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "backup_id", defaultValue = "''")
    val backupId: String,
    val headword: String,
    @ColumnInfo(name = "language_tag")
    val languageTag: String,
    val notes: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "modified_at_epoch_millis")
    val modifiedAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "''")
    val reading: String = "",
)
