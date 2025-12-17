package collector.freya.app.database.media

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import collector.freya.app.database.media.models.MediaEntity

@Dao
interface MediaDao {

    @Query("SELECT COUNT(*) FROM media")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM media WHERE isUploaded = 0")
    suspend fun getUnuploadedCount(): Int

    @Query("SELECT * FROM media ORDER BY dayEpoch DESC, timestamp DESC")
    fun getPagingSource(): PagingSource<Int, MediaEntity>

    @Query("SELECT * FROM media ORDER BY dayEpoch DESC, timestamp DESC")
    suspend fun getAll(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE isUploaded = 0 ORDER BY dayEpoch DESC, timestamp DESC")
    suspend fun getAllUnuploaded(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getById(id: String): MediaEntity

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(medias: List<MediaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MediaEntity)

    @Query("UPDATE media SET isFavorite = :isFavorite WHERE id = :mediaId")
    suspend fun updateFavorite(mediaId: String, isFavorite: Boolean)

    @Query("UPDATE media SET isUploaded = :isUploaded WHERE id = :mediaId")
    suspend fun updateUploadStatus(mediaId: String, isUploaded: Boolean)
}