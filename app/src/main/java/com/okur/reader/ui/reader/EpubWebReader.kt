package com.okur.reader.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.okur.reader.data.PageMode
import com.okur.reader.data.ReaderPrefs
import com.okur.reader.data.ReaderTheme
import com.okur.reader.format.ReflowableBook
import com.okur.reader.format.prepareChapterHtml
import com.okur.reader.reader.ReaderCss
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream

internal const val BOOK_HOST = "okur.book"

/** Compose tarafından WebView içindeki okuma motoruna komut göndermek için. */
class WebReaderController {
    internal var webView: WebView? = null

    private fun js(code: String) {
        webView?.evaluateJavascript("window.Okur && $code", null)
    }

    private fun eval(code: String, callback: (String) -> Unit) {
        val wv = webView ?: return callback("")
        wv.evaluateJavascript("window.Okur ? $code : ''") { raw ->
            val value = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
            callback(value.orEmpty())
        }
    }

    fun next() = js("Okur.next()")
    fun prev() = js("Okur.prev()")
    fun setTheme(css: String) = js("Okur.setTheme(${JSONObject.quote(css)})")
    fun setConfig(json: String) = js("Okur.setConfig(${JSONObject.quote(json)})")
    fun goToAnchor(id: String) = js("Okur.goToAnchor(${JSONObject.quote(id)})")
    fun ensureVisible(snippet: String) = js("Okur.ensureVisible(${JSONObject.quote(snippet)})")
    fun clearSearch() = js("Okur.clearSearch()")
    fun textFromHere(callback: (String) -> Unit) = eval("Okur.textFromHere(200000)", callback)
    fun visibleSnippet(callback: (String) -> Unit) = eval("Okur.visibleSnippet()", callback)
}

private class ReaderCallbacks(
    val onPage: (Int, Int) -> Unit,
    val onScroll: (Float) -> Unit,
    val onTap: () -> Unit,
    val onNext: () -> Unit,
    val onPrev: () -> Unit,
    val onLink: (Uri) -> Unit,
)

/** JS'nin konfigürasyonu senkron okuyabilmesi için (JavaBridge iş parçacığından erişilir). */
private class ConfigHolder {
    @Volatile
    var json: String = "{}"
}

private class Bridge(
    private val config: ConfigHolder,
    private val callbacks: () -> ReaderCallbacks,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun getConfig(): String = config.json

    @JavascriptInterface
    fun onReady(page: Int, count: Int) {
        main.post { callbacks().onPage(page, count) }
    }

    @JavascriptInterface
    fun onPage(page: Int, count: Int) {
        main.post { callbacks().onPage(page, count) }
    }

    @JavascriptInterface
    fun onProgress(fraction: Double) {
        main.post { callbacks().onScroll(fraction.toFloat()) }
    }

    @JavascriptInterface
    fun onTap(zone: String) {
        main.post { callbacks().onTap() }
    }

    @JavascriptInterface
    fun next() {
        main.post { callbacks().onNext() }
    }

    @JavascriptInterface
    fun prev() {
        main.post { callbacks().onPrev() }
    }

    @JavascriptInterface
    fun onLink(url: String) {
        main.post { callbacks().onLink(Uri.parse(url)) }
    }
}

private class BookWebClient(
    private val context: Context,
    private val book: ReflowableBook,
    private val onLink: (Uri) -> Unit,
) : WebViewClient() {

    private fun empty(status: Int = 404) =
        WebResourceResponse("text/plain", "utf-8", status, if (status == 404) "Not Found" else "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        // Gizlilik ve çevrimdışı çalışma için dış ağ istekleri engellenir
        if (url.host != BOOK_HOST) return empty(403)
        val path = url.path?.trimStart('/') ?: return empty()
        if (path == "__okur/reader.js") {
            return WebResourceResponse("application/javascript", "utf-8", context.assets.open("reader/reader.js"))
        }
        val res = runCatching { book.resource(path) }.getOrNull() ?: return empty()
        if (res.mime == "text/html") {
            val html = prepareChapterHtml(String(res.bytes, Charsets.UTF_8), "https://$BOOK_HOST/__okur/reader.js")
            return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
        }
        val encoding = if (res.mime.startsWith("text/") || res.mime.endsWith("xml")) "utf-8" else null
        return WebResourceResponse(res.mime, encoding, ByteArrayInputStream(res.bytes))
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        onLink(request.url)
        return true
    }
}

fun chapterUrl(path: String): String =
    Uri.Builder().scheme("https").authority(BOOK_HOST).path("/$path").build().toString()

/** Kitap içi bağlantı ise (bölüm yolu, çapa) döndürür. */
fun internalLinkTarget(uri: Uri): Pair<String, String?>? =
    if (uri.host == BOOK_HOST) (uri.path?.trimStart('/') ?: "") to uri.fragment else null

private fun layoutJson(prefs: ReaderPrefs): JSONObject = JSONObject().apply {
    put("mode", if (prefs.pageMode == PageMode.PAGED) "paged" else "scroll")
    put("margin", prefs.margin)
    put("vmargin", 30)
    put("tapZones", prefs.tapZones)
    put("dual", prefs.dualPage)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EpubWebReader(
    book: ReflowableBook,
    request: ChapterRequest?,
    prefs: ReaderPrefs,
    theme: ReaderTheme,
    controller: WebReaderController,
    onPage: (Int, Int) -> Unit,
    onScroll: (Float) -> Unit,
    onTap: () -> Unit,
    onNextChapter: () -> Unit,
    onPrevChapter: () -> Unit,
    onLink: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val css = remember(prefs, theme) { ReaderCss.build(prefs, theme) }
    val config = remember { ConfigHolder() }
    val callbacks by rememberUpdatedState(ReaderCallbacks(onPage, onScroll, onTap, onNextChapter, onPrevChapter, onLink))

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.textZoom = 100
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = false
                settings.mediaPlaybackRequiresUserGesture = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isHapticFeedbackEnabled = false
                setBackgroundColor(theme.background.toArgb())
                addJavascriptInterface(Bridge(config) { callbacks }, "OkurBridge")
                webViewClient = BookWebClient(ctx.applicationContext, book) { uri -> callbacks.onLink(uri) }
                controller.webView = this
            }
        },
        update = { it.setBackgroundColor(theme.background.toArgb()) },
        onRelease = { wv ->
            if (controller.webView === wv) controller.webView = null
            wv.stopLoading()
            wv.destroy()
        },
    )

    // Bölüm yükleme: yeni istek geldiğinde konfigürasyonu hazırla ve sayfayı yükle
    LaunchedEffect(request?.token) {
        val req = request ?: return@LaunchedEffect
        val chapter = book.chapters.getOrNull(req.index) ?: return@LaunchedEffect
        config.json = layoutJson(prefs).apply {
            put("css", css)
            put("fraction", req.fraction.toDouble())
            put("atEnd", req.atEnd)
            req.anchor?.let { put("anchor", it) }
            book.language?.let { put("lang", it) }
            put("hasPrev", req.index > 0)
            put("hasNext", req.index < book.chapters.lastIndex)
            req.search?.let { put("search", JSONObject().put("query", it.query).put("nth", it.nth)) }
        }.toString()
        controller.webView?.loadUrl(chapterUrl(chapter.path))
    }

    // Tema/tipografi değişince sayfayı yeniden yüklemeden uygula
    LaunchedEffect(css) {
        config.json = runCatching { JSONObject(config.json).put("css", css).toString() }.getOrDefault(config.json)
        controller.setTheme(css)
    }

    // Yerleşim değişiklikleri (sayfa modu, kenar boşluğu, çift sütun)
    LaunchedEffect(prefs.pageMode, prefs.margin, prefs.dualPage, prefs.tapZones) {
        val layout = layoutJson(prefs)
        config.json = runCatching {
            val merged = JSONObject(config.json)
            layout.keys().forEach { k -> merged.put(k, layout.get(k)) }
            merged.toString()
        }.getOrDefault(config.json)
        controller.setConfig(layout.toString())
    }
}
