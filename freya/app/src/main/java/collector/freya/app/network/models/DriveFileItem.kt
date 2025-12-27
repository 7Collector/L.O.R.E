package collector.freya.app.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DriveFileItem(
    val name: String,
    @SerialName("is_file") val isFile: Boolean,
    val size: Long,
    val modified: Double,
)