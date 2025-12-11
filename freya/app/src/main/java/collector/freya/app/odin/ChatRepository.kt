package collector.freya.app.odin

import collector.freya.app.network.SocketType
import collector.freya.app.network.WebSocketListener
import collector.freya.app.odin.models.ChatMessage
import collector.freya.app.odin.models.MessageState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class ChatRepository(
    private var id: String?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isConnected = false
    private val okHttpClient = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val webSocketListener = WebSocketListener(scope)

    // Messages Flow
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _chatId = MutableStateFlow<String?>(id)
    val chatId: StateFlow<String?> = _chatId

    init {
        connectWebSocket()

        scope.launch {
            webSocketListener.socketEventChannel.consumeAsFlow().collect { event ->
                // Update chat id
                if (id == null) _chatId.update { event.chatId }

                when (event.type) {
                    SocketType.CONNECTED -> isConnected = true
                    SocketType.MESSAGE -> _messages.update { messages ->
                        messages.map { msg ->
                            if (msg.id == event.messageId) {
                                msg.copy(reply = msg.reply + event.text)
                            } else msg
                        }
                    }

                    SocketType.UPDATE -> _messages.update { messages ->
                        messages.map { msg ->
                            if (msg.id == event.messageId) {
                                msg.copy(updates = (msg.updates + event.text.toString()))
                            } else msg
                        }
                    }

                    SocketType.EXCEPTION -> {
                        isConnected = false
                        _messages.update { messages ->
                            messages.map { msg ->
                                if (msg.id == event.messageId) {
                                    msg.copy(state = MessageState.FAILED)
                                } else msg
                            }
                        }
                    }
                    SocketType.CLOSED -> isConnected = false
                }
            }
        }
    }

    private fun connectWebSocket(chatId: String? = null) {
        webSocket = okHttpClient.newWebSocket(createRequest(chatId), webSocketListener)
    }

    fun sendMessage(message: ChatMessage) {
        _messages.update { it + message }
        if (webSocket == null || !isConnected) connectWebSocket()
        webSocket!!.send(Json.encodeToString(message))
    }

    private fun createRequest(chatId: String? = null, apiKey: String = "koala"): Request {
        val websocketURL =
            "wss://freyaslittlehelper.loca.lt/odin/chat/${chatId}?x-api-key=${apiKey}"

        return Request.Builder()
            .url(websocketURL)
            .build()
    }
}