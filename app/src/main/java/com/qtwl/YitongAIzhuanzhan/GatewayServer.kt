package com.qtwl.YitongAIzhuanzhan

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class GatewayServer(
    private val context: Context,
    private val port: Int = 7773
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "GatewayServer"
        private const val AUTOMATION_TIMEOUT_MS = 150_000L
        private const val HTTP_WAIT_TIMEOUT_MS = 165_000L
    }

    var onRequestReceived: ((String) -> Unit)? = null
    var onReplyReady: ((String) -> Unit)? = null
    var onRequestFailed: ((String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // One WebView can only type and submit one prompt safely at a time. Rejecting
    // concurrent callers is preferable to interleaving prompts and returning the
    // wrong website reply to the wrong OpenAI request.
    private val requestSlot = Semaphore(1, true)

    override fun serve(session: IHTTPSession): Response {
        val savedKey = GatewayPrefs.getApiKey(context)
        if (savedKey.isNotEmpty()) {
            val auth = session.headers["authorization"].orEmpty()
            if (!auth.equals("Bearer $savedKey", ignoreCase = true)) {
                return errorResponse(Response.Status.UNAUTHORIZED, "unauthorized")
            }
        }

        return try {
            when {
                session.uri == "/health" && session.method == Method.GET -> healthResponse()
                session.uri == "/v1/models" && session.method == Method.GET -> modelsResponse()
                session.uri == "/v1/chat/completions" && session.method == Method.POST -> handleChat(session)
                else -> errorResponse(Response.Status.NOT_FOUND, "not found")
            }
        } catch (error: Exception) {
            Log.e(TAG, "Gateway request failed", error)
            onRequestFailed?.invoke(error.message ?: "Gateway request failed")
            errorResponse(Response.Status.INTERNAL_ERROR, error.message ?: "internal error")
        }
    }

    private fun healthResponse(): Response = jsonResponse(
        JSONObject()
            .put("status", if (isRunning()) "ok" else "stopped")
            .put("port", port)
            .toString()
    )

    private fun modelsResponse(): Response {
        val models = JSONArray().apply {
            put(JSONObject().put("id", "qtai-sj").put("object", "model").put("owned_by", "qitong"))
            put(JSONObject().put("id", "qtllq").put("object", "model").put("owned_by", "qitong"))
        }
        return jsonResponse(JSONObject().put("object", "list").put("data", models).toString())
    }

    private fun handleChat(session: IHTTPSession): Response {
        if (!requestSlot.tryAcquire()) {
            return errorResponse(
                Response.Status.SERVICE_UNAVAILABLE,
                "The gateway is already processing another WebView request"
            )
        }

        try {
            val body = runCatching { readBody(session) }.getOrElse { error ->
                return errorResponse(
                    Response.Status.BAD_REQUEST,
                    error.message ?: "request body must be valid UTF-8"
                )
            }
            val request = runCatching { JSONObject(body) }.getOrElse {
                return errorResponse(Response.Status.BAD_REQUEST, "invalid JSON body")
            }
            val messages = request.optJSONArray("messages")
                ?: return errorResponse(Response.Status.BAD_REQUEST, "messages must be an array")
            val stream = request.optBoolean("stream", false)
            val modelId = request.optString("model", "qtai-sj").ifBlank { "qtai-sj" }

            val lastUser = (messages.length() - 1 downTo 0)
                .asSequence()
                .mapNotNull { index -> messages.optJSONObject(index) }
                .firstOrNull { message -> message.optString("role") == "user" }
                ?.optString("content")
                ?.trim()
                .orEmpty()
            if (lastUser.isBlank()) {
                return errorResponse(Response.Status.BAD_REQUEST, "no user message")
            }

            onRequestReceived?.invoke(lastUser)

            val latch = CountDownLatch(1)
            val resultRef = AtomicReference<WebAutomationResult?>()
            val handleRef = AtomicReference<AutomationHandle?>()

            mainHandler.post {
                val tab = runCatching { WebViewManager.getCurrentTab() }.getOrNull()
                val webView = tab?.webView
                if (tab == null || webView == null) {
                    resultRef.set(
                        WebAutomationResult(
                            success = false,
                            stage = "ready",
                            detail = "webview not ready"
                        )
                    )
                    latch.countDown()
                    return@post
                }

                val platform = tab.platformId
                    ?.let(AiPlatformRegistry::get)
                    ?: AiPlatformRegistry.detect(tab.url)
                runCatching {
                    JsInjector.sendAndAwaitReply(
                        platformId = platform.id,
                        webView = webView,
                        message = lastUser,
                        timeoutMs = AUTOMATION_TIMEOUT_MS
                    ) { result ->
                        resultRef.set(result)
                        latch.countDown()
                    }
                }.onSuccess(handleRef::set)
                    .onFailure { error ->
                        resultRef.set(
                            WebAutomationResult(
                                success = false,
                                stage = "send",
                                detail = error.message ?: "WebView automation failed"
                            )
                        )
                        latch.countDown()
                    }
            }

            val completed = runCatching {
                latch.await(HTTP_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }.getOrDefault(false)
            if (!completed) {
                handleRef.get()?.cancel()
                val detail = "Timed out waiting for the AI website reply"
                onRequestFailed?.invoke(detail)
                return errorResponse(Response.Status.SERVICE_UNAVAILABLE, detail)
            }

            val result = resultRef.get()
                ?: return errorResponse(Response.Status.INTERNAL_ERROR, "reply task completed without a result")
            if (!result.success || result.response.isBlank()) {
                val detail = result.detail.ifBlank { "AI reply capture failed" }
                onRequestFailed?.invoke(detail)
                return errorResponse(Response.Status.INTERNAL_ERROR, detail)
            }

            onReplyReady?.invoke(result.response)
            return if (stream) {
                streamResponse(modelId, result.response)
            } else {
                completionResponse(modelId, result.response)
            }
        } finally {
            requestSlot.release()
        }
    }

    internal fun readBody(session: IHTTPSession): String {
        // NanoHTTPD 2.3.1 defaults a present Content-Type without an explicit
        // charset to US-ASCII. OpenAI-compatible clients commonly send
        // application/json without charset=UTF-8, which turns raw Chinese
        // text into U+FFFD replacement characters before JSONObject sees it.
        // Read the declared raw bytes ourselves and decode strict UTF-8.
        val contentLength = session.headers["content-length"]
            ?.trim()
            ?.toLongOrNull()
        if (contentLength != null) {
            return Utf8RequestBody.readExact(session.inputStream, contentLength)
        }

        // Fallback for unusual requests without Content-Length. NanoHTTPD may
        // already have decoded the body here, so never forward a body that has
        // replacement characters: a clear 400 is safer than sending gibberish
        // to the AI website.
        val contentType = session.headers["content-type"].orEmpty()
        if (!contentType.contains("charset=", ignoreCase = true)) {
            session.headers["content-type"] = if (contentType.isBlank()) {
                "application/json; charset=UTF-8"
            } else {
                "$contentType; charset=UTF-8"
            }
        }
        val files = HashMap<String, String>()
        session.parseBody(files)
        return Utf8RequestBody.validateLegacyDecodedBody(files["postData"].orEmpty())
    }

    private fun completionResponse(modelId: String, reply: String): Response {
        val response = JSONObject().apply {
            put("id", "chatcmpl-${System.currentTimeMillis()}")
            put("object", "chat.completion")
            put("created", System.currentTimeMillis() / 1000L)
            put("model", modelId)
            put(
                "choices",
                JSONArray().put(
                    JSONObject()
                        .put("index", 0)
                        .put(
                            "message",
                            JSONObject().put("role", "assistant").put("content", reply)
                        )
                        .put("finish_reason", "stop")
                )
            )
        }
        return jsonResponse(response.toString())
    }

    private fun streamResponse(modelId: String, reply: String): Response {
        val chunk = JSONObject().apply {
            put("id", "chatcmpl-${System.currentTimeMillis()}")
            put("object", "chat.completion.chunk")
            put("created", System.currentTimeMillis() / 1000L)
            put("model", modelId)
            put(
                "choices",
                JSONArray().put(
                    JSONObject()
                        .put("index", 0)
                        .put("delta", JSONObject().put("content", reply))
                        .put("finish_reason", JSONObject.NULL)
                )
            )
        }
        val sse = "data: $chunk\n\ndata: [DONE]\n\n"
        return newFixedLengthResponse(
            Response.Status.OK,
            "text/event-stream; charset=utf-8",
            sse
        ).apply {
            addHeader("Cache-Control", "no-cache")
            addHeader("Connection", "close")
        }
    }

    private fun jsonResponse(json: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json)

    private fun errorResponse(status: Response.Status, message: String): Response =
        newFixedLengthResponse(
            status,
            "application/json; charset=utf-8",
            JSONObject()
                .put(
                    "error",
                    JSONObject()
                        .put("message", message)
                        .put("type", "gateway_error")
                        .put("code", status.requestStatus)
                )
                .toString()
        )

    fun startServer(): Boolean = try {
        start(SOCKET_READ_TIMEOUT, false)
        val running = isRunning()
        if (running) Log.i(TAG, "QiTong web gateway started on port $port")
        else Log.e(TAG, "NanoHTTPD returned without entering the running state")
        running
    } catch (error: Exception) {
        Log.e(TAG, "Gateway start failed", error)
        false
    }

    fun isRunning(): Boolean = isAlive
}
