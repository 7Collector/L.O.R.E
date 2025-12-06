package collector.freya.app.odin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import collector.freya.app.odin.models.AiModel
import collector.freya.app.odin.models.Attachment
import collector.freya.app.odin.models.ChatMessage
import collector.freya.app.odin.models.MessageState
import collector.freya.app.odin.models.Sender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface UIEvent {
    object ScrollToBottom : UIEvent
    data class Toast(val message: String) : UIEvent
}

data class ChatScreenUIState(
    val isOptionsBottomSheetOpen: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val attachedElements: List<Attachment> = emptyList(),
    val selectedModel: AiModel = AiModel.NORMAL,
    val webSearchEnabled: Boolean = true,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatScreenUIState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UIEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            chatRepository.messages.collect { list ->
                _uiState.update { it.copy(messages = list) }
            }
        }
    }

    // Input Text Functions
    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    private fun clearInputText() {
        _uiState.update { it.copy(inputText = "") }
    }

    // UI Events Helper Function
    private fun scrollToBottom() {
        viewModelScope.launch {
            _events.emit(UIEvent.ScrollToBottom)
        }
    }

    private fun showToast(message: String) {
        viewModelScope.launch {
            _events.emit(UIEvent.Toast(message))
        }
    }

    // Send Message Function
    fun sendMessage() {
        // Create Message
        val message = ChatMessage(
            text = uiState.value.inputText,
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            state = MessageState.PROCESSING,
            sender = Sender.USER,
            attachments = uiState.value.attachedElements,
            model = uiState.value.selectedModel,
        )
        // Send Message
        chatRepository.sendMessage(message)
    }
}