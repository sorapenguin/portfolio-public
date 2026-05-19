package com.example.idlegame.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class SettingsRepository(private val context: Context) {

    val soundEffects: Flow<Boolean> = context.dataStore.data
        .map { it[AppSettings.KEY_SOUND_EFFECTS] ?: AppSettings.DEFAULT_SOUND_EFFECTS }

    val vibration: Flow<Boolean> = context.dataStore.data
        .map { it[AppSettings.KEY_VIBRATION] ?: AppSettings.DEFAULT_VIBRATION }

    suspend fun setSoundEffects(enabled: Boolean) {
        context.dataStore.edit { it[AppSettings.KEY_SOUND_EFFECTS] = enabled }
    }

    suspend fun setVibration(enabled: Boolean) {
        context.dataStore.edit { it[AppSettings.KEY_VIBRATION] = enabled }
    }
}
