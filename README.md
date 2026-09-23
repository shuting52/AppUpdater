---
AIGC:
  Label: "1"
  ContentProducer: 001191340100MA8QP9WJ5400000
  ProduceID: 0ad09686-5df5-4d43-aab9-6556604a9d41:art_75ac842253b07792f3c4357c4a4ffb34
  ReservedCode1: ""
  ContentPropagator: 001191340100MA8QP9WJ5400000
  PropagateID: 0ad09686-5df5-4d43-aab9-6556604a9d41:art_75ac842253b07792f3c4357c4a4ffb34
  ReservedCode2: ""
---
# AppUpdater ? 卡通自动更新（落地版）

从 [懒得听 (landeting)](https://github.com/shuting52/landeting) 音乐播放器中**独立提取**的自动更新模块，完整保留「动态卡通更新弹窗」的交互体验，并已转换为**可直接真机验证的落地版本**：

> **检测新版本 → 动态卡通弹窗（Canvas 全手绘音乐猫）→ 下载（进度环 + 均衡器动效）→ 进度条走完自动安装 → 完成（彩带庆祝）**

任意 Android 应用都可以直接接入这套更新交互，包名/依赖完全独立（`com.appupdater`）。

---

## ✨ 与演示版相比，落地版做了这些升级

| 项 | 演示版 | ✅ 落地版 |
|---|---|---|
| ? 下载完成后 | 停在「立即安装」按钮，需手动点击 | **进度条 100% 后自动调起安装**，零手动干预 |
| ?️ 安装权限缺失时 | 引导开启后需用户自行操作 | 引导开启后回弹窗一键「重新安装」继续 |
| ? APK 缓存续传 | 无 | 已下载的 APK 再次检测到直接安装，不重复下载 |
| ? 完整性校验 | 无 | 下载后校验 `apkSize`，大小不符提示重试 |
| ? 安装完成 | 「好的」关弹窗 | 「立即重启」→ 冷启动新版本（覆盖安装后旧进程自动结束） |
| ? 构建脚本 | 输出占位地址 | 自动探测局域网 IP，注入真实 `update.json` 与可下载 APK |
| ? 真机验证 | 需自备服务器 | 新增 `serve.sh` 一键本地托管，WiFi 局域网秒级验证完整链路 |

## ? 完整自动更新流程（落地版）

```
App 启动
  ├─ AppUpdateChecker.fetchLatestInfo()
  │   ├─ 拉取远端 update.json (BuildConfig.UPDATE_CHECK_URL)
  │   └─ 失败时回退内置 assets/update_demo.json（演示预览）
  ├─ hasNewVersion(): 远端 versionCode > 本地 versionCode ?
  ├─ YES → CartoonUpdateDialog 动态卡通弹窗
  │   ├─ 检测中 (Checking)   — 猫咪待机动画
  │   ├─ 发现新版本 (Found)   — 版本对照条 + 更新说明 + 「立即更新」
  │   ├─ 下载中 (Downloading) — 进度环 + 均衡器动效
  │   ├─ ✅ 进度条走完 100% → 自动进入安装（无需点击）
  │   ├─ 安装中 (Installing)  — 火箭发射动画
  │   ├─ 完成 (Done)          — 彩带庆祝 + 「立即重启」
  │   └─ 错误 / 权限引导 (Error / NeedInstallPermission)
  └─ ApkDownloader → AppInstaller
      ├─ PackageInstaller 系统会话（原子化替换旧版，数据保留）
      └─ 兜底 FileProvider + ACTION_VIEW 系统安装器
```

## ? 项目结构

```
app/src/main/java/com/appupdater/
├── MainActivity.kt                  # 演示宿主（挂载弹窗 + 手动检查）
├── update/                          # ? 自动更新核心模块（可复制接入）
│   ├── AppUpdateChecker.kt          # 版本检测（远端清单 / 演示回退）
│   ├── ApkDownloader.kt             # APK 流式下载（应用专属目录，无需存储权限）
│   ├── AppInstaller.kt              # PackageInstaller + FileProvider 双路径安装
│   ├── UpdateInstallReceiver.kt     # 安装结果广播接收
│   └── UpdateModels.kt              # UpdateInfo 模型 + UpdateState 状态机
├── ui/components/CartoonUpdateDialog.kt   # ? 动态卡通更新弹窗（核心亮点）
└── viewmodel/UpdateViewModel.kt     # 更新编排：检测→下载→【自动安装】→重试/重启
```

## ? 环境要求

- JDK 21
- Android SDK（platform 36 / compileSdk 36）
- 首次用 **Android Studio 打开项目**生成 `gradlew`（或本机装 gradle 9.x 后执行 `gradle wrapper`）

## ? 三步真机验证「进度条走完自动安装」

```bash
# ① 一键构建 v1.0.0（旧版）+ v1.0.1（新版），并自动生成指向本机局域网的真实 update.json
./scripts/build_apks.sh

# ② 启动本地托管（把 update.json 和新版 APK 共享给手机）
./scripts/serve.sh          # 会打印 http://<本机IP>:8000/update.json

# ③ 安装旧版并打开
adb install dist/appupdater-v1.0.0-debug.apk
```

打开旧版 App 后：**自动检测 → 卡通弹窗「发现新版本」→ 点「立即更新」→ 进度条走到 100% → 自动进入安装 → 火箭动画 → 覆盖安装成功**。? 全程只点一次按钮。

> 模拟器验证：`UPDATE_CHECK_URL=http://10.0.2.2:8000/update.json ./scripts/build_apks.sh` 重新构建旧版即可。

## ? 接入你自己的 App（三步）

1. **拷贝代码**：`update/` 包 + `ui/components/CartoonUpdateDialog.kt`；
2. **编排流程**：复制 `UpdateViewModel`（或把方法合并进你的 ViewModel）；
3. **挂载弹窗**（参考 `MainActivity` 底部）：

```kotlin
val updateState by viewModel.updateState.collectAsStateWithLifecycle()
if (updateState !is UpdateState.Idle && updateState !is UpdateState.Checking) {
    CartoonUpdateDialog(
        state = updateState,
        currentVersion = BuildConfig.VERSION_NAME,
        newVersion = viewModel.latestVersionName,
        onStartDownload = { viewModel.startUpdateDownload() },   // 下载完自动安装
        onInstall = { viewModel.installUpdate() },               // 权限开启后继续安装
        onOpenInstallSettings = { viewModel.openInstallPermissionSettings() },
        onDismiss = { viewModel.dismissUpdate() },
        onRetry = { viewModel.retryUpdate() },
        onDone = { viewModel.dismissUpdate() },
        onRestartApp = { viewModel.restartApp() }                // 安装完成重启到新版本
    )
}
```

### 接入注意
- **FileProvider authority**：`AppInstaller` 按 `${packageName}.fileprovider` 动态拼接，改包名时同步改 `AndroidManifest.xml` 里的 `<provider android:authorities="你的包名.fileprovider">`；
- **签名一致**：覆盖安装要求新旧 APK 同一把密钥（debug 包共用 debug key；发布包务必用同一把发布密钥）；
- **权限**：`AndroidManifest.xml` 需声明 `INTERNET` 与 `REQUEST_INSTALL_PACKAGES`（已包含），Android 8.0+ 需用户在系统弹窗中允许「安装未知应用」，应用会引导跳转设置页；
- **强制更新**：`update.json` 里 `forceUpdate: true` 时弹窗不可关闭。

## ? 更新清单字段

```json
{
  "versionCode": 2,          // 版本号（比较大小判定是否有新版本）
  "versionName": "1.0.1",    // 版本名（展示用）
  "downloadUrl": "http://192.168.x.x:8000/appupdater-v1.0.1-debug.apk",
  "apkSize": 12345678,       // APK 大小（字节）—— 落地版会据此校验下载完整性
  "forceUpdate": false,      // 是否强制更新
  "md5": "",                 // APK MD5 校验（可选）
  "releaseNotes": ["更新说明1", "更新说明2"]
}
```

## ? 环境变量

| 变量 | 作用 |
|---|---|
| `UPDATE_CHECK_URL` | 远端 update.json 地址（默认自动指向本机局域网 `http://<IP>:8000/update.json`） |
| `APP_VERSION_CODE` | 覆盖构建版本号（如 1 / 2 / 3…） |
| `APP_VERSION_NAME` | 覆盖构建版本名（如 1.0.0 / 1.0.1…） |

## ? 发布到真实服务器（GitHub Release 方式）

```bash
export GH_TOKEN=你的_github_token
./scripts/publish_update.sh dist/appupdater-v1.0.2.apk 3 1.0.2 "新增功能" "修复问题"
# 自动：校验 APK → 创建/上传 Release → 生成 update.json → 推送回仓库
```

---

> 本项目为「懒得听」音乐播放器更新模块的独立提取版（落地版）。
> 原项目完整功能见 [shuting52/landeting](https://github.com/shuting52/landeting)。

内容由AI生成
