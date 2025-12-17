package collector.freya.app.network.models

import kotlinx.serialization.Serializable

@Serializable
data class UploadResult(
    val id: Int,
    val name: String
)