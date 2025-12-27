package collector.freya.app.network.models

import kotlinx.serialization.Serializable

@Serializable
data class DriveGenericResponse(
    val status: String,
    val createdPath: String? = null,
    val deletedPath: String? = null,
    val oldPath: String? = null,
    val newPath: String? = null,
)