package com.example.localvocabulary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "sense_dictionary_provenance_fields",
    primaryKeys = ["sense_id", "field"],
    foreignKeys = [
        ForeignKey(
            entity = SenseDictionaryProvenanceEntity::class,
            parentColumns = ["sense_id"],
            childColumns = ["sense_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SenseDictionaryProvenanceFieldEntity(
    @ColumnInfo(name = "sense_id")
    val senseId: Long,
    val field: String,
)
