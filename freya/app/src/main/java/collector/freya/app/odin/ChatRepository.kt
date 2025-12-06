package collector.freya.app.odin

import collector.freya.app.network.ChatApiService
import collector.freya.app.odin.models.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

class ChatRepository @Inject constructor(private val apiService: ChatApiService) {

    val messages = MutableStateFlow<List<ChatMessage>>(emptyList())

    fun sendMessage(message: ChatMessage) {
        // TODO
        print(message)
    }
}