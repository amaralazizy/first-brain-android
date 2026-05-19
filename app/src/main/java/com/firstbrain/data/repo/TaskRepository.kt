package com.firstbrain.data.repo

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
import com.firstbrain.data.remote.FeedbackRequest
import com.firstbrain.data.remote.RecommendationApi
import com.firstbrain.data.remote.RecommendationRequest
import com.firstbrain.data.remote.TaskFeatureWrapper
import com.firstbrain.data.remote.TaskFeatures
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
    private val recommendationApi: RecommendationApi,
    private val workManager: WorkManager,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observeTasks(): Flow<List<TaskEntity>> = taskDao.observeAll()
    fun observePicks(): Flow<List<TaskEntity>> = taskDao.observePicks()
    fun observeHistory(): Flow<List<TaskEntity>> = taskDao.observeHistory()
    fun observeTask(id: Int): Flow<TaskEntity?> = taskDao.observeById(id)
    fun observeInteractions(taskId: Int) = interactionDao.observeForTask(taskId)

    suspend fun createTask(
        title: String,
        description: String?,
        urgency: Urgency,
        taskType: TaskType,
        estimatedEffort: Int,
        deadline: Instant?,
        hasDeadline: Boolean,
    ): Long = withContext(io) {
        val task = TaskEntity(
            title = title,
            description = description,
            urgency = urgency,
            taskType = taskType,
            estimatedEffort = estimatedEffort,
            deadline = deadline,
            hasDeadline = hasDeadline,
        )
        val id = taskDao.insert(task)
        val savedTask = task.copy(id = id.toInt())
        scheduleReminders(savedTask)
        rescoreAll()
        id
    }

    private fun scheduleReminders(task: TaskEntity) {
        val deadline = task.deadline ?: return
        val now = Instant.now()

        // 1. One day before
        val oneDayBefore = deadline.minus(Duration.ofDays(1))
        if (oneDayBefore.isAfter(now)) {
            val delay = Duration.between(now, oneDayBefore).toMillis()
            enqueueReminder(task.id, delay, ReminderWorker.TYPE_ONE_DAY)
        }

        // 2. Effort + 1 hour before
        // estimatedEffort is in hours
        val finalCallTime = deadline.minus(Duration.ofHours(task.estimatedEffort.toLong() + 1))
        if (finalCallTime.isAfter(now)) {
            val delay = Duration.between(now, finalCallTime).toMillis()
            enqueueReminder(task.id, delay, ReminderWorker.TYPE_FINAL_CALL)
        }

        // 3. At deadline
        if (deadline.isAfter(now)) {
            val delay = Duration.between(now, deadline).toMillis()
            enqueueReminder(task.id, delay, ReminderWorker.TYPE_DEADLINE)
        }
    }

    private fun enqueueReminder(taskId: Int, delayMs: Long, type: String) {
        val workData = Data.Builder()
            .putInt(ReminderWorker.KEY_TASK_ID, taskId)
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

    suspend fun complete(id: Int) = mutate(id, InteractionAction.completed) { task ->
        cancelReminders(id)
        task.copy(
            status = TaskStatus.completed,
            completedAt = Instant.now(),
            updatedAt = Instant.now(),
            lastInteractedAt = Instant.now(),
        )
    }

    private fun cancelReminders(taskId: Int) {
        workManager.cancelAllWorkByTag("task_reminder_$taskId")
    }

    suspend fun skip(id: Int) = mutate(id, InteractionAction.skipped) { task ->
        task.copy(
            status = TaskStatus.skipped,
            skipCount = task.skipCount + 1,
            updatedAt = Instant.now(),
            lastInteractedAt = Instant.now(),
        )
    }

    suspend fun reopen(id: Int) = mutate(id, InteractionAction.reopened) { task ->
        val updated = task.copy(
            status = TaskStatus.pending,
            completedAt = null,
            updatedAt = Instant.now(),
            lastInteractedAt = Instant.now(),
        )
        scheduleReminders(updated)
        updated
    }

    suspend fun delete(id: Int) = withContext(io) {
        cancelReminders(id)
        taskDao.deleteById(id)
    }

    suspend fun logViewed(id: Int) = withContext(io) {
        interactionDao.insert(
            InteractionEntity(taskId = id, action = InteractionAction.viewed, occurredAt = Instant.now()),
        )
    }

    /** Recompute the heuristic score for every pending task using the AI Model API. */
    suspend fun rescoreAll() = withContext(io) {
        val pendingTasks = taskDao.pending()
        if (pendingTasks.isEmpty()) return@withContext

        val now = Instant.now()
        val zdt = ZonedDateTime.ofInstant(now, ZoneId.systemDefault())
        
        // Prepare features for all tasks
        val wrappers = pendingTasks.map { task ->
            TaskFeatureWrapper(
                id = task.id.toString(),
                features = mapToFeatures(task, zdt, pendingTasks.size)
            )
        }

        try {
            val scores = recommendationApi.recommend(RecommendationRequest(wrappers))
            
            // Update local DB with new scores
            scores.forEach { response ->
                val taskId = response.id.toIntOrNull() ?: return@forEach
                taskDao.updateScore(taskId, response.score)
                // Note: In a full implementation, you'd also save response.explanations 
                // to a local table to power the Insights screen.
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to local heuristic if API fails
            pendingTasks.forEach { task ->
                taskDao.updateScore(task.id, RankingHeuristic.score(task, now))
            }
        }
    }

    private fun mapToFeatures(task: TaskEntity, now: ZonedDateTime, totalPending: Int): TaskFeatures {
        val deadlineDist = if (task.hasDeadline && task.deadline != null) {
            val days = Duration.between(now.toInstant(), task.deadline).toHours() / 24.0
            max(0.0, days)
        } else {
            7.0 // Default far distance
        }

        val hour = now.hour
        
        return TaskFeatures(
            priority = when(task.urgency) {
                Urgency.Low -> 1
                Urgency.Medium -> 2
                Urgency.High -> 3
                Urgency.Critical -> 4
            },
            estimated_duration = task.estimatedEffort * 60, // Convert hours to minutes
            is_recurring = 0, // Not tracked in current schema
            deadline_dist = deadlineDist,
            energy_required = 3, // Default medium
            is_work = if (task.taskType == TaskType.work) 1 else 0,
            is_personal = if (task.taskType == TaskType.personal) 1 else 0,
            is_health = if (task.taskType == TaskType.health) 1 else 0,
            is_other = if (task.taskType == TaskType.other || task.taskType == TaskType.learning) 1 else 0,
            day_of_week = now.dayOfWeek.value,
            hour_of_day = hour,
            current_energy = 3, // Default
            work_load = totalPending,
            recent_completion_rate = 0.7, // Placeholder for interaction analytics
            is_morning = if (hour in 5..11) 1 else 0,
            is_afternoon = if (hour in 12..16) 1 else 0,
            is_evening = if (hour in 17..21) 1 else 0
        )
    }

    private suspend fun mutate(
        id: Int,
        action: InteractionAction,
        transform: (TaskEntity) -> TaskEntity,
    ) = withContext(io) {
        val current = taskDao.byId(id) ?: return@withContext
        val updated = transform(current)
        taskDao.update(updated)
        interactionDao.insert(
            InteractionEntity(taskId = id, action = action, occurredAt = Instant.now()),
        )

        // Send feedback to AI model
        try {
            val now = ZonedDateTime.now()
            val pendingCount = taskDao.pending().size
            recommendationApi.sendFeedback(
                FeedbackRequest(
                    task_id = id.toString(),
                    action = action.name,
                    timestamp = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    features = mapToFeatures(current, now, pendingCount)
                )
            )
        } catch (e: Exception) {
            e.printStackTrace() // Silent failure for feedback
        }

        rescoreAll()
    }
}
