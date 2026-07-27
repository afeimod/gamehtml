#!/bin/bash
# ── 下载 Ruffle 引擎到 assets 目录（离线资源）──────────────
# 用法: bash scripts/download-ruffle.sh
# 如果 ruffle.js 已存在则跳过，可删除后重新下载。
set -e

DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$DIR/app/src/main/assets/web/engines/ruffle"
MARKER="$DEST/ruffle.js"

if [ -f "$MARKER" ]; then
  echo "✓ Ruffle 已存在于 assets，跳过下载。"
  echo "  如需重新下载，请先删除: rm -rf $DEST"
  exit 0
fi

mkdir -p "$DEST"
TMP=$(mktemp -d)
trap "rm -rf $TMP" EXIT

# 尝试通过 GitHub API 获取最新 nightly selfhosted 包
echo "正在获取 Ruffle 最新版本..."
URL=""
API_RESP=$(curl -fsSL --connect-timeout 15 \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/ruffle-rs/ruffle/releases" 2>/dev/null || echo "")

if [ -n "$API_RESP" ]; then
  URL=$(echo "$API_RESP" | grep -oP '"browser_download_url"\s*:\s*"\Khttps://[^"]*selfhosted\.zip' | head -1)
fi

if [ -z "$URL" ]; then
  echo "API 获取失败，使用固定版本回退..."
  URL="https://github.com/ruffle-rs/ruffle/releases/download/nightly-2024_08_26/ruffle_web_latest_releases_selfhosted.zip"
fi

echo "下载: $URL"
ZIP="$TMP/ruffle.zip"
curl -fsSL -o "$ZIP" "$URL"

echo "解压到: $DEST"
unzip -o -q "$ZIP" -d "$DEST"

if [ ! -f "$MARKER" ]; then
  echo "✗ 错误: 解压后未找到 ruffle.js"
  echo "  请手动从 https://github.com/ruffle-rs/ruffle/releases 下载 selfhosted zip"
  echo "  解压到: $DEST"
  exit 1
fi

echo "✓ Ruffle 引擎已放入 assets: $DEST"
ls -lh "$DEST"
