# 🚀 綦桐AI转站 — 超级开发指南（自教文件）

> ⚡ **最高优先级：** 本文件是 AI 代理开发綦桐AI转站的**自教指南**，每次迭代必须优先阅读和执行
> **核心规则：优先启动 Debug 编译**（`./gradlew assembleDebug`），快速验证

---

## 📌 开发铁律（每次必读）

0. **📖 每次开发前先读本文件** — 不跳步，不省略
1. **🔢 启动检测：先读版本号，再开干** — 接到任何开发指令，**第一步必须先读 `app/build.gradle.kts` 确认当前 `versionName` 和 `versionCode`**，然后**立即递增**（测试版 `-N` +1，正式版走正式流程）。**禁止等用户提醒才升级版本号，AI自己记住要主动做**
2. **🔥 优先 Debug 编译** — 所有修改先 `assembleDebug` 验证通过
3. **💾 改前先备份单个文件** — 改哪个文件就备份哪个文件（`cp xxx.kt xxx.kt.bak`）
4. **🔍 参照备份开发** — 打开备份文件（xxx.kt.bak）参考，修改现有文件（xxx.kt），避免花括号等格式错误
5. **❌ 编译报错时先恢复备份** — 从刚备份的 `.bak` 文件恢复，**禁止直接从Git拉取**
6. **✅ 编译必须通过** — `BUILD SUCCESSFUL` 才能交付
7. **🚿 Git提交前清理** — 删 `.bak`、`backup_*`、临时文件
8. **📖 每次改代码前先看本文件** — 严格按流程走，不跳步
9. **⌨️ 改代码优先使用工具编辑** — 用 `edit_file` 工具修改，避免手动替换导致格式错乱
10. **🚫 禁止卸载APP** — 签名不对重新签名，禁止卸载（保持用户数据）
11. **📲 安装使用 Shizuku 权限** — 无 root 权限时用 Shizuku 授权安装
12. **🔒 发布不泄漏本地凭证** — 签名证书(`*.jks`/`*.keystore`)、密码(`storePassword`/`keyPassword`)、构建产物(`*.idsig`/`*.apk`/`*.aab`) 禁止提交Git。`.gitignore` 已含规则，`git add` 前先 `git status` 检查有无敏感文件
13. **📄 每次发布前同步更新README.md和CHANGELOG.md** — 版本号、更新日志、功能描述必须与当前版本一致，改完再提交Git
14. **🏠 双轨Git制 — 测试版提交本地Git，正式版才推远程** — 测试版只 commit 到本地 `.git`，不 `git push`；正式版才 `git push origin` + 打标签 + Release。本地Git作为"测试版存档"，远程Git作为"正式版发布"
15. **📦 本地Git archive 发布到目录** — 测试通过后，用 `git archive` 导出干净代码到发布目录，供产出比对
16. **🌐 全程适配语言** — 新增文字/UI/通知/错误提示须同步添加 `values/`（默认中文）、`values-en/`（英文）、`values-zh/`、`values-zh-rCN/`、`values-zh-rTW/`、`values-zh-rHK/` 多语言资源，确保中英及港台繁简全覆盖
17. **🔢 每次开发必须升级版本号，严禁重复使用** — 无论测试版还是正式版，**每次修改代码（含修复、新功能、文档调整）都必须先递增 versionCode，versionName 同步递增**（测试版 `-N` 每次 +1，正式版去 `-N`）。**禁止重复使用同一个版本号**（重复会导致覆盖安装不生效、系统判定版本未更新或更新不兼容）。每次 `git commit` 前必须先确认 build.gradle.kts 版本号已递增且未与历史重复
18. **📛 APK 文件名必须统一格式 `AppName-版本号-android.apk`** — 测试版和正式版复制到 sdcard 的 APK 文件名必须统一使用 `AppName-版本号-android.apk` 格式（如 `QitongAI-zhuanzhan-1.0.1-android.apk`、`QitongAI-zhuanzhan-1.0.1-1-android.apk`），**禁止使用 `app-debug.apk` 等无版本号或不同格式的文件名直接安装**，否则 Android 系统会因包签名不一致或文件名差异导致更新不兼容（安装失败或无法覆盖安装）
---

## 1. 版本号规则

### 测试版
```
格式：1.0.x-N  （N是测试序号，每次测试递增）
例如：1.0.1-1 → 1.0.1-2 → 1.0.1-3 ...
```
- **测试版** = 只编译安装本地验证，不发Git（仅本地commit存档）
- 每次测试 versionCode 递增1，versionName 的 -N 数字递增

### 正式版
```
格式：1.0.x  （去掉 -N）
例如：1.0.1-3 测试通过 → 发布 1.0.1
```
- **正式版** = 编译 + 复制到sdcard + 安装本地 + 推Git + 打标签 + GitHub Release
- 正式发布时 versionCode 保持测试版最后的值，versionName 去掉 -N 后缀

### 示例
| 阶段 | versionName | versionCode | 操作 |
|------|-------------|-------------|------|
| 测试1 | 1.0.3-1 | 34 | 编译安装验证（本地Git commit） |
| 测试2 | 1.0.3-2 | 37 | 编译安装验证（本地Git commit） |
| 测试3 | 1.0.3-3 | 38 | 编译安装验证（本地Git commit） |
| 测试4 | 1.0.3-4 | 39 | 编译安装验证（本地Git commit） |
| 测试5 | 1.0.3-5 | 40 | 编译安装验证（本地Git commit） |
| 测试6 | 1.0.3-6 | 41 | 编译安装验证（本地Git commit） |
| 测试7 | 1.0.3-7 | 42 | 编译安装验证（本地Git commit） |
| **正式发布** | **1.0.3** | **42** | **编译+安装+推Git+Release** |

---

## 2. 修改代码（对照备份法）

### 2.1 核心修改流程
```
① 备份要改的文件  →  cp TargetFile.kt TargetFile.kt.bak
② 打开备份文件       →  参考其结构
③ 修改原文件         →  照着备份的逻辑去改
④ 编译验证           →  ./gradlew assembleDebug
⑤ 编译失败？         →  cp TargetFile.kt.bak TargetFile.kt（恢复备份）
⑥ 重新修改再编译
```

### 2.2 对照备份法详解
```
备份文件 TargetFile.kt.bak  ← 打开参考（不改它）
                ↓ 对照
现有文件 TargetFile.kt       ← 实际修改（编译它）
```
- 备份文件是**已知能编译通过的**，打开它看花括号、函数结构
- 照着备份的结构去改现有文件
- 这样就**不会出现花括号错乱、函数被吃**等问题

### 2.3 关键源文件
| 文件 | 路径相对项目根 |
|------|---------------|
| 版本号 & 包名 | app/build.gradle.kts |
| 主入口 | app/src/main/java/.../MainActivity.kt |
| 主题-颜色 | app/src/main/java/.../ui/theme/Color.kt |
| 主题-主题 | app/src/main/java/.../ui/theme/Theme.kt |
| 主题-字体 | app/src/main/java/.../ui/theme/Type.kt |
| Android清单 | app/src/main/AndroidManifest.xml |
| 多语言资源 | app/src/main/res/values-*/strings.xml |

### 2.4 编译报错时**绝对禁止**的操作
```
❌ git checkout -- xxx.kt         ← 禁止！会丢失本地修改
❌ git restore xxx.kt             ← 禁止！会丢失本地修改
✅ cp xxx.kt.bak xxx.kt          ← 正确！从备份恢复
```
备份文件就是用来兜底的，`.bak` 就是你的安全网。

---

## 3. 编译

```bash
cd /data/data/com.ai.assistance.operit/files/workspace/綦桐AI转站

# Debug 版（测试用，优先）
./gradlew assembleDebug

# Release 版（正式发布用）
./gradlew clean assembleRelease
```

常见问题: LazyColumn 中 Composable 要用 item {} 包裹

---

## 4. 安装到设备

```bash
# ★★★ 复制到 sdcard（必须带版本号，命名统一为 AppName-版本号-android.apk）★★★
# 例如：QitongAI-zhuanzhan-1.0.1-android.apk（正式版）/ QitongAI-zhuanzhan-1.0.1-1-android.apk（测试版）
cp app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/QitongAI-zhuanzhan-版本号-android.apk

# 安装（需先复制到 /data/local/tmp/）
cp /sdcard/Download/QitongAI-zhuanzhan-版本号-android.apk /data/local/tmp/app.apk
chmod 644 /data/local/tmp/app.apk
pm install -r /data/local/tmp/app.apk

# 验证版本
dumpsys package com.qtwl.YitongAIzhuanzhan | grep -E 'versionName|versionCode'
```

---

## 5. 验证清单

- 编译通过 → BUILD SUCCESSFUL
- 安装成功 → pm install Success
- 多语言切换 → 简中/繁中/英文显示正确
- 启动不闪退 → 主界面正常显示

---

## 6. 测试版 vs 正式版

### 测试模式（我说"测试"时）
```
① 改版本号 → 1.0.x-N（N递增）
② 改代码（对照备份法）
③ ./gradlew assembleDebug
④ 复制APK到sdcard（带版本号，命名统一：QitongAI-zhuanzhan-1.0.x-N-android.apk）
⑤ 安装到设备
⑥ 验证功能
⑦ 提交本地Git（不 push 远程）
⑧ 可选：git archive 导出到发布目录 ~/publish/
```
**不推远程Git，不打远程标签，不发GitHub Release**

### 正式模式（我说"发布"时）
```
① 改版本号 → 1.0.x（去掉 -N）
② 改代码（对照备份法）
③ 更新README.md（版本号+更新日志）
④ 更新CHANGELOG.md（追加新版本日志）
⑤ ./gradlew assembleDebug
⑥ 复制APK到sdcard（带版本号，命名统一：QitongAI-zhuanzhan-1.0.x-android.apk）
⑦ 安装到设备
⑧ 验证功能
⑨ 清理备份文件（删.bak）
⑩ 提交本地Git
⑪ 推远程Git + 打标签 + GitHub Release（APK上传）
```

---

## 7. 推送 Git & 发布

### 7.1 本地Git（测试版用，不碰远程）
测试版禁止推远程，但需要本地存档，方便回滚和比对。
```bash
cd /data/data/com.ai.assistance.operit/files/workspace/綦桐AI转站

# ⚠️ 先检查有无敏感文件被跟踪
git status

# 清理备份文件
find . -name '*.bak' -delete
find . -name '*.before_py' -delete

# 提交到本地（不 push）
git add -A
git commit -m 'v1.0.x-N - 更新说明'
```

### 7.2 远程Git（正式版用）
正式版才推远程 + 打标签。
```bash
cd /data/data/com.ai.assistance.operit/files/workspace/綦桐AI转站

# ⚠️ 先检查有无敏感文件被跟踪
git status
# 确认没有 *.jks *.keystore *.idsig *.apk *.aab 等文件再提交

# 清理备份文件
find . -name '*.bak' -delete
find . -name '*.before_py' -delete

# 提交
git add -A
git commit -m 'v1.0.x - 更新说明'
git push

# 打标签
git tag -f v1.0.x
git push origin v1.0.x -f
```

### 7.3 GitHub Release
```bash
GIT_TOKEN=$(git remote -v | head -1 | sed 's/.*qtgf520://;s/@.*//')
API_URL='https://api.github.com/repos/qtgf520/QitongAI-zhuanzhan/releases'

# 创建 Release
curl -s -X POST "$API_URL" \
  -H "Authorization: Bearer $GIT_TOKEN" \
  -d '{"tag_name":"v1.0.x","name":"v1.0.x","prerelease":false}'

# 上传 APK（带版本号）
RELEASE_ID=$(curl -s -H "Authorization: Bearer $GIT_TOKEN" \
  "$API_URL/tags/v1.0.x" | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")

curl -s -X POST \
  "https://uploads.github.com/repos/qtgf520/QitongAI-zhuanzhan/releases/$RELEASE_ID/assets?name=QitongAI-zhuanzhan-v1.0.x.apk" \
  -H "Authorization: Bearer $GIT_TOKEN" \
  -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary @app/build/outputs/apk/debug/app-debug.apk
```

### 7.4 本地Git archive 发布到目录（可选）
测试通过后，想把当前版本导出成干净目录：
```bash
# 创建发布目录
mkdir -p ~/publish/v1.0.x

# 用 git archive 导出干净代码
git archive --format=tar HEAD | tar -x -C ~/publish/v1.0.x

# 或直接复制构建产物
cp -r app/build/outputs/apk/debug/app-debug.apk ~/publish/v1.0.x/QitongAI-zhuanzhan-v1.0.x.apk

# 发布目录在 proot 中，可 cp 到 /sdcard/ 共享给 Android
cp -r ~/publish/v1.0.x /sdcard/Download/publish/
```

---

## 8. 清理旧文件

```bash
# 清理旧APK（保留最新）
cd /sdcard/Download
ls QitongAI-zhuanzhan*.apk 2>/dev/null | grep -v 'QitongAI-zhuanzhan-v1.0.x.apk' | while read f; do rm -f "$f"; done

# 清理旧备份（保留最新）
cd /data/data/com.ai.assistance.operit/files/workspace/綦桐AI转站
ls -dt backup_*/ | tail -n +2 | xargs rm -rf 2>/dev/null
```

---

> **文档版本:** v18 — 2026-08-16
> **适用于:** 綦桐AI转站 v1.0.3+
> **核心改动:**
> - 铁律16：新增"每次开发必须升级版本号，严禁重复使用"
> - 铁律17：新增"APK 文件名必须统一格式 AppName-版本号-android.apk"
> - 第1节示例：更新为当前 v1.0.3 实际版本序列
> - 第4节安装：APK 命名改为统一格式
> - 第6节流程：增加 APK 命名规范说明
