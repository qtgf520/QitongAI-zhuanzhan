package com.qtwl.YitongAIzhuanzhan

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Result of one complete web-AI interaction: ready -> fill -> send -> stable reply. */
data class WebAutomationResult(
    val success: Boolean,
    val stage: String,
    val detail: String,
    val response: String = ""
)

class AutomationHandle internal constructor(
    private val onCancel: () -> Unit = {}
) {
    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        if (cancelled.compareAndSet(false, true)) onCancel()
    }

    internal fun isCancelled(): Boolean = cancelled.get()
}

private data class ReplySnapshot(
    val text: String,
    val count: Int,
    val loading: Boolean,
    val inputFound: Boolean,
    val loginLikely: Boolean,
    val authBlocked: Boolean,
    val url: String
)

/**
 * JavaScript bridge for web AI pages.
 *
 * Unlike the original fire-and-forget implementation, this bridge takes a reply
 * baseline before sending, waits for a new assistant-only message, and requires
 * that message to remain stable after generation stops before returning it.
 */
object JsInjector {
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val READY_TIMEOUT_MS = 45_000L
    private const val POLL_INTERVAL_MS = 900L
    private const val REQUIRED_STABLE_POLLS = 3
    private const val REQUIRED_STABLE_POLLS_WHILE_LOADING = 10

    @Volatile
    private var slateFillerSource: String? = null

    fun sendAndAwaitReply(
        platformId: String,
        webView: WebView,
        message: String,
        timeoutMs: Long = 150_000L,
        callback: (WebAutomationResult) -> Unit
    ): AutomationHandle {
        val platform = AiPlatformRegistry.get(platformId) ?: AiPlatformRegistry.generic()
        val requestId = UUID.randomUUID().toString()
        val handle = AutomationHandle {
            ReplyBridge.unregister(requestId)
            cancelReplyWatcher(webView, requestId)
        }
        val finished = AtomicBoolean(false)
        val deadline = System.currentTimeMillis() + timeoutMs

        fun finish(result: WebAutomationResult) {
            if (finished.compareAndSet(false, true)) {
                ReplyBridge.unregister(requestId)
                cancelReplyWatcher(webView, requestId)
                if (!handle.isCancelled()) callback(result)
            }
        }

        fun begin() {
            waitUntilReady(
                platform = platform,
                webView = webView,
                handle = handle,
                readyDeadline = minOf(deadline, System.currentTimeMillis() + READY_TIMEOUT_MS)
            ) { ready, readyDetail ->
                if (!ready) {
                    finish(
                        WebAutomationResult(
                            success = false,
                            stage = "ready",
                            detail = readyDetail
                        )
                    )
                    return@waitUntilReady
                }

                captureSnapshot(platform, webView, handle) { baseline ->
                    if (baseline == null) {
                        finish(
                            WebAutomationResult(
                                success = false,
                                stage = "baseline",
                                detail = "Could not inspect the current conversation"
                            )
                        )
                        return@captureSnapshot
                    }

                    ReplyBridge.register(requestId) { content ->
                        val reply = content.trim()
                        if (reply.isNotBlank() && reply != message.trim()) {
                            finish(
                                WebAutomationResult(
                                    success = true,
                                    stage = "complete",
                                    detail = "Semantically captured reply from ${platform.displayName}",
                                    response = reply
                                )
                            )
                        }
                    }

                    armReplyWatcher(
                        platform = platform,
                        webView = webView,
                        handle = handle,
                        requestId = requestId,
                        baseline = baseline,
                        sentMessage = message,
                        timeoutMs = timeoutMs
                    ) { watcherArmed ->
                        if (!watcherArmed) {
                            finish(
                                WebAutomationResult(
                                    success = false,
                                    stage = "watcher",
                                    detail = "Could not arm the semantic reply watcher for ${platform.displayName}"
                                )
                            )
                            return@armReplyWatcher
                        }

                        evaluateJson(webView, buildFillScript(platform, message), handle) { fill ->
                            if (fill?.optBoolean("success", false) != true) {
                                finish(
                                    WebAutomationResult(
                                        success = false,
                                        stage = "fill",
                                        detail = fill?.optString("error")
                                            ?.takeIf { it.isNotBlank() }
                                            ?: "The message input could not be filled"
                                    )
                                )
                                return@evaluateJson
                            }

                            mainHandler.postDelayed({
                                if (handle.isCancelled()) return@postDelayed
                                evaluateJson(webView, buildVerifyFilledMessageScript(platform, message), handle) { verified ->
                                    if (verified?.optBoolean("success", false) != true) {
                                        finish(
                                            WebAutomationResult(
                                                success = false,
                                                stage = "fill",
                                                detail = verified?.optString("error")
                                                    ?.takeIf { it.isNotBlank() }
                                                    ?: "The message input changed before it could be sent"
                                            )
                                        )
                                        return@evaluateJson
                                    }
                                    evaluateJson(webView, buildSubmitScript(platform), handle) { send ->
                                    if (send?.optBoolean("success", false) != true) {
                                        finish(
                                            WebAutomationResult(
                                                success = false,
                                                stage = "send",
                                                detail = send?.optString("error")
                                                    ?.takeIf { it.isNotBlank() }
                                                    ?: "No working send control was found"
                                            )
                                        )
                                        return@evaluateJson
                                    }

                                    // 双通道：语义监控（SlateFiller） + DOM轮询（兜底）
                                    pollForStableReply(
                                        platform = platform,
                                        webView = webView,
                                        handle = handle,
                                        deadline = deadline,
                                        baseline = baseline,
                                        sentMessage = message,
                                        lastText = "",
                                        stablePolls = 0
                                    ) { result ->
                                        if (!finished.get()) finish(result)
                                    }

                                    // 超时兜底
                                    val remainingMs = (deadline - System.currentTimeMillis()).coerceAtLeast(1_000L)
                                    mainHandler.postDelayed({
                                        if (handle.isCancelled() || finished.get()) return@postDelayed
                                        captureSnapshot(platform, webView, handle) { snapshot ->
                                            if (handle.isCancelled() || finished.get()) return@captureSnapshot
                                            val partial = snapshot?.text.orEmpty().trim()
                                            finish(
                                                WebAutomationResult(
                                                    success = false,
                                                    stage = "reply",
                                                    detail = "No high-confidence completed assistant reply was detected from ${platform.displayName} before timeout (${snapshot?.url.orEmpty()})",
                                                    response = partial.takeIf { it.isNotBlank() && it != message.trim() }.orEmpty()
                                                )
                                            )
                                        }
                                    }, remainingMs)
                                    }
                                }
                            }, platform.afterFillDelayMs)
                        }
                    }
                }
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) begin() else mainHandler.post { begin() }
        return handle
    }

    private fun waitUntilReady(
        platform: AiPlatformDefinition,
        webView: WebView,
        handle: AutomationHandle,
        readyDeadline: Long,
        callback: (Boolean, String) -> Unit
    ) {
        if (handle.isCancelled()) return
        evaluateJson(webView, buildReadyScript(platform), handle) { json ->
            if (handle.isCancelled()) return@evaluateJson
            val inputFound = json?.optBoolean("inputFound", false) == true
            val readyState = json?.optString("readyState").orEmpty()
            if (inputFound && readyState != "loading") {
                callback(true, "ready")
                return@evaluateJson
            }

            if (System.currentTimeMillis() >= readyDeadline) {
                val loginLikely = json?.optBoolean("loginLikely", false) == true
                val url = json?.optString("url").orEmpty()
                val detail = if (loginLikely) {
                    "${platform.displayName} requires sign-in or account confirmation before its message box is available ($url)"
                } else {
                    "${platform.displayName} did not expose a usable message box before the readiness timeout ($url)"
                }
                callback(false, detail)
            } else {
                mainHandler.postDelayed({
                    waitUntilReady(platform, webView, handle, readyDeadline, callback)
                }, POLL_INTERVAL_MS)
            }
        }
    }

    private fun pollForStableReply(
        platform: AiPlatformDefinition,
        webView: WebView,
        handle: AutomationHandle,
        deadline: Long,
        baseline: ReplySnapshot,
        sentMessage: String,
        lastText: String,
        stablePolls: Int,
        callback: (WebAutomationResult) -> Unit
    ) {
        if (handle.isCancelled()) return
        captureSnapshot(platform, webView, handle) { snapshot ->
            if (handle.isCancelled()) return@captureSnapshot
            if (snapshot == null) {
                if (System.currentTimeMillis() >= deadline) {
                    callback(
                        WebAutomationResult(
                            success = false,
                            stage = "reply",
                            detail = "The page stopped returning conversation state"
                        )
                    )
                } else {
                    mainHandler.postDelayed({
                        pollForStableReply(
                            platform,
                            webView,
                            handle,
                            deadline,
                            baseline,
                            sentMessage,
                            lastText,
                            stablePolls,
                            callback
                        )
                    }, POLL_INTERVAL_MS)
                }
                return@captureSnapshot
            }

            val normalisedReply = snapshot.text.trim()
            val normalisedSent = sentMessage.trim()
            val hasNewReply = normalisedReply.isNotBlank() &&
                normalisedReply != normalisedSent &&
                (snapshot.count > baseline.count || normalisedReply != baseline.text.trim())
            val nextStable = if (hasNewReply && snapshot.text == lastText) stablePolls + 1 else 0

            if (snapshot.authBlocked && !hasNewReply) {
                callback(
                    WebAutomationResult(
                        success = false,
                        stage = "auth",
                        detail = "${platform.displayName} requires sign-in before a reply can be generated (${snapshot.url})"
                    )
                )
                return@captureSnapshot
            }

            val requiredStablePolls = if (snapshot.loading) {
                REQUIRED_STABLE_POLLS_WHILE_LOADING
            } else {
                REQUIRED_STABLE_POLLS
            }
            if (hasNewReply && nextStable >= requiredStablePolls) {
                callback(
                    WebAutomationResult(
                        success = true,
                        stage = "complete",
                        detail = "Reply captured from ${platform.displayName}",
                        response = snapshot.text
                    )
                )
                return@captureSnapshot
            }

            if (System.currentTimeMillis() >= deadline) {
                callback(
                    WebAutomationResult(
                        success = false,
                        stage = "reply",
                        detail = if (hasNewReply) {
                            "${platform.displayName} produced text but it did not reach a stable completed state before timeout"
                        } else {
                            "No new assistant reply was detected from ${platform.displayName} before timeout (${snapshot.url})"
                        },
                        response = snapshot.text.takeIf { hasNewReply }.orEmpty()
                    )
                )
                return@captureSnapshot
            }

            mainHandler.postDelayed({
                pollForStableReply(
                    platform = platform,
                    webView = webView,
                    handle = handle,
                    deadline = deadline,
                    baseline = baseline,
                    sentMessage = sentMessage,
                    lastText = if (hasNewReply) snapshot.text else lastText,
                    stablePolls = nextStable,
                    callback = callback
                )
            }, POLL_INTERVAL_MS)
        }
    }

    private fun captureSnapshot(
        platform: AiPlatformDefinition,
        webView: WebView,
        handle: AutomationHandle,
        callback: (ReplySnapshot?) -> Unit
    ) {
        evaluateJson(webView, buildSnapshotScript(platform), handle) { json ->
            callback(
                json?.let {
                    ReplySnapshot(
                        text = it.optString("text").trim(),
                        count = it.optInt("count", 0),
                        loading = it.optBoolean("loading", false),
                        inputFound = it.optBoolean("inputFound", false),
                        loginLikely = it.optBoolean("loginLikely", false),
                        authBlocked = it.optBoolean("authBlocked", false),
                        url = it.optString("url")
                    )
                }
            )
        }
    }

    private fun armReplyWatcher(
        platform: AiPlatformDefinition,
        webView: WebView,
        handle: AutomationHandle,
        requestId: String,
        baseline: ReplySnapshot,
        sentMessage: String,
        timeoutMs: Long,
        callback: (Boolean) -> Unit
    ) {
        if (handle.isCancelled()) return
        val source = loadSlateFiller(webView)
        if (source == null) {
            callback(false)
            return
        }
        val options = JSONObject().apply {
            put("requestId", requestId)
            put("replySelectors", JSONArray(platform.assistantMessageSelectors))
            put("loadingSelectors", JSONArray(platform.loadingSelectors))
            put("baselineCount", baseline.count)
            put("baselineText", baseline.text)
            put("sentMessage", sentMessage)
            put("timeoutMs", timeoutMs)
        }
        webView.evaluateJavascript(source) {
            if (handle.isCancelled()) return@evaluateJavascript
            val script = "(function(){return window.__watchReply ? window.__watchReply(${options}) : false;})()"
            webView.evaluateJavascript(script) { raw ->
                if (handle.isCancelled()) return@evaluateJavascript
                callback(raw == "true")
            }
        }
    }

    private fun loadSlateFiller(webView: WebView): String? {
        slateFillerSource?.let { return it }
        return synchronized(this) {
            slateFillerSource ?: runCatching {
                webView.context.assets.open("SlateFiller.js").bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrNull()?.also { slateFillerSource = it }
        }
    }

    private fun cancelReplyWatcher(webView: WebView, requestId: String) {
        val script = "(function(){if(window.__cancelReplyWatcher){window.__cancelReplyWatcher(${JSONObject.quote(requestId)});}})()"
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching { webView.evaluateJavascript(script, null) }
        } else {
            mainHandler.post { runCatching { webView.evaluateJavascript(script, null) } }
        }
    }

    private fun buildReadyScript(platform: AiPlatformDefinition): String {
        val inputs = JSONArray(platform.inputSelectors).toString()
        return """
(function(){
  try {
    var inputSelectors = $inputs;
    function roots(){
      var result=[document], queue=[document];
      while(queue.length){
        var root=queue.shift();
        var all=root.querySelectorAll ? root.querySelectorAll('*') : [];
        for(var i=0;i<all.length;i++) if(all[i].shadowRoot){ result.push(all[i].shadowRoot); queue.push(all[i].shadowRoot); }
      }
      return result;
    }
    function usable(el){
      if(!el || el.disabled || el.getAttribute('aria-disabled')==='true') return false;
      var style=getComputedStyle(el);
      return !el.hidden && style.display!=='none' && style.visibility!=='hidden';
    }
    function first(selectors){
      var rs=roots();
      for(var s=0;s<selectors.length;s++) for(var r=0;r<rs.length;r++){
        var el=rs[r].querySelector(selectors[s]); if(usable(el)) return el;
      }
      return null;
    }
    var body=(document.body && document.body.innerText || '').toLowerCase();
    var loginLikely=/登录|登錄|sign in|log in|扫码|掃碼|verify your account/.test(body);
    return JSON.stringify({
      inputFound: !!first(inputSelectors),
      readyState: document.readyState,
      loginLikely: loginLikely,
      title: document.title || '',
      url: location.href
    });
  }catch(e){ return JSON.stringify({error:String(e), url:location.href}); }
})()
""".trimIndent()
    }

    private fun buildFillScript(platform: AiPlatformDefinition, message: String): String {
        val inputs = JSONArray(platform.inputSelectors).toString()
        val text = JSONObject.quote(message)
        return """
(function(){
  try {
    var selectors=$inputs, text=$text;
    function roots(){
      var result=[document], queue=[document];
      while(queue.length){
        var root=queue.shift(), all=root.querySelectorAll ? root.querySelectorAll('*') : [];
        for(var i=0;i<all.length;i++) if(all[i].shadowRoot){ result.push(all[i].shadowRoot); queue.push(all[i].shadowRoot); }
      }
      return result;
    }
    function usable(el){
      if(!el || el.disabled || el.getAttribute('aria-disabled')==='true') return false;
      var style=getComputedStyle(el);
      return !el.hidden && style.display!=='none' && style.visibility!=='hidden';
    }
    function first(){
      var rs=roots();
      for(var s=0;s<selectors.length;s++) for(var r=0;r<rs.length;r++){
        var el=rs[r].querySelector(selectors[s]); if(usable(el)) return el;
      }
      return null;
    }
    var input=first();
    if(!input) return JSON.stringify({success:false,error:'Message input not found'});
    input.focus();
    try{ input.click(); }catch(ignore){}

    var isKimiEditor = !!(input.matches && input.matches('.chat-input-editor[contenteditable="true"],.chat-input-editor[role="textbox"]'));
    if(isKimiEditor){
      function normaliseKimiEditor(v){
        return String(v||'').normalize('NFC').replace(/\u00a0/g,' ').replace(/[\u0000-\u001F\u007F\u00AD\u034F\u061C\u180E\u200B-\u200F\u202A-\u202E\u2060-\u206F\uFEFF]/g,'').replace(/\s+/g,' ').trim();
      }
      var kimiSelection=window.getSelection(), kimiRange=document.createRange();
      kimiRange.selectNodeContents(input);
      kimiSelection.removeAllRanges();
      kimiSelection.addRange(kimiRange);
      try{ document.execCommand('insertText',false,text); }catch(ignore){}
      var kimiActual=normaliseKimiEditor(input.innerText || input.textContent || '');
      var kimiExpected=normaliseKimiEditor(text);
      if(kimiActual!==kimiExpected){
        input.replaceChildren(document.createTextNode(text));
        input.dispatchEvent(new Event('input',{bubbles:true}));
        kimiActual=normaliseKimiEditor(input.innerText || input.textContent || '');
      }
      return JSON.stringify({success:true,method:'kimi-contenteditable',value:kimiActual});
    }

    if(input.tagName==='TEXTAREA' || input.tagName==='INPUT'){
      var proto=input.tagName==='TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
      var descriptor=Object.getOwnPropertyDescriptor(proto,'value');
      if(descriptor && descriptor.set) descriptor.set.call(input,text); else input.value=text;
      input.dispatchEvent(new InputEvent('beforeinput',{bubbles:true,cancelable:true,data:text,inputType:'insertText'}));
      input.dispatchEvent(new InputEvent('input',{bubbles:true,data:text,inputType:'insertText'}));
      input.dispatchEvent(new Event('change',{bubbles:true}));
    }else{
      var selection=window.getSelection(), range=document.createRange();
      range.selectNodeContents(input); range.collapse(false);
      selection.removeAllRanges(); selection.addRange(range);
      var inserted=false;
      try{
        document.execCommand('selectAll',false,null);
        inserted=document.execCommand('insertText',false,text);
      }catch(ignore){}
      var actual=(input.innerText || input.textContent || '').trim();
      if(!inserted || actual!==text.trim()){
        input.innerHTML='';
        input.appendChild(document.createTextNode(text));
        actual=(input.innerText || input.textContent || '').trim();
      }
      if(actual!==text.trim()){
        try{
          var dt=new DataTransfer(); dt.setData('text/plain',text);
          input.dispatchEvent(new ClipboardEvent('paste',{bubbles:true,cancelable:true,clipboardData:dt}));
        }catch(ignore){}
      }
      input.dispatchEvent(new InputEvent('beforeinput',{bubbles:true,cancelable:true,data:text,inputType:'insertText'}));
      input.dispatchEvent(new InputEvent('input',{bubbles:true,data:text,inputType:'insertText'}));
      input.dispatchEvent(new Event('change',{bubbles:true}));
    }
    return JSON.stringify({success:true,value:(input.value || input.innerText || input.textContent || '')});
  }catch(e){ return JSON.stringify({success:false,error:String(e)}); }
})()
""".trimIndent()
    }

    private fun buildVerifyFilledMessageScript(platform: AiPlatformDefinition, message: String): String {
        if (platform.id != "kimi") return """JSON.stringify({success:true})"""
        val inputs = JSONArray(platform.inputSelectors).toString()
        val text = JSONObject.quote(message)
        return """
(function(){
  try{
    var selectors=$inputs, expected=$text;
    function normalise(v){
      return String(v||'').normalize('NFC').replace(/\u00a0/g,' ')
        .replace(/[\u0000-\u001F\u007F\u00AD\u034F\u061C\u180E\u200B-\u200F\u202A-\u202E\u2060-\u206F\uFEFF]/g,'')
        .replace(/\s+/g,' ').trim();
    }
    function usable(el){
      if(!el || el.disabled || el.getAttribute('aria-disabled')==='true') return false;
      var style=getComputedStyle(el), rect=el.getBoundingClientRect();
      return !el.hidden && style.display!=='none' && style.visibility!=='hidden' && (rect.width>0 || rect.height>0);
    }
    var input=null;
    for(var i=0;i<selectors.length && !input;i++){
      var candidate=null; try{candidate=document.querySelector(selectors[i]);}catch(ignore){}
      if(usable(candidate)) input=candidate;
    }
    if(!input) return JSON.stringify({success:false,error:'Kimi message editor disappeared before Send'});
    var actual=normalise(input.innerText || input.textContent || input.value || '');
    var wanted=normalise(expected);
    return JSON.stringify({
      success: actual===wanted,
      error: actual===wanted ? '' : 'Kimi editor content changed or duplicated before Send',
      value: actual
    });
  }catch(e){return JSON.stringify({success:false,error:String(e)});}
})()
""".trimIndent()
    }

    private fun buildSubmitScript(platform: AiPlatformDefinition): String {
        val inputs = JSONArray(platform.inputSelectors).toString()
        val buttons = JSONArray(platform.sendButtonSelectors).toString()
        return """
(function(){
  try{
    var inputSelectors=$inputs, buttonSelectors=$buttons;
    function roots(){
      var result=[document], queue=[document];
      while(queue.length){
        var root=queue.shift(), all=root.querySelectorAll ? root.querySelectorAll('*') : [];
        for(var i=0;i<all.length;i++) if(all[i].shadowRoot){ result.push(all[i].shadowRoot); queue.push(all[i].shadowRoot); }
      }
      return result;
    }
    function usable(el){
      if(!el || el.disabled || el.getAttribute('aria-disabled')==='true') return false;
      var style=getComputedStyle(el);
      return !el.hidden && style.display!=='none' && style.visibility!=='hidden';
    }
    function first(selectors){
      var rs=roots();
      for(var s=0;s<selectors.length;s++) for(var r=0;r<rs.length;r++){
        var el=rs[r].querySelector(selectors[s]); if(usable(el)) return el;
      }
      return null;
    }
    var button=first(buttonSelectors);
    if(!button){
      var rs=roots();
      for(var r=0;r<rs.length && !button;r++){
        var candidates=[]; try{candidates=rs[r].querySelectorAll('button,[role="button"]');}catch(ignore){}
        for(var c=0;c<candidates.length;c++){
          var label=((candidates[c].innerText||candidates[c].textContent||'')+' '+(candidates[c].getAttribute('aria-label')||'')).trim();
          if(usable(candidates[c]) && /^(send|发送)$/i.test(label)){button=candidates[c];break;}
        }
      }
    }
    if(button){
      try{ button.focus(); }catch(ignore){}
      try{ button.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,cancelable:true})); }catch(ignore){}
      try{ button.dispatchEvent(new PointerEvent('pointerup',{bubbles:true,cancelable:true})); }catch(ignore){}
      button.click();
      return JSON.stringify({success:true,method:'button'});
    }
    var input=first(inputSelectors);
    if(!input) return JSON.stringify({success:false,error:'Neither send button nor message input is available'});
    input.focus();
    input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));
    input.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));
    return JSON.stringify({success:true,method:'enter'});
  }catch(e){ return JSON.stringify({success:false,error:String(e)}); }
})()
""".trimIndent()
    }

    private fun buildSnapshotScript(platform: AiPlatformDefinition): String {
        val inputs = JSONArray(platform.inputSelectors).toString()
        val replies = JSONArray(platform.assistantMessageSelectors).toString()
        val loading = JSONArray(platform.loadingSelectors).toString()
        return """
(function(){
  try{
    var inputSelectors=$inputs, replySelectors=$replies, loadingSelectors=$loading;
    var fallbackSelectors=[
      '[data-role="assistant"]',
      '[data-message-author-role="assistant"]',
      '[data-testid*="assistant"]',
      '[class*="assistant-message"]',
      '[class*="message-assistant"]',
      '.ds-markdown', '.qwen-markdown', '.markdown-body', '.prose'
    ];
    function roots(){
      var result=[document], queue=[document];
      while(queue.length){
        var root=queue.shift(), all=root.querySelectorAll ? root.querySelectorAll('*') : [];
        for(var i=0;i<all.length;i++) if(all[i].shadowRoot){ result.push(all[i].shadowRoot); queue.push(all[i].shadowRoot); }
      }
      return result;
    }
    function visible(el){
      if(!el) return false;
      var style=getComputedStyle(el), rect=el.getBoundingClientRect();
      return !el.hidden && style.display!=='none' && style.visibility!=='hidden' && rect.width>0 && rect.height>0;
    }
    function isUserMessage(el){
      return !!(el.closest && el.closest(
        '[data-role="user"],[data-message-author-role="user"],[data-testid*="user"],'+
        '[class*="user-message"],[class*="message-user"],[class*="human-message"]'
      ));
    }
    function collectElements(selectors){
      var rs=roots(), elements=[], seen=new Set();
      for(var s=0;s<selectors.length;s++) for(var r=0;r<rs.length;r++){
        var nodes=[];
        try{ nodes=rs[r].querySelectorAll(selectors[s]); }catch(ignore){}
        for(var n=0;n<nodes.length;n++){
          var el=nodes[n];
          if(!seen.has(el) && visible(el) && !isUserMessage(el) &&
             el.tagName!=='TEXTAREA' && el.tagName!=='INPUT' && !el.isContentEditable){
            seen.add(el); elements.push(el);
          }
        }
      }
      elements.sort(function(a,b){
        if(a===b) return 0;
        var relation=a.compareDocumentPosition ? a.compareDocumentPosition(b) : 0;
        if(relation & Node.DOCUMENT_POSITION_FOLLOWING) return -1;
        if(relation & Node.DOCUMENT_POSITION_PRECEDING) return 1;
        return a.getBoundingClientRect().top-b.getBoundingClientRect().top;
      });
      return elements;
    }
    var rs=roots(), inputFound=false, isLoading=false;
    for(var s=0;s<inputSelectors.length;s++) for(var r=0;r<rs.length;r++){
      var input=null; try{ input=rs[r].querySelector(inputSelectors[s]); }catch(ignore){}
      if(visible(input)) inputFound=true;
    }
    for(var s=0;s<loadingSelectors.length;s++) for(var r=0;r<rs.length;r++){
      var nodes=[]; try{ nodes=rs[r].querySelectorAll(loadingSelectors[s]); }catch(ignore){}
      for(var n=0;n<nodes.length;n++) if(visible(nodes[n])) isLoading=true;
    }
    var elements=collectElements(replySelectors);
    if(elements.length===0) elements=collectElements(fallbackSelectors);
    var messages=[], seenText=new Set();
    for(var i=0;i<elements.length;i++){
      var text=(elements[i].innerText || elements[i].textContent || '').replace(/\u00a0/g,' ').trim();
      if(text && !seenText.has(text)){ seenText.add(text); messages.push(text); }
    }
    var body=(document.body && document.body.innerText || '').toLowerCase();
    var authBlocked=false;
    var authSelectors=['[role="dialog"]','[class*="login-modal" i]','[class*="signin-modal" i]','[class*="login-dialog" i]'];
    for(var a=0;a<authSelectors.length && !authBlocked;a++){
      var authNodes=[]; try{authNodes=document.querySelectorAll(authSelectors[a]);}catch(ignore){}
      for(var ai=0;ai<authNodes.length;ai++){
        if(!visible(authNodes[ai])) continue;
        var authText=(authNodes[ai].innerText || authNodes[ai].textContent || '').toLowerCase();
        if(/登录|登錄|sign in|log in|continue with google|phone number|手机号|手機號/.test(authText)){
          authBlocked=true; break;
        }
      }
    }
    return JSON.stringify({
      text: messages.length ? messages[messages.length-1] : '',
      count: messages.length,
      loading: isLoading,
      inputFound: inputFound,
      loginLikely: /登录|登入|sign in|log in|验证码|验证|verify your account/.test(body),
      authBlocked: authBlocked,
      url: location.href
    });
  }catch(e){ return JSON.stringify({error:String(e),url:location.href}); }
})()
""".trimIndent()
    }

    fun getExtractScript(): String = """
(function(){
  try{
    var selectors=['[data-role="assistant"]','[data-message-author-role="assistant"]','[data-testid*="assistant"]','.ds-markdown','.qwen-markdown','.markdown-body','.ai-message','.bot-message'];
    var messages=[], seen=new Set();
    selectors.forEach(function(selector){
      document.querySelectorAll(selector).forEach(function(el){
        if(el.tagName==='TEXTAREA' || el.tagName==='INPUT' || el.isContentEditable) return;
        var text=(el.innerText || el.textContent || '').trim();
        if(text && !seen.has(text)){ seen.add(text); messages.push(text); }
      });
    });
    return JSON.stringify({success:true,title:document.title,url:location.href,messages:messages,count:messages.length});
  }catch(e){ return JSON.stringify({success:false,error:String(e)}); }
})()
""".trimIndent()

    fun getDiagnoseScript(): String = """
(function(){
  try{
    var r={title:document.title,url:location.href,readyState:document.readyState,userAgent:navigator.userAgent};
    r.textareas=document.querySelectorAll('textarea').length;
    r.contenteditables=document.querySelectorAll('[contenteditable="true"]').length;
    r.buttons=document.querySelectorAll('button').length;
    r.inputs=document.querySelectorAll('input').length;
    r.buttonTexts=[];
    document.querySelectorAll('button').forEach(function(b){ var t=(b.innerText||b.textContent||'').trim(); if(t && t.length<50) r.buttonTexts.push(t); });
    return JSON.stringify(r);
  }catch(e){ return JSON.stringify({error:String(e)}); }
})()
""".trimIndent()

    fun injectJs(webView: WebView, script: String, callback: ((String) -> Unit)? = null) {
        webView.evaluateJavascript(script) { raw ->
            callback?.invoke(decodeJavascriptValue(raw))
        }
    }

    fun autoSendMessage(
        webView: WebView,
        message: String,
        callback: ((Boolean, String) -> Unit)? = null
    ) {
        webView.evaluateJavascript("(function(){return location.href})()") { rawUrl ->
            val platform = AiPlatformRegistry.detect(decodeJavascriptValue(rawUrl))
            sendAndAwaitReply(platform.id, webView, message) { result ->
                callback?.invoke(
                    result.success,
                    if (result.success) result.response else result.detail
                )
            }
        }
    }

    fun extractChat(webView: WebView, callback: ((String) -> Unit)? = null) {
        webView.evaluateJavascript("(function(){return location.href})()") { rawUrl ->
            val platform = AiPlatformRegistry.detect(decodeJavascriptValue(rawUrl))
            val source = loadSlateFiller(webView)
            if (source == null) {
                callback?.invoke("Could not load the semantic extraction engine")
                return@evaluateJavascript
            }
            val options = JSONObject().apply {
                put("replySelectors", JSONArray(platform.assistantMessageSelectors))
            }
            webView.evaluateJavascript(source) {
                val script = "(function(){return window.__extractBestReply ? window.__extractBestReply(${options}) : JSON.stringify({success:false,error:'Semantic extractor unavailable'});})()"
                webView.evaluateJavascript(script) { raw ->
                    val decoded = decodeJavascriptValue(raw)
                    val json = runCatching { JSONObject(decoded) }.getOrNull()
                    val result = if (json?.optBoolean("success", false) == true) {
                        json.optString("markdown").ifBlank { json.optString("text") }
                    } else {
                        json?.optString("error")?.takeIf { it.isNotBlank() }
                            ?: "No assistant reply could be identified on this page"
                    }
                    callback?.invoke(result)
                }
            }
        }
    }

    // Compatibility entry points retained for existing callers.
    fun sendToDoubao(webView: WebView, text: String, onResult: (String) -> Unit) {
        sendAndAwaitReply("doubao", webView, text) { result ->
            onResult(if (result.success) "REPLY:${result.response}" else "ERROR:${result.detail}")
        }
    }

    fun fillAndSend(tag: String, webView: WebView, text: String, onResult: (String) -> Unit) {
        sendAndAwaitReply(tag, webView, text) { result ->
            onResult(if (result.success) "REPLY:${result.response}" else "ERROR:${result.detail}")
        }
    }

    fun getAutoChatScript(message: String): String =
        buildFillScript(AiPlatformRegistry.generic(), message)

    private fun evaluateJson(
        webView: WebView,
        script: String,
        handle: AutomationHandle,
        callback: (JSONObject?) -> Unit
    ) {
        if (handle.isCancelled()) return
        webView.evaluateJavascript(script) { raw ->
            if (handle.isCancelled()) return@evaluateJavascript
            val decoded = decodeJavascriptValue(raw)
            callback(runCatching { JSONObject(decoded) }.getOrNull())
        }
    }

    private fun decodeJavascriptValue(raw: String?): String {
        if (raw == null || raw == "null" || raw == "undefined") return ""
        return if (raw.startsWith('"') && raw.endsWith('"')) {
            runCatching { JSONArray("[$raw]").getString(0) }.getOrElse {
                raw.substring(1, raw.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\r", "\r")
                    .replace("\\\\", "\\")
                    .replace(Regex("\\\\u([0-9a-fA-F]{4})")) {
                        it.groupValues[1].toInt(16).toChar().toString()
                    }
            }
        } else { raw }
    }
}
