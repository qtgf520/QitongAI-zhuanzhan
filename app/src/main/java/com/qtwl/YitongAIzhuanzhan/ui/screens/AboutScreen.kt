package com.qtwl.YitongAIzhuanzhan.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qtwl.YitongAIzhuanzhan.AppHider
import com.qtwl.YitongAIzhuanzhan.BookmarkManager
import com.qtwl.YitongAIzhuanzhan.BrowserBrainConfig
import com.qtwl.YitongAIzhuanzhan.GatewayPrefs
import com.qtwl.YitongAIzhuanzhan.GatewayService
import com.qtwl.YitongAIzhuanzhan.IpHelper
import com.qtwl.YitongAIzhuanzhan.LocaleManager
import com.qtwl.YitongAIzhuanzhan.R
import com.qtwl.YitongAIzhuanzhan.ui.components.GlassCard
import com.qtwl.YitongAIzhuanzhan.ui.theme.*
import androidx.compose.material3.ripple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onLanguageChanged: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToBookmarks: () -> Unit = {},
    onNavigateToMcpBrowser: () -> Unit = {}
) {
    val isDark = MaterialTheme.colorScheme.background == GlassBackgroundDark
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val currentLang = remember { LocaleManager.getLanguageIndex(context) }
    val restoredDefaultMessage = stringResource(R.string.restored_default)
    val qqGroupCopiedMessage = stringResource(R.string.qq_group_copied)
    val qqGroupClipLabel = stringResource(R.string.qq_group_number_clip_label)
    var showBrainConfig by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.about_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) GlassBackgroundDark else GlassBackground,
                    titleContentColor = if (isDark) AppleLabelDark else AppleLabel,
                    navigationIconContentColor = if (isDark) AppleBlueLight else AppleBlue
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (isDark) GlassBackgroundDark else GlassBackground)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(20.dp))
                    .background(brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(AppleBlue, AppleBlueLight))),
                contentAlignment = Alignment.Center
            ) { Text("綦", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White) }

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (isDark) AppleLabelDark else AppleLabel)
            Text(stringResource(R.string.version), style = MaterialTheme.typography.bodyMedium, color = if (isDark) AppleTertiaryLabelDark else AppleTertiaryLabel)

            Spacer(Modifier.height(24.dp))

            // 网关设置
            SectionTitle("网关设置", Icons.Outlined.PowerSettingsNew, isDark)
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), elevation = 2.dp) {
                LinkItem(Icons.Outlined.Settings, "网关配置", "端口、API Key、后台保活", onNavigateToSettings, isDark)
            }

            Spacer(Modifier.height(20.dp))

            // 浏览器设置
            SectionTitle("浏览器设置", Icons.Outlined.Public, isDark)
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), elevation = 2.dp) {
                Column {
                    val brainEnabled = remember { mutableStateOf(BrowserBrainConfig.isEnabled(context)) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Psychology, contentDescription = null, tint = if (isDark) AppleBlueLight else AppleBlue, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("浏览器大脑", style = MaterialTheme.typography.bodyLarge, color = if (isDark) AppleLabelDark else AppleLabel)
                            Text("AI自动生成脚本并执行浏览器操作", style = MaterialTheme.typography.bodySmall, color = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel)
                        }
                        Switch(
                            checked = brainEnabled.value,
                            onCheckedChange = { BrowserBrainConfig.setEnabled(context, it); brainEnabled.value = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppleBlue,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = if (isDark) AppleGray.copy(alpha = 0.4f) else AppleGray2.copy(alpha = 0.5f)
                            )
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = if (isDark) GlassBorderDark else GlassBorder)
                    LinkItem(Icons.Outlined.Tune, "大脑配置", "API地址、模型名", {
                        showBrainConfig = true
                    }, isDark)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = if (isDark) GlassBorderDark else GlassBorder)
                    LinkItem(Icons.Outlined.TravelExplore, "MCP浏览器", "独立浏览器，专供MCP对接", onNavigateToMcpBrowser, isDark)
                }
            }

            Spacer(Modifier.height(20.dp))

            // 隐私保护
            SectionTitle("隐私保护", Icons.Outlined.Security, isDark)
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), elevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Security, contentDescription = null, tint = if (isDark) AppleBlueLight else AppleBlue, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("任务视图隐藏", style = MaterialTheme.typography.bodyLarge, color = if (isDark) AppleLabelDark else AppleLabel)
                        Text("AI 任务运行时隐藏在最近任务", style = MaterialTheme.typography.bodySmall, color = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel)
                    }
                    var appHiderEnabled by remember { mutableStateOf(AppHider.isEnabled(context)) }
                    Switch(
                        checked = appHiderEnabled,
                        onCheckedChange = { AppHider.setEnabled(context, it); appHiderEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppleBlue,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = if (isDark) AppleGray.copy(alpha = 0.4f) else AppleGray2.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 设置
            SectionTitle(stringResource(R.string.bookmarks_title), Icons.Outlined.Bookmarks, isDark)
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), elevation = 2.dp) {
                Column {
                    LinkItem(Icons.Outlined.BookmarkAdd, stringResource(R.string.bookmarks_list), stringResource(R.string.bookmark_count, BookmarkManager.getBookmarks(context).size), onNavigateToBookmarks, isDark)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = if (isDark) GlassBorderDark else GlassBorder)
                    LinkItem(Icons.Outlined.Restore, stringResource(R.string.restore_default_bookmarks), stringResource(R.string.restore_default_bookmarks_desc), {
                        BookmarkManager.resetToDefault(context)
                        Toast.makeText(context, restoredDefaultMessage, Toast.LENGTH_SHORT).show()
                    }, isDark)
                }
            }

            Spacer(Modifier.height(20.dp))

            // 语言
            SectionTitle(stringResource(R.string.language_settings), Icons.Outlined.Language, isDark)
            LanguageSettings(isDark, currentLang) { LocaleManager.setLanguageIndex(context, it); onLanguageChanged() }

            Spacer(Modifier.height(16.dp))

            // 链接
            SectionTitle(stringResource(R.string.project_links), Icons.Outlined.Link, isDark)
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), elevation = 2.dp) {
                Column {
                    LinkItem(Icons.Outlined.Code, stringResource(R.string.github_repo), "GitHub", {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/qtgf520/QitongAI-zhuanzhan")))
                    }, isDark)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = if (isDark) GlassBorderDark else GlassBorder)
                    LinkItem(Icons.Outlined.BugReport, stringResource(R.string.feedback), stringResource(R.string.submit_issue), {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/qtgf520/QitongAI-zhuanzhan/issues")))
                    }, isDark)
                }
            }

            Spacer(Modifier.height(16.dp))

            // QQ群
            SectionTitle(stringResource(R.string.community), Icons.Outlined.Groups, isDark)
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), elevation = 2.dp) {
                LinkItem(Icons.Outlined.Chat, stringResource(R.string.qq_group), "1007488535", {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://qm.qq.com/q/4v8sVX4cKc")))
                    } catch (e: Exception) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(qqGroupClipLabel, "1007488535"))
                        Toast.makeText(context, qqGroupCopiedMessage, Toast.LENGTH_SHORT).show()
                    }
                }, isDark)
            }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.copyright), style = MaterialTheme.typography.bodySmall, color = if (isDark) AppleTertiaryLabelDark else AppleTertiaryLabel, modifier = Modifier.padding(bottom = 32.dp))
        }
    }
    BrainConfigDialog(context, isDark, showBrainConfig) { showBrainConfig = false }
}

@Composable
private fun SectionTitle(title: String, icon: ImageVector, isDark: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, start = 4.dp)) {
        Icon(icon, contentDescription = null, tint = if (isDark) AppleBlueLight else AppleBlue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = if (isDark) AppleLabelDark else AppleLabel)
    }
}

@Composable
private fun LanguageSettings(isDark: Boolean, currentLang: Int, onLanguageSelected: (Int) -> Unit) {
    var selectedLang by remember { mutableStateOf(currentLang) }
    val languages = listOf(
        stringResource(R.string.follow_system),
        stringResource(R.string.chinese_simple),
        stringResource(R.string.chinese_traditional_tw),
        stringResource(R.string.chinese_traditional_hk),
        stringResource(R.string.english)
    )
    LaunchedEffect(currentLang) { selectedLang = currentLang }

    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), elevation = 2.dp) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            languages.forEachIndexed { index, lang ->
                Row(modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(bounded = false, radius = 24.dp)) { selectedLang = index; onLanguageSelected(index) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedLang == index, onClick = { selectedLang = index; onLanguageSelected(index) }, colors = RadioButtonDefaults.colors(selectedColor = AppleBlue, unselectedColor = if (isDark) AppleGray else AppleGray2))
                    Spacer(Modifier.width(8.dp))
                    Text(lang, style = MaterialTheme.typography.bodyMedium, color = if (isDark) AppleLabelDark else AppleLabel)
                }
                if (index < languages.size - 1) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = if (isDark) GlassBorderDark else GlassBorder)
            }
        }
    }
}

@Composable
private fun BrainConfigDialog(context: Context, isDark: Boolean, showDialog: Boolean, onDismiss: () -> Unit) {
    var baseUrl by remember { mutableStateOf(BrowserBrainConfig.getBaseUrl(context)) }
    var apiKey by remember { mutableStateOf(BrowserBrainConfig.getApiKey(context)) }
    var model by remember { mutableStateOf(BrowserBrainConfig.getModel(context)) }
    var mcpPort by remember { mutableStateOf(BrowserBrainConfig.getMcpPort(context).toString()) }
    var mcpEnabled by remember { mutableStateOf(BrowserBrainConfig.isMcpEnabled(context)) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = if (isDark) GlassBackgroundDark else GlassBackground,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, tint = AppleBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("大脑配置", fontWeight = FontWeight.SemiBold)
                }
            },
            text = {
                Column {
                    Text("API地址", style = MaterialTheme.typography.bodySmall, color = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel)
                    OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it },
                        modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true, placeholder = { Text("http://localhost:8889") },
                        colors = brainFieldColors(isDark), shape = RoundedCornerShape(10.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("API Key（可选）", style = MaterialTheme.typography.bodySmall, color = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel)
                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true, placeholder = { Text("sk-...") },
                        colors = brainFieldColors(isDark), shape = RoundedCornerShape(10.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("模型名", style = MaterialTheme.typography.bodySmall, color = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel)
                    OutlinedTextField(value = model, onValueChange = { model = it },
                        modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true, placeholder = { Text("qtllq") },
                        colors = brainFieldColors(isDark), shape = RoundedCornerShape(10.dp))
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = if (isDark) GlassBorderDark else GlassBorder)
                    Spacer(Modifier.height(12.dp))
                    Text("MCP服务", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = if (isDark) AppleLabelDark else AppleLabel)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("启用MCP", style = MaterialTheme.typography.bodySmall, color = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel, modifier = Modifier.weight(1f))
                        Switch(checked = mcpEnabled, onCheckedChange = { mcpEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppleBlue,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = if (isDark) AppleGray.copy(alpha = 0.4f) else AppleGray2.copy(alpha = 0.5f)))
                    }
                    Text("MCP端口", style = MaterialTheme.typography.bodySmall, color = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel)
                    OutlinedTextField(value = mcpPort, onValueChange = { mcpPort = it },
                        modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true, placeholder = { Text("7774") },
                        colors = brainFieldColors(isDark), shape = RoundedCornerShape(10.dp))
                }
            },
            confirmButton = {
                Button(onClick = {
                    BrowserBrainConfig.setBaseUrl(context, baseUrl)
                    BrowserBrainConfig.setApiKey(context, apiKey)
                    BrowserBrainConfig.setModel(context, model)
                    BrowserBrainConfig.setMcpEnabled(context, mcpEnabled)
                    mcpPort.toIntOrNull()?.let { BrowserBrainConfig.setMcpPort(context, it) }
                    onDismiss()
                }, colors = ButtonDefaults.buttonColors(containerColor = AppleBlue), shape = RoundedCornerShape(10.dp)) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun brainFieldColors(isDark: Boolean) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppleBlue.copy(alpha = 0.5f),
    unfocusedBorderColor = if (isDark) GlassBorderDark else GlassBorder,
    cursorColor = AppleBlue,
    focusedTextColor = if (isDark) AppleLabelDark else AppleLabel,
    unfocusedTextColor = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel
)

@Composable
private fun LinkItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, isDark: Boolean) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = ripple(bounded = false, radius = 24.dp), onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (isDark) AppleBlueLight else AppleBlue, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (isDark) AppleLabelDark else AppleLabel)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = if (isDark) AppleGray2 else AppleGray, modifier = Modifier.size(20.dp))
    }
}