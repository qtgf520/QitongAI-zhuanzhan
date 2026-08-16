package com.qtwl.YitongAIzhuanzhan.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.qtwl.YitongAIzhuanzhan.AiPlatformRegistry
import com.qtwl.YitongAIzhuanzhan.AutoChatTaskQueue
import com.qtwl.YitongAIzhuanzhan.BookmarkManager
import com.qtwl.YitongAIzhuanzhan.JsInjector
import com.qtwl.YitongAIzhuanzhan.MultiAiPipelineRunner
import com.qtwl.YitongAIzhuanzhan.PipelineRunState
import com.qtwl.YitongAIzhuanzhan.PipelineSnapshot
import com.qtwl.YitongAIzhuanzhan.R
import com.qtwl.YitongAIzhuanzhan.WebViewManager
import com.qtwl.YitongAIzhuanzhan.ui.components.GlassCard
import com.qtwl.YitongAIzhuanzhan.ui.theme.*

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(onNavigateToAbout: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background == GlassBackgroundDark
    val context = LocalContext.current
    val newTabLabel = stringResource(R.string.new_tab)
    val bookmarkedMessage = stringResource(R.string.bookmarked)
    val removedBookmarkFormat = stringResource(R.string.removed_bookmark, "__BOOKMARK_NAME__")
    val defaultsRestoredMessage = stringResource(R.string.default_bookmarks_restored)

    var refreshKey by remember { mutableIntStateOf(0) }
    val onStateChange: () -> Unit = remember { { refreshKey++ } }

    var urlInput by remember { mutableStateOf("") }
    var showUrlBar by remember { mutableStateOf(false) }
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }
    var showAiExtract by remember { mutableStateOf(false) }
    var aiMessage by remember { mutableStateOf("") }
    var extractedContent by remember { mutableStateOf("") }
    var showPipelineDialog by remember { mutableStateOf(false) }
    var pipelinePrompt by remember { mutableStateOf("") }
    var pipelineOrder by remember {
        mutableStateOf(AiPlatformRegistry.supported.map { it.id })
    }
    var selectedPipelinePlatforms by remember {
        mutableStateOf(AiPlatformRegistry.supported.map { it.id }.toSet())
    }
    var pipelineSnapshot by remember { mutableStateOf(PipelineSnapshot.Idle) }
    val pipelineRunner = remember(context) {
        MultiAiPipelineRunner(context) { snapshot ->
            pipelineSnapshot = snapshot
        }
    }

    DisposableEffect(pipelineRunner) {
        WebViewManager.addListener(onStateChange)
        onDispose {
            WebViewManager.removeListener(onStateChange)
            pipelineRunner.cancel()
        }
    }

    // 确保至少有一个标签页
    remember {
        AutoChatTaskQueue.initialize(context)
        if (WebViewManager.getTabCount() == 0) {
            WebViewManager.createTab(context)
        }
        true
    }

    LaunchedEffect(Unit) {
        if (WebViewManager.getTabCount() > 0) {
            refreshKey++
        }
    }

    val currentTab = remember(refreshKey) { WebViewManager.getCurrentTab() }
    val tabs = remember(refreshKey) { WebViewManager.getTabs() }
    val currentIndex = remember(refreshKey) { WebViewManager.getCurrentIndex() }

    Scaffold(
        containerColor = Color.Transparent,
        // Top and bottom app bars own the system-bar insets explicitly.
        // Disable Scaffold insets so edge-to-edge Android versions do not
        // apply a second offset around the WebView content.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                Surface(
                    color = if (isDark) GlassBackgroundDark else GlassBackground,
                    tonalElevation = 0.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { showTabSwitcher = !showTabSwitcher },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isDark) GlassSurfaceDarkMode2 else GlassSurfaceDark
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Tab,
                                    contentDescription = stringResource(R.string.tabs),
                                    tint = if (isDark) AppleBlueLight else AppleBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(
    onClick = onNavigateToAbout,
    modifier = Modifier
        .size(32.dp)
        .clip(CircleShape)
        .background(
            if (isDark) GlassSurfaceDarkMode2 else GlassSurfaceDark
        )
) {
    Icon(
        imageVector = Icons.Outlined.Info,
        contentDescription = stringResource(R.string.about),
        tint = if (isDark) AppleBlueLight else AppleBlue,
        modifier = Modifier.size(20.dp)
    )
}
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (currentTab?.title?.isNotEmpty() == true) currentTab.title else stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) AppleLabelDark else AppleLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                            )
                            Text(
                                text = "${currentIndex + 1}/${tabs.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }

                Surface(
                    color = if (isDark) GlassBackgroundDark else GlassBackground,
                    tonalElevation = 0.dp
                ) {
                    GlassUrlBar(
                        url = currentTab?.url ?: "",
                        onUrlChange = { urlInput = it },
                        onGo = {
                            val finalUrl = if (urlInput.startsWith("http://") || urlInput.startsWith("https://")) urlInput
                            else "https://$urlInput"
                            currentTab?.webView?.loadUrl(finalUrl)
                            showUrlBar = false
                        },
                        isDark = isDark,
                        showUrlBar = showUrlBar,
                        onToggleUrlBar = { showUrlBar = !showUrlBar }
                    )
                }
                // 编辑URL时留出键盘空间
                if (showUrlBar) {
                    Spacer(Modifier.height(8.dp))
                }

                if (currentTab?.isLoading == true) {
                    LinearProgressIndicator(
                        progress = { (currentTab.progress) / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = AppleBlue,
                        trackColor = Color.Transparent,
                    )
                }
            }
        },
        bottomBar = {
            BottomNavigationBar(
                canGoBack = currentTab?.canGoBack ?: false,
                canGoForward = currentTab?.canGoForward ?: false,
                isLoading = currentTab?.isLoading ?: false,
                onBack = { currentTab?.webView?.goBack() },
                onForward = { currentTab?.webView?.goForward() },
                onRefresh = { currentTab?.webView?.reload() },
                onStop = { currentTab?.webView?.stopLoading() },
                onHome = { currentTab?.webView?.loadUrl("https://www.doubao.com") },
                onNewTab = {
                    WebViewManager.createTab(context)
                    refreshKey++
                },
                onBookmarks = { showBookmarks = !showBookmarks },
                onAiDialog = { showAiDialog = true },
                onPipeline = { showPipelineDialog = true },
                isDark = isDark
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding)
                .background(if (isDark) GlassBackgroundDark else GlassBackground)
        ) {
            // 顶部渐变
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                if (isDark) AppleBlue.copy(alpha = 0.05f) else AppleBlue.copy(alpha = 0.03f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // WebView 内容 - 占满整个空间
            currentTab?.let { tab ->
                key(tab.id) {
                    AndroidView(
                        factory = { ctx ->
                            val webView = requireNotNull(WebViewManager.initWebView(ctx, tab.id))
                            (webView.parent as? ViewGroup)?.removeView(webView)
                            webView
                        },
                        update = { webView ->
                            tab.webView = webView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 加载指示器
            if (currentTab?.isLoading == true && (currentTab.progress) < 30) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                    color = AppleBlue,
                    strokeWidth = 2.dp
                )
            }

            // 标签页切换器 - 全屏覆盖
            if (showTabSwitcher) {
                TabSwitcherOverlay(
                    tabs = tabs, currentIndex = currentIndex, isDark = isDark,
                    onSwitchTab = { index ->
                        WebViewManager.switchTab(index); showTabSwitcher = false; refreshKey++
                    },
                    onCloseTab = { index ->
                        WebViewManager.closeTab(index); refreshKey++
                    },
                    onNewTab = {
                        WebViewManager.createTab(context)
                        showTabSwitcher = false; refreshKey++
                    },
                    onDismiss = { showTabSwitcher = false }
                )
            }

            // 收藏夹覆盖层 - 全屏覆盖
            if (showBookmarks) {
                BookmarkOverlay(
                    bookmarks = BookmarkManager.getBookmarks(context), isDark = isDark,
                    onOpen = { bookmark ->
                        WebViewManager.createTab(context, bookmark.url)
                        showBookmarks = false; refreshKey++
                    },
                    onAdd = {
                        currentTab?.let { tab ->
                            BookmarkManager.addBookmark(context, tab.title.ifEmpty { newTabLabel }, tab.url)
                            Toast.makeText(context, bookmarkedMessage, Toast.LENGTH_SHORT).show()
                        }
                        showBookmarks = false
                    },
                    onRemove = { bookmark ->
                        BookmarkManager.removeBookmark(context, bookmark.url)
                        Toast.makeText(context, removedBookmarkFormat.replace("__BOOKMARK_NAME__", bookmark.name), Toast.LENGTH_SHORT).show()
                        refreshKey++
                    },
                    onReset = {
                        BookmarkManager.resetToDefault(context)
                        Toast.makeText(context, defaultsRestoredMessage, Toast.LENGTH_SHORT).show()
                        refreshKey++
                    },
                    onDismiss = { showBookmarks = false }
                )
            }

            // AI 注入对话框
            if (showAiDialog) {
                AiSendDialog(
                    message = aiMessage,
                    onMessageChange = { aiMessage = it },
                    onSend = {
                        currentTab?.webView?.let { wv ->
                            JsInjector.autoSendMessage(wv, aiMessage, callback = { success, detail ->
    Toast.makeText(context, detail, Toast.LENGTH_SHORT).show()
})
                        }
                        showAiDialog = false
                    },
                    onExtract = {
                        currentTab?.webView?.let { wv ->
                            JsInjector.extractChat(wv) { result ->
                                extractedContent = result; showAiExtract = true
                            }
                        }
                        showAiDialog = false
                    },
                    onDiagnose = {
                        currentTab?.webView?.let { wv ->
                            JsInjector.injectJs(wv, JsInjector.getDiagnoseScript()) { result ->
                                extractedContent = result; showAiExtract = true
                            }
                        }
                        showAiDialog = false
                    },
                    onDismiss = { showAiDialog = false }, isDark = isDark
                )
            }

            // 提取结果对话框
            if (showPipelineDialog) {
                MultiAiPipelineDialog(
                    prompt = pipelinePrompt,
                    onPromptChange = { pipelinePrompt = it },
                    orderedPlatformIds = pipelineOrder,
                    selectedPlatformIds = selectedPipelinePlatforms,
                    onTogglePlatform = { platformId ->
                        selectedPipelinePlatforms = if (platformId in selectedPipelinePlatforms) {
                            selectedPipelinePlatforms - platformId
                        } else {
                            selectedPipelinePlatforms + platformId
                        }
                    },
                    onMovePlatform = { fromIndex, toIndex ->
                        if (fromIndex in pipelineOrder.indices && toIndex in pipelineOrder.indices) {
                            val reordered = pipelineOrder.toMutableList()
                            val moved = reordered.removeAt(fromIndex)
                            reordered.add(toIndex, moved)
                            pipelineOrder = reordered
                        }
                    },
                    snapshot = pipelineSnapshot,
                    onStart = {
                        val selectedInOrder = pipelineOrder.filter { it in selectedPipelinePlatforms }
                        pipelineRunner.start(pipelinePrompt, selectedInOrder)
                    },
                    onCancel = { pipelineRunner.cancel() },
                    onDismiss = {
                        if (pipelineSnapshot.state != PipelineRunState.RUNNING) {
                            showPipelineDialog = false
                        }
                    },
                    isDark = isDark
                )
            }

            if (showAiExtract) {
                AiResultDialog(content = extractedContent, onDismiss = { showAiExtract = false }, isDark = isDark)
            }
        }
    }
}

@Composable
private fun TabSwitcherOverlay(
    tabs: List<com.qtwl.YitongAIzhuanzhan.WebViewTab>,
    currentIndex: Int, isDark: Boolean,
    onSwitchTab: (Int) -> Unit, onCloseTab: (Int) -> Unit,
    onNewTab: () -> Unit, onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 100.dp, start = 16.dp, end = 16.dp)
                .heightIn(max = 500.dp).verticalScroll(scrollState).clickable(enabled = false) {},
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = onNewTab, colors = ButtonDefaults.buttonColors(containerColor = AppleBlue),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.new_tab))
            }
            tabs.forEachIndexed { index, tab ->
                GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSwitchTab(index) },
                    shape = RoundedCornerShape(12.dp), elevation = if (index == currentIndex) 4.dp else 1.dp) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tab.title.ifEmpty { stringResource(R.string.new_tab) }, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                                color = if (isDark) AppleLabelDark else AppleLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(tab.url, style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (tabs.size > 1) {
                            IconButton(onClick = { onCloseTab(index) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = if (isDark) AppleGray2 else AppleGray, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassUrlBar(
    url: String, onUrlChange: (String) -> Unit, onGo: () -> Unit,
    isDark: Boolean, showUrlBar: Boolean, onToggleUrlBar: () -> Unit
) {
    Surface(
        color = if (isDark) GlassBackgroundDark else GlassBackground,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = AppleGreen)
            Spacer(Modifier.width(6.dp))
            if (showUrlBar) {
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    modifier = Modifier.weight(1f).height(42.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppleBlue.copy(alpha = 0.5f),
                        unfocusedBorderColor = if (isDark) GlassBorderDark else GlassBorder,
                        cursorColor = AppleBlue,
                        focusedTextColor = if (isDark) AppleLabelDark else AppleLabel,
                        unfocusedTextColor = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = onGo, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.go), tint = AppleBlue, modifier = Modifier.size(18.dp))
                }
            } else {
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (isDark) GlassSurfaceDarkMode2 else GlassSurfaceDark)
                        .clickable { onToggleUrlBar() }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onToggleUrlBar, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit), tint = if (isDark) AppleGray2 else AppleGray, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun BottomNavigationBar(
    canGoBack: Boolean, canGoForward: Boolean, isLoading: Boolean,
    onBack: () -> Unit, onForward: () -> Unit, onRefresh: () -> Unit, onStop: () -> Unit,
    onHome: () -> Unit, onNewTab: () -> Unit,
    onBookmarks: () -> Unit, onAiDialog: () -> Unit,
    onPipeline: () -> Unit,
    isDark: Boolean
) {
    Surface(
        color = if (isDark) GlassBackgroundDark else GlassBackground,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Eight actions overflow the old fixed-width toolbar on compact
            // phones. Fit and center them instead of starting a scroll row at
            // an apparently shifted horizontal position.
            val compact = maxWidth < 380.dp
            val buttonSize = if (compact) 36.dp else 42.dp
            val iconSize = if (compact) 20.dp else 22.dp
            val horizontalPadding = if (compact) 4.dp else 8.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavButton(Icons.AutoMirrored.Filled.ArrowBack, canGoBack, onBack, isDark, buttonSize, iconSize)
                NavButton(Icons.AutoMirrored.Filled.ArrowForward, canGoForward, onForward, isDark, buttonSize, iconSize)
                NavButton(
                    if (isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                    true,
                    if (isLoading) onStop else onRefresh,
                    isDark,
                    buttonSize,
                    iconSize
                )
                NavButton(Icons.Filled.Home, true, onHome, isDark, buttonSize, iconSize)
                NavButton(Icons.Filled.Add, true, onNewTab, isDark, buttonSize, iconSize)
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(
                            if (isDark) GlassBorderDark else GlassBorder,
                            RoundedCornerShape(1.dp)
                        )
                )
                NavButton(Icons.Outlined.Bookmarks, true, onBookmarks, isDark, buttonSize, iconSize)
                NavButton(Icons.AutoMirrored.Filled.Send, true, onAiDialog, isDark, buttonSize, iconSize)
                NavButton(Icons.Filled.AccountTree, true, onPipeline, isDark, buttonSize, iconSize)
            }
        }
    }
}

@Composable
private fun NavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    isDark: Boolean,
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp
) {
    val iconColor = if (enabled) {
        if (isDark) AppleLabelDark else AppleLabel
    } else {
        if (isDark) AppleGray.copy(alpha = 0.3f) else AppleGray2.copy(alpha = 0.3f)
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(buttonSize).clip(CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun AiSendDialog(
    message: String, onMessageChange: (String) -> Unit,
    onSend: () -> Unit, onExtract: () -> Unit, onDiagnose: () -> Unit,
    onDismiss: () -> Unit, isDark: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) GlassBackgroundDark else GlassBackground,
        titleContentColor = if (isDark) AppleLabelDark else AppleLabel,
        textContentColor = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SmartToy, contentDescription = null, tint = AppleBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ai_injection), fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column {
                OutlinedTextField(value = message, onValueChange = onMessageChange,
                    modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall,
                    placeholder = { Text(stringResource(R.string.message_hint)) }, minLines = 2, maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppleBlue.copy(alpha = 0.5f),
                        unfocusedBorderColor = if (isDark) GlassBorderDark else GlassBorder,
                        cursorColor = AppleBlue,
                        focusedTextColor = if (isDark) AppleLabelDark else AppleLabel,
                        unfocusedTextColor = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel
                    ), shape = RoundedCornerShape(10.dp))
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = onExtract) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.extract_chat), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onDiagnose) {
                        Icon(Icons.Outlined.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.diagnose_page), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSend, enabled = message.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AppleBlue), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.send))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun AiResultDialog(content: String, onDismiss: () -> Unit, isDark: Boolean) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) GlassBackgroundDark else GlassBackground,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DataObject, contentDescription = null, tint = AppleBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.extract_result), fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(scrollState)
                .background(if (isDark) GlassSurfaceDarkMode2 else GlassSurfaceDark, RoundedCornerShape(8.dp)).padding(12.dp)) {
                Text(text = content.ifEmpty { stringResource(R.string.no_data) }, style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) AppleLabelDark else AppleLabel)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = AppleBlue),
                shape = RoundedCornerShape(10.dp)) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
private fun BookmarkOverlay(
    bookmarks: List<com.qtwl.YitongAIzhuanzhan.Bookmark>,
    isDark: Boolean,
    onOpen: (com.qtwl.YitongAIzhuanzhan.Bookmark) -> Unit,
    onAdd: () -> Unit,
    onRemove: (com.qtwl.YitongAIzhuanzhan.Bookmark) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 100.dp, start = 16.dp, end = 16.dp)
                .heightIn(max = 500.dp).verticalScroll(scrollState).clickable(enabled = false) {},
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.bookmarks), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = if (isDark) AppleLabelDark else AppleLabel)
                TextButton(onClick = onAdd) {
                    Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.bookmark_current), style = MaterialTheme.typography.bodySmall)
                }
            }
            bookmarks.forEach { bookmark ->
                GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onOpen(bookmark) },
                    shape = RoundedCornerShape(10.dp), elevation = 1.dp) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Web, contentDescription = null, tint = AppleBlue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bookmark.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                                color = if (isDark) AppleLabelDark else AppleLabel)
                            Text(bookmark.url, style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onRemove(bookmark) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete), tint = if (isDark) AppleGray2 else AppleGray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onReset) {
                Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.restore_default), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}