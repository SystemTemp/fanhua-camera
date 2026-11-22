## 修复测试反馈的 6 个问题（v0.1.43-beta）

### P0：LUT 成片链路修复
- 出片时所有真实 LUT 滤镜（徕卡 look / 影调 tone）现在都会解析并应用
  LUT——此前只有用户导入的自定义 LUT 生效，其余 35 个 LUT 滤镜成片
  退化为原图 + 颗粒
- 修复 `setLut` 在 EGL 上下文就绪前调用的问题（会拿到 0 号纹理，
  自定义 LUT 拍出接近黑片）

### P1：逻辑与并发修复
- 自动构图裁切接通：AI 建议框现在跟随检测结果实时更新，
  按快门时按建议框真裁（此前 autoCropRect 恒为 null，是死链路）
- 陀螺仪矩阵返回副本，消除 GL 线程读半更新矩阵的数据竞争
- 模型下载取消立即生效（原先 isActive 被快照，取消要等整个文件下完）
- TFLite 解释器 close 与推理加同一把锁串行化，消除跨线程
  close 导致的 native crash 风险
- 4K 处理路径单独兜 OutOfMemoryError，OOM 时降级原图直出不闪退

### 代码卫生
- 删除死代码：boundSignature、buildViewPort（恒 null）、
  录像空转计时器（时长由 CameraX Status 事件推送）
- 合并重复的 appVersionName 实现；修正 cropToRect 的错位注释
- zh-rCN 补齐 11 条缺失翻译（超级月亮/水印/徕卡 M3/M9 等，
  此前繁体设备会回退英文）
- CI：build.yml 加入单元测试；Release 说明改读 release-notes.md
  （原先写死在 workflow 里，每次发布都是旧文案）
- 签名口令支持从 secrets 注入（本地构建回落默认值）

## 安装
arm64 / Android 8.0+，固定签名可覆盖升级。
