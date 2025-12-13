package collector.freya.app.database.chats

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import collector.freya.app.database.chats.models.ChatMessageEntity

@Dao
interface ChatMessagesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Query("""
    SELECT * FROM messages
    WHERE chatId = :chatId
    ORDER BY timestamp ASC
    """)
    suspend fun getAllForChat(chatId: String): List<ChatMessageEntity>
}