package com.firstbrain.data.repo

import com.firstbrain.data.local.InteractionAction
import com.firstbrain.data.local.InteractionDao
import com.firstbrain.data.local.InteractionEntity
import com.firstbrain.data.local.TaskDao
import com.firstbrain.data.local.TaskEntity
import com.firstbrain.data.local.TaskStatus
import com.firstbrain.data.local.TaskType
import com.firstbrain.data.local.Urgency
import com.firstbrain.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
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
        val id = taskDao.insert(
            TaskEntity(
                title = title,
                description = description,
                urgency = urgency,
                taskType = taskType,
                estimatedEffort = estimatedEffort,
                deadline = deadline,
                hasDeadline = hasDeadline,
            ),
        )
        rescoreAll()
        id
    }

    suspend fun complete(id: Int) = mutate(id, InteractionAction.completed) { task ->
        task.copy(
            status = TaskStatus.completed,
            completedAt = Instant.now(),
            updatedAt = Instant.now(),
            lastInteractedAt = Instant.now(),
        )
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
        task.copy(
            status = TaskStatus.pending,
            completedAt = null,
            updatedAt = Instant.now(),
            lastInteractedAt = Instant.now(),
        )
    }

    suspend fun delete(id: Int) = withContext(io) { taskDao.deleteById(id) }

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
