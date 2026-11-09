package net.dom53.inkita.core.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.data.api.KavitaApi
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

object KavitaApiFactory {
    private const val DEFAULT_TIMEOUT_SECONDS = 30L

    private fun baseRetrofitBuilder(
        baseUrl: String,
        client: Call.Factory,
    ): Retrofit.Builder {
        val moshi =
            Moshi
                .Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

        return Retrofit
            .Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .callFactory(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
    }

    fun createUnauthenticated(baseUrl: String): KavitaApi {
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(NetworkLoggingInterceptor)
                .callTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()

        val retrofit = baseRetrofitBuilder(baseUrl, client).build()
        return retrofit.create(KavitaApi::class.java)
    }

    fun createAuthenticated(
        config: AppConfig,
        followRedirects: Boolean = true,
    ): KavitaApi {
        val apiKeyInterceptor =
            Interceptor { chain ->
                val original = chain.request()
                val newRequest =
                    original
                        .newBuilder()
                        .header("x-api-key", config.apiKey)
                        .build()
                chain.proceed(newRequest)
            }

        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(apiKeyInterceptor)
                .addInterceptor(NetworkLoggingInterceptor)
                .followRedirects(followRedirects)
                .callTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()

        val retrofit = baseRetrofitBuilder(config.serverUrl, client.forKavita(config)).build()
        return retrofit.create(KavitaApi::class.java)
    }
}
