package collector.freya.app.database

import androidx.room.Database
import androidx.room.RoomDatabase
import collector.freya.app.database.chats.ChatMessagesDao
import collector.freya.app.database.chats.ChatsDao
import collector.freya.app.database.chats.models.Chat
import collector.freya.app.database.chats.models.ChatMessageEntity
import collector.freya.app.database.media.MediaDao
import collector.freya.app.database.media.models.MediaEntity

@Database(
    entities = [
        Chat::class,
        ChatMessageEntity::class,
        MediaEntity::class
    ],
    version = 5
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatsDao(): ChatsDao
    abstract fun chatMessagesDao(): ChatMessagesDao
    abstract fun mediaDao(): MediaDao
}