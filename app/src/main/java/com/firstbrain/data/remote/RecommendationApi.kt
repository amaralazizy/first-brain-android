package com.firstbrain.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@Serializable
data class RecommendationRequest(
    val tasks: List<TaskFeatureWrapper>
)

@Serializable
data class TaskFeatureWrapper(
    val id: String,
    val features: TaskFeatures
)

@Serializable
data class TaskFeatures(
    val priority: Int,
    val estimated_duration: Int,
    val is_recurring: Int,
    val deadline_dist: Double,
    val energy_required: Int,
    val is_work: Int,
    val is_personal: Int,
    val is_health: Int,
    val is_other: Int,
    val day_of_week: Int,
    val hour_of_day: Int,
    val current_energy: Int,
    val work_load: Int,
    val recent_completion_rate: Double,
    val is_morning: Int,
    val is_afternoon: Int,
    val is_evening: Int
)

@Serializable
data class RecommendationResponse(
    val id: String,
    val score: Double,
    val explanations: List<Explanation>
)

@Serializable
data class Explanation(
    val feature: String,
    val impact: Double
)

@Serializable
data class HealthResponse(
    val status: String,
    val model_loaded: Boolean,
    val last_training: String? = null
)

@Serializable
data class MetricsResponse(
    val roc_auc: Double? = null,
    val precision_at_5: Double? = null,
    val recall_at_5: Double? = null,
    val f1: Double? = null,
    val avg_precision: Double? = null,
    val calibration_error: Double? = null
)

@Serializable
data class TrainResponse(
    val status: String,
    val metrics: MetricsResponse? = null,
    val message: String? = null
)

@Serializable
data class FeedbackRequest(
    val task_id: String,
    val action: String,
    val timestamp: String,
    val features: TaskFeatures? = null
)

interface RecommendationApi {
    @POST("recommend")
    suspend fun recommend(@Body request: RecommendationRequest): List<RecommendationResponse>

    @GET("health")
    suspend fun checkHealth(): HealthResponse

    @GET("metrics")
    suspend fun getMetrics(): MetricsResponse

    @POST("train")
    suspend fun triggerTraining(): TrainResponse

    @POST("feedback")
    suspend fun sendFeedback(@Body feedback: FeedbackRequest)
}
