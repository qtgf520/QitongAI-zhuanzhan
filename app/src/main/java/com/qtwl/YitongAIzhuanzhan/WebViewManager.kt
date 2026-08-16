package com.qtwl.YitongAIzhuanzhan

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.qtwl.YitongAIzhuanzhan.GatewayPrefs
import java.util.concurrent.CopyOnWriteArraySet

// Desktop UA prevents several AI sites from forcing reduced mobile pages.
const val USER_AGENT_DESKTOP =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

data class WebViewTab(
    val id: Int,
    var url: String,
    var title: String,
    var platformId: String? = null,
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false,
    var isLoading: Boolean = false,
    var progress: Int = 0,
    var webView: WebView? = null
)

/**
 * Owns every browser tab and WebView used by both the visible browser and the
 * multi-AI pipeline. Pipeline stages receive a dedicated tab per platform, and
 * switching stages also switches the visible browser so WebViews remain active.
 */
object WebViewManager {
    private val tabs = mutableListOf<WebViewTab>()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tabCounter = 0
    private var currentTabIndex = 0

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listeners.forEach { it.invoke() }
        } else {
            mainHandler.post { listeners.forEach { it.invoke() } }
        }
    }

    fun createTab(
        context: Context,
        url: String = "https://www.doubao.com",
        platformId: String? = AiPlatformRegistry.detect(url).id.takeUnless { it == "generic" }
    ): WebViewTab {
        tabCounter++
        val tab = WebViewTab(
            id = tabCounter,
            url = url,
            title = "",
            platformId = platformId
        )
        tabs.add(tab)
        currentTabIndex = tabs.lastIndex
        notifyChanged()
        return tab
    }

    /** Returns an existing tab for a platform or creates and initialises one. */
    fun getOrCreatePlatformTab(context: Context, platform: AiPlatformDefinition): WebViewTab {
        val existingIndex = tabs.indexOfFirst { tab ->
            tab.platformId == platform.id || AiPlatformRegistry.detect(tab.url).id == platform.id
        }
        val tab = if (existingIndex >= 0) {
            tabs[existingIndex].also { it.platformId = platform.id }
        } else {
            createTab(context, platform.url, platform.id)
        }
        val index = tabs.indexOfFirst { it.id == tab.id }
        if (index >= 0) currentTabIndex = index
        initWebView(context, tab.id)
        notifyChanged()
        return tab
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun initWebView(context: Context, tabId: Int, onReply: (String) -> Unit = {}): WebView? {
        val tab = tabs.find { it.id == tabId } ?: return null
        tab.webView?.let { return it }

        val appContext = context.applicationContext
        val wv = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Register one bridge per WebView. Request-scoped replies are routed to
            // the exact automation that armed the JavaScript watcher.
            addJavascriptInterface(object {
                @JavascriptInterface
                fun onReplyForRequest(requestId: String, content: String) {
                    Handler(Looper.getMainLooper()).post {
                        val delivered = ReplyBridge.deliver(requestId, content)
                        if (delivered && content.isNotBlank()) {
                            onReply(content)
                            NotificationHelper.showReply(appContext, tabId.toString(), content)
                        }
                    }
                }

                @JavascriptInterface
                fun onReply(content: String) {
                    Handler(Looper.getMainLooper()).post {
                        if (content.isNotBlank()) {
                            onReply(content)
                            NotificationHelper.showReply(appContext, tabId.toString(), content)
                        }
                    }
                }

                @JavascriptInterface
                fun onStatus(msg: String) {
                    Log.d("QitongCapture", msg)
                    if (!msg.startsWith("CAPTURE_DIAG ")) {
                        Handler(Looper.getMainLooper()).post {
                            NotificationHelper.update(appContext, "綦桐AI转站", msg)
                        }
                    }
                }
            }, "Android")

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportZoom(true)
            settings.allowFileAccess = false
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = USER_AGENT_DESKTOP
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.databaseEnabled = true
            settings.allowContentAccess = true
            settings.textZoom = GatewayPrefs.getTextZoom(appContext)

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            PersistentCookieJar(appContext).restore()

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    tab.isLoading = true
                    tab.progress = 0
                    url?.let {
                        tab.url = it
                        val detected = AiPlatformRegistry.detect(it)
                        if (detected.id != "generic") tab.platformId = detected.id
                    }
                    notifyChanged()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    tab.isLoading = false
                    tab.progress = 100
                    tab.title = view?.title.orEmpty()
                    tab.canGoBack = view?.canGoBack() ?: false
                    tab.canGoForward = view?.canGoForward() ?: false
                    CookieManager.getInstance().flush()
                    PersistentCookieJar(appContext).save()
                    notifyChanged()
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean = false
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    tab.progress = newProgress
                    tab.isLoading = newProgress < 100
                    notifyChanged()
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    tab.title = title.orEmpty()
                    notifyChanged()
                }
            }
        }
        tab.webView = wv
        if (tab.url.isNotBlank()) wv.loadUrl(tab.url)
        notifyChanged()
        return wv
    }

    fun getCurrentTab(): WebViewTab? = tabs.getOrNull(currentTabIndex)
    fun getTab(index: Int): WebViewTab? = tabs.getOrNull(index)
    fun getTabById(tabId: Int): WebViewTab? = tabs.firstOrNull { it.id == tabId }
    fun getTabCount(): Int = tabs.size
    fun getCurrentIndex(): Int = currentTabIndex

    fun switchTab(index: Int): Boolean {
        if (index !in tabs.indices) return false
        currentTabIndex = index
        notifyChanged()
        return true
    }

    fun switchToTabId(tabId: Int): Boolean {
        val index = tabs.indexOfFirst { it.id == tabId }
        return switchTab(index)
    }

    fun addTab(context: Context, url: String = "https://www.google.com"): WebViewTab =
        createTab(context, url)

    fun closeTab(index: Int): Boolean {
        if (tabs.size <= 1 || index !in tabs.indices) return false
        tabs[index].webView?.let { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        tabs.removeAt(index)
        if (index < currentTabIndex) {
            currentTabIndex--
        } else if (currentTabIndex >= tabs.size) {
            currentTabIndex = tabs.lastIndex
        }
        notifyChanged()
        return true
    }

    fun getTabs(): List<WebViewTab> = tabs.toList()

    fun destroyAll() {
        tabs.forEach { tab ->
            tab.webView?.let { webView ->
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            }
        }
        tabs.clear()
        tabCounter = 0
        currentTabIndex = 0
        notifyChanged()
    }
}
