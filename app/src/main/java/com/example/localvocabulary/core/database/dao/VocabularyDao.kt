package com.example.localvocabulary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.localvocabulary.core.database.entity.EntryTagCrossRef
import com.example.localvocabulary.core.database.entity.EntryDictionaryProvenanceEntity
import com.example.localvocabulary.core.database.entity.ExampleEntity
import com.example.localvocabulary.core.database.entity.SenseEntity
import com.example.localvocabulary.core.database.entity.SenseDictionaryProvenanceEntity
import com.example.localvocabulary.core.database.entity.SenseDictionaryProvenanceFieldEntity
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity
import com.example.localvocabulary.core.database.entity.VocabularyPronunciationEntity
import com.example.localvocabulary.core.database.entity.PronunciationDictionaryProvenanceEntity
import com.example.localvocabulary.core.database.entity.EntryWordbookCrossRef
import com.example.localvocabulary.core.database.relation.VocabularyEntryWithDetails
import com.example.localvocabulary.core.database.relation.VocabularyListEntryWithDetails
import kotlinx.coroutines.flow.Flow

data class SenseWrite(
    val meaning: String,
    val partOfSpeech: String,
    val examples: List<ExampleWrite>,
    val provenance: SenseDictionaryProvenanceWrite? = null,
    val grammaticalGender: String? = null,
    val grammaticalGenderRaw: String? = null,
    val stableId: String = java.util.UUID.randomUUID().toString(),
)

data class ExampleWrite(
    val text: String,
    val stableId: String = java.util.UUID.randomUUID().toString(),
    val meaning: String = "",
    val origin: String = "UNKNOWN",
    val sourceTitle: String? = null,
    val sourceUrl: String? = null,
    val sourceLocator: String? = null,
    val capturedAt: Long? = null,
)

data class PronunciationWrite(
    val stableId: String,
    val notation: String,
    val value: String,
    val languageTag: String?,
    val provenance: SenseDictionaryProvenanceWrite? = null,
)

data class SenseDictionaryProvenanceWrite(
    val providerId: String,
    val sourceEntryId: String?,
    val sourceSenseId: String?,
    val sourceName: String,
    val sourceUrl: String?,
    val licenseName: String,
    val licenseUrl: String?,
    val datasetVersion: String?,
    val importedFields: Set<String>,
    val importedAtEpochMillis: Long,
    val modifiedAfterImport: Boolean,
)

data class VocabularyPracticeRow(
    val entryId: Long,
    val headword: String,
    val languageTag: String,
    val representativeMeaning: String,
    val reading: String,
    val pronunciation: String,
    val partOfSpeech: String,
    val grammaticalGender: String,
    val example: String,
)

@Dao
interface VocabularyDao {
    @Transaction
    @Query(
        """
        SELECT DISTINCT vocabulary_entries.*
        FROM vocabulary_entries
        LEFT JOIN senses ON senses.entry_id = vocabulary_entries.id
        LEFT JOIN examples ON examples.sense_id = senses.id
        WHERE (
            :query = '' OR
            vocabulary_entries.headword LIKE '%' || :query || '%' ESCAPE '\' COLLATE NOCASE OR
            vocabulary_entries.notes LIKE '%' || :query || '%' ESCAPE '\' COLLATE NOCASE OR
            vocabulary_entries.reading LIKE '%' || :query || '%' ESCAPE '\' COLLATE NOCASE OR
            senses.meaning LIKE '%' || :query || '%' ESCAPE '\' COLLATE NOCASE OR
            examples.text LIKE '%' || :query || '%' ESCAPE '\' COLLATE NOCASE
        )
        AND (
            :tagId IS NULL OR EXISTS (
                SELECT 1 FROM entry_tag_cross_refs
                WHERE entry_tag_cross_refs.entry_id = vocabulary_entries.id
                AND entry_tag_cross_refs.tag_id = :tagId
            )
        )
        AND (
            :wordbookId IS NULL OR EXISTS (
                SELECT 1 FROM entry_wordbook_cross_refs
                WHERE entry_wordbook_cross_refs.entry_id = vocabulary_entries.id
                AND entry_wordbook_cross_refs.wordbook_id = :wordbookId
            )
        )
        AND (:languageTag IS NULL OR vocabulary_entries.language_tag = :languageTag)
        ORDER BY vocabulary_entries.modified_at_epoch_millis DESC,
                 vocabulary_entries.headword COLLATE NOCASE ASC
        """,
    )
    fun observeEntries(
        query: String,
        tagId: Long?,
        wordbookId: Long? = null,
        languageTag: String? = null,
    ): Flow<List<VocabularyListEntryWithDetails>>

    @Query("SELECT DISTINCT language_tag FROM vocabulary_entries ORDER BY language_tag ASC")
    fun observeLanguages(): Flow<List<String>>

    @Query(
        """
        SELECT
            entry.id AS entryId,
            entry.headword AS headword,
            entry.language_tag AS languageTag,
            COALESCE(
                (
                    SELECT sense.meaning
                    FROM senses AS sense
                    WHERE sense.entry_id = entry.id AND TRIM(sense.meaning) <> ''
                    ORDER BY sense.sort_order ASC, sense.id ASC
                    LIMIT 1
                ),
                ''
            ) AS representativeMeaning,
            entry.reading AS reading,
            COALESCE(
                (
                    SELECT pronunciation.value
                    FROM vocabulary_pronunciations AS pronunciation
                    WHERE pronunciation.entry_id = entry.id AND TRIM(pronunciation.value) <> ''
                    ORDER BY pronunciation.sort_order ASC, pronunciation.id ASC
                    LIMIT 1
                ),
                ''
            ) AS pronunciation,
            COALESCE(
                (
                    SELECT sense.part_of_speech
                    FROM senses AS sense
                    WHERE sense.entry_id = entry.id AND TRIM(sense.part_of_speech) <> ''
                    ORDER BY sense.sort_order ASC, sense.id ASC
                    LIMIT 1
                ),
                ''
            ) AS partOfSpeech,
            COALESCE(
                (
                    SELECT COALESCE(
                        NULLIF(TRIM(sense.grammatical_gender_raw), ''),
                        sense.grammatical_gender
                    )
                    FROM senses AS sense
                    WHERE sense.entry_id = entry.id AND (
                        TRIM(COALESCE(sense.grammatical_gender_raw, '')) <> '' OR
                        TRIM(COALESCE(sense.grammatical_gender, '')) <> ''
                    )
                    ORDER BY sense.sort_order ASC, sense.id ASC
                    LIMIT 1
                ),
                ''
            ) AS grammaticalGender,
            COALESCE(
                (
                    SELECT example.text
                    FROM examples AS example
                    INNER JOIN senses AS sense ON sense.id = example.sense_id
                    WHERE sense.entry_id = entry.id AND TRIM(example.text) <> ''
                    ORDER BY sense.sort_order ASC, example.sort_order ASC, example.id ASC
                    LIMIT 1
                ),
                ''
            ) AS example
        FROM vocabulary_entries AS entry
        WHERE TRIM(entry.headword) <> ''
        AND TRIM(entry.language_tag) <> ''
        AND (:languageTag IS NULL OR entry.language_tag = :languageTag)
        AND (
            :wordbookId IS NULL OR EXISTS (
                SELECT 1 FROM entry_wordbook_cross_refs AS relation
                WHERE relation.entry_id = entry.id AND relation.wordbook_id = :wordbookId
            )
        )
        AND (
            :tagId IS NULL OR EXISTS (
                SELECT 1 FROM entry_tag_cross_refs AS relation
                WHERE relation.entry_id = entry.id AND relation.tag_id = :tagId
            )
        )
        AND (
            TRIM(entry.reading) <> '' OR
            EXISTS (
                SELECT 1 FROM senses AS sense
                WHERE sense.entry_id = entry.id AND (
                    TRIM(sense.meaning) <> '' OR
                    TRIM(sense.part_of_speech) <> '' OR
                    TRIM(COALESCE(sense.grammatical_gender, '')) <> '' OR
                    TRIM(COALESCE(sense.grammatical_gender_raw, '')) <> ''
                )
            ) OR
            EXISTS (
                SELECT 1 FROM examples AS example
                INNER JOIN senses AS sense ON sense.id = example.sense_id
                WHERE sense.entry_id = entry.id AND TRIM(example.text) <> ''
            ) OR
            EXISTS (
                SELECT 1 FROM vocabulary_pronunciations AS pronunciation
                WHERE pronunciation.entry_id = entry.id AND TRIM(pronunciation.value) <> ''
            )
        )
        ORDER BY entry.id ASC
        """,
    )
    suspend fun findPracticeRows(
        languageTag: String?,
        wordbookId: Long?,
        tagId: Long?,
    ): List<VocabularyPracticeRow>

    @Transaction
    @Query("SELECT * FROM vocabulary_entries WHERE language_tag = :languageTag ORDER BY id ASC")
    suspend fun findEntriesByLanguage(languageTag: String): List<VocabularyEntryWithDetails>

    @Transaction
    @Query("SELECT * FROM vocabulary_entries WHERE id = :id")
    fun observeEntry(id: Long): Flow<VocabularyEntryWithDetails?>

    @Transaction
    @Query("SELECT * FROM vocabulary_entries ORDER BY id ASC")
    suspend fun getAllEntries(): List<VocabularyEntryWithDetails>

    @Query("SELECT * FROM vocabulary_entries WHERE backup_id = :backupId LIMIT 1")
    suspend fun findEntryByBackupId(backupId: String): VocabularyEntryEntity?

    @Query("SELECT * FROM vocabulary_entries WHERE id = :id")
    suspend fun findEntryEntity(id: Long): VocabularyEntryEntity?

    @Insert
    suspend fun insertEntry(entry: VocabularyEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: VocabularyEntryEntity): Int

    @Query("DELETE FROM vocabulary_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM vocabulary_entries")
    suspend fun deleteAllEntries()

    @Query("DELETE FROM senses WHERE entry_id = :entryId")
    suspend fun deleteSenses(entryId: Long)

    @Query("SELECT * FROM senses WHERE stable_id = :stableId")
    suspend fun findSense(stableId: String): SenseEntity?

    @Query("SELECT * FROM examples WHERE stable_id = :stableId")
    suspend fun findExample(stableId: String): ExampleEntity?

    @Update
    suspend fun updateSense(sense: SenseEntity): Int

    @Update
    suspend fun updateExample(example: ExampleEntity): Int

    @Query("DELETE FROM senses WHERE entry_id = :entryId AND stable_id NOT IN (:retainedIds)")
    suspend fun deleteRemovedSenses(entryId: Long, retainedIds: List<String>)

    @Query("DELETE FROM examples WHERE sense_id = :senseId")
    suspend fun deleteExamples(senseId: Long)

    @Query("DELETE FROM examples WHERE sense_id = :senseId AND stable_id NOT IN (:retainedIds)")
    suspend fun deleteRemovedExamples(senseId: Long, retainedIds: List<String>)

    @Query("DELETE FROM sense_dictionary_provenance WHERE sense_id = :senseId")
    suspend fun deleteSenseProvenance(senseId: Long)

    @Insert
    suspend fun insertSense(sense: SenseEntity): Long

    @Insert
    suspend fun insertExamples(examples: List<ExampleEntity>)

    @Insert
    suspend fun insertSenseProvenance(provenance: SenseDictionaryProvenanceEntity)

    @Insert
    suspend fun insertSenseProvenanceFields(fields: List<SenseDictionaryProvenanceFieldEntity>)

    @Query("DELETE FROM entry_dictionary_provenance WHERE entry_id = :entryId")
    suspend fun deleteEntryProvenance(entryId: Long)

    @Insert
    suspend fun insertEntryProvenance(provenance: EntryDictionaryProvenanceEntity)

    @Query("DELETE FROM vocabulary_pronunciations WHERE entry_id = :entryId")
    suspend fun deletePronunciations(entryId: Long)

    @Insert
    suspend fun insertPronunciation(pronunciation: VocabularyPronunciationEntity): Long

    @Insert
    suspend fun insertPronunciationProvenance(
        provenance: PronunciationDictionaryProvenanceEntity,
    )

    @Query("DELETE FROM entry_tag_cross_refs WHERE entry_id = :entryId")
    suspend fun deleteEntryTags(entryId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntryTags(crossRefs: List<EntryTagCrossRef>)

    @Query("DELETE FROM entry_wordbook_cross_refs WHERE entry_id = :entryId")
    suspend fun deleteEntryWordbooks(entryId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntryWordbooks(crossRefs: List<EntryWordbookCrossRef>)

    @Transaction
    suspend fun saveEntry(
        entry: VocabularyEntryEntity,
        senses: List<SenseWrite>,
        tagIds: Set<Long>,
        readingProvenance: SenseDictionaryProvenanceWrite? = null,
        wordbookIds: Set<Long> = emptySet(),
        pronunciations: List<PronunciationWrite> = emptyList(),
    ): Long {
        val entryId = if (entry.id == 0L) {
            insertEntry(entry)
        } else {
            check(updateEntry(entry) == 1) { "Vocabulary entry ${entry.id} no longer exists" }
            entry.id
        }

        require(senses.map { it.stableId }.distinct().size == senses.size) {
            "Duplicate sense identities"
        }
        val exampleIds = senses.flatMap { it.examples }.map { it.stableId }
        require(exampleIds.distinct().size == exampleIds.size) { "Duplicate example identities" }
        // Check ownership before deletions so a backup cannot move a child between parents.
        senses.forEach { sense ->
            val existing = findSense(sense.stableId)
            require(existing == null || existing.entryId == entryId) { "Sense belongs to another entry" }
            sense.examples.forEach { example ->
                val existingExample = findExample(example.stableId)
                require(existingExample == null || existingExample.senseId == existing?.id) {
                    "Example belongs to another sense"
                }
            }
        }
        if (senses.isEmpty()) deleteSenses(entryId)
        else deleteRemovedSenses(entryId, senses.map { it.stableId })
        deleteEntryProvenance(entryId)
        readingProvenance?.let { provenance ->
            insertEntryProvenance(
                EntryDictionaryProvenanceEntity(
                    entryId = entryId,
                    field = "READING",
                    providerId = provenance.providerId,
                    sourceEntryId = provenance.sourceEntryId,
                    sourceSenseId = provenance.sourceSenseId,
                    sourceName = provenance.sourceName,
                    sourceUrl = provenance.sourceUrl,
                    licenseName = provenance.licenseName,
                    licenseUrl = provenance.licenseUrl,
                    datasetVersion = provenance.datasetVersion,
                    importedAtEpochMillis = provenance.importedAtEpochMillis,
                    modifiedAfterImport = provenance.modifiedAfterImport,
                ),
            )
        }
        deletePronunciations(entryId)
        pronunciations.forEachIndexed { index, pronunciation ->
            val pronunciationId = insertPronunciation(
                VocabularyPronunciationEntity(
                    stableId = pronunciation.stableId,
                    entryId = entryId,
                    notation = pronunciation.notation,
                    value = pronunciation.value,
                    languageTag = pronunciation.languageTag,
                    sortOrder = index,
                ),
            )
            pronunciation.provenance?.let { provenance ->
                insertPronunciationProvenance(
                    PronunciationDictionaryProvenanceEntity(
                        pronunciationId = pronunciationId,
                        providerId = provenance.providerId,
                        sourceEntryId = provenance.sourceEntryId,
                        sourceSenseId = provenance.sourceSenseId,
                        sourceName = provenance.sourceName,
                        sourceUrl = provenance.sourceUrl,
                        licenseName = provenance.licenseName,
                        licenseUrl = provenance.licenseUrl,
                        datasetVersion = provenance.datasetVersion,
                        importedAtEpochMillis = provenance.importedAtEpochMillis,
                        modifiedAfterImport = provenance.modifiedAfterImport,
                    ),
                )
            }
        }
        senses.forEachIndexed { senseIndex, sense ->
            val existingSense = findSense(sense.stableId)
            val senseEntity = SenseEntity(
                id = existingSense?.id ?: 0,
                stableId = sense.stableId,
                entryId = entryId,
                meaning = sense.meaning,
                partOfSpeech = sense.partOfSpeech,
                sortOrder = senseIndex,
                grammaticalGender = sense.grammaticalGender,
                grammaticalGenderRaw = sense.grammaticalGenderRaw,
            )
            val senseId = if (existingSense == null) insertSense(senseEntity) else {
                check(updateSense(senseEntity) == 1)
                existingSense.id
            }
            if (sense.examples.isEmpty()) deleteExamples(senseId)
            else deleteRemovedExamples(senseId, sense.examples.map { it.stableId })
            sense.examples.forEachIndexed { exampleIndex, example ->
                val existingExample = findExample(example.stableId)
                val exampleEntity = ExampleEntity(
                    id = existingExample?.id ?: 0,
                    stableId = example.stableId,
                    senseId = senseId,
                    text = example.text,
                    sortOrder = exampleIndex,
                    meaning = example.meaning,
                    origin = example.origin,
                    sourceTitle = example.sourceTitle,
                    sourceUrl = example.sourceUrl,
                    sourceLocator = example.sourceLocator,
                    capturedAt = example.capturedAt,
                )
                if (existingExample == null) insertExamples(listOf(exampleEntity))
                else check(updateExample(exampleEntity) == 1)
            }
            deleteSenseProvenance(senseId)
            sense.provenance?.let { provenance ->
                insertSenseProvenance(
                    SenseDictionaryProvenanceEntity(
                        senseId = senseId,
                        providerId = provenance.providerId,
                        sourceEntryId = provenance.sourceEntryId,
                        sourceSenseId = provenance.sourceSenseId,
                        sourceName = provenance.sourceName,
                        sourceUrl = provenance.sourceUrl,
                        licenseName = provenance.licenseName,
                        licenseUrl = provenance.licenseUrl,
                        datasetVersion = provenance.datasetVersion,
                        importedAtEpochMillis = provenance.importedAtEpochMillis,
                        modifiedAfterImport = provenance.modifiedAfterImport,
                    ),
                )
                insertSenseProvenanceFields(
                    provenance.importedFields.sorted().map { field ->
                        SenseDictionaryProvenanceFieldEntity(senseId, field)
                    },
                )
            }
        }

        deleteEntryTags(entryId)
        insertEntryTags(tagIds.map { tagId -> EntryTagCrossRef(entryId, tagId) })
        deleteEntryWordbooks(entryId)
        insertEntryWordbooks(
            wordbookIds.map { wordbookId -> EntryWordbookCrossRef(entryId, wordbookId) },
        )
        return entryId
    }
}
