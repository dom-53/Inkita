package net.dom53.inkita.core.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.dom53.inkita.core.auth.AuthManager
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.KavitaApi
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

object KavitaApiFactory {
    @Volatile
    private var authDependencies: Pair<AppPreferences, AuthManager>? = null

    fun installAuthDependencies(
        preferences: AppPreferences,
        authManager: AuthManager,
    ) {
        authDependencies = preferences to authManager
    }

    private fun baseRetrofitBuilder(
        baseUrl: String,
        client: OkHttpClient,
    ): Retrofit.Builder {
        val moshi =
            Moshi
                .Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

        return Retrofit
            .Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
    }

    fun createUnauthenticated(baseUrl: String): KavitaApi {
        val logging =
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(logging)
                .build()

        val retrofit = baseRetrofitBuilder(baseUrl, client).build()
        return retrofit.create(KavitaApi::class.java)
    }

    fun createAuthenticated(
        baseUrl: String,
        token: String,
    ): KavitaApi {
        val authInterceptor =
            Interceptor { chain ->
                val latestToken =
                    authDependencies?.let { (prefs, authManager) ->
                        // Keep token current so requests after a refresh use the updated value.
                        runBlocking { authManager.ensureValidToken() ?: prefs.configFlow.first().token }
                    } ?: token
                val original = chain.request()
                val newRequest =
                    original
                        .newBuilder()
                        .header("Authorization", "Bearer $latestToken")
                        .build()
                chain.proceed(newRequest)
            }

        val logging =
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

        val authenticator =
            authDependencies?.let { (_, authManager) ->
                TokenAuthenticator(authManager)
            }

        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(logging)
                .apply {
                    authenticator?.let { authenticator(it) }
                }.build()

        val retrofit = baseRetrofitBuilder(baseUrl, client).build()
        return retrofit.create(KavitaApi::class.java)
    }

    private class TokenAuthenticator(
        private val authManager: AuthManager,
    ) : Authenticator {
        override fun authenticate(
            route: okhttp3.Route?,
            response: Response,
        ): Request? {
            if (responseCount(response) >= 2) return null

            val token =
                runBlocking {
                    authManager.ensureValidToken(forceRefresh = true)
                } ?: return null
            if (token.isBlank()) return null

            return response.request
                .newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }

        private fun responseCount(response: Response): Int {
            var current: Response? = response
            var count = 1
            while (current?.priorResponse != null) {
                count++
                current = current.priorResponse
            }
            return count
        }
    }
}
