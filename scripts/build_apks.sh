#!/usr/bin/env bash
# ============================================================
# AppUpdater「落地版」一键构建脚本
#
# 产物（dist/）：
#   appupdater-v1.0.0-debug.apk   (versionCode=1, 旧版 — 先安装它)
#   appupdater-v1.0.1-debug.apk   (versionCode=2, 新版 — 更新目标)
#   update.json                   (真实清单：downloadUrl 指向本机局域网地址)
#
# 两个 APK 使用同一 debug 签名 → 完美演示「覆盖安装、数据保留」。
#
# 构建前确认环境：
#   JDK 21，Android SDK（platform 36），ANDROID_HOME 已配置，
#   Android Studio 打开过一次项目（生成 gradlew），或本机装有 gradle 9.x。
#
# 用法：
#   ./scripts/build_apks.sh                  # 构建 + 自动注入本机局域网 IP
#   UPDATE_CHECK_URL=https://x/update.json ./scripts/build_apks.sh   # 自定义远端地址
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DIST_DIR="$ROOT_DIR/dist"
mkdir -p "$DIST_DIR"

if [ ! -f "$ROOT_DIR/gradlew" ]; then
  echo "!! 未找到 gradlew。请先用 Android Studio 打开项目（会自动生成 wrapper），"
  echo "   或在本目录执行: gradle wrapper --gradle-version 9.3.1"
  echo "   然后重新运行本脚本。"
  exit 1
fi

# ---------- 解析更新检查地址 ----------
UPDATE_CHECK_URL="${UPDATE_CHECK_URL:-}"
LAN_IP=""
if [ -z "$UPDATE_CHECK_URL" ]; then
  # 自动探测本机局域网 IP（供 adb 连接的手机通过同一 WiFi 访问）
  LAN_IP="$(ip route get 1 2>/dev/null | awk '{print $7; exit}')"
  [ -z "$LAN_IP" ] && LAN_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
  [ -z "$LAN_IP" ] && LAN_IP="127.0.0.1"
  UPDATE_CHECK_URL="http://${LAN_IP}:8000/update.json"
  echo "==> 未指定 UPDATE_CHECK_URL，自动使用本机局域网地址: ${UPDATE_CHECK_URL}"
  echo "    手机需与电脑同一 WiFi；若用模拟器可改 http://10.0.2.2:8000/update.json"
fi

echo "==> [1/3] 构建旧版 v1.0.0 (versionCode=1)"
(cd "$ROOT_DIR" && APP_VERSION_CODE=1 APP_VERSION_NAME=1.0.0 \
  UPDATE_CHECK_URL="$UPDATE_CHECK_URL" \
  ./gradlew :app:assembleDebug)

APK_SRC="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
cp "$APK_SRC" "$DIST_DIR/appupdater-v1.0.0-debug.apk"
echo "    → 已产出 $DIST_DIR/appupdater-v1.0.0-debug.apk"

echo "==> [2/3] 构建新版 v1.0.1 (versionCode=2)"
(cd "$ROOT_DIR" && APP_VERSION_CODE=2 APP_VERSION_NAME=1.0.1 \
  UPDATE_CHECK_URL="$UPDATE_CHECK_URL" \
  ./gradlew :app:assembleDebug)

cp "$APK_SRC" "$DIST_DIR/appupdater-v1.0.1-debug.apk"
echo "    → 已产出 $DIST_DIR/appupdater-v1.0.1-debug.apk"

APK_SIZE=$(stat -c%s "$DIST_DIR/appupdater-v1.0.1-debug.apk" 2>/dev/null || stat -f%z "$DIST_DIR/appupdater-v1.0.1-debug.apk")

echo "==> [3/3] 生成本地 update.json（apkSize=$APK_SIZE）"
cat > "$DIST_DIR/update.json" <<JSON
{
  "versionCode": 2,
  "versionName": "1.0.1",
  "downloadUrl": "http://${LAN_IP:-127.0.0.1}:8000/appupdater-v1.0.1-debug.apk",
  "apkSize": $APK_SIZE,
  "forceUpdate": false,
  "md5": "",
  "releaseNotes": [
    "全新动态卡通更新弹窗，升级过程更有趣",
    "下载进度条走完自动安装，无需手动确认",
    "Canvas 全手绘音乐猫 + 火箭发射 + 彩带庆祝动画",
    "修复若干已知问题，体验更稳定"
  ]
}
JSON

echo ""
echo "构建完成 ✅"
ls -lh "$DIST_DIR"
echo ""
echo "下一步（真机/模拟器验证完整自动更新）："
echo "  1) 启动本地托管:   ./scripts/serve.sh            # http://${LAN_IP:-127.0.0.1}:8000"
echo "  2) 安装旧版:       adb install $DIST_DIR/appupdater-v1.0.0-debug.apk"
echo "  3) 打开旧版 App → 自动检测到 v1.0.1 → 卡通弹窗 → 进度条走完 → 自动安装新版"
