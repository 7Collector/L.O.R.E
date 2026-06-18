package collector.freya.app.orion

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import collector.freya.app.database.media.models.MediaEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject


sealed interface GalleryItem {
    data class Photo(val media: MediaEntity) : GalleryItem
    data class Header(val timestamp: Long) : GalleryItem
}

data class PhotosUIState(
    val openedPhoto: MediaEntity? = null,
    val memories: List<collector.freya.app.network.models.MemoryAlbum> = emptyList(),
    val mapPhotos: List<collector.freya.app.network.models.MapPhoto> = emptyList(),
)

@RequiresApi(Build.VERSION_CODES.Q)
@HiltViewModel
class PhotosViewModel @Inject constructor(
    val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotosUIState())
    val uiState = _uiState.asStateFlow()

    val serverUrl = mediaRepository.preferencesRepository.getServerBaseUrl()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "lore.rakshitrajendra.in")

    val media = mediaRepository.media.map { pagingData ->
        pagingData.map { entity: MediaEntity ->
            GalleryItem.Photo(entity)
        }.insertSeparators { before: GalleryItem.Photo?, after: GalleryItem.Photo? ->
            when {
                after == null -> null
                before == null -> GalleryItem.Header(after.media.timestamp)

                !sameDay(
                    before.media.timestamp, after.media.timestamp
                ) -> GalleryItem.Header(after.media.timestamp)

                else -> null
            }
        }
    }.cachedIn(viewModelScope)

    init {
        loadMemories()
        // Load default map photos for a wide bounds (e.g. whole world)
        loadMapPhotos(-90.0, 90.0, -180.0, 180.0)
    }

    fun loadMemories() {
        viewModelScope.launch {
            val list = mediaRepository.getMemories()
            _uiState.update { it.copy(memories = list) }
        }
    }

    fun loadMapPhotos(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        viewModelScope.launch {
            val list = mediaRepository.getMapPhotos(minLat, maxLat, minLon, maxLon)
            _uiState.update { it.copy(mapPhotos = list) }
        }
    }

    fun openPhoto(photo: MediaEntity) {
        _uiState.update { it.copy(openedPhoto = photo) }
    }

    fun closePhoto() {
        _uiState.update { it.copy(openedPhoto = null) }
    }

    fun setFavorite() {
        val openedPhoto = uiState.value.openedPhoto
        if (openedPhoto == null) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(openedPhoto = openedPhoto.copy(isFavorite = !openedPhoto.isFavorite))
            }
            mediaRepository.setFavorite(openedPhoto.id, openedPhoto.isFavorite)
        }
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val zone = ZoneId.systemDefault()
        val da = Instant.ofEpochMilli(a).atZone(zone).toLocalDate()
        val db = Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
        return da == db
    }
}