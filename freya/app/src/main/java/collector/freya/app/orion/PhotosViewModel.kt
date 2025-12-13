package collector.freya.app.orion

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import collector.freya.app.orion.models.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class PhotosUIState(
    val photos: Map<String, List<MediaItem>> = emptyMap(),
    val openedPhoto: MediaItem? = null
)

@RequiresApi(Build.VERSION_CODES.Q)
@HiltViewModel
class PhotosViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotosUIState())
    val uiState = _uiState.asStateFlow()

    init {
        val currentInstant = Instant.now()
        val now = currentInstant.atZone(ZoneId.systemDefault())
        viewModelScope.launch {
            mediaRepository.startQuery()
        }
        viewModelScope.launch {
            mediaRepository.media.collect { media ->
                _uiState.update {
                    it.copy(photos = media.groupBy { photo ->
                        val instant = Instant.ofEpochMilli(photo.timestamp)
                        val dateTime = instant.atZone(ZoneId.systemDefault())
                        val pattern = if (dateTime.year == now.year) "dd MMM" else "dd MMM yyyy"
                        DateTimeFormatter.ofPattern(pattern).format(dateTime)
                    })
                }
            }
        }
    }

    fun openPhoto(photo: MediaItem) {
        _uiState.update { it.copy(openedPhoto = photo) }
    }

    fun closePhoto() {
        _uiState.update { it.copy(openedPhoto = null) }
    }
}