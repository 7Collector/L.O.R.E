package collector.freya.app.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

@ExperimentalCoroutinesApi
class WebSocketListener(
    private val scope: CoroutineScope,
) : WebSocketListener() {

    val socketEventChannel: Channel<SocketUpdate> = Channel(10)

    override fun onOpen(webSocket: WebSocket, response: Response) {
        // Connected
        scope.launch {
            socketEventChannel.send(SocketUpdate(type = SocketType.CONNECTED))
        }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        scope.launch {
            socketEventChannel.send(Json.decodeFromString<SocketUpdate>(text))
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        scope.launch {
            socketEventChannel.send(SocketUpdate(type = SocketType.CLOSED))
        }
        webSocket.close(1000, null)
        socketEventChannel.close()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        scope.launch {
            socketEventChannel.send(SocketUpdate(type = SocketType.EXCEPTION))
        }
    }
}

@Serializable
data class SocketUpdate(
    val type: SocketType,
    val text: String? = null,
    @SerialName("chat_id") val chatId: String? = null,
    @SerialName("message_id") val messageId: String? = null,
)

enum class SocketType {
    CONNECTED,
    MESSAGE,
    UPDATE,
    EXCEPTION,
    CLOSED
}