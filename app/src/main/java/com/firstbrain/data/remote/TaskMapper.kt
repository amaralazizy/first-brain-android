package com.firstbrain.data.remote

import com.firstbrain.data.local.TaskEntity
import com.firstbrain.data.local.TaskStatus
import com.firstbrain.data.local.TaskType
import com.firstbrain.data.local.Urgency
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val iso: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

/**
 * Parse a `timestamptz` string from PostgREST. The Neon Data API often
 * returns values without an offset suffix (e.g. `2026-05-19T22:21:30.548`),
 * which `Instant.parse` rejects. We treat the offset-less form as UTC.
 */
internal fun parseInstant(raw: String): Instant {
    val trimmed = raw.trim()
    val endsWithOffset = trimmed.endsWith("Z") ||
        trimmed.lastIndexOf('+') > trimmed.indexOf('T') ||
        // Watch for the '-' that's part of the offset (after T), not the date dashes.
        trimmed.lastIndexOf('-') > trimmed.indexOf('T')
    return if (endsWithOffset) {
        Instant.parse(trimmed)
    } else {
        LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC)
    }
}

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
    deadline = deadline?.let(::parseInstant),
    hasDeadline = has_deadline,
    skipCount = skip_count,
    status = parseStatus(status),
    createdAt = parseInstant(created_at),
    updatedAt = parseInstant(updated_at),
    completedAt = completed_at?.let(::parseInstant),
    lastInteractedAt = last_interacted_at?.let(::parseInstant),
    recScore = rec_score,
    dirty = false,           // freshly pulled from server
    deleted = deleted,
)

private fun parseUrgency(s: String): Urgency = runCatching { Urgency.valueOf(s) }.getOrDefault(Urgency.Medium)
private fun parseTaskType(s: String): TaskType = runCatching { TaskType.valueOf(s) }.getOrDefault(TaskType.other)
private fun parseStatus(s: String): TaskStatus = runCatching { TaskStatus.valueOf(s) }.getOrDefault(TaskStatus.pending)
