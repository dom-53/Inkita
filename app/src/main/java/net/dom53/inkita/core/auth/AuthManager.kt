package net.dom53.inkita.core.auth

import kotlinx.coroutines.flow.first
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.dto.TokenRequestDto
import org.json.JSONObject
import java.util.Base64

/**
 * Handles refresh-token flow and persists updated tokens.
 */
@Suppress("MagicNumber")
class AuthManager(
    private val preferences: AppPreferences,
) {
    private val refreshEarlySeconds = 120L

    /**
     * Attempts to refresh the access token if a refresh token is available.
     *
     * @return true if refresh succeeded and tokens were updated, false otherwise.
     */
    suspend fun maybeRefresh(): Boolean {
        val config = safeConfig() ?: return false

        if (config.serverUrl.isBlank() || config.refreshToken.isBlank() || config.token.isBlank()) return false

        val api = KavitaApiFactory.createUnauthenticated(config.serverUrl)

        return try {
            val response =
                api.refreshToken(
                    TokenRequestDto(
                        token = config.token,
                        refreshToken = config.refreshToken,
                    ),
                )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                preferences.setTokens(
                    token = body.token,
                    refreshToken = body.refreshToken,
                )
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns a valid token, refreshing if expired/expiring-soon or when forced.
     */
    @Suppress("MagicNumber")
    suspend fun ensureValidToken(forceRefresh: Boolean = false): String? {
        val config = safeConfig() ?: return null
        if (config.token.isBlank()) return null
        val canRefresh = config.serverUrl.isNotBlank() && config.refreshToken.isNotBlank()
        if (forceRefresh && !canRefresh) return null

        val nowEpochSeconds = System.currentTimeMillis() / 1000
        val needsRefresh =
            forceRefresh ||
                isExpired(config.token, nowEpochSeconds) ||
                (config.refreshToken.isNotBlank() && isExpiringSoon(config.token, nowEpochSeconds, refreshEarlySeconds))

        if (needsRefresh && canRefresh) {
            val refreshed = maybeRefresh()
            if (!refreshed && forceRefresh) return null
        }

        val updated = safeConfig()
        return updated?.token?.takeIf { it.isNotBlank() }
    }

    suspend fun logout(clearServer: Boolean = false) {
        preferences.clearAuth(clearServer)
    }

    private fun isExpired(
        token: String,
        nowEpochSeconds: Long,
    ): Boolean = decodeExpiryEpochSeconds(token)?.let { it <= nowEpochSeconds } ?: false

    private fun isExpiringSoon(
        token: String,
        nowEpochSeconds: Long,
        leewaySeconds: Long,
    ): Boolean = decodeExpiryEpochSeconds(token)?.let { it <= nowEpochSeconds + leewaySeconds } ?: false

    @Suppress("MagicNumber")
    private fun decodeExpiryEpochSeconds(token: String): Long? {
        val parts = token.split(".")
        if (parts.size < 2) return null
        val payload = parts[1]
        val padded =
            when (payload.length % 4) {
                2 -> "$payload=="
                3 -> "$payload="
                else -> payload
            }
        return try {
            val json =
                String(
                    Base64
                        .getUrlDecoder()
                        .decode(padded),
                    Charsets.UTF_8,
                )
            val obj = JSONObject(json)
            obj.optLong("exp").takeIf { it > 0 }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun safeConfig() =
        try {
            preferences.configFlow.first()
        } catch (_: Exception) {
            null
        }
}
