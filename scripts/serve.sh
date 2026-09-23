#!/usr/bin/env bash
# ============================================================
# AppUpdater「落地版」本地托管脚本
#
# 在 dist/ 目录起一个 HTTP 服务，让 adb 连接的真机/模拟器
# 能直接访问 update.json 与新版 APK，完成真实下载与自动安装。
#
# 用法：
#   ./scripts/serve.sh            # 默认端口 8000，监听全部网卡
#   ./scripts/serve.sh 9000       # 自定义端口
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DIST_DIR="$ROOT_DIR/dist"
PORT="${1:-8000}"

if [ ! -f "$DIST_DIR/update.json" ] || [ ! -f "$DIST_DIR/appupdater-v1.0.1-debug.apk" ]; then
  echo "!! dist/ 缺少 update.json 或新版 APK，请先运行 ./scripts/build_apks.sh"
  exit 1
fi

LAN_IP="$(ip route get 1 2>/dev/null | awk '{print $7; exit}')"
[ -z "$LAN_IP" ] && LAN_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
[ -z "$LAN_IP" ] && LAN_IP="127.0.0.1"

echo "=============================================================="
echo "  本地更新服务器已启动 ✅"
echo "  真机（同一 WiFi）:  http://${LAN_IP}:${PORT}/update.json"
echo "  模拟器专用地址:     http://10.0.2.2:${PORT}/update.json"
echo "  新版 APK:           http://${LAN_IP}:${PORT}/appupdater-v1.0.1-debug.apk"
echo "  按 Ctrl+C 停止"
echo "=============================================================="
echo ""
echo "  提示：旧版 App 中把 UPDATE_CHECK_URL 指到上面的 update.json 地址，"
echo "  启动即可触发「检测 → 卡通弹窗 → 下载 → 自动安装」。"

cd "$DIST_DIR"
python3 -m http.server "$PORT" --bind 0.0.0.0
