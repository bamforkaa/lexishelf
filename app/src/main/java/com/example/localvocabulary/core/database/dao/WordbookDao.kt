package com.example.localvocabulary.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.localvocabulary.core.database.entity.EntryWordbookCrossRef
import com.example.localvocabulary.core.database.entity.WordbookEntity
import kotlinx.coroutines.flow.Flow

data class WordbookWithEntryCount(
    @Embedded val wordbook: WordbookEntity,
    val entryCount: Int,
)

@Dao
interface WordbookDao {
    @Query("SELECT * FROM wordbooks ORDER BY name COLLATE NOCASE ASC")
    fun observeWordbooks(): Flow<List<WordbookEntity>>

    @Query(
        """
        SELECT wordbooks.*, COUNT(entry_wordbook_cross_refs.entry_id) AS entryCount
        FROM wordbooks
        LEFT JOIN entry_wordbook_cross_refs
            ON entry_wordbook_cross_refs.wordbook_id = wordbooks.id
        GROUP BY wordbooks.id
        ORDER BY wordbooks.name COLLATE NOCASE ASC
        """,
    )
    fun observeWordbooksWithEntryCounts(): Flow<List<WordbookWithEntryCount>>

    @Query("SELECT * FROM wordbooks ORDER BY id ASC")
    suspend fun getAll(): List<WordbookEntity>

    @Query("SELECT * FROM wordbooks WHERE id = :id")
    suspend fun findById(id: Long): WordbookEntity?

    @Query("SELECT * FROM wordbooks WHERE normalized_name = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): WordbookEntity?

    @Query("SELECT * FROM wordbooks WHERE backup_id = :backupId LIMIT 1")
    suspend fun findByBackupId(backupId: String): WordbookEntity?

    @Insert
    suspend fun insert(wordbook: WordbookEntity): Long

    @Update
    suspend fun update(wordbook: WordbookEntity): Int

    @Query("DELETE FROM wordbooks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM wordbooks")
    suspend fun deleteAll()

    @Query(
        "SELECT entry_id FROM entry_wordbook_cross_refs WHERE wordbook_id = :wordbookId ORDER BY entry_id",
    )
    fun observeEntryIds(wordbookId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemberships(crossRefs: List<EntryWordbookCrossRef>): List<Long>

    @Query(
        "DELETE FROM entry_wordbook_cross_refs " +
            "WHERE wordbook_id = :wordbookId AND entry_id IN (:entryIds)",
    )
    suspend fun deleteMemberships(wordbookId: Long, entryIds: List<Long>): Int

    @Transaction
    suspend fun addEntries(wordbookId: Long, entryIds: Set<Long>): Int =
        insertMemberships(entryIds.map { EntryWordbookCrossRef(it, wordbookId) })
            .count { it != -1L }

    @Transaction
    suspend fun removeEntries(wordbookId: Long, entryIds: Set<Long>): Int =
        if (entryIds.isEmpty()) 0 else deleteMemberships(wordbookId, entryIds.toList())
}
