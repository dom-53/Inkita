package net.dom53.inkita.data.repository

import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val appPreferences: AppPreferences,
) : AuthRepository {
    override suspend fun configure(
        serverUrl: String,
        apiKey: String,
        userId: Int,
    ) {
        appPreferences.updateKavitaConfig(
            serverUrl = serverUrl,
            apiKey = apiKey,
            userId = userId,
        )
    }
}
