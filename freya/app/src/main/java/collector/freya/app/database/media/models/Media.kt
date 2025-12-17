package collector.freya.app.database.media.models

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.core.net.toUri
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val name: String,
    val size: Int,
    val timestamp: Long,
    val dayEpoch: Long,
    val isFavorite: Boolean,
    val isUploaded: Boolean
)