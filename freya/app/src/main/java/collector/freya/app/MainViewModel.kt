package collector.freya.app

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class MainScreenState {
    ChatScreen,
    DriveScreen,
    PhotosScreen,
    SettingsScreen
}

data class MainScreenUIState(
    val mainScreenState: MainScreenState = MainScreenState.ChatScreen,
    val showAppBar: Boolean = true
)

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(MainScreenUIState())
    val uiState = _uiState.asStateFlow()
    
    fun onScreenSelected(screen: MainScreenState) {
        _uiState.update { it.copy(mainScreenState = screen) }
    }

    fun setAppBarVisibility(visibility: Boolean) {
        _uiState.update { it.copy(showAppBar = visibility) }
    }
}