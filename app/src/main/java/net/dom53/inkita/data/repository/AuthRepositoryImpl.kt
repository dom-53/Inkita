package net.dom53.inkita.data.repository

import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.dto.LoginRequest
import net.dom53.inkita.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val appPreferences: AppPreferences,
) : AuthRepository {
    override suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
        apiKey: String,
    ) {
        val api = KavitaApiFactory.createUnauthenticated(serverUrl)

        val response =
            api.login(
                LoginRequest(
                    username = username,
                    password = password,
                    apiKey = apiKey,
                ),
            )

        appPreferences.updateAfterLogin(
            serverUrl = serverUrl,
            username = response.username,
            apiKey = apiKey, // the one you logged in with
            token = response.token,
            refreshToken = response.refreshToken,
            userId = response.id,
        )
    }
}
