package collector.freya.app.orion.workers

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import collector.freya.app.R
import collector.freya.app.database.media.MediaDao
import collector.freya.app.orion.MediaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    val mediaDao: MediaDao,
    val mediaRepository: MediaRepository,
) : CoroutineWorker(appContext, workerParams) {

    private val notificationId = 102
    private val channelId = "SYNC_CHANNEL_ID"

    override suspend fun doWork(): Result {
        Log.i("WORKER", "${javaClass.name} Started!")

        showNotification(0, 0, true)

        val totalCount = mediaDao.getUnuploadedCount()

        cancelNotification()

        if (totalCount == 0) return Result.success()

        var currentProgress = 0
        val unUploadedItems = mediaDao.getAllUnuploaded()

        unUploadedItems.forEach {
            try {
                val success =
                    mediaRepository.uploadFile(it.id, fileUri = it.uri.toUri(), name = it.name)
                if (success) {
                    currentProgress++
                    showNotification(totalCount, currentProgress, false)
                }
            } catch (e: Exception) {
                Log.e("UploadWorker", "Failed to upload item ${it.id}", e)
            }
        }

        cancelNotification()

        return Result.success()
    }

    private fun showNotification(
        max: Int,
        progress: Int,
        indeterminate: Boolean,
    ) {
        val title = "Uploading Media"
        val content = if (indeterminate) {
            "Preparing upload..."
        } else {
            "Uploaded $progress of $max items"
        }

        val notification =
            NotificationCompat.Builder(applicationContext, channelId).setContentTitle(title)
                .setContentText(content).setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true).setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(max, progress, indeterminate).build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.e("UploadWorker", "Missing notification permission", e)
        }
    }

    private fun cancelNotification() {
        NotificationManagerCompat.from(applicationContext).cancel(notificationId)
    }

    companion object {
        fun getWorkRequest(): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<UploadWorker>().build()
        }
    }
}