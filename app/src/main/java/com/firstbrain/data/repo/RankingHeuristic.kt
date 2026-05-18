package com.firstbrain.data.repo

import com.firstbrain.data.local.TaskEntity
import com.firstbrain.data.local.Urgency
import java.time.Instant
import kotlin.math.max

data class FeatureContribution(val label: String, val value: Double)

/**
 * Local ranking heuristic for "Today's Picks". Decomposable so the Insights
 * screen can show each feature's contribution to the final score (a simple
 * stand-in for the SHAP explanations used in the server-side model).
 */
object RankingHeuristic {

    private const val DAY_MS = 86_400_000.0

    fun score(task: TaskEntity, now: Instant = Instant.now()): Double =
        breakdown(task, now).sumOf { it.value }

    fun breakdown(task: TaskEntity, now: Instant = Instant.now()): List<FeatureContribution> {
        val nowMs = now.toEpochMilli()

        val daysSinceCreation = (nowMs - task.createdAt.toEpochMilli()) / DAY_MS
        val lastSeen = task.lastInteractedAt ?: task.createdAt
        val daysSinceLastInteraction = (nowMs - lastSeen.toEpochMilli()) / DAY_MS

        var deadlineProximity = 0.0
        var isOverdue = 0.0
        if (task.hasDeadline && task.deadline != null) {
            val daysUntil = (task.deadline.toEpochMilli() - nowMs) / DAY_MS
            when {
                daysUntil <= 0 -> { isOverdue = 1.0; deadlineProximity = 1.0 }
                daysUntil <= 7 -> deadlineProximity = 1.0 - daysUntil / 7.0
            }
        }

        val urgencyWeight = when (task.urgency) {
            Urgency.Low -> 0.25
            Urgency.Medium -> 0.50
            Urgency.High -> 0.85
            Urgency.Critical -> 1.0
        }

        val skipPenalty = max(0.0, task.skipCount * 0.05)
        val stalenessBoost = (daysSinceLastInteraction / 14.0).coerceIn(0.0, 0.3)
        val ageDecay = (daysSinceCreation / 60.0).coerceIn(0.0, 0.2)

        return listOf(
            FeatureContribution("urgency", urgencyWeight),
            FeatureContribution("deadline proximity", deadlineProximity * 0.6),
            FeatureContribution("overdue", isOverdue * 0.8),
            FeatureContribution("staleness boost", stalenessBoost),
            FeatureContribution("skip penalty", -skipPenalty),
            FeatureContribution("age decay", -ageDecay),
        )
    }
}
