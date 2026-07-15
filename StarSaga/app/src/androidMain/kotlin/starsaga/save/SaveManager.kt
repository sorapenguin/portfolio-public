package starsaga.save

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import starsaga.data.RpgSaveData

private val Context.starSagaDataStore by preferencesDataStore(name = "starsaga_save")

class SaveManager(context: Context) : ISaveManager {
    private val dataStore = context.applicationContext.starSagaDataStore

    override suspend fun save(data: RpgSaveData): RpgSaveData {
        val timestamped = data.copy(lastSavedSec = System.currentTimeMillis() / 1000)
        dataStore.edit { preferences ->
            preferences[SAVE_KEY] = SaveDataJson.encode(timestamped)
        }
        return timestamped
    }

    override suspend fun load(): RpgSaveData? {
        val encoded = dataStore.data.first()[SAVE_KEY]
        if (encoded.isNullOrBlank()) return null
        return runCatching { SaveDataJson.decode(encoded) }.getOrNull()
    }

    override suspend fun delete() {
        dataStore.edit { preferences ->
            preferences.remove(SAVE_KEY)
        }
    }

    private companion object {
        val SAVE_KEY = stringPreferencesKey("rpg_save_json")
    }
}
