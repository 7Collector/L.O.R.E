package collector.freya.app.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DriveUploadResponse(
    val status: String,
    @SerialName("saved_as") val savedAs: String,
)