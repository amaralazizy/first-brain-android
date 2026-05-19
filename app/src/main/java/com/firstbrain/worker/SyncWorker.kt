package com.firstbrain.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.firstbrain.data.sync.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Reconciles local Room with Neon and drains the feedback outbox.
 *
 *  - One-shot: kicked off after every mutation (push hot edits ASAP).
 *  - Periodic: every 15 min to pull other-device edits.
 * Both have a `CONNECTED` constraint — WorkManager retries automatically
 * when the network returns, so we never need a manual polling loop.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = syncRepository.syncAll()
        return if (result.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_ONESHOT = "sync_oneshot"
        private const val UNIQUE_PERIODIC = "sync_periodic"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueueNow(workManager: WorkManager) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniqueWork(UNIQUE_ONESHOT, ExistingWorkPolicy.REPLACE, req)
        }

        fun schedulePeriodic(workManager: WorkManager) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }

        fun cancelAll(workManager: WorkManager) {
            workManager.cancelUniqueWork(UNIQUE_ONESHOT)
            workManager.cancelUniqueWork(UNIQUE_PERIODIC)
        }
    }
}
