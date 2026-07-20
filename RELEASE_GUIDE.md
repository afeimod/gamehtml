# 🚀 发布到 GitHub 完整指南

本指南介绍如何把 Game4399 项目上传到 GitHub，并通过 GitHub Actions 手动触发 APK 构建。

## 一、上传项目到 GitHub

### 1.1 在 GitHub 创建空仓库

1. 登录 [github.com](https://github.com)
2. 右上角 `+` → **New repository**
3. 填写：
   - Repository name: `Game4399`
   - Description: `4399 安卓 App，支持 Flash 插件、触屏 + 物理键盘 + 虚拟手柄`
   - 选择 **Public**（推荐，可使用免费 CI）或 Private
   - **不要勾选** "Initialize this repository"（不添加 README/.gitignore/license，避免冲突）
4. 点 **Create repository**

### 1.2 初始化本地 Git 仓库

```bash
cd /workspace/Game4399

# 初始化
git init
git branch -M main

# 配置用户（如果之前没配过）
git config user.name  "你的名字"
git config user.email "你的邮箱@example.com"

# 设置 gradlew 可执行权限（关键！）
chmod +x gradlew scripts/generate-keystore.sh

# 添加所有文件
git add .

# 首次提交
git commit -m "feat: 初始化 Game4399 项目

- WebView + Ruffle WebAssembly 实现 Flash 支持
- 触屏 + 物理键盘 + 虚拟手柄三种输入方式
- 4399 手机版/电脑版/分类/收藏/历史完整功能
- GitHub Actions 手动触发构建 APK workflow"
```

### 1.3 推送到 GitHub

```bash
# 关联远程仓库（把 <你的用户名> 换成实际用户名）
git remote add origin https://github.com/<你的用户名>/Game4399.git

# 推送
git push -u origin main
```

如果用 SSH：
```bash
git remote add origin git@github.com:<你的用户名>/Game4399.git
git push -u origin main
```

推送成功后，仓库根目录应能看到：
```
.github/         (workflows + 模板)
app/             (源代码)
gradle/          (wrapper 配置)
scripts/         (辅助脚本)
build.gradle.kts
settings.gradle.kts
gradlew
gradlew.bat
README.md
LICENSE
CONTRIBUTING.md
...
```

## 二、手动触发 APK 构建

### 2.1 触发 Debug 构建（最快验证）

1. 进入仓库页面 → 顶部 **Actions** 标签
2. 左侧选择 **Build APK** workflow
3. 右侧点 **Run workflow** 下拉
4. 配置：
   - Branch: `main`
   - Build type: `debug`
   - Upload release: `false`（保持默认）
5. 点绿色 **Run workflow** 按钮

构建约需 8-15 分钟（首次更久，因为要下载 SDK）。

### 2.2 下载构建产物

1. 构建完成后，点击该次 workflow run
2. 拉到底部 **Artifacts** 区域
3. 点击 `Game4399-debug-<run_number>` 下载 zip
4. 解压得到 `Game4399-1.0.0-<sha>-<date>-debug.apk`
5. 传到手机安装即可

### 2.3 构建 Release 版本

#### 方式 A：使用 debug 签名（无需配置 Secrets，开箱即用）

直接在 Run workflow 时选 `release` 即可。Workflow 会自动用 Android SDK 的 `debug.keystore` 签名，产物可直接安装。

#### 方式 B：使用自有签名（推荐正式发布）

1. 本地生成 keystore：
   ```bash
   cd Game4399
   ./scripts/generate-keystore.sh
   # 按提示输入两次密码（storePassword 与 keyPassword 可相同）
   ```

2. Base64 编码 keystore：
   ```bash
   base64 app/release.keystore > /tmp/release.keystore.base64
   ```

3. 在 GitHub 仓库 → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**，添加 4 个 Secrets：

   | Name | Value |
   |------|-------|
   | `SIGNING_KEY_BASE64` | `/tmp/release.keystore.base64` 文件内容（一长串 base64） |
   | `SIGNING_STORE_PASSWORD` | 你设置的 store 密码 |
   | `SIGNING_KEY_ALIAS` | `game4399`（默认） |
   | `SIGNING_KEY_PASSWORD` | 你设置的 key 密码 |

4. 再次到 Actions → Build APK → Run workflow，选 `release`。Workflow 会自动解码 keystore 并用自有签名产出 `app-release.apk`。

## 三、自动发布 Release（推 tag 触发）

当你想发布新版本时：

```bash
# 1. 更新版本号（可选）
# 编辑 app/build.gradle.kts 中的 versionCode 和 versionName

# 2. 提交
git add .
git commit -m "chore: bump version to 1.0.1"

# 3. 打 tag
git tag v1.0.1
git push origin v1.0.1
```

推送 tag 后会自动：
1. 触发 Build APK workflow
2. 同时构建 debug + release APK
3. 创建 GitHub Release 并附带 APK 下载链接
4. 生成 Release Notes

用户可直接在 `https://github.com/<你的用户名>/Game4399/releases` 下载 APK。

## 四、CI 与本地构建的等价性

| 场景 | 本地 | GitHub Actions |
|------|------|----------------|
| Gradle Wrapper 缺失 | 需手动 `gradle wrapper` | workflow 自动生成 |
| Android SDK | 需自行安装 | `android-actions/setup-android` 自动安装 |
| JDK | 需 JDK 17 | `actions/setup-java` 自动安装 |
| Release 签名 | 用 `app/keystore.properties` | 用 Repository Secrets |
| 产物位置 | `app/build/outputs/apk/` | Artifacts 下载区 |

## 五、常见问题

### Q1：workflow 失败提示 "gradle-wrapper.jar not found"
A：workflow 已包含自动生成步骤。若仍失败，检查 `gradle/wrapper/gradle-wrapper.properties` 是否存在且 `distributionUrl` 正确。

### Q2：构建 OOM 或超时
A：workflow 默认 `timeout-minutes: 30`。如需更多时间，编辑 `.github/workflows/build-apk.yml` 修改超时。

### Q3：APK 安装时提示"未知来源"
A：这是 Android 系统限制。手机设置 → 安全 → 允许"未知来源"安装即可。

### Q4：如何修改应用图标
A：替换 `app/src/main/res/drawable/ic_launcher_foreground.xml` 与 `mipmap-anydpi-v26/ic_launcher.xml`。

### Q5：如何查看构建日志
A：Actions 页面点击具体的 workflow run → 展开各 step 即可查看完整输出。

## 六、仓库目录最终结构

```
Game4399/
├── .github/
│   ├── workflows/
│   │   ├── build-apk.yml      ← 手动/推tag 触发构建 APK
│   │   └── ci-check.yml       ← PR/push 时自动校验
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.md
│   │   └── feature_request.md
│   ├── pull_request_template.md
│   └── dependabot.yml         ← 自动更新依赖
├── app/
│   ├── src/main/...
│   ├── build.gradle.kts       ← 含签名配置
│   ├── proguard-rules.pro
│   └── keystore.properties.example
├── gradle/
│   ├── wrapper/
│   │   └── gradle-wrapper.properties
│   └── libs.versions.toml
├── scripts/
│   └── generate-keystore.sh
├── .gitignore
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew                    ← Unix 构建脚本
├── gradlew.bat                ← Windows 构建脚本
├── README.md
├── LICENSE
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
└── RELEASE_GUIDE.md           ← 本文档
```
