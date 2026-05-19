package com.firstbrain.data.remote

import com.firstbrain.data.local.TaskEntity
import com.firstbrain.data.local.TaskStatus
import com.firstbrain.data.local.TaskType
import com.firstbrain.data.local.Urgency
import java.time.Instant
import java.time.format.DateTimeFormatter

private val iso: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

fun TaskEntity.toRemoteDto(): TaskRemoteDto = TaskRemoteDto(
    id = id,
    title = title,
    description = description,
    urgency = urgency.name,
    task_type = taskType.name,
    estimated_effort = estimatedEffort,
    deadline = deadline?.let { iso.format(it) },
    has_deadline = hasDeadline,
    skip_count = skipCount,
    status = status.name,
    rec_score = recScore,
    created_at = iso.format(createdAt),
    updated_at = iso.format(updatedAt),
    completed_at = completedAt?.let { iso.format(it) },
    last_interacted_at = lastInteractedAt?.let { iso.format(it) },
    deleted = deleted,
)

fun TaskRemoteDto.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    urgency = parseUrgency(urgency),
    taskType = parseTaskType(task_type),
    estimatedEffort = estimated_effort,
    deadline = deadline?.let(Instant::parse),
    hasDeadline = has_deadline,
    skipCount = skip_count,
    status = parseStatus(status),
    createdAt = Instant.parse(created_at),
    updatedAt = Instant.parse(updated_at),
    completedAt = completed_at?.let(Instant::parse),
    lastInteractedAt = last_interacted_at?.let(Instant::parse),
    recScore = rec_score,
    dirty = false,           // freshly pulled from server
    deleted = deleted,
)

private fun parseUrgency(s: String): Urgency = runCatching { Urgency.valueOf(s) }.getOrDefault(Urgency.Medium)
private fun parseTaskType(s: String): TaskType = runCatching { TaskType.valueOf(s) }.getOrDefault(TaskType.other)
private fun parseStatus(s: String): TaskStatus = runCatching { TaskStatus.valueOf(s) }.getOrDefault(TaskStatus.pending)
