package com.firstbrain.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.firstbrain.FirstBrainApp
import com.firstbrain.R
import com.firstbrain.data.local.TaskDao
import com.firstbrain.data.local.TaskStatus
import com.firstbrain.ui.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskDao: TaskDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val reminderType = inputData.getString(KEY_REMINDER_TYPE) ?: ""

        val task = taskDao.byId(taskId) ?: return Result.success()

        // Only notify if the task is still pending
        if (task.status != TaskStatus.pending) return Result.success()

        val title = when (reminderType) {
            TYPE_ONE_DAY -> "Deadline Tomorrow"
            TYPE_FINAL_CALL -> "Final Call"
            TYPE_DEADLINE -> "Deadline Reached"
            else -> "Task Reminder"
        }

        val content = when (reminderType) {
            TYPE_ONE_DAY -> "Task '${task.title}' is due in 24 hours."
            TYPE_FINAL_CALL -> "It's time to start working on '${task.title}' to finish on time!"
            TYPE_DEADLINE -> "Task '${task.title}' is due now!"
            else -> "Reminder for task: ${task.title}"
        }

        showNotification(taskId.hashCode(), title, content)

        return Result.success()
    }

    private fun showNotification(id: Int, title: String, content: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, FirstBrainApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        with(NotificationManagerCompat.from(applicationContext)) {
            // Permission check is usually handled by the activity/fragment before scheduling
            // but for simplicity and robustness we can check here or just try if we have permission.
            try {
                notify(id, builder.build())
            } catch (e: SecurityException) {
                // Handle missing permission if necessary
            }
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_REMINDER_TYPE = "reminder_type"
        const val TYPE_ONE_DAY = "one_day"
        const val TYPE_FINAL_CALL = "final_call"
        const val TYPE_DEADLINE = "at_deadline"
    }
}
