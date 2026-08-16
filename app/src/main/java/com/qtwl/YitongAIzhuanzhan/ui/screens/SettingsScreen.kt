package com.qtwl.YitongAIzhuanzhan.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.qtwl.YitongAIzhuanzhan.GatewayPrefs
import com.qtwl.YitongAIzhuanzhan.GatewayService
import com.qtwl.YitongAIzhuanzhan.IpHelper
import com.qtwl.YitongAIzhuanzhan.R
import com.qtwl.YitongAIzhuanzhan.ui.components.GlassCard
import com.qtwl.YitongAIzhuanzhan.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == GlassBackgroundDark
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var gatewayEnabled by remember { mutableStateOf(GatewayPrefs.isEnabled(context)) }
    var gatewayPort by remember { mutableStateOf(GatewayPrefs.getPort(context)) }
    var gatewayApiKey by remember { mutableStateOf(GatewayPrefs.getApiKey(context)) }
    var showApiKey by remember { mutableStateOf(false) }
    var customUa by remember { mutableStateOf(GatewayPrefs.getUserAgent(context)) }
    var textZoom by remember { mutableIntStateOf(GatewayPrefs.getTextZoom(context)) }
    val gatewayRunning by remember { derivedStateOf { GatewayService.isRunning } }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
        ) {
            Spacer(Modifier.height(16.dp))

            // 网关开关
            SectionTitle(stringResource(R.string.gateway_switch), Icons.Outlined.PowerSettingsNew, isDark)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                elevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.gateway_switch),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) AppleLabelDark else AppleLabel
                        )
                        Text(
                            text = if (gatewayEnabled) stringResource(R.string.gateway_on) else stringResource(R.string.gateway_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (gatewayEnabled) AppleGreen else (if (isDark) AppleGray2 else AppleGray),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Switch(
                        checked = gatewayEnabled,
                        onCheckedChange = {
                            gatewayEnabled = it
                            GatewayPrefs.setEnabled(context, it)
                            if (it) {
                                try {
                                    GatewayService.start(context)
                                } catch (e: Exception) {
                                    // 通知权限未授权时失败
                                }
                            } else {
                                GatewayService.stop(context)
                            }
                        },
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

            // 网关配置
            SectionTitle(stringResource(R.string.gateway_config), Icons.Outlined.Settings, isDark)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                elevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 使用说明
                    Text(
                        "使用说明：在 Cherry Studio 等客户端里添加 OpenAI 兼容接口，地址填下方 IP:端口，API Key 填下方密钥即可。",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // 主机地址（只读，可复制）
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Dns,
                            contentDescription = null,
                            tint = if (isDark) AppleBlueLight else AppleBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.gateway_host),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) AppleLabelDark else AppleLabel
                        )
                    }
                    val ips = run {
                        val list = IpHelper.getAllIps().toMutableList()
                        if (!list.contains("127.0.0.1")) list.add(0, "127.0.0.1")
                        if (!list.contains("localhost")) list.add(0, "localhost")
                        list
                    }
                    Column {
                        if (ips.isNotEmpty()) {
                            ips.forEach { ip ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isDark) GlassSurfaceDarkMode2 else GlassSurfaceDark)
                                        .clickable {
                                            val text = "$ip:${gatewayPort}"
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("网关地址", text))
                                            Toast.makeText(context, "已复制 $text", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$ip:${gatewayPort}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDark) AppleLabelDark else AppleLabel,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = "复制",
                                        tint = if (isDark) AppleBlueLight else AppleBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "未连接网络",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 端口（可编辑）
                    ConfigField(
                        label = stringResource(R.string.gateway_port),
                        value = gatewayPort,
                        placeholder = "7773",
                        onValueChange = {
                            val oldPort = gatewayPort
                            gatewayPort = it
                            GatewayPrefs.setPort(context, it)
                            if (gatewayEnabled && oldPort != it) {
                                GatewayService.stop(context)
                                GatewayService.start(context)
                            }
                        },
                        isDark = isDark,
                        icon = Icons.Outlined.Router,
                        keyboardType = KeyboardType.Number
                    )

                    Spacer(Modifier.height(12.dp))

                    // API Key（可编辑）
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = null,
                            tint = if (isDark) AppleBlueLight else AppleBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.gateway_api_key),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) AppleLabelDark else AppleLabel
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { showApiKey = !showApiKey },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showApiKey) stringResource(R.string.hide) else stringResource(R.string.show),
                                tint = if (isDark) AppleGray2 else AppleGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = gatewayApiKey,
                        onValueChange = {
                            gatewayApiKey = it
                            GatewayPrefs.setApiKey(context, it)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        placeholder = { Text("sk-xxx...", style = MaterialTheme.typography.bodySmall) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppleBlue.copy(alpha = 0.5f),
                            unfocusedBorderColor = if (isDark) GlassBorderDark else GlassBorder,
                            cursorColor = AppleBlue,
                            focusedTextColor = if (isDark) AppleLabelDark else AppleLabel,
                            unfocusedTextColor = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 浏览器设置
            SectionTitle(stringResource(R.string.browser_settings), Icons.Outlined.Smartphone, isDark)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                elevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 字体缩放
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.TextFields, contentDescription = null, tint = if (isDark) AppleBlueLight else AppleBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.text_zoom), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (isDark) AppleLabelDark else AppleLabel)
                        Spacer(Modifier.weight(1f))
                        Text("${textZoom}%", style = MaterialTheme.typography.bodySmall, color = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel)
                    }
                    Slider(
                        value = textZoom.toFloat(),
                        onValueChange = { textZoom = it.toInt(); GatewayPrefs.setTextZoom(context, it.toInt()) },
                        valueRange = 50f..200f,
                        steps = 14,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = AppleBlue,
                            activeTrackColor = AppleBlue.copy(alpha = 0.6f),
                            inactiveTrackColor = if (isDark) AppleGray.copy(alpha = 0.3f) else AppleGray2.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(Modifier.height(16.dp))

                    // UA
                    ConfigField(
                        label = "User-Agent",
                        value = customUa,
                        placeholder = stringResource(R.string.default_user_agent),
                        onValueChange = {
                            customUa = it
                            GatewayPrefs.setUserAgent(context, it)
                        },
                        isDark = isDark,
                        icon = Icons.Outlined.Code
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 网关状态
            SectionTitle(stringResource(R.string.gateway_status), Icons.Outlined.Monitor, isDark)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                elevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StatusRow(
                        label = stringResource(R.string.running_status),
                        value = if (gatewayRunning) stringResource(R.string.gateway_running) else stringResource(R.string.gateway_stopped),
                        valueColor = if (gatewayRunning) AppleGreen else (if (isDark) AppleGray2 else AppleGray),
                        isDark = isDark
                    )
                    Spacer(Modifier.height(8.dp))
                    StatusRow(
                        label = stringResource(R.string.gateway_api_key),
                        value = if (gatewayApiKey.isNotEmpty()) "${gatewayApiKey.take(8)}..." else stringResource(R.string.not_set),
                        isDark = isDark
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String, icon: ImageVector, isDark: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp, start = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDark) AppleBlueLight else AppleBlue,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isDark) AppleLabelDark else AppleLabel
        )
    }
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    isDark: Boolean,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDark) AppleBlueLight else AppleBlue,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isDark) AppleLabelDark else AppleLabel
        )
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        textStyle = MaterialTheme.typography.bodySmall,
        singleLine = true,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppleBlue.copy(alpha = 0.5f),
            unfocusedBorderColor = if (isDark) GlassBorderDark else GlassBorder,
            cursorColor = AppleBlue,
            focusedTextColor = if (isDark) AppleLabelDark else AppleLabel,
            unfocusedTextColor = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel
        ),
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    isDark: Boolean,
    valueColor: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDark) AppleSecondaryLabelDark else AppleSecondaryLabel
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: (if (isDark) AppleLabelDark else AppleLabel)
        )
    }
}