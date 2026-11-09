package net.dom53.inkita.data.repository

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.dto.UserDto
import net.dom53.inkita.domain.repository.AuthRepository
import net.dom53.inkita.domain.repository.KavitaConnectionException
import net.dom53.inkita.domain.repository.KavitaConnectionException.Problem
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Response
import java.io.EOFException
import java.io.IOException
import java.security.GeneralSecurityException
import javax.net.ssl.SSLException

class AuthRepositoryImpl internal constructor(
    private val saveConfig: suspend (AppConfig) -> Unit,
) : AuthRepository {
    constructor(appPreferences: AppPreferences) : this(appPreferences::updateKavitaConfig)

    override suspend fun configure(config: AppConfig): AppConfig {
        val accountId = verifyConnection(config)
        val verified = config.copy(userId = config.userId.takeIf { it > 0 } ?: accountId)
        saveConfig(verified)
        return verified
    }

    private suspend fun verifyConnection(config: AppConfig): Int {
        val url =
            config.serverUrl.toHttpUrlOrNull()?.takeIf {
                it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null &&
                    (config.caCertificate == null || it.isHttps)
            } ?: throw KavitaConnectionException(Problem.InvalidUrl)
        if (config.apiKey.isBlank() || config.apiKey.any { it !in ' '..'~' }) {
            throw KavitaConnectionException(Problem.RejectedKey)
        }
        return requireAccountId(requestAccount(config.copy(serverUrl = url.toString())))
    }

    private suspend fun requestAccount(config: AppConfig): Response<UserDto> {
        // Validate the address entered by the user without forwarding its API key to a redirect target.
        return try {
            KavitaApiFactory.createAuthenticated(config, followRedirects = false).getAccount()
        } catch (e: Exception) {
            val problem =
                when (e) {
                    is SSLException, is GeneralSecurityException -> Problem.Tls
                    is JsonDataException, is JsonEncodingException, is EOFException -> Problem.UnexpectedResponse
                    is IOException -> Problem.Unreachable
                    else -> throw e
                }
            throw KavitaConnectionException(problem)
        }
    }

    private fun requireAccountId(response: Response<UserDto>): Int {
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            val status = response.code()
            val problem = if (status == 401 || status == 403) Problem.RejectedKey else Problem.UnexpectedResponse
            throw KavitaConnectionException(problem, status)
        }
        return response.body()?.id?.takeIf { it > 0 } ?: throw KavitaConnectionException(Problem.UnexpectedResponse)
    }
}
