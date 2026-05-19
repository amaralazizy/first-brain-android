package com.firstbrain.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@Serializable
data class TaskFeatures(
    val id: Int,
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
    val id: Int,
    val score: Double,
    val explanation: List<FeatureContribution>
)

@Serializable
data class HealthResponse(
    val status: String,
    val model_ready: Boolean,
    val trained_at: String? = null,
    val roc_auc: Double? = null,
    val precision_at_5: Double? = null
)

@Serializable
data class TrainResponse(
    val metrics: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    val trained_at: String,
    val duration_seconds: Double
)

@Serializable
data class FeedbackRequest(
    val task_id: Int,
    val action: String, // "complete" | "skip"
    val score: Double? = null
)

interface RecommendationApi {
    @POST("recommend")
    suspend fun recommend(@Body request: RecommendRequest): List<ScoredTask>

    @GET("health")
    suspend fun checkHealth(): HealthResponse

    @GET("metrics")
    suspend fun getMetrics(): Map<String, kotlinx.serialization.json.JsonElement>

    @POST("train")
    suspend fun triggerTraining(): TrainResponse

    @POST("feedback")
    suspend fun sendFeedback(@Body feedback: FeedbackRequest)
}
