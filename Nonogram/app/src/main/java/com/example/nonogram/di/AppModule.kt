package com.example.nonogram.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.nonogram.data.local.AdSkipTicketPreferences
import com.example.nonogram.data.local.HintPreferences
import com.example.nonogram.data.local.LoginBonusPreferences
import com.example.nonogram.data.local.NonogramDatabase
import com.example.nonogram.data.local.PuzzleDao
import com.example.nonogram.data.local.StaminaPreferences
import com.example.nonogram.data.remote.NonogramApiService
import com.example.nonogram.data.remote.RetrofitClient
import com.example.nonogram.data.local.BuiltinPuzzleLoader
import com.example.nonogram.data.repository.PuzzleRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import androidx.work.WorkManager
import javax.inject.Singleton

private val Context.nonogramDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "nonogram_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.nonogramDataStore

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NonogramDatabase =
        NonogramDatabase.getInstance(context)

    @Provides
    @Singleton
    fun providePuzzleDao(db: NonogramDatabase): PuzzleDao = db.puzzleDao()

    @Provides
    @Singleton
    fun provideApiService(): NonogramApiService = RetrofitClient.apiService

    @Provides
    @Singleton
    fun provideBuiltinPuzzleLoader(@ApplicationContext context: Context): BuiltinPuzzleLoader =
        BuiltinPuzzleLoader(context)

    @Provides
    @Singleton
    fun provideRepository(
        dao: PuzzleDao,
        api: NonogramApiService,
        builtinLoader: BuiltinPuzzleLoader,
    ): PuzzleRepository = PuzzleRepository(dao, api, builtinLoader)

    @Provides
    @Singleton
    fun provideHintPreferences(dataStore: DataStore<Preferences>): HintPreferences =
        HintPreferences(dataStore)

    @Provides
    @Singleton
    fun provideStaminaPreferences(dataStore: DataStore<Preferences>): StaminaPreferences =
        StaminaPreferences(dataStore)

    @Provides
    @Singleton
    fun provideAdSkipTicketPreferences(dataStore: DataStore<Preferences>): AdSkipTicketPreferences =
        AdSkipTicketPreferences(dataStore)

    @Provides
    @Singleton
    fun provideLoginBonusPreferences(dataStore: DataStore<Preferences>): LoginBonusPreferences =
        LoginBonusPreferences(dataStore)

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
