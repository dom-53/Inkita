package net.dom53.inkita.core.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Minimal wrapper around EncryptedSharedPreferences for sensitive values (API key).
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

    fun getApiKey(): String = safeGet(KEY_API_KEY)

    fun getImageApiKey(): String = safeGet(KEY_IMAGE_API_KEY)

    fun setApiKey(apiKey: String) {
        prefs
            .edit()
            .putString(KEY_API_KEY, apiKey)
            .apply()
    }

    fun setImageApiKey(apiKey: String) {
        prefs
            .edit()
            .putString(KEY_IMAGE_API_KEY, apiKey)
            .apply()
    }

    fun clearLegacyTokens() {
        prefs
            .edit()
            .remove(KEY_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
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
        private const val KEY_API_KEY = "api_key"
        private const val KEY_IMAGE_API_KEY = "image_api_key"
        private const val KEY_TOKEN = "token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
