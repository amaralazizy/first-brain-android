package com.firstbrain.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Queued feedback events to be POSTed to `/feedback`. Persisted so mutations
 * made while offline are eventually delivered when connectivity returns.
 */
@Entity(tableName = "feedback_outbox")
data class FeedbackOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: String,
    val action: String,              // "complete" | "skip"
    val score: Double?,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
)
