package com.example.localvocabulary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "pronunciation_dictionary_provenance",
    foreignKeys = [
        ForeignKey(
            entity = VocabularyPronunciationEntity::class,
            parentColumns = ["id"],
            childColumns = ["pronunciation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PronunciationDictionaryProvenanceEntity(
    @PrimaryKey
    @ColumnInfo(name = "pronunciation_id")
    val pronunciationId: Long,
    @ColumnInfo(name = "provider_id") val providerId: String,
    @ColumnInfo(name = "source_entry_id") val sourceEntryId: String?,
    @ColumnInfo(name = "source_sense_id") val sourceSenseId: String?,
    @ColumnInfo(name = "source_name") val sourceName: String,
    @ColumnInfo(name = "source_url") val sourceUrl: String?,
    @ColumnInfo(name = "license_name") val licenseName: String,
    @ColumnInfo(name = "license_url") val licenseUrl: String?,
    @ColumnInfo(name = "dataset_version") val datasetVersion: String?,
    @ColumnInfo(name = "imported_at_epoch_millis") val importedAtEpochMillis: Long,
    @ColumnInfo(name = "modified_after_import") val modifiedAfterImport: Boolean,
)
