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
import javax.net.ssl.SSLException

class AuthRepositoryImpl internal constructor(
    private val saveConfig: suspend (AppConfig) -> Unit,
) : AuthRepository {
    constructor(appPreferences: AppPreferences) : this({ config ->
        appPreferences.updateKavitaConfig(config.serverUrl, config.apiKey, config.imageApiKey, config.userId)
    })

    override suspend fun configure(
        serverUrl: String,
        apiKey: String,
        imageApiKey: String,
        userId: Int,
    ) {
        val accountId = verifyConnection(serverUrl, apiKey)
        saveConfig(AppConfig(serverUrl, apiKey, imageApiKey, userId.takeIf { it > 0 } ?: accountId))
    }

    private suspend fun verifyConnection(
        serverUrl: String,
        apiKey: String,
    ): Int {
        val url =
            serverUrl.toHttpUrlOrNull()?.takeIf {
                it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null
            } ?: throw KavitaConnectionException(Problem.InvalidUrl)
        if (apiKey.isBlank() || apiKey.any { it !in ' '..'~' }) {
            throw KavitaConnectionException(Problem.RejectedKey)
        }
        return requireAccountId(requestAccount(url.toString(), apiKey))
    }

    private suspend fun requestAccount(
        serverUrl: String,
        apiKey: String,
    ): Response<UserDto> {
        // Validate the address entered by the user without forwarding its API key to a redirect target.
        val api = KavitaApiFactory.createAuthenticated(serverUrl, apiKey, followRedirects = false)
        return try {
            api.getAccount()
        } catch (_: JsonDataException) {
            throw KavitaConnectionException(Problem.UnexpectedResponse)
        } catch (e: IOException) {
            val problem =
                when (e) {
                    is SSLException -> Problem.Tls
                    is JsonEncodingException, is EOFException -> Problem.UnexpectedResponse
                    else -> Problem.Unreachable
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
