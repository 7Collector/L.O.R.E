package collector.freya.app.mimir

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.paging.Pager
import androidx.paging.PagingConfig
import collector.freya.app.database.drive.DriveDao
import collector.freya.app.database.drive.models.FileItem
import collector.freya.app.network.DriveApiService
import collector.freya.app.network.models.DriveGenericResponse
import collector.freya.app.network.models.UriRequestBody
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject

data class LocalFileMetadata(
    val name: String,
    val size: Long,
    val mimeType: String,
    val lastModified: Long,
)

class DriveRepository @Inject constructor(
    @ApplicationContext val context: Context,
    val driveDao: DriveDao,
    val driveApi: DriveApiService,
) {

    fun files(path: String) = Pager(
        config = PagingConfig(
            pageSize = 50, prefetchDistance = 10, enablePlaceholders = false
        ), pagingSourceFactory = { driveDao.getPagingSourceWithFoldersFirst(path) }).flow

    suspend fun setFavorite(id: String, isFav: Boolean) {
        driveDao.updateFavorite(id, isFav)
    }

    suspend fun setUploaded(id: String) {
        driveDao.updateUploadStatus(id, true)
    }

    suspend fun uploadFile(
        fileUri: Uri,
        serverPath: String = "/",
    ): Boolean = withContext(Dispatchers.IO) {

        val uniqueId = UUID.randomUUID().toString()
        val metadata = getFileMetadataFromUri(fileUri)

        val fileItem = FileItem(
            id = uniqueId,
            parentId = serverPath,
            path = serverPath,
            uri = fileUri.toString(),
            name = metadata.name,
            size = metadata.size.toInt(),
            timestamp = metadata.lastModified,
            isFile = true,
            isFavorite = false,
            isUploaded = false
        )

        driveDao.insert(fileItem)

        val requestBody = UriRequestBody(context.contentResolver, fileUri)
        val filePart = MultipartBody.Part.createFormData("file", metadata.name, requestBody)

        try {
            val response = driveApi.uploadFile(path = serverPath, file = filePart)
            if (response.isSuccessful) {
                setUploaded(uniqueId)
                Log.d("DriveRepo", "Upload success: ${response.body()?.savedAs}")
                true
            } else {
                Log.e("DriveRepo", "Upload failed: ${response.code()}")
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun createFolder(parentPath: String, folderName: String): Boolean {
        val id = UUID.randomUUID().toString()
        val item = FileItem(
            id = id,
            path = parentPath,
            parentId = parentPath,
            uri = parentPath,
            name = folderName,
            size = 0,
            timestamp = System.currentTimeMillis(),
            isFile = false,
            isFavorite = false,
            isUploaded = false,
        )
        driveDao.insert(item)
        val success = withContext(Dispatchers.IO) {
            performGenericAction { driveApi.createFolder(parentPath, folderName) }
        }
        if (success) setUploaded(id)
        return success
    }

    suspend fun deleteItem(path: String): Boolean = withContext(Dispatchers.IO) {
        performGenericAction { driveApi.deleteItem(path) }
    }

    suspend fun renameItem(path: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        performGenericAction { driveApi.renameItem(path, newName) }
    }

    private suspend fun performGenericAction(
        apiCall: suspend () -> Response<DriveGenericResponse>,
    ): Boolean {
        return try {
            val response = apiCall()
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun saveFileToDisk(body: ResponseBody, file: File): Boolean {
        return try {
            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            try {
                inputStream = body.byteStream()
                outputStream = FileOutputStream(file)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
                true
            } catch (e: Exception) {
                Log.e("DriveRepo", "Error saving file", e)
                false
            } finally {
                inputStream?.close()
                outputStream?.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getFileMetadataFromUri(uri: Uri): LocalFileMetadata {
        var name = "upload_${System.currentTimeMillis()}.bin"
        var size = 0L
        var lastModified = System.currentTimeMillis()
        var mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

        if (uri.scheme == "content") {
            val projection = arrayOf(
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
            )

            try {
                val cursor: Cursor? =
                    context.contentResolver.query(uri, projection, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) name = it.getString(nameIndex)

                        val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1) size = it.getLong(sizeIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (uri.scheme == "file") {
            val path = uri.path
            if (path != null) {
                val file = File(path)
                name = file.name
                size = file.length()

                val extension = MimeTypeMap.getFileExtensionFromUrl(path)
                val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                if (type != null) mimeType = type
            }
        }

        return LocalFileMetadata(name, size, mimeType, lastModified)
    }
}