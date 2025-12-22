package net.dom53.inkita.core.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import net.dom53.inkita.core.network.NetworkLoggingInterceptor
import net.dom53.inkita.data.api.KavitaApi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

object KavitaApiFactory {
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
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(NetworkLoggingInterceptor)
                .callTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

        val retrofit = baseRetrofitBuilder(baseUrl, client).build()
        return retrofit.create(KavitaApi::class.java)
    }

    fun createAuthenticated(
        baseUrl: String,
        apiKey: String,
    ): KavitaApi {
        val apiKeyInterceptor =
            Interceptor { chain ->
                val original = chain.request()
                val newRequest =
                    original
                        .newBuilder()
                        .header("x-api-key", apiKey)
                        .build()
                chain.proceed(newRequest)
            }

        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(apiKeyInterceptor)
                .addInterceptor(NetworkLoggingInterceptor)
                .callTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

        val retrofit = baseRetrofitBuilder(baseUrl, client).build()
        return retrofit.create(KavitaApi::class.java)
    }
}
