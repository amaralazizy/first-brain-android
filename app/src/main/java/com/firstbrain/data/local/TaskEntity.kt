package com.firstbrain.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String?,
    val urgency: Urgency,
    @ColumnInfo(name = "task_type") val taskType: TaskType,
    @ColumnInfo(name = "estimated_effort") val estimatedEffort: Int,
    val deadline: Instant?,
    @ColumnInfo(name = "has_deadline") val hasDeadline: Boolean,
    @ColumnInfo(name = "skip_count") val skipCount: Int = 0,
    val status: TaskStatus = TaskStatus.pending,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now(),
    @ColumnInfo(name = "completed_at") val completedAt: Instant? = null,
    @ColumnInfo(name = "last_interacted_at") val lastInteractedAt: Instant? = null,
    /** Cached recommendation score from local heuristic or remote model. */
    @ColumnInfo(name = "rec_score") val recScore: Double? = null,
    /** Sync flags for remote Neon Postgres. */
    @ColumnInfo(name = "dirty") val dirty: Boolean = true,
    @ColumnInfo(name = "deleted") val deleted: Boolean = false,
)
