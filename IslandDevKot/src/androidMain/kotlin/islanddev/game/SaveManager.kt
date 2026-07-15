package islanddev.game

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import islanddev.model.SaveData
import kotlinx.coroutines.flow.first

private val Context.islandDevDataStore by preferencesDataStore(name = "island_dev")

class SaveManager(context: Context) : ISaveManager {
    private val dataStore = context.applicationContext.islandDevDataStore

    override suspend fun save(data: SaveData) {
        val timestamped = data.copy(lastSavedSec = System.currentTimeMillis() / 1000)
        dataStore.edit { preferences ->
            preferences[SAVE_KEY] = SaveDataJson.encode(timestamped)
        }
    }

    override suspend fun load(): SaveData {
        val encoded = dataStore.data.first()[SAVE_KEY]
        if (encoded.isNullOrBlank()) return SaveData()

        return runCatching {
            SaveDataJson.decode(encoded)
        }.getOrDefault(SaveData())
    }

    private companion object {
        val SAVE_KEY = stringPreferencesKey("island_dev_save")
    }
}
