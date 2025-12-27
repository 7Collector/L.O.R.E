package collector.freya.app.odin

import android.util.Log
import collector.freya.app.database.PreferencesRepository
import collector.freya.app.database.chats.ChatMessagesDao
import collector.freya.app.database.chats.ChatsDao
import collector.freya.app.database.chats.models.Chat
import collector.freya.app.database.chats.models.toDomain
import collector.freya.app.network.SocketType
import collector.freya.app.network.WebSocketListener
import collector.freya.app.odin.models.ChatMessage
import collector.freya.app.odin.models.MessageState
import collector.freya.app.odin.models.toEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    private var id: String,
    private val chatsDao: ChatsDao,
    private val chatMessagesDao: ChatMessagesDao,
    private val preferencesRepository: PreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val okHttpClient = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val webSocketListener = WebSocketListener(scope)

    private val _state = MutableStateFlow(SocketState(chatId = id))
    val state: StateFlow<SocketState> = _state

    private val replyBuffers = mutableMapOf<String, StringBuilder>()

    init {
        connectWebSocket(id)

        scope.launch {
            _state.update { it.copy(messages = getMessagesForChat(id)) }

            webSocketListener.socketEventChannel.consumeAsFlow().collect { event ->
                if (event.chatId != null) {
                    id = event.chatId
                    Log.d("ChatRepository", "Received Chat ID: ${event.chatId}")
                    _state.update { it.copy(chatId = event.chatId) }
                    saveChat(id)
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
                        if (event.text == "end") saveMessage(_state.value.messages.find { it.id == event.messageId }!!)
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
                                        msg == last && last.state != MessageState.SUCCESS -> msg.copy(
                                            state = MessageState.FAILED
                                        )

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

    fun clear() {
        scope.cancel()
        webSocket?.close(1000, null)
    }

    fun sendMessage(message: ChatMessage) {
        _state.update { it.copy(isResponding = true, messages = it.messages + message) }
        if (webSocket == null || state.value.connectionState != ConnectionState.CONNECTED) connectWebSocket(
            id
        )
        webSocket!!.send(Json.encodeToString(message))
    }

    fun stopGeneration() {
        _state.update { it.copy(isResponding = false) }
    }

    suspend fun saveChat(
        id: String,
        title: String = "Chat: $id",
        timestamp: Long = System.currentTimeMillis(),
    ) {
        Log.d("ChatRepository", "Saving chat chatId=${id}")
        chatsDao.insert(
            Chat(
                id = id,
                title = title,
                timestamp = timestamp
            )
        )
    }

    suspend fun saveMessage(message: ChatMessage) {
        Log.d("ChatRepository", "Saving message id=${message.id}, chatId=${message.chatId}")
        val chatId = message.chatId ?: return
        chatMessagesDao.insert(message.toEntity(chatId))
    }

    suspend fun saveMessages(messages: List<ChatMessage>) {
        chatMessagesDao.insertAll(
            messages.map { it.toEntity(chatId = it.chatId!!) }
        )
    }

    suspend fun getMessagesForChat(chatId: String): List<ChatMessage> {
        return chatMessagesDao.getAllForChat(chatId)
            .map { it.toDomain() }
    }

    private fun connectWebSocket(chatId: String) {
        webSocket = okHttpClient.newWebSocket(createRequest(chatId), webSocketListener)
        _state.update { it.copy(connectionState = ConnectionState.CONNECTING) }
    }

    private fun createRequest(chatId: String): Request {
        val apiKey = runBlocking {
            preferencesRepository.getApiKey().first()
        }
        val baseUrl = runBlocking {
            preferencesRepository.getServerBaseUrl().first()
        }
        val scheme = if (baseUrl.first().isDigit()) "ws" else "wss"
        val websocketURL =
            "$scheme://${baseUrl}/odin/chat/${chatId}?x-api-key=${apiKey}"

        return Request.Builder().url(websocketURL).build()
    }
}
