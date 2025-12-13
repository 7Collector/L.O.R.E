package collector.freya.app.orion

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.annotation.RequiresApi
import collector.freya.app.orion.models.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MediaRepository @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
) {

    private val _media = MutableStateFlow<List<MediaItem>>(emptyList())
    val media = _media.asStateFlow()

    init {


    }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun startQuery() = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED
        )
        val selection = ""
        val selectionArgs = arrayOf<String>()
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        applicationContext.contentResolver.query(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateTakenColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateAddedColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val size = cursor.getInt(sizeColumn)

                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val dateTaken = cursor.getLong(dateTakenColumn)
                val dateAddedSeconds = cursor.getLong(dateAddedColumn)

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

                mediaList += MediaItem(contentUri, null, name, size, createdAt)
            }
        }
        _media.update { mediaList }
    }
}