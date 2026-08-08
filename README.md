# 🔄 綦桐AI转站

[English documentation](README_EN.md)

> **多AI网页自动化对话工具** — 像真人一样操作免费网页AI，自动串联多平台对话

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-purple)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-2026.01.01-blue)](https://developer.android.com/jetpack/compose)
[![AGP](https://img.shields.io/badge/AGP-9.0.0-green)](https://developer.android.com/build)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen)](https://developer.android.com)

---

## 💡 项目理念

**"让安卓设备成为你的AI中转站"**

以 WebView 多实例池模拟真人浏览器，实现：
- 🧑‍💻 **像真人一样操作** — 真实浏览器内核，不越权、不root、不逆向抓包
- 🔄 **多AI串并联** — 豆包→通义→DeepSeek... 自动串联对话
- 🌐 **通用所有网页AI** — 选择器配置外置JSON，不改代码适配新站点
- ⚡ **高效率** — WebView 实例池 + 任务队列 + 并发调度
- 🔐 **安全合规** — 不走无障碍、不越权、仅供个人学习研究

---

## 🚀 当前状态

### ✅ 已完成
- 项目骨架搭建（Kotlin + Compose + Material3）
- 多语言资源（简中/繁中台湾/繁中香港/英文）— 42个字符串×6个目录
- 苹果水晶玻璃 UI（GlassCard 组件）
- 多标签页管理（WebView 多实例池）
- 收藏夹预置 7 个 AI 平台（豆包/元宝/通义/DeepSeek/Kimi/Google/GitHub）
- Cookie 持久化（PersistentCookieJar.kt）— 7个平台登录态不丢
- 桌面 UA 适配（骗过移动端限制）
- 字体缩放 + User-Agent 自定义设置
- JS 注入引擎（JsInjector.kt）— 支持豆包/元宝/通义/Kimi/DeepSeek
- 真人输入模拟（HumanLikeInput.kt）— 逐字打字（30~180ms 随机间隔）
- 任务队列调度（AutoChatTaskQueue.kt）— 平台独立标签页、串行执行、完整回复回调
- **多AI流水线串联** — 将一个平台的完整回复自动传给下一个平台，支持排序、重试、取消和逐步状态
- 四级输入降级：Selection+TextEvent → InputEvent → execCommand → 直接赋值
- Shadow DOM 穿透查找
- Slate 编辑器适配（`[data-slate-editor]`）
- 完整点击事件链：mousedown → mouseup → click → form.submit
- MutationObserver 监听回复完成
- 关于页收藏管理（添加/编辑/删除/恢复默认）
- 底部收藏按钮 toggle（点击展开，再点击关闭）
- 多标签列表滚动（超出 500dp 自动滚动）
- 顶部标题栏优化（缩小按钮、减小字体、增加 padding）
- URL 栏点击编辑（点击文字直接进入编辑模式）
- **豆包专属适配** — 原型 setter 绕过 React 受控组件劫持 + Enter 优先 + 按钮兜底
- **元宝/豆包分支隔离** — `fillAndSend(tag)` 分发，JS 完全独立
- **selectors.json 选择器清单** — 集中记录豆包、元宝、通义、DeepSeek、Kimi 的输入、发送、回复和加载选择器

### 🛠️ 规划中
- [ ] **网关代理支持** — WebView 走网关
- [ ] **对话历史保存** — 本地持久化
- [ ] **任务队列 UI** — 可视化队列状态
- [ ] **导出/导入配置** — 备份恢复
- [ ] **夜间模式优化** — 深色主题
- [ ] **性能优化** — WebView 池复用

---

## 🔗 多AI流水线

1. 先在需要使用的各个网页AI中完成登录。应用不会绕过登录、验证码或平台安全检查。
2. 点击浏览器底栏的流水线图标。
3. 输入初始提示词，勾选平台并调整执行顺序。
4. 应用会为每个平台创建或复用独立标签页，等待页面就绪后发送内容。
5. 只有在检测到新的助手回复、生成状态停止且文本连续稳定后，才会把回复传给下一步。
6. 每一步默认失败后重试一次；仍失败则停止后续步骤并显示原因。运行中可随时取消。

当前内置适配：**豆包、元宝、通义千问、DeepSeek、Kimi**。网页服务会持续更新 DOM、登录流程和风控策略，因此应用会明确报告“需要登录”“找不到输入框”“未检测到稳定回复”等错误，而不会把发送动作误报为流水线成功。

验证包括纯 Kotlin 调度测试，以及在 Android WebView 中使用三种不同 DOM 结构完成 `豆包 → 元宝 → DeepSeek` 的真实注入、回复提取和逐级转发测试。

## 🏗️ 项目结构

```
綦桐AI转站/
├── app/
│   ├── src/main/
│   │   ├── java/com/qtwl/YitongAIzhuanzhan/
│   │   │   ├── MainActivity.kt          # 主入口
│   │   │   ├── WebViewManager.kt        # 多实例池 + Cookie 持久化
│   │   │   ├── JsInjector.kt            # JS 注入引擎（豆包/元宝/通用）
│   │   │   ├── HumanLikeInput.kt        # 真人输入模拟
│   │   │   ├── AutoChatTaskQueue.kt     # 独立任务串行调度
│   │   │   ├── AiPlatformRegistry.kt     # 五个平台定义与选择器
│   │   │   ├── MultiAiPipeline.kt        # 纯Kotlin流水线状态机
│   │   │   ├── MultiAiPipelineRunner.kt  # WebView流水线执行器
│   │   │   ├── BookmarkManager.kt       # 收藏管理
│   │   │   ├── LocaleManager.kt         # 多语言管理
│   │   │   ├── PersistentCookieJar.kt   # Cookie 保存/恢复
│   │   │   └── ui/
│   │   │       ├── screens/             # 页面
│   │   │       │   ├── BrowserScreen.kt
│   │   │       │   ├── AboutScreen.kt
│   │   │       │   ├── SettingsScreen.kt
│   │   │       │   └── BookmarkEditScreen.kt
│   │   │       ├── components/          # 组件
│   │   │       │   └── GlassCard.kt
│   │   │       └── theme/               # 主题
│   │   ├── res/
│   │   │   ├── values/                  # 默认中文
│   │   │   ├── values-en/               # 英文
│   │   │   ├── values-zh/               # 中文通用
│   │   │   ├── values-zh-rCN/           # 简体
│   │   │   ├── values-zh-rTW/           # 繁体台湾
│   │   │   ├── values-zh-rHK/           # 繁体香港
│   │   │   └── ...                      # 图标/主题
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── app/src/main/assets/
│   ├── selectors.json                   # 五个平台选择器清单
│   └── SlateFiller.js                   # 四级降级输入引擎
├── DEV_GUIDE.md                         # 开发指南（铁律必读）
├── CHANGELOG.md                         # 更新日志
├── qitong.jks                           # 签名证书（本地）
└── setup_android_env.sh                 # ARM64构建环境脚本
```

---

## 🛠️ 快速开始

### 环境要求
- JDK 17+
- Android SDK
- ARM64 Linux 环境（Operit 内置）

### 构建
```bash
# 1. 初始化环境（仅首次）
chmod +x ./setup_android_env.sh
./setup_android_env.sh

# 2. 编译
./gradlew assembleDebug

# 3. 安装
cp app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/綦桐AI转站.apk
cp /sdcard/Download/綦桐AI转站.apk /data/local/tmp/app.apk
chmod 644 /data/local/tmp/app.apk
pm install -r /data/local/tmp/app.apk
```

---

## 📦 技术栈

| 组件 | 版本 |
|------|------|
| Kotlin | 2.3.10 |
| AGP | 9.0.0 |
| Compose BOM | 2026.01.01 |
| compileSdk | 35 |
| targetSdk | 35 |
| minSdk | 24 |
| ABI | arm64-v8a |
| 签名 | qitong.jks |

---

## 📜 许可

本项目仅供个人学习研究，禁止商用、禁止对外服务。

---

> **当前版本：** v1.0.3-4 (versionCode=39)
> **发布日期：** 2026-08-05  
> **状态：** 正式版 🎉
