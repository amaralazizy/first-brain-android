package com.firstbrain

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.firstbrain.worker.DailyDigestWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FirstBrainApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        DailyDigestWorker.schedule(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Channel for specific task reminders
            val reminderName = "Task Reminders"
            val reminderDesc = "Notifications for upcoming task deadlines"
            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                reminderName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = reminderDesc }
            notificationManager.createNotificationChannel(reminderChannel)

            // Channel for the daily summary
            val digestName = "Daily Digest"
            val digestDesc = "Daily summary of your top tasks"
            val digestChannel = NotificationChannel(
                CHANNEL_DIGEST,
                digestName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = digestDesc }
            notificationManager.createNotificationChannel(digestChannel)
        }
    }

    companion object {
        const val CHANNEL_REMINDERS = "task_reminders_channel"
        const val CHANNEL_DIGEST = "daily_digest_channel"
    }
}
