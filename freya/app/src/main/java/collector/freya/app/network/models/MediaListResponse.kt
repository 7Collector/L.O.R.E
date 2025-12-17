package collector.freya.app.network.models

import kotlinx.serialization.Serializable

@Serializable
data class MediaListResponse(
    val page: Int,
    val limit: Int,
    val total: Int,
    val data: List<MediaItem>,
)