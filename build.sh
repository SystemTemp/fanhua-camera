#!/usr/bin/env bash
# 繁花如梦相机 · 构建辅助脚本
#
# 这个脚本解决两类常见问题：
# 1. HOME 目录只读 —— Gradle 与 Android SDK 默认往 ~/.gradle、~/.android 写，
#    会直接失败。脚本把 GRADLE_USER_HOME / ANDROID_USER_HOME 重定向到项目内的
#    .buildenv/ 目录。
# 2. JDK 版本不匹配 —— AGP 8.6 需要 JDK 17+，系统自带的可能是 8 或更高版本。
#    若 .buildenv/jdk 下已有 JDK 17 就优先使用，否则用系统 JAVA_HOME。
#
# 首次使用会自动下载到 .buildenv/（已加入 .gitignore，不进仓库）：
#   bash build.sh --setup
#
# 常规用法：
#   bash build.sh                      # 等价于 :app:assembleDebug
#   bash build.sh :app:assembleRelease
#   bash build.sh :app:testDebugUnitTest
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

ENV_DIR="$ROOT/.buildenv"
GRADLE_VERSION="8.9"
JDK_URL="https://mirrors.huaweicloud.com/openjdk/17.0.2/openjdk-17.0.2_linux-x64_bin.tar.gz"
BUILD_TOOLS_URL="https://dl.google.com/android/repository/build-tools_r34-linux.zip"
PLATFORM_URL="https://dl.google.com/android/repository/platform-34_r03.zip"

log() { printf '\033[1;33m[构建]\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m[错误]\033[0m %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- 环境准备

setup() {
    mkdir -p "$ENV_DIR"

    if [ ! -d "$ENV_DIR/jdk/jdk-17.0.2" ]; then
        log "下载 JDK 17 ..."
        mkdir -p "$ENV_DIR/jdk"
        curl -fsSL -o "$ENV_DIR/jdk/jdk.tar.gz" "$JDK_URL"
        tar xzf "$ENV_DIR/jdk/jdk.tar.gz" -C "$ENV_DIR/jdk"
        rm -f "$ENV_DIR/jdk/jdk.tar.gz"
    fi

    if [ ! -x "$ENV_DIR/gradle-$GRADLE_VERSION/bin/gradle" ]; then
        log "下载 Gradle $GRADLE_VERSION ..."
        curl -fsSL -o "$ENV_DIR/gradle.zip" \
            "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
        unzip -q -o "$ENV_DIR/gradle.zip" -d "$ENV_DIR"
        rm -f "$ENV_DIR/gradle.zip"
        chmod +x "$ENV_DIR/gradle-$GRADLE_VERSION/bin/gradle"
    fi

    if [ ! -d "$ENV_DIR/sdk/build-tools/34.0.0" ]; then
        log "下载 Android Build Tools 34 ..."
        mkdir -p "$ENV_DIR/sdk/build-tools"
        curl -fsSL -o "$ENV_DIR/bt.zip" "$BUILD_TOOLS_URL"
        unzip -q -o "$ENV_DIR/bt.zip" -d "$ENV_DIR/sdk/build-tools"
        # 解压出来的目录名是 android-14，规整成 34.0.0
        local inner
        inner="$(find "$ENV_DIR/sdk/build-tools" -maxdepth 1 -mindepth 1 -type d \
            ! -name "34.0.0" | head -1)"
        [ -n "$inner" ] && mv "$inner" "$ENV_DIR/sdk/build-tools/34.0.0"
        rm -f "$ENV_DIR/bt.zip"
    fi

    if [ ! -d "$ENV_DIR/sdk/platforms/android-34" ]; then
        log "下载 Android Platform 34 ..."
        mkdir -p "$ENV_DIR/sdk/platforms"
        curl -fsSL -o "$ENV_DIR/platform.zip" "$PLATFORM_URL"
        unzip -q -o "$ENV_DIR/platform.zip" -d "$ENV_DIR/sdk/platforms"
        rm -f "$ENV_DIR/platform.zip"
    fi

    log "环境准备完成"
}

if [ "${1:-}" = "--setup" ]; then
    setup
    exit 0
fi

# ---------------------------------------------------------------- 环境变量

# 优先使用项目内的 JDK 17，其次系统 JAVA_HOME
if [ -d "$ENV_DIR/jdk/jdk-17.0.2" ]; then
    export JAVA_HOME="$ENV_DIR/jdk/jdk-17.0.2"
elif [ -n "${JAVA_HOME:-}" ]; then
    :
else
    die "未找到 JDK。请先执行: bash build.sh --setup"
fi

export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ENV_DIR/home}"
export TMPDIR="${TMPDIR:-$ENV_DIR/tmp}"
export ANDROID_HOME="${ANDROID_HOME:-$ENV_DIR/sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$ENV_DIR/androiduser}"

mkdir -p "$GRADLE_USER_HOME" "$TMPDIR" "$ANDROID_USER_HOME"

# local.properties 指向 SDK（AGP 会读取它）
if [ -d "$ANDROID_HOME" ]; then
    printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$ROOT/local.properties"
fi

# ---------------------------------------------------------------- 选择 Gradle

if [ -x "$ENV_DIR/gradle-$GRADLE_VERSION/bin/gradle" ]; then
    GRADLE="$ENV_DIR/gradle-$GRADLE_VERSION/bin/gradle"
elif [ -x "$ROOT/gradlew" ]; then
    GRADLE="$ROOT/gradlew"
else
    die "未找到 Gradle。请先执行: bash build.sh --setup"
fi

TASK="${1:-:app:assembleDebug}"
[ $# -gt 0 ] && shift

exec "$GRADLE" "$TASK" \
    -Djava.io.tmpdir="$TMPDIR" \
    --console=plain \
    "$@"
