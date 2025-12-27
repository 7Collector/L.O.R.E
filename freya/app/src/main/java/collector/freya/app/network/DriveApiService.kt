package collector.freya.app.network

import collector.freya.app.network.models.DriveGenericResponse
import collector.freya.app.network.models.DriveListResponse
import collector.freya.app.network.models.DriveUploadResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface DriveApiService {
    @Streaming
    @GET("mimir/download/{path}")
    suspend fun downloadFile(
        @Path("path", encoded = true) path: String,
    ): Response<ResponseBody>

    @Multipart
    @POST("mimir/upload")
    suspend fun uploadFile(
        @Query("path") path: String = "/",
        @Part file: MultipartBody.Part,
    ): Response<DriveUploadResponse>

    @GET("mimir/list")
    suspend fun listFiles(
        @Query("path") path: String = "/",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): Response<DriveListResponse>

    @POST("mimir/create_folder")
    suspend fun createFolder(
        @Query("path") path: String = "/",
        @Query("name") folderName: String,
    ): Response<DriveGenericResponse>

    @DELETE("mimir/delete")
    suspend fun deleteItem(
        @Query("path") path: String,
    ): Response<DriveGenericResponse>

    @PUT("mimir/rename")
    suspend fun renameItem(
        @Query("path") path: String,
        @Query("name") newName: String,
    ): Response<DriveGenericResponse>
}