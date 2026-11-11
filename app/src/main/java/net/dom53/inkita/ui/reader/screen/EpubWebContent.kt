package net.dom53.inkita.ui.reader.screen

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import net.dom53.inkita.core.network.isSameOrigin
import net.dom53.inkita.core.storage.AppConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Element
import java.io.File
import java.io.IOException
import java.net.URLConnection
import java.util.UUID

private const val READER_ORIGIN = "https://appassets.androidplatform.net"
private const val FONT_PATH = "/_inkita/fonts/"
private const val OFFLINE_PATH = "/_inkita/book/"
private const val CONTENT_POLICY =
    "default-src 'none'; script-src 'none'; style-src 'self' 'unsafe-inline'; " +
        "img-src 'self' data:; font-src 'self' data:; media-src 'self'; base-uri 'self'; form-action 'none'"
internal const val READER_FONT_URL = "$READER_ORIGIN$FONT_PATH"

internal class EpubWebContent(
    context: Context,
    html: String,
    config: AppConfig?,
    fromOffline: Boolean,
    offlineAssetPaths: Set<String>,
) {
    private val server = config?.serverUrl?.toHttpUrlOrNull()?.takeUnless { fromOffline }
    private val kavita = config?.takeIf { server != null }?.let(::KavitaWebResources)
    private val offlinePrefix = "$OFFLINE_PATH${UUID.randomUUID()}/"
    private val paths = offlineAssetPaths.toList()
    private val offlineAssets = paths.withIndex().associate { (index, path) -> path to index.toString() }
    private val assets = WebViewAssetLoader.AssetsPathHandler(context)
    private val loader =
        WebViewAssetLoader
            .Builder()
            .addPathHandler(FONT_PATH) { path ->
                if (path.split('/').none { it == "." || it == ".." } && path.substringAfterLast('.') in setOf("ttf", "otf")) {
                    assets.handle("fonts/$path")
                } else {
                    blockedResource()
                }
            }.addPathHandler(offlinePrefix) { token ->
                val path = paths.getOrNull(token.toIntOrNull() ?: -1)
                path?.let { openImage(context, it) } ?: blockedResource()
            }.build()

    val documentUrl = "$READER_ORIGIN/_inkita/document/${UUID.randomUUID()}"

    @Volatile
    private var documentHtml = ""
    private val document =
        Jsoup.parse(html).apply {
            outputSettings().prettyPrint(false)
            select("script, iframe, frame, frameset, object, embed, base, meta[http-equiv], template, portal").remove()
            select("link").filterNot { it.attr("rel").equals("stylesheet", ignoreCase = true) }.forEach { it.remove() }
            getAllElements().forEach(::prepareElement)
            server?.let { head().prependElement("base").attr("href", "$READER_ORIGIN${it.encodedPath}") }
        }

    fun load(
        view: WebView,
        css: String,
    ) {
        document.head().selectFirst("#reader-style")?.remove()
        document
            .head()
            .appendElement("style")
            .attr("id", "reader-style")
            .appendChild(DataNode(css))
        documentHtml = document.outerHtml()
        view.loadUrl(documentUrl)
    }

    private fun prepareElement(element: Element) {
        element
            .attributes()
            .asList()
            .filter { it.key.startsWith("on", ignoreCase = true) || it.key == "srcdoc" }
            .forEach { element.removeAttr(it.key) }
        for (attribute in listOf("src", "href", "xlink:href", "poster")) {
            if (!element.hasAttr(attribute)) continue
            val value = element.attr(attribute).trim()
            val mapped =
                if (element.normalName() == "a" && attribute == "href" && value.startsWith("#")) {
                    // Resolve anchors against the reader document even when a base URL is set for book resources.
                    documentUrl + value
                } else {
                    resourceUrl(value)
                }
            if (mapped == null) element.removeAttr(attribute) else element.attr(attribute, mapped)
        }
    }

    fun isDocumentUrl(url: String?): Boolean = url?.substringBefore('#') == documentUrl

    private fun resourceUrl(value: String): String? {
        if (value.isEmpty()) return null
        offlineAssets[value]?.let { return "$READER_ORIGIN$offlinePrefix$it" }
        if (value.startsWith("#") || value.startsWith("data:image/", ignoreCase = true)) return value
        val origin = server ?: return null
        val url = origin.resolve(value)?.takeIf { origin.isSameOrigin(it) } ?: return null
        val clean = url.withoutApiKey()
        return "$READER_ORIGIN${clean.encodedPath}" + (clean.encodedQuery?.let { "?$it" } ?: "") +
            (clean.encodedFragment?.let { "#$it" } ?: "")
    }

    fun intercept(request: WebResourceRequest): WebResourceResponse {
        val uri = request.url
        if (request.method != "GET" || uri.scheme != "https" ||
            uri.authority != WebViewAssetLoader.DEFAULT_DOMAIN
        ) {
            return blockedResource()
        }
        if (request.isForMainFrame) {
            if (!isDocumentUrl(uri.toString())) return blockedResource()
            return WebResourceResponse(
                "text/html",
                "UTF-8",
                200,
                "OK",
                mapOf("Content-Security-Policy" to CONTENT_POLICY, "Cache-Control" to "no-store"),
                documentHtml.byteInputStream(),
            )
        }
        loader.shouldInterceptRequest(uri)?.let { return it }
        if (uri.path.orEmpty().startsWith("/_inkita/")) return blockedResource()
        val url =
            server
                ?.newBuilder()
                ?.encodedPath(uri.encodedPath ?: "/")
                ?.encodedQuery(uri.encodedQuery)
                ?.build()
                ?: return blockedResource()
        return kavita?.fetch(url) ?: blockedResource()
    }

    private fun openImage(
        context: Context,
        path: String,
    ): WebResourceResponse? {
        return try {
            val uri = Uri.parse(path)
            val type = if (uri.scheme == "content") context.contentResolver.getType(uri) else null
            val guessed = URLConnection.guessContentTypeFromName(uri.lastPathSegment)
            val input = if (uri.scheme == "content") context.contentResolver.openInputStream(uri) else File(uri.path ?: return null).inputStream()
            input?.let { WebResourceResponse(type?.takeIf { it.startsWith("image/") } ?: guessed ?: "application/octet-stream", null, it) }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
}

internal fun HttpUrl.withoutApiKey(): HttpUrl =
    newBuilder()
        .apply {
            queryParameterNames.filter { it.equals("apiKey", ignoreCase = true) }.forEach { removeAllQueryParameters(it) }
        }.build()

internal fun blockedResource() = WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), null)

@Suppress("DEPRECATION", "SetJavaScriptEnabled")
internal fun WebView.configureEpubWebView(
    content: () -> EpubWebContent,
    pageFinished: (WebView) -> Unit,
) {
    settings.javaScriptEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.allowFileAccessFromFileURLs = false
    settings.allowUniversalAccessFromFileURLs = false
    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
    webViewClient =
        object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest,
            ): WebResourceResponse = content().intercept(request)

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest,
            ): Boolean =
                !(
                    request.isForMainFrame &&
                        request.method == "GET" &&
                        request.url.encodedFragment != null &&
                        content().isDocumentUrl(request.url.toString())
                )

            override fun onPageFinished(
                view: WebView,
                url: String?,
            ) {
                if (url == content().documentUrl) pageFinished(view)
            }
        }
}
