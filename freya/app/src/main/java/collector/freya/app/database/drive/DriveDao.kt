package collector.freya.app.database.drive

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import collector.freya.app.database.drive.models.FileItem

@Dao
interface DriveDao {
    @Query("SELECT COUNT(*) FROM files")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM files WHERE isUploaded = 0")
    suspend fun getUnuploadedCount(): Int

    @Query("SELECT * FROM files WHERE path = :path ORDER BY timestamp DESC")
    fun getPagingSource(path: String = "/"): PagingSource<Int, FileItem>

    @Query("SELECT * FROM files ORDER BY timestamp DESC")
    suspend fun getAll(): List<FileItem>

    @Query("SELECT * FROM files WHERE isFavorite = 1 ORDER BY timestamp DESC")
    suspend fun getAllFavourites(): List<FileItem>

    @Query("SELECT * FROM files WHERE path = :path ORDER BY isFile ASC, timestamp DESC")
    fun getPagingSourceWithFoldersFirst(path: String = "/"): PagingSource<Int, FileItem>

    @Query("""
        SELECT * FROM files WHERE path = :path 
        ORDER BY 
            isFile ASC,
            CASE WHEN :sortOrder = 'NAME_ASC' THEN name END ASC,
            CASE WHEN :sortOrder = 'NAME_DESC' THEN name END DESC,
            CASE WHEN :sortOrder = 'DATE_ASC' THEN timestamp END ASC,
            CASE WHEN :sortOrder = 'DATE_DESC' THEN timestamp END DESC,
            CASE WHEN :sortOrder = 'SIZE_ASC' THEN size END ASC,
            CASE WHEN :sortOrder = 'SIZE_DESC' THEN size END DESC,
            CASE WHEN :sortOrder = 'TYPE_ASC' THEN SUBSTR(name, INSTR(name, '.')) END ASC,
            CASE WHEN :sortOrder = 'TYPE_DESC' THEN SUBSTR(name, INSTR(name, '.')) END DESC
    """)
    fun getPagingSourceSorted(path: String, sortOrder: String): PagingSource<Int, FileItem>

    @Query("SELECT * FROM files ORDER BY isFile ASC, timestamp DESC")
    suspend fun getAllWithFoldersFirst(): List<FileItem>

    @Query("SELECT * FROM files WHERE isUploaded = 0 ORDER BY timestamp DESC")
    suspend fun getAllUnuploaded(): List<FileItem>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun getById(id: String): FileItem

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<FileItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: FileItem)

    @Query("UPDATE files SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE files SET isUploaded = :isUploaded WHERE id = :id")
    suspend fun updateUploadStatus(id: String, isUploaded: Boolean)

    @Query("DELETE FROM files WHERE path = :path AND isUploaded = 1")
    suspend fun deleteUploadedFilesInPath(path: String)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM files WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}