package collector.freya.app.mimir.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import collector.freya.app.R
import collector.freya.app.mimir.DriveRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DriveUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    val driveRepository: DriveRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val uriString = inputData.getString("uri") ?: return Result.failure()
        val serverPath = inputData.getString("serverPath") ?: "/"

        Log.i("DriveUploadWorker", "Starting upload of $uriString to $serverPath")
        setProgress(workDataOf("progress" to 0, "status" to "Starting..."))

        try {
            setProgress(workDataOf("progress" to 30, "status" to "Uploading..."))
            val success = driveRepository.uploadFile(Uri.parse(uriString), serverPath)
            if (success) {
                setProgress(workDataOf("progress" to 100, "status" to "Completed"))
                return Result.success()
            }
        } catch (e: Exception) {
            Log.e("DriveUploadWorker", "Error uploading file", e)
        }

        setProgress(workDataOf("progress" to 0, "status" to "Failed"))
        return Result.failure()
    }
}
