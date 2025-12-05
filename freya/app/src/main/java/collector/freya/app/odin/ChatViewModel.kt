package collector.freya.app.odin

import androidx.lifecycle.ViewModel
import collector.freya.app.odin.models.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ChatScreenUIState(val messages: List<ChatMessage> = emptyList())

@HiltViewModel
class ChatViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow<ChatScreenUIState>(ChatScreenUIState())
    val uiState = _uiState.asStateFlow()

}