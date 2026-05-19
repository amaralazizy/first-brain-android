package com.firstbrain.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InteractionDao {

    @Insert
    suspend fun insert(interaction: InteractionEntity): Long

    @Query("SELECT * FROM task_interactions WHERE task_id = :taskId ORDER BY occurred_at DESC")
    fun observeForTask(taskId: String): Flow<List<InteractionEntity>>

    @Query("""
        SELECT `action`, COUNT(*) AS count
        FROM task_interactions
        WHERE occurred_at >= :sinceMillis
        GROUP BY `action`
    """)
    suspend fun actionCountsSince(sinceMillis: Long): List<ActionCount>
}

data class ActionCount(val action: InteractionAction, val count: Int)
