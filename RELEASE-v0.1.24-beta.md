# v0.1.24-beta 发布说明

**版本**：0.1.24-beta（versionCode 25）
**提交**：e9ba500（已推送 GitHub main + tag v0.1.24-beta）
**APK**：`app/build/outputs/apk/release/app-release.apk`（25.1 MB，本机固定签名 fanhua-release.p12）

## 本次变更
1. **徕卡水印三态可切换**（参照「相机整合」徕卡一瞬包实现）
   - 关闭 / 白边画框（徕卡 92 号，水印在照片外部）/ 照片内叠加（标准 7 号）。
   - 新增 `WatermarkMode` 枚举；设置页水印项改为三态选择。
   - 仅当滤镜属于 LEICA 组时生效。
2. **滤镜色卡改用真实 LUT 预览图**
   - 从相机整合的徕卡包搬来 24 张 LUT 预览图到 `res/drawable/lut_preview_*.png`。
   - `FilterSwatch` 优先用真实预览图，无预览图时回退参数化色块。
3. **多语种文案**：新增 `watermark_off / watermark_frame / watermark_embedded`，覆盖简/繁/英/日/韩/西/法/德/俄共 9 语种。

## 验证
- `bash build.sh :app:assembleDebug` 通过
- `bash build.sh :app:assembleRelease` 通过（本机固定签名）

## 待办（需完整 PAT 在 GitHub 网页或本地执行）
因本机会话仓库凭据仅支持 git 操作，无法调用 Releases API，请在拥有完整权限的环境完成：
- 在 GitHub 以 tag `v0.1.24-beta` 创建 Release，标题「v0.1.24-beta：徕卡水印三态 + 滤镜真实色卡」，
  正文即本文档「本次变更」段落，并上传 `app-release.apk`。
- 或在本地用 PAT 执行（替换 $PAT）：
  ```bash
  gh release create v0.1.24-beta app/build/outputs/apk/release/app-release.apk \
    --title "v0.1.24-beta：徕卡水印三态 + 滤镜真实色卡" \
    --notes-file RELEASE-v0.1.24-beta.md --prerelease
  ```
- Release 发布后由 GitHub Actions 自动同步代码/Release/version.json 到 Gitee。
