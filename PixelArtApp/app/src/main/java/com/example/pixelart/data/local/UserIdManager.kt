package com.example.pixelart.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class UserIdManager(private val dataStore: DataStore<Preferences>) {
    private val key = stringPreferencesKey("pixelart_username")

    val userIdFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[key] ?: ""
    }

    suspend fun getOrCreate(): String {
        val current = dataStore.data.first()[key]
        if (!current.isNullOrBlank()) return current
        val generated = generate()
        dataStore.edit { it[key] = generated }
        return generated
    }

    private fun generate(): String {
        val adjectives = listOf("swift", "brave", "dark", "iron", "frost", "storm", "silver", "golden", "cosmic", "lunar")
        val nouns = listOf("nebula", "phoenix", "comet", "aurora", "prism", "echo", "nova", "cipher", "zenith", "vortex")
        return "${adjectives.random()}-${nouns.random()}-${(10..99).random()}"
    }
}
