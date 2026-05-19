package com.firstbrain.data.repo

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.firstbrain.data.local.FeedbackOutboxDao
import com.firstbrain.data.local.FeedbackOutboxEntity
import com.firstbrain.data.local.InteractionAction
import com.firstbrain.data.local.InteractionDao
import com.firstbrain.data.local.InteractionEntity
import com.firstbrain.data.local.TaskDao
import com.firstbrain.data.local.TaskEntity
import com.firstbrain.data.local.TaskStatus
import com.firstbrain.data.local.TaskType
import com.firstbrain.data.local.Urgency
import com.firstbrain.di.IoDispatcher
import com.firstbrain.worker.ReminderWorker
import com.firstbrain.worker.SyncWorker
import com.firstbrain.data.remote.RecommendationApi
import com.firstbrain.data.remote.RecommendRequest
import com.firstbrain.data.remote.TaskFeatures
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Single source of truth for tasks. Coordinates local Room data and
 * remote AI model scoring via [RecommendationApi].
 */
@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val interactionDao: InteractionDao,
    private val feedbackOutboxDao: FeedbackOutboxDao,
    private val recommendationApi: RecommendationApi,
    private val workManager: WorkManager,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observeTasks(): Flow<List<TaskEntity>> = taskDao.observeAll()
    fun observePicks(): Flow<List<TaskEntity>> = taskDao.observePicks()
    fun observeHistory(): Flow<List<TaskEntity>> = taskDao.observeHistory()
    fun observeTask(id: String): Flow<TaskEntity?> = taskDao.observeById(id)
    fun observeInteractions(taskId: String) = interactionDao.observeForTask(taskId)

    suspend fun createTask(
        title: String,
        description: String?,
        urgency: Urgency,
        taskType: TaskType,
        estimatedEffort: Int,
        deadline: Instant?,
        hasDeadline: Boolean,
    ): String = withContext(io) {
        val task = TaskEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            urgency = urgency,
            taskType = taskType,
            estimatedEffort = estimatedEffort,
            deadline = deadline,
            hasDeadline = hasDeadline,
        )
        taskDao.insert(task)
        scheduleReminders(task)
        rescoreAll()
        SyncWorker.enqueueNow(workManager)
        task.id
    }

    private fun scheduleReminders(task: TaskEntity) {
        val deadline = task.deadline ?: return
        val now = Instant.now()

        val oneDayBefore = deadline.minus(Duration.ofDays(1))
        if (oneDayBefore.isAfter(now)) {
            val delay = Duration.between(now, oneDayBefore).toMillis()
            enqueueReminder(task.id, delay, ReminderWorker.TYPE_ONE_DAY)
        }

        val finalCallTime = deadline.minus(Duration.ofHours(task.estimatedEffort.toLong() + 1))
        if (finalCallTime.isAfter(now)) {
            val delay = Duration.between(now, finalCallTime).toMillis()
            enqueueReminder(task.id, delay, ReminderWorker.TYPE_FINAL_CALL)
        }

        if (deadline.isAfter(now)) {
            val delay = Duration.between(now, deadline).toMillis()
            enqueueReminder(task.id, delay, ReminderWorker.TYPE_DEADLINE)
        }
    }

    private fun enqueueReminder(taskId: String, delayMs: Long, type: String) {
        val workData = Data.Builder()
            .putString(ReminderWorker.KEY_TASK_ID, taskId)
            .putString(ReminderWorker.KEY_REMINDER_TYPE, type)
            .build()

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workData)
            .addTag("task_reminder_$taskId")
            .build()

        workManager.enqueueUniqueWork(
            "task_reminder_${taskId}_$type",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    suspend fun complete(id: String) = mutate(id, InteractionAction.completed) { task ->
        cancelReminders(id)
        task.copy(
            status = TaskStatus.completed,
            completedAt = Instant.now(),
            updatedAt = Instant.now(),
            lastInteractedAt = Instant.now(),
        )
    }

    private fun cancelReminders(taskId: String) {
        workManager.cancelAllWorkByTag("task_reminder_$taskId")
    }

    suspend fun skip(id: String) = mutate(id, InteractionAction.skipped) { task ->
        task.copy(
            status = TaskStatus.skipped,
            skipCount = task.skipCount + 1,
            updatedAt = Instant.now(),
            lastInteractedAt = Instant.now(),
        )
    }

    suspend fun reopen(id: String) = mutate(id, InteractionAction.reopened) { task ->
        val updated = task.copy(
            status = TaskStatus.pending,
            completedAt = null,
            updatedAt = Instant.now(),
            lastInteractedAt = Instant.now(),
        )
        scheduleReminders(updated)
        updated
    }

    suspend fun delete(id: String) = withContext(io) {
        cancelReminders(id)
        taskDao.softDelete(id, System.currentTimeMillis())
        SyncWorker.enqueueNow(workManager)
    }

    suspend fun logViewed(id: String) = withContext(io) {
        interactionDao.insert(
            InteractionEntity(taskId = id, action = InteractionAction.viewed, occurredAt = Instant.now()),
        )
    }

    /** Recompute the score for every pending task using the AI Model API. */
    suspend fun rescoreAll() = withContext(io) {
        val pendingTasks = taskDao.pending()
        if (pendingTasks.isEmpty()) return@withContext

        val now = Instant.now()
        val zdt = ZonedDateTime.ofInstant(now, ZoneId.systemDefault())

        val features = pendingTasks.map { mapToFeatures(it, zdt) }

        try {
            val scores = recommendationApi.recommend(
                RecommendRequest(tasks = features, top_k = features.size)
            )
            scores.forEach { response ->
                taskDao.updateScore(response.id, response.score)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            pendingTasks.forEach { task ->
                taskDao.updateScore(task.id, RankingHeuristic.score(task, now))
            }
        }
    }

    private fun mapToFeatures(task: TaskEntity, now: ZonedDateTime): TaskFeatures {
        val nowInstant = now.toInstant()
        val daysSinceCreation = Duration.between(task.createdAt, nowInstant).toHours() / 24.0
        val daysSinceLastInteraction = Duration.between(
            task.lastInteractedAt ?: task.createdAt,
            nowInstant
        ).toHours() / 24.0

        val daysUntilDeadline = task.deadline?.let {
            Duration.between(nowInstant, it).toHours() / 24.0
        } ?: 9999.0
        val isOverdue = if (task.hasDeadline && daysUntilDeadline < 0) 1 else 0
        val deadlineProximity = if (task.hasDeadline) {
            1.0 / (1.0 + max(0.0, daysUntilDeadline))
        } else 0.0

        val weekday = now.dayOfWeek.value % 7
        val isWeekend = if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) 1 else 0

        return TaskFeatures(
            id = task.id,
            days_since_creation = daysSinceCreation,
            days_since_last_interaction = daysSinceLastInteraction,
            days_until_deadline = daysUntilDeadline,
            is_overdue = isOverdue,
            deadline_proximity = deadlineProximity,
            skip_count = task.skipCount,
            estimated_effort = task.estimatedEffort,
            has_deadline = if (task.hasDeadline) 1 else 0,
            weekday = weekday,
            is_weekend = isWeekend,
            task_type = mapTaskType(task.taskType),
            urgency = mapUrgency(task.urgency)
        )
    }

    private fun mapTaskType(t: TaskType): String = when (t) {
        TaskType.work -> "Do"
        TaskType.learning -> "Learn"
        TaskType.personal -> "Life"
        TaskType.health -> "Life"
        TaskType.other -> "Idea"
    }

    private fun mapUrgency(u: Urgency): String = when (u) {
        Urgency.Low -> "Low"
        Urgency.Medium -> "Medium"
        Urgency.High, Urgency.Critical -> "High"
    }

    private fun mapFeedbackAction(action: InteractionAction): String? = when (action) {
        InteractionAction.completed -> "complete"
        InteractionAction.skipped, InteractionAction.snoozed -> "skip"
        InteractionAction.viewed, InteractionAction.reopened -> null
    }

    private suspend fun mutate(
        id: String,
        action: InteractionAction,
        transform: (TaskEntity) -> TaskEntity,
    ) = withContext(io) {
        val current = taskDao.byId(id) ?: return@withContext
        val updated = transform(current).copy(dirty = true)
        taskDao.update(updated)
        interactionDao.insert(
            InteractionEntity(taskId = id, action = action, occurredAt = Instant.now()),
        )

        mapFeedbackAction(action)?.let { serverAction ->
            feedbackOutboxDao.insert(
                FeedbackOutboxEntity(
                    taskId = id,
                    action = serverAction,
                    score = current.recScore,
                )
            )
        }

        rescoreAll()
        SyncWorker.enqueueNow(workManager)
    }
}
