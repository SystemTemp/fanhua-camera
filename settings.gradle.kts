pluginManagement {
    repositories {
        // 国内镜像优先：dl.google.com 在本机直连超时，腾讯云 / 阿里云镜像速度快
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        // repo1.maven.org 对本机返回 500，显式走 repo.maven.apache.org 取 AAR 主体
        maven("https://repo.maven.apache.org/maven2")
        mavenCentral()
    }
}

rootProject.name = "FanhuaCamera"
include(":app")
