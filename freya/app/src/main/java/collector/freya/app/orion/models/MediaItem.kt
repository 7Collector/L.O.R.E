package collector.freya.app.orion.models

import android.graphics.Bitmap
import android.media.MediaTimestamp
import android.net.Uri

data class MediaItem(
    val uri: Uri,
    val thumbnail: Bitmap?,
    val name: String,
    val size: Int,
    val timestamp: Long
)
