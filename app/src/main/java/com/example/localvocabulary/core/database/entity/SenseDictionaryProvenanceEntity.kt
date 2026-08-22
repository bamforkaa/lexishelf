package com.example.localvocabulary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "sense_dictionary_provenance",
    foreignKeys = [
        ForeignKey(
            entity = SenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sense_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SenseDictionaryProvenanceEntity(
    @PrimaryKey
    @ColumnInfo(name = "sense_id")
    val senseId: Long,
    @ColumnInfo(name = "provider_id")
    val providerId: String,
    @ColumnInfo(name = "source_entry_id")
    val sourceEntryId: String?,
    @ColumnInfo(name = "source_sense_id")
    val sourceSenseId: String?,
    @ColumnInfo(name = "source_name")
    val sourceName: String,
    @ColumnInfo(name = "source_url")
    val sourceUrl: String?,
    @ColumnInfo(name = "license_name")
    val licenseName: String,
    @ColumnInfo(name = "license_url")
    val licenseUrl: String?,
    @ColumnInfo(name = "dataset_version")
    val datasetVersion: String?,
    @ColumnInfo(name = "imported_at_epoch_millis")
    val importedAtEpochMillis: Long,
    @ColumnInfo(name = "modified_after_import")
    val modifiedAfterImport: Boolean,
)
