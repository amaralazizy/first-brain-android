package com.firstbrain.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "task_interactions",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("task_id")],
)
data class InteractionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: Int,
    val action: InteractionAction,
    @ColumnInfo(name = "occurred_at") val occurredAt: Instant,
    /** Optional recommendation score at the time the interaction happened. */
    val score: Double? = null,
)
