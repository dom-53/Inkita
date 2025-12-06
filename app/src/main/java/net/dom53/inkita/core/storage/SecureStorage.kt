package net.dom53.inkita.core.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Minimal wrapper around EncryptedSharedPreferences for sensitive values (token, refresh token, API key).
 * Falls back to empty strings on errors to avoid crashes.
 */
class SecureStorage(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val masterKey =
        MasterKey
            .Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    private val prefs =
        EncryptedSharedPreferences.create(
            appContext,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    fun getToken(): String = safeGet(KEY_TOKEN)

    fun getRefreshToken(): String = safeGet(KEY_REFRESH_TOKEN)

    fun getApiKey(): String = safeGet(KEY_API_KEY)

    fun setTokens(
        token: String,
        refreshToken: String,
    ) {
        prefs
            .edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun setApiKey(apiKey: String) {
        prefs
            .edit()
            .putString(KEY_API_KEY, apiKey)
            .apply()
    }

    fun clear() {
        prefs
            .edit()
            .clear()
            .apply()
    }

    private fun safeGet(key: String): String = runCatching { prefs.getString(key, "") ?: "" }.getOrDefault("")

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_API_KEY = "api_key"
    }
}
