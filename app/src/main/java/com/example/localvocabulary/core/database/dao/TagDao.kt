package com.example.localvocabulary.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.localvocabulary.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

data class TagWithEntryCount(
    @Embedded val tag: TagEntity,
    val entryCount: Int,
)

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun observeTags(): Flow<List<TagEntity>>

    @Query(
        """
        SELECT tags.*, COUNT(entry_tag_cross_refs.entry_id) AS entryCount
        FROM tags
        LEFT JOIN entry_tag_cross_refs ON entry_tag_cross_refs.tag_id = tags.id
        GROUP BY tags.id
        ORDER BY tags.name COLLATE NOCASE ASC
        """,
    )
    fun observeTagsWithEntryCounts(): Flow<List<TagWithEntryCount>>

    @Query("SELECT * FROM tags ORDER BY id ASC")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun findById(id: Long): TagEntity?

    @Query("SELECT * FROM tags WHERE normalized_name = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): TagEntity?

    @Query("SELECT * FROM tags WHERE backup_id = :backupId LIMIT 1")
    suspend fun findByBackupId(backupId: String): TagEntity?

    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity): Int

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM tags")
    suspend fun deleteAll()
}
