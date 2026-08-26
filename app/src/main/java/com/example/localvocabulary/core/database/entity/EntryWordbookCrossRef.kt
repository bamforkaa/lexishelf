package com.example.localvocabulary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "entry_wordbook_cross_refs",
    primaryKeys = ["entry_id", "wordbook_id"],
    foreignKeys = [
        ForeignKey(
            entity = VocabularyEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WordbookEntity::class,
            parentColumns = ["id"],
            childColumns = ["wordbook_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("wordbook_id")],
)
data class EntryWordbookCrossRef(
    @ColumnInfo(name = "entry_id")
    val entryId: Long,
    @ColumnInfo(name = "wordbook_id")
    val wordbookId: Long,
)
