package collector.freya.app.database.chats.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import collector.freya.app.odin.models.AiModel
import collector.freya.app.odin.models.ChatMessage
import collector.freya.app.odin.models.MessageState

@Entity(
    tableName = "messages",
    indices = [Index("chatId")]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val timestamp: Long,
    val prompt: String,
    val reply: String,
    val state: MessageState,
    val model: AiModel,
)

fun ChatMessageEntity.toDomain(): ChatMessage =
    ChatMessage(
        id = id,
        chatId = chatId,
        timestamp = timestamp,
        state = state,
        prompt = prompt,
        reply = reply,
        model = model
    )
