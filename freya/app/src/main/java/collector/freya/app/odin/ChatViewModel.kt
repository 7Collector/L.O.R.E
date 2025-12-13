package collector.freya.app.odin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import collector.freya.app.odin.models.AiModel
import collector.freya.app.odin.models.Attachment
import collector.freya.app.odin.models.ChatMessage
import collector.freya.app.odin.models.MessageState
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
    val connectionState: ConnectionState = ConnectionState.CONNECTING,
    val isResponding: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val attachedElements: List<Attachment> = emptyList(),
    val selectedModel: AiModel = AiModel.NORMAL,
    val webSearchEnabled: Boolean = true,
    val fileAccess: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
) : ViewModel() {
    private val chatRepository = ChatRepository("")

    private val _uiState = MutableStateFlow(ChatScreenUIState())
    val uiState = _uiState.asStateFlow()

    private var chatId: String? = null

    private val _events = MutableSharedFlow<UIEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            chatRepository.state.collect { state ->
                _uiState.update {
                    chatId = state.chatId
                    it.copy(
                        messages = state.messages,
                        connectionState = state.connectionState,
                        isResponding = state.isResponding
                    )
                }
            }
        }
    }

    // Input Related Functions
    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun removeAttachment(attachment: Attachment) {
        _uiState.update { state -> state.copy(attachedElements = state.attachedElements.filter { it != attachment }) }
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

    // Options Bottom Sheet Functions
    fun toggleBottomSheet() {
        _uiState.update { it.copy(isOptionsBottomSheetOpen = !it.isOptionsBottomSheetOpen) }
    }

    fun onModelSelected(model: AiModel) {
        _uiState.update { it.copy(selectedModel = model) }
    }

    fun onWebSearchModified(enabled: Boolean) {
        _uiState.update { it.copy(webSearchEnabled = enabled) }
    }

    fun onFileAccessModified(enabled: Boolean) {
        _uiState.update { it.copy(fileAccess = enabled) }
    }

    // Send Message Function
    fun sendMessage() {
        // Create Message
        val message = ChatMessage(
            prompt = uiState.value.inputText,
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            timestamp = System.currentTimeMillis(),
            state = MessageState.PROCESSING,
            attachments = uiState.value.attachedElements,
            model = uiState.value.selectedModel,
        )

        // Clear
        clearInputText()

        // Send Message
        chatRepository.sendMessage(message)
    }

    fun stopGeneration() {
        chatRepository.stopGeneration()
    }
}