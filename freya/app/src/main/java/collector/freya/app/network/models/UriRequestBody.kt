package collector.freya.app.network.models

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException

class UriRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
) : RequestBody() {

    override fun contentType(): MediaType? {
        val type = contentResolver.getType(uri) ?: "application/octet-stream"
        return type.toMediaTypeOrNull()
    }

    override fun contentLength(): Long {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex != -1) cursor.getLong(sizeIndex) else -1L
                } else -1L
            } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    override fun writeTo(sink: BufferedSink) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.source().use { source ->
                    sink.writeAll(source)
                }
            } ?: throw IOException("Could not open stream for $uri")
        } catch (e: Exception) {
            throw IOException(e)
        }
    }
}