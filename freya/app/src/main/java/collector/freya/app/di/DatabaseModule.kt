package collector.freya.app.di

import android.content.Context
import androidx.room.Room
import collector.freya.app.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton

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
        ).build()

    @Provides
    fun provideChatsDao(db: AppDatabase) = db.chatsDao()

    @Provides
    fun provideChatMessagesDao(db: AppDatabase) = db.chatMessagesDao()
}