#!/usr/bin/env bash
#
# 一键项目初始化脚本
# 用法：./scripts/init-project.sh
#
# 功能：
#   1. 检查 Java 环境
#   2. 生成 Gradle Wrapper（gradlew + gradle-wrapper.jar）
#   3. 设置脚本可执行权限
#   4. 验证项目结构
#
set -e

cd "$(dirname "$0")/.."

echo "====================================="
echo "  Game4399 项目初始化"
echo "====================================="
echo ""

# 1. Java 检查
echo "[1/4] 检查 Java 环境..."
if ! command -v java > /dev/null 2>&1; then
    echo "✗ 未检测到 java，请先安装 JDK 17"
    echo "  建议：https://adoptium.net/temurin/releases/?version=17"
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | head -1 | awk -F\" '{print $2}')
echo "✓ Java: $JAVA_VERSION"

# 2. Gradle Wrapper 检查
echo ""
echo "[2/4] 检查 Gradle Wrapper..."
if [ -f "gradle/wrapper/gradle-wrapper.jar" ] && [ -f "gradlew" ]; then
    echo "✓ Gradle Wrapper 已存在"
else
    echo "Gradle Wrapper 缺失，正在生成..."
    if command -v gradle > /dev/null 2>&1; then
        gradle wrapper --gradle-version 8.7 --distribution-type bin
        echo "✓ Gradle Wrapper 已生成"
    else
        echo "✗ 系统未安装 gradle，无法自动生成 Wrapper"
        echo ""
        echo "请选择以下方式之一："
        echo ""
        echo "  方式 A：用 Android Studio 打开项目，它会自动生成 Wrapper"
        echo "  方式 B：手动安装 Gradle 后重试本脚本"
        echo "         macOS:   brew install gradle"
        echo "         Ubuntu:  sudo apt install gradle"
        echo "         Windows: choco install gradle"
        echo ""
        echo "  方式 C：直接用 GitHub Actions 在线构建（无需本地环境）"
        echo "         详见 RELEASE_GUIDE.md"
        exit 1
    fi
fi

# 3. 权限设置
echo ""
echo "[3/4] 设置脚本可执行权限..."
chmod +x gradlew 2>/dev/null && echo "✓ gradlew" || true
chmod +x scripts/generate-keystore.sh 2>/dev/null && echo "✓ scripts/generate-keystore.sh" || true
chmod +x scripts/init-project.sh 2>/dev/null && echo "✓ scripts/init-project.sh" || true

# 4. 项目结构检查
echo ""
echo "[4/4] 验证项目结构..."
REQUIRED_FILES=(
    "settings.gradle.kts"
    "build.gradle.kts"
    "gradle/libs.versions.toml"
    "gradle/wrapper/gradle-wrapper.properties"
    "app/build.gradle.kts"
    "app/src/main/AndroidManifest.xml"
    "app/src/main/assets/player.html"
    "app/src/main/java/com/game4399/app/MainActivity.kt"
    "app/src/main/java/com/game4399/app/GameActivity.kt"
    ".github/workflows/build-apk.yml"
    "README.md"
    "LICENSE"
)
MISSING=0
for f in "${REQUIRED_FILES[@]}"; do
    if [ -f "$f" ]; then
        echo "  ✓ $f"
    else
        echo "  ✗ 缺失: $f"
        MISSING=$((MISSING + 1))
    fi
done

echo ""
echo "====================================="
if [ $MISSING -eq 0 ]; then
    echo "  ✓ 初始化完成！项目结构完整"
    echo ""
    echo "  下一步："
    echo "    ./gradlew assembleDebug       # 构建 Debug APK"
    echo "    ./gradlew assembleRelease     # 构建 Release APK"
    echo "    或直接用 Android Studio 打开"
    echo ""
    echo "  APK 产出位置："
    echo "    app/build/outputs/apk/debug/app-debug.apk"
    echo "    app/build/outputs/apk/release/app-release.apk"
else
    echo "  ✗ 有 $MISSING 个文件缺失"
    exit 1
fi
echo "====================================="
