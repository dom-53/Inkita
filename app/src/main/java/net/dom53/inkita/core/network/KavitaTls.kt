package net.dom53.inkita.core.network

import net.dom53.inkita.core.storage.AppConfig
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okio.buffer
import okio.source
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

private const val MAX_CERTIFICATE_FILE_BYTES = 64 * 1024L

internal fun readKavitaCaCertificate(input: InputStream): String =
    input.source().buffer().use { source ->
        require(!source.request(MAX_CERTIFICATE_FILE_BYTES + 1))
        val certificate = readCaCertificate(source.readByteArray())
        Base64.getEncoder().encodeToString(certificate.encoded)
    }

private fun readCaCertificate(bytes: ByteArray): X509Certificate {
    val certificates = CertificateFactory.getInstance("X.509").generateCertificates(bytes.inputStream())
    val certificate = requireNotNull(certificates.singleOrNull() as? X509Certificate)
    require(certificate.basicConstraints >= 0 && certificate.keyUsage?.getOrNull(5) != false)
    certificate.checkValidity()
    return certificate
}

internal fun HttpUrl.isSameOrigin(other: HttpUrl): Boolean = scheme == other.scheme && host == other.host && port == other.port

internal fun OkHttpClient.forKavita(config: AppConfig): Call.Factory {
    val encodedCa = config.caCertificate ?: return this
    val origin = requireNotNull(config.serverUrl.toHttpUrlOrNull())
    require(origin.isHttps)
    val scopedClient by lazy { withKavitaCa(origin, encodedCa) }
    return Call.Factory { request ->
        val client = if (origin.isSameOrigin(request.url)) scopedClient else this
        client.newCall(request)
    }
}

private fun OkHttpClient.withKavitaCa(
    origin: HttpUrl,
    encodedCa: String,
): OkHttpClient {
    val certificate = readCaCertificate(Base64.getDecoder().decode(encodedCa))
    val keyStore =
        KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setCertificateEntry("kavita-ca", certificate)
        }
    val trustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).run {
            init(keyStore)
            trustManagers.filterIsInstance<X509TrustManager>().single()
        }
    val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
    return newBuilder()
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            val redirect = response.header("Location")?.let(request.url::resolve)
            if (response.isRedirect && redirect != null && !origin.isSameOrigin(redirect)) {
                response.close()
                throw SSLPeerUnverifiedException("The Kavita server redirected outside its configured origin")
            }
            response
        }.build()
}
