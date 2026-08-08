package com.qtwl.YitongAIzhuanzhan

import fi.iki.elonen.NanoHTTPD
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class GatewayServerRobolectricTest {
    private lateinit var server: GatewayServer

    @Before
    fun setUp() {
        server = GatewayServer(RuntimeEnvironment.getApplication(), 0)
        WebViewManager.destroyAll()
    }

    @After
    fun tearDown() {
        server.stop()
        WebViewManager.destroyAll()
    }

    @Test
    fun gatewayReadBodyPreservesRawUtf8WhenJsonHeaderHasNoCharset() {
        val json = """{"model":"qtai-sj","messages":[{"role":"user","content":"你好，网关烟雾测试 🚀 — café — Привет"}]}"""
        val bytes = json.toByteArray(Charsets.UTF_8)
        val session = FakeSession(
            body = bytes,
            headers = mutableMapOf(
                "content-length" to bytes.size.toString(),
                "content-type" to "application/json"
            )
        )

        assertEquals(json, server.readBody(session))
    }

    @Test
    fun allFiveProductionPlatformDefinitionsCreateConfiguredWebViews() {
        assertEquals(
            setOf("doubao", "yuanbao", "tongyi", "deepseek", "kimi"),
            AiPlatformRegistry.supported.map { it.id }.toSet()
        )

        AiPlatformRegistry.supported.forEach { platform ->
            assertTrue(platform.inputSelectors.isNotEmpty())
            assertTrue(platform.sendButtonSelectors.isNotEmpty())
            assertTrue(platform.assistantMessageSelectors.isNotEmpty())
            assertEquals(platform.id, AiPlatformRegistry.detect(platform.url).id)

            val tab = WebViewManager.createTab(
                context = RuntimeEnvironment.getApplication(),
                url = "about:blank",
                platformId = platform.id
            )
            val webView = WebViewManager.initWebView(RuntimeEnvironment.getApplication(), tab.id)
            assertNotNull("WebView was not created for ${platform.id}", webView)
            assertTrue("JavaScript disabled for ${platform.id}", webView!!.settings.javaScriptEnabled)
            assertEquals(USER_AGENT_DESKTOP, webView.settings.userAgentString)
        }
    }

    private class FakeSession(
        body: ByteArray,
        private val headers: MutableMap<String, String>,
        private val uri: String = "/v1/chat/completions",
        private val method: NanoHTTPD.Method = NanoHTTPD.Method.POST
    ) : NanoHTTPD.IHTTPSession {
        private val stream = ByteArrayInputStream(body)
        private val parms = mutableMapOf<String, String>()
        private val parameters = mutableMapOf<String, MutableList<String>>()

        override fun execute() = Unit
        override fun getCookies(): NanoHTTPD.CookieHandler = throw UnsupportedOperationException()
        override fun getHeaders(): MutableMap<String, String> = headers
        override fun getInputStream(): InputStream = stream
        override fun getMethod(): NanoHTTPD.Method = method
        @Suppress("DEPRECATION")
        override fun getParms(): MutableMap<String, String> = parms
        override fun getParameters(): MutableMap<String, MutableList<String>> = parameters
        override fun getQueryParameterString(): String? = null
        override fun getUri(): String = uri
        override fun parseBody(files: MutableMap<String, String>) = Unit
        override fun getRemoteIpAddress(): String = "127.0.0.1"
        override fun getRemoteHostName(): String = "localhost"
    }
}
