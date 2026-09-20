package com.example.localvocabulary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "examples",
    foreignKeys = [
        ForeignKey(
            entity = SenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sense_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sense_id"), Index(value = ["stable_id"], unique = true)],
)
data class ExampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "sense_id")
    val senseId: Long,
    val text: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(defaultValue = "''")
    val meaning: String = "",
    @ColumnInfo(defaultValue = "'UNKNOWN'")
    val origin: String = "UNKNOWN",
    @ColumnInfo(name = "source_title")
    val sourceTitle: String? = null,
    @ColumnInfo(name = "source_url")
    val sourceUrl: String? = null,
    @ColumnInfo(name = "source_locator")
    val sourceLocator: String? = null,
    @ColumnInfo(name = "captured_at")
    val capturedAt: Long? = null,
    @ColumnInfo(name = "stable_id", defaultValue = "''")
    val stableId: String = java.util.UUID.randomUUID().toString(),
)
