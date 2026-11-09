package net.dom53.inkita.domain.repository

interface AuthRepository {
    suspend fun configure(
        serverUrl: String,
        apiKey: String,
        imageApiKey: String,
        userId: Int = 0,
    )
}

class KavitaConnectionException(
    val problem: Problem,
    val statusCode: Int? = null,
) : Exception() {
    enum class Problem { InvalidUrl, RejectedKey, Unreachable, Tls, UnexpectedResponse }
}
