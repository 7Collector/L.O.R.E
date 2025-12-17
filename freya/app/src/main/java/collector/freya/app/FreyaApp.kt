package collector.freya.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import collector.freya.app.orion.workers.SyncWorker
import collector.freya.app.orion.workers.UploadWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FreyaApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        WorkManager.getInstance(this)
            .enqueueUniqueWork(
                "sync_media_worker",
                ExistingWorkPolicy.KEEP,
                SyncWorker.getWorkRequest()
            )

        WorkManager.getInstance(this)
            .enqueueUniqueWork(
                "upload_media_worker",
                ExistingWorkPolicy.KEEP,
                UploadWorker.getWorkRequest()
            )
    }

    private fun createNotificationChannel() {
        val name = "Upload Service"
        val descriptionText = "Notifications for background media sync"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel("SYNC_CHANNEL_ID", name, importance).apply {
            description = descriptionText
        }
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
