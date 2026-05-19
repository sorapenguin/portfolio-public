package com.example.idlegame.settings

import androidx.datastore.preferences.core.booleanPreferencesKey

object AppSettings {
    val KEY_SOUND_EFFECTS = booleanPreferencesKey("sound_effects")
    val KEY_VIBRATION     = booleanPreferencesKey("vibration")

    const val DEFAULT_SOUND_EFFECTS = true
    const val DEFAULT_VIBRATION     = true
}
