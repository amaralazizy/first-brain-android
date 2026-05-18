package com.firstbrain.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY created_at DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("""
        SELECT * FROM tasks
        WHERE status = 'pending'
        ORDER BY rec_score DESC, urgency DESC
    """)
    fun observePicks(): Flow<List<TaskEntity>>

    @Query("""
        SELECT * FROM tasks
        WHERE status IN ('completed', 'skipped')
        ORDER BY updated_at DESC
    """)
    fun observeHistory(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun byId(id: Int): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    fun observeById(id: Int): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE status = 'pending'")
    suspend fun pending(): List<TaskEntity>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE tasks SET rec_score = :score WHERE id = :id")
    suspend fun updateScore(id: Int, score: Double?)
}
