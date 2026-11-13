pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // repo1.maven.org 对本机返回 500，显式走 repo.maven.apache.org 取 AAR 主体
        maven("https://repo.maven.apache.org/maven2")
        mavenCentral()
    }
}

rootProject.name = "FanhuaCamera"
include(":app")