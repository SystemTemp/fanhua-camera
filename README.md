# 繁花如梦相机 · Fanhua Camera

An open-source Android photography app featuring Mi-style dream filters, Leica M9-inspired color grading, a real-time GLES super-resolution pipeline, hardware/gyroscope stabilization, ML Kit powered auto-composition and Live Photo capture. Built with Kotlin, Jetpack Compose and CameraX — no third-party filter libraries.

小米「繁花如梦」风格滤镜、徕卡 M9「一瞬」色彩近似、GPU 实时超分（1080p 采集 → 4K 输出）、硬件/陀螺仪防抖、ML Kit 自动构图、实况照片，9 语种界面。

## Features

- Mi "Flourishing Dream" and Leica M9 "Moment" film-style filters (color curves, soft bloom, grain, vignette)
- Real-time GLES pipeline: EASU edge-adaptive upsampling + RCAS sharpening (1080p → 4K)
- Hardware OIS/EIS plus gyroscope software stabilization
- AI auto-composition via ML Kit face detection (rule-of-thirds scoring, crop suggestions)
- Live Photo: one shutter press captures a photo plus a 3-second clip
- CameraX effect pipeline shared between preview and video recording
- Photo / Video / Pro / SuperRes modes, manual focus, zoom, flash
- 9 languages (zh-CN / zh-TW / en / ja / ko / es / fr / de / ru)

## Requirements

- Android 8.0+ (minSdk 26), targetSdk 34
- Gradle 8.7 / JDK 21

## Build

```
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Project structure

```
app/src/main/java/com/fanhua/camera/
  ui/           # Compose UI (camera, settings, gallery, common widgets)
  camera/core/  # CameraController, CameraXEngine, CapabilityProbe
  camera/capture/  # CameraX GL filter effect and surface processor
  camera/gl/    # EGL context, GL pipeline, shader passes (EASU / RCAS / bloom / grain)
  camera/filters/  # filter presets and color grades
  camera/stabilize/  # gyroscope stabilization
  camera/analyzer/   # face analyzer and composition engine
  media/        # photo processor (offline filter + upscale), MediaStore
  data/         # DataStore settings, locale manager
```

## Author

[SystemTemp](https://github.com/SystemTemp)

## License

[Apache-2.0](LICENSE)
