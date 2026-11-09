package net.dom53.inkita.ui.reader.screen

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import net.dom53.inkita.core.network.forKavita
import net.dom53.inkita.core.network.isSameOrigin
import net.dom53.inkita.core.storage.AppConfig
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.GeneralSecurityException

/** WebView keeps its normal trust policy. Fetch only resources from the opted-in Kavita origin here. */
internal class KavitaWebResources(
    config: AppConfig,
) {
    private val origin = config.serverUrl.toHttpUrl()
    private val calls = OkHttpClient().forKavita(config)

    fun intercept(resource: WebResourceRequest): WebResourceResponse? {
        val url = resource.url.toString().toHttpUrlOrNull() ?: return null
        if (resource.isForMainFrame || resource.method != "GET" || !origin.isSameOrigin(url)) return null

        val request =
            Request
                .Builder()
                .url(url)
                .headers(resource.requestHeaders.toHeaders())
                .build()
        return try {
            calls.newCall(request).execute().asWebResource()
        } catch (_: IOException) {
            failure()
        } catch (_: GeneralSecurityException) {
            failure()
        }
    }

    private fun Response.asWebResource(): WebResourceResponse {
        val content = body
        if (code in 300..399 || content == null) {
            content?.close()
            return failure()
        }
        val type = content.contentType()
        return WebResourceResponse(
            type?.let { "${it.type}/${it.subtype}" },
            type?.charset()?.name(),
            code,
            message.ifBlank { "HTTP $code" },
            headers.toMultimap().mapValues { (_, values) -> values.joinToString(", ") },
            content.byteStream(),
        )
    }

    private fun failure() = WebResourceResponse("text/plain", "UTF-8", 502, "Bad Gateway", emptyMap(), null)
}
