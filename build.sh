#!/usr/bin/env bash
# 手动构建脚本 (Linux / macOS)
# 用法:
#   ./build.sh                      # 编译 debug, 全 ABI
#   ./build.sh release arm64-v8a    # release 包, 仅 arm64
#   ./build.sh debug x86
# 要求本机已安装: JDK 17+, Android SDK (含 platforms;android-34, build-tools;34.0.0), Gradle 8.7+
set -e

BUILD_TYPE="${1:-debug}"
ABI="${2:-all}"

if [ ! -f "local.properties" ]; then
  if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "[!] ANDROID_HOME / ANDROID_SDK_ROOT 未设置，且没有 local.properties"
    echo "    请设置: export ANDROID_HOME=/path/to/android-sdk"
    exit 1
  fi
  SDK_DIR="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
  echo "sdk.dir=$SDK_DIR" > local.properties
fi

case "$ABI" in
  arm64-v8a|armeabi-v7a|x86|x86_64)
    ABILIST="$ABI"
    ;;
  *)
    ABILIST="arm64-v8a,armeabi-v7a,x86,x86_64"
    ;;
esac

echo "[*] Build type: $BUILD_TYPE   ABI: $ABILIST"

GRADLE="${GRADLE_BIN:-gradle}"
if ! command -v "$GRADLE" >/dev/null 2>&1; then
  echo "[!] 没找到 gradle 命令，请安装 Gradle 8.7+ 或设置 GRADLE_BIN=/path/to/gradle"
  exit 1
fi

CAP_BUILD_TYPE="$(echo "$BUILD_TYPE" | awk '{print toupper(substr($0,1,1)) substr($0,2)}')"
"$GRADLE" ":app:assemble${CAP_BUILD_TYPE}" -PabiList="$ABILIST" --no-daemon --stacktrace

APK="app/build/outputs/apk/${BUILD_TYPE}/app-${BUILD_TYPE}.apk"
if [ -f "$APK" ]; then
  echo "[OK] APK 已生成: $APK"
  ls -la "$APK"
else
  echo "[!] 没找到 APK: $APK"
  ls -la app/build/outputs/apk/${BUILD_TYPE}/ 2>/dev/null || true
  exit 1
fi
