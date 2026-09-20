package com.example.localvocabulary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "senses",
    foreignKeys = [
        ForeignKey(
            entity = VocabularyEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entry_id"), Index(value = ["stable_id"], unique = true)],
)
data class SenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "entry_id")
    val entryId: Long,
    val meaning: String,
    @ColumnInfo(name = "part_of_speech")
    val partOfSpeech: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "grammatical_gender")
    val grammaticalGender: String? = null,
    @ColumnInfo(name = "grammatical_gender_raw")
    val grammaticalGenderRaw: String? = null,
    @ColumnInfo(name = "stable_id", defaultValue = "''")
    val stableId: String = java.util.UUID.randomUUID().toString(),
)
