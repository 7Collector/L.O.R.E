package collector.freya.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

enum class MainScreenState {
    ChatScreen,
    DriveScreen,
    PhotosScreen,
    SettingsScreen
}

data class MainScreenUIState(
    val mainScreenState: MainScreenState = MainScreenState.ChatScreen,
    val showAppBar: Boolean = true,
    val currentChatId: String = UUID.randomUUID().toString(),
)

@HiltViewModel
class MainViewModel @Inject constructor(
    repository: MainRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainScreenUIState())
    val uiState = _uiState.asStateFlow()

    val chats = repository.getChats()
        .cachedIn(viewModelScope)

    fun onScreenSelected(screen: MainScreenState) {
        _uiState.update { it.copy(mainScreenState = screen) }
    }

    fun setAppBarVisibility(visibility: Boolean) {
        _uiState.update { it.copy(showAppBar = visibility) }
    }

    fun setCurrentChatId(id: String) {
        _uiState.update { it.copy(currentChatId = id) }
    }
}