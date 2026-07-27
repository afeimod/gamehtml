# 构建手册

## 三种构建方式

### 1. 手动构建（推荐先在本地跑一次）

需要：
- JDK 17+
- Android SDK API 24+ （最低 24，目标 34）
- Internet（下载 Gradle 依赖）

```bash
git clone https://github.com/yourname/FlashGameBox.git
cd FlashGameBox
chmod +x gradlew

# 设置 ANDROID_HOME
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME

# debug 构建（不需要签名）
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# release 构建（默认用 debug key 签名）
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk （或 app-release-unsigned.apk）

# release 签名（用你自己的 keystore）
keytool -genkey -v -keystore flashbox.jks -keyalg RSA -keysize 2048 -validity 10000 -alias flashbox
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=$(pwd)/flashbox.jks \
  -Pandroid.injected.signing.store.password=YOUR_PASS \
  -Pandroid.injected.signing.key.alias=flashbox \
  -Pandroid.injected.signing.key.password=YOUR_PASS
```

### 2. GitHub Actions 手动触发（无需本地环境）

1. 把代码推到 GitHub
2. 进入仓库的 **Actions** 标签
3. 选 **Build Android Release APK** workflow
4. 点 **Run workflow** 按钮，填入可选输入：
   - `keystore_base64`：`base64 -w 0 your.jks > keystore.b64`，把内容粘进去
   - `keystore_alias`：keystore 的 alias（默认 `flashbox`）
   - `keystore_password`：keystore 密码
   - `key_password`：key 密码
   - `build_type`：`release` / `debug` / `signedRelease`
   - `app_version`：可选的版本号覆盖
   - `app_version_code`：可选的 versionCode
5. 等 ~3-8 分钟，下载 Artifacts 里的 APK

### 3. Android Studio

1. 用 Android Studio 打开项目根目录
2. 等 Gradle sync 完
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
4. 完成后弹窗里点 **locate** 跳到产物

## 故障排查

| 报错 | 解决 |
|------|------|
| `SDK location not found` | `local.properties` 写 `sdk.dir=/path/to/android-sdk` |
| `Minimum supported Gradle version` | 用项目自带 gradle wrapper：`./gradlew` |
| `Failed to find target android-XX` | 安装对应 API：`sdkmanager "platforms;android-34"` |
| `OutOfMemory` | 在 `gradle.properties` 调大 `org.gradle.jvmargs=-Xmx4g` |
| `Execution failed for task ':app:processReleaseResources'` | 删 `~/.gradle/caches/transforms-*` 重新构建 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 卸载旧版再装：`adb uninstall com.flashbox.app` |

## 第三方引擎更新

### 更新 Ruffle

```bash
# 下载最新 selfhosted
curl -L -o app/src/main/assets/www/engines/ruffle/ruffle.js \
  https://cdn.jsdelivr.net/npm/@ruffle-rs/ruffle@latest/ruffle.js
```

### 更新 Waflash

```bash
# 重新下载 Waflash-Player 仓库
rm -rf tmp-waflash
git clone https://github.com/AbhinavJaiswal1/Waflash-Player.git tmp-waflash
cp tmp-waflash/waflash/* app/src/main/assets/www/engines/waflash/
```

### 更新 swf2js

我们用的是内置的 `swf2js-lite`（自实现），如需切换为上游 `swf2js`：

```bash
curl -L -o app/src/main/assets/www/engines/swf2js/swf2js.js \
  https://example.com/path/to/swf2js.js
# 并修改 js/app.js 中 Engines.swf2js 的 load 逻辑
```

## 安装到设备

```bash
adb install app/build/outputs/apk/release/app-release.apk
# 或
adb install -r app-debug.apk    # 覆盖安装
adb uninstall com.flashbox.app  # 卸载
```

## 调试

Android 4.4+ 可以在 Chrome 远程调试 WebView：
1. 启用 USB 调试
2. `chrome://inspect/#devices` → 找到 WebView
3. 直接 devtools

如果设备 < Android 4.4 不可用；本项目 `minSdk = 24` 没问题。
