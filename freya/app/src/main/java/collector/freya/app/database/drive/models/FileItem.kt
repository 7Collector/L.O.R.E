package collector.freya.app.database.drive.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "files")
data class FileItem(
    @PrimaryKey val id: String,
    val parentId: String?,
    val path: String,
    val uri: String,
    val name: String,
    val size: Int,
    val timestamp: Long,
    val isFile: Boolean,
    val isFavorite: Boolean,
    val isUploaded: Boolean,
)