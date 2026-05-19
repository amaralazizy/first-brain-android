package com.firstbrain.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE deleted = 0 ORDER BY created_at DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("""
        SELECT * FROM tasks
        WHERE status = 'pending' AND deleted = 0
        ORDER BY rec_score DESC, urgency DESC
    """)
    fun observePicks(): Flow<List<TaskEntity>>

    @Query("""
        SELECT * FROM tasks
        WHERE status IN ('completed', 'skipped') AND deleted = 0
        ORDER BY updated_at DESC
    """)
    fun observeHistory(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE status = 'pending' AND deleted = 0")
    suspend fun pending(): List<TaskEntity>

    @Query("""
        SELECT * FROM tasks
        WHERE status = 'pending' AND deleted = 0
        ORDER BY rec_score DESC, urgency DESC
        LIMIT :limit
    """)
    suspend fun topPending(limit: Int): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE dirty = 1")
    suspend fun dirty(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET deleted = 1, dirty = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun hardDeleteById(id: String)

    @Query("UPDATE tasks SET rec_score = :score WHERE id = :id")
    suspend fun updateScore(id: String, score: Double?)

    @Query("UPDATE tasks SET dirty = 0 WHERE id = :id")
    suspend fun clearDirty(id: String)
}
