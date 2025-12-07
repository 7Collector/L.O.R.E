package collector.freya.app.odin

import collector.freya.app.network.ChatApiService
import collector.freya.app.network.WebSocketListener
import collector.freya.app.odin.models.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import javax.inject.Inject

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class ChatRepository @Inject constructor(
    private val apiService: ChatApiService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val okHttpClient = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val webSocketListener = WebSocketListener(scope)

    // Messages Flow
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    init {
        webSocket = okHttpClient.newWebSocket(createRequest(), webSocketListener)

        scope.launch {
            webSocketListener.socketEventChannel.consumeAsFlow().collect {
                // find message with the id and add the text to it
            }
        }
    }

    fun sendMessage(message: ChatMessage) {
        // TODO
        print(message)
    }

    private fun createRequest(): Request {
        val websocketURL = "wss://freyaslittlehelper"

        return Request.Builder()
            .url(websocketURL)
            .build()
    }
}