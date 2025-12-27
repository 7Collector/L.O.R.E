package collector.freya.app.di

import collector.freya.app.database.PreferencesRepository
import collector.freya.app.network.ChatApiService
import collector.freya.app.network.DriveApiService
import collector.freya.app.network.MediaApiService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    val contentType = "application/json".toMediaType()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder().header("x-api-key", "koala")
                .method(original.method, original.body).build()
            chain.proceed(request)
        }.build()
    }

    @Provides
    fun provideRetrofitInstance(
        client: OkHttpClient,
        preferencesRepository: PreferencesRepository,
    ): Retrofit {
        val baseUrl = runBlocking {
            val address = preferencesRepository.getServerBaseUrl().first()
            val local = address.first().isDigit()
            val scheme = if (local) "http" else "https"
            "$scheme://$address/"
        }
        return Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(Json.asConverterFactory(contentType)).build()
    }

    @Provides
    fun provideChatApiService(retrofit: Retrofit): ChatApiService {
        return retrofit.create(ChatApiService::class.java)
    }

    @Provides
    fun provideMediaApiService(retrofit: Retrofit): MediaApiService {
        return retrofit.create(MediaApiService::class.java)
    }

    @Provides
    fun provideDriveApiService(retrofit: Retrofit): DriveApiService {
        return retrofit.create(DriveApiService::class.java)
    }
}