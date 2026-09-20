package com.example.localvocabulary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.localvocabulary.core.database.entity.ReviewEventEntity
import com.example.localvocabulary.core.database.entity.ReviewStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_states ORDER BY stable_id")
    fun observeStates(): Flow<List<ReviewStateEntity>>

    @Query("SELECT * FROM review_states ORDER BY stable_id")
    suspend fun states(): List<ReviewStateEntity>

    @Query("SELECT * FROM review_states WHERE stable_id = :id")
    suspend fun state(id: String): ReviewStateEntity?

    @Query("SELECT * FROM review_states WHERE sense_stable_id = :senseId")
    suspend fun stateForSense(senseId: String): ReviewStateEntity?

    @Insert suspend fun insertState(state: ReviewStateEntity)
    @Update suspend fun updateState(state: ReviewStateEntity)

    @Query("SELECT * FROM review_events ORDER BY reviewed_at, stable_id")
    suspend fun events(): List<ReviewEventEntity>

    @Query("SELECT * FROM review_events WHERE reviewed_at >= :start AND reviewed_at < :end ORDER BY reviewed_at, stable_id")
    suspend fun eventsBetween(start: Long, end: Long): List<ReviewEventEntity>

    @Query("SELECT * FROM review_events WHERE review_state_id = :stateId ORDER BY reviewed_at, stable_id")
    suspend fun eventsForState(stateId: String): List<ReviewEventEntity>

    @Query("SELECT * FROM review_events WHERE stable_id = :id")
    suspend fun event(id: String): ReviewEventEntity?

    @Insert suspend fun insertEvent(event: ReviewEventEntity)

}
