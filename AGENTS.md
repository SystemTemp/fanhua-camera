# 项目约定（Fanhua Camera）

本文档记录本项目必须遵守的工作流约定，任何对本项目的修改、发布都必须遵循以下规则。

## 仓库地址与同步规则

- **主仓库（推送目标）**：`https://github.com/SystemTemp/fanhua-camera.git`（remote 名：`origin`）
- **Gitee 镜像**：`https://gitee.com/TDSI/fanhua-camera.git`（remote 名：`gitee`）

规则：
1. 代码与 Release 一律**只推送到 GitHub（origin）**，禁止直接 push 到 Gitee。
2. GitHub 推送代码后，由 GitHub Actions 自动同步代码到 Gitee；GitHub 发布 Release 时，由工作流 `.github/workflows/sync-gitee-release.yml` 自动同步 Release、APK 附件和版本通道 `version.json` 到 Gitee。
3. Gitee 不会反向同步到 GitHub。
4. 本机网络可能无法直连 github.com：拉取可用镜像 `https://gitclone.com/github.com/SystemTemp/fanhua-camera.git` 或直连 Gitee（`git fetch gitee`）；推送前需确保网络可直连 GitHub 或已配置代理。

## 版本发布流程（必须按顺序执行）

1. **修改代码**：完成功能/修复，本地编译验证通过。
2. **修改版本号**：编辑 `app/build.gradle.kts` 的 `defaultConfig`：
   - `versionName`：语义化版本号，正式版形如 `1.0.0`，预发布版形如 `0.1.12-beta`
   - `versionCode`：单调递增整数（当前为 12），每次发布必须 +1
3. **编译**：
   - Debug：`./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
   - Release：`./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
   - 要求：JDK 21、Gradle 8.7，Android 项目根目录为 `app/`
4. **提交并推送**：提交信息按项目惯例写（如 `v0.1.12-beta: <变更摘要>`），push 到 `origin main`。
5. **发布 GitHub Release**：在 GitHub 上打 tag（如 `v0.1.12-beta`）并创建 Release，附上编译好的 APK，填写更新说明（中文）。
6. Release 发布后，等待 GitHub Actions 完成 Gitee 同步（代码 + Release + version.json），再告知用户发布完成。
7. **签名（重要）**：release 构建必须使用**本机**固定签名 `signing/fanhua-release.p12`（PKCS12，alias `fanhua`，store/key 密码均为 `fanhua2026`）。**该文件只保留在本机，不提交仓库（已在 .gitignore 排除 `signing/`）**。禁止使用 debug 签名或让 CI 每次生成新密钥——不同版本签名不一致会导致用户无法覆盖安装升级（INSTALL_FAILED_UPDATE_INCOMPATIBLE）。
8. **应用内更新**：使用系统安装器（ACTION_VIEW + FileProvider）安装更新，不要用 PackageInstaller 静默会话安装自身（会终止应用进程，表现为点击安装后闪退）。

## 仓库卫生

- `.trae/`（IDE 配置与文档）与 `signing/`（本机签名文件）均已被 `.gitignore` 排除，**不得提交**。

## 项目概况

- Android 相机应用（Kotlin + Jetpack Compose + CameraX），包名 `com.fanhua.camera`
- minSdk 26 / targetSdk 34 / compileSdk 34
- 源码根目录：`app/src/main/java/com/fanhua/camera/`（ui / camera / media / data 模块）
- 应用内更新检查依赖 Gitee 的版本通道 `https://gitee.com/TDSI/file/raw/master/fanhua/version.json`（由 Release 工作流维护）
