package com.firstbrain.data.remote

import kotlinx.serialization.Serializable

/**
 * Wire shape of a row in `public.tasks` as exposed by the Neon Data API (PostgREST).
 *
 *  - `user_id` is set server-side from `auth.user_id()` and omitted on writes.
 *  - All timestamp fields are ISO-8601 strings (`timestamptz`).
 *  - Enums match the SQL `check` constraints exactly (urgency, task_type, status).
 */
@Serializable
data class TaskRemoteDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val urgency: String,
    val task_type: String,
    val estimated_effort: Int,
    val deadline: String? = null,
    val has_deadline: Boolean,
    val skip_count: Int = 0,
    val status: String = "pending",
    val rec_score: Double? = null,
    val created_at: String,
    val updated_at: String,
    val completed_at: String? = null,
    val last_interacted_at: String? = null,
    val deleted: Boolean = false,
)
