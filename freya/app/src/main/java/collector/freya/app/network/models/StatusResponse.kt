package collector.freya.app.network.models

import kotlinx.serialization.Serializable

@Serializable
data class StatusResponse(
    val status: String
)