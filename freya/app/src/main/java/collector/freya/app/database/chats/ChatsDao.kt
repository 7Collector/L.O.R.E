package collector.freya.app.database.chats

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import collector.freya.app.database.chats.models.Chat

@Dao
interface ChatsDao {
    @Query("SELECT * FROM chats")
    fun getAll(): List<Chat>

    @Query("SELECT * FROM chats WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Chat?

    @Query("SELECT * FROM chats ORDER BY timestamp DESC")
    fun pagingSource(): PagingSource<Int, Chat>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chat: Chat): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chats: List<Chat>)

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM chats")
    suspend fun clear()
}