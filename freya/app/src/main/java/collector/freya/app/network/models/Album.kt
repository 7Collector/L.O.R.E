package collector.freya.app.network.models

import kotlinx.serialization.Serializable

@Serializable
data class Album(
    val id: Int,
    val name: String
)

@Serializable
data class AlbumCreateResponse(
    val status: String,
    val album: String
)