package com.example.nonogram.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

class LoginBonusPreferences(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val LAST_BONUS_DATE = stringPreferencesKey("login_bonus_last_date")
    }

    /** 今日まだボーナスを受け取っていなければ true を返し、受け取り済みとして記録する */
    suspend fun checkAndClaim(today: String): Boolean {
        val prefs = dataStore.data.first()
        if (prefs[Keys.LAST_BONUS_DATE] == today) return false
        dataStore.edit { it[Keys.LAST_BONUS_DATE] = today }
        return true
    }
}
