// AI-assisted: drafted with Claude (Anthropic), reviewed and adapted by the team.
// See README §12 for the team's originality statement.

package com.firstbrain.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FeedbackOutboxDao {

    @Insert
    suspend fun insert(event: FeedbackOutboxEntity): Long

    @Query("SELECT * FROM feedback_outbox ORDER BY id ASC")
    suspend fun all(): List<FeedbackOutboxEntity>

    @Query("DELETE FROM feedback_outbox WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM feedback_outbox")
    suspend fun count(): Int
}