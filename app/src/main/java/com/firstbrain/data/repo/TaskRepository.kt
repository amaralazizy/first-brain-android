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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for tasks. Everything is local: Room owns the data,
 * the ViewModels observe it as Flows, and ranking is computed in-process by
 * [RankingHeuristic]. There is intentionally no network or sync layer.
 */
@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val interactionDao: InteractionDao,
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

    /** Recompute the heuristic score for every pending task. */
    suspend fun rescoreAll() = withContext(io) {
        val now = Instant.now()
        taskDao.pending().forEach { task ->
            taskDao.updateScore(task.id, RankingHeuristic.score(task, now))
        }
    }

    private suspend fun mutate(
        id: Int,
        action: InteractionAction,
        transform: (TaskEntity) -> TaskEntity,
    ) = withContext(io) {
        val current = taskDao.byId(id) ?: return@withContext
        taskDao.update(transform(current))
        interactionDao.insert(
            InteractionEntity(taskId = id, action = action, occurredAt = Instant.now()),
        )
        rescoreAll()
    }
}
