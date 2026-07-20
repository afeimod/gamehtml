# 贡献指南

欢迎为 Game4399 项目贡献代码！

## 🚀 快速开始

1. Fork 本仓库
2. 克隆到本地：
   ```bash
   git clone https://github.com/<你的用户名>/Game4399.git
   cd Game4399
   ```
3. 生成 Gradle Wrapper（首次）：
   ```bash
   gradle wrapper --gradle-version 8.7
   ```
4. 用 Android Studio Hedgehog+ 打开项目根目录
5. 创建特性分支：`git checkout -b feature/your-feature`
6. 提交：`git commit -m "feat: 新增功能"`
7. 推送：`git push origin feature/your-feature`
8. 提交 Pull Request

## 📋 提交规范

使用 [Conventional Commits](https://www.conventionalcommits.org/)：

| 前缀 | 用途 |
|------|------|
| `feat:` | 新功能 |
| `fix:` | 修复 Bug |
| `docs:` | 文档更新 |
| `style:` | 代码格式（不影响逻辑） |
| `refactor:` | 重构 |
| `perf:` | 性能优化 |
| `test:` | 测试 |
| `chore:` | 构建/工具链 |

示例：
```
feat: 新增虚拟手柄透明度调节
fix: 修复 SWF 链接拦截失败
docs: 补充离线 Ruffle 部署说明
```

## ✅ 代码要求

- Kotlin 代码遵循 [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- 新增功能需更新相关文档
- 公共 API 添加 KDoc 注释
- 不引入不必要的依赖

## 🧪 测试

提交前请确认：
- [ ] 项目能 `./gradlew assembleDebug` 成功
- [ ] 在 Android 6.0 / 10.0 / 14 真机或模拟器上能启动
- [ ] 涉及 Flash 的改动至少在 1 个 4399 Flash 游戏上验证

## 📦 构建

本地构建：
```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

CI 构建见 `.github/workflows/`，可在 GitHub Actions 页面手动触发。

## 🐛 反馈问题

- 提 Issue 时请描述：设备型号、Android 版本、复现步骤、预期与实际表现
- 附上 logcat 截图更佳

## 📄 许可

提交的代码默认遵循本项目的 MIT 许可证。
