package collector.freya.app.network

import collector.freya.app.network.models.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface MediaApiService {

    @Multipart
    @POST("orion/upload")
    suspend fun uploadMediaFile(
        @Part file: MultipartBody.Part,
        @Query("album_id") albumId: Int? = null
    ): Response<UploadResult>

    @GET("orion/file/{media_id}")
    @Streaming
    suspend fun getFile(@Path("media_id") mediaId: Int): Response<ResponseBody>

    @GET("orion/thumb/{media_id}")
    suspend fun getThumbnail(@Path("media_id") mediaId: Int): Response<ResponseBody>

    @GET("orion/info/{media_id}")
    suspend fun getMediaInfo(@Path("media_id") mediaId: Int): Response<MediaItem>

    @GET("orion/list")
    suspend fun listMedia(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<MediaListResponse>

    @POST("orion/album")
    suspend fun createAlbum(@Query("name") name: String): Response<AlbumCreateResponse>

    @GET("orion/albums")
    suspend fun listAlbums(): Response<List<Album>>

    @POST("orion/album/{album_id}/add")
    suspend fun addToAlbum(
        @Path("album_id") albumId: Int,
        @Query("media_id") mediaId: Int
    ): Response<StatusResponse>

    @GET("orion/album/{album_id}")
    suspend fun getAlbumItems(
        @Path("album_id") albumId: Int,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<MediaListResponse>

    @DELETE("orion/delete/{media_id}")
    suspend fun deleteMedia(@Path("media_id") mediaId: Int): Response<StatusResponse>

    @POST("orion/favorite/{media_id}")
    suspend fun favoriteMedia(@Path("media_id") mediaId: Int): Response<FavoriteResponse>

    @POST("orion/unfavorite/{media_id}")
    suspend fun unfavoriteMedia(@Path("media_id") mediaId: Int): Response<FavoriteResponse>
}