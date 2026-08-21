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
    indices = [Index("sense_id")],
)
data class ExampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "sense_id")
    val senseId: Long,
    val text: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
)
