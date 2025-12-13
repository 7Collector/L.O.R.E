package collector.freya.app.database.chats.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import collector.freya.app.odin.models.ChatMessage

@Entity(tableName = "chats")
data class Chat(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long
)
