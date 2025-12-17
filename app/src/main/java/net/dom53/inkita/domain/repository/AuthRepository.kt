package net.dom53.inkita.domain.repository

interface AuthRepository {
    suspend fun configure(
        serverUrl: String,
        apiKey: String,
        userId: Int = 0,
    )
}
