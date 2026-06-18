package collector.freya.app.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaItem(
    val id: Int,
    val name: String,
    val mime: String,
    val favorite: Boolean,
    val path: String?,
    @SerialName("is_video") val isVideo: Boolean?,
    val width: Int?,
    val height: Int?,
    val duration: Double?,
    val size: Long?,
    val created: Double?,
    val modified: Double?
)

@Serializable
data class MemoryPhoto(
    val id: Int,
    val name: String,
    val mime: String,
    val favorite: Boolean,
    val date: String
)

@Serializable
data class MemoryAlbum(
    val year: Int,
    val title: String,
    val photos: List<MemoryPhoto>
)

@Serializable
data class MapPhoto(
    val id: Int,
    val name: String,
    val mime: String,
    val favorite: Boolean,
    val latitude: Double,
    val longitude: Double,
    val thumbnail_url: String
)