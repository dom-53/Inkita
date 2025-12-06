package net.dom53.inkita.domain.repository

interface AuthRepository {
    suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
        apiKey: String,
    )
}
