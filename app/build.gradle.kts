plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.fanhua.camera"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fanhua.camera"
        minSdk = 26
        targetSdk = 34
        versionCode = 38
        versionName = "0.1.37-beta"
        vectorDrawables {
            useSupportLibrary = true
        }
        // 必须显式列出 zh-rCN：只写 "zh" 会让 AAPT 过滤掉 values-zh-rCN，
        // 导致简体设备找不到精确匹配而回退到 zh-rTW（显示繁体）。
        resourceConfigurations += listOf(
            "zh", "zh-rCN", "zh-rTW", "en", "ja", "ko", "es", "fr", "de", "ru"
        )
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("signing/fanhua-release.p12")
            storePassword = "fanhua2026"
            keyAlias = "fanhua"
            keyPassword = "fanhua2026"
        }
    }

    buildTypes {
        release {
            // 开启 R8 代码压缩与资源压缩：可去掉未使用的 Compose / CameraX 代码路径，
            // 显著缩小 APK 体积。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val cameraxVersion = "1.4.2"

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-video:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-effects:$cameraxVersion")

    implementation("com.google.mlkit:face-detection:16.1.7")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    testImplementation("junit:junit:4.13.2")
    // 单元测试里 Android 的 org.json 是桩实现，需用真实实现替换
    testImplementation("org.json:json:20240303")
}
