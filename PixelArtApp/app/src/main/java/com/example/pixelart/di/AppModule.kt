package com.example.pixelart.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.pixelart.data.local.PixelArtDatabase
import com.example.pixelart.data.local.PuzzleDao
import com.example.pixelart.data.local.UserIdManager
import com.example.pixelart.data.remote.PixelArtApiService
import com.example.pixelart.data.remote.RetrofitClient
import com.example.pixelart.data.repository.PixelArtRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.pixelArtDataStore by preferencesDataStore(name = "pixelart_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.pixelArtDataStore

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PixelArtDatabase =
        PixelArtDatabase.getInstance(context)

    @Provides
    @Singleton
    fun providePuzzleDao(database: PixelArtDatabase): PuzzleDao = database.puzzleDao()

    @Provides
    @Singleton
    fun provideApiService(): PixelArtApiService = RetrofitClient.apiService

    @Provides
    @Singleton
    fun provideUserIdManager(dataStore: DataStore<Preferences>): UserIdManager =
        UserIdManager(dataStore)

    @Provides
    @Singleton
    fun provideRepository(
        dao: PuzzleDao,
        api: PixelArtApiService,
        userIdManager: UserIdManager,
    ): PixelArtRepository = PixelArtRepository(dao, api, userIdManager)
}
