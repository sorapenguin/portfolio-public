package com.example.nonogram.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AdSkipTicketPreferences(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val TICKET_COUNT     = intPreferencesKey("ad_skip_ticket_count")
        val LAST_AD_TIME     = longPreferencesKey("skip_ticket_last_ad_time")
        val AD_WATCHED_TODAY = intPreferencesKey("skip_ticket_ad_watched_today")
        val AD_LAST_DATE     = stringPreferencesKey("skip_ticket_ad_last_date")
    }

    val flow: Flow<Int> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[Keys.TICKET_COUNT] ?: INITIAL_COUNT }

    suspend fun use(): Boolean {
        var consumed = false
        dataStore.edit { prefs ->
            val current = prefs[Keys.TICKET_COUNT] ?: INITIAL_COUNT
            if (current > 0) {
                prefs[Keys.TICKET_COUNT] = current - 1
                consumed = true
            }
        }
        return consumed
    }

    suspend fun award(amount: Int = 1) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.TICKET_COUNT] ?: INITIAL_COUNT
            prefs[Keys.TICKET_COUNT] = (current + amount).coerceAtMost(MAX_COUNT)
        }
    }

    suspend fun canWatchSkipTicketAd(today: String): Boolean {
        val prefs = dataStore.data.first()
        if ((prefs[Keys.TICKET_COUNT] ?: INITIAL_COUNT) >= MAX_COUNT) return false
        val lastDate = prefs[Keys.AD_LAST_DATE] ?: ""
        val watchedToday = if (lastDate == today) prefs[Keys.AD_WATCHED_TODAY] ?: 0 else 0
        if (watchedToday >= SKIP_AD_DAILY_LIMIT) return false
        val lastAdTime = prefs[Keys.LAST_AD_TIME] ?: 0L
        return System.currentTimeMillis() - lastAdTime >= SKIP_AD_COOLDOWN_MS
    }

    suspend fun skipAdWatchedToday(today: String): Int {
        val prefs = dataStore.data.first()
        val lastDate = prefs[Keys.AD_LAST_DATE] ?: ""
        return if (lastDate == today) prefs[Keys.AD_WATCHED_TODAY] ?: 0 else 0
    }

    suspend fun recordSkipTicketAdWatch(today: String) {
        val prefs = dataStore.data.first()
        val lastDate = prefs[Keys.AD_LAST_DATE] ?: ""
        val watchedToday = if (lastDate == today) prefs[Keys.AD_WATCHED_TODAY] ?: 0 else 0
        dataStore.edit { p ->
            val current = p[Keys.TICKET_COUNT] ?: INITIAL_COUNT
            p[Keys.TICKET_COUNT]     = (current + 1).coerceAtMost(MAX_COUNT)
            p[Keys.LAST_AD_TIME]     = System.currentTimeMillis()
            p[Keys.AD_WATCHED_TODAY] = watchedToday + 1
            p[Keys.AD_LAST_DATE]     = today
        }
    }

    companion object {
        const val INITIAL_COUNT       = 3
        const val MAX_COUNT           = 10
        const val SKIP_AD_DAILY_LIMIT = 3
        const val SKIP_AD_COOLDOWN_MS = 10 * 60 * 1000L
        const val LOGIN_BONUS_AMOUNT  = 3
    }
}
