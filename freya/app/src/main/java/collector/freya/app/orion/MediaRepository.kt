package collector.freya.app.orion

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import collector.freya.app.database.PreferencesRepository
import collector.freya.app.database.media.MediaDao
import collector.freya.app.database.media.models.MediaEntity
import collector.freya.app.network.MediaApiService
import collector.freya.app.network.models.UriRequestBody
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class MediaRepository @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    val preferencesRepository: PreferencesRepository,
    private val apiService: MediaApiService,
    val mediaDao: MediaDao,
) {

    val media
        get() = Pager(
            config = PagingConfig(
                pageSize = 50, prefetchDistance = 10, enablePlaceholders = false
            ), pagingSourceFactory = { mediaDao.getPagingSource() }).flow

    suspend fun setFavorite(id: String, isFav: Boolean) {
        mediaDao.updateFavorite(id, isFav)
    }

    suspend fun setUploaded(id: String) {
        mediaDao.updateUploadStatus(id, true)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun startQuery() = withContext(Dispatchers.IO) {
        var lastDateAdded = preferencesRepository.getLastDateAddedSyncedForMediaCollection().first()
        val mediaList = mutableListOf<MediaEntity>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
        )

        val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf(lastDateAdded.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        applicationContext.contentResolver.query(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val size = cursor.getInt(sizeColumn)

                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )

                val dateTaken = cursor.getLong(dateTakenColumn)
                val dateAddedSeconds = cursor.getLong(dateAddedColumn)
                lastDateAdded = dateAddedSeconds
                val createdAt = if (dateTaken > 0) {
                    dateTaken
                } else {
                    dateAddedSeconds * 1000
                }

                /*val thumbnail = try {
                    applicationContext.contentResolver.loadThumbnail(
                        contentUri,
                        Size(320, 320),
                        null
                    )
                } catch (_: Exception) {
                    null
                }*/
                Log.d("ROOM_DB", "Added to local list")
                val zone = ZoneId.systemDefault()
                val dayEpoch = Instant.ofEpochMilli(createdAt)
                    .atZone(zone)
                    .toLocalDate()
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()

                mediaList += MediaEntity(
                    id = id.toString(),
                    uri = contentUri.toString(),
                    name = name,
                    size = size,
                    timestamp = createdAt,
                    dayEpoch = dayEpoch,
                    isFavorite = false,
                    isUploaded = false
                )
            }
        }
        Log.d("ROOM_DB", "Added to database")
        mediaDao.insertAll(mediaList)
        preferencesRepository.updateLastMediaDateAdded(lastDateAdded)
    }

    suspend fun uploadFile(
        id: String,
        fileUri: Uri,
        albumId: Int? = null,
    ): Boolean {

        // val mimeType = applicationContext.contentResolver.getType(fileUri) ?: "application/octet-stream"


        val contentUri = ContentUris.withAppendedId(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, // Handle Videos too here
            id.toLong()
        )

        val requestBody = UriRequestBody(applicationContext.contentResolver, contentUri)

        val filePart = MultipartBody.Part.createFormData("file", "upload_$id.jpg", requestBody)

        try {
            val response = apiService.uploadMediaFile(filePart, albumId)
            if (response.isSuccessful) setUploaded(id)
            return response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}