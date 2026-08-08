package com.qtwl.YitongAIzhuanzhan

import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PipelineWebViewInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val webViews = mutableListOf<WebView>()

    @After
    fun cleanUp() {
        onMain {
            webViews.forEach { it.destroy() }
            webViews.clear()
        }
    }

    @Test
    fun threeWebViewsCaptureAndForwardRepliesEndToEnd() {
        val fixtures = mapOf(
            "doubao" to createFixture(
                baseUrl = "https://www.doubao.com/chat/",
                inputHtml = "<textarea data-testid='chat_input_input'></textarea>",
                buttonHtml = "<button id='flow-end-msg-send'>Send</button>",
                responseHtml = "<div data-testid='message_text_content' id='response'></div>",
                loadingHtml = "<div class='loading-spinner' id='loading' style='display:none'>loading</div>",
                prefix = "D:"
            ),
            "yuanbao" to createFixture(
                baseUrl = "https://yuanbao.tencent.com/",
                inputHtml = "<div data-slate-editor='true' contenteditable='true' id='editor'></div>",
                buttonHtml = "<button data-testid='send-button'>Send</button>",
                responseHtml = "<div data-role='assistant' id='response'></div>",
                loadingHtml = "<div data-testid='loading-indicator' id='loading' style='display:none'>loading</div>",
                prefix = "Y:"
            ),
            "deepseek" to createFixture(
                baseUrl = "https://chat.deepseek.com/",
                inputHtml = "<textarea id='editor'></textarea>",
                buttonHtml = "<button aria-label='Send'>Send</button>",
                responseHtml = "<div data-message-author-role='assistant' id='response'></div>",
                loadingHtml = "<div aria-busy='false' id='loading' style='display:none'>loading</div>",
                prefix = "S:"
            )
        )

        val terminal = CountDownLatch(1)
        val finalSnapshot = AtomicReference(PipelineSnapshot.Idle)
        val coordinator = PipelineCoordinator(
            PipelineStepExecutor { step, prompt, callback ->
                val handleRef = AtomicReference<AutomationHandle?>()
                onMain {
                    handleRef.set(
                        JsInjector.sendAndAwaitReply(
                            platformId = step.platformId,
                            webView = fixtures.getValue(step.platformId),
                            message = prompt,
                            timeoutMs = 15_000L
                        ) { result ->
                            callback(
                                PipelineExecutionResult(
                                    success = result.success,
                                    output = result.response,
                                    detail = result.detail
                                )
                            )
                        }
                    )
                }
                PipelineCancellation {
                    onMain { handleRef.get()?.cancel() }
                }
            }
        )

        coordinator.start(
            prompt = "hello",
            pipelineSteps = listOf(
                PipelineStep("doubao", maxRetries = 0),
                PipelineStep("yuanbao", maxRetries = 0),
                PipelineStep("deepseek", maxRetries = 0)
            )
        ) { snapshot ->
            finalSnapshot.set(snapshot)
            if (snapshot.state in setOf(
                    PipelineRunState.SUCCEEDED,
                    PipelineRunState.FAILED,
                    PipelineRunState.CANCELLED
                )
            ) {
                terminal.countDown()
            }
        }

        assertTrue("Pipeline did not finish in time", terminal.await(45, TimeUnit.SECONDS))
        val result = finalSnapshot.get()
        assertEquals(result.detail, PipelineRunState.SUCCEEDED, result.state)
        assertEquals("S:Y:D:hello", result.finalOutput)
        assertEquals(
            listOf("D:hello", "Y:D:hello", "S:Y:D:hello"),
            result.steps.map { it.output }
        )
    }

    @Test
    fun echoedUserPromptIsNotAcceptedAsAssistantReply() {
        val fixture = createFixture(
            baseUrl = "https://chat.deepseek.com/",
            inputHtml = "<textarea id='editor'></textarea>",
            buttonHtml = "<button aria-label='Send'>Send</button>",
            responseHtml = "<div data-message-author-role='assistant' id='response'></div>",
            loadingHtml = "<div aria-busy='false' id='loading' style='display:none'>loading</div>",
            prefix = ""
        )
        val terminal = CountDownLatch(1)
        val captured = AtomicReference<WebAutomationResult>()

        onMain {
            JsInjector.sendAndAwaitReply(
                platformId = "deepseek",
                webView = fixture,
                message = "echo-me",
                timeoutMs = 4_000L
            ) { result ->
                captured.set(result)
                terminal.countDown()
            }
        }

        assertTrue("Echo rejection test did not finish", terminal.await(10, TimeUnit.SECONDS))
        assertEquals(false, captured.get().success)
        assertEquals("reply", captured.get().stage)
    }

    @Test
    fun freshAssistantBeatsStaleReplySidebarAndPreservesMarkdown() {
        val fixture = createHtmlFixture(
            baseUrl = "https://chat.deepseek.com/",
            bodyHtml = """
                <aside>
                  <div class="markdown-body">
                    SIDEBAR CHROME SHOULD NEVER WIN even when this block is deliberately much
                    longer than the actual model response. It exists before Send and is navigation.
                  </div>
                </aside>
                <div data-message-author-role="assistant">
                  <div class="ds-markdown">OLD ANSWER</div>
                </div>
                <textarea id="editor"></textarea>
                <button aria-label="Send">Send</button>
                <div id="loading" aria-busy="false" style="display:none">loading</div>
                <main id="conversation"></main>
            """.trimIndent(),
            script = """
                var input = document.getElementById('editor');
                var button = document.querySelector('button');
                var loading = document.getElementById('loading');
                var conversation = document.getElementById('conversation');
                button.addEventListener('click', function(){
                  var value = input.value || '';
                  var user = document.createElement('div');
                  user.setAttribute('data-role', 'user');
                  user.textContent = value;
                  conversation.appendChild(user);
                  loading.style.display = 'block';
                  loading.setAttribute('aria-busy', 'true');
                  setTimeout(function(){
                    var assistant = document.createElement('div');
                    assistant.setAttribute('data-message-author-role', 'assistant');
                    assistant.innerHTML = '<div class="ds-markdown"><h2>Fresh result</h2>' +
                      '<p>Correct answer</p><pre><code class="language-kotlin">val x = 1</code></pre>' +
                      '<ul><li>A</li><li>B</li></ul></div>';
                    conversation.appendChild(assistant);
                    loading.style.display = 'none';
                    loading.setAttribute('aria-busy', 'false');
                  }, 300);
                });
            """.trimIndent()
        )

        val result = runAutomation("deepseek", fixture, "new question", 9_000L)
        assertTrue(result.detail, result.success)
        assertTrue(result.response.contains("## Fresh result"))
        assertTrue(result.response.contains("Correct answer"))
        assertTrue(result.response.contains("```kotlin"))
        assertTrue(result.response.contains("val x = 1"))
        assertTrue(result.response.contains("- A"))
        assertFalse(result.response.contains("SIDEBAR CHROME"))
        assertFalse(result.response.contains("OLD ANSWER"))
    }

    @Test
    fun unicodePromptSurvivesWebViewInjectionAndReplyCaptureExactly() {
        val fixture = createFixture(
            baseUrl = "https://www.doubao.com/chat/",
            inputHtml = "<textarea data-testid='chat_input_input'></textarea>",
            buttonHtml = "<button id='flow-end-msg-send'>Send</button>",
            responseHtml = "<div data-testid='message_text_content' id='response'></div>",
            loadingHtml = "<div class='loading-spinner' id='loading' style='display:none'>loading</div>",
            prefix = "回声:"
        )
        val prompt = "你好，测试中文 🚀 — café — Привет"

        val result = runAutomation("doubao", fixture, prompt, 9_000L)

        assertTrue(result.detail, result.success)
        assertEquals("回声:$prompt", result.response.trim())
        assertFalse(result.response.contains("�"))
    }

    @Test
    fun qwenCurrentAnswerClassPrefixIsCaptured() {
        val fixture = createFixture(
            baseUrl = "https://www.qianwen.com/",
            inputHtml = "<textarea id='editor'></textarea>",
            buttonHtml = "<button type='submit'>Send</button>",
            responseHtml = "<div class='message-select-wrapper-answer-live' id='response'></div>",
            loadingHtml = "<div aria-busy='false' id='loading' style='display:none'>loading</div>",
            prefix = "Q:"
        )
        val prompt = "Qwen current DOM sentinel 你好🚀"

        val result = runAutomation("tongyi", fixture, prompt, 9_000L)

        assertTrue(result.detail, result.success)
        assertEquals("Q:$prompt", result.response.trim())
    }

    @Test
    fun kimiContenteditableReceivesPromptExactlyOnce() {
        val fixture = createFixture(
            baseUrl = "https://www.kimi.com/",
            inputHtml = "<div class='chat-input-editor' role='textbox' contenteditable='true'></div>",
            buttonHtml = "<button type='submit'>Send</button>",
            responseHtml = "<div class='segment-assistant'><div class='markdown' id='response'></div></div>",
            loadingHtml = "<div aria-busy='false' id='loading' style='display:none'>loading</div>",
            prefix = "K:"
        )
        val prompt = "Kimi exactly once 你好🚀"

        val result = runAutomation("kimi", fixture, prompt, 9_000L)

        assertTrue(result.detail, result.success)
        assertEquals("K:$prompt", result.response.trim())
        assertFalse(result.response.contains(prompt + prompt))
    }

    @Test
    fun visibleLoginModalTerminatesAsAuthInsteadOfReplyTimeout() {
        val fixture = createHtmlFixture(
            baseUrl = "https://www.kimi.com/",
            bodyHtml = """
                <div class="chat-input-editor" role="textbox" contenteditable="true"></div>
                <button type="submit">Send</button>
                <main id="conversation"></main>
            """.trimIndent(),
            script = """
                var button = document.querySelector('button');
                button.addEventListener('click', function(){
                  var modal = document.createElement('div');
                  modal.className = 'login-modal oversea';
                  modal.textContent = 'Log in to New Chat Continue with Google OR Log in with phone number';
                  document.body.appendChild(modal);
                });
            """.trimIndent()
        )

        val result = runAutomation("kimi", fixture, "auth gate test", 9_000L)

        assertFalse(result.success)
        assertEquals("auth", result.stage)
        assertTrue(result.detail.contains("requires sign-in"))
    }

    @Test
    fun streamingPauseDoesNotReturnPartialAssistantReply() {
        val fixture = createHtmlFixture(
            baseUrl = "https://chat.deepseek.com/",
            bodyHtml = """
                <textarea id="editor"></textarea>
                <button aria-label="Send">Send</button>
                <div id="loading" aria-busy="false" style="display:none">loading</div>
                <main id="conversation">
                  <div data-message-author-role="assistant" id="response"></div>
                </main>
            """.trimIndent(),
            script = """
                var input = document.getElementById('editor');
                var button = document.querySelector('button');
                var loading = document.getElementById('loading');
                var response = document.getElementById('response');
                var conversation = document.getElementById('conversation');
                button.addEventListener('click', function(){
                  var user = document.createElement('div');
                  user.setAttribute('data-role', 'user');
                  user.textContent = input.value || '';
                  conversation.insertBefore(user, response);
                  loading.style.display = 'block';
                  loading.setAttribute('aria-busy', 'true');
                  response.textContent = 'first chunk';
                  setTimeout(function(){ response.textContent = 'first chunk final chunk'; }, 2200);
                  setTimeout(function(){
                    loading.style.display = 'none';
                    loading.setAttribute('aria-busy', 'false');
                  }, 2500);
                });
            """.trimIndent()
        )

        val result = runAutomation("deepseek", fixture, "stream please", 10_000L)
        assertTrue(result.detail, result.success)
        assertEquals("first chunk final chunk", result.response.trim())
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
        assertTrue("Automation did not finish in time", terminal.await(timeoutMs + 6_000L, TimeUnit.MILLISECONDS))
        return captured.get()
    }

    private fun createHtmlFixture(
        baseUrl: String,
        bodyHtml: String,
        script: String
    ): WebView {
        val html = """
            <!doctype html>
            <html>
              <head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
              <body>
                $bodyHtml
                <script>
                  (function(){
                    $script
                  })();
                </script>
              </body>
            </html>
        """.trimIndent()
        return createWebView(baseUrl, html)
    }

    private fun createFixture(
        baseUrl: String,
        inputHtml: String,
        buttonHtml: String,
        responseHtml: String,
        loadingHtml: String,
        prefix: String
    ): WebView {
        val html = """
            <!doctype html>
            <html>
              <head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
              <body>
                $inputHtml
                $buttonHtml
                $loadingHtml
                $responseHtml
                <script>
                  (function(){
                    var input = document.querySelector('textarea, [contenteditable="true"]');
                    var button = document.querySelector('button');
                    var loading = document.getElementById('loading');
                    var response = document.getElementById('response');
                    button.addEventListener('click', function(){
                      var value = input.value !== undefined ? input.value : (input.innerText || input.textContent || '');
                      loading.style.display = 'block';
                      loading.setAttribute('aria-busy', 'true');
                      setTimeout(function(){
                        response.textContent = '${prefix}' + value;
                        loading.style.display = 'none';
                        loading.setAttribute('aria-busy', 'false');
                      }, 250);
                    });
                  })();
                </script>
              </body>
            </html>
        """.trimIndent()

        return createWebView(baseUrl, html)
    }

    private fun createWebView(baseUrl: String, html: String): WebView {
        val loaded = CountDownLatch(1)
        val holder = AtomicReference<WebView>()
        onMain {
            val webView = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onReplyForRequest(requestId: String, content: String) {
                        Handler(Looper.getMainLooper()).post {
                            ReplyBridge.deliver(requestId, content)
                        }
                    }

                    @JavascriptInterface
                    fun onReply(content: String) = Unit

                    @JavascriptInterface
                    fun onStatus(message: String) = Unit
                }, "Android")
                layoutParams = android.view.ViewGroup.LayoutParams(1080, 1920)
                measure(
                    View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
                )
                layout(0, 0, 1080, 1920)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        loaded.countDown()
                    }
                }
                loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
            }
            holder.set(webView)
            webViews += webView
        }
        assertTrue("Fixture failed to load: $baseUrl", loaded.await(10, TimeUnit.SECONDS))
        return holder.get()
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            instrumentation.runOnMainSync(block)
        }
    }
}
