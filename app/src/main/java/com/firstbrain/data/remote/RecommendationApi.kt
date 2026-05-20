// AI-assisted: drafted with Claude (Anthropic), reviewed and adapted by the team.
// See README §12 for the team's originality statement.

package com.firstbrain.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class TaskFeatures(
    val id: String,
    val days_since_creation: Double,
    val days_since_last_interaction: Double,
    val days_until_deadline: Double,
    val is_overdue: Int,
    val deadline_proximity: Double,
    val skip_count: Int,
    val estimated_effort: Int,
    val has_deadline: Int,
    val weekday: Int,
    val is_weekend: Int,
    val task_type: String, // Do | Learn | Life | Idea
    val urgency: String    // Low | Medium | High
)

@Serializable
data class RecommendRequest(
    val tasks: List<TaskFeatures>,
    val top_k: Int = 5
)

@Serializable
data class FeatureContribution(
    val feature: String,
    val shap_value: Double
)

@Serializable
data class ScoredTask(
    val id: String,
    val score: Double,
    val explanation: List<FeatureContribution>
)

@Serializable
data class FeedbackRequest(
    val task_id: String,
    val action: String, // "complete" | "skip"
    val score: Double? = null
)

interface RecommendationApi {
    @POST("recommend")
    suspend fun recommend(@Body request: RecommendRequest): List<ScoredTask>

    @POST("feedback")
    suspend fun sendFeedback(@Body feedback: FeedbackRequest)
}