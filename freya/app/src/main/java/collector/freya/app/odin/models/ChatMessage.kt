package collector.freya.app.odin.models

import collector.freya.app.database.chats.models.ChatMessageEntity
import kotlinx.serialization.Serializable

@Serializable
enum class MessageState(val showText: String) {
    PROCESSING("Sending..."),
    SUCCESS(""),
    FAILED("Something went wrong!"),
    NETWORK_ERROR("Network error!")
}

@Serializable
enum class AiModel {
    NORMAL,
    DEEP,
    CLOUD
}

@Serializable
data class ChatMessage(
    val id: String,
    val chatId: String? = null,
    val timestamp: Long,
    val state: MessageState,
    val attachments: List<Attachment> = emptyList(),
    val updates: List<String> = emptyList(),
    val prompt: String,
    val reply: String = "",
    val model: AiModel,
)

fun ChatMessage.toEntity(chatId: String): ChatMessageEntity =
    ChatMessageEntity(
        id = id,
        chatId = chatId,
        timestamp = timestamp,
        prompt = prompt,
        reply = reply,
        state = state,
        model = model
    )