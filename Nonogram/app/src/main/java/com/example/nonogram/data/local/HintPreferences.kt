package com.example.nonogram.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class HintPreferences(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val HINT_COUNT = intPreferencesKey("hint_count")
    }

    val flow: Flow<Int> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.HINT_COUNT] ?: INITIAL_COUNT }

    suspend fun consume(): Int {
        var result = 0
        dataStore.edit { prefs ->
            val current = prefs[Keys.HINT_COUNT] ?: INITIAL_COUNT
            result = (current - 1).coerceAtLeast(0)
            prefs[Keys.HINT_COUNT] = result
        }
        return result
    }

    suspend fun addHints(amount: Int = REWARD_AMOUNT): Int {
        var result = 0
        dataStore.edit { prefs ->
            val current = prefs[Keys.HINT_COUNT] ?: INITIAL_COUNT
            result = current + amount
            prefs[Keys.HINT_COUNT] = result
        }
        return result
    }

    companion object {
        const val INITIAL_COUNT = 10
        const val REWARD_AMOUNT = 10
    }
}
