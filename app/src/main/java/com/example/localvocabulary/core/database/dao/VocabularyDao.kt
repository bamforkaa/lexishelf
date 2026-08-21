package com.example.localvocabulary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.localvocabulary.core.database.entity.EntryTagCrossRef
import com.example.localvocabulary.core.database.entity.ExampleEntity
import com.example.localvocabulary.core.database.entity.SenseEntity
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity
import com.example.localvocabulary.core.database.relation.VocabularyEntryWithDetails
import kotlinx.coroutines.flow.Flow

data class SenseWrite(
    val meaning: String,
    val partOfSpeech: String,
    val examples: List<String>,
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
        ORDER BY vocabulary_entries.modified_at_epoch_millis DESC,
                 vocabulary_entries.headword COLLATE NOCASE ASC
        """,
    )
    fun observeEntries(query: String, tagId: Long?): Flow<List<VocabularyEntryWithDetails>>

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

    @Insert
    suspend fun insertSense(sense: SenseEntity): Long

    @Insert
    suspend fun insertExamples(examples: List<ExampleEntity>)

    @Query("DELETE FROM entry_tag_cross_refs WHERE entry_id = :entryId")
    suspend fun deleteEntryTags(entryId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntryTags(crossRefs: List<EntryTagCrossRef>)

    @Transaction
    suspend fun saveEntry(
        entry: VocabularyEntryEntity,
        senses: List<SenseWrite>,
        tagIds: Set<Long>,
    ): Long {
        val entryId = if (entry.id == 0L) {
            insertEntry(entry)
        } else {
            check(updateEntry(entry) == 1) { "Vocabulary entry ${entry.id} no longer exists" }
            entry.id
        }

        deleteSenses(entryId)
        senses.forEachIndexed { senseIndex, sense ->
            val senseId = insertSense(
                SenseEntity(
                    entryId = entryId,
                    meaning = sense.meaning,
                    partOfSpeech = sense.partOfSpeech,
                    sortOrder = senseIndex,
                ),
            )
            insertExamples(
                sense.examples.mapIndexed { exampleIndex, text ->
                    ExampleEntity(
                        senseId = senseId,
                        text = text,
                        sortOrder = exampleIndex,
                    )
                },
            )
        }

        deleteEntryTags(entryId)
        insertEntryTags(tagIds.map { tagId -> EntryTagCrossRef(entryId, tagId) })
        return entryId
    }
}
