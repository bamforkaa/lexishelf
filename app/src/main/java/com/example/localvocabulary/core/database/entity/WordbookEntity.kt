package com.example.localvocabulary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wordbooks",
    indices = [
        Index(value = ["normalized_name"], unique = true),
        Index(value = ["backup_id"], unique = true),
    ],
)
data class WordbookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "backup_id")
    val backupId: String,
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
)
