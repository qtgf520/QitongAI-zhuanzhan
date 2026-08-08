# 📋 更新日志

## v1.0.3-4 (2026-08-08) — 真实站点烟雾测试 + Qwen/Kimi 适配

### 🧪 测试
- 新增 Robolectric 网关/平台回归测试：验证无 `charset` 的原始 UTF-8 JSON 请求，以及 5 个生产平台的 WebView 配置。
- 新增可选真实站点 Android 烟雾测试（默认 CI 不执行网络请求），可逐站或一次测试豆包、元宝、Qwen/通义、DeepSeek、Kimi。
- API 35 仿真器回归套件覆盖 Unicode、流式回复、旧回复/侧边栏干扰、Qwen 当前 DOM、Kimi 单次输入以及登录弹窗分类。

### 🐛 修复
- Qwen/通义生产站已迁移到 `www.qianwen.com`：更新主 URL、Host 列表和当前 `message-select-*` 回复容器选择器；真实消息/回复烟雾测试通过。
- Kimi 更新为 `www.kimi.com`，适配当前 `.chat-input-editor` 富文本编辑器，保证消息只写入一次，并在 React 编辑器稳定后再次校验再发送。
- Kimi 当前无类名 `Send` 按钮增加严格的可见文本回退；登录弹窗现在快速返回 `auth`，不再误报为回复超时。
- 真实环境测试会明确区分站点/账号环境阻断：豆包地区限制、元宝/DeepSeek 登录门槛、Kimi 登录门槛不会被伪装为成功。

---

## v1.0.3-3 (2026-08-08) — UTF-8 网关修复

### 🐛 修复
- 修复 OpenAI 兼容客户端发送 `application/json` 但未显式携带 `charset` 时，中文请求被 NanoHTTPD 2.3.1 按 US-ASCII 解码为 `���` 的问题。
- 网关在有 `Content-Length` 时直接读取原始请求字节并严格按 UTF-8 解码；异常 UTF-8 返回明确的 400 错误，不再把乱码继续发送到网页 AI。
- 无 `Content-Length` 的回退路径会在 NanoHTTPD 解析前补充 `charset=UTF-8`。
- 新增中文、Emoji、重音字符和西里尔字符的单元测试与真实 Android WebView 注入回归测试。

---


## v1.0.2 (2026-08-05) — 正式版 🎉

### ✨ 新增
- 🧠 **浏览器大脑** — `BrowserBrain.kt` + `BrowserBrainConfig.kt`，通过本地网关自动生成并执行JS脚本
- 🌐 **MCP浏览器** — `McpBrowserScreen.kt`，独立浏览器界面专供MCP外部对接
- 🔄 **大脑配置** — 关于页可配置API地址（默认 `http://localhost:7773`）、API Key、模型名（默认 `qtai-sj`）
- 📡 **模型列表** — 网关 `/v1/models` 增加 `qtai-sj` 和 `qtllq` 两个模型

### 🐛 修复
- ✅ **网关主线程问题** — `GatewayServer.handleChat` 通过 `Handler` 切换到主线程执行WebView操作
- ✅ **IP显示** — 设置页增加 `localhost` 和 `127.0.0.1` 显示，支持单独复制
- ✅ **端口同步重启** — 修改网关端口时自动重启网关服务
- ✅ **JS回复抓取增强** — `buildSnapshotScript` 改用全页面文本回退，降低过滤阈值
- ✅ **豆包选择器更新** — 增加 `div[contenteditable='true']`、`[role='textbox']` 等新选择器
- ✅ **底部按钮背景统一** — 所有底部按钮去除蓝色背景，统一透明

### 🎨 界面
- 关于页浏览器设置区（大脑开关、配置、MCP入口）
- 顶部按钮行改为两层（按钮行+标题行）
- 去掉右上角历史按钮

---

## v1.0.1-2 (2026-07-26) — 测试版 🔧

### ✨ 新增
- 🌐 **OpenAI 兼容网关** — `GatewayServer.kt`（NanoHTTPD :8080）+ `GatewayService.kt`（前台服务保活）
- 🔔 **通知栏动态更新** — `NotificationHelper.kt`，网关状态实时显示
- 👀 **运行时任务视图隐藏** — `AppHider.kt`，AI 任务时从最近任务列表隐藏
- 📡 **开机自启** — `BootReceiver.kt`，网关开机自动启动
- ⚙️ **GatewayPrefs** — 网关配置持久化（已合并到 SettingsScreen.kt）

### 🐛 修复
- ✅ **Android JS 桥注册** — `WebViewManager.initWebView()` 里每个 WebView 创建时注册
- ✅ **AppHider API 兼容性** — 使用 `ActivityManager.AppTask.setExcludeFromRecents()`
- ✅ **GatewayPrefs 重复声明冲突** — 删除独立文件，使用 SettingsScreen.kt 中已有的

---

## v1.0.0 (2026-07-26) — 正式版 🎉

### ✨ 新增（来自 adybag14-cyber 工程师 PR #1）
- 🔗 **端到端多 AI 流水线串联** — `MultiAiPipeline.kt` + `MultiAiPipelineRunner.kt`
- 📋 **AI 平台注册中心** — `AiPlatformRegistry.kt`
- 💬 **多 AI 流水线对话框** — `MultiAiPipelineDialog.kt`
- 🧪 **完整测试覆盖** — 单元测试 + 集成测试
- 🇬🇧 **完整英文本地化** — `README_EN.md` + 英文资源
- 🔧 **Windows 构建修复** — 跨平台兼容

### ✨ 新增（豆包专属适配）
- 🎯 **原型 setter 填充方案** — 绕过 React 受控组件劫持
- ⌨️ **Enter 优先 + 按钮兜底双保险发送**
- 👀 **MutationObserver 回复监听**
- 🔀 **分支隔离** — `fillAndSend(tag)` 统一分发
- 📄 **selectors.json 配置外置**

### 🐛 修复
- 多标签页切换时 WebView 不显示
- 多标签页列表溢出叠加
- 底部导航栏按钮换行
- 顶部标题栏被遮挡
- URL 栏点击编辑
- 关于页收藏管理
- 底部收藏按钮 toggle
- JVM 内存不足导致编译失败
- Android WebView 输入框焦点问题
- Windows `assembleDebug` 和 `testDebugUnitTest` 兼容性

---

## v1.0.0-25 至 v1.0.0-26 (2026-07-24)

### ✨ 核心功能
- 🍪 **Cookie 持久化** — `PersistentCookieJar.kt`
- ⌨️ **真人输入模拟** — `HumanLikeInput.kt`
- 📋 **任务队列调度** — `AutoChatTaskQueue.kt`
- 🔧 **四级输入降级**
- 🔍 **Shadow DOM 穿透**
- 📝 **Slate 编辑器适配**
- 🖱️ **完整点击事件链**
- 👀 **MutationObserver 监听**

### 🐛 修复
- 多标签页关闭时索引错乱
- 浏览器界面被挤压
- 覆盖层叠加
- 编译报错 `CookieManager` 未导入

---

## v1.0.0-1 至 v1.0.0-24 (2026-07-24)

### ✨ 基础框架
- 🏗️ **项目骨架** — Kotlin + Jetpack Compose + Material3
- 🌐 **多语言支持** — 简中/繁中台湾/繁中香港/英文
- 🎨 **苹果水晶玻璃 UI** — `GlassCard` 组件
- 📑 **多标签页管理** — WebView 多实例池
- ⭐ **收藏夹预置** — 豆包/元宝/通义/DeepSeek/Kimi/Google/GitHub
- 🔐 **Cookie 持久化**
- 💻 **桌面 UA** — 骗过移动端限制
- 🔍 **字体缩放** + User-Agent 自定义

---

## 🚀 技术栈
| 组件 | 版本 |
|------|------|
| Kotlin | 2.3.10 |
| AGP | 9.0.0 |
| Compose BOM | 2026.01.01 |
| compileSdk / targetSdk | 35 |
| minSdk | 24 |
| ABI | arm64-v8a |

---

> **当前版本：** v1.0.3-4 (versionCode=39)
> **状态：** 正式版 🎉  
> **发布日期：** 2026-08-05