package collector.freya.app.database

import androidx.room.Database
import androidx.room.RoomDatabase
import collector.freya.app.database.chats.ChatMessagesDao
import collector.freya.app.database.chats.ChatsDao
import collector.freya.app.database.chats.models.Chat
import collector.freya.app.database.chats.models.ChatMessageEntity

@Database(
    entities = [
        Chat::class,
        ChatMessageEntity::class
    ],
    version = 1
)
abstract class AppDatabase: RoomDatabase() {
    abstract fun chatsDao(): ChatsDao
    abstract fun chatMessagesDao(): ChatMessagesDao
}