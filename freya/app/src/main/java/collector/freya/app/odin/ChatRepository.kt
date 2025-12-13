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

enum class ConnectionState {
    CONNECTED,
    CONNECTING,
    DISCONNECTED
}

data class SocketState(
    val chatId: String?,
    val connectionState: ConnectionState = ConnectionState.CONNECTING,
    val isResponding: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
)

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class ChatRepository(
    private var id: String?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val okHttpClient = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val webSocketListener = WebSocketListener(scope)

    private val _state = MutableStateFlow(SocketState(chatId = id))
    val state: StateFlow<SocketState> = _state

    private val replyBuffers = mutableMapOf<String, StringBuilder>()

    init {
        connectWebSocket()

        scope.launch {
            webSocketListener.socketEventChannel.consumeAsFlow().collect { event ->
                if (id == null) {
                    id = event.chatId
                    _state.update { it.copy(chatId = event.chatId) }
                }

                when (event.type) {
                    SocketType.CONNECTED -> {
                        _state.update { it.copy(connectionState = ConnectionState.CONNECTED) }
                    }

                    SocketType.MESSAGE -> {
                        val buffer =
                            replyBuffers.getOrPut(event.messageId.toString()) { StringBuilder() }
                        buffer.append(event.text)
                        _state.update { s ->
                            s.copy(
                                messages = s.messages.map { msg ->
                                    if (msg.id == event.messageId) msg.copy(reply = buffer.toString())
                                    else msg
                                }
                            )
                        }
                    }

                    SocketType.UPDATE -> {
                        _state.update { s ->
                            s.copy(
                                isResponding = event.text != "end",
                                messages = s.messages.map { msg ->
                                    if (msg.id == event.messageId) {
                                        if (event.text == "end") msg.copy(state = MessageState.SUCCESS)
                                        else msg.copy(updates = msg.updates + event.text.toString())
                                    } else msg
                                }
                            )
                        }
                    }

                    SocketType.EXCEPTION -> {
                        _state.update { s ->
                            s.copy(
                                isResponding = false,
                                connectionState = ConnectionState.DISCONNECTED,
                                messages = s.messages.map { msg ->
                                    val last = s.messages.last()
                                    when {
                                        msg.id == event.messageId -> msg.copy(state = MessageState.FAILED)
                                        msg == last && last.state != MessageState.SUCCESS  -> msg.copy(state = MessageState.FAILED)
                                        else -> msg
                                    }
                                }
                            )
                        }
                    }

                    SocketType.CLOSED -> {
                        _state.update { s ->
                            s.copy(
                                isResponding = false,
                                connectionState = ConnectionState.DISCONNECTED,
                                messages = s.messages.map { msg ->
                                    val last = s.messages.last()
                                    if (msg == last && last.state != MessageState.SUCCESS) {
                                        msg.copy(state = MessageState.FAILED)
                                    } else msg
                                })
                        }
                    }
                }
            }
        }
    }

    private fun connectWebSocket(chatId: String? = null) {
        webSocket = okHttpClient.newWebSocket(createRequest(chatId), webSocketListener)
        _state.update { it.copy(connectionState = ConnectionState.CONNECTING) }
    }

    fun sendMessage(message: ChatMessage) {
        _state.update { it.copy(isResponding = true, messages = it.messages + message) }
        if (webSocket == null || state.value.connectionState != ConnectionState.CONNECTED) connectWebSocket()
        webSocket!!.send(Json.encodeToString(message))
    }

    fun stopGeneration() {
        _state.update { it.copy(isResponding = false) }
    }

    private fun createRequest(chatId: String? = null, apiKey: String = "koala"): Request {
        val websocketURL =
            "wss://freyaslittlehelper.loca.lt/odin/chat/${chatId}?x-api-key=${apiKey}"

        return Request.Builder().url(websocketURL).build()
    }
}
