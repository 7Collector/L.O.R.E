package collector.freya.app.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
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
        webSocket.send("Hi")
        webSocket.send("Hi again")
        webSocket.send("Hi again again")
        webSocket.send("Hi again again again")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        scope.launch {
            socketEventChannel.send(SocketUpdate(text))
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        scope.launch {
            socketEventChannel.send(SocketUpdate(exception = SocketAbortedException()))
        }
        webSocket.close(1000, null)
        socketEventChannel.close()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        scope.launch {
            socketEventChannel.send(SocketUpdate(exception = t))
        }
    }

}

class SocketAbortedException : Exception()

data class SocketUpdate(
    val text: String? = null,
    val byteString: ByteString? = null,
    val exception: Throwable? = null,
)
