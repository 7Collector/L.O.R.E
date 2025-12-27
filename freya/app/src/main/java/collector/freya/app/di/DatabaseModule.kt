package collector.freya.app.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import collector.freya.app.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton
import java.util.concurrent.Executors

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "app.db"
        ).setQueryCallback(
            { sql, bindArgs ->
                Log.d("ROOM_DB", "SQL: $sql | args: $bindArgs")
            },
            Executors.newSingleThreadExecutor()
        ).build()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    fun provideChatsDao(db: AppDatabase) = db.chatsDao()

    @Provides
    fun provideChatMessagesDao(db: AppDatabase) = db.chatMessagesDao()

    @Provides
    fun provideMediaMessagesDao(db: AppDatabase) = db.mediaDao()

    @Provides
    fun provideDriveDao(db: AppDatabase) = db.driveDao()
}