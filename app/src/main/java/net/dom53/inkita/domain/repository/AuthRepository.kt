package net.dom53.inkita.domain.repository

import net.dom53.inkita.core.storage.AppConfig

interface AuthRepository {
    suspend fun configure(config: AppConfig): AppConfig
}

class KavitaConnectionException(
    val problem: Problem,
    val statusCode: Int? = null,
) : Exception() {
    enum class Problem { InvalidUrl, RejectedKey, Unreachable, Tls, UnexpectedResponse }
}
