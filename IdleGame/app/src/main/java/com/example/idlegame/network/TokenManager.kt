package com.example.idlegame.network

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object TokenManager {
    private const val PREF_NAME = "auth_prefs_v2"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"

    private fun prefs(context: Context) = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Exception) {
        // 暗号化ストレージの初期化失敗時（端末互換性問題等）は平文フォールバック
        context.getSharedPreferences("auth_prefs_fallback", Context.MODE_PRIVATE)
    }

    fun saveAuth(context: Context, token: String, userId: Long, username: String) {
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .apply()
    }

    fun getToken(context: Context): String? =
        prefs(context).getString(KEY_TOKEN, null)

    fun getUsername(context: Context): String? =
        prefs(context).getString(KEY_USERNAME, null)

    fun isLoggedIn(context: Context): Boolean = getToken(context) != null

    fun clearAuth(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
