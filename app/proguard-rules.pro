# 繁花如梦相机 · R8 规则

# ---------- Kotlin ----------
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers class kotlin.Metadata { public <methods>; }

# Kotlin 协程
-dontwarn kotlinx.coroutines.**
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# ---------- CameraX ----------
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ---------- ML Kit 人脸检测 ----------
# ML Kit 依赖反射加载模型与原生库，必须整体保留
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_face.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ---------- DataStore ----------
-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends androidx.datastore.preferences.core.Preferences { *; }

# ---------- Compose ----------
# Compose 运行时通过反射查找 @Composable 的元信息
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ---------- 项目自身 ----------
# 枚举名用于持久化（preset / photo_aspect 等以 name 存储），不能被混淆
-keepclassmembers enum com.fanhua.camera.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static ** fromKey(java.lang.String);
}
# 枚举常量名本身也要保留
-keepclassmembers enum com.fanhua.camera.camera.filters.FilterPreset {
    <fields>;
}
-keepclassmembers enum com.fanhua.camera.camera.core.PhotoAspect {
    <fields>;
}

# 调试日志与崩溃上报需要可读的类名
-keepnames class com.fanhua.camera.** { *; }

# ---------- 其它 ----------
-dontwarn org.jetbrains.annotations.**
