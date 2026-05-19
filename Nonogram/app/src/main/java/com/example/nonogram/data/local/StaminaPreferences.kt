package com.example.nonogram.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import kotlin.math.min

class StaminaPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        const val MAX = 8
        const val AD_RECOVERY = 6
        private const val RECOVERY_MS = 10 * 60 * 1000L
    }

    private object Keys {
        val CURRENT = intPreferencesKey("stamina_current")
        val LAST_UPDATE = longPreferencesKey("stamina_last_update")
    }

    // 呼び出し時点でのスタミナを計算して返す（リアルタイム更新なし）
    suspend fun get(): Int {
        val prefs = dataStore.data.first()
        val stored = prefs[Keys.CURRENT] ?: MAX
        if (stored >= MAX) return MAX
        val elapsed = System.currentTimeMillis() - (prefs[Keys.LAST_UPDATE] ?: System.currentTimeMillis())
        return min(MAX, stored + (elapsed / RECOVERY_MS).toInt())
    }

    suspend fun minutesToNextRecovery(): Int {
        if (get() >= MAX) return 0
        val prefs = dataStore.data.first()
        val elapsed = System.currentTimeMillis() - (prefs[Keys.LAST_UPDATE] ?: System.currentTimeMillis())
        val remaining = RECOVERY_MS - (elapsed % RECOVERY_MS)
        return ((remaining + 59_000L) / 60_000L).toInt()
    }

    suspend fun consume(): Boolean {
        val current = get()
        if (current <= 0) return false
        dataStore.edit { prefs ->
            prefs[Keys.CURRENT] = current - 1
            prefs[Keys.LAST_UPDATE] = System.currentTimeMillis()
        }
        return true
    }

    suspend fun addFromAd() {
        val current = get()
        dataStore.edit { prefs ->
            prefs[Keys.CURRENT] = min(MAX, current + AD_RECOVERY)
            prefs[Keys.LAST_UPDATE] = System.currentTimeMillis()
        }
    }
}
