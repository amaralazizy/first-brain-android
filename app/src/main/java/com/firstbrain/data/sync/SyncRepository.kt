package com.firstbrain.data.sync

import com.firstbrain.data.auth.AuthRepository
import com.firstbrain.data.auth.AuthState
import com.firstbrain.data.auth.TokenStore
import com.firstbrain.data.local.FeedbackOutboxDao
import com.firstbrain.data.local.TaskDao
import com.firstbrain.data.remote.FeedbackRequest
import com.firstbrain.data.remote.NeonTasksApi
import com.firstbrain.data.remote.RecommendationApi
import com.firstbrain.data.remote.toEntity
import com.firstbrain.data.remote.toRemoteDto
import com.firstbrain.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates push/pull between Room and the Neon Data API, and drains
 * the feedback outbox to the recommendation server.
 *
 * Push happens before pull so locally-dirty rows reach the server before
 * we ask "what's new?" — otherwise a fresh pull could clobber pending edits.
 * Inbound rows older than the local copy are skipped (last-write-wins by
 * `updated_at`).
 */
@Singleton
class SyncRepository @Inject constructor(
    private val tasksApi: NeonTasksApi,
    private val recommendationApi: RecommendationApi,
    private val taskDao: TaskDao,
    private val feedbackDao: FeedbackOutboxDao,
    private val authRepository: AuthRepository,
    private val tokenStore: TokenStore,
    private val syncState: SyncStateStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val mutex = Mutex()

    suspend fun syncTasks(): Result<Unit> = withContext(io) {
        if (authRepository.state.value !is AuthState.Authenticated) {
            return@withContext Result.success(Unit) // nothing to sync; no retry
        }
        mutex.withLock {
            runCatching {
                push()
                pull()
            }
        }
    }

    suspend fun drainFeedback(): Result<Unit> = withContext(io) {
        runCatching {
            val pending = feedbackDao.all()
            for (event in pending) {
                recommendationApi.sendFeedback(
                    FeedbackRequest(
                        task_id = event.taskId,
                        action = event.action,
                        score = event.score,
                    )
                )
                feedbackDao.delete(event.id)
            }
        }
    }

    /** One-shot full reconcile: tasks + feedback. */
    suspend fun syncAll(): Result<Unit> {
        val tasks = syncTasks()
        val feedback = drainFeedback()
        return if (tasks.isFailure) tasks else feedback
    }

    private suspend fun push() {
        val dirty = taskDao.dirty()
        if (dirty.isEmpty()) return
        val response = tasksApi.upsert(dirty.map { it.toRemoteDto() })
        // Server may rewrite updated_at via the trigger; persist the echoed row,
        // then clear the dirty flag so subsequent pulls don't bounce.
        for (row in response) {
            taskDao.upsert(row.toEntity()) // dirty = false from toEntity()
        }
    }

    private suspend fun pull() {
        val userId = tokenStore.userId ?: return
        val since = syncState.lastSyncedAt(userId)
        val incoming = if (since == null) {
            tasksApi.list()
        } else {
            tasksApi.listSince("gt.$since")
        }
        if (incoming.isEmpty()) return

        var highWater = since
        for (dto in incoming) {
            val local = taskDao.byId(dto.id)
            // Skip if the local copy is at least as fresh — protects unflushed edits.
            if (local != null && local.updatedAt >= java.time.Instant.parse(dto.updated_at)) {
                continue
            }
            taskDao.upsert(dto.toEntity())
            if (highWater == null || dto.updated_at > highWater) {
                highWater = dto.updated_at
            }
        }
        if (highWater != null) syncState.setLastSyncedAt(userId, highWater)
    }
}
