/**
 * SlateFiller — 四级降级输入引擎
 * 通过 WebView.evaluateJavascript 注入（绕 CSP）
 */
(function (window) {
  if (window.__slateFiller) return;
  window.__slateFiller = true;

  // ===== 工具函数 =====

  // React Fiber 反查 Slate editor 实例
  function getSlateEditor(el) {
    const keys = Object.keys(el);
    const fiberKey = keys.find(k =>
      k.startsWith('__reactFiber') || k.startsWith('__reactInternalInstance')
    );
    if (!fiberKey) return null;
    let fiber = el[fiberKey];
    while (fiber) {
      if (fiber.stateNode && fiber.stateNode.props && fiber.stateNode.props.editor) {
        return fiber.stateNode.props.editor;
      }
      fiber = fiber.return;
    }
    return null;
  }

  // 递归穿透 Shadow DOM 查找
  function deepQuery(sel, root) {
    root = root || document;
    let results = [];
    const direct = root.querySelectorAll(sel);
    results.push(...direct);
    const all = root.querySelectorAll('*');
    all.forEach(el => {
      if (el.shadowRoot) {
        results = results.concat(deepQuery(sel, el.shadowRoot));
      }
    });
    return results;
  }

  // 找输入框（优先级：Slate > contenteditable > textarea）
  function findInput() {
    // Slate editor
    const slate = document.querySelector('[data-slate-editor]');
    if (slate) return { el: slate, type: 'slate' };

    // contenteditable
    const ce = document.querySelector('div[contenteditable="true"], [contenteditable=""]');
    if (ce) return { el: ce, type: 'contenteditable' };

    // textarea
    const ta = document.querySelector('textarea');
    if (ta) return { el: ta, type: 'textarea' };

    return null;
  }

  // ===== 四级输入策略 =====

  // 策略1：Slate editor.insertText
  function fillViaSlateEditor(el, text) {
    const editor = getSlateEditor(el);
    if (!editor) return false;
    try {
      if (editor.focus) editor.focus();
      editor.insertText(text);
      if (editor.onChange) editor.onChange();
      return true;
    } catch (e) {
      console.warn('[SlateFiller] slate insertText failed:', e);
      return false;
    }
  }

  // 策略2：Clipboard paste
  function fillViaPaste(el, text) {
    try {
      el.focus();
      const dataTransfer = new DataTransfer();
      dataTransfer.setData('text/plain', text);
      const pasteEvent = new ClipboardEvent('paste', {
        bubbles: true,
        cancelable: true,
        clipboardData: dataTransfer
      });
      const selectAll = new KeyboardEvent('keydown', {
        key: 'a', code: 'KeyA', ctrlKey: true, bubbles: true
      });
      el.dispatchEvent(selectAll);
      const delEvent = new KeyboardEvent('keydown', {
        key: 'Delete', code: 'Delete', bubbles: true
      });
      el.dispatchEvent(delEvent);
      const handled = el.dispatchEvent(pasteEvent);
      if (!handled) {
        document.execCommand('insertText', false, text);
      }
      return true;
    } catch (e) {
      console.warn('[SlateFiller] paste failed:', e);
      return false;
    }
  }

  // 策略3：beforeinput + input 事件
  function fillViaBeforeInput(el, text) {
    try {
      el.focus();
      if (el.setSelectionRange) {
        el.setSelectionRange(el.value.length, el.value.length);
      }
      const beforeInput = new InputEvent('beforeinput', {
        bubbles: true,
        cancelable: true,
        inputType: 'insertText',
        data: text,
        isComposing: false
      });
      const beforeResult = el.dispatchEvent(beforeInput);
      const inputEvent = new InputEvent('input', {
        bubbles: true,
        cancelable: true,
        inputType: 'insertText',
        data: text
      });
      el.dispatchEvent(inputEvent);
      if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') {
        const start = el.selectionStart || 0;
        const end = el.selectionEnd || 0;
        el.value = el.value.substring(0, start) + text + el.value.substring(end);
        el.dispatchEvent(new Event('change', { bubbles: true }));
      } else {
        document.execCommand('insertText', false, text);
      }
      return beforeResult;
    } catch (e) {
      console.warn('[SlateFiller] beforeinput failed:', e);
      return false;
    }
  }

  // 策略4：execCommand 兜底
  function fillViaExecCommand(el, text) {
    try {
      el.focus();
      document.execCommand('insertText', false, text);
      return true;
    } catch (e) {
      console.warn('[SlateFiller] execCommand failed:', e);
      return false;
    }
  }

  // ===== 主入口 =====
  window.__fillText = function (text) {
    const input = findInput();
    if (!input) return JSON.stringify({ ok: false, reason: 'NO_INPUT' });

    const { el, type } = input;

    if (type === 'slate') {
      if (fillViaSlateEditor(el, text)) {
        return JSON.stringify({ ok: true, method: 'slate_editor' });
      }
    }

    if (fillViaPaste(el, text)) {
      return JSON.stringify({ ok: true, method: 'paste' });
    }
    if (fillViaBeforeInput(el, text)) {
      return JSON.stringify({ ok: true, method: 'beforeinput' });
    }
    if (fillViaExecCommand(el, text)) {
      return JSON.stringify({ ok: true, method: 'execCommand' });
    }

    return JSON.stringify({ ok: false, reason: 'ALL_FAILED' });
  };

  // ===== 发送按钮 =====
  window.__clickSend = function () {
    const candidates = deepQuery('[aria-label*="发送" i], [aria-label*="send" i], [data-testid*="send" i], button[type="submit"]');
    const btn = candidates.find(el => {
      const txt = (el.textContent || '') + (el.getAttribute('aria-label') || '');
      return /发送|send|submit/i.test(txt);
    });

    if (btn) {
      const button = btn.tagName === 'BUTTON' ? btn : btn.closest('button');
      if (button) {
        button.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true }));
        button.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, cancelable: true }));
        button.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
        return JSON.stringify({ ok: true, method: 'button_click' });
      }
    }

    const input = findInput();
    if (input) {
      input.el.focus();
      input.el.dispatchEvent(new KeyboardEvent('keydown', {
        key: 'Enter', code: 'Enter', keyCode: 13,
        which: 13, bubbles: true, cancelable: true
      }));
      input.el.dispatchEvent(new KeyboardEvent('keyup', {
        key: 'Enter', code: 'Enter', keyCode: 13,
        which: 13, bubbles: true, cancelable: true
      }));
      return JSON.stringify({ ok: true, method: 'enter_key' });
    }

    return JSON.stringify({ ok: false, reason: 'NO_SEND_BUTTON' });
  };

  // ===== Semantic reply capture v3 =====
  // Capture only assistant candidates that are new or mutated after the request
  // baseline. Selectors are hints; role, recency and DOM mutation drive scoring.
  var Q_USER = [
    '[data-role="user"]','[data-message-author-role="user"]','[data-testid*="user" i]',
    '[class*="user-message" i]','[class*="message-user" i]','[class*="human-message" i]'
  ];
  var Q_ASSISTANT_FALLBACK = [
    '[data-role="assistant"]','[data-message-author-role="assistant"]',
    '[data-testid*="assistant" i]','[class*="assistant-message" i]',
    '[class*="message-assistant" i]','[class*="bot-message" i]',
    '.ds-markdown','.qwen-markdown','.markdown-body','.prose'
  ];

  function qVisible(el) {
    if (!el || !el.isConnected) return false;
    var st;
    try { st=getComputedStyle(el); } catch(e) { return false; }
    if (el.hidden || st.display==='none' || st.visibility==='hidden' || st.opacity==='0') return false;
    var r=el.getBoundingClientRect();
    return r.width>0 || r.height>0;
  }
  function qText(v) {
    return String(v||'').replace(/\u00a0/g,' ').replace(/\r\n?/g,'\n')
      .replace(/[ \t]+\n/g,'\n').replace(/\n[ \t]+/g,'\n').replace(/\n{3,}/g,'\n\n').trim();
  }
  function qClosest(el, sels) {
    if (!el || !el.closest) return null;
    for (var i=0;i<sels.length;i++) { try { var x=el.closest(sels[i]); if(x)return x; }catch(e){} }
    return null;
  }
  function qRoots() {
    var out=[document], queue=[document], seen=new Set([document]);
    while(queue.length){
      var root=queue.shift(), all=root.querySelectorAll?root.querySelectorAll('*'):[];
      for(var i=0;i<all.length;i++) if(all[i].shadowRoot&&!seen.has(all[i].shadowRoot)){
        seen.add(all[i].shadowRoot); out.push(all[i].shadowRoot); queue.push(all[i].shadowRoot);
      }
    }
    return out;
  }
  function qQuery(sel) {
    var out=[], seen=new Set(), rs=qRoots();
    for(var r=0;r<rs.length;r++){
      var nodes=[]; try{nodes=rs[r].querySelectorAll(sel);}catch(e){}
      for(var i=0;i<nodes.length;i++) if(!seen.has(nodes[i])){seen.add(nodes[i]);out.push(nodes[i]);}
    }
    return out;
  }
  function qLatest(sels) {
    var all=[], seen=new Set();
    sels.forEach(function(s){qQuery(s).forEach(function(el){if(!seen.has(el)&&qVisible(el)){seen.add(el);all.push(el);}});});
    all.sort(function(a,b){
      if(a===b)return 0; var p=a.compareDocumentPosition(b);
      if(p&Node.DOCUMENT_POSITION_FOLLOWING)return -1;
      if(p&Node.DOCUMENT_POSITION_PRECEDING)return 1; return 0;
    });
    return all.length?all[all.length-1]:null;
  }
  function qAfter(a,b) {
    if(!a||!b||a===b)return false;
    return !!(b.compareDocumentPosition(a)&Node.DOCUMENT_POSITION_FOLLOWING);
  }
  function qMarkdown(el) {
    function walk(n) {
      if(n.nodeType===Node.TEXT_NODE)return String(n.nodeValue||'').replace(/\u00a0/g,' ');
      if(n.nodeType!==Node.ELEMENT_NODE)return '';
      var t=n.tagName;
      if(['SCRIPT','STYLE','NOSCRIPT','SVG','BUTTON','FORM','INPUT','TEXTAREA'].indexOf(t)>=0)return '';
      if(n.getAttribute('aria-hidden')==='true')return '';
      if(t==='BR')return '\n';
      if(t==='HR')return '\n\n---\n\n';
      if(t==='PRE'){
        var c=(n.textContent||'').replace(/\r\n?/g,'\n').replace(/\n+$/,'');
        var code=n.querySelector('code'), lang='';
        if(code){var m=String(code.className||'').match(/(?:language-|lang-)([\w.+#-]+)/i);if(m)lang=m[1];}
        return '\n\n```'+lang+'\n'+c+'\n```\n\n';
      }
      if(t==='CODE'&&(!n.parentElement||n.parentElement.tagName!=='PRE')){
        var c=qText(n.textContent); return c?'`'+c.replace(/`/g,'\\`')+'`':'';
      }
      if(/^H[1-6]$/.test(t))return '\n\n'+'#'.repeat(Number(t.slice(1)))+' '+qText(n.textContent)+'\n\n';
      if(t==='A'){
        var txt=children(n).trim()||qText(n.textContent), href=n.getAttribute('href')||'';
        return href&&!href.startsWith('javascript:')?'['+txt+']('+href+')':txt;
      }
      if(t==='LI'){
        var p=n.parentElement, ordered=p&&p.tagName==='OL';
        var prefix=ordered?(Array.from(p.children).indexOf(n)+1)+'. ':'- ';
        return prefix+children(n).trim().replace(/\n+/g,'\n  ')+'\n';
      }
      if(t==='UL'||t==='OL')return '\n'+children(n).trimEnd()+'\n';
      if(t==='BLOCKQUOTE')return '\n\n'+children(n).trim().split('\n').map(function(x){return '> '+x;}).join('\n')+'\n\n';
      if(t==='TABLE'){
        var rows=Array.from(n.querySelectorAll('tr')).map(function(r){
          return Array.from(r.querySelectorAll('th,td')).map(function(c){return qText(c.textContent).replace(/\|/g,'\\|').replace(/\n/g,' ');});
        }).filter(function(r){return r.length;});
        if(!rows.length)return '';
        var w=Math.max.apply(Math,rows.map(function(r){return r.length;}));
        rows.forEach(function(r){while(r.length<w)r.push('');});
        var out=['| '+rows[0].join(' | ')+' |','| '+new Array(w).fill('---').join(' | ')+' |'];
        for(var i=1;i<rows.length;i++)out.push('| '+rows[i].join(' | ')+' |');
        return '\n\n'+out.join('\n')+'\n\n';
      }
      var x=children(n);
      if(['P','DIV','SECTION','ARTICLE','MAIN'].indexOf(t)>=0)return '\n\n'+x.trim()+'\n\n';
      return x;
    }
    function children(n){var o='';for(var i=0;i<n.childNodes.length;i++)o+=walk(n.childNodes[i]);return o;}
    return walk(el).replace(/[ \t]+\n/g,'\n').replace(/\n{3,}/g,'\n\n').trim()||qText(el.innerText||el.textContent);
  }
  function qCandidates(replySelectors) {
    var sels=(replySelectors||[]).concat(Q_ASSISTANT_FALLBACK), out=[], seen=new Set();
    for(var s=0;s<sels.length;s++)qQuery(sels[s]).forEach(function(el){
      if(seen.has(el)||!qVisible(el)||el.tagName==='TEXTAREA'||el.tagName==='INPUT'||el.isContentEditable)return;
      if(qClosest(el,Q_USER))return;
      var text=qText(el.innerText||el.textContent); if(!text)return;
      seen.add(el); out.push({el:el,text:text,platform:s<(replySelectors||[]).length});
    });
    return out;
  }
  function qAssistantRole(el) {
    return !!qClosest(el,['[data-role="assistant"]','[data-message-author-role="assistant"]',
      '[data-testid*="assistant" i]','[class*="assistant-message" i]',
      '[class*="message-assistant" i]','[class*="bot-message" i]']);
  }
  function qChrome(el) {
    return !!qClosest(el,['nav','aside','footer','[role="navigation"]',
      '[class*="sidebar" i]','[class*="toolbar" i]','[class*="menu" i]']);
  }
  function qTransient(el) {
    return !!qClosest(el,['[role="alert"]','[class*="toast" i]',
      '[class*="snackbar" i]','[class*="notification" i]']);
  }
  function qDepth(el) {
    var depth=0;
    for(var n=el;n&&n.parentElement;n=n.parentElement)depth++;
    return depth;
  }
  function qAfterTurn(el,anchor) {
    if(!el||!anchor||el===anchor)return false;
    if(qAfter(el,anchor))return true;
    try{
      var er=el.getBoundingClientRect(), ar=anchor.getBoundingClientRect();
      return er.top >= ar.bottom-4;
    }catch(e){return false;}
  }

  function qManualScore(c, latestUser) {
    var n=0,why=[];
    if(qAssistantRole(c.el)){n+=100;why.push('assistant-role:+100');}
    if(c.platform){n+=70;why.push('platform-selector:+70');}
    if(latestUser&&qAfter(c.el,latestUser)){n+=40;why.push('after-user:+40');}
    n+=15;why.push('visible:+15');
    if(c.text.length>=40){n+=10;why.push('substantial:+10');}
    if(qChrome(c.el)){n-=80;why.push('page-chrome:-80');}
    return {el:c.el,text:c.text,markdown:qMarkdown(c.el),score:n,reasons:why};
  }

  window.__extractBestReply=function(options){
    try{
      options=options||{};
      var reply=Array.isArray(options.replySelectors)?options.replySelectors:[];
      var latestUser=qLatest(Q_USER);
      var ranked=qCandidates(reply).map(function(c){return qManualScore(c,latestUser);});
      ranked.sort(function(x,y){
        if(y.score!==x.score)return y.score-x.score;
        var p=x.el.compareDocumentPosition(y.el);
        if(p&Node.DOCUMENT_POSITION_FOLLOWING)return 1;
        if(p&Node.DOCUMENT_POSITION_PRECEDING)return -1;
        return y.text.length-x.text.length;
      });
      var best=ranked.length?ranked[0]:null;
      return JSON.stringify({
        success:!!best,
        url:location.href,
        title:document.title||'',
        markdown:best?(best.markdown||best.text):'',
        text:best?best.text:'',
        score:best?best.score:0,
        reasons:best?best.reasons:[],
        candidates:ranked.slice(0,5).map(function(x){return{score:x.score,length:x.text.length,reasons:x.reasons};})
      });
    }catch(e){return JSON.stringify({success:false,url:location.href,error:String(e)});}
  };

  window.__replyWatchers=window.__replyWatchers||{};
  window.__cancelReplyWatcher=function(requestId){
    var k=String(requestId),w=window.__replyWatchers[k]; if(!w)return false;
    if(w.timer)clearInterval(w.timer); if(w.observer)w.observer.disconnect();
    delete window.__replyWatchers[k]; return true;
  };

  window.__watchReply=function(options){
    options=options||{};
    var id=String(options.requestId||Date.now()); window.__cancelReplyWatcher(id);
    var reply=Array.isArray(options.replySelectors)?options.replySelectors:[];
    var loading=Array.isArray(options.loadingSelectors)?options.loadingSelectors:[];
    var sent=qText(options.sentMessage||''), baselineText=qText(options.baselineText||'');
    var deadline=Date.now()+Math.max(10000,Number(options.timeoutMs||150000));
    var baseline=qCandidates(reply), baseNodes=new WeakSet(), baseTexts=new Set();
    baseline.forEach(function(c){baseNodes.add(c.el);baseTexts.add(c.text);}); if(baselineText)baseTexts.add(baselineText);
    var latestUser=qLatest(Q_USER), touched=new WeakSet(), recent=[], recentSeen=new WeakSet(), promptAnchor=null,
      lastMutation=Date.now(), lastContentChange=Date.now(), last='',stable=0,diag=null,done=false;

    function touchedNear(el){for(var i=0,n=el;n&&i<8;i++,n=n.parentElement)if(touched.has(n))return true;return false;}
    function markRecent(el){
      if(!el||el.nodeType!==Node.ELEMENT_NODE)return;
      touched.add(el);
      if(!recentSeen.has(el)){recentSeen.add(el);recent.push(el);}
      if(recent.length>500)recent=recent.filter(function(x){return x&&x.isConnected;}).slice(-350);
    }
    function promptTextMatches(text){
      text=qText(text);
      if(!sent||!text)return false;
      if(text===sent)return true;
      return sent.length>=4 && text.indexOf(sent)>=0 && text.length<=sent.length+80;
    }
    function findPromptAnchor(){
      var known=qLatest(Q_USER);
      if(known&&promptTextMatches(qMarkdown(known)))return known;
      var best=null,bestDepth=-1;
      for(var i=recent.length-1;i>=0;i--){
        for(var n=recent[i],h=0;n&&h<7;h++,n=n.parentElement){
          if(!n.isConnected||!qVisible(n)||n.isContentEditable||n.tagName==='INPUT'||n.tagName==='TEXTAREA'||n.tagName==='BUTTON')continue;
          if(qChrome(n)||qTransient(n))continue;
          if(!promptTextMatches(qMarkdown(n)))continue;
          var d=qDepth(n); if(d>bestDepth){best=n;bestDepth=d;}
        }
      }
      return best;
    }
    function recentCandidates(){
      var out=[],seen=new Set(),anchor=promptAnchor;
      if(!anchor)return out;
      for(var i=0;i<recent.length;i++){
        for(var n=recent[i],h=0;n&&h<8;h++,n=n.parentElement){
          if(seen.has(n)||!n.isConnected||!qVisible(n))continue;
          seen.add(n);
          if(n===document.body||n===document.documentElement||n===anchor||(n.contains&&n.contains(anchor)))continue;
          if(n.isContentEditable||['INPUT','TEXTAREA','BUTTON','FORM','NAV','ASIDE','FOOTER','SCRIPT','STYLE'].indexOf(n.tagName)>=0)continue;
          if(qChrome(n)||qTransient(n)||qClosest(n,Q_USER))continue;
          if(!qAfterTurn(n,anchor))continue;
          var markdown=qMarkdown(n),text=qText(markdown);
          if(!text||text===sent||text.length>30000)continue;
          out.push({el:n,text:text,platform:false,dynamic:true,depth:qDepth(n)});
        }
      }
      return out;
    }
    function isLoading(){
      for(var s=0;s<loading.length;s++){var ns=qQuery(loading[s]);for(var i=0;i<ns.length;i++)if(qVisible(ns[i]))return true;}
      return false;
    }
    function score(c){
      var changed=touched.has(c.el)||touchedNear(c.el), fresh=!baseNodes.has(c.el), duplicate=baseTexts.has(c.text);
      var role=qAssistantRole(c.el), afterAnchor=promptAnchor&&qAfterTurn(c.el,promptAnchor);
      var n=0,why=[];
      if(role){n+=100;why.push('assistant-role:+100');}
      if(c.platform){n+=70;why.push('platform-selector:+70');}
      if(c.dynamic){n+=30;why.push('post-send-mutation:+30');}
      if(fresh){n+=40;why.push('new-node:+40');}
      if(changed){n+=25;why.push('mutated:+25');}
      if(latestUser&&qAfterTurn(c.el,latestUser)){n+=30;why.push('after-user:+30');}
      if(afterAnchor){n+=50;why.push('after-prompt-anchor:+50');}
      n+=15; why.push('visible:+15');
      if(c.text.length>=40){n+=10;why.push('substantial:+10');}
      if(qChrome(c.el)){n-=80;why.push('page-chrome:-80');}
      if(qTransient(c.el)){n-=100;why.push('transient-ui:-100');}
      if(duplicate){n-=60;why.push('baseline-text:-60');}
      if(sent&&c.text===sent){n-=120;why.push('prompt-echo:-120');}
      var qualified=role||c.platform||(!!c.dynamic&&!!afterAnchor);
      return {el:c.el,text:c.text,markdown:qMarkdown(c.el),score:n,newReply:fresh||changed||!duplicate,
        qualified:qualified,dynamic:!!c.dynamic,depth:c.depth||qDepth(c.el),reasons:why};
    }
    function best(){
      var candidates=qCandidates(reply),byEl=new Set(candidates.map(function(c){return c.el;}));
      recentCandidates().forEach(function(c){if(!byEl.has(c.el)){byEl.add(c.el);candidates.push(c);}});
      var a=candidates.map(score).filter(function(c){return c.qualified&&c.newReply&&c.text&&c.text!==sent;});
      a.sort(function(x,y){
        if(y.score!==x.score)return y.score-x.score;
        if(y.text.length!==x.text.length)return y.text.length-x.text.length;
        if(y.depth!==x.depth)return y.depth-x.depth;
        var p=x.el.compareDocumentPosition(y.el);
        if(p&Node.DOCUMENT_POSITION_FOLLOWING)return 1;
        if(p&Node.DOCUMENT_POSITION_PRECEDING)return -1; return 0;
      });
      var out=[],seen=new Set(); for(var i=0;i<a.length;i++){var k=qText(a[i].text);if(!seen.has(k)){seen.add(k);out.push(a[i]);}}
      return {best:out[0]||null,top:out.slice(0,5)};
    }
    function status(d){try{if(window.Android&&window.Android.onStatus)window.Android.onStatus('CAPTURE_DIAG '+JSON.stringify(d));}catch(e){}}
    function finish(c){
      if(done)return;done=true;window.__cancelReplyWatcher(id);diag.result='complete';status(diag);
      try{if(window.Android&&window.Android.onReplyForRequest)window.Android.onReplyForRequest(id,c.markdown||c.text);}catch(e){}
    }
    function check(){
      if(done)return;
      if(Date.now()>=deadline){status(diag||{requestId:id,result:'timeout',baselineCandidates:baseline.length});window.__cancelReplyWatcher(id);return;}
      latestUser=qLatest(Q_USER)||latestUser;
      if(!promptAnchor)promptAnchor=findPromptAnchor();
      var r=best(),c=r.best,load=isLoading(),current=c?qText(c.markdown||c.text):'';
      if(c&&current&&current===last){
        stable++;
      }else{
        if(current!==last)lastContentChange=Date.now();
        last=current;
        stable=0;
      }
      var contentQuiet=Date.now()-lastContentChange, pageQuiet=Date.now()-lastMutation;
      diag={requestId:id,result:'watching',score:c?c.score:0,textLength:current.length,stablePolls:stable,
        quietForMs:contentQuiet,pageQuietForMs:pageQuiet,loading:load,baselineCandidates:baseline.length,candidateCount:r.top.length,
        promptAnchorFound:!!promptAnchor,dynamicFallback:!!(c&&c.dynamic),reasons:c?c.reasons:[],
        topCandidates:r.top.map(function(x){return{score:x.score,length:x.text.length,dynamic:x.dynamic,tag:x.el.tagName,
          cls:String(x.el.className||'').slice(0,120),reasons:x.reasons};})};
      var dynamicOnly=c&&c.dynamic&&!qAssistantRole(c.el)&&!c.platform;
      var requiredStable=dynamicOnly?4:2, requiredQuiet=dynamicOnly?2600:1600;
      if(c&&c.score>=110&&stable>=requiredStable&&contentQuiet>=requiredQuiet&&(!load||contentQuiet>=4500))finish(c);
    }
    var observedRoots=new WeakSet();
    var observer=new MutationObserver(function(ms){
      lastMutation=Date.now();
      ms.forEach(function(m){
        var t=m.target&&m.target.nodeType===Node.ELEMENT_NODE?m.target:m.target&&m.target.parentElement;if(t)markRecent(t);
        Array.from(m.addedNodes||[]).forEach(function(n){if(n.nodeType===Node.ELEMENT_NODE)markRecent(n);});
      });
      observeKnownRoots();
      setTimeout(check,120);
    });
    function observeKnownRoots(){
      qRoots().forEach(function(root){
        var target=root===document?document.documentElement:root;
        if(!target||observedRoots.has(target))return;
        try{observer.observe(target,{childList:true,subtree:true,characterData:true});observedRoots.add(target);}catch(e){}
      });
    }
    observeKnownRoots();
    window.__replyWatchers[id]={observer:observer,timer:setInterval(check,450)};check();return true;
  };

  window.__captureDiagnostics=function(){
    try{return JSON.stringify({url:location.href,title:document.title,candidates:qCandidates([]).slice(-10).map(function(c){return{tag:c.el.tagName,text:c.text.slice(0,200)};})});}
    catch(e){return JSON.stringify({url:location.href,error:String(e)});}
  };
  console.log('[SlateFiller] ready');

})(window);
