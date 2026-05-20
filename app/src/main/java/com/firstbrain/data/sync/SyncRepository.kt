package com.firstbrain.data.sync

import android.util.Log
import com.firstbrain.data.auth.AuthRepository
import com.firstbrain.data.auth.AuthState
import com.firstbrain.data.auth.TokenStore
import com.firstbrain.data.local.FeedbackOutboxDao
import com.firstbrain.data.local.TaskDao
import com.firstbrain.data.remote.FeedbackRequest
import com.firstbrain.data.remote.NeonTasksApi
import com.firstbrain.data.remote.RecommendationApi
import com.firstbrain.data.remote.parseInstant
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
            }.onFailure { Log.w(TAG, "syncTasks failed", it) }
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
        }.onFailure { Log.w(TAG, "drainFeedback failed", it) }
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
        Log.d(TAG, "Pulling for $userId: since=$since, found ${incoming.size} tasks")
        if (incoming.isEmpty()) return

        var highWater = since
        for (dto in incoming) {
            val local = taskDao.byId(dto.id)
            val incomingUpdatedAt = runCatching { parseInstant(dto.updated_at) }.getOrNull()

            // Skip if the local copy is strictly fresher — protects unflushed edits.
            // If local is null (first sync), we always want it.
            if (local != null && incomingUpdatedAt != null && local.updatedAt >= incomingUpdatedAt) {
                Log.v(TAG, "Skipping stale task ${dto.id}: local=${local.updatedAt}, remote=${dto.updated_at}")
                continue
            }

            Log.d(TAG, "Upserting task ${dto.id} (deleted=${dto.deleted})")
            // Server doesn't store SHAP explanations; preserve the local cache so
            // the Insights / TaskAdapter rationale survives a pull.
            taskDao.upsert(dto.toEntity().copy(explanationJson = local?.explanationJson))
            if (highWater == null || dto.updated_at > highWater) {
                highWater = dto.updated_at
            }
        }
        if (highWater != null) syncState.setLastSyncedAt(userId, highWater)
    }

    private companion object {
        const val TAG = "SyncRepository"
    }
}
