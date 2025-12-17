package collector.freya.app.orion.workers

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import collector.freya.app.orion.MediaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    val mediaRepository: MediaRepository,
) : CoroutineWorker(appContext, workerParams) {

    @RequiresApi(Build.VERSION_CODES.Q)
    override suspend fun doWork(): Result {
        Log.i("WORKER", "${javaClass.name} Started!")
        mediaRepository.startQuery()
        return Result.success()
    }

    companion object {
        fun getWorkRequest(): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<SyncWorker>().build()
        }
    }
}