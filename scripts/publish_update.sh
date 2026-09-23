#!/usr/bin/env bash
# ============================================================
# 懒得听「自动更新发布脚本」
# 用法:
#   ./scripts/publish_update.sh <APK路径> <versionCode> <versionName> [更新说明...]
# 示例:
#   ./scripts/publish_update.sh dist/landeting-v1.0.2.apk 3 1.0.2 "新增功能" "修复问题"
# 功能:
#   1. 校验 APK 元数据 (aapt)
#   2. 创建/更新 GitHub Release 并上传 APK
#   3. 自动生成 update.json 更新清单 (versionCode/下载地址/大小/说明)
#   4. 推送 update.json 到仓库 main 分支
#   手机端 AppUpdateChecker 检测到 versionCode 增大即自动弹窗 -> 下载 -> 安装
# ============================================================
set -euo pipefail

APK_PATH="${1:?用法: publish_update.sh <APK路径> <versionCode> <versionName> [说明...]}"
VERSION_CODE="${2:?缺少 versionCode}"
VERSION_NAME="${3:?缺少 versionName}"
shift 3
RELEASE_NOTES=("$@")

GH_TOKEN="${GH_TOKEN:-}"
REPO_OWNER="${REPO_OWNER:-你的GitHub用户名}"
REPO_NAME="${REPO_NAME:-你的仓库名}"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

if [ -z "$GH_TOKEN" ]; then
  echo "!! 请设置 GH_TOKEN 环境变量 (GitHub 访问令牌)"
  exit 1
fi

# ---------- 校验 APK ----------
AAPT_BIN="$(command -v aapt 2>/dev/null || echo "${ANDROID_HOME:-}/build-tools/36.0.0/aapt")"
if [ -x "$AAPT_BIN" ]; then
  echo "==> 校验 APK 元数据..."
  "$AAPT_BIN" dump badging "$APK_PATH" 2>&1 | grep -E "package:|launchable-activity" | head -3
fi

APK_SIZE=$(stat -c%s "$APK_PATH")
APK_BASENAME=$(basename "$APK_PATH")
TAG_NAME="v${VERSION_NAME}"
API="https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}"

echo "==> 发布 ${APK_BASENAME} (versionCode=${VERSION_CODE} / v${VERSION_NAME}, ${APK_SIZE} bytes)"

# ---------- 构造 Release Body ----------
RELEASE_BODY=$(python3 - "$VERSION_NAME" "${RELEASE_NOTES[@]}" <<'PYEOF'
import json, sys
vn = sys.argv[1]
notes = [n for n in sys.argv[2:] if n.strip()]
body = "AppUpdater v%s 正式版\n" % vn
if notes:
    body += "\n".join("- " + n for n in notes)
else:
    body += "- 优化使用体验，修复已知问题"
print(json.dumps(body, ensure_ascii=False))
PYEOF
)

# ---------- 创建或更新 Release ----------
RELEASE_URL="${API}/releases/tags/${TAG_NAME}"
EXISTING=$(curl -s -H "Authorization: token ${GH_TOKEN}" "$RELEASE_URL" || true)
if echo "$EXISTING" | python3 -c "import json,sys; json.load(sys.stdin)['id']" 2>/dev/null; then
  REL_ID=$(echo "$EXISTING" | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")
  echo "==> Release ${TAG_NAME} 已存在 (id=${REL_ID})，更新信息..."
  curl -s -X PATCH -H "Authorization: token ${GH_TOKEN}" -H "Content-Type: application/json" \
    -d "{\"name\":\"AppUpdater v${VERSION_NAME}\",\"body\":${RELEASE_BODY}}" \
    "${API}/releases/${REL_ID}" > /dev/null
else
  echo "==> 创建 Release ${TAG_NAME}..."
  REL_ID=$(curl -s -X POST -H "Authorization: token ${GH_TOKEN}" -H "Content-Type: application/json" \
    -d "{\"tag_name\":\"${TAG_NAME}\",\"name\":\"AppUpdater v${VERSION_NAME}\",\"body\":${RELEASE_BODY},\"draft\":false,\"prerelease\":false}" \
    "${API}/releases" | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")
fi

# ---------- 删除同名旧 asset ----------
OLD_ASSET=$(curl -s -H "Authorization: token ${GH_TOKEN}" "${API}/releases/${REL_ID}/assets" \
  | python3 -c "
import json, sys
for a in json.load(sys.stdin):
    if a['name'] == '$APK_BASENAME':
        print(a['id'])
        break
")
if [ -n "${OLD_ASSET:-}" ]; then
  echo "==> 删除旧 asset ${OLD_ASSET}..."
  curl -s -X DELETE -H "Authorization: token ${GH_TOKEN}" \
    "${API}/releases/assets/${OLD_ASSET}" > /dev/null
fi

# ---------- 上传 APK ----------
echo "==> 上传 APK..."
UPLOAD_RESULT=$(curl -s -X POST -H "Authorization: token ${GH_TOKEN}" \
  -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary @"${APK_PATH}" \
  "https://uploads.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/${REL_ID}/assets?name=${APK_BASENAME}")
DOWNLOAD_URL=$(echo "$UPLOAD_RESULT" | python3 -c "import json,sys; print(json.load(sys.stdin).get('browser_download_url',''))")
if [ -z "$DOWNLOAD_URL" ]; then
  echo "!! 上传失败，请检查 token 权限"
  exit 1
fi
echo "==> 下载地址: ${DOWNLOAD_URL}"

# ---------- 生成 update.json ----------
NOTES_JSON=$(python3 - "${RELEASE_NOTES[@]}" <<'PYEOF'
import json, sys
notes = [n for n in sys.argv[1:] if n.strip()]
if not notes:
    notes = ["优化使用体验，修复已知问题"]
print(json.dumps(notes, ensure_ascii=False))
PYEOF
)
python3 - "$VERSION_CODE" "$VERSION_NAME" "$DOWNLOAD_URL" "$APK_SIZE" "$NOTES_JSON" <<'PYEOF' > "${ROOT_DIR}/update.json"
import json, sys
vc, vn, url, size, notes = int(sys.argv[1]), sys.argv[2], sys.argv[3], int(sys.argv[4]), json.loads(sys.argv[5])
info = {
    "versionCode": vc,
    "versionName": vn,
    "downloadUrl": url,
    "apkSize": size,
    "forceUpdate": False,
    "md5": "",
    "releaseNotes": notes,
}
print(json.dumps(info, ensure_ascii=False, indent=2))
PYEOF
echo "==> update.json 已生成:"
cat "${ROOT_DIR}/update.json"

# ---------- 推送 update.json ----------
cd "$ROOT_DIR"
git add update.json
git -c user.name="${REPO_OWNER}" -c user.email="${REPO_OWNER}@users.noreply.github.com" \
  commit -m "release: v${VERSION_NAME} (versionCode ${VERSION_CODE})" 2>/dev/null || true
git push "https://x-access-token:${GH_TOKEN}@github.com/${REPO_OWNER}/${REPO_NAME}.git" main

echo ""
echo "✅ 发布完成！"
echo "   新版本: v${VERSION_NAME} (versionCode ${VERSION_CODE})"
echo "   更新地址: ${DOWNLOAD_URL}"
echo "   更新清单: https://raw.githubusercontent.com/${REPO_OWNER}/${REPO_NAME}/main/update.json"
