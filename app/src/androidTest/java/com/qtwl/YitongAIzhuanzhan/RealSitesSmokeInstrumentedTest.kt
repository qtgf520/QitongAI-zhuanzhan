package com.qtwl.YitongAIzhuanzhan

import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Opt-in live smoke test. It intentionally sends real messages to all configured
 * web-AI sites and therefore must never run as part of the default test suite.
 * Run with instrumentation argument: -e realSites true
 */
@RunWith(AndroidJUnit4::class)
class RealSitesSmokeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @After
    fun cleanUp() {
        onMain { WebViewManager.destroyAll() }
    }

    @Test
    fun allSupportedSitesAcceptAndReturnRealUnicodeMessage() {
        val enabled = InstrumentationRegistry.getArguments().getString("realSites") == "true"
        assumeTrue("Real-site smoke tests require -e realSites true", enabled)

        val failures = mutableListOf<String>()
        val results = mutableListOf<String>()
        val requestedPlatform = InstrumentationRegistry.getArguments().getString("platform")?.trim().orEmpty()
        val platforms = if (requestedPlatform.isBlank()) {
            AiPlatformRegistry.supported
        } else {
            AiPlatformRegistry.supported.filter { it.id == requestedPlatform }
        }
        assertTrue("Unknown requested platform: $requestedPlatform", platforms.isNotEmpty())

        platforms.forEach { platform ->
            onMain { WebViewManager.destroyAll() }
            val tabRef = AtomicReference<WebViewTab>()
            val webViewRef = AtomicReference<WebView>()
            onMain {
                val tab = WebViewManager.createTab(
                    context = instrumentation.targetContext,
                    url = platform.url,
                    platformId = platform.id
                )
                val webView = requireNotNull(
                    WebViewManager.initWebView(instrumentation.targetContext, tab.id)
                )
                webView.measure(
                    View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
                )
                webView.layout(0, 0, 1080, 1920)
                tabRef.set(tab)
                webViewRef.set(webView)
            }

            val tab = tabRef.get()
            val webView = webViewRef.get()
            waitForInitialPage(tab, 45_000L)

            val controlsBefore = inspectControlsDom(webView)
            Log.i("QITONG_REAL_CONTROLS", "${platform.id} BEFORE $controlsBefore")

            val stamp = System.currentTimeMillis()
            val sentinel = "SMOKE_OK_${platform.id.uppercase(Locale.ROOT)}_${stamp}_你好🚀"
            val prompt = "这是綦桐AI自动化烟雾测试。请只回复以下标记，不要添加其他文字：$sentinel"
            val result = runAutomation(platform.id, webView, prompt, 90_000L)
            val passed = result.success && result.response.contains(sentinel)
            val diagnostics = if (passed) "" else inspectSentinelDom(webView, sentinel)
            if (!passed) {
                Log.i("QITONG_REAL_CONTROLS", "${platform.id} AFTER ${inspectControlsDom(webView)}")
            }
            val summary = buildString {
                append(platform.id)
                append(" passed=").append(passed)
                append(" stage=").append(result.stage)
                append(" url=").append(tab.url)
                append(" detail=").append(result.detail.take(240))
                append(" response=").append(result.response.replace('\n', ' ').take(300))
                if (diagnostics.isNotBlank()) append(" dom=").append(diagnostics.take(1800))
            }
            results += summary
            Log.i("QITONG_REAL_SMOKE", summary)
            if (!passed) failures += summary
        }

        Log.i("QITONG_REAL_SMOKE", "RESULTS\n" + results.joinToString("\n"))
        assertTrue(
            "One or more real sites failed the smoke test:\n${failures.joinToString("\n")}",
            failures.isEmpty()
        )
    }



    private fun inspectControlsDom(webView: WebView): String {
        val terminal = CountDownLatch(1)
        val captured = AtomicReference("")
        val script = """
(function(){
  try {
    function txt(el){return (el.innerText||el.textContent||'').replace(/\s+/g,' ').trim().slice(0,160);}
    function info(el){
      var raw=(el.innerText||el.textContent||'');
      return {
        tag:el.tagName,id:el.id||'',cls:String(el.className||'').slice(0,180),
        role:el.getAttribute('role')||'',aria:el.getAttribute('aria-label')||'',
        testid:el.getAttribute('data-testid')||'',ce:el.getAttribute('contenteditable')||'',
        disabled:!!el.disabled,text:txt(el),rawLength:Array.from(raw).length,
        codepoints:Array.from(raw).slice(0,180).map(function(c){return c.codePointAt(0).toString(16);}).join(',')
      };
    }
    var inputs=Array.from(document.querySelectorAll('textarea,[contenteditable="true"],[role="textbox"],.ProseMirror')).slice(0,12).map(info);
    var buttons=Array.from(document.querySelectorAll('button,[role="button"]')).filter(function(el){
      var r=el.getBoundingClientRect(),s=getComputedStyle(el);return s.display!=='none'&&s.visibility!=='hidden'&&(r.width>0||r.height>0);
    }).slice(0,28).map(info);
    var dialogs=Array.from(document.querySelectorAll('[role="dialog"],[class*="modal" i],[class*="login" i],[class*="signin" i]')).slice(0,10).map(info);
    var body=(document.body&&document.body.innerText||'').replace(/\s+/g,' ').trim();
    var loginWords=(body.match(/.{0,50}(?:登录|登錄|sign in|log in|扫码|驗證|验证).{0,100}/ig)||[]).slice(0,8);
    return JSON.stringify({url:location.href,inputs:inputs,buttons:buttons,dialogs:dialogs,loginWords:loginWords});
  }catch(e){return JSON.stringify({error:String(e),url:location.href});}
})()
""".trimIndent()
        onMain {
            JsInjector.injectJs(webView, script) { value -> captured.set(value); terminal.countDown() }
        }
        terminal.await(8, TimeUnit.SECONDS)
        return captured.get()
    }

    private fun inspectSentinelDom(webView: WebView, sentinel: String): String {
        val terminal = CountDownLatch(1)
        val captured = AtomicReference("")
        val token = org.json.JSONObject.quote(sentinel)
        val script = """
(function(){
  try {
    var token=$token;
    function attrs(el){
      return {
        tag:el.tagName,
        id:el.id||'',
        cls:String(el.className||'').slice(0,240),
        role:el.getAttribute('role')||'',
        dataRole:el.getAttribute('data-role')||'',
        author:el.getAttribute('data-message-author-role')||'',
        testid:el.getAttribute('data-testid')||'',
        aria:el.getAttribute('aria-label')||''
      };
    }
    var all=Array.from(document.querySelectorAll('*')).map(function(el){
      var text=(el.innerText||el.textContent||'').replace(/\s+/g,' ').trim();
      return {el:el,text:text};
    }).filter(function(x){return x.text && x.text.indexOf(token)>=0;});
    all.sort(function(a,b){return a.text.length-b.text.length;});
    var matches=all.slice(0,12).map(function(x){
      var chain=[],n=x.el;
      for(var i=0;n&&i<5;i++,n=n.parentElement) chain.push(attrs(n));
      return {length:x.text.length,text:x.text.slice(0,300),chain:chain};
    });
    var inputs=Array.from(document.querySelectorAll('textarea,[contenteditable="true"],[role="textbox"]')).slice(0,10).map(attrs);
    return JSON.stringify({url:location.href,title:document.title,matches:matches,inputs:inputs});
  }catch(e){return JSON.stringify({error:String(e),url:location.href});}
})()
""".trimIndent()
        onMain {
            JsInjector.injectJs(webView, script) { value ->
                captured.set(value)
                terminal.countDown()
            }
        }
        terminal.await(8, TimeUnit.SECONDS)
        return captured.get()
    }

    private fun waitForInitialPage(tab: WebViewTab, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!tab.isLoading && tab.progress >= 80 && tab.url.startsWith("http")) return
            Thread.sleep(500)
        }
    }

    private fun runAutomation(
        platformId: String,
        webView: WebView,
        message: String,
        timeoutMs: Long
    ): WebAutomationResult {
        val terminal = CountDownLatch(1)
        val captured = AtomicReference<WebAutomationResult>()
        onMain {
            JsInjector.sendAndAwaitReply(
                platformId = platformId,
                webView = webView,
                message = message,
                timeoutMs = timeoutMs
            ) { result ->
                captured.set(result)
                terminal.countDown()
            }
        }
        if (!terminal.await(timeoutMs + 10_000L, TimeUnit.MILLISECONDS)) {
            return WebAutomationResult(
                success = false,
                stage = "test-timeout",
                detail = "Instrumentation smoke test timed out"
            )
        }
        return captured.get()
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else instrumentation.runOnMainSync(block)
    }
}
