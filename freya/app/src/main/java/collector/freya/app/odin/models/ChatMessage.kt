package collector.freya.app.odin.models

enum class Sender {
    AI,
    USER,
    SYSTEM
}

enum class MessageState(val showText: String) {
    PROCESSING("Sending..."),
    SUCCESS(""),
    FAILED("Something went wrong!"),
    NETWORK_ERROR("Network error!")
}

enum class AiModel {
    NORMAL,
    DEEP,
    CLOUD
}

data class ChatMessage(
    val id: String,
    val timestamp: Long,
    val state: MessageState,
    val sender: Sender,
    val attachments: List<Attachment> = emptyList(),
    val text: String,
    val model: AiModel
)